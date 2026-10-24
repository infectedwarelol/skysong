#version 150

// Draws a captured shape (the hands, or players and mobs) back onto the screen with a shader
// look: a fill (flowing palette gradient, glass, dark or aura), an outline and a soft outer glow.

uniform sampler2D Sampler0;   // mask from fx_mask: r brightness, g in view, a coverage
uniform sampler2D Sampler1;   // the captured colors
uniform sampler2D Sampler2;   // the scene behind (for glass)
uniform sampler2D Sampler3;   // blurred coverage (for the glow)

uniform vec2 OutSize;
uniform vec4 Fill;     // mode (0 normal, 1 gradient, 2 glass, 3 dark, 4 aura), opacity, time, draw the captured colors
uniform vec4 Edge;     // outline width in pixels, glow strength, fill behind walls, outline whitening
uniform vec4 GlassP;   // saturation, whiteness, distortion, tint
uniform vec4 Style;    // palette size, phase, closed loop, unused
uniform vec4 AuraP;    // aura: flow speed, pattern size, unused, unused
uniform vec4 Pal0;
uniform vec4 Pal1;
uniform vec4 Pal2;
uniform vec4 Pal3;
uniform vec4 Pal4;
uniform vec4 Pal5;

in vec2 texCoord;
out vec4 fragColor;

vec3 pal(int i) {
    if (i <= 0) return Pal0.rgb;
    if (i == 1) return Pal1.rgb;
    if (i == 2) return Pal2.rgb;
    if (i == 3) return Pal3.rgb;
    if (i == 4) return Pal4.rgb;
    return Pal5.rgb;
}

vec3 paletteRamp(float t) {
    int count = int(Style.x + 0.5);
    if (count <= 1) return pal(0);
    float f = clamp(t, 0.0, 1.0) * float(count - 1);
    int i = clamp(int(floor(f)), 0, count - 1);
    int j = min(i + 1, count - 1);
    float frac = clamp(f - float(i), 0.0, 1.0);
    frac = frac * frac * (3.0 - 2.0 * frac);
    return mix(pal(i), pal(j), frac);
}

vec3 paletteLoop(float t) {
    int count = int(Style.x + 0.5);
    if (count <= 1) return pal(0);
    float f = fract(t) * float(count);
    int i1 = clamp(int(floor(f)), 0, count - 1);
    float u = clamp(f - float(i1), 0.0, 1.0);
    int i0 = i1 - 1; if (i0 < 0) i0 += count;
    int i2 = i1 + 1; if (i2 >= count) i2 -= count;
    int i3 = i1 + 2; if (i3 >= count) i3 -= count;
    vec3 c0 = pal(i0); vec3 c1 = pal(i1); vec3 c2 = pal(i2); vec3 c3 = pal(i3);
    float u2 = u * u; float u3 = u2 * u;
    vec3 cyclic = 0.5 * ((2.0 * c1) + (-c0 + c2) * u + (2.0 * c0 - 5.0 * c1 + 4.0 * c2 - c3) * u2
                  + (-c0 + 3.0 * c1 - 3.0 * c2 + c3) * u3);
    float tri = 0.5 - 0.5 * cos(6.2831853 * fract(t));
    return clamp(mix(paletteRamp(tri), cyclic, clamp(Style.z, 0.0, 1.0)), 0.0, 1.0);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * vnoise(p);
        p = p * 2.02 + vec2(17.3, 9.1);
        a *= 0.5;
    }
    return s;
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 1e-10)), d / (q.x + 1e-10), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

// The same hue, rich and at full strength (light pastel theme colors become deep ones)
vec3 vivid(vec3 c) {
    vec3 h = rgb2hsv(c);
    if (h.y < 0.05) return c;
    h.y = max(h.y, 0.72);
    h.z = 1.0;
    return hsv2rgb(h);
}

// Aura: the shape filled with slowly flowing light in the client colors. Deep, almost black pools,
// rich color clouds and soft light bands sweeping across, like a nebula moving inside the hand.
vec3 aura(vec2 uv, float time) {
    vec2 p = uv * vec2(OutSize.x / max(OutSize.y, 1.0), 1.0) * (2.4 / max(AuraP.y, 0.05));
    float t = time * 0.32 * AuraP.x;
    vec2 q = vec2(fbm(p + vec2(0.0, t)), fbm(p + vec2(5.2, 1.3) - vec2(t * 0.8, 0.0)));
    vec2 r = vec2(fbm(p + 1.7 * q + vec2(1.7, 9.2) + t * 0.45), fbm(p + 1.7 * q + vec2(8.3, 2.8) - t * 0.35));
    float n = fbm(p + 1.8 * r);
    vec3 hue = vivid(paletteLoop(n * 0.65 + q.x * 0.35 + Style.y));
    vec3 deep = hue * 0.06 + vec3(0.008, 0.0, 0.02);
    vec3 mid = hue * 0.5;
    vec3 lit = hue * 0.92 + vec3(0.05);
    float k = smoothstep(0.22, 0.82, n);
    vec3 col = k < 0.5 ? mix(deep, mid, k * 2.0) : mix(mid, lit, k * 2.0 - 1.0);
    // Soft light bands drifting across diagonally
    float band = sin((p.x * 0.9 + p.y * 1.25) * 1.6 - t * 3.2 + r.x * 3.0) * 0.5 + 0.5;
    band = pow(band, 4.0);
    col += mix(hue, vec3(1.0), 0.2) * band * 0.28;
    // Darker toward the top, brighter heart, as the light comes from inside
    col *= 0.78 + 0.34 * smoothstep(0.2, 0.8, q.y);
    return clamp(col, 0.0, 1.0);
}

float coverage(vec2 uv) {
    return texture(Sampler0, uv).a;
}

// Outline band: coverage spread out to the width, minus the shape itself, plus a crisp inner edge
float outline(vec2 uv, float mask, float width) {
    if (width <= 0.0) return 0.0;
    vec2 px = 1.0 / OutSize;
    float grown = 0.0;
    for (int i = 0; i < 16; i++) {
        float a = 6.2831853 * float(i) / 16.0;
        vec2 d = vec2(cos(a), sin(a));
        grown = max(grown, coverage(uv + d * width * px));
        grown = max(grown, coverage(uv + d * width * 0.5 * px));
    }
    float inner = min(min(coverage(uv + vec2(px.x, 0.0)), coverage(uv - vec2(px.x, 0.0))),
                      min(coverage(uv + vec2(0.0, px.y)), coverage(uv - vec2(0.0, px.y))));
    float innerEdge = clamp(mask - inner, 0.0, 1.0);
    return clamp(max(grown - mask, innerEdge), 0.0, 1.0);
}

vec3 glass(vec2 uv, vec3 tint, float time) {
    vec2 p = uv * OutSize / 48.0;
    vec2 n = vec2(vnoise(p + time * 0.15), vnoise(p * 1.7 - time * 0.11 + 19.0)) - 0.5;
    vec3 refl = texture(Sampler2, clamp(uv + n * GlassP.z * 2.0, 0.0, 1.0)).rgb;
    float luma = dot(refl, vec3(0.299, 0.587, 0.114));
    refl = max(mix(vec3(luma), refl, GlassP.x), 0.0);
    vec3 lit = mix(refl, vec3(1.0), clamp(luma, 0.0, 1.0) * GlassP.y);
    return mix(lit, lit * tint, GlassP.w);
}

// src "over" dst, straight alpha
vec4 over(vec4 src, vec4 dst) {
    float a = src.a + dst.a * (1.0 - src.a);
    if (a <= 0.0) return vec4(0.0);
    return vec4((src.rgb * src.a + dst.rgb * dst.a * (1.0 - src.a)) / a, a);
}

void main() {
    vec2 uv = texCoord;
    vec4 m = texture(Sampler0, uv);
    float mask = m.a;
    float time = Fill.z;
    vec3 grad = paletteLoop(uv.x * 0.55 + uv.y * 0.35 + Style.y);

    vec4 acc = vec4(0.0);

    bool auraMode = int(Fill.x + 0.5) == 4;

    // Soft glow outside the shape (a pale halo for the aura)
    if (Edge.y > 0.0) {
        float blurred = texture(Sampler3, uv).a;
        float glowA = (1.0 - exp(-blurred * Edge.y * 1.35)) * (1.0 - smoothstep(0.0, 1.0, mask));
        acc = vec4(mix(grad, vec3(1.0), auraMode ? 0.55 : 0.12), clamp(glowA, 0.0, 1.0));
    }

    // Fill
    if (mask > 0.002) {
        int mode = int(Fill.x + 0.5);
        vec4 src = texture(Sampler1, uv);
        vec3 srcRgb = src.a > 0.001 ? src.rgb / src.a : vec3(0.0);
        vec4 base = Fill.w > 0.5 ? vec4(srcRgb, src.a) : vec4(0.0);
        vec4 fill = base;
        if (m.g > 0.5) {
            vec3 look = srcRgb;
            if (mode == 1) {
                look = grad * (0.42 + 0.9 * m.r) + vec3(0.06) * m.r;
            } else if (mode == 2) {
                look = glass(uv, grad, time) * (0.7 + 0.55 * m.r) + grad * 0.08;
            } else if (mode == 3) {
                look = srcRgb * 0.16 + grad * 0.12;
            } else if (mode == 4) {
                look = aura(uv, time);
                // A faint hint of the model's own shading keeps its shape readable
                look *= 0.9 + 0.2 * m.r;
            }
            if (mode != 0) {
                if (Fill.w > 0.5) {
                    fill = vec4(mix(srcRgb, look, Fill.y), src.a);
                } else {
                    fill = vec4(look, mask * Fill.y);
                }
            }
        } else if (Edge.z > 0.0) {
            // Behind a wall: a see-through color fill
            fill = vec4(grad, mask * Edge.z);
        }
        acc = over(fill, acc);
    }

    // Outline on top
    float o = outline(uv, mask, Edge.x);
    if (o > 0.0) {
        acc = over(vec4(mix(grad, vec3(1.0), Edge.w), o), acc);
    }

    if (acc.a <= 0.002) discard;
    fragColor = acc;
}
