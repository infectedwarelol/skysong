#version 150

// Storm: a heavy overcast of rolling clouds, lit from behind by the sun breaking through in bright
// patches, with a pale haze at the horizon and the odd lightning flash inside the clouds.

uniform mat4 InvViewProj;
uniform vec4 Misc;      // time, width, height, brightness
uniform vec4 SkyCol;    // first color, color mode
uniform vec4 Sun;       // direction to the sun, daylight (0 night - 1 day)

in vec2 texCoord;
out vec4 fragColor;

const mat2 ROT = mat2(0.80, 0.60, -0.60, 0.80);

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

float fbm(vec2 p, int octaves) {
    float amp = 0.5;
    float sum = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        sum += amp * vnoise(p);
        p = ROT * p * 2.02 + 7.1;
        amp *= 0.5;
    }
    return sum;
}

// Cloud density on the cloud layer, with the domain warped so the clouds billow and roll
float clouds(vec2 p, float time) {
    vec2 drift = vec2(time * 0.018, time * 0.006);
    vec2 q = vec2(fbm(p * 0.6 + drift, 4), fbm(p * 0.6 - drift + 3.7, 4));
    float d = fbm(p + q * 1.6 + drift * 2.0, 6);
    return smoothstep(0.30, 0.78, d);
}

void main() {
    float time = Misc.x;
    float brightness = Misc.w;
    float day = Sun.w;

    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 pFar = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec4 pNear = InvViewProj * vec4(ndc, -1.0, 1.0);
    vec3 rd = normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);
    vec3 sunDir = normalize(Sun.xyz);
    float sunAmount = max(dot(rd, sunDir), 0.0);

    // Base: grey-blue overcast, paler toward the horizon
    vec3 zenith = mix(vec3(0.035, 0.04, 0.055), vec3(0.20, 0.22, 0.26), day);
    vec3 horizon = mix(vec3(0.07, 0.075, 0.09), vec3(0.62, 0.65, 0.70), day);
    float up = clamp(rd.y, 0.0, 1.0);
    vec3 col = mix(horizon, zenith, pow(up, 0.55));

    if (rd.y > -0.05) {
        // Cloud layer
        float t = 1.0 / (max(rd.y, 0.0) + 0.08);
        vec2 p = rd.xz * t * 1.3;
        float dens = clouds(p, time);
        // Light from the sun: thin cloud glows, thick cloud is dark underneath
        float thin = 1.0 - dens;
        float lightPath = clouds(p + sunDir.xz * 0.35, time);
        float lit = clamp(0.55 + (dens - lightPath) * 1.6, 0.0, 1.0);
        vec3 dark = mix(vec3(0.03, 0.035, 0.045), vec3(0.16, 0.17, 0.20), day);
        vec3 light = mix(vec3(0.10, 0.11, 0.13), vec3(0.78, 0.80, 0.84), day);
        vec3 cloudCol = mix(dark, light, lit * 0.8 + thin * 0.2);
        // Silver lining and glare where the sun breaks through
        float glare = pow(sunAmount, 8.0) * thin;
        cloudCol += vec3(1.0, 0.97, 0.92) * (glare * 1.6 + pow(sunAmount, 60.0) * thin * 2.5) * day;
        float fade = smoothstep(-0.05, 0.12, rd.y);
        col = mix(col, cloudCol, dens * 0.92 * fade + 0.25 * fade);

        // Lightning: a rare flash lighting a patch of cloud from inside
        float slot = floor(time * 1.3);
        float strike = step(0.965, hash21(vec2(slot, 7.0)));
        if (strike > 0.0) {
            float phase = fract(time * 1.3);
            float flash = strike * (exp(-phase * 9.0) + 0.6 * exp(-abs(phase - 0.25) * 30.0));
            vec2 at = vec2(hash21(vec2(slot, 1.0)), hash21(vec2(slot, 2.0))) * 6.28318;
            vec3 dir = normalize(vec3(cos(at.x), 0.35 + 0.4 * hash21(vec2(slot, 3.0)), sin(at.x)));
            float near = pow(max(dot(rd, dir), 0.0), 18.0);
            col += vec3(0.75, 0.8, 1.0) * flash * (near * 1.5 + 0.12) * dens * fade;
        }
    }

    // Sun disc softened by the cloud cover
    col += vec3(1.0, 0.96, 0.88) * pow(sunAmount, 400.0) * 0.6 * day;
    // Night: a few stars through gaps
    if (day < 0.5) {
        vec2 sp = rd.xz / (abs(rd.y) + 0.2) * 90.0;
        float star = step(0.9985, hash21(floor(sp))) * smoothstep(0.05, 0.3, rd.y);
        col += vec3(star) * (0.5 - day) * 0.6;
    }
    // Tint with the chosen colors a touch
    col = mix(col, col * (0.75 + 0.5 * SkyCol.rgb), 0.12);
    fragColor = vec4(col * brightness, 1.0);
}
