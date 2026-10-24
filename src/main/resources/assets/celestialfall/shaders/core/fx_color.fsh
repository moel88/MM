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
float scene(ivec2 p){return dist(texelFetch(Sampler1,clamp(p,ivec2(0),ivec2(ScreenSize)-1),0).r);}
void main(){
    vec4 c=vertexColor*ColorModulator;
    if(Soft>0.0){
        ivec2 p=ivec2(gl_FragCoord.xy);
        float self=dist(gl_FragCoord.z),behind=scene(p);
        // Only a surface that runs into the world fades. One lying along the world just behind it (a ring or glow
        // on the floor) changes depth across the screen as that world does, and keeps all of its light.
        vec2 world=vec2(scene(p+ivec2(1,0))-behind,scene(p+ivec2(0,1))-behind),own=vec2(dFdx(self),dFdy(self));
        float along=1.0-smoothstep(.2,.45,length(own-world)/max(max(length(own),length(world)),1e-5));
        c.a*=clamp((behind-self)/(Soft*mix(1.0,.1,along)),0.0,1.0);
    }
    if(c.a<.002)discard;
    fragColor=c;
}
