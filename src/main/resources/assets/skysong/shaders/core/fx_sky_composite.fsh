#version 150

// Puts the finished sky on screen with its bloom. The black hole is kept in an HDR encoding and
// gets its tone mapping and background stars here; the others just add a soft glow.

uniform sampler2D Sampler0;   // sky
uniform sampler2D Sampler1;   // bloom, largest first
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;

uniform mat4 InvViewProj;
uniform vec4 Misc;      // time, width, height, brightness
uniform vec4 SkyCol2;   // second color, sky type (1 = black hole)

in vec2 texCoord;
out vec4 fragColor;

const float BLOOM_STRENGTH = 0.13;

vec3 nmzHash33(vec3 q) {
    uvec3 p = uvec3(ivec3(q));
    p = p * uvec3(374761393U, 1103515245U, 668265263U) + p.zxy + p.yzx;
    p = p.yzx * (p.zxy ^ (p >> 3U));
    return vec3(p ^ (p >> 16U)) * (1.0 / vec3(0xffffffffU));
}

vec3 bhStars(vec3 dir, float time) {
    vec3 c = vec3(0.0);
    vec3 p = dir * 42.0;
    float dens = 0.05;
    float amp = 1.0;
    for (int i = 0; i < 4; i++) {
        vec3 id = floor(p);
        vec3 q = fract(p) - 0.5;
        vec3 rn = nmzHash33(id);
        float core = smoothstep(0.30, 0.0, length(q));
        float hit = step(rn.x, dens);
        float tw = 0.78 + 0.22 * sin(time * (0.6 + rn.z * 1.3) + rn.y * 47.0);
        vec3 tint = mix(vec3(1.0, 0.74, 0.50), vec3(0.72, 0.86, 1.0), rn.y);
        c += hit * core * core * tint * (0.35 + 0.65 * rn.z) * tw * amp;
        p = p * 1.63 + 17.0;
        dens *= 0.62;
        amp *= 0.78;
    }
    return c * 0.42;
}

vec3 decodeHDR(vec3 c) {
    c = pow(min(c, vec3(0.9975)), vec3(2.2));
    return c / max(1.0 - c, vec3(0.0055));
}

vec4 cubic(float x) {
    float x2 = x * x;
    float x3 = x2 * x;
    vec4 w;
    w.x = -x3 + 3.0 * x2 - 3.0 * x + 1.0;
    w.y = 3.0 * x3 - 6.0 * x2 + 4.0;
    w.z = -3.0 * x3 + 3.0 * x2 + 3.0 * x + 1.0;
    w.w = x3;
    return w / 6.0;
}

vec3 bicubic(sampler2D tex, vec2 uv) {
    vec2 resolution = vec2(textureSize(tex, 0));
    vec2 coord = uv * resolution;

    float fx = fract(coord.x);
    float fy = fract(coord.y);
    coord.x -= fx;
    coord.y -= fy;
    fx -= 0.5;
    fy -= 0.5;

    vec4 xcubic = cubic(fx);
    vec4 ycubic = cubic(fy);

    vec4 c = vec4(coord.x - 0.5, coord.x + 1.5, coord.y - 0.5, coord.y + 1.5);
    vec4 s = vec4(xcubic.x + xcubic.y, xcubic.z + xcubic.w, ycubic.x + ycubic.y, ycubic.z + ycubic.w);
    vec4 offset = c + vec4(xcubic.y, xcubic.w, ycubic.y, ycubic.w) / s;

    vec3 s0 = texture(tex, vec2(offset.x, offset.z) / resolution).rgb;
    vec3 s1 = texture(tex, vec2(offset.y, offset.z) / resolution).rgb;
    vec3 s2 = texture(tex, vec2(offset.x, offset.w) / resolution).rgb;
    vec3 s3 = texture(tex, vec2(offset.y, offset.w) / resolution).rgb;

    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);

    return mix(mix(s3, s2, sx), mix(s1, s0, sx), sy);
}

vec3 blackHole() {
    vec3 color = decodeHDR(bicubic(Sampler0, texCoord));

    vec3 bloom = decodeHDR(bicubic(Sampler1, texCoord)) * 1.00;
    bloom += decodeHDR(bicubic(Sampler2, texCoord)) * 1.00;
    bloom += decodeHDR(bicubic(Sampler3, texCoord)) * 1.00;
    bloom += decodeHDR(bicubic(Sampler4, texCoord)) * 0.90;
    bloom += decodeHDR(bicubic(Sampler5, texCoord)) * 0.80;
    bloom += decodeHDR(bicubic(Sampler6, texCoord)) * 0.65;

    color += bloom * BLOOM_STRENGTH;

    color = pow(color, vec3(1.5));
    color = color / (1.0 + color);
    color = pow(color, vec3(1.0 / 1.5));
    color = color * color * (3.0 - 2.0 * color);
    color = pow(color, vec3(1.3, 1.20, 1.0));
    color = clamp(color * 1.01, 0.0, 1.0);
    color = pow(color, vec3(0.7 / 2.2));

    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 pFar = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec4 pNear = InvViewProj * vec4(ndc, -1.0, 1.0);
    vec3 rd = normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);

    vec3 bhDir = normalize(vec3(0.34, 0.62, 0.71));
    float cosT = clamp(dot(rd, bhDir), -1.0, 1.0);
    float theta = acos(cosT);
    vec3 toHole = bhDir - cosT * rd;
    float tl = length(toHole);
    vec3 rdL = rd;
    if (tl > 1e-4) {
        rdL = normalize(rd + toHole / tl * (0.010 / max(theta, 0.05)));
    }

    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 stars = bhStars(rdL, Misc.x) * smoothstep(0.10, 0.17, theta);
    color += stars * Misc.w * clamp(1.0 - lum * 2.4, 0.0, 1.0);

    return color;
}

void main() {
    vec3 result;
    if (SkyCol2.w > 0.5 && SkyCol2.w < 1.5) {
        result = blackHole();
    } else {
        vec3 sky = texture(Sampler0, texCoord).rgb;
        vec3 bloom = texture(Sampler1, texCoord).rgb * 1.00
                   + texture(Sampler2, texCoord).rgb * 0.90
                   + texture(Sampler3, texCoord).rgb * 0.75;
        result = sky + bloom * 0.34;
    }
    fragColor = vec4(result, 1.0);
}
