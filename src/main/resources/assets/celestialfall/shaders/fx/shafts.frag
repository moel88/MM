#version 150
// Light shafts: the effect light smeared along rays toward a bright source on the screen.
uniform sampler2D Src;
uniform vec2 Center;
uniform float Length;
uniform float Decay;
uniform vec2 Aspect;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec2 step=(Center-texCoord)*Length/40.0;
    vec3 sum=vec3(0.0);float w=1.0,total=0.0;vec2 uv=texCoord;
    for(int i=0;i<40;i++){
        sum+=texture(Src,uv).rgb*w;total+=w;w*=Decay;uv+=step;
    }
    // Fade the rays out away from the source so the whole screen does not glow.
    float d=length((texCoord-Center)*Aspect);
    fragColor=vec4(sum/total*exp(-d*1.6),1.0);
}
