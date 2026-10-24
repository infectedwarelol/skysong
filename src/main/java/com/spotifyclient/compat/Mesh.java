package com.spotifyclient.compat;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

public final class Mesh {
    private static final int FLOATS = 12;
    private static int vao = -1;
    private static int vbo;
    private static int ibo;
    private static int indexedQuads;
    private static FloatBuffer data = MemoryUtil.memAllocFloat(49152);
    private final Mode mode;
    private int vertices;
    private float minX = Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE;
    private float maxY = -Float.MAX_VALUE;
    private final float[] v = new float[12];
    private boolean open;

    private Mesh(Mode mode) {
        this.mode = mode;
        data.clear();
    }

    public static Mesh begin(Mode mode, VertexFormat format) {
        return new Mesh(mode);
    }

    private void flush() {
        if (this.open) {
            this.open = false;
            if (data.remaining() < 12) {
                FloatBuffer bigger = MemoryUtil.memAllocFloat(data.capacity() * 2);
                data.flip();
                bigger.put(data);
                MemoryUtil.memFree(data);
                data = bigger;
            }

            data.put(this.v);
            this.vertices++;
        }
    }

    public Mesh addVertex(Matrix4f m, float x, float y, float z) {
        Vector3f p = m.transformPosition(x, y, z, new Vector3f());
        return this.addVertex(p.x, p.y, p.z);
    }

    public Mesh addVertex(float x, float y, float z) {
        this.flush();
        this.open = true;
        this.minX = Math.min(this.minX, x);
        this.minY = Math.min(this.minY, y);
        this.maxX = Math.max(this.maxX, x);
        this.maxY = Math.max(this.maxY, y);
        this.v[0] = x;
        this.v[1] = y;
        this.v[2] = z;
        this.v[3] = this.v[4] = this.v[5] = this.v[6] = 1.0F;
        this.v[7] = this.v[8] = 0.0F;
        this.v[9] = 0.0F;
        this.v[10] = 1.0F;
        this.v[11] = 0.0F;
        return this;
    }

    public Mesh setColor(int argb) {
        return this.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
    }

    public Mesh setColor(int r, int g, int b, int a) {
        this.v[3] = (r & 0xFF) / 255.0F;
        this.v[4] = (g & 0xFF) / 255.0F;
        this.v[5] = (b & 0xFF) / 255.0F;
        this.v[6] = (a & 0xFF) / 255.0F;
        return this;
    }

    public Mesh setColor(float r, float g, float b, float a) {
        return this.setColor((int)(r * 255.0F), (int)(g * 255.0F), (int)(b * 255.0F), (int)(a * 255.0F));
    }

    public Mesh setUv(float u, float t) {
        this.v[7] = u;
        this.v[8] = t;
        return this;
    }

    public Mesh setOverlay(int overlay) {
        return this;
    }

    public Mesh setLight(int light) {
        return this;
    }

    public Mesh setNormal(float x, float y, float z) {
        this.v[9] = x;
        this.v[10] = y;
        this.v[11] = z;
        return this;
    }

    public boolean isEmpty() {
        return this.vertices == 0 && !this.open;
    }

    public void discard() {
        this.open = false;
        this.vertices = 0;
        data.clear();
    }

    public void draw() {
        this.flush();
        Shader shader = Shader.current();
        if (this.vertices != 0 && shader != null && shader.ready()) {
            if (Gpu.guiMode()) {
                int[] saved = Gpu.saveState();
                if (Compat.drawCollectedGuiUnder(this.minX, this.minY, this.maxX, this.maxY)) {
                    Gpu.restoreState(saved);
                }
            }

            try {
                setup();
                shader.prepare();
                Gpu.beforeDraw();
                GlStateManager._glBindVertexArray(vao);
                GL15.glBindBuffer(34962, vbo);
                data.flip();
                GL15.glBufferData(34962, data, 35040);
                switch (this.mode.name()) {
                    case "QUADS":
                        int quads = this.vertices / 4;
                        indexQuads(quads);
                        GL11.glDrawElements(4, quads * 6, 5125, 0L);
                        break;
                    case "TRIANGLES":
                        GL11.glDrawArrays(4, 0, this.vertices);
                        break;
                    case "TRIANGLE_STRIP":
                        GL11.glDrawArrays(5, 0, this.vertices);
                        break;
                    case "TRIANGLE_FAN":
                        GL11.glDrawArrays(6, 0, this.vertices);
                        break;
                    case "LINES":
                    case "DEBUG_LINES":
                        GL11.glDrawArrays(1, 0, this.vertices);
                        break;
                    case "LINE_STRIP":
                    case "DEBUG_LINE_STRIP":
                        GL11.glDrawArrays(3, 0, this.vertices);
                }

                GlStateManager._glBindVertexArray(0);
                GlStateManager._glUseProgram(0);
            } finally {
                Gpu.afterDraw();
                this.discard();
            }
        } else {
            this.discard();
        }
    }

    private static void setup() {
        if (vao < 0) {
            vao = GlStateManager._glGenVertexArrays();
            vbo = GlStateManager._glGenBuffers();
            ibo = GlStateManager._glGenBuffers();
            GlStateManager._glBindVertexArray(vao);
            GL15.glBindBuffer(34962, vbo);
            int stride = 48;
            int[] sizes = new int[]{3, 4, 2, 3};
            long offset = 0L;

            for (int i = 0; i < sizes.length; i++) {
                GL20.glEnableVertexAttribArray(i);
                GL20.glVertexAttribPointer(i, sizes[i], 5126, false, stride, offset);
                offset += sizes[i] * 4L;
            }

            GL15.glBindBuffer(34963, ibo);
            GlStateManager._glBindVertexArray(0);
        }
    }

    private static void indexQuads(int quads) {
        if (quads > indexedQuads) {
            int n = Math.max(quads, Math.max(1024, indexedQuads * 2));
            IntBuffer idx = MemoryUtil.memAllocInt(n * 6);

            for (int q = 0; q < n; q++) {
                int b = q * 4;
                idx.put(b).put(b + 1).put(b + 2).put(b + 2).put(b + 3).put(b);
            }

            idx.flip();
            GL15.glBufferData(34963, idx, 35044);
            MemoryUtil.memFree(idx);
            indexedQuads = n;
        }
    }
}
