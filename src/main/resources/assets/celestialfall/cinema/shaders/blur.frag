#version 150
uniform sampler2D Source;
uniform vec2 Direction;
uniform int Extract;
in vec2 uv;
out vec4 fragColor;
vec3 sampleGlow(vec2 p) {
    vec3 c=texture(Source,p).rgb;
    if(Extract==1)c*=smoothstep(.90,1.6,max(c.r,max(c.g,c.b)));
    return c;
}
void main(){
    vec3 c=sampleGlow(uv)*.227027;
    c+=(sampleGlow(uv+Direction*1.384615)+sampleGlow(uv-Direction*1.384615))*.316216;
    c+=(sampleGlow(uv+Direction*3.230769)+sampleGlow(uv-Direction*3.230769))*.070270;
    fragColor=vec4(c,1.);
}
