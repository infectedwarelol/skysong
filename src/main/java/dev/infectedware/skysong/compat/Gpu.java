package dev.infectedware.skysong.compat;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

public final class Gpu {
    private static final float[] color = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f modelView = new Matrix4f();
    private static final Matrix4f worldProjection = new Matrix4f();
    private static final Matrix4f worldView = new Matrix4f();
    private static final int UNITS = 8;
    private static final int[] textures = new int[8];
    private static final Map<Integer, GlTexture> glTextures = new HashMap<>();
    private static final Map<Integer, Integer> sampling = new HashMap<>();
    private static final int[] samplers = new int[4];
    private static final Map<RenderTarget, Gpu.Fbo> fbos = new WeakHashMap<>();
    private static boolean guiMode;
    private static final ArrayDeque<int[]> guiClips = new ArrayDeque<>();

    private Gpu() {
    }

    public static void enableBlend() {
        GlStateManager._enableBlend();
    }

    public static void disableBlend() {
        GlStateManager._disableBlend();
    }

    public static void defaultBlendFunc() {
        blendFuncSeparate(770, 771, 1, 0);
    }

    public static void blendFunc(int src, int dst) {
        blendFuncSeparate(src, dst, src, dst);
    }

    public static void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void enableDepthTest() {
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(515);
    }

    public static void disableDepthTest() {
        GlStateManager._disableDepthTest();
    }

    public static void depthMask(boolean write) {
        GlStateManager._depthMask(write);
    }

    public static void enableCull() {
        GlStateManager._enableCull();
    }

    public static void disableCull() {
        GlStateManager._disableCull();
    }

    public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
        colorMaskRaw(r, g, b, a);
    }

    public static void setColor(float r, float g, float b, float a) {
        color[0] = r;
        color[1] = g;
        color[2] = b;
        color[3] = a;
    }

    static float[] color() {
        return color;
    }

    public static void setMatrices(Matrix4f proj, Matrix4f view) {
        projection.set(proj);
        modelView.set(view);
    }

    public static void useGuiMatrices() {
        guiMode = true;
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        bindFramebuffer(36160, fbo(target));
        GlStateManager._viewport(0, 0, target.width, target.height);
        Window window = Minecraft.getInstance().getWindow();
        float w = (float)(window.getWidth() / window.getGuiScale());
        float h = (float)(window.getHeight() / window.getGuiScale());
        projection.setOrtho(0.0F, w, h, 0.0F, 1000.0F, 11000.0F);
        modelView.identity().translate(0.0F, 0.0F, -10000.0F);
    }

    public static void setWorldCamera(Matrix4f proj, Matrix4f view) {
        worldProjection.set(proj);
        worldView.set(view);
    }

    public static Matrix4f worldProjection() {
        return worldProjection;
    }

    public static Matrix4f worldView() {
        return worldView;
    }

    static Matrix4f projection() {
        return projection;
    }

    static Matrix4f modelView() {
        return modelView;
    }

    private static void colorMaskRaw(boolean r, boolean g, boolean b, boolean a) {
        GlStateManager._colorMask(r, g, b, true);
    }

    public static void texture(int unit, int glId) {
        textures[unit] = glId;
    }

    public static void clearTextures() {
        Arrays.fill(textures, 0);
    }

    static void bindTextures(int[] units) {
        for (int unit : units) {
            bindTexture(unit, textures[unit]);
            GL33.glBindSampler(unit, sampler(textures[unit]));
        }

        GlStateManager._activeTexture(33984);
        GL13.glActiveTexture(33984);
    }

    private static int track(GpuTexture texture) {
        GlTexture gl = (GlTexture)texture;
        int id = gl.glId();
        if (glTextures.get(id) != gl) {
            if (glTextures.size() > 256) {
                glTextures.values().removeIf(GlTexture::isClosed);
            }

            glTextures.put(id, gl);
        }

        return id;
    }

    static void setSampling(int id, boolean smooth, boolean repeat) {
        sampling.put(id, (smooth ? 1 : 0) | (repeat ? 2 : 0));
    }

    private static int sampler(int id) {
        int mode = sampling.getOrDefault(id, 0);
        if (samplers[mode] == 0) {
            int s = GL33.glGenSamplers();
            int filter = (mode & 1) != 0 ? 9729 : 9728;
            int wrap = (mode & 2) != 0 ? 10497 : 33071;
            GL33.glSamplerParameteri(s, 10241, filter);
            GL33.glSamplerParameteri(s, 10240, filter);
            GL33.glSamplerParameteri(s, 10242, wrap);
            GL33.glSamplerParameteri(s, 10243, wrap);
            samplers[mode] = s;
        }

        return samplers[mode];
    }

    public static int textureId(Identifier location, boolean smooth) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(location);
        return textureId(tex, smooth);
    }

    public static int textureId(AbstractTexture tex, boolean smooth) {
        int id = track(tex.getTexture());
        setSampling(id, smooth, true);
        return id;
    }

    static void bindFramebuffer(int target, int fb) {
        GlStateManager._glBindFramebuffer(target, fb);
        GL30.glBindFramebuffer(target, fb);
    }

    static void bindTexture(int unit, int id) {
        GlStateManager._activeTexture(33984 + unit);
        GL13.glActiveTexture(33984 + unit);
        GlStateManager._bindTexture(id);
        GL11.glBindTexture(3553, id);
    }

    public static int color(RenderTarget t) {
        return track(t.getColorTexture());
    }

    public static int depth(RenderTarget t) {
        return t.getDepthTexture() == null ? 0 : track(t.getDepthTexture());
    }

    public static int fbo(RenderTarget t) {
        GpuTexture color = t.getColorTexture();
        GpuTexture depthTex = t.getDepthTexture();
        Gpu.Fbo f = fbos.get(t);
        if (f != null && f.color() == color && f.depth() == depthTex) {
            return f.id();
        }

        if (f != null) {
            bindFramebuffer(36160, 0);
            GlStateManager._glDeleteFramebuffers(f.id());
        }

        int c = color(t);
        int d = depth(t);
        int fb = GlStateManager.glGenFramebuffers();
        bindFramebuffer(36160, fb);
        GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, c, 0);
        if (d != 0) {
            GlStateManager._glFramebufferTexture2D(36160, 36096, 3553, d, 0);
        }

        fbos.put(t, new Gpu.Fbo(fb, color, depthTex));
        return fb;
    }

    static boolean guiMode() {
        return guiMode;
    }

    static int[] saveState() {
        int[] vp = new int[4];
        GL11.glGetIntegerv(2978, vp);
        return new int[]{
            GL11.glIsEnabled(3042) ? 1 : 0,
            GL11.glGetInteger(32969),
            GL11.glGetInteger(32968),
            GL11.glGetInteger(32971),
            GL11.glGetInteger(32970),
            GL11.glIsEnabled(2929) ? 1 : 0,
            GL11.glGetBoolean(2930) ? 1 : 0,
            GL11.glIsEnabled(2884) ? 1 : 0,
            GL11.glGetInteger(36006),
            vp[0],
            vp[1],
            vp[2],
            vp[3]
        };
    }

    static void restoreState(int[] s) {
        if (s[0] == 1) {
            enableBlend();
        } else {
            disableBlend();
        }

        blendFuncSeparate(s[1], s[2], s[3], s[4]);
        if (s[5] == 1) {
            enableDepthTest();
        } else {
            disableDepthTest();
        }

        depthMask(s[6] == 1);
        if (s[7] == 1) {
            enableCull();
        } else {
            disableCull();
        }

        bindFramebuffer(36160, s[8]);
        GlStateManager._viewport(s[9], s[10], s[11], s[12]);
    }

    public static void bind(RenderTarget t) {
        guiMode = false;
        bindFramebuffer(36160, fbo(t));
        GlStateManager._viewport(0, 0, t.width, t.height);
    }

    public static void blit(RenderTarget from, RenderTarget to) {
        copy(from, to, 16384, from.width == to.width && from.height == to.height ? 9728 : 9729);
    }

    public static void blitDepth(RenderTarget from, RenderTarget to) {
        copy(from, to, 256, 9728);
    }

    public static void blitRegion(RenderTarget from, RenderTarget to, int x0, int y0, int x1, int y1, boolean linear) {
        GlStateManager._disableScissorTest();
        int read = fbo(from);
        int draw = fbo(to);
        bindFramebuffer(36008, read);
        bindFramebuffer(36009, draw);
        GlStateManager._glBlitFrameBuffer(x0, y0, x1, y1, 0, 0, to.width, to.height, 16384, linear ? 9729 : 9728);
        bindFramebuffer(36160, 0);
    }

    private static void copy(RenderTarget from, RenderTarget to, int bits, int filter) {
        GlStateManager._disableScissorTest();
        int read = fbo(from);
        int draw = fbo(to);
        bindFramebuffer(36008, read);
        bindFramebuffer(36009, draw);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, bits, filter);
        bindFramebuffer(36160, 0);
    }

    public static void clear(RenderTarget t, float r, float g, float b, float a) {
        bind(t);
        float[] keepColor = new float[4];
        GL11.glGetFloatv(3106, keepColor);
        double keepDepth = GL11.glGetDouble(2931);
        colorMaskRaw(true, true, true, true);
        GlStateManager._depthMask(true);
        GlStateManager._disableScissorTest();
        GL11.glClearColor(r, g, b, a);
        GL11.glClearDepth(1.0);
        GL11.glClear(16384 | (depth(t) != 0 ? 256 : 0));
        GL11.glClearColor(keepColor[0], keepColor[1], keepColor[2], keepColor[3]);
        GL11.glClearDepth(keepDepth);
    }

    public static void clearAlpha(RenderTarget t, float a) {
        bind(t);
        float[] keep = new float[4];
        GL11.glGetFloatv(3106, keep);
        colorMaskRaw(false, false, false, true);
        GlStateManager._disableScissorTest();
        GL11.glClearColor(0.0F, 0.0F, 0.0F, a);
        GL11.glClear(16384);
        colorMaskRaw(true, true, true, true);
        GL11.glClearColor(keep[0], keep[1], keep[2], keep[3]);
    }

    static void beforeDraw() {
        colorMaskRaw(true, true, true, true);
        GlStateManager._disableColorLogicOp();
        GlStateManager._disablePolygonOffset();
        GlStateManager._polygonMode(1032, 6914);
        int[] clip = guiClips.isEmpty() ? null : guiClips.peek();
        if (clip != null) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(clip[0], clip[1], clip[2], clip[3]);
        } else {
            GlStateManager._disableScissorTest();
        }
    }

    static void pushGuiClip(Matrix4f m, int x1, int y1, int x2, int y2) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        Vector3f a = m.transformPosition(x1, y1, 0.0F, new Vector3f());
        Vector3f b = m.transformPosition(x2, y2, 0.0F, new Vector3f());
        int left = (int)Math.floor(Math.min(a.x, b.x) * scale);
        int right = (int)Math.ceil(Math.max(a.x, b.x) * scale);
        int top = (int)Math.floor(Math.min(a.y, b.y) * scale);
        int bottom = (int)Math.ceil(Math.max(a.y, b.y) * scale);
        int height = window.getHeight();
        int[] box = new int[]{left, height - bottom, Math.max(0, right - left), Math.max(0, bottom - top)};
        int[] outer = guiClips.peek();
        if (outer != null) {
            int x0 = Math.max(box[0], outer[0]);
            int y0 = Math.max(box[1], outer[1]);
            int x3 = Math.min(box[0] + box[2], outer[0] + outer[2]);
            int y3 = Math.min(box[1] + box[3], outer[1] + outer[3]);
            box = new int[]{x0, y0, Math.max(0, x3 - x0), Math.max(0, y3 - y0)};
        }

        guiClips.push(box);
    }

    static void popGuiClip() {
        guiClips.poll();
    }

    public static void afterDraw() {
        GlCommandEncoder encoder = (GlCommandEncoder)RenderSystem.getDevice().createCommandEncoder();
        encoder.lastPipeline = null;
        encoder.lastProgram = null;
    }

    private record Fbo(int id, Object color, Object depth) {
    }
}
