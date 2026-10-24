#version 150

// Like the game's text programs: full-bright, 1 = one-channel (TrueType) glyphs whose red is the coverage.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float TextMode;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (TextMode > 0.5) tex = tex.rrrr;
    vec4 color = tex * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color;
}
