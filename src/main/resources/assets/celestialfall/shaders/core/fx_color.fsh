#version 150
// Effect geometry on the HDR effect layer. ColorModulator may run above 1 (light hotter than white);
// Soft fades a surface out where it meets the world (blocks of depth), so flat effect planes never show a
// hard line where they cut into the ground or a body. Sampler1 holds the scene depth.
uniform sampler2D Sampler1;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
uniform float Soft;
in vec4 vertexColor;
out vec4 fragColor;
float dist(float d){return ProjMat[3][2]/(d*2.0-1.0+ProjMat[2][2]);}
void main(){
    vec4 c=vertexColor*ColorModulator;
    if(Soft>0.0){
        float scene=texture(Sampler1,gl_FragCoord.xy/ScreenSize).r;
        c.a*=clamp((dist(scene)-dist(gl_FragCoord.z))/Soft,0.0,1.0);
    }
    if(c.a<.002)discard;
    fragColor=c;
}
