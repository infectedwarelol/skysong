#version 150

// Turns a capture of the hands or of some entities into a mask for the effect passes:
// r = brightness of the model, g = 1 where it is in view (not behind a wall), a = coverage.
// Parts behind walls are dropped unless they should show through.

uniform sampler2D Sampler0;   // captured color
uniform sampler2D Sampler1;   // captured depth
uniform sampler2D Sampler2;   // scene depth
uniform vec4 Flags;           // compare depth, keep hidden parts

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, texCoord);
    if (c.a < 0.004) {
        fragColor = vec4(0.0);
        return;
    }
    float visible = 1.0;
    if (Flags.x > 0.5) {
        float own = texture(Sampler1, texCoord).r;
        float scene = texture(Sampler2, texCoord).r;
        // Depth is packed near 1; compare the distance to the far plane with a little slack
        visible = (1.0 - own) >= (1.0 - scene) * 0.985 - 1e-7 ? 1.0 : 0.0;
    }
    vec3 rgb = c.rgb / c.a;
    float luma = dot(rgb, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(luma, visible, 0.0, c.a * max(visible, Flags.y));
}
