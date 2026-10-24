#version 150

// Multi-channel signed distance field text: sharp at any size and scale.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float DistanceRange;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec3 msd = texture(Sampler0, texCoord0).rgb;
    float sd = median(msd.r, msd.g, msd.b);
    vec2 unitRange = vec2(DistanceRange) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float coverage = clamp(screenPxRange * (sd - 0.5) * 1.45 + 0.5, 0.0, 1.0);
    vec4 color = vertexColor * ColorModulator;
    float a = coverage * color.a;
    if (a <= 0.002) {
        discard;
    }
    fragColor = vec4(color.rgb, a);
}
