#version 150

// One direction of a gaussian blur of the mask coverage, for the soft glow around a shape.

uniform sampler2D Sampler0;
uniform vec4 Blur;   // step x, step y (one texel in the source), radius in texels

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float radius = max(Blur.z, 0.5);
    float sigma = radius / 2.2;
    float sum = 0.0;
    float weights = 0.0;
    for (int i = -8; i <= 8; i++) {
        float o = float(i) * radius / 8.0;
        float w = exp(-(o * o) / (2.0 * sigma * sigma));
        sum += texture(Sampler0, texCoord + Blur.xy * o).a * w;
        weights += w;
    }
    fragColor = vec4(0.0, 0.0, 0.0, sum / weights);
}
