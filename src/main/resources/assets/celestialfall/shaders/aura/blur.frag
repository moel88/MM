#version 150
// One direction of the aura's glow: a gaussian (sigma 2.5 taps) spread by Step (red, green and alpha: the aura's, and the
// sky-blue glow's close light), and the sky-blue glow's wide light (blue) by StepB, which reaches further.
uniform sampler2D Src;
uniform vec2 Step;
uniform vec2 StepB;
in vec2 texCoord;
out vec4 fragColor;
const float W[7]=float[](0.1610,0.1486,0.1169,0.0784,0.0448,0.0218,0.0090);
void main(){
    vec4 c=texture(Src,texCoord);
    vec3 s=c.rga*W[0];float b=c.b*W[0];
    for(int i=1;i<7;i++){
        vec2 o=Step*float(i),ob=StepB*float(i);
        s+=(texture(Src,texCoord+o).rga+texture(Src,texCoord-o).rga)*W[i];
        b+=(texture(Src,texCoord+ob).b+texture(Src,texCoord-ob).b)*W[i];
    }
    fragColor=vec4(s.xy,b,s.z);
}
