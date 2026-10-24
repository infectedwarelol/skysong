#version 150

// World Shader → Storm: everything that faces up is wet and glossy, with puddles that mirror the
// world (screen-space reflections), a pale haze in the distance and a cool, punchy color grade.

uniform sampler2D Sampler0;   // the scene
uniform sampler2D Sampler1;   // scene depth
uniform sampler2D Sampler2;   // the lyrics mirrored under the floor (premultiplied color)
uniform sampler2D Sampler3;   // the screen before the lyrics (what the wet ground reflects)
uniform sampler2D Sampler4;   // its depth (reflection rays pass through the floating words)

uniform mat4 Proj;
uniform mat4 InvProj;
uniform mat4 ViewRot;         // world direction → view direction
uniform vec2 OutSize;
uniform vec4 CamPos;          // camera x, y, z (wrapped), time
uniform vec4 Params;          // reflections, puddles, fog, daylight
uniform vec4 FogCol;          // haze color, rain wetness boost
uniform vec4 Mirror;          // floor height relative to the camera, mirror on

in vec2 texCoord;
out vec4 fragColor;

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
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + 11.7;
        a *= 0.5;
    }
    return s;
}

float depthAt(vec2 uv) {
    return texture(Sampler1, uv).r;
}

vec3 viewPos(vec2 uv, float d) {
    vec4 p = InvProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

vec2 project(vec3 v) {
    vec4 c = Proj * vec4(v, 1.0);
    return c.xy / c.w * 0.5 + 0.5;
}

// Normal from the depth buffer, taking the flatter neighbour on each axis so edges stay clean
vec3 normalAt(vec2 uv, vec3 p) {
    vec2 px = 1.0 / OutSize;
    vec3 r = viewPos(uv + vec2(px.x, 0.0), depthAt(uv + vec2(px.x, 0.0)));
    vec3 l = viewPos(uv - vec2(px.x, 0.0), depthAt(uv - vec2(px.x, 0.0)));
    vec3 u = viewPos(uv + vec2(0.0, px.y), depthAt(uv + vec2(0.0, px.y)));
    vec3 d = viewPos(uv - vec2(0.0, px.y), depthAt(uv - vec2(0.0, px.y)));
    vec3 dx = abs(r.z - p.z) < abs(l.z - p.z) ? r - p : p - l;
    vec3 dy = abs(u.z - p.z) < abs(d.z - p.z) ? u - p : p - d;
    vec3 n = normalize(cross(dx, dy));
    return dot(n, -p) < 0.0 ? -n : n;
}

vec3 reflection(vec3 p, vec3 n, vec3 v, vec3 fallback, out float found) {
    vec3 r = normalize(reflect(v, n));
    found = 0.0;
    vec3 pos = p + n * 0.03;
    float stepLen = 0.12 + length(p) * 0.012;
    vec2 hitUv = vec2(0.0);
    for (int i = 0; i < 44; i++) {
        vec3 prev = pos;
        pos += r * stepLen;
        stepLen *= 1.1;
        vec2 suv = project(pos);
        if (suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0 || pos.z > -0.05) break;
        float sd = texture(Sampler4, suv).r;
        if (sd >= 1.0) continue;
        float diff = viewPos(suv, sd).z - pos.z;
        if (diff > 0.0 && diff < stepLen * 2.5 + 0.3) {
            vec3 a = prev, b = pos;
            for (int k = 0; k < 5; k++) {
                vec3 m = (a + b) * 0.5;
                vec2 muv = project(m);
                float md = viewPos(muv, texture(Sampler4, muv).r).z;
                if (md - m.z > 0.0) b = m; else a = m;
            }
            hitUv = project(b);
            vec2 edge = min(hitUv, 1.0 - hitUv);
            found = smoothstep(0.0, 0.08, min(edge.x, edge.y)) * (1.0 - float(i) / 44.0);
            break;
        }
    }
    vec3 hit = found > 0.0 ? texture(Sampler3, hitUv).rgb : fallback;
    return mix(fallback, hit, found);
}

// The mirrored lyrics, softened a little and rippled by the rain
vec4 lyricMirror(vec2 uv, vec2 ground) {
    vec2 px = 1.0 / OutSize;
    vec2 ripple = (vec2(vnoise(ground * 3.0 + CamPos.w * 1.7), vnoise(ground * 3.0 - CamPos.w * 1.3 + 7.0)) - 0.5) * 3.0 * px;
    vec2 u = uv + ripple;
    vec4 m = texture(Sampler2, u) * 0.4;
    m += texture(Sampler2, u + vec2(px.x, 0.0)) * 0.15;
    m += texture(Sampler2, u - vec2(px.x, 0.0)) * 0.15;
    m += texture(Sampler2, u + vec2(0.0, px.y * 1.5)) * 0.15;
    m += texture(Sampler2, u - vec2(0.0, px.y * 1.5)) * 0.15;
    return m;
}

vec3 grade(vec3 c) {
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, 1.18);                       // a bit more color
    c = (c - 0.5) * 1.08 + 0.5;                      // a bit more contrast
    c *= vec3(0.96, 0.99, 1.05);                     // cool storm light
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec3 col = texture(Sampler0, texCoord).rgb;
    float d = depthAt(texCoord);
    if (d >= 1.0) {
        fragColor = vec4(col, 1.0);
        return;
    }
    vec3 p = viewPos(texCoord, d);
    vec3 v = normalize(p);
    vec3 n = normalAt(texCoord, p);
    mat3 toWorld = transpose(mat3(ViewRot));
    vec3 nw = toWorld * n;
    vec3 pw = toWorld * p;

    // Wet: up-facing surfaces, puddles where the noise is high
    float upFacing = smoothstep(0.80, 0.97, nw.y);
    vec2 ground = pw.xz + CamPos.xz;
    float puddleNoise = fbm(ground * 0.28) * 0.75 + fbm(ground * 1.3) * 0.25;
    float coverage = clamp(Params.y + FogCol.w * 0.2, 0.0, 1.0);
    float puddle = smoothstep(1.0 - coverage - 0.08, 1.0 - coverage + 0.08, puddleNoise);
    float wet = upFacing * Params.x;

    if (wet > 0.01) {
        vec3 rw = toWorld * reflect(v, n);
        vec3 skyRefl = FogCol.rgb * mix(0.65, 1.0, clamp(rw.y, 0.0, 1.0));
        float found;
        vec3 refl = reflection(p, n, v, skyRefl, found);
        float fresnel = 0.02 + 0.98 * pow(1.0 - max(dot(-v, n), 0.0), 5.0);
        // Damp ground only glints at low angles; puddles are real mirrors
        float amount = wet * mix(0.32 + 0.4 * fresnel, 0.75, puddle);
        vec3 darkened = col * mix(1.0, 0.8, wet);
        col = mix(darkened, refl, clamp(amount, 0.0, 0.78));

        if (Mirror.y > 0.5) {
            // Lyrics mirrored in the wet floor they float over (fades on surfaces at other heights)
            float onFloor = exp(-abs(pw.y - Mirror.x) * 3.0);
            vec4 m = lyricMirror(texCoord, ground);
            float k = clamp(wet * onFloor * mix(0.7, 0.95, puddle), 0.0, 1.0);
            col = col * (1.0 - m.a * k) + m.rgb * k;
        }
    }

    // Haze: distance fog toward the storm's horizon color
    float dist = length(p);
    float fog = 1.0 - exp(-dist * Params.z * 0.02);
    fog *= fog;
    col = mix(col, FogCol.rgb, clamp(fog * 0.9, 0.0, 0.9));

    fragColor = vec4(grade(col), 1.0);
}
