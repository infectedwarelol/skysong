import { DurableObject } from "cloudflare:workers";

interface Env {
  HUB: DurableObjectNamespace<Hub>;
  SESSION_SERVER?: string;
}

interface Song {
  title: string;
  artist: string;
  trackId: string;
  positionMs: number;
  durationMs: number;
  playing: boolean;
  at: number;
}

interface Session {
  uuid: string;
  name: string;
  song: Song | null;
  follows: string | null;
  lastSong: number;
}

interface Account {
  name: string;
  friends: string[];
  incoming: string[];
  outgoing: string[];
}

const NAME = /^[A-Za-z0-9_]{3,16}$/;
const SPOTIFY_ID = /^[A-Za-z0-9]{22}$/;
const MAX_FRIENDS = 200;
const MAX_PENDING = 100;
const MAX_TEXT = 128;
const MAX_MESSAGE = 4096;
const SONG_INTERVAL_MS = 500;
const MOJANG_SESSION_SERVER = "https://sessionserver.mojang.com";

function text(value: unknown): string {
  return typeof value === "string" ? value.replace(/\p{Cc}/gu, "").slice(0, MAX_TEXT) : "";
}

function number(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? Math.min(Math.floor(value), 86_400_000) : 0;
}

function dashed(id: string): string {
  return `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
}

function without(list: string[], uuid: string): string[] {
  return list.filter((entry) => entry !== uuid);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/") {
      return new Response("Skysong relay");
    }

    if (url.pathname !== "/ws") {
      return new Response("Not found", { status: 404 });
    }

    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("Expected a WebSocket", { status: 426 });
    }

    return env.HUB.get(env.HUB.idFromName("hub")).fetch(request);
  },
};

export class Hub extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(): Promise<Response> {
    const pair = new WebSocketPair();
    this.ctx.acceptWebSocket(pair[1]);
    pair[1].serializeAttachment(null);
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  async webSocketMessage(ws: WebSocket, raw: string | ArrayBuffer): Promise<void> {
    if (typeof raw !== "string" || raw.length > MAX_MESSAGE) {
      return;
    }

    let message: Record<string, unknown>;
    try {
      message = JSON.parse(raw);
    } catch {
      return;
    }

    if (!message || typeof message !== "object" || typeof message.t !== "string") {
      return;
    }

    const session = ws.deserializeAttachment() as Session | null;
    if (!session) {
      if (message.t === "hello") {
        await this.hello(ws, message);
      }
      return;
    }

    const name = text(message.name);
    switch (message.t) {
      case "add":
        await this.add(ws, session, name);
        break;
      case "accept":
        await this.accept(ws, session, name);
        break;
      case "decline":
        await this.dropRequest(session.uuid, await this.uuidOf(name));
        break;
      case "cancel":
        await this.dropRequest(await this.uuidOf(name), session.uuid);
        break;
      case "remove":
        await this.remove(session, name);
        break;
      case "song":
        await this.song(ws, session, message);
        break;
      case "follow":
        await this.follow(ws, session, name);
        break;
      case "unfollow":
        await this.unfollow(ws, session);
        break;
    }
  }

  async webSocketClose(ws: WebSocket): Promise<void> {
    await this.left(ws);
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    await this.left(ws);
  }

  private async left(ws: WebSocket): Promise<void> {
    const session = ws.deserializeAttachment() as Session | null;
    ws.serializeAttachment(null);
    if (!session || this.sockets(session.uuid).length > 0) {
      return;
    }

    if (session.follows) {
      await this.pushState(session.follows);
    }

    const account = await this.account(session.uuid);
    if (account) {
      for (const friend of account.friends) {
        await this.pushState(friend);
      }
    }
  }

  private async hello(ws: WebSocket, message: Record<string, unknown>): Promise<void> {
    const name = text(message.name);
    const serverId = text(message.serverId);
    if (!NAME.test(name) || serverId.length < 8) {
      this.fail(ws, "Invalid sign-in");
      return;
    }

    const base = this.env.SESSION_SERVER || MOJANG_SESSION_SERVER;
    const url = `${base}/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`;
    let profile: { id?: string; name?: string };
    try {
      const response = await fetch(url);
      if (response.status !== 200) {
        this.fail(ws, "Minecraft account could not be verified");
        return;
      }
      profile = await response.json();
    } catch {
      this.fail(ws, "Minecraft login servers did not answer, try again soon");
      return;
    }

    if (!profile.id || !profile.name || !/^[0-9a-f]{32}$/.test(profile.id)) {
      this.fail(ws, "Minecraft account could not be verified");
      return;
    }

    const uuid = dashed(profile.id);
    const realName = profile.name;
    const account = (await this.account(uuid)) ?? { name: realName, friends: [], incoming: [], outgoing: [] };
    if (account.name !== realName) {
      await this.ctx.storage.delete(`n:${account.name.toLowerCase()}`);
      account.name = realName;
    }

    await this.ctx.storage.put(`u:${uuid}`, account);
    await this.ctx.storage.put(`n:${realName.toLowerCase()}`, uuid);
    for (const other of this.sockets(uuid)) {
      other.serializeAttachment(null);
      other.close(4000, "Signed in somewhere else");
    }

    const session: Session = { uuid, name: realName, song: null, follows: null, lastSong: 0 };
    ws.serializeAttachment(session);
    this.send(ws, { t: "welcome", uuid, name: realName });
    await this.pushState(uuid);
    for (const friend of account.friends) {
      await this.pushState(friend);
    }
  }

  private async add(ws: WebSocket, session: Session, name: string): Promise<void> {
    if (!NAME.test(name)) {
      this.send(ws, { t: "error", message: "That isn't a Minecraft name" });
      return;
    }

    const target = await this.uuidOf(name);
    if (!target) {
      this.send(ws, { t: "error", message: `${name} hasn't turned on Skysong party yet` });
      return;
    }

    if (target === session.uuid) {
      this.send(ws, { t: "error", message: "You can't add yourself" });
      return;
    }

    const me = await this.account(session.uuid);
    const them = await this.account(target);
    if (!me || !them) {
      return;
    }

    if (me.friends.includes(target)) {
      this.send(ws, { t: "error", message: `${them.name} is already your friend` });
      return;
    }

    if (me.incoming.includes(target)) {
      await this.accept(ws, session, them.name);
      return;
    }

    if (me.outgoing.includes(target)) {
      this.send(ws, { t: "error", message: `Request to ${them.name} already sent` });
      return;
    }

    if (me.friends.length >= MAX_FRIENDS || me.outgoing.length >= MAX_PENDING || them.incoming.length >= MAX_PENDING) {
      this.send(ws, { t: "error", message: "Too many friends or requests" });
      return;
    }

    me.outgoing.push(target);
    them.incoming.push(session.uuid);
    await this.save(session.uuid, me);
    await this.save(target, them);
    this.notice(target, `${session.name} sent you a friend request`);
    this.send(ws, { t: "notice", message: `Friend request sent to ${them.name}` });
    await this.pushState(session.uuid);
    await this.pushState(target);
  }

  private async accept(ws: WebSocket, session: Session, name: string): Promise<void> {
    const target = await this.uuidOf(name);
    const me = await this.account(session.uuid);
    const them = target ? await this.account(target) : null;
    if (!target || !me || !them || !me.incoming.includes(target)) {
      return;
    }

    if (me.friends.length >= MAX_FRIENDS || them.friends.length >= MAX_FRIENDS) {
      this.send(ws, { t: "error", message: "Too many friends" });
      return;
    }

    me.incoming = without(me.incoming, target);
    me.outgoing = without(me.outgoing, target);
    them.incoming = without(them.incoming, session.uuid);
    them.outgoing = without(them.outgoing, session.uuid);
    me.friends.push(target);
    them.friends.push(session.uuid);
    await this.save(session.uuid, me);
    await this.save(target, them);
    this.notice(target, `${session.name} accepted your friend request`);
    await this.pushState(session.uuid);
    await this.pushState(target);
  }

  private async dropRequest(receiver: string | null, sender: string | null): Promise<void> {
    if (!receiver || !sender) {
      return;
    }

    const to = await this.account(receiver);
    const from = await this.account(sender);
    if (!to || !from || !to.incoming.includes(sender)) {
      return;
    }

    to.incoming = without(to.incoming, sender);
    from.outgoing = without(from.outgoing, receiver);
    await this.save(receiver, to);
    await this.save(sender, from);
    await this.pushState(receiver);
    await this.pushState(sender);
  }

  private async remove(session: Session, name: string): Promise<void> {
    const target = await this.uuidOf(name);
    const me = await this.account(session.uuid);
    const them = target ? await this.account(target) : null;
    if (!target || !me || !them || !me.friends.includes(target)) {
      return;
    }

    me.friends = without(me.friends, target);
    them.friends = without(them.friends, session.uuid);
    await this.save(session.uuid, me);
    await this.save(target, them);
    this.clearFollow(session.uuid, target);
    this.clearFollow(target, session.uuid);
    await this.pushState(session.uuid);
    await this.pushState(target);
  }

  private clearFollow(follower: string, followed: string): void {
    for (const socket of this.sockets(follower)) {
      const session = socket.deserializeAttachment() as Session;
      if (session.follows === followed) {
        session.follows = null;
        socket.serializeAttachment(session);
      }
    }
  }

  private async song(ws: WebSocket, session: Session, message: Record<string, unknown>): Promise<void> {
    const now = Date.now();
    if (now - session.lastSong < SONG_INTERVAL_MS) {
      return;
    }

    const title = text(message.title);
    const trackId = text(message.trackId);
    session.song = title
      ? {
          title,
          artist: text(message.artist),
          trackId: SPOTIFY_ID.test(trackId) ? trackId : "",
          positionMs: number(message.positionMs),
          durationMs: number(message.durationMs),
          playing: message.playing === true,
          at: now,
        }
      : null;
    session.lastSong = now;
    ws.serializeAttachment(session);
    const account = await this.account(session.uuid);
    if (!account) {
      return;
    }

    for (const friend of account.friends) {
      for (const socket of this.sockets(friend)) {
        this.send(socket, { t: "song", uuid: session.uuid, song: this.songView(session.song) });
      }
    }
  }

  private async follow(ws: WebSocket, session: Session, name: string): Promise<void> {
    const target = await this.uuidOf(name);
    const me = await this.account(session.uuid);
    if (!target || !me || !me.friends.includes(target) || this.sockets(target).length === 0) {
      this.send(ws, { t: "error", message: `${name} isn't online` });
      return;
    }

    const previous = session.follows;
    session.follows = target;
    ws.serializeAttachment(session);
    if (previous && previous !== target) {
      await this.pushState(previous);
    }

    if (previous !== target) {
      this.notice(target, `${session.name} is listening along with you`);
    }

    await this.pushState(target);
    await this.pushState(session.uuid);
  }

  private async unfollow(ws: WebSocket, session: Session): Promise<void> {
    const previous = session.follows;
    session.follows = null;
    ws.serializeAttachment(session);
    if (previous) {
      await this.pushState(previous);
    }
    await this.pushState(session.uuid);
  }

  private async pushState(uuid: string): Promise<void> {
    const sockets = this.sockets(uuid);
    if (sockets.length === 0) {
      return;
    }

    const account = await this.account(uuid);
    if (!account) {
      return;
    }

    const friends = [];
    for (const friend of account.friends) {
      const other = await this.account(friend);
      if (!other) {
        continue;
      }

      const live = this.sockets(friend).map((socket) => socket.deserializeAttachment() as Session)[0];
      friends.push({
        uuid: friend,
        name: other.name,
        online: live !== undefined,
        song: live ? this.songView(live.song) : null,
        listening: live?.follows === uuid,
      });
    }

    const incoming = await this.names(account.incoming);
    const outgoing = await this.names(account.outgoing);
    for (const socket of sockets) {
      const session = socket.deserializeAttachment() as Session;
      this.send(socket, { t: "state", friends, incoming, outgoing, following: session.follows });
    }
  }

  private songView(song: Song | null): Record<string, unknown> | null {
    if (!song) {
      return null;
    }

    return {
      title: song.title,
      artist: song.artist,
      trackId: song.trackId,
      positionMs: song.positionMs,
      durationMs: song.durationMs,
      playing: song.playing,
      ageMs: Date.now() - song.at,
    };
  }

  private async names(uuids: string[]): Promise<string[]> {
    const out: string[] = [];
    for (const uuid of uuids) {
      const account = await this.account(uuid);
      if (account) {
        out.push(account.name);
      }
    }
    return out;
  }

  private sockets(uuid: string): WebSocket[] {
    return this.ctx.getWebSockets().filter((socket) => {
      const session = socket.deserializeAttachment() as Session | null;
      return session !== null && session.uuid === uuid;
    });
  }

  private async account(uuid: string): Promise<Account | null> {
    return (await this.ctx.storage.get<Account>(`u:${uuid}`)) ?? null;
  }

  private async save(uuid: string, account: Account): Promise<void> {
    await this.ctx.storage.put(`u:${uuid}`, account);
  }

  private async uuidOf(name: string): Promise<string | null> {
    if (!NAME.test(name)) {
      return null;
    }
    return (await this.ctx.storage.get<string>(`n:${name.toLowerCase()}`)) ?? null;
  }

  private notice(uuid: string, message: string): void {
    for (const socket of this.sockets(uuid)) {
      this.send(socket, { t: "notice", message });
    }
  }

  private fail(ws: WebSocket, message: string): void {
    this.send(ws, { t: "error", message });
    ws.close(4001, message.slice(0, 120));
  }

  private send(ws: WebSocket, payload: Record<string, unknown>): void {
    try {
      ws.send(JSON.stringify(payload));
    } catch {
      ws.serializeAttachment(null);
    }
  }
}
