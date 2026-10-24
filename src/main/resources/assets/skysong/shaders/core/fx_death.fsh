#version 150

// Death Effect, four ways to go:
//  0 Shattered Glass: your view cracks like glass hit from the front, the cracks glow in your
//    colors, the world drains grey, the edges pulse dark red with two heartbeats.
//  1 Flatline: the world goes cold, dark and grainy like a hospital monitor (the heartbeat line
//    itself is drawn on the HUD).
//  2 Ascension: you are lifted out: light pours down from above in rays, everything blooms and
//    washes out to bright light while motes drift up.
//  3 Signal Lost: the picture breaks up like a dying video feed: lines tear sideways, blocks
//    corrupt, the colors split, a bar rolls through, and it all sinks into static.

uniform sampler2D Sampler0;   // the world

uniform vec4 Death;           // seconds since death, cracks on, color drained, heartbeat pulse
uniform vec4 Tint;            // color one
uniform vec4 Tint2;           // color two (same as one for a single color)
uniform vec4 Mode;            // which effect
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 a = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    a += dot(a, a.yzx + 33.33);
    return fract((a.xx + a.yz) * a.zy);
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 tintAt(float k) {
    return mix(Tint.rgb, Tint2.rgb, clamp(k, 0.0, 1.0));
}

// ── 0: Shattered Glass ─────────────────────────────────────────────

void voronoi(vec2 x, out float f1, out float f2, out vec2 cell) {
    vec2 n = floor(x), f = fract(x);
    f1 = 8.0;
    f2 = 8.0;
    cell = n;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 r = g + hash22(n + g) - f;
            float d = dot(r, r);
            if (d < f1) {
                f2 = f1;
                f1 = d;
                cell = n + g;
            } else if (d < f2) {
                f2 = d;
            }
        }
    }
    f1 = sqrt(f1);
    f2 = sqrt(f2);
}

vec3 shattered(vec2 uv, vec2 c, float r, float t) {
    float zoom = 1.0 - 0.07 * smoothstep(0.0, 3.5, t);
    vec2 zuv = 0.5 + (uv - 0.5) * zoom;
    vec2 warped = c * (4.0 + 9.0 * exp(-r * 3.0));
    float f1, f2;
    vec2 cell;
    voronoi(warped, f1, f2, cell);
    float edge = f2 - f1;
    float reach = 1.6 * smoothstep(0.0, 0.32, t);
    float cracked = Death.y * (1.0 - smoothstep(reach - 0.12, reach, r));
    vec2 shift = (hash22(cell + 7.3) - 0.5) * 0.02 * cracked * smoothstep(0.0, 0.15, r);
    vec3 col = texture(Sampler0, clamp(zuv + shift, 0.0, 1.0)).rgb;
    col *= mix(1.0, 0.86 + 0.28 * hash22(cell).x, cracked);
    float width = 0.028 + 0.03 * exp(-r * 4.0);
    float line = (1.0 - smoothstep(0.0, width, edge)) * cracked;
    float rim = (1.0 - smoothstep(width, width * 2.6, edge)) * cracked;
    col = mix(col, col * 0.2, line * 0.85);
    col += tintAt(hash22(cell).y) * (rim - line * 0.5) * (0.35 + 0.45 * Death.w);
    col = mix(col, vec3(luma(col)) * vec3(1.03, 0.97, 0.97), Death.z);
    float vig = smoothstep(0.3, 1.05, r);
    col = mix(col, col * 0.2 + vec3(0.32, 0.02, 0.04) * (0.4 + Death.w), vig * (0.5 + 0.4 * Death.w));
    col += vec3(1.0) * exp(-r * 7.0) * max(0.0, 1.0 - t * 5.0) * 0.9;
    return col;
}

// ── 1: Flatline ────────────────────────────────────────────────────

vec3 flatline(vec2 uv, float r, float t) {
    vec3 col = texture(Sampler0, uv).rgb;
    float cold = smoothstep(0.0, 1.2, t);
    col = mix(col, vec3(luma(col)) * vec3(0.82, 0.95, 1.08), cold);
    col *= mix(1.0, 0.3, cold);
    // Monitor scanlines and grain
    col *= 0.88 + 0.12 * sin(gl_FragCoord.y * 1.5708);
    col += (hash12(gl_FragCoord.xy + fract(t) * 431.0) - 0.5) * 0.06 * cold;
    // Each beat lights the screen a little in your colors, then it stays dark
    col += tintAt(uv.x) * Death.w * 0.14 * (1.0 - smoothstep(0.2, 0.9, r));
    col *= 1.0 - smoothstep(0.4, 1.1, r) * 0.85;
    return col;
}

// ── 2: Ascension ───────────────────────────────────────────────────

vec3 ascension(vec2 uv, vec2 c, float t) {
    float lift = smoothstep(0.0, 4.0, t);
    // Rising: the world slides away below you
    vec2 duv = uv + vec2(0.0, 0.05 * lift);
    vec3 col = texture(Sampler0, clamp(duv, 0.0, 1.0)).rgb;
    // Rays: the picture smeared toward a light above the middle
    vec2 light = vec2(0.5, 1.2);
    vec3 rays = vec3(0.0);
    float amount = smoothstep(0.0, 1.0, t);
    for (int i = 0; i < 12; i++) {
        float k = float(i) / 12.0;
        vec2 s = duv + (light - duv) * k * 0.22 * amount;
        vec3 smp = texture(Sampler0, clamp(s, 0.0, 1.0)).rgb;
        rays += max(smp - 0.45, 0.0) * (1.0 - k);
    }
    col += rays * 0.55 * amount;
    // Bloom and a warm wash toward light
    col += max(col - 0.55, 0.0) * 1.6 * amount;
    vec3 heaven = mix(vec3(1.0, 0.96, 0.88), tintAt(uv.y) * 0.5 + 0.6, 0.35);
    col = mix(col, heaven, smoothstep(0.6, 4.5, t) * 0.8 * (0.6 + 0.4 * uv.y));
    col = mix(col, vec3(luma(col)), 0.25 * amount);
    // Motes of light drifting up
    vec2 p = uv * vec2(OutSize.x / max(OutSize.y, 1.0), 1.0) * 22.0 + vec2(0.0, -t * 1.1);
    vec2 cell = floor(p);
    float h = hash12(cell);
    if (h > 0.9) {
        vec2 center = cell + 0.3 + 0.4 * hash22(cell + 3.0);
        float d = length(p - center);
        col += tintAt(h) * 0.5 * (1.0 - smoothstep(0.0, 0.12, d)) * amount + vec3(1.0) * (1.0 - smoothstep(0.0, 0.05, d)) * amount;
    }
    // The light is strongest overhead
    col += vec3(1.0, 0.95, 0.85) * exp(-length(c - vec2(0.0, 0.62)) * 2.4) * 0.35 * amount;
    return col;
}

// ── 3: Signal Lost ─────────────────────────────────────────────────

vec3 signalLost(vec2 uv, float t) {
    float g = smoothstep(0.0, 1.6, t);
    float frame = floor(t * 14.0);
    // Lines tearing sideways
    float row = floor(uv.y * 54.0);
    float tear = hash12(vec2(row, frame));
    if (tear > 1.0 - 0.35 * g) uv.x += (hash12(vec2(row, frame + 3.0)) - 0.5) * 0.18 * g;
    // Corrupt blocks
    vec2 blk = floor(uv * vec2(22.0, 13.0));
    float bh = hash12(blk + floor(t * 9.0) * 7.1);
    if (bh > 1.0 - 0.09 * g) uv += (hash22(blk + frame) - 0.5) * 0.12;
    // The colors split apart
    float split = (0.003 + 0.014 * g) * (0.6 + 0.4 * sin(t * 23.0));
    vec3 col;
    col.r = texture(Sampler0, clamp(uv + vec2(split, 0.0), 0.0, 1.0)).r;
    col.g = texture(Sampler0, clamp(uv, 0.0, 1.0)).g;
    col.b = texture(Sampler0, clamp(uv - vec2(split, 0.0), 0.0, 1.0)).b;
    // Tinted corruption in the broken blocks
    if (bh > 1.0 - 0.09 * g) col = mix(col, tintAt(bh), 0.5);
    // A dark bar rolling up the screen, scanlines
    float bar = 1.0 - smoothstep(0.0, 0.06, abs(fract(uv.y - t * 0.4) - 0.5) - 0.02);
    col *= 1.0 - 0.45 * bar * g;
    col *= 0.86 + 0.14 * sin(gl_FragCoord.y * 1.5708);
    // Sinking into static
    float n = hash12(gl_FragCoord.xy * 0.5 + vec2(frame * 13.1, frame * 7.7));
    vec3 snow = vec3(n) * mix(vec3(1.0), tintAt(n) + 0.3, 0.25);
    col = mix(col, snow, smoothstep(0.9, 3.2, t) * 0.88);
    col = mix(col, vec3(luma(col)), 0.35 * g);
    return col;
}

void main() {
    float t = Death.x;
    vec2 aspect = vec2(OutSize.x / max(OutSize.y, 1.0), 1.0);
    vec2 c = (texCoord - 0.5) * aspect;
    float r = length(c);
    int mode = int(Mode.x + 0.5);
    vec3 col;
    if (mode == 1) col = flatline(texCoord, r, t);
    else if (mode == 2) col = ascension(texCoord, c, t);
    else if (mode == 3) col = signalLost(texCoord, t);
    else col = shattered(texCoord, c, r, t);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
