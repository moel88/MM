#version 150
// The scene depth as a plain float texture: the effects test against the real depth buffer and read this copy.
uniform sampler2D DepthTex;
in vec2 texCoord;
out vec4 fragColor;
void main(){fragColor=vec4(texture(DepthTex,texCoord).r,0.0,0.0,1.0);}
