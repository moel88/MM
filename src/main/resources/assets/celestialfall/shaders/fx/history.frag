#version 150
// Afterglow: the brightest of the light now and the light of the frames before, fading with game time
// (a maximum, not a sum: a paused game keeps the same picture instead of piling light up).
uniform sampler2D Prev;
uniform sampler2D Cur;
uniform float Decay;
in vec2 texCoord;
out vec4 fragColor;
void main(){fragColor=vec4(max(texture(Prev,texCoord).rgb*Decay,texture(Cur,texCoord).rgb),1.0);}
