#version 150

// The same box blur as vanilla's menu blur (box_blur.fsh, Minecraft 1.20.5+), for versions that
// do not have it: the glass panels look the same on every version.

uniform sampler2D Sampler0;
uniform vec4 Box;   // step x, step y (one texel in the blur direction), radius in texels

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 sampleStep = Box.xy;
    vec4 blurred = vec4(0.0);
    float actualRadius = round(Box.z);
    // Linear sampling between texel pairs halves the samples; the last texel gets half a weight
    for (float a = -actualRadius + 0.5; a <= actualRadius; a += 2.0) {
        blurred += texture(Sampler0, texCoord + sampleStep * a);
    }
    blurred += texture(Sampler0, texCoord + sampleStep * actualRadius) / 2.0;
    fragColor = blurred / (actualRadius + 0.5);
}
