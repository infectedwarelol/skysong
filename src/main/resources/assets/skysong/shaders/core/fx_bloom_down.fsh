#version 150

// One step of the sky bloom chain: a 3x3 tent filter into a texture half the size. The first
// step keeps only what is brighter than the threshold (HDR skies keep everything).

uniform sampler2D Sampler0;
uniform vec4 Params;   // texel x, texel y, threshold, source is HDR-encoded

in vec2 texCoord;
out vec4 fragColor;

vec3 decodeHDR(vec3 c) {
    c = pow(min(c, vec3(0.9975)), vec3(2.2));
    return c / max(1.0 - c, vec3(0.0055));
}

vec3 encodeHDR(vec3 c) {
    c = max(c, vec3(0.0));
    return pow(c / (1.0 + c), vec3(1.0 / 2.2));
}

vec3 fetch(vec2 uv, float hdr) {
    vec3 c = texture(Sampler0, uv).rgb;
    return hdr > 0.5 ? decodeHDR(c) : c;
}

void main() {
    vec2 hp = Params.xy;
    float hdr = Params.w;

    vec3 s = fetch(texCoord, hdr) * 4.0;
    s += fetch(texCoord + vec2(hp.x, 0.0), hdr) * 2.0;
    s += fetch(texCoord - vec2(hp.x, 0.0), hdr) * 2.0;
    s += fetch(texCoord + vec2(0.0, hp.y), hdr) * 2.0;
    s += fetch(texCoord - vec2(0.0, hp.y), hdr) * 2.0;
    s += fetch(texCoord + hp, hdr);
    s += fetch(texCoord - hp, hdr);
    s += fetch(texCoord + vec2(hp.x, -hp.y), hdr);
    s += fetch(texCoord - vec2(hp.x, -hp.y), hdr);
    s /= 16.0;

    if (hdr > 0.5) {
        fragColor = vec4(encodeHDR(s), 1.0);
        return;
    }
    fragColor = vec4(max(s - Params.z, vec3(0.0)), 1.0);
}
