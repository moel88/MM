#version 150
// Bloom: 3x3 tent upsample of the smaller level added to this level, so every level spreads its light wider.
uniform sampler2D Small;
uniform sampler2D Current;
uniform vec2 Texel;
uniform float Spread;
in vec2 texCoord;
out vec4 fragColor;
vec3 s(vec2 o){return texture(Small,texCoord+o*Texel*Spread).rgb;}
void main(){
    vec3 t=(s(vec2(-1,1))+2.0*s(vec2(0,1))+s(vec2(1,1))+2.0*s(vec2(-1,0))+4.0*s(vec2(0))+2.0*s(vec2(1,0))+s(vec2(-1,-1))+2.0*s(vec2(0,-1))+s(vec2(1,-1)))/16.0;
    fragColor=vec4(texture(Current,texCoord).rgb+t,1.0);
}
