#version 150
uniform sampler2D Atlas;
in vec2 uv;
in vec4 tint;
void main() { if(texture(Atlas,uv).a*tint.a<.004)discard; }
