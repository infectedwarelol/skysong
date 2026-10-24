#version 150

// Rounded rectangle (per-corner radii) as a signed distance field, filled or stroked, with the
// four corner colors interpolated across it. Optionally textured (album art, skins) and clipped
// to the same shape. Anti-aliasing uses the screen-space derivative, so edges are one pixel soft
// at any scale.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec2 Size;
uniform vec4 Radius;    // top-left, top-right, bottom-right, bottom-left
uniform float Thickness; // 0 = filled
uniform float TexMode;
uniform vec4 UVRect;

in vec2 texCoord0; // local position in units (0..Size, extended by the AA padding)
in vec4 vertexColor;

out vec4 fragColor;

float roundBox(vec2 local) {
    vec2 c = local - Size * 0.5;
    float r = c.x < 0.0 ? (c.y < 0.0 ? Radius.x : Radius.w) : (c.y < 0.0 ? Radius.y : Radius.z);
    r = min(r, min(Size.x, Size.y) * 0.5);
    vec2 q = abs(c) - Size * 0.5 + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    float d = roundBox(texCoord0);
    float w = max(fwidth(d), 1e-4);
    float cover = clamp(0.5 - d / w, 0.0, 1.0);
    if (Thickness > 0.0) {
        cover -= clamp(0.5 - (d + Thickness) / w, 0.0, 1.0);
    }
    vec4 color = vertexColor;
    if (TexMode > 0.5) {
        vec2 uv = UVRect.xy + clamp(texCoord0 / Size, 0.0, 1.0) * (UVRect.zw - UVRect.xy);
        color *= texture(Sampler0, uv);
    }
    color *= ColorModulator;
    float a = color.a * cover;
    if (a < 0.002) {
        discard;
    }
    fragColor = vec4(color.rgb, a);
}
