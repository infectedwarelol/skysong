#version 150

// Blocks: color grading for the world (saturation, brightness, contrast) and a soft wash of the
// client palette over it. The custom sky is left alone.

uniform sampler2D Sampler0;   // the scene
uniform sampler2D Sampler1;   // scene depth

uniform vec4 Grade;    // saturation, brightness, contrast, palette tint
uniform vec4 Style;    // palette size, phase, closed loop, skip the sky
uniform vec4 Pal0;
uniform vec4 Pal1;
uniform vec4 Pal2;
uniform vec4 Pal3;
uniform vec4 Pal4;
uniform vec4 Pal5;

in vec2 texCoord;
out vec4 fragColor;

vec3 pal(int i) {
    if (i <= 0) return Pal0.rgb;
    if (i == 1) return Pal1.rgb;
    if (i == 2) return Pal2.rgb;
    if (i == 3) return Pal3.rgb;
    if (i == 4) return Pal4.rgb;
    return Pal5.rgb;
}

vec3 paletteRamp(float t) {
    int count = int(Style.x + 0.5);
    if (count <= 1) return pal(0);
    float f = clamp(t, 0.0, 1.0) * float(count - 1);
    int i = clamp(int(floor(f)), 0, count - 1);
    int j = min(i + 1, count - 1);
    float frac = clamp(f - float(i), 0.0, 1.0);
    frac = frac * frac * (3.0 - 2.0 * frac);
    return mix(pal(i), pal(j), frac);
}

void main() {
    vec3 c = texture(Sampler0, texCoord).rgb;
    if (Style.w > 0.5 && texture(Sampler1, texCoord).r >= 0.99999) {
        fragColor = vec4(c, 1.0);
        return;
    }
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(luma), c, Grade.x);
    c = (c - 0.5) * Grade.z + 0.5;
    c *= Grade.y;

    // Palette wash: slow diagonal waves, keeping the light and shade of the scene
    float t = 0.5 + 0.5 * sin((texCoord.x * 1.3 + texCoord.y * 0.8) * 3.14159 + Style.y * 6.2831853);
    vec3 tint = paletteRamp(t);
    float l = dot(max(c, 0.0), vec3(0.299, 0.587, 0.114));
    vec3 washed = tint * (l * 1.35 + 0.04);
    c = mix(c, washed, Grade.w * 0.6);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
