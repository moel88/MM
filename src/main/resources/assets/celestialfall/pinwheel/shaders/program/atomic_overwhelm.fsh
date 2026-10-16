// G seen from close outside its damage column: the view is dragged toward the pillar (radial blur),
// light splits at the edges, lavender light floods out from the pillar and the edges of the frame
// darken, pulsing with every surge. Driven by AtomicOverwhelm from the cast clock; Veil prepends the
// GLSL version.
uniform sampler2D DiffuseSampler0;
uniform float AtomicStrength;
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
    if (AtomicStrength <= 0.001 || texCoord.y < AtomicBar || texCoord.y > 1.0 - AtomicBar) {
        fragColor = base;
        return;
    }
    float k = AtomicStrength, pulse = AtomicSurge;
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    // Toward the pillar when it is in view, toward the middle of the screen otherwise.
    vec2 center = mix(vec2(0.5), AtomicCenter, AtomicFacing);
    vec2 d = texCoord - center;
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
    vec3 col = acc / wsum;
    // Split light at the edges.
    float split = k * (0.0015 + 0.0045 * pulse) * length(d * vec2(aspect, 1.0));
    vec2 dir = normalize(d + vec2(1e-5));
    col.r = mix(col.r, scene(texCoord + dir * split).r, 0.7);
    col.b = mix(col.b, scene(texCoord - dir * split).b, 0.7);
    // Lavender light flooding out from the pillar, a flash on each surge.
    float x = (texCoord.x - center.x) * aspect;
    float flood = AtomicFacing * exp(-pow(abs(x) / max(AtomicWidth * aspect * 3.0, 0.05), 2.0));
    col += vec3(0.62, 0.5, 1.0) * k * (0.015 + 0.035 * pulse) * (0.2 + 0.8 * flood);
    // The edges of the frame close in.
    vec2 v = (texCoord - 0.5) * vec2(aspect, 1.0);
    float vignette = smoothstep(0.35, 1.15, length(v));
    col *= 1.0 - vignette * k * (0.28 + 0.14 * pulse);
    fragColor = vec4(col, base.a);
}
