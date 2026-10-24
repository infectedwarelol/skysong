#version 150

// Swing Trail: the hands of this frame, whitened, laid over the fading trail of the frames
// before. Stored premultiplied.

uniform sampler2D Sampler0;   // the trail so far
uniform sampler2D Sampler1;   // the hands right now

uniform vec4 Trail;           // how much of the old trail stays, whitening, unused, unused
uniform vec4 Tint;
uniform vec4 Tint2;
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

void main() {
    // Fade the old trail (the small step makes sure it reaches zero in 8 bits)
    vec4 prev = max(texture(Sampler0, texCoord) * Trail.x - vec4(1.5 / 255.0), vec4(0.0));
    vec4 cur = texture(Sampler1, texCoord);
    float a = cur.a;
    vec3 rgb = a > 0.001 ? clamp(cur.rgb / a, 0.0, 1.0) : vec3(0.0);
    vec3 white = mix(rgb, merged(texCoord), Trail.y);
    vec4 now = vec4(white * a, a);
    fragColor = now + prev * (1.0 - a);
}
