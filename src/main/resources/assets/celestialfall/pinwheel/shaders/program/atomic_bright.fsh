// G's light cut out for the glow and the light shafts: the bright pixels of the pillar (near its axis
// on screen), at quarter resolution; four bilinear taps cover the 4x4 pixels behind each output pixel.
// Veil prepends the GLSL version.
uniform sampler2D DiffuseSampler0;
uniform vec2 InSize;
uniform vec2 AtomicAxisA;
uniform vec2 AtomicAxisB;
uniform float AtomicWidth;
uniform float AtomicLightOn;
in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 px = 1.0 / InSize;
    vec3 c = 0.25 * (texture(DiffuseSampler0, texCoord + px * vec2(-1.0, -1.0)).rgb
        + texture(DiffuseSampler0, texCoord + px * vec2(1.0, -1.0)).rgb
        + texture(DiffuseSampler0, texCoord + px * vec2(-1.0, 1.0)).rgb
        + texture(DiffuseSampler0, texCoord + px * vec2(1.0, 1.0)).rgb);
    // Distance to the pillar's axis on screen (in screen heights), so only the pillar feeds the light.
    float aspect = InSize.x / max(InSize.y, 1.0);
    vec2 a = AtomicAxisA * vec2(aspect, 1.0), b = AtomicAxisB * vec2(aspect, 1.0), p = texCoord * vec2(aspect, 1.0);
    vec2 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-8), 0.0, 1.0);
    float w = AtomicWidth * aspect;
    float onPillar = 1.0 - smoothstep(w * 1.15, w * 1.15 + 0.04, length(p - (a + ab * t)));
    float peak = max(c.r, max(c.g, c.b));
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float light = smoothstep(0.55, 0.95, peak) * smoothstep(0.35, 0.8, luma);
    fragColor = vec4(c * light * onPillar * AtomicLightOn, 1.0);
}
