#version 150
// Bloom: 13-tap downsample. The first level takes only the light the effects added (effect layer minus the
// scene under it), so the sky, white blocks and lit ground never bloom.
uniform sampler2D SrcA;
uniform sampler2D SrcB;
uniform vec2 Texel;
uniform int First;
in vec2 texCoord;
out vec4 fragColor;
vec3 s(vec2 o){
    vec2 uv=texCoord+o*Texel;
    vec3 c=texture(SrcA,uv).rgb;
    if(First==1)c=max(c-texture(SrcB,uv).rgb,0.0);
    return min(c,vec3(64.0));
}
void main(){
    vec3 a=s(vec2(-2,2)),b=s(vec2(0,2)),c=s(vec2(2,2)),d=s(vec2(-2,0)),e=s(vec2(0)),f=s(vec2(2,0)),g=s(vec2(-2,-2)),h=s(vec2(0,-2)),i=s(vec2(2,-2));
    vec3 j=s(vec2(-1,1)),k=s(vec2(1,1)),l=s(vec2(-1,-1)),m=s(vec2(1,-1));
    vec3 r=e*.125+(a+c+g+i)*.03125+(b+d+f+h)*.0625+(j+k+l+m)*.125;
    fragColor=vec4(r,1.0);
}
