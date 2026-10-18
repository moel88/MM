#version 150
uniform sampler2D Source;
uniform sampler2D Bloom;
uniform sampler2D Depth;
uniform vec2 Resolution;
uniform float Focus;
in vec2 uv;
out vec4 fragColor;
float linearDepth(float d){return (.05*90.)/(90.-d*(90.-.05));}
vec3 tonemap(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
void main(){
    float z=linearDepth(texture(Depth,uv).r);
    float radius=clamp((z-Focus-1.5)*.47,0.,3.7);
    vec2 pixel=1./Resolution;
    vec3 c=texture(Source,uv).rgb;float weight=1.;
    for(int i=0;i<8;i++) {
        float a=float(i)*.785398;vec2 p=uv+vec2(cos(a),sin(a))*pixel*radius;
        float zz=linearDepth(texture(Depth,p).r);
        float w=step(z-.7,zz);
        c+=texture(Source,p).rgb*w;weight+=w;
    }
    c/=weight;
    vec3 n=texture(Source,uv+vec2(0,pixel.y)).rgb;
    vec3 s=texture(Source,uv-vec2(0,pixel.y)).rgb;
    vec3 e=texture(Source,uv+vec2(pixel.x,0)).rgb;
    vec3 w=texture(Source,uv-vec2(pixel.x,0)).rgb;
    vec3 luma=vec3(.299,.587,.114);
    float lo=min(min(dot(n,luma),dot(s,luma)),min(dot(e,luma),dot(w,luma)));
    float hi=max(max(dot(n,luma),dot(s,luma)),max(dot(e,luma),dot(w,luma)));
    float edge=smoothstep(.10,.50,hi-lo);
    c=mix(c,(c*4.+n+s+e+w)/8.,edge*.55);
    c+=texture(Bloom,uv).rgb*.115;
    float vignette=1.-.10*smoothstep(.15,.80,distance(uv,vec2(.53,.51)));
    c=tonemap(c*1.02)*vignette;
    fragColor=vec4(pow(c,vec3(1./2.2)),1.);
}
