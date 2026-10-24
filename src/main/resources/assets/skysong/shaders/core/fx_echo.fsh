#version 150

// Afterimages and Swing Trail: puts the white copies (or the hand's trail) on screen with a soft
// glow around them.

uniform sampler2D Sampler0;   // ghosts: r shading, a visibility | trail: premultiplied color, a
uniform sampler2D Sampler1;   // blurred alpha, for the glow
uniform sampler2D Sampler2;   // trail only: the hands right now (the trail is hidden under them)

uniform vec4 Look;            // opacity, glow strength, mode (0 ghosts, 1 trail), inner glow
uniform vec4 Tint;            // color
uniform vec4 Tint2;           // second color, flowing through the first (same as Tint for one color)
uniform vec4 Flow;            // time, screen aspect

in vec2 texCoord;
out vec4 fragColor;


float hashM(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noiseM(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hashM(i), hashM(i + vec2(1.0, 0.0)), f.x), mix(hashM(i + vec2(0.0, 1.0)), hashM(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Where two colors are in use: slow soft clouds of one flowing through the other
vec3 merged(vec2 uv) {
    vec2 p = uv * vec2(Flow.y, 1.0) * 2.6;
    float n = noiseM(p + vec2(Flow.x * 0.25, -Flow.x * 0.18)) * 0.65 + noiseM(p * 2.1 - Flow.x * 0.3) * 0.35;
    return mix(Tint.rgb, Tint2.rgb, smoothstep(0.28, 0.72, n));
}

vec4 over(vec4 src, vec4 dst) {
    float a = src.a + dst.a * (1.0 - src.a);
    if (a <= 0.0) return vec4(0.0);
    return vec4((src.rgb * src.a + dst.rgb * dst.a * (1.0 - src.a)) / a, a);
}

void main() {
    vec4 s = texture(Sampler0, texCoord);
    float a = s.a;
    bool trail = Look.z > 0.5;
    if (trail) {
        a = 1.0 - (1.0 - a) * (1.0 - a) * (1.0 - a);
        a *= 1.0 - texture(Sampler2, texCoord).a;
    }

    vec3 tint = merged(texCoord);
    vec3 col;
    if (trail) {
        col = s.a > 0.001 ? clamp(s.rgb / s.a, 0.0, 1.0) : tint;
    } else {
        col = tint * mix(0.8, 1.06, s.r);
    }

    float blurred = texture(Sampler1, texCoord).a;
    vec3 glowCol = mix(tint, vec3(1.0), 0.3);
    vec4 acc = vec4(0.0);

    // Glow around the shape
    if (Look.y > 0.0) {
        float g = (1.0 - exp(-blurred * Look.y * 1.6)) * (1.0 - smoothstep(0.0, 0.6, a));
        acc = vec4(glowCol, clamp(g, 0.0, 1.0));
    }

    // The shape, brighter toward its edges like it is lit from inside
    float fa = clamp(a * Look.x, 0.0, 1.0);
    if (fa > 0.002) {
        float rim = clamp(1.0 - blurred, 0.0, 1.0) * Look.w;
        vec3 c = mix(col, glowCol, rim);
        acc = over(vec4(c, fa), acc);
    }

    if (acc.a <= 0.002) discard;
    fragColor = acc;
}
