package dev.infectedware.skysong.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.Mesh;
import dev.infectedware.skysong.compat.Shader;
import dev.infectedware.skysong.compat.VertexSink;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class LyricLayers {
    private static final Shader TEXT = Shader.of("fx_text");
    private static final VertexConsumer IGNORE = new VertexSink() {
        @Override
        protected void onVertex(float x, float y, float z) {
        }
    };

    private LyricLayers() {
    }

    public static MultiBufferSource remap(LyricLayers.Batch batch, LyricLayers.Pass pass, boolean throughWalls, boolean smooth) {
        batch.pass = pass;
        batch.throughWalls = throughWalls;
        batch.smooth = smooth;
        return batch;
    }

    public static final class Batch implements MultiBufferSource {
        private LyricLayers.Pass pass = LyricLayers.Pass.MAIN;
        private boolean throughWalls;
        private boolean smooth;
        private final Map<List<Object>, LyricLayers.Glyphs> byTexture = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType layer) {
            Identifier texture = Compat.layerTexture(layer);
            if (texture == null) {
                return LyricLayers.IGNORE;
            }

            boolean intensity = layer.toString().contains("intensity");
            return this.byTexture
                .computeIfAbsent(
                    List.of(texture, intensity, this.pass, this.throughWalls, this.smooth),
                    k -> new LyricLayers.Glyphs(texture, intensity, this.pass, this.throughWalls, this.smooth)
                );
        }

        public void endBatch() {
            if (!this.byTexture.isEmpty()) {
                try {
                    for (LyricLayers.Glyphs g : this.byTexture.values()) {
                        g.draw();
                    }
                } finally {
                    this.byTexture.clear();
                    Gpu.clearTextures();
                    Gpu.disableBlend();
                    Gpu.defaultBlendFunc();
                    Gpu.enableDepthTest();
                    Gpu.depthMask(true);
                    Gpu.enableCull();
                }
            }
        }
    }

    private static final class Glyphs extends VertexSink {
        final Identifier texture;
        final boolean intensity;
        final boolean throughWalls;
        final boolean smooth;
        final LyricLayers.Pass pass;
        final List<float[]> vertices = new ArrayList<>();
        float[] current;

        Glyphs(Identifier texture, boolean intensity, LyricLayers.Pass pass, boolean throughWalls, boolean smooth) {
            this.texture = texture;
            this.intensity = intensity;
            this.pass = pass;
            this.throughWalls = throughWalls;
            this.smooth = smooth;
        }

        @Override
        protected void onVertex(float x, float y, float z) {
            this.current = new float[]{x, y, z, 255.0F, 255.0F, 255.0F, 255.0F, 0.0F, 0.0F};
            this.vertices.add(this.current);
        }

        @Override
        protected void onColor(int r, int g, int b, int a) {
            if (this.current != null) {
                this.current[3] = r;
                this.current[4] = g;
                this.current[5] = b;
                this.current[6] = a;
            }
        }

        @Override
        protected void onUv(float u, float v) {
            if (this.current != null) {
                this.current[7] = u;
                this.current[8] = v;
            }
        }

        void draw() {
            if (this.vertices.size() >= 4) {
                int tex = Gpu.textureId(this.texture, this.smooth);
                Gpu.enableBlend();
                Gpu.blendFuncSeparate(770, 771, 1, 771);
                if (this.throughWalls) {
                    Gpu.disableDepthTest();
                } else {
                    Gpu.enableDepthTest();
                }

                Gpu.depthMask(this.pass == LyricLayers.Pass.MAIN && !this.throughWalls);
                Gpu.disableCull();
                Gpu.texture(0, tex);
                LyricLayers.TEXT.set("TextMode", this.intensity ? 1.0F : 0.0F);
                LyricLayers.TEXT.bind();
                Mesh m = Mesh.begin(Mode.QUADS);

                for (float[] v : this.vertices) {
                    m.addVertex(v[0], v[1], v[2]).setColor((int)v[3], (int)v[4], (int)v[5], (int)v[6]).setUv(v[7], v[8]);
                }

                m.draw();
                if (this.smooth) {
                    Gpu.textureId(this.texture, false);
                }
            }
        }
    }

    public enum Pass {
        GLOW,
        OUTLINE,
        MAIN
    }
}
