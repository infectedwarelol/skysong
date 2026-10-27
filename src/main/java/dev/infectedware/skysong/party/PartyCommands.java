package dev.infectedware.skysong.party;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.module.ModuleRegistry;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class PartyCommands {
    public static final String ROOT = "sparty";
    private static final String PREFIX = "§b[Skysong] §f";

    private PartyCommands() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(build(ROOT));
            dispatcher.register(ClientCommandManager.literal("skysong").then(build("party")));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        return ClientCommandManager.literal(name)
            .executes(ctx -> help(ctx.getSource()))
            .then(ClientCommandManager.literal("help").executes(ctx -> help(ctx.getSource())))
            .then(ClientCommandManager.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
            .then(ClientCommandManager.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
            .then(ClientCommandManager.literal("list").executes(ctx -> list(ctx.getSource())))
            .then(ClientCommandManager.literal("stop").executes(ctx -> {
                PartyClient.unfollow();
                return 1;
            }))
            .then(withPlayer("invite", PartyCommands::playersHere, PartyClient::invite))
            .then(withPlayer("accept", () -> names(PartyClient.incoming()), player -> byName(PartyClient.incoming(), player, PartyClient::accept)))
            .then(withPlayer("decline", () -> names(PartyClient.incoming()), player -> byName(PartyClient.incoming(), player, PartyClient::decline)))
            .then(withPlayer("cancel", () -> names(PartyClient.outgoing()), player -> byName(PartyClient.outgoing(), player, PartyClient::cancel)))
            .then(withPlayer("remove", PartyCommands::memberNames, player -> member(player, PartyClient::remove)))
            .then(withPlayer("listen", PartyCommands::memberNames, player -> member(player, PartyClient::follow)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> withPlayer(String name, Supplier<List<String>> suggestions, Consumer<String> run) {
        SuggestionProvider<FabricClientCommandSource> suggest = (ctx, builder) -> SharedSuggestionProvider.suggest(suggestions.get(), builder);
        return ClientCommandManager.literal(name)
            .then(ClientCommandManager.argument("player", StringArgumentType.word()).suggests(suggest).executes(ctx -> {
                if (!ready(ctx)) {
                    return 0;
                }

                run.accept(StringArgumentType.getString(ctx, "player"));
                return 1;
            }));
    }

    private static boolean ready(CommandContext<FabricClientCommandSource> ctx) {
        if (!ModSettings.partyEnabled) {
            ctx.getSource().sendError(Component.literal("Party is off: /" + ROOT + " on"));
            return false;
        }

        if (!PartyClient.isConnected()) {
            ctx.getSource().sendError(Component.literal("Not connected yet: " + PartyClient.status()));
            return false;
        }

        return true;
    }

    private static void byName(List<PartyClient.Person> people, String name, Consumer<String> run) {
        PartyClient.Person p = PartyClient.byName(people, name);
        if (p == null) {
            say("No invite from or to " + name);
        } else {
            run.accept(p.uuid());
        }
    }

    private static void member(String name, Consumer<String> run) {
        PartyClient.Friend f = PartyClient.friendByName(name);
        if (f == null) {
            say(name + " isn't in your party");
        } else {
            run.accept(f.uuid());
        }
    }

    private static int setEnabled(FabricClientCommandSource source, boolean on) {
        ClientModule party = ModuleRegistry.byName("Party");
        if (party != null) {
            party.setOn(on);
        } else {
            ModSettings.partyEnabled = on;
            ModConfig.save();
        }

        source.sendFeedback(Component.literal(PREFIX + "Party " + (on ? "on" : "off")));
        return 1;
    }

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(PREFIX + "Party: §7" + PartyClient.status()));
        String r = "§7/" + ROOT + " ";
        source.sendFeedback(Component.literal(r + "invite <player>§8 - invite someone by name"));
        source.sendFeedback(Component.literal(r + "accept|decline <player>§8 - answer an invite"));
        source.sendFeedback(Component.literal(r + "cancel <player>§8 - take back an invite"));
        source.sendFeedback(Component.literal(r + "listen <player>§8 - listen along, §7/" + ROOT + " stop§8 to stop"));
        source.sendFeedback(Component.literal(r + "remove <player>§8 - remove from your party"));
        source.sendFeedback(Component.literal(r + "list§8 - members and invites, §7on|off"));
        return 1;
    }

    private static int list(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(PREFIX + "Party: §7" + PartyClient.status()));
        List<PartyClient.Friend> friends = PartyClient.friends();
        if (friends.isEmpty()) {
            source.sendFeedback(Component.literal("§7No party members yet: /" + ROOT + " invite <player>"));
        }

        for (PartyClient.Friend f : friends) {
            String song = !f.online() ? "§8offline" : f.song() == null ? "§7not sharing a song" : "§a" + f.song().label();
            source.sendFeedback(Component.literal(" §f" + f.name() + " §8· " + song + (PartyClient.isFollowing(f.uuid()) ? " §b(listening)" : "")));
        }

        for (PartyClient.Person p : PartyClient.incoming()) {
            source.sendFeedback(inviteLine(p.name()));
        }

        for (PartyClient.Person p : PartyClient.outgoing()) {
            source.sendFeedback(Component.literal(" §7Invite sent to §f" + p.name()));
        }

        return 1;
    }

    static void announceInvite(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(inviteLine(name), false);
        }
    }

    private static Component inviteLine(String name) {
        MutableComponent line = Component.literal(PREFIX + name + " invited you to their party ");
        line.append(button("[Accept]", ChatFormatting.GREEN, "/" + ROOT + " accept " + name));
        line.append(Component.literal(" "));
        line.append(button("[Decline]", ChatFormatting.RED, "/" + ROOT + " decline " + name));
        return line;
    }

    private static Component button(String label, ChatFormatting color, String command) {
        return Component.literal(label)
            .withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(PREFIX + text), false);
            }
        });
    }

    private static List<String> playersHere() {
        return PartyClient.playersHere().stream().map(PartyClient.Candidate::name).toList();
    }

    private static List<String> memberNames() {
        return PartyClient.friends().stream().map(PartyClient.Friend::name).toList();
    }

    private static List<String> names(List<PartyClient.Person> people) {
        return people.stream().map(PartyClient.Person::name).toList();
    }
}
