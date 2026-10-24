#version 150

// Liquid glass panel: the blurred scene behind it, dimmed and tinted with the client palette
// (horizontal wave, drifting blobs or four corners), a thin bright rim and a little refraction
// with color fringing right at the edge. Mode 1 draws the soft outer glow instead.

uniform sampler2D Sampler0;   // blurred copy of the scene
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
uniform vec2 Size;
uniform vec4 Radius;          // top-left, top-right, bottom-right, bottom-left
uniform vec4 Glass;           // alpha, rim sharpness, rim strength, refraction
uniform vec4 Style;           // gradient style (0 wave, 1 blobs, 2 corners), blob phase, wave offset, palette size
uniform vec4 Extra;           // tint alpha, has backdrop, mode (0 glass, 1 glow), palette closed-loop factor
uniform vec4 Glow;            // glow intensity, glow radius, style sweep (-1 = none), previous style
uniform vec4 Pal0;
uniform vec4 Pal1;
uniform vec4 Pal2;
uniform vec4 Pal3;
uniform vec4 Pal4;
uniform vec4 Pal5;

in vec2 texCoord0;            // local position in units
in vec4 vertexColor;

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
    int count = int(Style.w + 0.5);
    if (count <= 1) return pal(0);
    float f = clamp(t, 0.0, 1.0) * float(count - 1);
    int i = clamp(int(floor(f)), 0, count - 1);
    int j = min(i + 1, count - 1);
    float frac = clamp(f - float(i), 0.0, 1.0);
    frac = frac * frac * (3.0 - 2.0 * frac);
    return mix(pal(i), pal(j), frac);
}

vec3 paletteLoop(float t) {
    int count = int(Style.w + 0.5);
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
    vec3 mirror = paletteRamp(tri);
    return clamp(mix(mirror, cyclic, clamp(Extra.w, 0.0, 1.0)), 0.0, 1.0);
}

vec3 boxGradient(vec2 uv, float phase) {
    vec3 tl = paletteLoop(phase);
    vec3 tr = paletteLoop(phase + 0.25);
    vec3 br = paletteLoop(phase + 0.5);
    vec3 bl = paletteLoop(phase + 0.75);
    return mix(mix(tl, tr, uv.x), mix(bl, br, uv.x), uv.y);
}

vec3 meshGradient(vec2 uv, float phase, float aspect) {
    float a = 6.2831853 * phase;
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float kx = mod(fi, 2.0) < 0.5 ? 1.0 : 2.0;
        float ky = mod(fi, 2.0) < 0.5 ? 2.0 : 1.0;
        vec2 p = vec2(0.5) + 0.34 * vec2(sin(a * kx + fi * 2.3999), cos(a * ky - fi * 1.618));
        vec2 d = (uv - p) * vec2(aspect, 1.0);
        float w = 1.0 / (dot(d, d) * 5.0 + 0.06);
        sum += paletteRamp(fi / 5.0) * w;
        wsum += w;
    }
    return sum / wsum;
}

vec3 styleColor(float id, vec2 uv) {
    int s = int(id + 0.5);
    if (s == 1) return meshGradient(uv, Style.y, Size.x / max(Size.y, 0.001));
    if (s == 2) return boxGradient(uv, Style.y * 20.0);
    float tri = abs(fract(uv.x + fract(Style.z)) * 2.0 - 1.0);
    return paletteRamp(smoothstep(0.0, 1.0, tri)) * 0.86;
}

float roundBox(vec2 local) {
    vec2 c = local - Size * 0.5;
    float r = c.x < 0.0 ? (c.y < 0.0 ? Radius.x : Radius.w) : (c.y < 0.0 ? Radius.y : Radius.z);
    r = min(r, min(Size.x, Size.y) * 0.5);
    vec2 q = abs(c) - Size * 0.5 + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    float d = roundBox(texCoord0);
    vec2 uv = clamp(texCoord0 / Size, 0.0, 1.0);
    vec3 tint = styleColor(Style.x, uv);
    float axis = uv.x + (uv.y - 0.5) * 0.30;
    if (Glow.z >= 0.0) {
        // Style change: the new style softly fades in from left to right (no light band)
        vec3 before = styleColor(Glow.w, uv);
        float head = Glow.z * 1.8 - 0.4;
        tint = mix(before, tint, 1.0 - smoothstep(head - 0.2, head + 0.2, axis));
    }

    if (Extra.z > 0.5) {
        // Soft glow outside the panel
        float radius = max(Glow.y, 1.0);
        float t = max(d, 0.0) / radius;
        float g = exp(-t * t * 4.0) * smoothstep(-1.0, 0.5, d);
        float ga = clamp(Glow.x * 0.55 * g * Glass.x, 0.0, 1.0) * ColorModulator.a;
        if (ga < 0.002) discard;
        fragColor = vec4(tint, ga);
        return;
    }

    float w = max(fwidth(d), 1e-4);
    float cover = clamp(0.5 - d / w, 0.0, 1.0);

    // Rim: strongest right at the edge, falling off sharply inward
    vec2 halfSize = Size * 0.5;
    float maxDist = max(min(halfSize.x, halfSize.y), 0.001);
    float edge = 1.0 - clamp(abs(d) / maxDist, 0.0, 1.0);
    float rim = Glass.y > 20.0 ? exp(Glass.y * log(clamp(edge, 0.001, 1.0))) : pow(clamp(edge, 0.0, 1.0), max(Glass.y, 0.001));
    rim = clamp(rim, 0.0, 1.0);

    vec3 backdrop;
    if (Extra.y > 0.5) {
        vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
        vec2 c = texCoord0 - halfSize;
        vec2 dir = length(c) > 0.001 ? normalize(c) : vec2(0.0);
        dir.y = -dir.y; // local y points down, framebuffer y points up
        vec2 ofs = dir * rim * Glass.w * 0.25;
        vec2 ca = ofs * 0.12;
        vec2 s = clamp(screenUv + ofs, 0.0, 1.0);
        backdrop.r = texture(Sampler0, clamp(s + ca, 0.0, 1.0)).r;
        backdrop.g = texture(Sampler0, s).g;
        backdrop.b = texture(Sampler0, clamp(s - ca, 0.0, 1.0)).b;
    } else {
        backdrop = vec3(0.07, 0.08, 0.1);
    }

    float noise = fract(sin(dot(texCoord0 * 7.0, vec2(12.9898, 78.233))) * 43758.5453);
    tint = tint * (1.0 - (0.5 / 255.0) * noise) + (0.5 / 255.0) * noise;

    float panelAlpha = Extra.x;
    vec3 tinted = mix(backdrop * 0.78, tint, clamp(panelAlpha * 0.32, 0.0, 0.75));
    vec3 color = mix(tinted, tint, rim * Glass.z);
    float baseAlpha = Extra.y > 0.5 ? 1.0 : 0.55;
    float alpha = mix(baseAlpha, max(panelAlpha, baseAlpha * 0.8), rim) * cover * Glass.x * ColorModulator.a;

    if (alpha < 0.002) discard;
    fragColor = vec4(color, alpha);
}
