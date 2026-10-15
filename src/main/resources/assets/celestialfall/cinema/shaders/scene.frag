#version 150
uniform sampler2D Atlas;
uniform sampler2DShadow ShadowMap;
uniform vec3 Camera;
uniform float Time;
in vec3 world;
in vec3 normal;
in vec2 uv;
in vec4 tint;
in vec4 shadowCoord;
in float material;
in float rigged;
out vec4 fragColor;
const vec3 SUN=normalize(vec3(-.40,.83,.39));
float shadowVisibility(vec3 n) {
    vec3 q=shadowCoord.xyz/shadowCoord.w*.5+.5;
    if(any(lessThan(q,vec3(0))) || any(greaterThan(q,vec3(1))))return 1.;
    float bias=max(.00028,.00115*(1.-max(dot(n,SUN),0.)));
    float result=0.;
    for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)result+=texture(ShadowMap,vec3(q.xy+vec2(x,y)*1.6/2048.,q.z-bias));
    return result/9.;
}
void main() {
    vec4 tex=texture(Atlas,uv)*tint;
    if(tex.a<.004)discard;
    vec3 n=normalize(normal),v=normalize(Camera-world);
    if(dot(n,v)<0.)n=-n;
    vec3 base=pow(max(tex.rgb,vec3(0)),vec3(2.2));
    bool metal=material>1.5 && material<2.5;
    bool glass=material>2.5 && material<3.5;
    bool crystal=material>3.5 && material<4.5;
    float nl=max(dot(n,SUN),0.),vis=shadowVisibility(n);
    vec3 hemi=mix(vec3(.11,.16,.27),vec3(.35,.48,.72),n.y*.5+.5);
    vec3 fill=vec3(.16,.23,.34)*max(dot(n,normalize(vec3(.7,.2,-.5))),0.);
    vec3 light=base*(hemi+fill+vec3(1.90,1.73,1.56)*nl*vis);
    light+=base*vec3(.45,.32,.27)*pow(max(dot(n,v),0.),.6)*rigged;
    float fres=pow(1.-max(dot(n,v),0.),4.);
    float spec=pow(max(dot(n,normalize(SUN+v)),0.),metal?82.:glass?150.:40.);
    light+=vec3(1.,.92,.82)*spec*vis*(metal?2.8:glass?3.:.065);
    light+=vec3(.38,.67,1.)*fres*(metal?.38:glass?.95:.065);
    // Native player skin stays matte and readable, with no glossy facial treatment.
    if(rigged>.5 && material<.5)light=base*(vec3(.42,.47,.55)+vec3(.72,.70,.68)*nl*vis);
    if(metal)light=mix(light,base*(.38+1.45*nl*vis)+vec3(.68,.81,1.)*fres*.65,.45);
    float alpha=tex.a;
    if(glass) {
        vec3 reflected=reflect(-v,n);
        float panel=pow(max(dot(reflected,normalize(vec3(-.4,.7,.9))),0.),24.);
        light=vec3(.18,.42,.72)*.32+vec3(.88,.95,1.)*(fres*.8+panel*1.3)+spec*2.;
        alpha=clamp(.025+fres*.30+panel*.25+spec*.20,.025,.62);
    }
    if(crystal) {
        float flow=.8+.2*sin(world.y*110.-Time*5.);
        light+=base*.52+vec3(.1,.32,.52)*flow*.12;
    }
    if(material>.5&&material<1.5)light=base*2.1;
    if(material>5.5 && material<6.5)light=base*1.1;
    if(material>6.5) {
        vec3 cell=floor(world*1900.);
        float grain=fract(sin(dot(cell,vec3(12.9898,78.233,45.164)))*43758.5453);
        light=light*(.79+grain*.30)+base*.24;
        light+=vec3(.5,.85,1.)*smoothstep(.992,1.,grain)*.5;
    }
    // Fine contact darkening complements actual directional shadows.
    light*=1.-.11*exp(-max(world.y,0.)*7.);
    float distanceToEye=distance(Camera,world);
    float fog=1.-exp(-max(distanceToEye-6.,0.)*.018);
    light=mix(light,vec3(.54,.71,1.02),fog);
    fragColor=vec4(light,alpha);
}
