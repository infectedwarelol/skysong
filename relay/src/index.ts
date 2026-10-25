import { DurableObject } from "cloudflare:workers";
import { MOJANG_PLAYER_KEYS } from "./mojang-keys";

interface Env {
  HUB: DurableObjectNamespace<Hub>;
  MOJANG_KEYS?: string;
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
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const B64 = /^[A-Za-z0-9+/]+={0,2}$/;
const MAX_KEY_LIFETIME_MS = 14 * 86_400_000;

function bytes(b64: string): Uint8Array {
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

function sessionOf(ws: WebSocket): Session | null {
  const value = ws.deserializeAttachment() as Partial<Session> | null;
  return value && typeof value.uuid === "string" ? (value as Session) : null;
}

function text(value: unknown): string {
  return typeof value === "string" ? value.replace(/\p{Cc}/gu, "").slice(0, MAX_TEXT) : "";
}

function number(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? Math.min(Math.floor(value), 86_400_000) : 0;
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
    const nonce = Array.from(crypto.getRandomValues(new Uint8Array(24)), (b) => b.toString(16).padStart(2, "0")).join("");
    pair[1].serializeAttachment({ nonce });
    pair[1].send(JSON.stringify({ t: "challenge", nonce }));
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

    const session = sessionOf(ws);
    if (!session) {
      const pending = ws.deserializeAttachment() as { nonce?: string } | null;
      if (message.t === "hello" && pending?.nonce) {
        await this.hello(ws, message, pending.nonce);
      }
      return;
    }

    const name = UUID.test(text(message.uuid)) ? text(message.uuid) : "";
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
    const session = sessionOf(ws);
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

  private async hello(ws: WebSocket, message: Record<string, unknown>, nonce: string): Promise<void> {
    const realName = text(message.name);
    const uuid = text(message.uuid);
    const key = typeof message.key === "string" ? message.key : "";
    const keySignature = typeof message.keySignature === "string" ? message.keySignature : "";
    const signature = typeof message.signature === "string" ? message.signature : "";
    const expiresAt = typeof message.expiresAt === "number" ? message.expiresAt : 0;
    if (!NAME.test(realName) || !UUID.test(uuid) || !B64.test(key) || !B64.test(keySignature) || !B64.test(signature)) {
      this.fail(ws, "Invalid sign-in");
      return;
    }

    const now = Date.now();
    if (expiresAt <= now || expiresAt > now + MAX_KEY_LIFETIME_MS) {
      this.fail(ws, "Your Minecraft chat key expired, restart Minecraft");
      return;
    }

    try {
      if (!(await this.signedByMojang(uuid, expiresAt, bytes(key), bytes(keySignature)))) {
        this.fail(ws, "Minecraft account could not be verified");
        return;
      }

      const playerKey = await crypto.subtle.importKey("spki", bytes(key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
      if (!(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", playerKey, bytes(signature), new TextEncoder().encode(nonce)))) {
        this.fail(ws, "Minecraft account could not be verified");
        return;
      }
    } catch {
      this.fail(ws, "Minecraft account could not be verified");
      return;
    }

    const account = (await this.account(uuid)) ?? { name: realName, friends: [], incoming: [], outgoing: [] };
    account.name = realName;
    await this.ctx.storage.put(`u:${uuid}`, account);
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

  private async signedByMojang(uuid: string, expiresAt: number, key: Uint8Array, keySignature: Uint8Array): Promise<boolean> {
    const payload = new Uint8Array(24 + key.length);
    const view = new DataView(payload.buffer);
    const hex = uuid.replaceAll("-", "");
    view.setBigUint64(0, BigInt("0x" + hex.slice(0, 16)));
    view.setBigUint64(8, BigInt("0x" + hex.slice(16)));
    view.setBigUint64(16, BigInt(expiresAt));
    payload.set(key, 24);
    const trusted = this.env.MOJANG_KEYS ? this.env.MOJANG_KEYS.split(",") : MOJANG_PLAYER_KEYS;
    for (const encoded of trusted) {
      const mojang = await crypto.subtle.importKey("spki", bytes(encoded), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" }, false, ["verify"]);
      if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", mojang, keySignature, payload)) {
        return true;
      }
    }
    return false;
  }

  private async add(ws: WebSocket, session: Session, name: string): Promise<void> {
    const target = await this.uuidOf(name);
    if (!target) {
      this.send(ws, { t: "error", message: "That player hasn't turned on Skysong party yet" });
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
      this.send(ws, { t: "error", message: "That friend isn't online" });
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

  private async names(uuids: string[]): Promise<{ uuid: string; name: string }[]> {
    const out: { uuid: string; name: string }[] = [];
    for (const uuid of uuids) {
      const account = await this.account(uuid);
      if (account) {
        out.push({ uuid, name: account.name });
      }
    }
    return out;
  }

  private sockets(uuid: string): WebSocket[] {
    return this.ctx.getWebSockets().filter((socket) => {
      const session = sessionOf(socket);
      return session !== null && session.uuid === uuid;
    });
  }

  private async account(uuid: string): Promise<Account | null> {
    return (await this.ctx.storage.get<Account>(`u:${uuid}`)) ?? null;
  }

  private async save(uuid: string, account: Account): Promise<void> {
    await this.ctx.storage.put(`u:${uuid}`, account);
  }

  private async uuidOf(id: string): Promise<string | null> {
    return UUID.test(id) && (await this.account(id)) ? id : null;
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
