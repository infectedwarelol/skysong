package dev.infectedware.skysong.cosmetics;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.infectedware.skysong.ModSettings;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class Emotes {
    public static final String ROOT = "semote";
    public static final String[] NAMES = new String[]{"Wave", "Dab", "T-Pose", "Clap", "Salute", "Facepalm", "Cheer", "Zombie", "Shrug", "Point", "Dance"};
    private static final float[] SECONDS = new float[]{2.4F, 1.6F, 0.0F, 2.4F, 1.8F, 2.2F, 2.4F, 0.0F, 1.4F, 2.0F, 0.0F};
    private static final float BLEND = 0.18F;
    private static final double MOVE_STOP_SQ = 0.09;
    private static int playing = -1;
    private static long startedAt;
    private static long stoppingAt = -1L;
    private static Vec3 startPos;
    private static boolean changedCamera;

    private Emotes() {
    }

    static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(command(ROOT));
            dispatcher.register(ClientCommandManager.literal("skysong").then(command("emote")));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommandManager.literal(name)
            .executes(ctx -> {
                ctx.getSource().sendFeedback(Component.literal("§b[Skysong] §fEmotes: §7" + String.join(", ", Arrays.stream(NAMES).map(Emotes::id).toList())));
                ctx.getSource().sendFeedback(Component.literal("§7/" + ROOT + " <emote>§8 to play, §7/" + ROOT + " stop§8 to stop"));
                return 1;
            })
            .then(ClientCommandManager.literal("stop").executes(ctx -> {
                stop();
                return 1;
            }))
            .then(
                ClientCommandManager.argument("emote", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(NAMES).map(Emotes::id), builder))
                    .executes(ctx -> {
                        String wanted = StringArgumentType.getString(ctx, "emote");
                        for (int i = 0; i < NAMES.length; i++) {
                            if (id(NAMES[i]).equalsIgnoreCase(wanted)) {
                                if (!ModSettings.cosmeticEmotes) {
                                    ctx.getSource().sendError(Component.literal("Turn on Emotes in the Cosmetics tab first"));
                                    return 0;
                                }

                                play(i);
                                return 1;
                            }
                        }

                        ctx.getSource().sendError(Component.literal("No emote called " + wanted));
                        return 0;
                    })
            );
    }

    private static String id(String name) {
        return name.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
    }

    public static void play(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || index < 0 || index >= NAMES.length) {
            return;
        }

        playing = index;
        startedAt = System.currentTimeMillis();
        stoppingAt = -1L;
        startPos = mc.player.position();
        if (ModSettings.cosmeticEmoteThirdPerson && mc.options.getCameraType().isFirstPerson()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            changedCamera = true;
        }
    }

    public static void toggleSelected() {
        if (playing >= 0 && stoppingAt < 0L) {
            stop();
        } else {
            play(Math.floorMod(ModSettings.cosmeticEmote, NAMES.length));
        }
    }

    public static void stop() {
        if (playing >= 0 && stoppingAt < 0L) {
            stoppingAt = System.currentTimeMillis();
        }
    }

    public static boolean isPlaying() {
        return playing >= 0;
    }

    static void tick(Minecraft mc) {
        if (playing < 0) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || !ModSettings.cosmeticEmotes) {
            finish(mc);
            return;
        }

        long now = System.currentTimeMillis();
        float duration = SECONDS[playing] / Math.max(0.1F, ModSettings.cosmeticEmoteSpeed);
        if (stoppingAt < 0L && duration > 0.0F && (now - startedAt) / 1000.0F >= duration) {
            stoppingAt = now;
        }

        if (stoppingAt < 0L && startPos != null && player.position().subtract(startPos).horizontalDistanceSqr() > MOVE_STOP_SQ) {
            stoppingAt = now;
        }

        if (stoppingAt >= 0L && (now - stoppingAt) / 1000.0F >= BLEND) {
            finish(mc);
        }
    }

    private static void finish(Minecraft mc) {
        playing = -1;
        stoppingAt = -1L;
        if (changedCamera && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        changedCamera = false;
    }

    public static void apply(PlayerModel model, AvatarRenderState state) {
        int emote = playing;
        if (emote < 0 || !ModSettings.cosmeticEmotes || !Cosmetics.wears(state.id)) {
            return;
        }

        long now = System.currentTimeMillis();
        float t = (now - startedAt) / 1000.0F * ModSettings.cosmeticEmoteSpeed;
        float w = Math.min(1.0F, (now - startedAt) / 1000.0F / BLEND);
        if (stoppingAt >= 0L) {
            w = Math.min(w, 1.0F - Math.min(1.0F, (now - stoppingAt) / 1000.0F / BLEND));
        }

        if (w <= 0.0F) {
            return;
        }

        float pi = (float)Math.PI;
        switch (emote) {
            case 0 -> set(model.rightArm, 0.0F, 0.0F, 2.7F + Mth.sin(t * 12.0F) * 0.35F, w);
            case 1 -> {
                set(model.head, 0.6F, 0.55F, 0.0F, w);
                set(model.rightArm, -0.25F, 0.0F, 2.25F, w);
                set(model.leftArm, -1.9F, 0.85F, 0.0F, w);
            }
            case 2 -> {
                set(model.rightArm, 0.0F, 0.0F, pi / 2.0F, w);
                set(model.leftArm, 0.0F, 0.0F, -pi / 2.0F, w);
                set(model.rightLeg, 0.0F, 0.0F, 0.0F, w);
                set(model.leftLeg, 0.0F, 0.0F, 0.0F, w);
            }
            case 3 -> {
                float c = Math.abs(Mth.sin(t * 9.0F));
                set(model.rightArm, -1.35F, -(0.25F + 0.27F * c), 0.0F, w);
                set(model.leftArm, -1.35F, 0.25F + 0.27F * c, 0.0F, w);
            }
            case 4 -> set(model.rightArm, -2.2F, -0.55F, 0.25F, w);
            case 5 -> {
                set(model.head, 0.4F, 0.0F, 0.0F, w);
                set(model.rightArm, -2.0F, -0.65F, 0.1F, w);
            }
            case 6 -> {
                float bounce = Mth.sin(t * 10.0F) * 0.15F;
                set(model.rightArm, -2.85F + bounce, 0.0F, 0.35F, w);
                set(model.leftArm, -2.85F - bounce, 0.0F, -0.35F, w);
            }
            case 7 -> {
                float sway = Mth.sin(t * 2.0F) * 0.05F;
                set(model.rightArm, -pi / 2.0F + sway, -0.05F, 0.0F, w);
                set(model.leftArm, -pi / 2.0F - sway, 0.05F, 0.0F, w);
            }
            case 8 -> {
                float lift = Math.min(1.0F, t * 4.0F);
                set(model.rightArm, -0.35F, 0.0F, 0.55F * lift, w);
                set(model.leftArm, -0.35F, 0.0F, -0.55F * lift, w);
                set(model.head, model.head.xRot, model.head.yRot, 0.18F * lift, w);
            }
            case 9 -> set(model.rightArm, -pi / 2.0F + model.head.xRot, model.head.yRot, 0.0F, w);
            case 10 -> {
                float beat = Mth.sin(t * 6.0F);
                set(model.rightArm, 0.0F, 0.0F, 1.2F + beat, w);
                set(model.leftArm, 0.0F, 0.0F, -1.2F + beat, w);
                set(model.rightLeg, 0.0F, 0.0F, beat * 0.2F, w);
                set(model.leftLeg, 0.0F, 0.0F, beat * 0.2F, w);
                set(model.head, model.head.xRot, model.head.yRot, beat * 0.2F, w);
            }
            default -> {
            }
        }
    }

    private static void set(ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }
}
