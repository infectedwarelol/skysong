package dev.infectedware.skysong.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
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
    static final float[][] PET_SLOTS = new float[][]{{0.68F, -0.42F, 0.18F}, {-0.68F, -0.42F, 0.18F}, {0.32F, -0.8F, 0.48F}};
    private static final float[][] PET_SLOTS_FIRST_PERSON = new float[][]{{0.95F, -0.1F, -1.45F}, {-0.95F, -0.1F, -1.45F}, {1.55F, -0.3F, -1.9F}};
    private static final int BANNER_COLUMNS = 8;
    private static final int BANNER_ROWS = 10;
    private static final float[] BANNER_HEM = new float[]{0.0F, 0.09F, 0.02F, 0.13F, 0.04F, 0.11F, 0.0F, 0.08F, 0.03F};
    private static final float[] BANNER_PLEAT = pleats();
    private static final float[] BANNER_PREVIOUS = new float[(BANNER_COLUMNS + 1) * 6];
    private static final float[] BANNER_CURRENT = new float[(BANNER_COLUMNS + 1) * 6];
    private static final float[] CLOTH_SAMPLE = new float[9];
    private static final float[] CAPELET_CORNERS = new float[12];
    private static final float[] CHAIN_ANCHOR = new float[3];
    private static final int HALO_SEGMENTS = 32;
    private static final int ORBIT_SEGMENTS = 20;
    private static final int[] ORBIT_COLORS = new int[ORBIT_SEGMENTS + 1];
    private static final int HALO_RAYS = 12;
    private static final int[] RING_COLORS = new int[HALO_SEGMENTS + 1];
    private static final int CROWN_SPIKES = 8;
    private static final int HORN_SEGMENTS = 6;
    private static final PoseStack HORN_STACK = new PoseStack();
    private static final float[] WING_TEMPO = new float[]{0.16F, 0.22F, 0.42F, 0.27F, 0.12F, 0.18F};
    private static final float[] WING_SWING = new float[]{13.0F, 11.0F, 7.0F, 16.0F, 17.0F, 9.0F};
    private static final int[] PET_TYPES = new int[Cosmetics.MAX_PETS];
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
    private static final int LANTERN_SIDES = 8;
    private static final float[] LANTERN_COS = octagon(true);
    private static final float[] LANTERN_SIN = octagon(false);
    private static final float[] LANTERN_RADIUS = new float[]{0.055F, 0.085F, 0.095F, 0.085F, 0.055F};
    private static final float[] LANTERN_Y = new float[]{0.125F, 0.16F, 0.21F, 0.26F, 0.295F};
    private static final int LANTERN_PAPER = 0xFFFFB347;
    private static final int LANTERN_WOOD = 0xFF4A2C1A;
    private static final int LANTERN_TASSEL = 0xFFC0392B;
    private static final int SPHERE_LATITUDES = 6;
    private static final int SPHERE_LONGITUDES = 8;
    private static final float[] SPHERE = unitSphere();
    private static final float[] CLOUD_PUFFS = new float[]{
        0.0F, 0.0F, 0.0F, 0.09F, 0.09F, 0.02F, 0.0F, 0.07F, -0.09F, 0.02F, 0.01F, 0.07F, 0.04F, -0.06F, 0.01F, 0.065F, -0.04F, -0.05F, -0.02F, 0.06F
    };
    private static final float[] CLOUD_DROPS = new float[]{-0.07F, 0.02F, 0.03F, -0.03F, 0.08F, 0.01F, -0.02F, 0.04F, 0.05F, 0.05F, -0.06F, -0.04F};
    private static final int CLOUD_TOP = 0xFFF4F7FB;
    private static final int CLOUD_BOTTOM = 0xFFC9D3E0;
    private static final int RAIN = 0xFF8EC9FF;
    static final float[] RIBBON_X = new float[]{-0.17F, 0.0F, 0.17F};
    private static final float[] RIBBON_HALF_WIDTH = new float[]{0.07F, 0.09F, 0.07F};
    static final float[] RIBBON_LENGTH = new float[]{0.86F, 1.02F, 0.86F};
    private static final int RIBBON_SEGMENTS = 12;
    private static final int SHARD_ROWS = 6;
    private static final int SHARD_COLUMNS = 5;
    static final int TENTACLES = 5;
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

        if (ModSettings.cosmeticCape && !Cosmetics.shapedCape() && state.showCape && Cosmetics.fxQuality() > 0
            && !state.chestEquipment.has(DataComponents.GLIDER) && CosmeticTextures.cape() != null) {
            pose.pushPose();
            this.getParentModel().root().translateAndRotate(pose);
            this.getParentModel().body.translateAndRotate(pose);
            capeGlow(pose, collector, state);
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
            pets(pose, collector, light, age, state.walkAnimationSpeed, false);
            pose.popPose();
        }
    }

    private static void capeGlow(PoseStack pose, SubmitNodeCollector collector, AvatarRenderState state) {
        Identifier texture = CosmeticTextures.capeGlow();
        if (texture == null) {
            return;
        }

        boolean armored = !state.chestEquipment.isEmpty();
        pose.translate(0.0F, armored ? -0.053125F : 0.0F, armored ? 0.19375F : 0.125F);
        pose.mulPose(Axis.XP.rotationDegrees(6.0F + state.capeLean / 2.0F + state.capeFlap));
        pose.mulPose(Axis.ZP.rotationDegrees(state.capeLean2 / 2.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - state.capeLean2 / 2.0F));
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(texture), (p, out) -> capeGlowCard(new Geo(p, out, FULL_BRIGHT), 0xFFFFFFFF));
        if (Cosmetics.fxQuality() == 2) {
            collector.submitCustomGeometry(pose, RenderTypes.energySwirl(texture, 0.0F, 0.0F), (p, out) -> capeGlowCard(new Geo(p, out, FULL_BRIGHT), 0xFF5A5A5A));
        }
    }

    private static void capeGlowCard(Geo geo, int argb) {
        float z = -0.0655F;
        geo.card(0.3125F, 0.0F, z, -0.3125F, 0.0F, z, -0.3125F, 1.0F, z, 0.3125F, 1.0F, z, 0.0F, 1.0F, argb, argb, argb, argb);
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
        float wave = 0.5F + 0.5F * Mth.sin(age * 0.035F);
        float flow = Cosmetics.reduceMotion() ? 0.5F : wave * wave * (3.0F - 2.0F * wave);
        int c = Cosmetics.color(ModSettings.cosmeticAuraColorMode, ModSettings.cosmeticAuraColor, ModSettings.cosmeticAuraColor2, flow);
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

        if (ModSettings.cosmeticHaloPhysics) {
            float partialTick = Compat.partialTick();
            float wobble = CosmeticPhysics.spring(CosmeticPhysics.HALO_WOBBLE, partialTick);
            pose.translate(CosmeticPhysics.spring(CosmeticPhysics.HALO_X, partialTick), 0.0F, CosmeticPhysics.spring(CosmeticPhysics.HALO_Z, partialTick));
            pose.mulPose(Axis.XP.rotationDegrees(CosmeticPhysics.spring(CosmeticPhysics.HALO_PITCH, partialTick) + wobble));
            pose.mulPose(Axis.ZP.rotationDegrees(CosmeticPhysics.spring(CosmeticPhysics.HALO_ROLL, partialTick) + wobble * 0.6F));
        }

        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(size, size, size);
        int fx = Cosmetics.fxQuality();
        float flow = Cosmetics.reduceMotion() ? 0.0F : age * 0.004F;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, FULL_BRIGHT);
            haloRing(geo, 0.2F, 0.02F, mode, c1, c2, flow, false);
            if (style == 1) {
                p.rotate(Axis.XP.rotationDegrees(14.0F));
                p.translate(0.0F, -0.06F, 0.0F);
                haloRing(geo, 0.15F, 0.016F, mode, c1, c2, flow, true);
            } else if (style == 2) {
                for (int i = 0; i < HALO_RAYS; i++) {
                    float a = Mth.TWO_PI * i / HALO_RAYS;
                    float cos = Mth.cos(a);
                    float sin = Mth.sin(a);
                    float len = i % 2 == 0 ? 0.12F : 0.07F;
                    int c = Cosmetics.color(mode, c1, c2, (float)i / HALO_RAYS);
                    float tipX = cos * (0.2F + len * 0.35F);
                    float tipZ = sin * (0.2F + len * 0.35F);
                    geo.tri(cos * 0.2F - sin * 0.018F, 0.0F, sin * 0.2F + cos * 0.018F, cos * 0.2F + sin * 0.018F, 0.0F, sin * 0.2F - cos * 0.018F, tipX, -len, tipZ, c);
                    geo.tri(cos * 0.2F - sin * 0.007F, -0.002F, sin * 0.2F + cos * 0.007F, cos * 0.2F + sin * 0.007F, -0.002F, sin * 0.2F - cos * 0.007F, tipX, -len, tipZ, Colors.mixWhite(c, 0.7F));
                }
            }
        });
        if (ModSettings.cosmeticHaloGlow && fx > 0) {
            collector.submitCustomGeometry(pose, glowType(), (p, out) -> {
                Geo geo = new Geo(p, out, FULL_BRIGHT);
                haloGlow(geo, 0.2F, 0.05F, mode, c1, c2, flow, 0.32F);
                if (fx == 2) {
                    haloGlow(geo, 0.2F, 0.09F, mode, c1, c2, flow, 0.12F);
                }

                if (style == 1) {
                    p.rotate(Axis.XP.rotationDegrees(14.0F));
                    p.translate(0.0F, -0.06F, 0.0F);
                    haloGlow(geo, 0.15F, 0.04F, mode, c1, c2, flow, 0.28F);
                } else if (style == 2) {
                    for (int i = 0; i < HALO_RAYS; i += 2) {
                        float a = Mth.TWO_PI * i / HALO_RAYS;
                        float x = Mth.cos(a) * 0.242F;
                        float z = Mth.sin(a) * 0.242F;
                        int c = glow(Cosmetics.color(mode, c1, c2, (float)i / HALO_RAYS), 0.5F);
                        geo.quad(x - 0.03F, -0.12F, z, x + 0.03F, -0.12F, z, x + 0.03F, -0.06F, z, x - 0.03F, -0.06F, z, c);
                        geo.quad(x, -0.12F, z - 0.03F, x, -0.12F, z + 0.03F, x, -0.06F, z + 0.03F, x, -0.06F, z - 0.03F, c);
                    }
                }
            });
        }

        pose.popPose();
    }

    private static void haloRing(Geo geo, float radius, float tube, int mode, int c1, int c2, float flow, boolean reversed) {
        int[] colors = RING_COLORS;
        for (int i = 0; i <= HALO_SEGMENTS; i++) {
            float t = ring((float)i / HALO_SEGMENTS + flow);
            colors[i] = Cosmetics.withAlpha(Colors.mixWhite(Cosmetics.color(mode, c1, c2, reversed ? 1.0F - t : t), 0.75F), 1.0F);
        }

        geo.torus(radius, tube * 0.4F, HALO_SEGMENTS, 4, colors);
        for (int i = 0; i <= HALO_SEGMENTS; i++) {
            float t = ring((float)i / HALO_SEGMENTS + flow);
            colors[i] = Cosmetics.withAlpha(Cosmetics.color(mode, c1, c2, reversed ? 1.0F - t : t), 0.78F);
        }

        geo.torus(radius, tube, HALO_SEGMENTS, 6, colors);
    }

    private static void haloGlow(Geo geo, float radius, float tube, int mode, int c1, int c2, float flow, float intensity) {
        int[] colors = RING_COLORS;
        for (int i = 0; i <= HALO_SEGMENTS; i++) {
            colors[i] = glow(Cosmetics.color(mode, c1, c2, ring((float)i / HALO_SEGMENTS + flow)), intensity);
        }

        geo.torus(radius, tube, HALO_SEGMENTS, 4, colors);
    }

    private static float ring(float position) {
        float t = position - Mth.floor(position);
        return t < 0.5F ? t * 2.0F : 2.0F - t * 2.0F;
    }

    static RenderType glowType() {
        return RenderTypes.energySwirl(CosmeticTextures.white(), 0.0F, 0.0F);
    }

    static int glow(int argb, float intensity) {
        float k = Math.clamp(intensity, 0.0F, 1.0F);
        int r = Math.round((argb >> 16 & 0xFF) * k);
        int g = Math.round((argb >> 8 & 0xFF) * k);
        int b = Math.round((argb & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
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
        if (ModSettings.cosmeticHatPhysics && tallHat(style)) {
            float partialTick = Compat.partialTick();
            pose.mulPose(Axis.XP.rotationDegrees(CosmeticPhysics.spring(CosmeticPhysics.HAT_PITCH, partialTick)));
            pose.mulPose(Axis.ZP.rotationDegrees(CosmeticPhysics.spring(CosmeticPhysics.HAT_ROLL, partialTick)));
        }

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
        if (Cosmetics.fxQuality() > 0 && (style == 1 || style == 2 || style == 3 || style == 6)) {
            collector.submitCustomGeometry(pose, glowType(), (p, out) -> hatGlow(p, new Geo(p, out, FULL_BRIGHT), style, accentColor));
        }

        pose.popPose();
    }

    private static void hatGlow(PoseStack.Pose p, Geo geo, int style, int accent) {
        int soft = glow(accent, 0.4F);
        if (style == 1) {
            for (int i = 0; i < CROWN_SPIKES; i++) {
                float am = Mth.TWO_PI * (i + 0.5F) / CROWN_SPIKES;
                geo.cube(Mth.cos(am) * 0.27F, -0.265F, Mth.sin(am) * 0.27F, 0.028F, soft);
            }
        } else if (style == 2) {
            geo.quad(-0.045F, -0.11F, -0.245F, 0.045F, -0.11F, -0.245F, 0.045F, -0.055F, -0.245F, -0.045F, -0.055F, -0.245F, soft);
        } else if (style == 3) {
            p.translate(0.07F, 0.0F, 0.0F);
            p.rotate(Axis.ZP.rotationDegrees(12.0F));
            geo.cube(0.0F, -0.46F, 0.0F, 0.06F, soft);
        } else {
            for (int side = -1; side <= 1; side += 2) {
                HORN_STACK.last().set(p);
                hornTip(side);
                geo.on(HORN_STACK.last()).cube(0.0F, 0.0F, 0.0F, 0.03F, soft);
            }
        }
    }

    private static boolean tallHat(int style) {
        return style == 2 || style == 3 || style == 7;
    }

    private static void topHat(Geo geo, int main, int accent) {
        geo.frustum(0.36F, 0.36F, -0.03F, -0.055F, 24, main, true, true);
        geo.frustum(0.21F, 0.22F, -0.055F, -0.42F, 24, main, false, true);
        geo.frustum(0.218F, 0.222F, -0.06F, -0.13F, 24, accent, false, false);
        geo.frustum(0.2225F, 0.2225F, -0.125F, -0.135F, 24, Colors.mixWhite(accent, 0.45F), false, false);
        geo.frustum(0.221F, 0.2215F, -0.36F, -0.4F, 24, Colors.mixWhite(main, 0.18F), false, false);
    }

    private static void conicalHat(Geo geo, int main, int accent) {
        int shade = Colors.darken(main, 0.82F);
        geo.cone(0.5F, -0.02F, 0.0F, -0.34F, 0.0F, 32, main, shade, 1, false);
        geo.frustum(0.5F, 0.47F, -0.02F, -0.045F, 32, accent, false, false);
        geo.frustum(0.505F, 0.5F, -0.018F, -0.026F, 32, Colors.mixWhite(accent, 0.4F), false, false);
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
        geo.frustum(0.262F, 0.262F, -0.02F, -0.035F, 24, Colors.mixWhite(main, 0.35F), false, false);
        for (int i = 0; i < CROWN_SPIKES; i++) {
            float a0 = Mth.TWO_PI * i / CROWN_SPIKES;
            float a1 = Mth.TWO_PI * (i + 1) / CROWN_SPIKES;
            float am = (a0 + a1) / 2.0F;
            geo.tri(Mth.cos(a0) * 0.26F, -0.13F, Mth.sin(a0) * 0.26F, Mth.cos(a1) * 0.26F, -0.13F, Mth.sin(a1) * 0.26F, Mth.cos(am) * 0.27F, -0.26F, Mth.sin(am) * 0.27F, main);
            geo.cube(Mth.cos(am) * 0.262F, -0.075F, Mth.sin(am) * 0.262F, 0.022F, accent);
            geo.cube(Mth.cos(am) * 0.27F, -0.265F, Mth.sin(am) * 0.27F, 0.016F, Colors.mixWhite(accent, 0.3F));
        }
    }

    private static void witchHat(Geo geo, int main, int accent) {
        geo.frustum(0.44F, 0.44F, -0.03F, -0.05F, 28, main, true, true);
        geo.frustum(0.23F, 0.13F, -0.05F, -0.36F, 20, main, false, false);
        geo.frustum(0.235F, 0.21F, -0.055F, -0.11F, 20, accent, false, false);
        geo.cone(0.13F, -0.36F, 0.0F, -0.62F, 0.18F, 20, main, main, 1, false);
        geo.box(-0.04F, -0.105F, -0.242F, 0.04F, -0.06F, -0.232F, Colors.mixWhite(accent, 0.55F));
    }

    private static void partyHat(PoseStack.Pose p, Geo geo, int main, int accent) {
        p.translate(0.07F, 0.0F, 0.0F);
        p.rotate(Axis.ZP.rotationDegrees(12.0F));
        geo.cone(0.15F, -0.02F, 0.0F, -0.45F, 0.0F, 16, main, accent, 2, true);
        geo.ball(0.0F, -0.46F, 0.0F, 0.045F, accent);
    }

    private static void ears(Geo geo, int main, int accent, boolean bunny) {
        for (int side = -1; side <= 1; side += 2) {
            float x = side * (bunny ? 0.11F : 0.17F);
            if (bunny) {
                float tilt = side * 0.08F;
                geo.box(x - 0.055F, -0.5F, -0.03F, x + 0.055F, -0.01F, 0.03F, main);
                geo.box(x - 0.03F + tilt * 0.1F, -0.46F, -0.035F, x + 0.03F + tilt * 0.1F, -0.06F, -0.03F, accent);
                geo.box(x - 0.018F + tilt * 0.1F, -0.4F, -0.037F, x + 0.018F + tilt * 0.1F, -0.12F, -0.035F, Colors.mixWhite(accent, 0.35F));
            } else {
                float outer = x + side * 0.08F;
                float inner = x - side * 0.08F;
                float tipX = x + side * 0.03F;
                geo.tri(inner, -0.01F, -0.03F, outer, -0.01F, -0.03F, tipX, -0.21F, -0.03F, main);
                geo.tri(inner, -0.01F, 0.03F, outer, -0.01F, 0.03F, tipX, -0.21F, 0.03F, main);
                geo.tri(inner + side * 0.03F, -0.03F, -0.035F, outer - side * 0.02F, -0.03F, -0.035F, tipX, -0.16F, -0.035F, accent);
                geo.tri(inner + side * 0.045F, -0.04F, -0.037F, outer - side * 0.035F, -0.04F, -0.037F, tipX, -0.12F, -0.037F, Colors.mixWhite(accent, 0.35F));
                geo.quad(inner, -0.01F, -0.03F, tipX, -0.21F, -0.03F, tipX, -0.21F, 0.03F, inner, -0.01F, 0.03F, main);
                geo.quad(outer, -0.01F, -0.03F, tipX, -0.21F, -0.03F, tipX, -0.21F, 0.03F, outer, -0.01F, 0.03F, main);
            }
        }
    }

    private static void horns(PoseStack.Pose p, Geo geo, int main, int accent) {
        PoseStack stack = HORN_STACK;
        for (int side = -1; side <= 1; side += 2) {
            stack.last().set(p);
            stack.translate(side * 0.17F, -0.01F, -0.12F);
            Geo segment = geo.on(stack.last());
            float r = 0.06F;
            for (int i = 0; i < HORN_SEGMENTS; i++) {
                stack.mulPose(Axis.ZP.rotationDegrees(side * 14.0F));
                stack.mulPose(Axis.XP.rotationDegrees(8.0F));
                float next = r * 0.78F;
                int color = i < 4 ? Colors.lerp(main, Colors.mixWhite(main, 0.25F), i / 4.0F) : accent;
                segment.frustum(r, next, 0.0F, -0.06F, 10, color, i == 0, i == HORN_SEGMENTS - 1);
                stack.translate(0.0F, -0.06F, 0.0F);
                r = next;
            }
        }
    }

    private static void hornTip(int side) {
        PoseStack stack = HORN_STACK;
        stack.translate(side * 0.17F, -0.01F, -0.12F);
        for (int i = 0; i < HORN_SEGMENTS; i++) {
            stack.mulPose(Axis.ZP.rotationDegrees(side * 14.0F));
            stack.mulPose(Axis.XP.rotationDegrees(8.0F));
            stack.translate(0.0F, -0.06F, 0.0F);
        }
    }

    private static void wings(PoseStack pose, SubmitNodeCollector collector, int light, float age, boolean flying, boolean crouching) {
        int style = Math.floorMod(ModSettings.cosmeticWingsStyle, Cosmetics.WINGS.length);
        int mode = ModSettings.cosmeticWingsColorMode;
        int root = Cosmetics.color(mode, ModSettings.cosmeticWingsColor, ModSettings.cosmeticWingsColor2, 0.0F);
        int tip = Cosmetics.color(mode, ModSettings.cosmeticWingsColor, ModSettings.cosmeticWingsColor2, 1.0F);
        float size = ModSettings.cosmeticWingsSize;
        float speed = ModSettings.cosmeticWingsSpeed * (flying ? 2.6F : 1.0F);
        float wave = Mth.sin(age * WING_TEMPO[style] * speed);
        float flap = style == 3 ? Math.signum(wave) * (float)Math.sqrt(Math.abs(wave)) : wave;
        boolean neon = style == Cosmetics.NEON_WINGS;
        float spread = crouching ? 62.0F : (flying ? 18.0F : (neon ? 14.0F : 32.0F));
        float amplitude = flying ? WING_SWING[style] * 1.8F : WING_SWING[style];
        if (ModSettings.cosmeticWingsPhysics) {
            float partialTick = Compat.partialTick();
            spread += CosmeticPhysics.spring(CosmeticPhysics.WING_SPREAD, partialTick);
            amplitude *= CosmeticPhysics.spring(CosmeticPhysics.WING_BEAT, partialTick);
        }

        float angle = spread + flap * amplitude;
        boolean glow = ModSettings.cosmeticWingsGlow || neon;
        RenderType type = glow ? RenderTypes.entityTranslucentEmissive(CosmeticTextures.wings(style)) : RenderTypes.entityTranslucent(CosmeticTextures.wings(style));
        int fx = Cosmetics.fxQuality();
        RenderType edgeType = RenderTypes.energySwirl(CosmeticTextures.wingsEdge(style), 0.0F, 0.0F);
        int wingLight = glow ? FULL_BRIGHT : light;
        float w = (neon ? 1.35F : 1.05F) * size;
        float h = (neon ? 0.95F : 1.1F) * size;
        int backRoot = Cosmetics.withAlpha(Colors.darken(root, 0.62F), 0.85F);
        int backTip = Cosmetics.withAlpha(Colors.darken(tip, 0.62F), 0.85F);
        int edge = glow(Colors.mixWhite(tip, 0.3F), neon ? 0.85F : glow ? 0.6F : 0.4F);
        int bloom = glow(tip, 0.22F);

        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 0.06F, 0.18F, 0.14F);
            pose.mulPose(Axis.YP.rotationDegrees(-side * angle));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -8.0F));
            float x = side * w;
            pose.pushPose();
            pose.translate(0.0F, 0.04F * h, -0.025F);
            pose.mulPose(Axis.YP.rotationDegrees(side * 7.0F + flap * side * 3.0F));
            collector.submitCustomGeometry(pose, type, (p, out) -> wingCard(new Geo(p, out, wingLight), x * 0.8F, h * 0.88F, 1.0F, backRoot, backTip));
            pose.popPose();
            collector.submitCustomGeometry(pose, type, (p, out) -> wingCard(new Geo(p, out, wingLight), x, h, 1.0F, root, tip));
            if (fx > 0) {
                collector.submitCustomGeometry(pose, edgeType, (p, out) -> wingCard(new Geo(p, out, FULL_BRIGHT), x, h, 1.0F, edge, edge));
            }

            if (fx == 2) {
                collector.submitCustomGeometry(pose, edgeType, (p, out) -> wingCard(new Geo(p, out, FULL_BRIGHT), x, h, 1.06F, bloom, bloom));
            }

            pose.popPose();
        }
    }

    private static void wingCard(Geo geo, float x, float h, float grow, int root, int tip) {
        float top = -0.32F * h * grow;
        float bottom = 0.68F * h * grow;
        float outer = x * grow;
        geo.card(0.0F, top, 0.0F, outer, top, 0.0F, outer, bottom, 0.0F, 0.0F, bottom, 0.0F, 0.0F, 1.0F, root, tip, tip, root);
    }

    private static void shapedCape(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float age) {
        int mode = ModSettings.cosmeticCapeColorMode;
        int c1 = Cosmetics.color(mode, ModSettings.cosmeticCapeColor, ModSettings.cosmeticCapeColor2, 0.0F);
        int c2 = Cosmetics.color(mode, ModSettings.cosmeticCapeColor, ModSettings.cosmeticCapeColor2, 1.0F);
        boolean armored = !state.chestEquipment.isEmpty();
        CosmeticPhysics.capeOffsetY = armored ? -0.053F : 0.0F;
        CosmeticPhysics.capeOffsetZ = armored ? 0.19F : 0.135F;
        pose.translate(0.0F, CosmeticPhysics.capeOffsetY, CosmeticPhysics.capeOffsetZ);
        int style = ModSettings.cosmeticCapeStyle;
        int fx = Cosmetics.fxQuality();
        if (style == Cosmetics.CAPELET_CAPE) {
            pose.pushPose();
            pose.translate(0.0F, 0.32F, 0.2F);
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> motes(new Geo(p, out, FULL_BRIGHT), age, c2, 0));
            if (fx > 0) {
                collector.submitCustomGeometry(pose, glowType(), (p, out) -> motes(new Geo(p, out, FULL_BRIGHT), age, c2, fx));
            }

            pose.popPose();
        }

        boolean physics = CosmeticPhysics.clothShown(Compat.partialTick());
        if (!physics) {
            float swing = 6.0F + state.capeLean / 2.0F + state.capeFlap;
            pose.mulPose(Axis.XP.rotationDegrees(style == Cosmetics.CAPELET_CAPE ? swing * 0.5F : swing));
            pose.mulPose(Axis.ZP.rotationDegrees(state.capeLean2 / 2.0F));
        }

        if (style == Cosmetics.SHARD_CAPE) {
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> shards(new Geo(p, out, FULL_BRIGHT), age, c1, c2, physics, 0));
            if (fx > 0) {
                collector.submitCustomGeometry(pose, glowType(), (p, out) -> shards(new Geo(p, out, FULL_BRIGHT), age, c1, c2, physics, fx));
            }
        } else if (style == Cosmetics.BANNER_CAPE) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CosmeticTextures.white()), (p, out) -> banner(new Geo(p, out, light), age, c1, c2, physics));
            if (fx > 0) {
                collector.submitCustomGeometry(pose, glowType(), (p, out) -> bannerTrim(new Geo(p, out, FULL_BRIGHT), age, c2, physics, fx));
            }
        } else if (style == Cosmetics.CAPELET_CAPE) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CosmeticTextures.white()), (p, out) -> capelet(new Geo(p, out, light), c1, c2, physics));
        } else {
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CosmeticTextures.white()), (p, out) -> {
                Geo geo = new Geo(p, out, light);
                for (int i = 0; i < RIBBON_X.length; i++) {
                    ribbon(geo, age, i, physics, c1, c2);
                }
            });
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> ribbonBeads(new Geo(p, out, FULL_BRIGHT), age, physics, c2, 0));
            if (fx > 0) {
                collector.submitCustomGeometry(pose, glowType(), (p, out) -> ribbonBeads(new Geo(p, out, FULL_BRIGHT), age, physics, c2, fx));
            }
        }
    }

    private static void ribbonPoint(int index, float t, float age, boolean physics, float[] out) {
        if (physics) {
            CosmeticPhysics.ribbonPoint(index, t, out);
            return;
        }

        float phase = index * 2.1F;
        out[0] = RIBBON_X[index] + Mth.sin(age * 0.07F + t * 3.0F + phase) * 0.035F * t;
        out[1] = t * RIBBON_LENGTH[index];
        out[2] = (index == 1 ? 0.012F : 0.0F) + Mth.sin(age * 0.16F - t * 5.5F + phase) * 0.05F * t;
    }

    private static void ribbon(Geo geo, float age, int index, boolean physics, int c1, int c2) {
        float rootHalf = RIBBON_HALF_WIDTH[index];
        float[] point = CLOTH_SAMPLE;
        ribbonPoint(index, 0.0F, age, physics, point);
        float prevX = point[0];
        float prevY = point[1];
        float prevZ = point[2];
        float prevHalf = rootHalf;
        int prevColor = c1;
        int rim = Cosmetics.withAlpha(Colors.mixWhite(c2, 0.55F), 0.9F);
        for (int s = 1; s <= RIBBON_SEGMENTS; s++) {
            float t = (float)s / RIBBON_SEGMENTS;
            ribbonPoint(index, t, age, physics, point);
            float x = point[0];
            float y = point[1];
            float z = point[2];
            float half = rootHalf * (1.0F - 0.35F * t);
            int color = Cosmetics.withAlpha(Colors.lerp(c1, c2, t), 1.0F - 0.2F * t);
            float dy = y - prevY;
            float dz = z - prevZ;
            float inv = Mth.invSqrt(Math.max(1.0E-8F, dy * dy + dz * dz));
            float ny = dz * inv;
            float nz = -dy * inv;
            if (s < RIBBON_SEGMENTS) {
                int back = Colors.darken(color, 0.55F);
                int prevBack = Colors.darken(prevColor, 0.55F);
                float by = ny * 0.004F;
                float bz = nz * 0.004F;
                geo.vertex(prevX - prevHalf, prevY + by, prevZ + bz, prevBack, 0.0F, 0.0F, 0.0F, ny, nz);
                geo.vertex(x - half, y + by, z + bz, back, 0.0F, 1.0F, 0.0F, ny, nz);
                geo.vertex(x + half, y + by, z + bz, back, 1.0F, 1.0F, 0.0F, ny, nz);
                geo.vertex(prevX + prevHalf, prevY + by, prevZ + bz, prevBack, 1.0F, 0.0F, 0.0F, ny, nz);
                geo.vertex(prevX - prevHalf, prevY, prevZ, prevColor, 0.0F, 0.0F, 0.0F, ny, nz);
                geo.vertex(prevX + prevHalf, prevY, prevZ, prevColor, 1.0F, 0.0F, 0.0F, ny, nz);
                geo.vertex(x + half, y, z, color, 1.0F, 1.0F, 0.0F, ny, nz);
                geo.vertex(x - half, y, z, color, 0.0F, 1.0F, 0.0F, ny, nz);
                float ry = -ny * 0.001F;
                float rz = -nz * 0.001F;
                for (int side = -1; side <= 1; side += 2) {
                    float outerPrev = prevX + side * prevHalf;
                    float outerNext = x + side * half;
                    geo.vertex(outerPrev, prevY + ry, prevZ + rz, rim, 0.0F, 0.0F, 0.0F, ny, nz);
                    geo.vertex(outerPrev - side * 0.008F, prevY + ry, prevZ + rz, rim, 1.0F, 0.0F, 0.0F, ny, nz);
                    geo.vertex(outerNext - side * 0.008F, y + ry, z + rz, rim, 1.0F, 1.0F, 0.0F, ny, nz);
                    geo.vertex(outerNext, y + ry, z + rz, rim, 0.0F, 1.0F, 0.0F, ny, nz);
                }
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

    private static void ribbonBeads(Geo geo, float age, boolean physics, int c2, int fx) {
        float[] point = CLOTH_SAMPLE;
        for (int i = 0; i < RIBBON_X.length; i++) {
            ribbonPoint(i, 1.0F, age, physics, point);
            float x = point[0];
            float y = point[1] + 0.035F;
            float z = point[2];
            if (fx == 0) {
                crossDiamond(geo, x, y, z, 0.018F, Cosmetics.withAlpha(Colors.mixWhite(c2, 0.6F), 0.95F));
                continue;
            }

            crossDiamond(geo, x, y, z, 0.045F, glow(c2, 0.45F));
            if (fx == 2) {
                crossDiamond(geo, x, y, z, 0.08F, glow(c2, 0.16F));
            }
        }
    }

    static void crossDiamond(Geo geo, float x, float y, float z, float size, int argb) {
        geo.vertex(x, y - size, z, argb, 0.5F, 0.0F, 0.0F, 0.0F, 1.0F);
        geo.vertex(x + size, y, z, argb, 1.0F, 0.5F, 0.0F, 0.0F, 1.0F);
        geo.vertex(x, y + size, z, argb, 0.5F, 1.0F, 0.0F, 0.0F, 1.0F);
        geo.vertex(x - size, y, z, argb, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F);
        geo.vertex(x, y - size, z, argb, 0.5F, 0.0F, 1.0F, 0.0F, 0.0F);
        geo.vertex(x, y, z + size, argb, 1.0F, 0.5F, 1.0F, 0.0F, 0.0F);
        geo.vertex(x, y + size, z, argb, 0.5F, 1.0F, 1.0F, 0.0F, 0.0F);
        geo.vertex(x, y, z - size, argb, 0.0F, 0.5F, 1.0F, 0.0F, 0.0F);
    }

    private static void shards(Geo geo, float age, int c1, int c2, boolean physics, int glowPass) {
        float[] sample = CLOTH_SAMPLE;
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
                if (physics) {
                    float sheetV = cy / 0.95F;
                    CosmeticPhysics.sample(cx / (0.25F + 0.05F * sheetV) * 0.5F + 0.5F, sheetV, sample);
                }

                if (glowPass > 0) {
                    shard(geo, physics, cx, cy, cz, halfWidth * 1.8F, halfHeight * 1.6F, 0.0F, glow(body, 0.14F + 0.12F * twinkle));
                    if (glowPass == 2) {
                        shard(geo, physics, cx, cy, cz, halfWidth * 2.6F, halfHeight * 2.2F, 0.0F, glow(body, 0.07F));
                    }

                    continue;
                }

                shard(geo, physics, cx, cy, cz, halfWidth * 1.08F, halfHeight * 1.06F, -0.008F, Cosmetics.withAlpha(Colors.darken(body, 0.35F), 0.85F));
                shard(geo, physics, cx, cy, cz, halfWidth, halfHeight, 0.0F, Cosmetics.withAlpha(body, 0.55F + 0.2F * twinkle));
                shard(geo, physics, cx, cy, cz, halfWidth * 0.45F, halfHeight * 0.45F, -0.004F, Cosmetics.withAlpha(Colors.mixWhite(body, 0.45F + 0.4F * twinkle), 0.9F));
            }
        }
    }

    private static void shard(Geo geo, boolean physics, float cx, float cy, float cz, float halfWidth, float halfHeight, float lift, int argb) {
        if (physics) {
            orientedDiamond(geo, halfWidth, halfHeight, lift, argb);
        } else {
            diamond(geo, cx, cy, cz + lift, halfWidth, halfHeight, argb);
        }
    }

    private static void orientedDiamond(Geo geo, float halfWidth, float halfHeight, float lift, int argb) {
        float[] s = CLOTH_SAMPLE;
        float ux = s[3];
        float uy = s[4];
        float uz = s[5];
        float vx = s[6];
        float vy = s[7];
        float vz = s[8];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float inverseU = Mth.invSqrt(Math.max(1.0E-8F, ux * ux + uy * uy + uz * uz)) * halfWidth;
        float inverseV = Mth.invSqrt(Math.max(1.0E-8F, vx * vx + vy * vy + vz * vz)) * halfHeight;
        float inverseN = Mth.invSqrt(Math.max(1.0E-8F, nx * nx + ny * ny + nz * nz));
        nx *= inverseN;
        ny *= inverseN;
        nz *= inverseN;
        float cx = s[0] + nx * lift;
        float cy = s[1] + ny * lift;
        float cz = s[2] + nz * lift;
        ux *= inverseU;
        uy *= inverseU;
        uz *= inverseU;
        vx *= inverseV;
        vy *= inverseV;
        vz *= inverseV;
        geo.vertex(cx - vx, cy - vy, cz - vz, argb, 0.5F, 0.0F, nx, ny, nz);
        geo.vertex(cx + ux, cy + uy, cz + uz, argb, 1.0F, 0.5F, nx, ny, nz);
        geo.vertex(cx + vx, cy + vy, cz + vz, argb, 0.5F, 1.0F, nx, ny, nz);
        geo.vertex(cx - ux, cy - uy, cz - uz, argb, 0.0F, 0.5F, nx, ny, nz);
    }

    private static void diamond(Geo geo, float cx, float cy, float cz, float halfWidth, float halfHeight, int argb) {
        geo.vertex(cx, cy - halfHeight, cz, argb, 0.5F, 0.0F, 0.0F, 0.0F, 1.0F);
        geo.vertex(cx + halfWidth, cy, cz, argb, 1.0F, 0.5F, 0.0F, 0.0F, 1.0F);
        geo.vertex(cx, cy + halfHeight, cz, argb, 0.5F, 1.0F, 0.0F, 0.0F, 1.0F);
        geo.vertex(cx - halfWidth, cy, cz, argb, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F);
    }

    static void pets(PoseStack pose, SubmitNodeCollector collector, int light, float age, float motion, boolean firstPerson) {
        int count = petTypes(PET_TYPES);
        for (int slot = 0; slot < count; slot++) {
            pet(pose, collector, light, age, motion, firstPerson, PET_TYPES[slot], slot);
        }
    }

    static int petTypes(int[] out) {
        int primary = ModSettings.cosmeticPetType;
        int limit = Math.clamp(ModSettings.cosmeticPetLimit, 1, Cosmetics.MAX_PETS);
        out[0] = primary;
        int extras = ModSettings.cosmeticPetExtras;
        int slot = 1;
        for (int type = 0; type < Cosmetics.PETS.length && slot < limit; type++) {
            if ((extras & 1 << type) != 0 && type != primary) {
                out[slot] = type;
                slot++;
            }
        }

        return slot;
    }

    static void chainAnchor(int type, int strand, float age, float[] out) {
        if (type == Cosmetics.JELLYFISH_PET) {
            float cycle = age * 0.09F;
            float contract = Cosmetics.reduceMotion() ? 0.0F : Math.max(0.0F, Mth.sin(cycle));
            float width = 1.0F - 0.18F * contract;
            float angle = Mth.TWO_PI * strand / TENTACLES + 0.3F;
            float baseX = Mth.cos(angle) * 0.11F * width;
            float baseZ = Mth.sin(angle) * 0.11F * width;
            float spin = age * 0.4F * Mth.DEG_TO_RAD;
            float cos = Mth.cos(spin);
            float sin = Mth.sin(spin);
            out[0] = baseX * cos + baseZ * sin;
            out[1] = -0.08F - Mth.sin(cycle - 0.8F) * 0.06F;
            out[2] = -baseX * sin + baseZ * cos;
        } else if (type == Cosmetics.KOI_PET) {
            float course = age * 0.03F + 0.4F * Mth.sin(age * 0.011F);
            float heading = (float)Mth.atan2(-Mth.sin(course), Mth.cos(course));
            float cos = Mth.cos(heading);
            float sin = Mth.sin(heading);
            int last = KOI_Z.length - 1;
            float tailX = Mth.sin(age * 0.25F - last * 0.7F) * 0.012F * last;
            float tailZ = KOI_Z[last];
            out[0] = Mth.cos(course) * 0.13F + tailX * cos + tailZ * sin;
            out[1] = Mth.sin(age * 0.06F) * 0.02F;
            out[2] = Mth.sin(course) * 0.13F - tailX * sin + tailZ * cos;
        } else {
            out[0] = 0.0F;
            out[1] = -0.22F;
            out[2] = 0.0F;
        }
    }

    private static void pet(PoseStack pose, SubmitNodeCollector collector, int light, float age, float motion, boolean firstPerson, int type, int slot) {
        boolean shaped = Cosmetics.shapedPet(type);
        float side = ModSettings.cosmeticPetSide == 1 ? 1.0F : -1.0F;
        float size = Cosmetics.petSize();
        float bob = shaped ? 0.0F : Mth.sin(age * 0.11F + slot * 2.0F) * 0.06F;
        float[] anchor = firstPerson ? PET_SLOTS_FIRST_PERSON[slot] : PET_SLOTS[slot];
        float partialTick = Compat.partialTick();
        boolean physics = ModSettings.cosmeticPetPhysics;
        float lagX = physics ? CosmeticPhysics.spring(CosmeticPhysics.PET_OFFSET + slot * 3, partialTick) : 0.0F;
        float lagY = physics ? CosmeticPhysics.spring(CosmeticPhysics.PET_OFFSET + slot * 3 + 1, partialTick) : 0.0F;
        float lagZ = physics ? CosmeticPhysics.spring(CosmeticPhysics.PET_OFFSET + slot * 3 + 2, partialTick) : 0.0F;
        if (GiantPets.giant(type)) {
            if (!firstPerson) {
                GiantPets.render(pose, collector, light, age, type, slot, lagX, lagY, lagZ);
            }

            return;
        }

        boolean chains = physics && CosmeticPhysics.chainShown(slot, type, partialTick);
        pose.pushPose();
        pose.translate(side * anchor[0] + lagX, anchor[1] + bob + lagY, anchor[2] + lagZ);

        if (!shaped) {
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(age * 0.03F) * 25.0F));
        }

        pose.scale(size, size, size);
        float glance = idle(age, slot, 140.0F, 30.0F) * idleSide(age, slot, 140.0F);
        float settle = idle(age, slot, 230.0F, 50.0F);
        float blink = idle(age, slot, 110.0F, 6.0F);
        if (type == Cosmetics.JELLYFISH_PET) {
            pose.translate(0.0F, settle * 0.04F, 0.0F);
            jellyfish(pose, collector, age, chains ? slot : -1);
        } else if (type == Cosmetics.ORRERY_PET) {
            pose.mulPose(Axis.XP.rotationDegrees(settle * 8.0F));
            orrery(pose, collector, light, age);
        } else if (type == Cosmetics.CRANE_PET) {
            pose.mulPose(Axis.YP.rotationDegrees(glance * 20.0F));
            crane(pose, collector, light, age);
        } else if (type == Cosmetics.KOI_PET) {
            koi(pose, collector, light, age, chains ? slot : -1, blink);
        } else if (type == Cosmetics.LANTERN_PET) {
            pose.translate(0.0F, settle * 0.015F, 0.0F);
            lantern(pose, collector, light, age, motion, chains ? slot : -1);
        } else if (type == Cosmetics.CLOUD_PET) {
            cloud(pose, collector, light, age, blink, settle);
        } else if (type <= 0 || type >= PET_ITEMS.length) {
            wisp(pose, collector, age, glance);
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(glance * 35.0F));
            pose.mulPose(Axis.XP.rotationDegrees(settle * 12.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            pose.scale(0.42F, 0.42F, 0.42F);
            Minecraft mc = Minecraft.getInstance();
            ItemStackRenderState itemState = PET_STATES[slot];
            mc.getItemModelResolver().updateForTopItem(itemState, PET_ITEMS[type], ItemDisplayContext.FIXED, mc.level, null, 0);
            itemState.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        }

        pose.popPose();
    }

    private static float idle(float age, int slot, float period, float duration) {
        float local = (age + slot * 53.0F) % period;
        return local >= duration ? 0.0F : Mth.sin(local / duration * (float)Math.PI);
    }

    private static float idleSide(float age, int slot, float period) {
        return ((int)((age + slot * 53.0F) / period) & 1) == 0 ? 1.0F : -1.0F;
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

    private static float[] octagon(boolean cosine) {
        float[] values = new float[LANTERN_SIDES + 1];
        for (int i = 0; i <= LANTERN_SIDES; i++) {
            float angle = Mth.TWO_PI * i / LANTERN_SIDES;
            values[i] = cosine ? Mth.cos(angle) : Mth.sin(angle);
        }

        return values;
    }

    private static float[] unitSphere() {
        float[] sphere = new float[SPHERE_LATITUDES * SPHERE_LONGITUDES * 12];
        int index = 0;
        for (int lat = 0; lat < SPHERE_LATITUDES; lat++) {
            for (int lon = 0; lon < SPHERE_LONGITUDES; lon++) {
                index = spherePoint(sphere, index, lat, lon);
                index = spherePoint(sphere, index, lat, lon + 1);
                index = spherePoint(sphere, index, lat + 1, lon + 1);
                index = spherePoint(sphere, index, lat + 1, lon);
            }
        }

        return sphere;
    }

    private static int spherePoint(float[] sphere, int index, int lat, int lon) {
        float theta = (float)Math.PI * lat / SPHERE_LATITUDES;
        float phi = Mth.TWO_PI * lon / SPHERE_LONGITUDES;
        sphere[index] = Mth.sin(theta) * Mth.cos(phi);
        sphere[index + 1] = -Mth.cos(theta);
        sphere[index + 2] = Mth.sin(theta) * Mth.sin(phi);
        return index + 3;
    }

    private static void bannerPoint(int k, float v, float age, boolean physics, float[] out, int offset) {
        float u = (float)k / BANNER_COLUMNS;
        if (physics) {
            float[] sample = CLOTH_SAMPLE;
            CosmeticPhysics.sample(u, v * (0.98F - BANNER_HEM[k]) / 0.98F, sample);
            sheetNormal(sample, out, offset + 3);
            float pleat = BANNER_PLEAT[k] * (0.4F + v);
            out[offset] = sample[0] + out[offset + 3] * pleat;
            out[offset + 1] = sample[1] + out[offset + 4] * pleat;
            out[offset + 2] = sample[2] + out[offset + 5] * pleat;
            return;
        }

        out[offset] = (u * 2.0F - 1.0F) * (0.25F + 0.06F * v);
        out[offset + 1] = v * (0.98F - BANNER_HEM[k]);
        out[offset + 2] = BANNER_PLEAT[k] * (0.4F + v) + Mth.sin(age * 0.07F - v * 3.0F + u * 1.5F) * 0.035F * v;
        out[offset + 3] = 0.0F;
        out[offset + 4] = 0.0F;
        out[offset + 5] = 1.0F;
    }

    private static void banner(Geo geo, float age, int c1, int c2, boolean physics) {
        float[] previous = BANNER_PREVIOUS;
        float[] current = BANNER_CURRENT;
        int liningBase = Colors.darken(c2, 0.55F);
        int rim = Colors.mixWhite(c1, 0.4F);
        int hem = Colors.mixWhite(c2, 0.2F);
        int previousColor = c1;
        for (int row = 0; row <= BANNER_ROWS; row++) {
            float v = (float)row / BANNER_ROWS;
            for (int k = 0; k <= BANNER_COLUMNS; k++) {
                bannerPoint(k, v, age, physics, current, k * 6);
            }

            int rowColor = Colors.lerp(c1, Colors.darken(c1, 0.72F), v);
            if (row > 0) {
                int fold = Colors.darken(previousColor, 0.88F);
                int lining = Colors.lerp(liningBase, Colors.darken(liningBase, 0.8F), v);
                for (int k = 0; k < BANNER_COLUMNS; k++) {
                    int a = k * 6;
                    int b = a + 6;
                    int color = (k & 1) == 0 ? previousColor : fold;
                    sheetQuad(geo, previous, current, a, b, 0.0F, 0.0F, 1.0F, color);
                    sheetQuad(geo, previous, current, a, b, -0.012F, 0.0F, 1.0F, lining);
                    if (row == BANNER_ROWS) {
                        sheetQuad(geo, previous, current, a, b, 0.001F, 0.7F, 1.0F, hem);
                    }
                }

                sheetQuad(geo, previous, current, 0, 6, 0.001F, 0.0F, 0.18F, rim);
                sheetQuad(geo, previous, current, BANNER_COLUMNS * 6 - 6, BANNER_COLUMNS * 6, 0.001F, 0.82F, 1.0F, rim);
            }

            System.arraycopy(current, 0, previous, 0, current.length);
            previousColor = rowColor;
        }
    }

    private static void sheetQuad(Geo geo, float[] previous, float[] current, int a, int b, float lift, float from, float to, int color) {
        if (from > 0.0F || to < 1.0F) {
            if (to <= 0.18F || from >= 0.82F) {
                float s0 = to <= 0.18F ? 0.0F : from;
                float s1 = to <= 0.18F ? to : 1.0F;
                geo.quad(
                    sheetLerp(previous, a, b, s0, 0, lift), sheetLerp(previous, a, b, s0, 1, lift), sheetLerp(previous, a, b, s0, 2, lift),
                    sheetLerp(previous, a, b, s1, 0, lift), sheetLerp(previous, a, b, s1, 1, lift), sheetLerp(previous, a, b, s1, 2, lift),
                    sheetLerp(current, a, b, s1, 0, lift), sheetLerp(current, a, b, s1, 1, lift), sheetLerp(current, a, b, s1, 2, lift),
                    sheetLerp(current, a, b, s0, 0, lift), sheetLerp(current, a, b, s0, 1, lift), sheetLerp(current, a, b, s0, 2, lift),
                    color
                );
                return;
            }

            geo.quad(
                rowLerp(previous, current, a, from, 0, lift), rowLerp(previous, current, a, from, 1, lift), rowLerp(previous, current, a, from, 2, lift),
                rowLerp(previous, current, b, from, 0, lift), rowLerp(previous, current, b, from, 1, lift), rowLerp(previous, current, b, from, 2, lift),
                current[b] + current[b + 3] * lift, current[b + 1] + current[b + 4] * lift, current[b + 2] + current[b + 5] * lift,
                current[a] + current[a + 3] * lift, current[a + 1] + current[a + 4] * lift, current[a + 2] + current[a + 5] * lift,
                color
            );
            return;
        }

        geo.quad(
            previous[a] + previous[a + 3] * lift, previous[a + 1] + previous[a + 4] * lift, previous[a + 2] + previous[a + 5] * lift,
            previous[b] + previous[b + 3] * lift, previous[b + 1] + previous[b + 4] * lift, previous[b + 2] + previous[b + 5] * lift,
            current[b] + current[b + 3] * lift, current[b + 1] + current[b + 4] * lift, current[b + 2] + current[b + 5] * lift,
            current[a] + current[a + 3] * lift, current[a + 1] + current[a + 4] * lift, current[a + 2] + current[a + 5] * lift,
            color
        );
    }

    private static float sheetLerp(float[] row, int a, int b, float t, int axis, float lift) {
        return Mth.lerp(t, row[a + axis] + row[a + 3 + axis] * lift, row[b + axis] + row[b + 3 + axis] * lift);
    }

    private static float rowLerp(float[] previous, float[] current, int a, float t, int axis, float lift) {
        return Mth.lerp(t, previous[a + axis] + previous[a + 3 + axis] * lift, current[a + axis] + current[a + 3 + axis] * lift);
    }

    private static void bannerTrim(Geo geo, float age, int c2, boolean physics, int fx) {
        float[] upper = BANNER_PREVIOUS;
        float[] lower = BANNER_CURRENT;
        for (int k = 0; k <= BANNER_COLUMNS; k++) {
            bannerPoint(k, 0.93F, age, physics, upper, k * 6);
            bannerPoint(k, 1.0F, age, physics, lower, k * 6);
        }

        int trim = glow(c2, 0.38F);
        int bloom = glow(c2, 0.12F);
        for (int k = 0; k < BANNER_COLUMNS; k++) {
            int a = k * 6;
            int b = a + 6;
            sheetQuad(geo, upper, lower, a, b, 0.003F, 0.0F, 1.0F, trim);
            if (fx == 2) {
                sheetQuad(geo, upper, lower, a, b, 0.012F, 0.0F, 1.0F, bloom);
            }
        }
    }

    private static void sheetNormal(float[] sample, float[] out, int offset) {
        float nx = sample[4] * sample[8] - sample[5] * sample[7];
        float ny = sample[5] * sample[6] - sample[3] * sample[8];
        float nz = sample[3] * sample[7] - sample[4] * sample[6];
        float lengthSquared = nx * nx + ny * ny + nz * nz;
        if (lengthSquared < 1.0E-10F) {
            out[offset] = 0.0F;
            out[offset + 1] = 0.0F;
            out[offset + 2] = 1.0F;
            return;
        }

        float inverse = Mth.invSqrt(lengthSquared);
        out[offset] = nx * inverse;
        out[offset + 1] = ny * inverse;
        out[offset + 2] = nz * inverse;
    }

    private static void capelet(Geo geo, int c1, int c2, boolean physics) {
        int top = Colors.mixWhite(c1, 0.12F);
        int bottom = Colors.darken(c1, 0.78F);
        int inside = Colors.darken(c1, 0.45F);
        int rim = Colors.mixWhite(c2, 0.45F);
        for (int row = 0; row < CAPELET_ROWS; row++) {
            float v0 = (float)row / CAPELET_ROWS;
            float v1 = (float)(row + 1) / CAPELET_ROWS;
            int color = row == CAPELET_ROWS - 1 ? c2 : Colors.lerp(top, bottom, v0);
            for (int k = 0; k < CAPELET_COLUMNS; k++) {
                capeletQuad(geo, k, v0, v1, physics, -0.01F, inside);
                capeletQuad(geo, k, v0, v1, physics, 0.0F, color);
                if (row == CAPELET_ROWS - 1) {
                    capeletQuad(geo, k, Mth.lerp(0.7F, v0, v1), v1, physics, 0.002F, rim);
                }
            }
        }
    }

    private static void capeletQuad(Geo geo, int k, float v0, float v1, boolean physics, float lift, int color) {
        float[] corners = CAPELET_CORNERS;
        float u0 = 2.0F * k / CAPELET_COLUMNS - 1.0F;
        float u1 = 2.0F * (k + 1) / CAPELET_COLUMNS - 1.0F;
        capeletPoint(corners, 0, u0, v0, CAPELET_DROP[k], physics, lift);
        capeletPoint(corners, 3, u1, v0, CAPELET_DROP[k + 1], physics, lift);
        capeletPoint(corners, 6, u1, v1, CAPELET_DROP[k + 1], physics, lift);
        capeletPoint(corners, 9, u0, v1, CAPELET_DROP[k], physics, lift);
        geo.quad(
            corners[0], corners[1], corners[2],
            corners[3], corners[4], corners[5],
            corners[6], corners[7], corners[8],
            corners[9], corners[10], corners[11],
            color
        );
    }

    private static void capeletPoint(float[] out, int offset, float centered, float v, float drop, boolean physics, float lift) {
        float depth = capeletDepth(centered, v) + lift;
        if (!physics) {
            out[offset] = centered * (0.28F + 0.05F * v);
            out[offset + 1] = v * drop;
            out[offset + 2] = depth;
            return;
        }

        float[] sample = CLOTH_SAMPLE;
        CosmeticPhysics.sample(centered * 0.5F + 0.5F, v * drop / 0.36F, sample);
        sheetNormal(sample, out, offset);
        out[offset] = sample[0] + out[offset] * depth;
        out[offset + 1] = sample[1] + out[offset + 1] * depth;
        out[offset + 2] = sample[2] + out[offset + 2] * depth;
    }

    private static float capeletDepth(float centered, float v) {
        return (0.02F + 0.035F * (1.0F - centered * centered)) * v;
    }

    private static void motes(Geo geo, float age, int c2, int glowPass) {
        int core = Cosmetics.withAlpha(Colors.mixWhite(c2, 0.35F), 0.95F);
        int halo = Cosmetics.withAlpha(c2, 0.25F);
        for (int i = 0; i < CAPELET_MOTES; i++) {
            float angle = age * 0.04F + Mth.TWO_PI * i / CAPELET_MOTES;
            float x = Mth.cos(angle) * 0.3F;
            float y = Mth.sin(angle) * 0.3F;
            float z = Mth.sin(angle * 2.0F) * 0.03F;
            if (glowPass > 0) {
                crossDiamond(geo, x, y, z, 0.07F, glow(c2, 0.3F));
                if (glowPass == 2) {
                    crossDiamond(geo, x, y, z, 0.12F, glow(c2, 0.1F));
                }

                continue;
            }

            diamond(geo, x, y, z, 0.045F, 0.045F, halo);
            diamond(geo, x, y, z - 0.002F, 0.02F, 0.022F, core);
            geo.vertex(x, y - 0.022F, z, core, 0.5F, 0.0F, 1.0F, 0.0F, 0.0F);
            geo.vertex(x, y, z + 0.02F, core, 1.0F, 0.5F, 1.0F, 0.0F, 0.0F);
            geo.vertex(x, y + 0.022F, z, core, 0.5F, 1.0F, 1.0F, 0.0F, 0.0F);
            geo.vertex(x, y, z - 0.02F, core, 0.0F, 0.5F, 1.0F, 0.0F, 0.0F);
        }
    }

    private static void koi(PoseStack pose, SubmitNodeCollector collector, int light, float age, int chainSlot, float blink) {
        if (chainSlot >= 0) {
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CosmeticTextures.white()), (p, out) -> koiTail(new Geo(p, out, light), chainSlot, age));
        }

        float course = age * 0.03F + 0.4F * Mth.sin(age * 0.011F);
        float headingX = -Mth.sin(course);
        float headingZ = Mth.cos(course);
        pose.pushPose();
        pose.translate(Mth.cos(course) * 0.13F, Mth.sin(age * 0.06F) * 0.02F, Mth.sin(course) * 0.13F);
        pose.mulPose(Axis.YP.rotation((float)Mth.atan2(headingX, headingZ)));
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CosmeticTextures.white()), (p, out) -> koiBody(new Geo(p, out, light), age, chainSlot < 0, blink));
        pose.popPose();
    }

    private static void koiTail(Geo geo, int slot, float age) {
        float[] anchor = CHAIN_ANCHOR;
        chainAnchor(Cosmetics.KOI_PET, 0, age, anchor);
        float shiftX = anchor[0] - CosmeticPhysics.chainPoint(slot, 0, 0);
        float shiftY = anchor[1] - CosmeticPhysics.chainPoint(slot, 0, 1);
        float shiftZ = anchor[2] - CosmeticPhysics.chainPoint(slot, 0, 2);
        int last = CosmeticPhysics.TAIL_POINTS - 1;
        for (int k = 0; k < last; k++) {
            float h0 = 0.012F + 0.043F * k / last;
            float h1 = 0.012F + 0.043F * (k + 1) / last;
            float x0 = CosmeticPhysics.chainPoint(slot, k, 0) + shiftX;
            float y0 = CosmeticPhysics.chainPoint(slot, k, 1) + shiftY;
            float z0 = CosmeticPhysics.chainPoint(slot, k, 2) + shiftZ;
            float x1 = CosmeticPhysics.chainPoint(slot, k + 1, 0) + shiftX;
            float y1 = CosmeticPhysics.chainPoint(slot, k + 1, 1) + shiftY;
            float z1 = CosmeticPhysics.chainPoint(slot, k + 1, 2) + shiftZ;
            geo.quad(x0, y0 - h0, z0, x1, y1 - h1, z1, x1, y1 + h1, z1, x0, y0 + h0, z0, KOI_FIN);
        }
    }

    private static void koiBody(Geo geo, float age, boolean fin, float blink) {
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
        if (fin) {
            geo.quad(tailX, 0.0F, tailZ, tailX + flick, -0.055F, tailZ - 0.07F, tailX + flick * 0.5F, 0.0F, tailZ - 0.045F, tailX + flick * 0.5F, 0.0F, tailZ - 0.045F, KOI_FIN);
            geo.quad(tailX, 0.0F, tailZ, tailX + flick, 0.055F, tailZ - 0.07F, tailX + flick * 0.5F, 0.0F, tailZ - 0.045F, tailX + flick * 0.5F, 0.0F, tailZ - 0.045F, KOI_FIN);
        }

        float paddle = Mth.sin(age * 0.2F) * 0.012F;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * KOI_RADIUS[1];
            geo.quad(x, 0.01F, 0.06F, x + side * 0.05F, 0.025F + paddle, 0.03F, x, 0.01F, 0.035F, x, 0.01F, 0.035F, KOI_FIN);
            float eyeX = side * KOI_RADIUS[1] * 0.92F;
            float lid = 0.005F * blink;
            geo.quad(eyeX, -0.018F + lid, 0.07F, eyeX, -0.018F + lid, 0.06F, eyeX, -0.008F - lid, 0.06F, eyeX, -0.008F - lid, 0.07F, 0xFF1A1A1A);
            geo.quad(eyeX * 1.01F, -0.016F + lid, 0.068F, eyeX * 1.01F, -0.016F + lid, 0.065F, eyeX * 1.01F, -0.013F, 0.065F, eyeX * 1.01F, -0.013F, 0.068F, 0xFFF7F3EC);
        }
    }

    private static void lantern(PoseStack pose, SubmitNodeCollector collector, int light, float age, float motion, int chainSlot) {
        float sway = 6.0F + 20.0F * Math.clamp(motion, 0.0F, 1.0F);
        float glow = Cosmetics.reduceMotion() ? 1.0F : 0.92F + 0.08F * Mth.sin(age * 0.17F) * Mth.sin(age * 0.071F);
        int paper = Colors.darken(LANTERN_PAPER, glow);
        int rib = Colors.darken(paper, 0.86F);
        int core = Cosmetics.withAlpha(0xFFFFF1C1, 0.55F * glow);
        pose.pushPose();
        if (chainSlot >= 0) {
            float[] anchor = CHAIN_ANCHOR;
            chainAnchor(Cosmetics.LANTERN_PET, 0, age, anchor);
            int last = CosmeticPhysics.LANTERN_POINTS - 1;
            float shiftX = anchor[0] - CosmeticPhysics.chainPoint(chainSlot, 0, 0);
            float shiftY = anchor[1] - CosmeticPhysics.chainPoint(chainSlot, 0, 1);
            float shiftZ = anchor[2] - CosmeticPhysics.chainPoint(chainSlot, 0, 2);
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CosmeticTextures.white()), (p, out) -> lanternString(new Geo(p, out, light), chainSlot, shiftX, shiftY, shiftZ));
            float endX = CosmeticPhysics.chainPoint(chainSlot, last, 0) + shiftX;
            float endY = CosmeticPhysics.chainPoint(chainSlot, last, 1) + shiftY;
            float endZ = CosmeticPhysics.chainPoint(chainSlot, last, 2) + shiftZ;
            float dx = endX - CosmeticPhysics.chainPoint(chainSlot, last - 1, 0) - shiftX;
            float dy = endY - CosmeticPhysics.chainPoint(chainSlot, last - 1, 1) - shiftY;
            float dz = endZ - CosmeticPhysics.chainPoint(chainSlot, last - 1, 2) - shiftZ;
            float inverse = Mth.invSqrt(Math.max(1.0E-8F, dx * dx + dy * dy + dz * dz));
            pose.translate(endX, endY, endZ);
            pose.mulPose(Axis.ZP.rotation((float)Mth.atan2(-dx, dy)));
            pose.mulPose(Axis.XP.rotation((float)Math.asin(Math.clamp(dz * inverse, -1.0F, 1.0F))));
            pose.mulPose(Axis.YP.rotationDegrees(age * 0.3F));
            pose.translate(0.0F, -0.1F, 0.0F);
        } else {
            pose.translate(0.0F, -0.22F, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.08F) * sway));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 0.061F + 1.3F) * sway * 0.5F));
            pose.mulPose(Axis.YP.rotationDegrees(age * 0.3F));
        }

        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(CosmeticTextures.white()), (p, out) -> lanternFrame(new Geo(p, out, light), age, chainSlot < 0));
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, FULL_BRIGHT);
            for (int ring = 0; ring < LANTERN_RADIUS.length - 1; ring++) {
                lanternBand(geo, LANTERN_RADIUS[ring], LANTERN_Y[ring], LANTERN_RADIUS[ring + 1], LANTERN_Y[ring + 1], paper, rib);
                lanternBand(geo, LANTERN_RADIUS[ring] * 0.45F, LANTERN_Y[ring], LANTERN_RADIUS[ring + 1] * 0.45F, LANTERN_Y[ring + 1], core, core);
            }
        });
        int fx = Cosmetics.fxQuality();
        if (fx > 0) {
            int warm = glow(paper, 0.22F * glow);
            int haze = glow(paper, 0.08F * glow);
            collector.submitCustomGeometry(pose, glowType(), (p, out) -> {
                Geo geo = new Geo(p, out, FULL_BRIGHT);
                lanternBand(geo, 0.11F, 0.15F, 0.12F, 0.21F, warm, warm);
                lanternBand(geo, 0.12F, 0.21F, 0.11F, 0.27F, warm, warm);
                if (fx == 2) {
                    lanternBand(geo, 0.16F, 0.13F, 0.17F, 0.21F, haze, haze);
                    lanternBand(geo, 0.17F, 0.21F, 0.16F, 0.29F, haze, haze);
                }
            });
        }

        pose.popPose();
    }

    private static void lanternString(Geo geo, int slot, float shiftX, float shiftY, float shiftZ) {
        for (int k = 0; k < CosmeticPhysics.LANTERN_POINTS - 1; k++) {
            float x0 = CosmeticPhysics.chainPoint(slot, k, 0) + shiftX;
            float y0 = CosmeticPhysics.chainPoint(slot, k, 1) + shiftY;
            float z0 = CosmeticPhysics.chainPoint(slot, k, 2) + shiftZ;
            float x1 = CosmeticPhysics.chainPoint(slot, k + 1, 0) + shiftX;
            float y1 = CosmeticPhysics.chainPoint(slot, k + 1, 1) + shiftY;
            float z1 = CosmeticPhysics.chainPoint(slot, k + 1, 2) + shiftZ;
            geo.quad(x0 - 0.004F, y0, z0, x0 + 0.004F, y0, z0, x1 + 0.004F, y1, z1, x1 - 0.004F, y1, z1, LANTERN_WOOD);
            geo.quad(x0, y0, z0 - 0.004F, x0, y0, z0 + 0.004F, x1, y1, z1 + 0.004F, x1, y1, z1 - 0.004F, LANTERN_WOOD);
        }
    }

    private static void lanternFrame(Geo geo, float age, boolean string) {
        if (string) {
            geo.quad(-0.004F, 0.0F, 0.0F, 0.004F, 0.0F, 0.0F, 0.004F, 0.1F, 0.0F, -0.004F, 0.1F, 0.0F, LANTERN_WOOD);
        }

        lanternBand(geo, 0.03F, 0.1F, 0.058F, 0.125F, LANTERN_WOOD, LANTERN_WOOD);
        lanternBand(geo, 0.058F, 0.295F, 0.045F, 0.315F, LANTERN_WOOD, LANTERN_WOOD);
        for (int i = 0; i < 3; i++) {
            float x = (i - 1) * 0.012F + Mth.sin(age * 0.09F + i) * 0.004F;
            geo.quad(x - 0.003F, 0.315F, 0.0F, x + 0.003F, 0.315F, 0.0F, x + 0.003F, 0.4F, 0.0F, x - 0.003F, 0.4F, 0.0F, LANTERN_TASSEL);
        }
    }

    private static void lanternBand(Geo geo, float r0, float y0, float r1, float y1, int color, int alternate) {
        for (int i = 0; i < LANTERN_SIDES; i++) {
            float cos0 = LANTERN_COS[i];
            float sin0 = LANTERN_SIN[i];
            float cos1 = LANTERN_COS[i + 1];
            float sin1 = LANTERN_SIN[i + 1];
            geo.quad(r0 * cos0, y0, r0 * sin0, r0 * cos1, y0, r0 * sin1, r1 * cos1, y1, r1 * sin1, r1 * cos0, y1, r1 * sin0, (i & 1) == 0 ? color : alternate);
        }
    }

    private static void cloud(PoseStack pose, SubmitNodeCollector collector, int light, float age, float blink, float settle) {
        float breathe = (Cosmetics.reduceMotion() ? 1.0F : 1.0F + 0.04F * Mth.sin(age * 0.05F)) - settle * 0.06F;
        float rain = Math.clamp(Mth.sin(age * 0.012F) * 2.0F + 0.5F, 0.0F, 1.0F);
        pose.pushPose();
        pose.translate(Mth.sin(age * 0.02F) * 0.05F, -0.04F, Mth.cos(age * 0.017F) * 0.03F);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, light);
            for (int i = 0; i < CLOUD_PUFFS.length; i += 4) {
                puff(geo, CLOUD_PUFFS[i], CLOUD_PUFFS[i + 1], CLOUD_PUFFS[i + 2], CLOUD_PUFFS[i + 3], breathe);
            }

            float lid = 0.008F * (1.0F - blink * 0.85F);
            for (int side = -1; side <= 1; side += 2) {
                float x = side * 0.032F;
                geo.quad(x - 0.007F, -lid, -0.092F, x + 0.007F, -lid, -0.092F, x + 0.007F, lid, -0.092F, x - 0.007F, lid, -0.092F, 0xFF3B4656);
            }

            if (rain <= 0.0F) {
                return;
            }

            for (int i = 0; i < CLOUD_DROPS.length; i += 2) {
                float phase = age * 0.045F + i * 0.085F;
                phase -= Mth.floor(phase);
                float x = CLOUD_DROPS[i];
                float z = CLOUD_DROPS[i + 1];
                float y = 0.06F + phase * 0.35F;
                int drop = Cosmetics.withAlpha(RAIN, 0.7F * rain * (1.0F - phase));
                geo.quad(x - 0.003F, y, z, x + 0.003F, y, z, x + 0.003F, y + 0.05F, z, x - 0.003F, y + 0.05F, z, drop);
                geo.quad(x, y, z - 0.003F, x, y, z + 0.003F, x, y + 0.05F, z + 0.003F, x, y + 0.05F, z - 0.003F, drop);
            }
        });
        pose.popPose();
    }

    private static void puff(Geo geo, float cx, float cy, float cz, float radius, float breathe) {
        float[] sphere = SPHERE;
        float vertical = radius * breathe;
        for (int i = 0; i < sphere.length; i += 3) {
            float nx = sphere[i];
            float ny = sphere[i + 1];
            float nz = sphere[i + 2];
            int color = Colors.lerp(CLOUD_TOP, CLOUD_BOTTOM, (ny + 1.0F) * 0.5F);
            geo.vertex(cx + nx * radius, cy + ny * vertical, cz + nz * radius, color, 0.0F, 0.0F, nx, ny, nz);
        }
    }

    private static int petColor(float t) {
        return Cosmetics.color(ModSettings.cosmeticPetColorMode, ModSettings.cosmeticPetColor, ModSettings.cosmeticPetColor2, t);
    }

    private static void jellyfish(PoseStack pose, SubmitNodeCollector collector, float age, int chainSlot) {
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
        if (chainSlot >= 0) {
            pose.popPose();
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> tentacleChains(new Geo(p, out, FULL_BRIGHT), chainSlot, age, tentacle));
            pose.pushPose();
            pose.translate(0.0F, -0.08F - Mth.sin(cycle - 0.8F) * 0.06F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(age * 0.4F));
        }

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
            for (int i = 0; i < TENTACLES && chainSlot < 0; i++) {
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
        if (Cosmetics.fxQuality() > 0) {
            int sheen = glow(c2, 0.22F);
            collector.submitCustomGeometry(pose, glowType(), (p, out) -> {
                Geo geo = new Geo(p, out, FULL_BRIGHT);
                geo.frustum(0.19F * widthScale, 0.17F * widthScale, 0.015F, -0.07F * heightScale, 16, sheen, false, false);
                geo.ball(0.0F, -0.08F * heightScale, 0.0F, 0.07F, sheen);
            });
        }

        pose.popPose();
    }

    private static void tentacleChains(Geo geo, int slot, float age, int tentacle) {
        float[] anchor = CHAIN_ANCHOR;
        for (int i = 0; i < TENTACLES; i++) {
            chainAnchor(Cosmetics.JELLYFISH_PET, i, age, anchor);
            int first = i * CosmeticPhysics.TENTACLE_POINTS;
            float prevX = anchor[0];
            float prevY = anchor[1];
            float prevZ = anchor[2];
            float shiftX = anchor[0] - CosmeticPhysics.chainPoint(slot, first, 0);
            float shiftY = anchor[1] - CosmeticPhysics.chainPoint(slot, first, 1);
            float shiftZ = anchor[2] - CosmeticPhysics.chainPoint(slot, first, 2);
            for (int k = 1; k < CosmeticPhysics.TENTACLE_POINTS; k++) {
                float t = (float)k / (CosmeticPhysics.TENTACLE_POINTS - 1);
                float x = CosmeticPhysics.chainPoint(slot, first + k, 0) + shiftX;
                float y = CosmeticPhysics.chainPoint(slot, first + k, 1) + shiftY;
                float z = CosmeticPhysics.chainPoint(slot, first + k, 2) + shiftZ;
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
            geo.frustum(0.074F, 0.074F, 0.006F, -0.006F, 16, Colors.mixWhite(ringColor, 0.35F), false, false);
        });
        int fx = Cosmetics.fxQuality();
        if (fx > 0) {
            int corona = glow(c2, 0.28F);
            int outer = glow(c2, 0.1F);
            collector.submitCustomGeometry(pose, glowType(), (p, out) -> {
                Geo geo = new Geo(p, out, FULL_BRIGHT);
                geo.ball(0.0F, 0.0F, 0.0F, 0.095F, corona);
                if (fx == 2) {
                    geo.ball(0.0F, 0.0F, 0.0F, 0.13F, outer);
                }
            });
        }

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
            int[] colors = ORBIT_COLORS;
            for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
                colors[i] = Colors.lerp(ringColor, Colors.mixWhite(ringColor, 0.3F), ring((float)i / ORBIT_SEGMENTS));
            }

            geo.torus(radius, 0.006F, ORBIT_SEGMENTS, 4, colors);
            geo.ball(px, 0.0F, pz, planetRadius, planetColor);
            geo.ball(px - planetRadius * 0.3F, -planetRadius * 0.3F, pz - planetRadius * 0.3F, planetRadius * 0.45F, Colors.mixWhite(planetColor, 0.4F));
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
            geo.tri(0.0F, 0.0F, 0.09F, 0.0F, 0.0F, -0.09F, -0.022F, 0.035F, 0.0F, fold);
            geo.tri(0.0F, 0.0F, 0.09F, 0.0F, 0.0F, -0.09F, 0.022F, 0.035F, 0.0F, crease);
            geo.tri(0.0F, 0.0F, 0.05F, 0.0F, 0.0F, 0.09F, 0.0F, -0.13F, 0.15F, paper);
            geo.tri(0.0F, -0.13F, 0.15F, 0.0F, -0.115F, 0.135F, 0.0F, -0.1F, 0.2F, tipColor);
            geo.tri(0.0F, 0.0F, -0.05F, 0.0F, 0.0F, -0.09F, 0.0F, -0.12F, -0.15F, paper);
            for (int side = -1; side <= 1; side += 2) {
                float tipX = side * 0.21F;
                float midX = side * 0.09F;
                float midY = lift * 0.45F - 0.012F;
                geo.tri(0.0F, -0.01F, 0.05F, midX, midY, 0.005F, tipX, lift, -0.03F, paper);
                geo.tri(0.0F, -0.01F, 0.05F, 0.0F, -0.01F, -0.06F, midX, midY, 0.005F, fold);
                geo.tri(0.0F, -0.01F, -0.06F, tipX, lift, -0.03F, midX, midY, 0.005F, crease);
                geo.tri(midX, midY - 0.001F, 0.005F, tipX, lift - 0.001F, -0.03F, tipX * 0.92F, lift - 0.001F, -0.018F, Colors.lerp(paper, tipColor, 0.6F));
            }
        });
        pose.popPose();
    }

    private static void wisp(PoseStack pose, SubmitNodeCollector collector, float age, float glance) {
        int c1 = petColor(0.0F);
        int c2 = petColor(1.0F);
        float pulse = Cosmetics.reduceMotion() ? 1.0F : 1.0F + Mth.sin(age * 0.25F) * 0.08F;
        float lookX = glance * 0.025F;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> {
            Geo geo = new Geo(p, out, FULL_BRIGHT);
            geo.ball(lookX, 0.0F, -0.01F, 0.07F * pulse, Cosmetics.withAlpha(0xFFFFFF, 0.95F));
            geo.ball(0.0F, 0.0F, 0.0F, 0.11F * pulse, Cosmetics.withAlpha(c1, 0.45F));
            for (int i = 0; i < 3; i++) {
                float a = age * 0.12F + Mth.TWO_PI * i / 3.0F;
                geo.cube(Mth.cos(a) * 0.22F, Mth.sin(a * 1.7F) * 0.05F, Mth.sin(a) * 0.22F, 0.018F, Cosmetics.withAlpha(i == 1 ? c2 : c1, 0.9F));
            }

            for (int i = 1; i <= 4; i++) {
                float trail = i * 0.045F;
                geo.cube(0.0F, trail * 0.4F, trail, 0.035F - i * 0.006F, Cosmetics.withAlpha(c1, 0.5F - i * 0.1F));
            }
        });
        int fx = Cosmetics.fxQuality();
        if (fx == 0) {
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CosmeticTextures.white()), (p, out) -> new Geo(p, out, FULL_BRIGHT).ball(0.0F, 0.0F, 0.0F, 0.16F * pulse, Cosmetics.withAlpha(c2, 0.16F)));
            return;
        }

        int aura = glow(c2, 0.3F);
        int haze = glow(c1, 0.12F);
        collector.submitCustomGeometry(pose, glowType(), (p, out) -> {
            Geo geo = new Geo(p, out, FULL_BRIGHT);
            geo.ball(0.0F, 0.0F, 0.0F, 0.16F * pulse, aura);
            if (fx == 2) {
                geo.ball(0.0F, 0.0F, 0.0F, 0.24F * pulse, haze);
            }
        });
    }
}
