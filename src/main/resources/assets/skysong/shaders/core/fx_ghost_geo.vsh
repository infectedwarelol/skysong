#version 150

// Afterimages: a frozen copy of a player's model. Only the texture's alpha is used (for the
// cut-out skin pixels); the copy itself is drawn as a white shape.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out float fade;
out float shade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    fade = Color.a;
    // Soft light from above so the copy keeps its shape
    vec3 n = normalize(Normal + vec3(0.0, 1e-4, 0.0));
    shade = clamp(0.55 + 0.45 * dot(n, normalize(vec3(0.25, 1.0, 0.35))), 0.0, 1.0);
}
