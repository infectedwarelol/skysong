#version 150

// Starfall: a clear night with a faint band of the milky way and shooting stars.

uniform mat4 InvViewProj;
uniform vec4 Misc;      // time, width, height, brightness
uniform vec4 SkyCol;    // first color, color mode (0 one color, 1 two colors, 2 rainbow)
uniform vec4 SkyCol2;   // second color, sky type

in vec2 texCoord;
out vec4 fragColor;

const mat2 ROT = mat2(0.80, 0.60, -0.60, 0.80);
const float TAU = 6.28318530718;
const float PI = 3.14159265359;
const int METEORS = 10;

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

vec3 starField(vec3 dir, float time) {
    vec3 c = vec3(0.0);
    vec3 p = dir * 42.0;
    float dens = 0.06;
    float amp = 1.0;
    for (int i = 0; i < 4; i++) {
        vec3 id = floor(p);
        vec3 q = fract(p) - 0.5;
        vec3 rn = nmzHash33(id);
        float core = smoothstep(0.30, 0.0, length(q));
        float hit = step(rn.x, dens);
        float tw = 0.75 + 0.25 * sin(time * (0.8 + rn.z * 1.8) + rn.y * 47.0);
        vec3 tint = mix(vec3(1.0, 0.74, 0.50), vec3(0.72, 0.86, 1.0), rn.y);
        c += hit * core * core * tint * (0.35 + 0.65 * rn.z) * tw * amp;
        p = p * 1.63 + 17.0;
        dens *= 0.62;
        amp *= 0.78;
    }
    return c;
}

vec3 meteors(vec3 rd, float time, vec3 themeTint) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < METEORS; i++) {
        float fi = float(i);
        vec3 gh = nmzHash33(vec3(fi * 13.0 + 7.0, 51.0, 3.0));

        float period = mix(2.2, 6.5, gh.x);
        float cycle = time / period + gh.y * 19.0;
        float fly = fract(cycle);
        if (fly > 0.16) {
            continue;
        }
        float t01 = fly / 0.16;

        float shower = floor(cycle);
        vec3 rnd = nmzHash33(vec3(fi * 29.0 + 5.0, shower, 11.0));
        vec3 rnd2 = nmzHash33(vec3(fi * 31.0 + 3.0, shower, 23.0));

        float az = rnd.x * TAU;
        float el = mix(0.12, 0.95, rnd.y);
        vec3 headStart = vec3(cos(el) * cos(az), sin(el), cos(el) * sin(az));

        vec3 east = normalize(cross(vec3(0.0, 1.0, 0.0), headStart));
        vec3 north = cross(headStart, east);
        vec3 dirT = normalize(-north + east * (rnd.z - 0.5) * 1.6);
        vec3 n = normalize(cross(headStart, dirT));
        vec3 ea = headStart;
        vec3 eb = cross(n, ea);

        float off = dot(rd, n);
        if (abs(off) > 0.02) {
            continue;
        }

        float along = atan(dot(rd, eb), dot(rd, ea));

        float arc = mix(0.35, 0.80, rnd2.x);
        float head = t01 * arc;
        float tailLen = mix(0.10, 0.22, rnd2.y) * (0.35 + 0.65 * t01);

        float s = along - head;
        if (s > 0.02 || s < -(tailLen + 0.05)) {
            continue;
        }

        float env = pow(sin(t01 * PI), 0.65);

        float w = mix(0.0016, 0.0026, rnd2.z);
        float line = exp(-off * off / (w * w));

        float tailT = clamp(-s / tailLen, 0.0, 1.0);
        float profile = (1.0 - tailT) * (1.0 - tailT);
        float trail = line * profile * step(s, 0.0);

        float dHead = length(vec2(s, off));
        float headCore = exp(-dHead * dHead / (0.0035 * 0.0035)) * 2.5;
        float headGlow = exp(-dHead * dHead / (0.012 * 0.012)) * 0.5;

        float sparkle = 0.85 + 0.15 * sin(along * 240.0 + shower * 12.0);

        vec3 hot = vec3(1.0, 0.97, 0.90);
        vec3 warm = mix(vec3(1.0, 0.55, 0.22), themeTint, 0.45);
        vec3 col = mix(hot, warm, tailT);

        sum += (trail * 1.35 * sparkle + headCore + headGlow) * col * env;
    }
    return sum;
}

void main() {
    float time = Misc.x;
    float brightness = Misc.w;

    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 pFar = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec4 pNear = InvViewProj * vec4(ndc, -1.0, 1.0);
    vec3 rd = normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);

    float axis = atan(rd.z, rd.x);
    vec3 tint = skyTint(axis, 0.5, time);

    vec3 col = vec3(0.014, 0.021, 0.050) * (0.55 + 0.45 * smoothstep(-1.0, 1.0, rd.y));
    col += starField(rd, time) * 1.05;

    float band = exp(-pow((abs(rd.y) - 0.18) * 2.4, 2.0));
    float milky = fbm(rd.xz * 3.2 + rd.y * 1.7 + time * 0.004);
    col += tint * band * pow(clamp(milky, 0.0, 1.0), 2.4) * 0.10;

    col += meteors(rd, time, tint);

    fragColor = vec4(tanh(min(col * brightness, vec3(16.0))), 1.0);
}
