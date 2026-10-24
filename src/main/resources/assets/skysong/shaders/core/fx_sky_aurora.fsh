#version 150

// Northern lights: three drifting curtains of light over a starry night, colored by the sky colors.

uniform mat4 InvViewProj;
uniform vec4 Misc;      // time, width, height, brightness
uniform vec4 SkyCol;    // first color, color mode (0 one color, 1 two colors, 2 rainbow)
uniform vec4 SkyCol2;   // second color, sky type

in vec2 texCoord;
out vec4 fragColor;

const mat2 ROT = mat2(0.80, 0.60, -0.60, 0.80);

vec3 hsv2rgb(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

vec3 nmzHash33(vec3 q) {
    uvec3 p = uvec3(ivec3(q));
    p = p * uvec3(374761393U, 1103515245U, 668265263U) + p.zxy + p.yzx;
    p = p.yzx * (p.zxy ^ (p >> 3U));
    return vec3(p ^ (p >> 16U)) * (1.0 / vec3(0xffffffffU));
}

float hash21(vec2 p) {
    uvec2 q = uvec2(ivec2(p)) * uvec2(1597334673U, 3812015801U);
    uint n = (q.x ^ q.y) * 1597334673U;
    return float(n) * (1.0 / 4294967295.0);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float amp = 0.5;
    float sum = 0.0;
    for (int i = 0; i < 4; i++) {
        sum += amp * vnoise(p);
        p = ROT * p * 2.03;
        amp *= 0.5;
    }
    return sum;
}

vec3 starField(vec3 dir, float time, float density, float twinkle) {
    vec3 c = vec3(0.0);
    vec3 p = dir * 42.0;
    float dens = density;
    float amp = 1.0;
    for (int i = 0; i < 4; i++) {
        vec3 id = floor(p);
        vec3 q = fract(p) - 0.5;
        vec3 rn = nmzHash33(id);
        float core = smoothstep(0.36, 0.0, length(q));
        float hit = step(rn.x, dens);
        float tw = 1.0 - twinkle + twinkle * (0.55 + 0.45 * sin(time * (1.2 + rn.z * 2.6) + rn.y * 47.0));
        vec3 tint = mix(vec3(1.0, 0.72, 0.45), vec3(0.72, 0.86, 1.0), rn.y);
        c += hit * core * core * tint * (0.35 + 0.65 * rn.z) * tw * amp * 0.75;
        p = p * 1.63 + 17.0;
        dens *= 0.62;
        amp *= 0.78;
    }
    return c;
}

vec3 skyTint(float axis, float t, float time) {
    float mode = SkyCol.w;
    if (mode > 1.5) {
        float hue = fract(axis * 0.159155 + time * 0.02 + t * 0.15);
        return hsv2rgb(vec3(hue, 0.85, 1.0));
    }
    if (mode > 0.5) {
        float k = clamp(0.5 + 0.5 * sin(axis * 2.0 + time * 0.15) + (t - 0.5) * 0.7, 0.0, 1.0);
        return mix(SkyCol.rgb, SkyCol2.rgb, k);
    }
    return SkyCol.rgb;
}

void main() {
    float time = Misc.x;
    float brightness = Misc.w;

    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 pFar = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec4 pNear = InvViewProj * vec4(ndc, -1.0, 1.0);
    vec3 rd = normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);

    float axis = atan(rd.z, rd.x);

    vec3 col = vec3(0.024, 0.036, 0.076) * (0.55 + 0.45 * smoothstep(-1.0, 1.0, rd.y));
    col += starField(rd, time, 0.030, 0.35) * 0.9;

    vec3 aur = vec3(0.0);
    float rawLum = 0.0;

    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float height = 1.0 + fi * 0.75;
        float seed = fi * 4.7;

        float tp = height / (max(rd.y, 0.0) + 0.13);
        vec2 p = rd.xz * tp * 0.55;
        p += vec2(time * 0.045 + seed * 3.3, time * 0.017 - seed * 2.1);

        float d = fbm(p * vec2(0.85, 0.30) + vec2(seed, 0.0));
        float center = 0.46 + fi * 0.075;
        float band = exp(-pow((d - center) / 0.052, 2.0));

        float rays = 0.5 + 0.5 * sin(p.x * 6.0 + fbm(p * 0.6) * 11.0 + time * (0.25 + fi * 0.1));
        band *= 0.30 + 0.70 * rays * rays;

        float fade = smoothstep(0.02 + fi * 0.02, 0.34 + fi * 0.12, rd.y);
        band *= fade;

        rawLum += band * (1.0 - fi * 0.3);

        vec3 tint = skyTint(axis + fi * 0.6, fi * 0.5, time);
        aur += tint * band * (1.0 - fi * 0.18);
    }

    col += aur * (1.25 * brightness);

    vec3 horizonTint = skyTint(axis, 0.0, time);
    float glow = exp(-abs(rd.y) * 6.5) * (0.08 + 0.42 * clamp(rawLum, 0.0, 1.5));
    col += horizonTint * glow * 0.55 * brightness;

    fragColor = vec4(col, 1.0);
}
