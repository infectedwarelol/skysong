package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.Mesh;
import dev.infectedware.skysong.compat.VertexSink;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

final class ModelSnapshot {
    private static boolean recording;
    private static final VertexConsumer IGNORE = new VertexSink() {
        @Override
        protected void onVertex(float x, float y, float z) {
        }
    };

    private ModelSnapshot() {
    }

    static boolean recording() {
        return recording;
    }

    static List<ModelSnapshot.Part> record(Entity e, float tickDelta) {
        Map<RenderType, ModelSnapshot.Part> parts = new IdentityHashMap<>();
        MultiBufferSource recorder = layer -> layer.format() == DefaultVertexFormat.NEW_ENTITY && layer.mode() == Mode.QUADS
            ? new ModelSnapshot.Recorder(parts.computeIfAbsent(layer, ModelSnapshot.Part::new))
            : IGNORE;
        recording = true;

        try {
            Compat.renderEntity(e, 0.0, 0.0, 0.0, tickDelta, new PoseStack(), recorder);
        } finally {
            recording = false;
        }

        List<ModelSnapshot.Part> list = new ArrayList<>();

        for (ModelSnapshot.Part part : parts.values()) {
            if (part.vertices >= 4) {
                list.add(part);
            }
        }

        return list;
    }

    static TextureTarget beginCapture(TextureTarget capture, RenderTarget main) {
        capture = Fx.ensure(capture, main.width, main.height, true, true);
        Fx.clearAndBind(capture);
        Gpu.blitDepth(main, capture);
        Gpu.bind(capture);
        return capture;
    }

    static void draw(ModelSnapshot.Part part, RenderTarget capture, ModelSnapshot.Transform transform, int alpha) {
        Mesh b = Mesh.begin(Mode.QUADS);
        float[] d = part.data;
        float[] pos = new float[3];
        float[] normal = new float[3];

        for (int v = 0; v < part.vertices; v++) {
            int i = v * 8;
            pos[0] = d[i];
            pos[1] = d[i + 1];
            pos[2] = d[i + 2];
            normal[0] = d[i + 5];
            normal[1] = d[i + 6];
            normal[2] = d[i + 7];
            transform.apply(v, pos, normal);
            b.addVertex(pos[0], pos[1], pos[2])
                .setColor(255, 255, 255, alpha)
                .setUv(d[i + 3], d[i + 4])
                .setNormal(normal[0], normal[1], normal[2]);
        }

        if (b.isEmpty()) {
            b.discard();
        } else {
            Gpu.bind(capture);
            Gpu.texture(0, part.texture);
            Fx.ghostGeo.bind();
            Gpu.disableBlend();
            Gpu.disableCull();
            Gpu.enableDepthTest();
            Gpu.depthMask(true);
            b.draw();
        }
    }

    static final class Composite {
        private TextureTarget blurA;
        private TextureTarget blurB;

        void apply(RenderTarget capture, RenderTarget main, float opacity, float glowRadius, float glowStrength, float[] tint) {
            int w = main.width;
            int h = main.height;
            boolean glow = glowStrength > 0.0F && glowRadius > 0.5F;
            if (glow) {
                int hw = Math.max(1, w / 2);
                int hh = Math.max(1, h / 2);
                this.blurA = Fx.ensure(this.blurA, hw, hh, false, true);
                this.blurB = Fx.ensure(this.blurB, hw, hh, false, true);
                float radius = Fx.px(glowRadius) / 2.0F;
                Gpu.bind(this.blurA);
                Fx.noBlend();
                Gpu.texture(0, Gpu.color(capture));
                Fx.set(Fx.blur, "Blur", 1.0F / hw, 0.0F, radius, 0.0F);
                Fx.draw(Fx.blur);
                Gpu.bind(this.blurB);
                Gpu.texture(0, Gpu.color(this.blurA));
                Fx.set(Fx.blur, "Blur", 0.0F, 1.0F / hh, radius, 0.0F);
                Fx.draw(Fx.blur);
            }

            Gpu.bind(main);
            Fx.blendOver();
            Gpu.texture(0, Gpu.color(capture));
            Gpu.texture(1, glow ? Gpu.color(this.blurB) : Gpu.color(capture));
            Gpu.texture(2, Gpu.color(capture));
            Fx.set(Fx.echo, "Look", opacity, glow ? glowStrength : 0.0F, 0.0F, 0.35F);
            Fx.setTints(Fx.echo, tint);
            Fx.draw(Fx.echo);
        }
    }

    static final class Part {
        float[] data = new float[512];
        int vertices;
        final int texture;

        Part(RenderType layer) {
            Identifier location = Compat.layerTexture(layer);
            this.texture = location == null ? 0 : Gpu.textureId(location, false);
        }

        void add() {
            if ((this.vertices + 1) * 8 > this.data.length) {
                this.data = Arrays.copyOf(this.data, this.data.length * 2);
            }

            this.vertices++;
        }
    }

    private static final class Recorder extends VertexSink {
        final ModelSnapshot.Part part;

        Recorder(ModelSnapshot.Part part) {
            this.part = part;
        }

        @Override
        protected void onVertex(float x, float y, float z) {
            this.part.add();
            int i = (this.part.vertices - 1) * 8;
            this.part.data[i] = x;
            this.part.data[i + 1] = y;
            this.part.data[i + 2] = z;
            this.part.data[i + 5] = 0.0F;
            this.part.data[i + 6] = 1.0F;
            this.part.data[i + 7] = 0.0F;
        }

        @Override
        protected void onUv(float u, float v) {
            if (this.part.vertices > 0) {
                int i = (this.part.vertices - 1) * 8;
                this.part.data[i + 3] = u;
                this.part.data[i + 4] = v;
            }
        }

        @Override
        protected void onNormal(float x, float y, float z) {
            if (this.part.vertices > 0) {
                int i = (this.part.vertices - 1) * 8;
                this.part.data[i + 5] = x;
                this.part.data[i + 6] = y;
                this.part.data[i + 7] = z;
            }
        }
    }

    interface Transform {
        void apply(int var1, float[] var2, float[] var3);
    }
}
