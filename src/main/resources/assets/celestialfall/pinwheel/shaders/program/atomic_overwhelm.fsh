// G's screen pass, last stage of the pipeline. For every viewer outside the damage column who sees the
// pillar, its light pours into the view: a wide lavender glow, a horizontal sheen and light shafts
// (from the earlier stages). Close to the pillar the view is also dragged toward it (radial blur),
// light splits at the edges, lavender light floods out and the edges of the frame darken. Both pulse
// with every surge. Driven by AtomicOverwhelm from the cast clock; Veil prepends the GLSL version.
uniform sampler2D DiffuseSampler0;
uniform sampler2D GlowSampler;
uniform sampler2D RaysSampler;
uniform float AtomicStrength;
uniform float AtomicRadiance;
uniform float AtomicSurge;
uniform vec2 AtomicCenter;
uniform float AtomicFacing;
uniform float AtomicWidth;
uniform float AtomicBar;
uniform vec2 OutSize;
in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// The frame between the letterbox bars, so nothing smears the bars into the picture.
vec3 scene(vec2 uv) {
    uv.y = clamp(uv.y, AtomicBar + 0.001, 1.0 - AtomicBar - 0.001);
    return texture(DiffuseSampler0, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    float k = AtomicStrength, shine = AtomicRadiance, pulse = AtomicSurge;
    if ((k <= 0.001 && shine <= 0.001) || texCoord.y < AtomicBar || texCoord.y > 1.0 - AtomicBar) {
        fragColor = base;
        return;
    }
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    // Toward the pillar when it is in view, toward the middle of the screen otherwise.
    vec2 center = mix(vec2(0.5), AtomicCenter, AtomicFacing);
    vec2 d = texCoord - center;
    vec3 col = base.rgb;
    if (k > 0.001) {
        // Radial blur: eight taps pulled toward the centre, harder on each surge.
        float pull = k * (0.014 + 0.026 * pulse);
        vec3 acc = vec3(0.0);
        float wsum = 0.0;
        for (int i = 0; i < 8; i++) {
            float t = float(i) / 7.0;
            float w = 1.0 - 0.6 * t;
            acc += scene(texCoord - d * pull * t) * w;
            wsum += w;
        }
        col = acc / wsum;
        // Split light at the edges.
        float split = k * (0.0015 + 0.0045 * pulse) * length(d * vec2(aspect, 1.0));
        vec2 dir = normalize(d + vec2(1e-5));
        col.r = mix(col.r, scene(texCoord + dir * split).r, 0.7);
        col.b = mix(col.b, scene(texCoord - dir * split).b, 0.7);
    }
    // The pillar's light: a lavender glow, a wider halo, a horizontal sheen across it and the light shafts.
    vec3 glow = texture(GlowSampler, texCoord).rgb;
    vec3 sheen = (texture(GlowSampler, texCoord + vec2(0.035, 0.0)).rgb + texture(GlowSampler, texCoord - vec2(0.035, 0.0)).rgb) * 0.3
        + (texture(GlowSampler, texCoord + vec2(0.09, 0.0)).rgb + texture(GlowSampler, texCoord - vec2(0.09, 0.0)).rgb) * 0.18
        + (texture(GlowSampler, texCoord + vec2(0.18, 0.0)).rgb + texture(GlowSampler, texCoord - vec2(0.18, 0.0)).rgb) * 0.08;
    // A wider, fainter halo from rings of taps around the pixel.
    vec3 wide = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.7853982 + 0.39;
        vec2 o = vec2(cos(a) / aspect, sin(a));
        wide += texture(GlowSampler, texCoord + o * 0.07).rgb * 0.08 + texture(GlowSampler, texCoord + o * 0.15).rgb * 0.045;
    }
    vec3 rays = texture(RaysSampler, texCoord).rgb;
    // Up close the pillar fills the frame and its light would haze over everything: the bigger it is on screen, the
    // less is added.
    float lift = shine * (0.85 + 0.35 * pulse) / (1.0 + 2.5 * AtomicWidth);
    // It spills into the dark around the pillar; where the frame is already bright (the pillar itself) it adds
    // little, and it is screen-blended so it never clips to a flat white.
    float room = 1.0 - 0.9 * smoothstep(0.3, 0.8, dot(col, vec3(0.2126, 0.7152, 0.0722)));
    vec3 light = (glow * vec3(0.7, 0.58, 1.0) * 1.0 + wide * vec3(0.62, 0.5, 1.0) * 0.9 + sheen * vec3(0.66, 0.54, 1.0) * 0.45
        + rays * vec3(0.95, 0.88, 1.0) * 1.5) * lift * room;
    col = 1.0 - (1.0 - col) * (1.0 - clamp(light, 0.0, 1.0));
    if (k > 0.001) {
        // Lavender light flooding out from the pillar, a flash on each surge.
        float x = (texCoord.x - center.x) * aspect;
        float flood = AtomicFacing * exp(-pow(abs(x) / max(AtomicWidth * aspect * 3.0, 0.05), 2.0));
        col += vec3(0.62, 0.5, 1.0) * k * (0.015 + 0.035 * pulse) * (0.2 + 0.8 * flood);
        // The edges of the frame close in.
        vec2 v = (texCoord - 0.5) * vec2(aspect, 1.0);
        float vignette = smoothstep(0.35, 1.15, length(v));
        col *= 1.0 - vignette * k * (0.28 + 0.14 * pulse);
    }
    fragColor = vec4(col, base.a);
}
