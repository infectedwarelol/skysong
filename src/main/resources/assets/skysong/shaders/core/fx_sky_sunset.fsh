#version 150

// Sunset: a warm horizon fading to lilac and violet, a low sun with god rays, soft clouds and early stars.

uniform mat4 InvViewProj;
uniform vec4 Misc;      // time, width, height, brightness
uniform vec4 SkyCol;    // first color, color mode (0 one color, 1 two colors, 2 rainbow)
uniform vec4 SkyCol2;   // second color, sky type

in vec2 texCoord;
out vec4 fragColor;

const mat2 ROT = mat2(0.80, 0.60, -0.60, 0.80);
const float TAU = 6.28318530718;
const vec3 SUN = vec3(-0.965, 0.10, 0.24);

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
    for (int i = 0; i < 5; i++) {
        sum += amp * vnoise(p);
        p = ROT * p * 2.03;
        amp *= 0.5;
    }
    return sum;
}

vec3 themeTint(float axis, float time) {
    float mode = SkyCol.w;
    if (mode > 1.5) {
        float h = fract(axis * 0.159155 + time * 0.02);
        vec3 rgb = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
        return mix(vec3(1.0), rgb, 0.6);
    }
    if (mode > 0.5) {
        return mix(SkyCol.rgb, SkyCol2.rgb, 0.5 + 0.5 * sin(axis + time * 0.1));
    }
    return SkyCol.rgb;
}

vec3 stars(vec3 dir, float time) {
    vec3 c = vec3(0.0);
    vec3 p = dir * 38.0;
    float dens = 0.05;
    for (int i = 0; i < 3; i++) {
        vec3 id = floor(p);
        vec3 q = fract(p) - 0.5;
        vec3 rn = nmzHash33(id);
        float core = smoothstep(0.32, 0.0, length(q));
        float tw = 0.7 + 0.3 * sin(time * (0.7 + rn.z * 1.6) + rn.y * 40.0);
        c += step(rn.x, dens) * core * core * mix(vec3(1.0, 0.86, 0.95), vec3(0.85, 0.9, 1.0), rn.y) * (0.4 + 0.6 * rn.z) * tw;
        p = p * 1.71 + 11.0;
        dens *= 0.6;
    }
    return c;
}

void main() {
    float time = Misc.x;
    float brightness = Misc.w;

    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 pFar = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec4 pNear = InvViewProj * vec4(ndc, -1.0, 1.0);
    vec3 rd = normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);
    vec3 sun = normalize(SUN);

    float axis = atan(rd.z, rd.x);
    float h = rd.y;
    float toSun = dot(rd, sun);
    float sunSide = 0.5 + 0.5 * toSun;

    vec3 zenith = vec3(0.16, 0.13, 0.32);
    vec3 upper = vec3(0.42, 0.36, 0.62);
    vec3 lilac = vec3(0.72, 0.62, 0.80);
    vec3 horizon = mix(vec3(0.86, 0.66, 0.74), vec3(1.0, 0.70, 0.52), sunSide * sunSide);
    vec3 tint = themeTint(axis, time);
    upper = mix(upper, upper * tint * 1.4, 0.2);
    lilac = mix(lilac, lilac * (0.6 + 0.6 * tint), 0.25);

    vec3 col;
    if (h > 0.0) {
        col = mix(horizon, lilac, smoothstep(0.0, 0.12, h));
        col = mix(col, upper, smoothstep(0.1, 0.38, h));
        col = mix(col, zenith, smoothstep(0.35, 0.95, h));
    } else {
        col = mix(horizon, vec3(0.42, 0.38, 0.50), smoothstep(0.0, 0.25, -h));
    }

    col += vec3(1.0, 0.62, 0.38) * pow(max(toSun, 0.0), 6.0) * 0.3 * smoothstep(-0.2, 0.25, h + 0.2);

    float sunDisc = smoothstep(0.9985, 0.9993, toSun);
    float sunGlow = pow(max(toSun, 0.0), 220.0) * 1.2 + pow(max(toSun, 0.0), 40.0) * 0.3;
    col += vec3(1.0, 0.86, 0.66) * (sunDisc * 3.0 + sunGlow);

    vec3 east = normalize(cross(vec3(0.0, 1.0, 0.0), sun));
    vec3 north = cross(sun, east);
    float ang = atan(dot(rd, north), dot(rd, east));
    float rays = fbm(vec2(ang * 9.0, time * 0.05)) * 0.7 + fbm(vec2(ang * 23.0 + 3.0, time * 0.08)) * 0.5;
    rays = pow(clamp(rays, 0.0, 1.0), 3.0);
    float rayFall = pow(max(toSun, 0.0), 3.0) * smoothstep(-0.05, 0.2, h);
    col += vec3(1.0, 0.82, 0.70) * rays * rayFall * 0.45;

    vec2 cloudUv = rd.xz / max(h + 0.18, 0.05) * 1.6 + vec2(time * 0.006, 0.0);
    float cloud = smoothstep(0.52, 0.82, fbm(cloudUv)) * smoothstep(0.02, 0.12, h) * (1.0 - smoothstep(0.35, 0.6, h));
    vec3 cloudCol = mix(vec3(0.62, 0.52, 0.70), vec3(1.0, 0.72, 0.62), sunSide * sunSide);
    col = mix(col, cloudCol, cloud * 0.55);

    float starMask = smoothstep(0.08, 0.45, h) * (1.0 - cloud) * (1.0 - smoothstep(0.4, 0.95, toSun));
    col += stars(rd, time) * starMask * 1.2;

    fragColor = vec4(tanh(min(col * brightness, vec3(16.0))), 1.0);
}
