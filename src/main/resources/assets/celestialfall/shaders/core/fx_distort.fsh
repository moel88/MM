#version 150
// Refraction written by the effects: a screen offset (in texture units) across a band, pushing the world
// behind it out on the band's outer half and in on its inner half, like air bent by a cut or a shock front.
// uv.y runs across the band (0 inner, 1 outer); tint.a is the strength.
uniform vec2 ScreenSize;
uniform float Amount;
in vec2 uv;
in vec4 tint;
out vec4 fragColor;
void main(){
    vec2 g=vec2(dFdx(uv.y),dFdy(uv.y));float gl=length(g);
    if(gl<1e-7||tint.a<.002)discard;
    float v=uv.y*2.0-1.0;
    float prof=sin(v*3.14159265)*(1.0-v*v)*1.6;
    vec2 px=g/gl*prof*tint.a*Amount;
    fragColor=vec4(px/ScreenSize,0.0,1.0);
}
