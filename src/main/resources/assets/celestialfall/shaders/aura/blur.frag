#version 150
// One direction of the aura's glow: a gaussian (sigma 2.5 taps) spread by Step.
uniform sampler2D Src;
uniform vec2 Step;
in vec2 texCoord;
out vec4 fragColor;
const float W[7]=float[](0.1610,0.1486,0.1169,0.0784,0.0448,0.0218,0.0090);
void main(){
    vec2 s=texture(Src,texCoord).rg*W[0];
    for(int i=1;i<7;i++){
        vec2 o=Step*float(i);
        s+=(texture(Src,texCoord+o).rg+texture(Src,texCoord-o).rg)*W[i];
    }
    fragColor=vec4(s,0.0,1.0);
}
