// Light shafts: each pixel gathers the cut-out light on its way toward the light centre (the middle of
// the pillar's visible axis), fading step by step, so beams stream outward from the pillar and whatever
// stands in front of it casts shadows into them. Quarter resolution. Veil prepends the GLSL version.
uniform sampler2D DiffuseSampler0;
uniform vec2 AtomicLight;
uniform float AtomicLightOn;
in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    const int N = 48;
    vec2 delta = (texCoord - AtomicLight) * (0.92 / float(N));
    vec2 uv = texCoord;
    float decay = 1.0;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < N; i++) {
        uv -= delta;
        sum += texture(DiffuseSampler0, uv).rgb * decay;
        decay *= 0.965;
    }
    fragColor = vec4(sum * (AtomicLightOn / float(N)), 1.0);
}
