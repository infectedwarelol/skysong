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
    private static final ItemStackRenderState PET_STATE = new ItemStackRenderState();
    private static final float[] RIBBON_X = new float[]{-0.17F, 0.0F, 0.17F};
    private static final float[] RIBBON_HALF_WIDTH = new float[]{0.07F, 0.09F, 0.07F};
    private static final float[] RIBBON_LENGTH = new float[]{0.86F, 1.02F, 0.86F};
    private static final int RIBBON_SEGMENTS = 12;
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

        if (ModSettings.cosmeticPet) {
            pose.pushPose();
            this.getParentModel().root().translateAndRotate(pose);
            pet(pose, collector, light, age, false);
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
        int c = Cosmetics.color(ModSettings.cosmeticAuraColorMode, ModSettings.cosmeticAuraColor, ModSettings.cosmeticAuraColor2, 0.5F + 0.5F * Mth.sin(age * 0.05F));
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
        pose.mulPose(Axis.XP.rotationDegrees(6.0F + state.capeLean / 2.0F + state.capeFlap));
        pose.mulPose(Axis.ZP.rotationDegrees(state.capeLean2 / 2.0F));
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, light);
            for (int i = 0; i < RIBBON_X.length; i++) {
                ribbon(geo, age, RIBBON_X[i], RIBBON_HALF_WIDTH[i], RIBBON_LENGTH[i], i * 2.1F, i == 1 ? 0.012F : 0.0F, c1, c2);
            }
        });
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

    static void pet(PoseStack pose, SubmitNodeCollector collector, int light, float age, boolean firstPerson) {
        int type = ModSettings.cosmeticPetType;
        float side = ModSettings.cosmeticPetSide == 1 ? 1.0F : -1.0F;
        float size = ModSettings.cosmeticPetSize;
        float bob = Mth.sin(age * 0.11F) * 0.06F;
        pose.pushPose();
        if (firstPerson) {
            pose.translate(side * 0.95F, -0.1F + bob, -1.45F);
        } else {
            pose.translate(side * 0.68F, -0.42F + bob, 0.18F);
        }

        pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(age * 0.03F) * 25.0F));
        pose.scale(size, size, size);
        if (type <= 0 || type >= PET_ITEMS.length) {
            wisp(pose, collector, age);
        } else {
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            pose.scale(0.42F, 0.42F, 0.42F);
            Minecraft mc = Minecraft.getInstance();
            mc.getItemModelResolver().updateForTopItem(PET_STATE, PET_ITEMS[type], ItemDisplayContext.FIXED, mc.level, null, 0);
            PET_STATE.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        }

        pose.popPose();
    }

    private static void wisp(PoseStack pose, SubmitNodeCollector collector, float age) {
        int mode = ModSettings.cosmeticPetColorMode;
        int c1 = Cosmetics.color(mode, ModSettings.cosmeticPetColor, ModSettings.cosmeticPetColor2, 0.0F);
        int c2 = Cosmetics.color(mode, ModSettings.cosmeticPetColor, ModSettings.cosmeticPetColor2, 1.0F);
        float pulse = 1.0F + Mth.sin(age * 0.25F) * 0.08F;
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
