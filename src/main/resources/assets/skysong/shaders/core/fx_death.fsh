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
//  4 Cinematic: black bars slide in, the world drains to film grey, slowly zooms and fades out.
//  5 Swallowed: the world spins into a dark hole in the middle that grows until it takes it all.
//  6 Frozen: frost crystals grow in from the edges and the picture turns to ice.
//  7 Pixelated: the picture breaks into bigger and bigger pixels with fewer colors, then goes dark.
//  8 Ink Bleed: ink creeps in from the edges with glowing tips.  9 Thermal: heat camera vision.
// 10 Kaleidoscope: the view folds into a turning mirror pattern.  11 Burn Away: the picture burns like paper.
// 12 Drowned: underwater waves and bubbles.  13 Rewind: a sepia tape rewinding.  14 Ripple: rings ripple out.
// 15 Stained Glass: the world turns into glass panes.  16 Ghost Echo: echoes fade to white.  17 Starry Void: space eats the world.

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

// ── 4: Cinematic ───────────────────────────────────────────────────

vec3 cinematic(vec2 uv, float r, float t) {
    float k = smoothstep(0.0, 1.2, t);
    float zoom = 1.0 - 0.06 * smoothstep(0.0, 4.0, t);
    vec2 zuv = 0.5 + (uv - 0.5) * zoom;
    vec3 col = texture(Sampler0, zuv).rgb;
    vec3 film = vec3(luma(col)) * vec3(1.04, 1.0, 0.94);
    col = mix(col, film, k);
    col = (col - 0.5) * (1.0 + 0.25 * k) + 0.5;
    col += (hash12(gl_FragCoord.xy + floor(t * 24.0) * 17.0) - 0.5) * 0.08 * k;
    col *= 1.0 - smoothstep(0.35, 1.05, r) * 0.7 * k;
    float bar = 0.13 * smoothstep(0.0, 0.9, t);
    float edge = min(uv.y, 1.0 - uv.y);
    float inBar = 1.0 - smoothstep(bar - 0.002, bar + 0.002, edge);
    col = mix(col, vec3(0.0), inBar);
    col += tintAt(uv.x) * 0.35 * (1.0 - smoothstep(0.0, 0.006, abs(edge - bar))) * step(0.01, bar);
    col *= 1.0 - smoothstep(2.6, 4.2, t);
    return col;
}

// ── 5: Swallowed ───────────────────────────────────────────────────

vec3 swallowed(vec2 uv, vec2 c, float r, float t) {
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    float pull = smoothstep(0.0, 3.5, t);
    float angle = pull * 6.0 * exp(-r * 2.2);
    float cs = cos(angle);
    float sn = sin(angle);
    vec2 rc = vec2(c.x * cs - c.y * sn, c.x * sn + c.y * cs) * (1.0 - 0.35 * pull * exp(-r * 1.5));
    vec2 suv = rc / vec2(aspect, 1.0) + 0.5;
    vec3 col = texture(Sampler0, clamp(suv, 0.0, 1.0)).rgb;
    float hole = 1.2 * smoothstep(0.3, 4.0, t) * smoothstep(0.3, 4.0, t);
    float glow = 1.0 - smoothstep(0.0, 0.08, abs(r - hole));
    col = mix(vec3(0.0), col, smoothstep(hole - 0.02, hole + 0.06, r));
    col += tintAt(fract(atan(c.y, c.x) / 6.2832 + t * 0.2)) * glow * 0.9 * step(0.01, hole);
    col = mix(col, vec3(luma(col)), 0.3 * pull);
    return col;
}

// ── 6: Frozen ──────────────────────────────────────────────────────

vec3 frozen(vec2 uv, vec2 c, float r, float t) {
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float f1, f2;
    vec2 cell;
    voronoi(c * 9.0, f1, f2, cell);
    float crystal = hash22(cell).x;
    float reach = 0.5 * smoothstep(0.0, 2.5, t) + 0.1 * crystal;
    float frost = 1.0 - smoothstep(reach - 0.08, reach + 0.02, edge);
    vec2 bend = (hash22(cell + 3.1) - 0.5) * 0.012 * frost;
    vec3 col = texture(Sampler0, clamp(uv + bend, 0.0, 1.0)).rgb;
    vec3 ice = mix(vec3(0.78, 0.9, 1.0), tintAt(crystal), 0.35);
    col = mix(col, col * vec3(0.75, 0.88, 1.05), smoothstep(0.0, 1.5, t));
    col = mix(col, ice * (0.75 + 0.35 * crystal), frost * 0.75);
    float crack = (1.0 - smoothstep(0.0, 0.05, f2 - f1)) * frost;
    col += vec3(0.9, 0.97, 1.0) * crack * 0.6;
    col = mix(col, vec3(luma(col)) * vec3(0.85, 0.95, 1.1), 0.4 * smoothstep(0.5, 3.0, t));
    col *= 1.0 - 0.3 * smoothstep(2.5, 4.5, t);
    return col;
}

// ── 7: Pixelated ───────────────────────────────────────────────────

vec3 pixelated(vec2 uv, float t) {
    float size = mix(1.0, 72.0, smoothstep(0.0, 2.8, t) * smoothstep(0.0, 2.8, t));
    vec2 grid = OutSize / size;
    vec2 puv = (floor(uv * grid) + 0.5) / grid;
    vec3 col = texture(Sampler0, puv).rgb;
    float levels = mix(32.0, 3.0, smoothstep(0.3, 2.6, t));
    col = floor(col * levels + 0.5) / levels;
    vec2 local = fract(uv * grid);
    float gap = step(0.92, max(local.x, local.y)) * step(6.0, size);
    col = mix(col, tintAt(hash12(floor(uv * grid))) * 0.4, gap * 0.6);
    col *= 1.0 - smoothstep(2.8, 4.2, t);
    return col;
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + 11.7;
        a *= 0.5;
    }
    return v;
}

vec3 inkBleed(vec2 uv, vec2 c, float r, float t) {
    vec3 col = texture(Sampler0, uv).rgb;
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float n = fbm(c * 5.0 + vec2(0.0, t * 0.3));
    float reach = 0.6 * smoothstep(0.0, 3.2, t);
    float ink = smoothstep(reach + 0.04, reach - 0.04, edge + n * 0.22);
    float rim = smoothstep(0.06, 0.0, abs(edge + n * 0.22 - reach));
    col = mix(col, vec3(luma(col)) * 0.8, smoothstep(0.0, 2.0, t) * 0.6);
    col = mix(col, vec3(0.02), ink);
    col += tintAt(n) * rim * 0.9 * step(0.01, reach);
    return col;
}

vec3 thermal(vec2 uv, float t) {
    vec3 src = texture(Sampler0, uv).rgb;
    float heat = clamp(luma(src) * 1.3 - 0.15 * smoothstep(0.5, 3.5, t), 0.0, 1.0);
    vec3 pal = mix(vec3(0.02, 0.0, 0.12), vec3(0.45, 0.0, 0.6), smoothstep(0.0, 0.3, heat));
    pal = mix(pal, vec3(1.0, 0.25, 0.0), smoothstep(0.3, 0.65, heat));
    pal = mix(pal, vec3(1.0, 0.95, 0.5), smoothstep(0.65, 1.0, heat));
    vec3 col = mix(src, pal, smoothstep(0.0, 0.6, t));
    float scan = 1.0 - smoothstep(0.0, 0.015, abs(uv.y - fract(t * 0.45)));
    col += tintAt(uv.x) * scan * 0.5;
    col *= 0.9 + 0.1 * sin(gl_FragCoord.y * 1.5708);
    col *= 1.0 - smoothstep(2.8, 4.4, t);
    return col;
}

vec3 kaleidoscope(vec2 uv, vec2 c, float r, float t) {
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    float k = smoothstep(0.0, 1.5, t);
    float seg = 6.2832 / 8.0;
    float a = atan(c.y, c.x) + t * 0.35 * k;
    a = mod(a, seg);
    a = abs(a - seg * 0.5);
    vec2 mirrored = vec2(cos(a), sin(a)) * r * (1.0 - 0.25 * k);
    vec2 kuv = mix(uv, mirrored / vec2(aspect, 1.0) + 0.5, k);
    vec3 col = texture(Sampler0, clamp(kuv, 0.0, 1.0)).rgb;
    col = mix(col, col * tintAt(fract(a / seg + r)) * 1.6, 0.35 * k);
    col *= 1.0 - smoothstep(0.4, 1.0, r) * 0.6 * k;
    col *= 1.0 - smoothstep(3.0, 4.5, t);
    return col;
}

vec3 burnAway(vec2 uv, vec2 c, float t) {
    vec3 col = texture(Sampler0, uv).rgb;
    float n = fbm(c * 4.0);
    float level = smoothstep(0.2, 3.4, t) * 1.15;
    float burnt = step(n, level - 0.05);
    float edge = smoothstep(0.08, 0.0, abs(n - level));
    vec3 ember = mix(vec3(1.0, 0.45, 0.05), tintAt(n), 0.4);
    col = mix(col, col * vec3(1.0, 0.8, 0.6), smoothstep(0.0, 1.0, t) * 0.5);
    col = mix(col, vec3(0.04, 0.03, 0.03) + ember * 0.05 * vnoise(c * 40.0 + t), burnt);
    col += ember * edge * 1.6;
    return col;
}

vec3 drowned(vec2 uv, vec2 c, float t) {
    float k = smoothstep(0.0, 2.0, t);
    vec2 wave = vec2(sin(uv.y * 18.0 + t * 3.0), cos(uv.x * 14.0 + t * 2.4)) * 0.006 * k;
    vec3 col = texture(Sampler0, clamp(uv + wave, 0.0, 1.0)).rgb;
    col = mix(col, col * vec3(0.3, 0.6, 0.9) + vec3(0.0, 0.05, 0.12), k);
    vec2 p = uv * vec2(OutSize.x / max(OutSize.y, 1.0), 1.0) * 14.0 + vec2(0.0, t * 1.4);
    vec2 cell = floor(p);
    float h = hash12(cell);
    if (h > 0.82) {
        vec2 center = cell + 0.3 + 0.4 * hash22(cell + 1.7);
        float d = length(p - center);
        float size = 0.12 + 0.12 * h;
        col += (vec3(0.7, 0.9, 1.0) + tintAt(h) * 0.3) * (smoothstep(size, size - 0.03, d) - smoothstep(size - 0.03, size - 0.08, d)) * 0.7 * k;
    }
    col *= 1.0 - smoothstep(1.5, 4.5, t) * (0.4 + 0.6 * smoothstep(0.2, 0.8, length(c)));
    return col;
}

vec3 rewind(vec2 uv, float t) {
    float k = smoothstep(0.0, 1.0, t);
    float band = floor(uv.y * 9.0 - t * 6.0);
    float off = (hash12(vec2(band, floor(t * 10.0))) - 0.5) * 0.05 * k;
    vec2 ruv = vec2(fract(uv.x + off), fract(uv.y + t * 0.6 * k));
    vec3 col = texture(Sampler0, ruv).rgb;
    vec3 sepia = vec3(luma(col)) * vec3(1.1, 0.95, 0.72);
    col = mix(col, sepia, k * 0.85);
    float track = 1.0 - smoothstep(0.0, 0.02, abs(fract(uv.y * 3.0 - t * 2.0) - 0.5));
    col = mix(col, vec3(1.0) * (0.6 + 0.4 * hash12(gl_FragCoord.xy + t)), track * 0.5 * k);
    col += tintAt(uv.y) * track * 0.25 * k;
    col *= 1.0 - smoothstep(3.0, 4.4, t);
    return col;
}

vec3 rippleOut(vec2 uv, vec2 c, float r, float t) {
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    float k = smoothstep(0.0, 0.8, t);
    float wave = sin(r * 34.0 - t * 9.0) * exp(-r * 1.6) * 0.02 * k;
    vec2 dir = r > 0.0001 ? c / r : vec2(0.0);
    vec2 wuv = uv + dir / vec2(aspect, 1.0) * wave;
    vec3 col = texture(Sampler0, clamp(wuv, 0.0, 1.0)).rgb;
    col = mix(col, vec3(luma(col)), smoothstep(0.5, 2.5, t) * 0.8);
    float ring = 1.0 - smoothstep(0.0, 0.025, abs(fract(r * 3.0 - t * 0.9) - 0.5) - 0.47);
    col += tintAt(r) * ring * 0.4 * k;
    col *= 1.0 - smoothstep(2.8, 4.4, t);
    return col;
}

vec3 stainedGlass(vec2 uv, vec2 c, float t) {
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    float k = smoothstep(0.0, 1.6, t);
    float f1, f2;
    vec2 cell;
    voronoi(c * mix(40.0, 9.0, k), f1, f2, cell);
    vec2 center = (cell + hash22(cell)) / mix(40.0, 9.0, k);
    vec3 pane = texture(Sampler0, clamp(center / vec2(aspect, 1.0) + 0.5, 0.0, 1.0)).rgb;
    vec3 col = texture(Sampler0, uv).rgb;
    vec3 glass = pane * (0.8 + 0.4 * hash22(cell).x) + tintAt(hash22(cell).y) * 0.2;
    col = mix(col, glass, k);
    float lead = 1.0 - smoothstep(0.0, 0.06, f2 - f1);
    col = mix(col, vec3(0.03), lead * k);
    col *= 1.0 - smoothstep(3.0, 4.5, t);
    return col;
}

vec3 ghostEcho(vec2 uv, float t) {
    float k = smoothstep(0.0, 1.0, t);
    vec3 col = texture(Sampler0, uv).rgb;
    vec3 acc = vec3(0.0);
    for (int i = 1; i <= 5; i++) {
        float f = float(i) / 5.0;
        vec2 e = 0.5 + (uv - 0.5) * (1.0 - 0.05 * f * k) + vec2(sin(t * 2.0 + f * 3.0), cos(t * 1.6 + f * 2.0)) * 0.01 * f * k;
        acc += texture(Sampler0, clamp(e, 0.0, 1.0)).rgb * (1.0 - f * 0.6);
    }
    acc /= 3.2;
    col = mix(col, acc, 0.75 * k);
    col = mix(col, vec3(luma(col)) * (0.8 + 0.4 * tintAt(uv.y)), 0.6 * k);
    col = mix(col, vec3(0.92, 0.95, 1.0), smoothstep(2.2, 4.4, t) * 0.85);
    return col;
}

vec3 starryVoid(vec2 uv, vec2 c, float r, float t) {
    vec3 col = texture(Sampler0, uv).rgb;
    float n = fbm(c * 3.0 + vec2(t * 0.05, 0.0));
    vec3 space = vec3(0.01, 0.0, 0.03) + tintAt(n) * pow(n, 2.5) * 0.6;
    vec2 p = c * 60.0;
    vec2 cell = floor(p);
    float h = hash12(cell);
    float twinkle = 0.5 + 0.5 * sin(t * 4.0 + h * 40.0);
    float star = step(0.96, h) * (1.0 - smoothstep(0.0, 0.18, length(fract(p) - 0.5))) * twinkle;
    space += vec3(1.0) * star;
    float reach = 1.4 * smoothstep(0.0, 3.0, t);
    float edge = vnoise(c * 6.0) * 0.25;
    float gone = smoothstep(reach - 0.05, reach + 0.05, -r + reach * 2.0 - edge);
    col = mix(col, vec3(luma(col)) * vec3(0.7, 0.75, 1.0), smoothstep(0.0, 1.0, t) * 0.5);
    col = mix(col, space, 1.0 - gone);
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
    else if (mode == 4) col = cinematic(texCoord, r, t);
    else if (mode == 5) col = swallowed(texCoord, c, r, t);
    else if (mode == 6) col = frozen(texCoord, c, r, t);
    else if (mode == 7) col = pixelated(texCoord, t);
    else if (mode == 8) col = inkBleed(texCoord, c, r, t);
    else if (mode == 9) col = thermal(texCoord, t);
    else if (mode == 10) col = kaleidoscope(texCoord, c, r, t);
    else if (mode == 11) col = burnAway(texCoord, c, t);
    else if (mode == 12) col = drowned(texCoord, c, t);
    else if (mode == 13) col = rewind(texCoord, t);
    else if (mode == 14) col = rippleOut(texCoord, c, r, t);
    else if (mode == 15) col = stainedGlass(texCoord, c, t);
    else if (mode == 16) col = ghostEcho(texCoord, t);
    else if (mode == 17) col = starryVoid(texCoord, c, r, t);
    else col = shattered(texCoord, c, r, t);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
