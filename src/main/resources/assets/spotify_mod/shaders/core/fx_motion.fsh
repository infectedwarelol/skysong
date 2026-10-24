#version 150

// Motion Blur: each pixel is smeared along the way it moved on screen since the last frame,
// worked out from its depth and the camera's turn and movement. Only the world is blurred (the
// hands and the HUD come after), a still camera stays perfectly sharp, and the smear is capped so
// fast flicks never turn the screen into mush.

uniform sampler2D Sampler0;   // the world this frame
uniform sampler2D Sampler1;   // its depth

uniform mat4 InvViewProj;     // this frame: screen -> world (camera-relative)
uniform mat4 PrevViewProj;    // last frame: world (camera-relative) -> screen
uniform vec4 CamDelta;        // camera movement since the last frame, include movement
uniform vec4 Params;          // blur scale, max length (screen fraction), samples, sharp center
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec4 here = texture(Sampler0, texCoord);
    float d = texture(Sampler1, texCoord).r;

    vec4 w = InvViewProj * vec4(texCoord * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec3 rel = w.xyz / w.w;
    // The sky only turns with the camera; the world also slides as the camera moves
    vec3 prevRel = d >= 1.0 ? rel : rel + CamDelta.xyz * CamDelta.w;
    vec4 prevClip = PrevViewProj * vec4(prevRel, 1.0);
    if (prevClip.w <= 0.0001) {
        fragColor = here;
        return;
    }
    vec2 prevUv = prevClip.xy / prevClip.w * 0.5 + 0.5;
    vec2 vel = (texCoord - prevUv) * Params.x;

    // Keep the crosshair area sharp for aiming
    if (Params.w > 0.0) {
        vec2 c = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
        vel *= mix(1.0, smoothstep(0.02, 0.22, length(c)), Params.w);
    }

    // Cap the length (in screen height units, so it looks the same on any window)
    vec2 aspect = vec2(OutSize.x / OutSize.y, 1.0);
    float len = length(vel * aspect);
    if (len > Params.y) vel *= Params.y / len;
    // Tiny movement: no blur at all, the picture stays crisp
    float px = length(vel * OutSize);
    if (px < 0.75) {
        fragColor = here;
        return;
    }
    vel *= smoothstep(0.75, 2.5, px);

    int n = int(Params.z + 0.5);
    float jitter = ign(gl_FragCoord.xy) - 0.5;
    vec3 sum = here.rgb;
    float weights = 1.0;
    for (int i = 0; i < 32; i++) {
        if (i >= n) break;
        float t = (float(i) + 0.5 + jitter) / float(n) - 0.5;
        vec2 uv = texCoord - vel * t;
        if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) continue;
        // Center-weighted so edges stay readable
        float wgt = 1.0 - abs(t) * 0.9;
        sum += texture(Sampler0, uv).rgb * wgt;
        weights += wgt;
    }
    fragColor = vec4(sum / weights, here.a);
}
