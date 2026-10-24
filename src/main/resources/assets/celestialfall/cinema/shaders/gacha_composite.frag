#version 150
// The gacha's pull scenes (2.39.0): the title cinema's composite with a stronger bloom and an exposure for the dark place.
uniform sampler2D Source;
uniform sampler2D Bloom;
uniform sampler2D Depth;
uniform vec2 Resolution;
uniform float Focus;
uniform float BloomStrength;
uniform float Exposure;
in vec2 uv;
out vec4 fragColor;
float linearDepth(float d){return (.05*90.)/(90.-d*(90.-.05));}
vec3 tonemap(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
void main(){
    float z=linearDepth(texture(Depth,uv).r);
    float radius=clamp((abs(z-Focus)-.6)*.9,0.,3.2);
    vec2 pixel=1./Resolution;
    vec3 c=texture(Source,uv).rgb;float weight=1.;
    for(int i=0;i<8;i++) {
        float a=float(i)*.785398;vec2 p=uv+vec2(cos(a),sin(a))*pixel*radius;
        float zz=linearDepth(texture(Depth,p).r);
        float w=step(z-.7,zz);
        c+=texture(Source,p).rgb*w;weight+=w;
    }
    c/=weight;
    c+=texture(Bloom,uv).rgb*BloomStrength;
    float vignette=1.-.32*smoothstep(.2,.85,distance(uv,vec2(.5,.5)));
    c=tonemap(c*Exposure)*vignette;
    fragColor=vec4(pow(c,vec3(1./2.2)),1.);
}
