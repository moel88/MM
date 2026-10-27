uniform sampler2D DiffuseSampler0;
in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// A deliberately visible authoring preview, never enabled by ordinary skill casts.
void main() {
    vec4 scene = texture(DiffuseSampler0, texCoord);
    float luma = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 grade = vec3(luma * 0.95 + 0.05, luma * 0.4, luma * 1.1 + 0.04);
    vec2 uv = texCoord * 2.0 - 1.0;
    float vignette = 1.0 - 0.2 * smoothstep(0.35, 1.7, dot(uv, uv));
    fragColor = vec4(mix(scene.rgb, grade, 0.85) * vignette, scene.a);
}
