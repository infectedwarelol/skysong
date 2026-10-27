package dev.infectedware.skysong.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.theme.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

public class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final Identifier AURA_TEXTURE = Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private static final int FULL_BRIGHT = 15728880;
    private static final ItemStack[] PET_ITEMS = new ItemStack[]{
        ItemStack.EMPTY,
        new ItemStack(Items.CREEPER_HEAD),
        new ItemStack(Items.DRAGON_HEAD),
        new ItemStack(Items.WITHER_SKELETON_SKULL),
        new ItemStack(Items.PIGLIN_HEAD),
        new ItemStack(Items.SLIME_BLOCK),
        new ItemStack(Items.ENDER_EYE),
        new ItemStack(Items.AMETHYST_CLUSTER)
    };
    private static final float[] HAT_HEIGHTS = new float[]{0.42F, 0.27F, 0.62F, 0.46F, 0.21F, 0.5F, 0.3F, 0.34F};
    private static final ItemStackRenderState[] PET_STATES = new ItemStackRenderState[]{
        new ItemStackRenderState(), new ItemStackRenderState(), new ItemStackRenderState()
    };
    private static final float[][] PET_SLOTS = new float[][]{{0.68F, -0.42F, 0.18F}, {-0.68F, -0.42F, 0.18F}, {0.32F, -0.8F, 0.48F}};
    private static final float[][] PET_SLOTS_FIRST_PERSON = new float[][]{{0.95F, -0.1F, -1.45F}, {-0.95F, -0.1F, -1.45F}, {1.55F, -0.3F, -1.9F}};
    private static final int BANNER_COLUMNS = 8;
    private static final int BANNER_ROWS = 10;
    private static final float[] BANNER_HEM = new float[]{0.0F, 0.09F, 0.02F, 0.13F, 0.04F, 0.11F, 0.0F, 0.08F, 0.03F};
    private static final float[] BANNER_PLEAT = pleats();
    private static final float[] BANNER_PREVIOUS = new float[(BANNER_COLUMNS + 1) * 3];
    private static final float[] BANNER_CURRENT = new float[(BANNER_COLUMNS + 1) * 3];
    private static final int CAPELET_COLUMNS = 10;
    private static final int CAPELET_ROWS = 4;
    private static final float[] CAPELET_DROP = capeletDrops();
    private static final int CAPELET_MOTES = 6;
    private static final float[] KOI_Z = new float[]{0.09F, 0.06F, 0.02F, -0.02F, -0.06F, -0.09F, -0.12F};
    private static final float[] KOI_RADIUS = new float[]{0.03F, 0.048F, 0.052F, 0.045F, 0.032F, 0.018F, 0.004F};
    private static final int[] KOI_PATCH = new int[]{0xFFF7F3EC, 0xFFF26B1D, 0xFFF26B1D, 0xFFF7F3EC, 0xFFE8541A, 0xFFF7F3EC};
    private static final int KOI_BELLY = 0xFFF7F3EC;
    private static final int KOI_FIN = 0xC0F26B1D;
    private static final float[] KOI_SIDE_X = new float[]{0.0F, 1.0F, 0.0F, -1.0F};
    private static final float[] KOI_SIDE_Y = new float[]{-1.0F, 0.0F, 0.8F, 0.0F};
    private static final float[] KOI_RING = new float[KOI_Z.length * 4 * 3];
    private static final float[] RIBBON_X = new float[]{-0.17F, 0.0F, 0.17F};
    private static final float[] RIBBON_HALF_WIDTH = new float[]{0.07F, 0.09F, 0.07F};
    private static final float[] RIBBON_LENGTH = new float[]{0.86F, 1.02F, 0.86F};
    private static final int RIBBON_SEGMENTS = 12;
    private static final int SHARD_ROWS = 6;
    private static final int SHARD_COLUMNS = 5;
    private static final int TENTACLES = 5;
    private static final int TENTACLE_SEGMENTS = 6;
    private final PlayerModel auraWide;
    private final PlayerModel auraSlim;

    public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
        CubeDeformation inflate = new CubeDeformation(0.55F);
        this.auraWide = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(inflate, false), 64, 64).bakeRoot(), false);
        this.auraSlim = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(inflate, true), 64, 64).bakeRoot(), true);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !Cosmetics.wears(state.id)) {
            return;
        }

        float age = state.ageInTicks;
        if (ModSettings.cosmeticAura && ModSettings.cosmeticAuraStyle != 1) {
            this.aura(pose, collector, light, state, age);
        }

        if (ModSettings.cosmeticWings && !(ModSettings.cosmeticWingsHideElytra && state.chestEquipment.has(DataComponents.GLIDER))) {
            pose.pushPose();
            this.getParentModel().root().translateAndRotate(pose);
            this.getParentModel().body.translateAndRotate(pose);
            wings(pose, collector, light, age, state.isFallFlying, state.isCrouching);
            pose.popPose();
        }

        if (ModSettings.cosmeticCape && Cosmetics.shapedCape() && !state.chestEquipment.has(DataComponents.GLIDER)) {
            pose.pushPose();
            this.getParentModel().root().translateAndRotate(pose);
            this.getParentModel().body.translateAndRotate(pose);
            shapedCape(pose, collector, light, state, age);
            pose.popPose();
        }

        boolean hat = ModSettings.cosmeticHat && !(ModSettings.cosmeticHatHideHelmet && !state.headEquipment.isEmpty());
        if (hat || ModSettings.cosmeticHalo) {
            pose.pushPose();
            this.getParentModel().root().translateAndRotate(pose);
            this.getParentModel().head.translateAndRotate(pose);
            if (hat) {
                hat(pose, collector, light);
            }

            if (ModSettings.cosmeticHalo) {
                float lift = hat ? Math.max(0.0F, HAT_HEIGHTS[Math.floorMod(ModSettings.cosmeticHatStyle, HAT_HEIGHTS.length)] * ModSettings.cosmeticHatSize - 0.05F) : 0.0F;
                halo(pose, collector, age, lift);
            }

            pose.popPose();
        }

        if (ModSettings.cosmeticPet && state.deathTime <= 0.0F) {
            pose.pushPose();
            this.getParentModel().root().translateAndRotate(pose);
            pets(pose, collector, light, age, false);
            pose.popPose();
        }
    }

    private void aura(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float age) {
        PlayerModel model = state.skin.model() == PlayerModelType.SLIM ? this.auraSlim : this.auraWide;
        float speed = ModSettings.cosmeticAuraSpeed;
        int tint = auraTint(age);
        collector.order(1)
            .submitModel(
                model,
                state,
                pose,
                RenderTypes.energySwirl(AURA_TEXTURE, age * 0.01F * speed % 1.0F, age * 0.01F * speed % 1.0F),
                light,
                OverlayTexture.NO_OVERLAY,
                tint,
                null,
                state.outlineColor,
                null
            );
    }

    public static int auraTint(float age) {
        int c = Cosmetics.color(ModSettings.cosmeticAuraColorMode, ModSettings.cosmeticAuraColor, ModSettings.cosmeticAuraColor2, Cosmetics.reduceMotion() ? 0.5F : 0.5F + 0.5F * Mth.sin(age * 0.05F));
        float s = Math.clamp(ModSettings.cosmeticAuraStrength, 0.0F, 1.0F);
        int r = Math.round((c >> 16 & 0xFF) * s);
        int g = Math.round((c >> 8 & 0xFF) * s);
        int b = Math.round((c & 0xFF) * s);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void halo(PoseStack pose, SubmitNodeCollector collector, float age, float lift) {
        float size = ModSettings.cosmeticHaloSize;
        int mode = ModSettings.cosmeticHaloColorMode;
        int c1 = ModSettings.cosmeticHaloColor;
        int c2 = ModSettings.cosmeticHaloColor2;
        int style = ModSettings.cosmeticHaloStyle;
        float spin = ModSettings.cosmeticHaloSpin ? age * 2.5F : 0.0F;
        float bob = Mth.sin(age * 0.08F) * 0.015F;
        pose.pushPose();
        if (style == 3) {
            pose.translate(0.0F, -0.42F + bob - ModSettings.cosmeticHaloHeight, 0.22F);
            pose.mulPose(Axis.XP.rotationDegrees(-70.0F));
        } else {
            pose.translate(0.0F, -0.68F + bob - ModSettings.cosmeticHaloHeight - lift, 0.0F);
        }

        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(size, size, size);
        RenderType type = RenderTypes.entityTranslucentEmissive(CosmeticTextures.white());
        collector.submitCustomGeometry(pose, type, (p, out) -> {
            Geo geo = new Geo(p, out, FULL_BRIGHT);
            int segments = 40;
            geo.torus(0.2F, 0.02F, segments, 6, i -> Cosmetics.color(mode, c1, c2, ring(i, segments)));
            if (ModSettings.cosmeticHaloGlow) {
                geo.torus(0.2F, 0.05F, segments, 8, i -> Cosmetics.withAlpha(Cosmetics.color(mode, c1, c2, ring(i, segments)), 0.22F));
            }

            if (style == 1) {
                p.rotate(Axis.XP.rotationDegrees(14.0F));
                p.translate(0.0F, -0.06F, 0.0F);
                geo.torus(0.15F, 0.016F, segments, 6, i -> Cosmetics.color(mode, c1, c2, 1.0F - ring(i, segments)));
            } else if (style == 2) {
                int rays = 12;
                for (int i = 0; i < rays; i++) {
                    float a = Mth.TWO_PI * i / rays;
                    float cos = Mth.cos(a);
                    float sin = Mth.sin(a);
                    float len = i % 2 == 0 ? 0.12F : 0.07F;
                    int c = Cosmetics.color(mode, c1, c2, (float)i / rays);
                    Vector3f base0 = new Vector3f(cos * 0.2F - sin * 0.018F, 0.0F, sin * 0.2F + cos * 0.018F);
                    Vector3f base1 = new Vector3f(cos * 0.2F + sin * 0.018F, 0.0F, sin * 0.2F - cos * 0.018F);
                    Vector3f tip = new Vector3f(cos * (0.2F + len * 0.35F), -len, sin * (0.2F + len * 0.35F));
                    geo.tri(base0, base1, tip, c);
                }
            }
        });
        pose.popPose();
    }

    private static float ring(int i, int segments) {
        float t = (float)i / segments;
        return t < 0.5F ? t * 2.0F : 2.0F - t * 2.0F;
    }

    private static void hat(PoseStack pose, SubmitNodeCollector collector, int light) {
        int style = ModSettings.cosmeticHatStyle;
        int mode = ModSettings.cosmeticHatColorMode;
        int main = Cosmetics.color(mode, ModSettings.cosmeticHatColor, ModSettings.cosmeticHatColor2, 0.0F);
        int accent = Cosmetics.color(mode, ModSettings.cosmeticHatColor, ModSettings.cosmeticHatColor2, 1.0F);
        if (mode == 2) {
            accent = 0xFF000000 | ModSettings.cosmeticHatColor2 & 0xFFFFFF;
        }

        int accentColor = accent;
        float size = ModSettings.cosmeticHatSize;
        pose.pushPose();
        pose.translate(0.0F, -0.5F, 0.0F);
        pose.scale(size, size, size);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, light);
            switch (style) {
                case 1 -> crown(geo, main, accentColor);
                case 2 -> witchHat(geo, main, accentColor);
                case 3 -> partyHat(p, geo, main, accentColor);
                case 4 -> ears(geo, main, accentColor, false);
                case 5 -> ears(geo, main, accentColor, true);
                case 6 -> horns(p, geo, main, accentColor);
                case 7 -> conicalHat(geo, main, accentColor);
                default -> topHat(geo, main, accentColor);
            }
        });
        pose.popPose();
    }

    private static void topHat(Geo geo, int main, int accent) {
        geo.frustum(0.36F, 0.36F, -0.03F, -0.055F, 24, main, true, true);
        geo.frustum(0.21F, 0.22F, -0.055F, -0.42F, 24, main, false, true);
        geo.frustum(0.218F, 0.222F, -0.06F, -0.13F, 24, accent, false, false);
    }

    private static void conicalHat(Geo geo, int main, int accent) {
        int shade = Colors.darken(main, 0.82F);
        Vector3f tip = new Vector3f(0.0F, -0.34F, 0.0F);
        geo.cone(0.5F, -0.02F, tip, 32, i -> i % 2 == 0 ? main : shade, false);
        geo.frustum(0.5F, 0.47F, -0.02F, -0.045F, 32, accent, false, false);
        geo.frustum(0.26F, 0.26F, -0.01F, -0.12F, 20, shade, false, false);
        for (int ring = 1; ring <= 2; ring++) {
            float r = 0.5F - ring * 0.14F;
            float y = -0.02F - 0.32F * (1.0F - r / 0.5F) - 0.004F;
            geo.frustum(r + 0.012F, r - 0.012F, y + 0.008F, y - 0.008F, 32, Colors.darken(main, 0.7F), false, false);
        }

        geo.ball(0.0F, -0.35F, 0.0F, 0.03F, accent);
    }

    private static void crown(Geo geo, int main, int accent) {
        geo.frustum(0.25F, 0.26F, -0.02F, -0.13F, 24, main, false, false);
        int spikes = 8;
        for (int i = 0; i < spikes; i++) {
            float a0 = Mth.TWO_PI * i / spikes;
            float a1 = Mth.TWO_PI * (i + 1) / spikes;
            float am = (a0 + a1) / 2.0F;
            geo.tri(
                new Vector3f(Mth.cos(a0) * 0.26F, -0.13F, Mth.sin(a0) * 0.26F),
                new Vector3f(Mth.cos(a1) * 0.26F, -0.13F, Mth.sin(a1) * 0.26F),
                new Vector3f(Mth.cos(am) * 0.27F, -0.26F, Mth.sin(am) * 0.27F),
                main
            );
            geo.cube(Mth.cos(am) * 0.262F, -0.075F, Mth.sin(am) * 0.262F, 0.022F, accent);
            geo.cube(Mth.cos(am) * 0.27F, -0.265F, Mth.sin(am) * 0.27F, 0.016F, accent);
        }
    }

    private static void witchHat(Geo geo, int main, int accent) {
        geo.frustum(0.44F, 0.44F, -0.03F, -0.05F, 28, main, true, true);
        geo.frustum(0.23F, 0.13F, -0.05F, -0.36F, 20, main, false, false);
        geo.frustum(0.235F, 0.21F, -0.055F, -0.11F, 20, accent, false, false);
        geo.cone(0.13F, -0.36F, new Vector3f(0.0F, -0.62F, 0.18F), 20, i -> main, false);
    }

    private static void partyHat(PoseStack.Pose p, Geo geo, int main, int accent) {
        p.translate(0.07F, 0.0F, 0.0F);
        p.rotate(Axis.ZP.rotationDegrees(12.0F));
        geo.cone(0.15F, -0.02F, new Vector3f(0.0F, -0.45F, 0.0F), 16, i -> (i / 2) % 2 == 0 ? main : accent, true);
        geo.ball(0.0F, -0.46F, 0.0F, 0.045F, accent);
    }

    private static void ears(Geo geo, int main, int accent, boolean bunny) {
        for (int side = -1; side <= 1; side += 2) {
            float x = side * (bunny ? 0.11F : 0.17F);
            if (bunny) {
                float tilt = side * 0.08F;
                geo.box(x - 0.055F, -0.5F, -0.03F, x + 0.055F, -0.01F, 0.03F, main);
                geo.box(x - 0.03F + tilt * 0.1F, -0.46F, -0.035F, x + 0.03F + tilt * 0.1F, -0.06F, -0.03F, accent);
            } else {
                float outer = x + side * 0.08F;
                float inner = x - side * 0.08F;
                for (float z = -0.03F; z <= 0.031F; z += 0.06F) {
                    geo.tri(new Vector3f(inner, -0.01F, z), new Vector3f(outer, -0.01F, z), new Vector3f(x + side * 0.03F, -0.21F, z), main);
                }

                geo.tri(
                    new Vector3f(inner + side * 0.03F, -0.03F, -0.035F),
                    new Vector3f(outer - side * 0.02F, -0.03F, -0.035F),
                    new Vector3f(x + side * 0.03F, -0.16F, -0.035F),
                    accent
                );
                geo.quad(
                    new Vector3f(inner, -0.01F, -0.03F), new Vector3f(x + side * 0.03F, -0.21F, -0.03F), new Vector3f(x + side * 0.03F, -0.21F, 0.03F), new Vector3f(inner, -0.01F, 0.03F), main
                );
                geo.quad(
                    new Vector3f(outer, -0.01F, -0.03F), new Vector3f(x + side * 0.03F, -0.21F, -0.03F), new Vector3f(x + side * 0.03F, -0.21F, 0.03F), new Vector3f(outer, -0.01F, 0.03F), main
                );
            }
        }
    }

    private static void horns(PoseStack.Pose p, Geo geo, int main, int accent) {
        for (int side = -1; side <= 1; side += 2) {
            PoseStack stack = new PoseStack();
            stack.last().set(p);
            stack.translate(side * 0.17F, -0.01F, -0.12F);
            float r = 0.06F;
            for (int i = 0; i < 6; i++) {
                stack.mulPose(Axis.ZP.rotationDegrees(side * 14.0F));
                stack.mulPose(Axis.XP.rotationDegrees(8.0F));
                float next = r * 0.78F;
                Geo seg = geo.on(stack.last());
                seg.frustum(r, next, 0.0F, -0.06F, 10, i < 4 ? main : accent, i == 0, i == 5);
                stack.translate(0.0F, -0.06F, 0.0F);
                r = next;
            }
        }
    }

    private static void wings(PoseStack pose, SubmitNodeCollector collector, int light, float age, boolean flying, boolean crouching) {
        int style = ModSettings.cosmeticWingsStyle;
        int mode = ModSettings.cosmeticWingsColorMode;
        int root = Cosmetics.color(mode, ModSettings.cosmeticWingsColor, ModSettings.cosmeticWingsColor2, 0.0F);
        int tip = Cosmetics.color(mode, ModSettings.cosmeticWingsColor, ModSettings.cosmeticWingsColor2, 1.0F);
        float size = ModSettings.cosmeticWingsSize;
        float speed = ModSettings.cosmeticWingsSpeed * (flying ? 2.6F : 1.0F);
        float flap = Mth.sin(age * 0.18F * speed);
        boolean neon = style == Cosmetics.NEON_WINGS;
        float spread = crouching ? 62.0F : (flying ? 18.0F : (neon ? 14.0F : 32.0F));
        float angle = spread + flap * (flying ? 22.0F : 12.0F);
        boolean glow = ModSettings.cosmeticWingsGlow || neon;
        RenderType type = glow ? RenderTypes.entityTranslucentEmissive(CosmeticTextures.wings(style)) : RenderTypes.entityTranslucent(CosmeticTextures.wings(style));
        int wingLight = glow ? FULL_BRIGHT : light;
        float w = (neon ? 1.35F : 1.05F) * size;
        float h = (neon ? 0.95F : 1.1F) * size;

        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 0.06F, 0.18F, 0.14F);
            pose.mulPose(Axis.YP.rotationDegrees(-side * angle));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -8.0F));
            int s = side;
            collector.submitCustomGeometry(pose, type, (p, out) -> {
                Geo geo = new Geo(p, out, wingLight);
                float x = s * w;
                geo.card(
                    new Vector3f(0.0F, -0.32F * h, 0.0F),
                    new Vector3f(x, -0.32F * h, 0.0F),
                    new Vector3f(x, 0.68F * h, 0.0F),
                    new Vector3f(0.0F, 0.68F * h, 0.0F),
                    0.0F,
                    1.0F,
                    root,
                    tip,
                    tip,
                    root
                );
            });
            pose.popPose();
        }
    }

    private static void shapedCape(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float age) {
        int mode = ModSettings.cosmeticCapeColorMode;
        int c1 = Cosmetics.color(mode, ModSettings.cosmeticCapeColor, ModSettings.cosmeticCapeColor2, 0.0F);
        int c2 = Cosmetics.color(mode, ModSettings.cosmeticCapeColor, ModSettings.cosmeticCapeColor2, 1.0F);
        boolean armored = !state.chestEquipment.isEmpty();
        pose.translate(0.0F, armored ? -0.053F : 0.0F, armored ? 0.19F : 0.135F);
        int style = ModSettings.cosmeticCapeStyle;
        if (style == Cosmetics.CAPELET_CAPE) {
            pose.pushPose();
            pose.translate(0.0F, 0.32F, 0.2F);
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> motes(new Geo(p, out, FULL_BRIGHT), age, c2));
            pose.popPose();
        }

        float swing = 6.0F + state.capeLean / 2.0F + state.capeFlap;
        pose.mulPose(Axis.XP.rotationDegrees(style == Cosmetics.CAPELET_CAPE ? swing * 0.5F : swing));
        pose.mulPose(Axis.ZP.rotationDegrees(state.capeLean2 / 2.0F));
        if (style == Cosmetics.SHARD_CAPE) {
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> shards(new Geo(p, out, FULL_BRIGHT), age, c1, c2));
        } else if (style == Cosmetics.BANNER_CAPE) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CosmeticTextures.white()), (p, out) -> banner(new Geo(p, out, light), age, c1, c2));
        } else if (style == Cosmetics.CAPELET_CAPE) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CosmeticTextures.white()), (p, out) -> capelet(new Geo(p, out, light), c1, c2));
        } else {
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CosmeticTextures.white()), (p, out) -> {
                Geo geo = new Geo(p, out, light);
                for (int i = 0; i < RIBBON_X.length; i++) {
                    ribbon(geo, age, RIBBON_X[i], RIBBON_HALF_WIDTH[i], RIBBON_LENGTH[i], i * 2.1F, i == 1 ? 0.012F : 0.0F, c1, c2);
                }
            });
        }
    }

    private static void ribbon(Geo geo, float age, float rootX, float rootHalf, float length, float phase, float depth, int c1, int c2) {
        float prevX = rootX;
        float prevY = 0.0F;
        float prevZ = depth;
        float prevHalf = rootHalf;
        int prevColor = c1;
        for (int s = 1; s <= RIBBON_SEGMENTS; s++) {
            float t = (float)s / RIBBON_SEGMENTS;
            float x = rootX + Mth.sin(age * 0.07F + t * 3.0F + phase) * 0.035F * t;
            float y = t * length;
            float z = depth + Mth.sin(age * 0.16F - t * 5.5F + phase) * 0.05F * t;
            float half = rootHalf * (1.0F - 0.35F * t);
            int color = Cosmetics.withAlpha(Colors.lerp(c1, c2, t), 1.0F - 0.2F * t);
            float dy = y - prevY;
            float dz = z - prevZ;
            float inv = Mth.invSqrt(dy * dy + dz * dz);
            float ny = dz * inv;
            float nz = -dy * inv;
            if (s < RIBBON_SEGMENTS) {
                geo.vertex(prevX - prevHalf, prevY, prevZ, prevColor, 0.0F, 0.0F, 0.0F, ny, nz);
                geo.vertex(prevX + prevHalf, prevY, prevZ, prevColor, 1.0F, 0.0F, 0.0F, ny, nz);
                geo.vertex(x + half, y, z, color, 1.0F, 1.0F, 0.0F, ny, nz);
                geo.vertex(x - half, y, z, color, 0.0F, 1.0F, 0.0F, ny, nz);
            } else {
                float notchY = y - dy * 0.8F;
                float notchZ = z - dz * 0.8F;
                geo.vertex(prevX - prevHalf, prevY, prevZ, prevColor, 0.0F, 0.0F, 0.0F, ny, nz);
                geo.vertex(prevX, prevY, prevZ, prevColor, 0.5F, 0.0F, 0.0F, ny, nz);
                geo.vertex(x, notchY, notchZ, color, 0.5F, 1.0F, 0.0F, ny, nz);
                geo.vertex(x - half, y, z, color, 0.0F, 1.0F, 0.0F, ny, nz);
                geo.vertex(prevX, prevY, prevZ, prevColor, 0.5F, 0.0F, 0.0F, ny, nz);
                geo.vertex(prevX + prevHalf, prevY, prevZ, prevColor, 1.0F, 0.0F, 0.0F, ny, nz);
                geo.vertex(x + half, y, z, color, 1.0F, 1.0F, 0.0F, ny, nz);
                geo.vertex(x, notchY, notchZ, color, 0.5F, 1.0F, 0.0F, ny, nz);
            }

            prevX = x;
            prevY = y;
            prevZ = z;
            prevHalf = half;
            prevColor = color;
        }
    }

    private static void shards(Geo geo, float age, int c1, int c2) {
        for (int row = 0; row < SHARD_ROWS; row++) {
            float v = (float)row / (SHARD_ROWS - 1);
            boolean staggered = (row & 1) == 1;
            int columns = staggered ? SHARD_COLUMNS - 1 : SHARD_COLUMNS;
            float spacing = 0.1F * (1.0F + 0.22F * v);
            float halfWidth = 0.042F * (1.0F + 0.15F * v);
            float halfHeight = 0.075F * (1.0F - 0.25F * v);
            for (int column = 0; column < columns; column++) {
                float offset = column - (columns - 1) * 0.5F;
                float cx = offset * spacing + Mth.sin(age * 0.05F + row * 1.3F + column) * 0.006F;
                float cy = 0.06F + row * 0.155F;
                float cz = Mth.sin(age * 0.12F - row * 0.7F + offset * 0.4F) * 0.03F * (0.3F + v);
                float twinkle = Cosmetics.reduceMotion() ? 0.5F : 0.5F + 0.5F * Mth.sin(age * 0.09F + row * 2.7F + column * 1.9F);
                int body = Colors.lerp(c1, c2, v);
                diamond(geo, cx, cy, cz, halfWidth, halfHeight, Cosmetics.withAlpha(body, 0.55F + 0.2F * twinkle));
                diamond(geo, cx, cy, cz - 0.004F, halfWidth * 0.45F, halfHeight * 0.45F, Cosmetics.withAlpha(Colors.mixWhite(body, 0.45F + 0.4F * twinkle), 0.9F));
            }
        }
    }

    private static void diamond(Geo geo, float cx, float cy, float cz, float halfWidth, float halfHeight, int argb) {
        geo.vertex(cx, cy - halfHeight, cz, argb, 0.5F, 0.0F, 0.0F, 0.0F, 1.0F);
        geo.vertex(cx + halfWidth, cy, cz, argb, 1.0F, 0.5F, 0.0F, 0.0F, 1.0F);
        geo.vertex(cx, cy + halfHeight, cz, argb, 0.5F, 1.0F, 0.0F, 0.0F, 1.0F);
        geo.vertex(cx - halfWidth, cy, cz, argb, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F);
    }

    static void pets(PoseStack pose, SubmitNodeCollector collector, int light, float age, boolean firstPerson) {
        int primary = ModSettings.cosmeticPetType;
        int limit = Math.clamp(ModSettings.cosmeticPetLimit, 1, Cosmetics.MAX_PETS);
        pet(pose, collector, light, age, firstPerson, primary, 0);
        int extras = ModSettings.cosmeticPetExtras;
        int slot = 1;
        for (int type = 0; type < Cosmetics.PETS.length && slot < limit; type++) {
            if ((extras & 1 << type) != 0 && type != primary) {
                pet(pose, collector, light, age, firstPerson, type, slot);
                slot++;
            }
        }
    }

    private static void pet(PoseStack pose, SubmitNodeCollector collector, int light, float age, boolean firstPerson, int type, int slot) {
        boolean shaped = Cosmetics.shapedPet(type);
        float side = ModSettings.cosmeticPetSide == 1 ? 1.0F : -1.0F;
        float size = Math.clamp(ModSettings.cosmeticPetSize, 0.5F, 2.0F);
        float bob = shaped ? 0.0F : Mth.sin(age * 0.11F + slot * 2.0F) * 0.06F;
        float[] anchor = firstPerson ? PET_SLOTS_FIRST_PERSON[slot] : PET_SLOTS[slot];
        pose.pushPose();
        pose.translate(side * anchor[0], anchor[1] + bob, anchor[2]);

        if (!shaped) {
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(age * 0.03F) * 25.0F));
        }

        pose.scale(size, size, size);
        if (type == Cosmetics.JELLYFISH_PET) {
            jellyfish(pose, collector, age);
        } else if (type == Cosmetics.ORRERY_PET) {
            orrery(pose, collector, light, age);
        } else if (type == Cosmetics.CRANE_PET) {
            crane(pose, collector, light, age);
        } else if (type == Cosmetics.KOI_PET) {
            koi(pose, collector, light, age);
        } else if (type <= 0 || type >= PET_ITEMS.length) {
            wisp(pose, collector, age);
        } else {
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            pose.scale(0.42F, 0.42F, 0.42F);
            Minecraft mc = Minecraft.getInstance();
            ItemStackRenderState itemState = PET_STATES[slot];
            mc.getItemModelResolver().updateForTopItem(itemState, PET_ITEMS[type], ItemDisplayContext.FIXED, mc.level, null, 0);
            itemState.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        }

        pose.popPose();
    }

    private static float[] pleats() {
        float[] pleats = new float[BANNER_COLUMNS + 1];
        for (int k = 0; k <= BANNER_COLUMNS; k++) {
            pleats[k] = (k & 1) == 0 ? 0.012F : -0.004F;
        }

        return pleats;
    }

    private static float[] capeletDrops() {
        float[] drops = new float[CAPELET_COLUMNS + 1];
        for (int k = 0; k <= CAPELET_COLUMNS; k++) {
            float centered = 2.0F * k / CAPELET_COLUMNS - 1.0F;
            drops[k] = 0.36F - 0.12F * centered * centered;
        }

        return drops;
    }

    private static void banner(Geo geo, float age, int c1, int c2) {
        float[] previous = BANNER_PREVIOUS;
        float[] current = BANNER_CURRENT;
        int liningBase = Colors.darken(c2, 0.55F);
        int previousColor = c1;
        for (int row = 0; row <= BANNER_ROWS; row++) {
            float v = (float)row / BANNER_ROWS;
            float half = 0.25F + 0.06F * v;
            for (int k = 0; k <= BANNER_COLUMNS; k++) {
                float u = (float)k / BANNER_COLUMNS;
                int i = k * 3;
                current[i] = (u * 2.0F - 1.0F) * half;
                current[i + 1] = v * (0.98F - BANNER_HEM[k]);
                current[i + 2] = BANNER_PLEAT[k] * (0.4F + v) + Mth.sin(age * 0.07F - v * 3.0F + u * 1.5F) * 0.035F * v;
            }

            int rowColor = Colors.lerp(c1, Colors.darken(c1, 0.72F), v);
            if (row > 0) {
                int fold = Colors.darken(previousColor, 0.88F);
                int lining = Colors.lerp(liningBase, Colors.darken(liningBase, 0.8F), v);
                for (int k = 0; k < BANNER_COLUMNS; k++) {
                    int a = k * 3;
                    int b = a + 3;
                    int color = (k & 1) == 0 ? previousColor : fold;
                    geo.quad(
                        previous[a], previous[a + 1], previous[a + 2],
                        previous[b], previous[b + 1], previous[b + 2],
                        current[b], current[b + 1], current[b + 2],
                        current[a], current[a + 1], current[a + 2],
                        color
                    );
                    geo.quad(
                        previous[a], previous[a + 1], previous[a + 2] - 0.012F,
                        previous[b], previous[b + 1], previous[b + 2] - 0.012F,
                        current[b], current[b + 1], current[b + 2] - 0.012F,
                        current[a], current[a + 1], current[a + 2] - 0.012F,
                        lining
                    );
                }
            }

            System.arraycopy(current, 0, previous, 0, current.length);
            previousColor = rowColor;
        }
    }

    private static void capelet(Geo geo, int c1, int c2) {
        int top = Colors.mixWhite(c1, 0.12F);
        int bottom = Colors.darken(c1, 0.78F);
        for (int row = 0; row < CAPELET_ROWS; row++) {
            float v0 = (float)row / CAPELET_ROWS;
            float v1 = (float)(row + 1) / CAPELET_ROWS;
            int color = row == CAPELET_ROWS - 1 ? c2 : Colors.lerp(top, bottom, v0);
            for (int k = 0; k < CAPELET_COLUMNS; k++) {
                float u0 = 2.0F * k / CAPELET_COLUMNS - 1.0F;
                float u1 = 2.0F * (k + 1) / CAPELET_COLUMNS - 1.0F;
                geo.quad(
                    u0 * (0.28F + 0.05F * v0), v0 * CAPELET_DROP[k], capeletDepth(u0, v0),
                    u1 * (0.28F + 0.05F * v0), v0 * CAPELET_DROP[k + 1], capeletDepth(u1, v0),
                    u1 * (0.28F + 0.05F * v1), v1 * CAPELET_DROP[k + 1], capeletDepth(u1, v1),
                    u0 * (0.28F + 0.05F * v1), v1 * CAPELET_DROP[k], capeletDepth(u0, v1),
                    color
                );
            }
        }
    }

    private static float capeletDepth(float centered, float v) {
        return (0.02F + 0.035F * (1.0F - centered * centered)) * v;
    }

    private static void motes(Geo geo, float age, int c2) {
        int core = Cosmetics.withAlpha(Colors.mixWhite(c2, 0.35F), 0.95F);
        int glow = Cosmetics.withAlpha(c2, 0.25F);
        for (int i = 0; i < CAPELET_MOTES; i++) {
            float angle = age * 0.04F + Mth.TWO_PI * i / CAPELET_MOTES;
            float x = Mth.cos(angle) * 0.3F;
            float y = Mth.sin(angle) * 0.3F;
            float z = Mth.sin(angle * 2.0F) * 0.03F;
            diamond(geo, x, y, z, 0.045F, 0.045F, glow);
            diamond(geo, x, y, z - 0.002F, 0.02F, 0.022F, core);
            geo.vertex(x, y - 0.022F, z, core, 0.5F, 0.0F, 1.0F, 0.0F, 0.0F);
            geo.vertex(x, y, z + 0.02F, core, 1.0F, 0.5F, 1.0F, 0.0F, 0.0F);
            geo.vertex(x, y + 0.022F, z, core, 0.5F, 1.0F, 1.0F, 0.0F, 0.0F);
            geo.vertex(x, y, z - 0.02F, core, 0.0F, 0.5F, 1.0F, 0.0F, 0.0F);
        }
    }

    private static void koi(PoseStack pose, SubmitNodeCollector collector, int light, float age) {
        float course = age * 0.03F + 0.4F * Mth.sin(age * 0.011F);
        float headingX = -Mth.sin(course);
        float headingZ = Mth.cos(course);
        pose.pushPose();
        pose.translate(Mth.cos(course) * 0.13F, Mth.sin(age * 0.06F) * 0.02F, Mth.sin(course) * 0.13F);
        pose.mulPose(Axis.YP.rotation((float)Mth.atan2(headingX, headingZ)));
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CosmeticTextures.white()), (p, out) -> koiBody(new Geo(p, out, light), age));
        pose.popPose();
    }

    private static void koiBody(Geo geo, float age) {
        float[] ring = KOI_RING;
        int rings = KOI_Z.length;
        for (int k = 0; k < rings; k++) {
            float sway = Mth.sin(age * 0.25F - k * 0.7F) * 0.012F * k;
            float radius = KOI_RADIUS[k];
            for (int side = 0; side < 4; side++) {
                int i = (k * 4 + side) * 3;
                ring[i] = sway + KOI_SIDE_X[side] * radius;
                ring[i + 1] = KOI_SIDE_Y[side] * radius;
                ring[i + 2] = KOI_Z[k];
            }
        }

        for (int side = 0; side < 4; side++) {
            int a = side * 3;
            int b = (side + 1) % 4 * 3;
            geo.quad(ring[a], ring[a + 1], ring[a + 2], ring[b], ring[b + 1], ring[b + 2], 0.0F, 0.0F, KOI_Z[0] + 0.03F, 0.0F, 0.0F, KOI_Z[0] + 0.03F, KOI_PATCH[0]);
        }

        for (int k = 0; k < rings - 1; k++) {
            for (int side = 0; side < 4; side++) {
                int next = (side + 1) % 4;
                int a = (k * 4 + side) * 3;
                int b = (k * 4 + next) * 3;
                int c = ((k + 1) * 4 + next) * 3;
                int d = ((k + 1) * 4 + side) * 3;
                int color = side == 0 || side == 3 ? KOI_PATCH[k] : KOI_BELLY;
                geo.quad(ring[a], ring[a + 1], ring[a + 2], ring[b], ring[b + 1], ring[b + 2], ring[c], ring[c + 1], ring[c + 2], ring[d], ring[d + 1], ring[d + 2], color);
            }
        }

        int tail = (rings - 1) * 4 * 3;
        float tailX = ring[tail];
        float tailZ = KOI_Z[rings - 1];
        float flick = Mth.sin(age * 0.25F - rings * 0.7F) * 0.03F;
        geo.quad(tailX, 0.0F, tailZ, tailX + flick, -0.055F, tailZ - 0.07F, tailX + flick * 0.5F, 0.0F, tailZ - 0.045F, tailX + flick * 0.5F, 0.0F, tailZ - 0.045F, KOI_FIN);
        geo.quad(tailX, 0.0F, tailZ, tailX + flick, 0.055F, tailZ - 0.07F, tailX + flick * 0.5F, 0.0F, tailZ - 0.045F, tailX + flick * 0.5F, 0.0F, tailZ - 0.045F, KOI_FIN);
        float paddle = Mth.sin(age * 0.2F) * 0.012F;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * KOI_RADIUS[1];
            geo.quad(x, 0.01F, 0.06F, x + side * 0.05F, 0.025F + paddle, 0.03F, x, 0.01F, 0.035F, x, 0.01F, 0.035F, KOI_FIN);
            float eyeX = side * KOI_RADIUS[1] * 0.92F;
            geo.quad(eyeX, -0.018F, 0.07F, eyeX, -0.018F, 0.06F, eyeX, -0.008F, 0.06F, eyeX, -0.008F, 0.07F, 0xFF1A1A1A);
        }
    }

    private static int petColor(float t) {
        return Cosmetics.color(ModSettings.cosmeticPetColorMode, ModSettings.cosmeticPetColor, ModSettings.cosmeticPetColor2, t);
    }

    private static void jellyfish(PoseStack pose, SubmitNodeCollector collector, float age) {
        int c1 = petColor(0.0F);
        int c2 = petColor(1.0F);
        float cycle = age * 0.09F;
        float contract = Cosmetics.reduceMotion() ? 0.0F : Math.max(0.0F, Mth.sin(cycle));
        float widthScale = 1.0F - 0.18F * contract;
        float heightScale = 1.0F + 0.15F * contract;
        pose.pushPose();
        pose.translate(0.0F, -0.08F - Mth.sin(cycle - 0.8F) * 0.06F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(age * 0.4F));
        int bell = Cosmetics.withAlpha(Colors.mixWhite(c1, 0.2F), 0.42F);
        int rim = Cosmetics.withAlpha(c2, 0.8F);
        int core = Cosmetics.withAlpha(Colors.mixWhite(c2, 0.5F), 0.85F);
        int tentacle = Cosmetics.withAlpha(Colors.lerp(c1, c2, 0.5F), 0.6F);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, FULL_BRIGHT);
            float w = widthScale;
            float h = heightScale;
            geo.frustum(0.16F * w, 0.17F * w, 0.0F, -0.06F * h, 16, bell, false, false);
            geo.frustum(0.17F * w, 0.14F * w, -0.06F * h, -0.12F * h, 16, bell, false, false);
            geo.frustum(0.14F * w, 0.08F * w, -0.12F * h, -0.16F * h, 16, bell, false, false);
            geo.frustum(0.08F * w, 0.0F, -0.16F * h, -0.175F * h, 16, bell, false, false);
            geo.frustum(0.165F * w, 0.16F * w, 0.012F, -0.004F, 16, rim, false, false);
            geo.ball(0.0F, -0.08F * h, 0.0F, 0.045F, core);
            for (int i = 0; i < TENTACLES; i++) {
                float angle = Mth.TWO_PI * i / TENTACLES + 0.3F;
                float baseX = Mth.cos(angle) * 0.11F * w;
                float baseZ = Mth.sin(angle) * 0.11F * w;
                float prevX = baseX;
                float prevY = 0.0F;
                float prevZ = baseZ;
                for (int k = 1; k <= TENTACLE_SEGMENTS; k++) {
                    float t = (float)k / TENTACLE_SEGMENTS;
                    float pull = 1.0F - 0.35F * contract * t;
                    float x = baseX * pull + Mth.sin(age * 0.13F - t * 4.0F + i) * 0.03F * t;
                    float y = t * 0.32F * (1.2F - 0.2F * contract);
                    float z = baseZ * pull + Mth.cos(age * 0.11F - t * 4.0F + i * 1.7F) * 0.03F * t;
                    float half = 0.009F * (1.0F - 0.6F * t);
                    int faded = Cosmetics.withAlpha(tentacle, 0.6F * (1.0F - 0.7F * t));
                    geo.vertex(prevX - half, prevY, prevZ, faded, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
                    geo.vertex(prevX + half, prevY, prevZ, faded, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
                    geo.vertex(x + half, y, z, faded, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
                    geo.vertex(x - half, y, z, faded, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
                    geo.vertex(prevX, prevY, prevZ - half, faded, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F);
                    geo.vertex(prevX, prevY, prevZ + half, faded, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F);
                    geo.vertex(x, y, z + half, faded, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
                    geo.vertex(x, y, z - half, faded, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
                    prevX = x;
                    prevY = y;
                    prevZ = z;
                }
            }
        });
        pose.popPose();
    }

    private static void orrery(PoseStack pose, SubmitNodeCollector collector, int light, float age) {
        int c1 = petColor(0.0F);
        int c2 = petColor(1.0F);
        int ringColor = Colors.darken(c1, 0.75F);
        int moonColor = Colors.mixWhite(c1, 0.55F);
        RenderType type = RenderTypes.entityCutoutNoCull(CosmeticTextures.white());
        pose.pushPose();
        pose.translate(0.0F, Mth.sin(age * 0.04F) * 0.015F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(age * 0.6F));
        collector.submitCustomGeometry(pose, type, (p, out) -> {
            Geo geo = new Geo(p, out, light);
            geo.ball(0.0F, 0.0F, 0.0F, 0.07F, c2);
            geo.frustum(0.012F, 0.012F, -0.07F, -0.11F, 6, ringColor, false, true);
        });
        orbit(pose, collector, type, light, 25.0F, 0.0F, 0.15F, age * 0.08F, 0.026F, ringColor, c1);
        orbit(pose, collector, type, light, -12.0F, -38.0F, 0.21F, -age * 0.045F, 0.02F, ringColor, moonColor);
        pose.popPose();
    }

    private static void orbit(
        PoseStack pose, SubmitNodeCollector collector, RenderType type, int light, float tiltX, float tiltZ, float radius, float angle, float planetRadius, int ringColor, int planetColor
    ) {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(tiltX));
        pose.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        float px = Mth.cos(angle) * radius;
        float pz = Mth.sin(angle) * radius;
        collector.submitCustomGeometry(pose, type, (p, out) -> {
            Geo geo = new Geo(p, out, light);
            geo.torus(radius, 0.006F, 20, 4, i -> ringColor);
            geo.ball(px, 0.0F, pz, planetRadius, planetColor);
        });
        pose.popPose();
    }

    private static void crane(PoseStack pose, SubmitNodeCollector collector, int light, float age) {
        int paper = Colors.mixWhite(petColor(0.0F), 0.7F);
        int fold = Colors.darken(paper, 0.82F);
        int crease = Colors.darken(paper, 0.68F);
        int tipColor = petColor(1.0F);
        float phase = age * 0.05F;
        float dx = Mth.cos(phase) * 0.18F;
        float dz = Mth.cos(phase * 2.0F) * 0.18F;
        float glide = Math.clamp((0.35F - Mth.sin(age * 0.031F)) * 3.0F, 0.0F, 1.0F);
        float lift = -(0.02F + Mth.sin(age * 0.45F) * 0.08F * glide);
        pose.pushPose();
        pose.translate(Mth.sin(phase) * 0.18F, -Mth.cos(phase * 2.0F) * 0.04F, Mth.sin(phase * 2.0F) * 0.09F);
        pose.mulPose(Axis.YP.rotation((float)Mth.atan2(dx, dz)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(phase) * 25.0F));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, light);
            Vector3f front = new Vector3f(0.0F, 0.0F, 0.09F);
            Vector3f back = new Vector3f(0.0F, 0.0F, -0.09F);
            geo.tri(front, back, new Vector3f(-0.022F, 0.035F, 0.0F), fold);
            geo.tri(front, back, new Vector3f(0.022F, 0.035F, 0.0F), crease);
            Vector3f head = new Vector3f(0.0F, -0.13F, 0.15F);
            geo.tri(new Vector3f(0.0F, 0.0F, 0.05F), front, head, paper);
            geo.tri(head, new Vector3f(0.0F, -0.115F, 0.135F), new Vector3f(0.0F, -0.1F, 0.2F), tipColor);
            geo.tri(new Vector3f(0.0F, 0.0F, -0.05F), back, new Vector3f(0.0F, -0.12F, -0.15F), paper);
            for (int side = -1; side <= 1; side += 2) {
                Vector3f rootFront = new Vector3f(0.0F, -0.01F, 0.05F);
                Vector3f rootBack = new Vector3f(0.0F, -0.01F, -0.06F);
                Vector3f tip = new Vector3f(side * 0.21F, lift, -0.03F);
                Vector3f mid = new Vector3f(side * 0.09F, lift * 0.45F - 0.012F, 0.005F);
                geo.tri(rootFront, mid, tip, paper);
                geo.tri(rootFront, rootBack, mid, fold);
                geo.tri(rootBack, tip, mid, crease);
            }
        });
        pose.popPose();
    }

    private static void wisp(PoseStack pose, SubmitNodeCollector collector, float age) {
        int c1 = petColor(0.0F);
        int c2 = petColor(1.0F);
        float pulse = Cosmetics.reduceMotion() ? 1.0F : 1.0F + Mth.sin(age * 0.25F) * 0.08F;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, FULL_BRIGHT);
            geo.ball(0.0F, 0.0F, 0.0F, 0.07F * pulse, Cosmetics.withAlpha(0xFFFFFF, 0.95F));
            geo.ball(0.0F, 0.0F, 0.0F, 0.11F * pulse, Cosmetics.withAlpha(c1, 0.45F));
            geo.ball(0.0F, 0.0F, 0.0F, 0.16F * pulse, Cosmetics.withAlpha(c2, 0.16F));
            for (int i = 0; i < 3; i++) {
                float a = age * 0.12F + Mth.TWO_PI * i / 3.0F;
                geo.cube(Mth.cos(a) * 0.22F, Mth.sin(a * 1.7F) * 0.05F, Mth.sin(a) * 0.22F, 0.018F, Cosmetics.withAlpha(i == 1 ? c2 : c1, 0.9F));
            }

            for (int i = 1; i <= 4; i++) {
                float trail = i * 0.045F;
                geo.cube(0.0F, trail * 0.4F, trail, 0.035F - i * 0.006F, Cosmetics.withAlpha(c1, 0.5F - i * 0.1F));
            }
        });
    }
}
