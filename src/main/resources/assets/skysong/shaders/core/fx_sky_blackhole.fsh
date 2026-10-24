#version 150

// Black hole: a ray-marched accretion disc bending the starlight around it. Rendered at half
// size; each frame is blended with the last one (reprojected) to smooth the noise.

uniform sampler2D Sampler0;   // noise
uniform sampler2D Sampler1;   // last frame

uniform mat4 InvViewProj;
uniform mat4 PrevViewProj;
uniform vec4 Misc;      // time, width, height, brightness
uniform vec4 SkyCol;    // first color, color mode (0 one color, 1 two colors, 2 rainbow)
uniform vec4 SkyCol2;   // second color, sky type
uniform vec4 Taa;       // jitter x, jitter y, history valid, sequence

in vec2 texCoord;
out vec4 fragColor;

const int ITERATIONS = 170;
const float FAR = 16.0;
const float ENTRY_R = 8.0;
const float CAM_DIST = 14.0;
const float INCL = 0.10;
const float EXPOSURE = 0.024;
const float THEME_MIX = 0.5;
const float STAR_GAIN = 0.26;
const float ZOOM = 2.4;
const float TAA_BLEND = 0.85;
const float SPIN = 0.16;
const float TAU = 6.28318531;

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
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = (p.xy + vec2(37.0, 17.0) * p.z) + f.xy;
    vec2 rg = textureLod(Sampler0, (uv + 0.5) / 256.0, 0.0).yx;
    return -1.0 + 2.0 * mix(rg.x, rg.y, f.z);
}

vec3 discCoord(float ang, float rad, float freq) {
    float a = 1.425 * freq;
    return vec3(cos(ang) * a, sin(ang) * a, rad * freq);
}

float octave(float ang, float rad, float freq, float aa) {
    float w = clamp(1.5 - aa * freq * 1.5, 0.0, 1.0);
    if (w <= 0.01) {
        return 0.5;
    }
    return mix(0.5, noise(discCoord(ang, rad, freq)) * 0.5 + 0.5, w);
}

float pcurve(float x, float a, float b) {
    float k = pow(a + b, a + b) / (pow(a, a) * pow(b, b));
    return k * pow(x, a) * pow(1.0 - x, b);
}

float sdTorus(vec3 p, vec2 t) {
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q) - t.y;
}

vec3 encodeHDR(vec3 c) {
    c = max(c, vec3(0.0));
    return pow(c / (1.0 + c), vec3(1.0 / 2.2));
}

vec3 starField(vec3 dir, float time, float pixAngle) {
    vec3 c = vec3(0.0);
    vec3 p = dir * 42.0;
    float scale = 42.0;
    float dens = 0.05;
    float amp = 1.0;
    for (int i = 0; i < 4; i++) {
        float fade = clamp(2.0 - pixAngle * scale / 0.17, 0.0, 1.0);
        if (fade > 0.01) {
            vec3 id = floor(p);
            vec3 q = fract(p) - 0.5;
            vec3 rn = nmzHash33(id);
            float core = smoothstep(0.44, 0.05, length(q));
            float hit = step(rn.x, dens);
            float tw = 0.80 + 0.20 * sin(time * (0.6 + rn.z * 1.3) + rn.y * 47.0);
            vec3 tint = mix(vec3(1.0, 0.72, 0.45), vec3(0.72, 0.86, 1.0), rn.y);
            c += hit * core * core * tint * (0.35 + 0.65 * rn.z) * tw * amp * fade;
        }
        p = p * 1.63 + 17.0;
        scale *= 1.63;
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

void Haze(inout vec3 color, vec3 pos, float alpha, vec3 mainColor) {
    if (dot(pos, pos) > 36.0) {
        return;
    }
    vec2 t = vec2(1.0, 0.01);
    float torusDist = length(sdTorus(pos + vec3(0.0, -0.05, 0.0), t));
    float bloomDisc = 1.0 / (pow(torusDist, 2.0) + 0.001);
    bloomDisc *= length(pos) < 0.5 ? 0.0 : 1.0;
    color += mainColor * bloomDisc * (2.9 / float(ITERATIONS)) * (1.0 - alpha);
}

void GasDisc(inout vec3 color, inout float alpha, vec3 pos, float time, vec3 mainColor, float aa, vec3 eyevec) {
    float discRadius = 3.2;
    float discWidth = 5.3;
    float discInner = discRadius - discWidth * 0.5;

    vec3 discNormal = vec3(0.0, 1.0, 0.0);
    float discThickness = 0.1;

    float distFromCenter = length(pos);
    if (distFromCenter > 7.0) {
        return;
    }
    float distFromDisc = dot(discNormal, pos);

    float radialGradient = 1.0 - clamp((distFromCenter - discInner) / discWidth * 0.5, 0.0, 1.0);
    float coverage = pcurve(radialGradient, 4.0, 0.9);

    discThickness *= radialGradient;
    coverage *= clamp(1.0 - abs(distFromDisc) / max(discThickness, 1e-5), 0.0, 1.0);

    vec3 tangent = normalize(vec3(-pos.z, 0.0, pos.x));
    float dop = pow(clamp(1.0 - 0.55 * dot(tangent, eyevec), 0.45, 1.9), -2.5);
    vec3 dopTint = mix(vec3(1.30, 0.72, 0.45), vec3(0.80, 0.92, 1.45), clamp((dop - 0.7) * 0.9, 0.0, 1.0));

    vec3 dustColorLit = mainColor * dop * dopTint;
    float dustGlow = 1.0 / (pow(1.0 - radialGradient, 2.0) * 290.0 + 0.002);
    vec3 dustColor = dustColorLit * dustGlow * 8.2;

    coverage = clamp(coverage * 0.7, 0.0, 1.0);

    float fade = pow((abs(distFromCenter - discInner) + 0.4), 4.0) * 0.04;
    float bloomFactor = 1.0 / (pow(distFromDisc, 2.0) * 40.0 + fade + 0.00002);
    vec3 b = dustColorLit * pow(bloomFactor, 1.5);

    b *= mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(pow(radialGradient, 2.0)));
    b *= mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(pow(radialGradient, 0.5)));

    dustColor = mix(dustColor, b * 150.0, clamp(1.0 - coverage, 0.0, 1.0));
    coverage = clamp(coverage + bloomFactor * bloomFactor * 0.1, 0.0, 1.0);

    if (coverage < 0.01) {
        return;
    }

    float ang = atan(-pos.x, -pos.z);
    float rad = (distFromCenter * 1.5 + 0.55 + distFromDisc * 1.5) * 0.95 + time * 0.012;

    float omega = SPIN * pow(3.2 / max(distFromCenter, 0.75), 1.5);
    float angA = ang + mod(time * omega, TAU);
    float angB = ang + mod(time * omega * 0.45, TAU);

    float n1 = 1.0;
    n1 *= octave(angA, rad, 3.0, aa);
    n1 *= octave(angB, rad, 6.0, aa);
    n1 *= octave(angA, rad, 12.0, aa);
    n1 *= octave(angB, rad, 24.0, aa);

    float n2 = 2.0;
    float rad2 = rad + 30.0;
    n2 *= octave(angB, rad2, 3.0, aa);
    n2 *= octave(angA, rad2, 6.0, aa);
    n2 *= octave(angB, rad2, 12.0, aa);
    n2 *= octave(angA, rad2, 24.0, aa);
    n2 *= octave(angB, rad2, 48.0, aa);
    n2 *= octave(angA, rad2, 92.0, aa);

    dustColor *= n1 * 0.998 + 0.002;
    coverage *= n2;

    float bandAng = ang + mod(time * omega * 0.5, TAU);
    float band = noise(discCoord(bandAng, rad, 1.35)) * 0.5 + 0.5;
    float grain = noise(discCoord(bandAng, rad + 70.0, 3.46)) * 0.5 + 0.5;
    vec3 texCol = mix(vec3(0.95, 0.55, 0.26), vec3(0.42, 0.60, 1.0), band) * (0.45 + 0.80 * grain);
    dustColor *= pow(texCol, vec3(2.0)) * 4.0;

    float arm = 0.5 + 0.5 * cos(2.0 * ang + 2.6 * log(max(distFromCenter, 0.3)) + time * SPIN * 4.5);
    dustColor *= 0.40 + 1.05 * arm * arm;
    coverage *= 0.78 + 0.22 * arm;

    float dAngA = abs(mod(angA - 1.35, TAU) - 3.14159265);
    float dRadA = distFromCenter - 1.5;
    float dAngB = abs(mod(angB - 4.2, TAU) - 3.14159265);
    float dRadB = distFromCenter - 2.7;
    float hot = exp(-(dAngA * dAngA * 0.5 + dRadA * dRadA * 7.0)) * 3.4
              + exp(-(dAngB * dAngB * 0.9 + dRadB * dRadB * 5.0)) * 1.8;
    dustColor *= 1.0 + hot;

    coverage = clamp(coverage * 1200.0 / float(ITERATIONS), 0.0, 1.0);
    dustColor = max(vec3(0.0), dustColor);

    coverage *= pcurve(radialGradient, 4.0, 0.9);

    color = (1.0 - alpha) * dustColor * coverage + color;
    alpha = (1.0 - alpha) * coverage + alpha;
}

void WarpSpace(inout vec3 eyevec, vec3 raypos) {
    float singularityDist = length(raypos);
    float warpFactor = 1.0 / (pow(singularityDist, 2.0) + 0.000001);
    vec3 singularityVector = -raypos / max(singularityDist, 1e-4);
    float warpAmount = 5.0;
    eyevec = normalize(eyevec + singularityVector * warpFactor * warpAmount / float(ITERATIONS));
}

vec3 rayDir(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 pFar = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec4 pNear = InvViewProj * vec4(ndc, -1.0, 1.0);
    return normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);
}

vec3 toLocal(vec3 dir, vec3 eX, vec3 eY, vec3 eZ) {
    vec3 local = vec3(dot(dir, eX), dot(dir, eY), dot(dir, eZ));
    local.xy /= ZOOM;
    return normalize(local);
}

vec3 sampleHistory(vec2 uv, vec2 res) {
    vec2 samplePos = uv * res;
    vec2 texPos1 = floor(samplePos - 0.5) + 0.5;
    vec2 f = samplePos - texPos1;

    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);

    vec2 w12 = w1 + w2;
    vec2 offset12 = w2 / w12;

    vec2 p0 = (texPos1 - 1.0) / res;
    vec2 p3 = (texPos1 + 2.0) / res;
    vec2 p12 = (texPos1 + offset12) / res;

    vec3 result = vec3(0.0);
    result += textureLod(Sampler1, vec2(p0.x, p0.y), 0.0).rgb * (w0.x * w0.y);
    result += textureLod(Sampler1, vec2(p12.x, p0.y), 0.0).rgb * (w12.x * w0.y);
    result += textureLod(Sampler1, vec2(p3.x, p0.y), 0.0).rgb * (w3.x * w0.y);
    result += textureLod(Sampler1, vec2(p0.x, p12.y), 0.0).rgb * (w0.x * w12.y);
    result += textureLod(Sampler1, vec2(p12.x, p12.y), 0.0).rgb * (w12.x * w12.y);
    result += textureLod(Sampler1, vec2(p3.x, p12.y), 0.0).rgb * (w3.x * w12.y);
    result += textureLod(Sampler1, vec2(p0.x, p3.y), 0.0).rgb * (w0.x * w3.y);
    result += textureLod(Sampler1, vec2(p12.x, p3.y), 0.0).rgb * (w12.x * w3.y);
    result += textureLod(Sampler1, vec2(p3.x, p3.y), 0.0).rgb * (w3.x * w3.y);

    return clamp(result, vec3(0.0), vec3(1.0));
}

void main() {
    float time = Misc.x;
    float brightness = Misc.w;

    vec2 res = vec2(textureSize(Sampler1, 0));

    vec3 rd = rayDir(texCoord);
    vec3 rdJitter = rayDir(texCoord + Taa.xy / res);

    vec3 bhDir = normalize(vec3(0.34, 0.62, 0.71));
    vec3 upRef = vec3(0.0, 1.0, 0.0);
    vec3 tang = normalize(upRef - dot(upRef, bhDir) * bhDir);
    vec3 eY = normalize(tang * cos(INCL) - bhDir * sin(INCL));
    vec3 eX = normalize(cross(eY, bhDir));
    vec3 eZ = cross(eX, eY);

    vec3 tint = skyTint(atan(rd.z, rd.x), 0.5, time);
    vec3 mainColor = mix(vec3(1.0), tint, THEME_MIX);

    vec3 camWorld = -bhDir * CAM_DIST;
    vec3 pos = vec3(dot(camWorld, eX), dot(camWorld, eY), dot(camWorld, eZ));
    vec3 eyevec = toLocal(rdJitter, eX, eY, eZ);
    float pixAngle = length(toLocal(rayDir(texCoord + vec2(1.0, 0.0) / res), eX, eY, eZ)
            - toLocal(rd, eX, eY, eZ));
    float aa = pixAngle * CAM_DIST * 1.425;

    vec3 color = vec3(0.0);
    float alpha = 0.0;
    float captured = 0.0;

    float impact = length(cross(pos, eyevec));
    float along = dot(pos, eyevec);

    if (impact < ENTRY_R && along < 0.0) {
        float tEnter = -along - sqrt(max(0.0, ENTRY_R * ENTRY_R - impact * impact));
        float stepLen = FAR / float(ITERATIONS);
        float dither = fract(hash21(gl_FragCoord.xy) + Taa.w);
        vec3 raypos = pos + eyevec * (tEnter + dither * stepLen);

        for (int i = 0; i < ITERATIONS; i++) {
            WarpSpace(eyevec, raypos);
            raypos += eyevec * stepLen;
            GasDisc(color, alpha, raypos, time, mainColor, aa, eyevec);
            Haze(color, raypos, alpha, mainColor);

            float r2 = dot(raypos, raypos);
            captured = max(captured, 1.0 - smoothstep(0.32, 0.85, sqrt(r2)));
            if (alpha > 0.995) {
                break;
            }
            if (r2 > 49.0 && dot(raypos, eyevec) > 0.0) {
                break;
            }
        }
    }

    color *= EXPOSURE;

    float ring = pow(clamp(captured * (1.0 - captured) * 4.0, 0.0, 1.0), 6.0);
    color += mix(vec3(1.0), mainColor, 0.35) * ring * 0.6 * (1.0 - alpha);

    color *= brightness;

    vec3 cur = encodeHDR(color);

    if (Taa.z > 0.5) {
        vec4 pc = PrevViewProj * vec4(rd, 0.0);
        if (pc.w > 1e-5) {
            vec2 prevUV = pc.xy / pc.w * 0.5 + 0.5;
            vec2 guard = 1.5 / res;
            if (all(greaterThan(prevUV, guard)) && all(lessThan(prevUV, 1.0 - guard))) {
                vec3 hist = clamp(sampleHistory(prevUV, res), cur - 0.35, cur + 0.35);
                cur = mix(cur, hist, TAA_BLEND);
            }
        }
    }

    cur += (fract(hash21(gl_FragCoord.xy + 13.7) + Taa.w) - 0.5) / 255.0;

    fragColor = vec4(max(cur, vec3(0.0)), 1.0);
}
