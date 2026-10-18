// Wide glow, vertical half: the same 15-tap Gaussian down the eighth-resolution horizontal pass.
// Veil prepends the GLSL version.
uniform sampler2D DiffuseSampler0;
uniform vec2 InSize;
in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    const float W[8] = float[](0.1346, 0.1273, 0.1078, 0.0817, 0.0553, 0.0335, 0.0182, 0.0089);
    vec2 step = vec2(0.0, 2.0 / InSize.y);
    vec3 sum = texture(DiffuseSampler0, texCoord).rgb * W[0];
    for (int i = 1; i < 8; i++) {
        sum += (texture(DiffuseSampler0, texCoord + step * float(i)).rgb + texture(DiffuseSampler0, texCoord - step * float(i)).rgb) * W[i];
    }
    fragColor = vec4(sum, 1.0);
}
