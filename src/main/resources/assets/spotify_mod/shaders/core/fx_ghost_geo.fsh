#version 150

// Afterimages: writes r = shading, a = how visible the copy still is (blending is off, so the
// nearest copy wins and the fade stays exact).

uniform sampler2D Sampler0;

in vec2 texCoord0;
in float fade;
in float shade;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    fragColor = vec4(shade, 0.0, 0.0, fade);
}
