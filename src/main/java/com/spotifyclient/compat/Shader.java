package com.spotifyclient.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.spotifyclient.SpotifyModClient;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

public final class Shader {
    static final String[] ATTRIBUTES = new String[]{"Position", "Color", "UV0", "Normal"};
    private static final List<Shader> ALL = new ArrayList<>();
    private static Shader current;
    public static final Shader POSITION_COLOR = of("position_color", null);
    public static final Shader POSITION_TEX = of("position_tex", null);
    private final String name;
    private int program = -1;
    private boolean failed;
    private final Map<String, Integer> locations = new HashMap<>();
    private final Map<String, Integer> types = new HashMap<>();
    private final Map<String, float[]> values = new HashMap<>();
    private int[] samplerUnits = new int[0];

    private Shader(String name) {
        this.name = name;
    }

    public static Shader of(String name, VertexFormat format) {
        Shader s = new Shader(name);
        ALL.add(s);
        return s;
    }

    public static void registerAll() {
    }

    public static void reloadAll() {
        for (Shader s : ALL) {
            s.release();
        }

        current = null;
    }

    public boolean ready() {
        return this.load();
    }

    public void bind() {
        if (this.load()) {
            current = this;
        }
    }

    static Shader current() {
        return current;
    }

    public void set(String uniform, float x) {
        this.values.put(uniform, new float[]{x});
    }

    public void set(String uniform, float x, float y) {
        this.values.put(uniform, new float[]{x, y});
    }

    public void set(String uniform, float x, float y, float z, float w) {
        this.values.put(uniform, new float[]{x, y, z, w});
    }

    public void set(String uniform, Matrix4f m) {
        this.values.put(uniform, m.get(new float[16]));
    }

    void prepare() {
        GlStateManager._glUseProgram(this.program);
        float[] c = Gpu.color();
        this.values.put("ColorModulator", new float[]{c[0], c[1], c[2], c[3]});
        this.values.put("ProjMat", Gpu.projection().get(new float[16]));
        this.values.put("ModelViewMat", Gpu.modelView().get(new float[16]));
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        this.values.put("ScreenSize", new float[]{mainTarget.width, mainTarget.height});

        for (Entry<String, float[]> e : this.values.entrySet()) {
            Integer loc = this.locations.get(e.getKey());
            if (loc != null) {
                upload(loc, this.types.get(e.getKey()), e.getValue());
            }
        }

        Gpu.bindTextures(this.samplerUnits);
    }

    private static void upload(int loc, int type, float[] v) {
        switch (type) {
            case 5126:
                GL20.glUniform1f(loc, at(v, 0));
                break;
            case 35664:
                GL20.glUniform2f(loc, at(v, 0), at(v, 1));
                break;
            case 35665:
                GL20.glUniform3f(loc, at(v, 0), at(v, 1), at(v, 2));
                break;
            case 35666:
                GL20.glUniform4f(loc, at(v, 0), at(v, 1), at(v, 2), at(v, 3));
                break;
            case 35676:
                if (v.length < 16) {
                    return;
                }

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer buf = stack.mallocFloat(16);
                    buf.put(v).flip();
                    GL20.glUniformMatrix4fv(loc, false, buf);
                }
        }
    }

    private static float at(float[] v, int i) {
        return i < v.length ? v[i] : 0.0F;
    }

    private boolean load() {
        if (this.program >= 0) {
            return true;
        }

        if (this.failed) {
            return false;
        }

        try {
            JsonObject json = JsonParser.parseString(read("shaders/core/" + this.name + ".json")).getAsJsonObject();
            String vsh = read("shaders/core/" + path(json.get("vertex").getAsString()) + ".vsh");
            String fsh = read("shaders/core/" + path(json.get("fragment").getAsString()) + ".fsh");
            int vs = compile(35633, vsh);
            int fs = compile(35632, fsh);
            int p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vs);
            GL20.glAttachShader(p, fs);

            for (int i = 0; i < ATTRIBUTES.length; i++) {
                GL20.glBindAttribLocation(p, i, ATTRIBUTES[i]);
            }

            GL20.glLinkProgram(p);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            if (GL20.glGetProgrami(p, 35714) == 0) {
                String log = GL20.glGetProgramInfoLog(p, 4096);
                GL20.glDeleteProgram(p);
                throw new IllegalStateException("link: " + log);
            }

            this.values.clear();
            if (json.has("uniforms")) {
                for (JsonElement el : json.getAsJsonArray("uniforms")) {
                    JsonObject u = el.getAsJsonObject();
                    JsonArray vals = u.getAsJsonArray("values");
                    float[] v = new float[vals.size()];

                    for (int i = 0; i < v.length; i++) {
                        v[i] = vals.get(i).getAsFloat();
                    }

                    this.values.put(u.get("name").getAsString(), v);
                }
            }

            this.locations.clear();
            this.types.clear();
            List<Integer> units = new ArrayList<>();
            GlStateManager._glUseProgram(p);
            int count = GL20.glGetProgrami(p, 35718);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer size = stack.mallocInt(1);
                IntBuffer type = stack.mallocInt(1);

                for (int i = 0; i < count; i++) {
                    String uname = GL20.glGetActiveUniform(p, i, size, type);
                    int loc = GL20.glGetUniformLocation(p, uname);
                    if (loc >= 0) {
                        if (uname.startsWith("Sampler") && type.get(0) == 35678) {
                            int unit = Integer.parseInt(uname.substring("Sampler".length()));
                            GL20.glUniform1i(loc, unit);
                            units.add(unit);
                        } else {
                            this.locations.put(uname, loc);
                            this.types.put(uname, type.get(0));
                        }
                    }
                }
            }

            GlStateManager._glUseProgram(0);
            Gpu.afterDraw();
            this.samplerUnits = units.stream().mapToInt(Integer::intValue).toArray();
            this.program = p;
            return true;
        } catch (Exception e) {
            this.failed = true;
            SpotifyModClient.LOGGER.error("[Versefall] Shader {} could not be made", this.name, e);
            return false;
        }
    }

    private void release() {
        if (this.program >= 0) {
            GL20.glDeleteProgram(this.program);
        }

        this.program = -1;
        this.failed = false;
    }

    private static int compile(int kind, String source) {
        int s = GL20.glCreateShader(kind);
        GL20.glShaderSource(s, source);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, 35713) == 0) {
            String log = GL20.glGetShaderInfoLog(s, 4096);
            GL20.glDeleteShader(s);
            throw new IllegalStateException((kind == 35633 ? "vertex" : "fragment") + ": " + log);
        } else {
            return s;
        }
    }

    private static String path(String id) {
        String p = id.contains(":") ? id.substring(id.indexOf(58) + 1) : id;
        return p.startsWith("core/") ? p.substring(5) : p;
    }

    private static String read(String path) throws IOException {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Resource resource = manager.getResource(Ids.of("spotify_mod", path)).orElseThrow(() -> new FileNotFoundException("spotify_mod:" + path));

        try (
            InputStream in = resource.open();
            InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8);
        ) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];

            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }

            return sb.toString();
        }
    }
}
