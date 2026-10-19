#version 150
// The gacha's pull scenes (2.39.0): a dark place lit by the hourglasses (each its grade's colour) and a dim cool key light.
uniform sampler2D Atlas;
uniform sampler2DShadow ShadowMap;
uniform vec3 Camera;
uniform float Time;
uniform vec3 Accent;
uniform float Glow;
uniform vec3 KeyDir;
uniform vec3 Key;
uniform vec3 Ambient;
uniform vec3 LightPos[12];
uniform vec3 LightColor[12];
uniform int Lights;
uniform float Hold;
in vec3 world;
in vec3 normal;
in vec2 uv;
in vec4 tint;
in vec4 shadowCoord;
in float material;
in float rigged;
out vec4 fragColor;
float shadowVisibility(vec3 n) {
    vec3 q=shadowCoord.xyz/shadowCoord.w*.5+.5;
    if(any(lessThan(q,vec3(0))) || any(greaterThan(q,vec3(1))))return 1.;
    float bias=max(.0003,.0012*(1.-max(dot(n,KeyDir),0.)));
    float result=0.;
    for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)result+=texture(ShadowMap,vec3(q.xy+vec2(x,y)*1.6/2048.,q.z-bias));
    return result/9.;
}
// Light from the hourglasses: soft wrapped diffuse, falling off with distance; spec for shiny things.
vec3 points(vec3 n,vec3 v,float wrap,float shine,float specular) {
    vec3 sum=vec3(0);
    for(int i=0;i<12;i++) {
        if(i>=Lights)break;
        vec3 l=LightPos[i]-world;float d=length(l);l/=max(d,1e-4);
        float fall=1./(1.+d*d*5.5);
        float diffuse=max((dot(n,l)+wrap)/(1.+wrap),0.);
        float spec=pow(max(dot(n,normalize(l+v)),0.),shine)*specular;
        sum+=LightColor[i]*fall*(diffuse+spec);
    }
    return sum;
}
void main() {
    bool mote=material>5.5&&material<6.5;
    vec4 tex=mote?tint:texture(Atlas,uv)*tint;
    if(tex.a<.004)discard;
    vec3 n=normalize(normal),v=normalize(Camera-world);
    if(dot(n,v)<0.)n=-n;
    vec3 base=pow(max(tex.rgb,vec3(0)),vec3(2.2));
    bool metal=material>1.5 && material<2.5;
    bool glass=material>2.5 && material<3.5;
    bool jewel=material>3.5 && material<4.5;
    float nl=max(dot(n,KeyDir),0.),vis=shadowVisibility(n);
    float fres=pow(1.-max(dot(n,v),0.),4.);
    vec3 light;
    float alpha=tex.a;
    if(rigged>.5 && material<.5) {
        // her skin stays matte and readable: a faint fill from the eye so her face never sinks into the dark
        light=base*(Ambient+Key*nl*vis+points(n,v,.45,8.,0.)+vec3(.05,.055,.07)*max(dot(n,v),0.));
        light+=base*Key*.9*pow(1.-max(dot(n,v),0.),3.)*max(dot(n,-KeyDir)*.5+.5,0.);
    } else if(metal) {
        vec3 p=points(n,v,.15,70.,2.4);
        light=base*(Ambient*1.5+Key*nl*vis*.8+p)+Key*pow(max(dot(n,normalize(KeyDir+v)),0.),82.)*vis*1.6+Accent*Glow*fres*.22;
    } else if(glass) {
        vec3 reflected=reflect(-v,n);
        float panel=pow(max(dot(reflected,normalize(vec3(-.4,.7,.9))),0.),24.);
        vec3 p=points(n,v,0.,140.,3.);
        light=vec3(.05,.10,.18)*.25+Key*(fres*.5+panel*.8)+p*.35+Accent*Glow*(.12+fres*.35);
        alpha=clamp(.03+fres*.30+panel*.18+dot(p,vec3(.1))+Glow*.03,.03,.66);
    } else if(jewel) {
        // an hourglass not yet woken keeps its jewels, sand and stream dark, so it never reads as a lit R
        float flow=.8+.2*sin(world.y*110.-Time*5.),wake=clamp(Glow*2.,0.,1.);
        light=base*(Ambient+points(n,v,.2,40.,1.))+mix(base*.03,Accent*Glow*1.6,wake)*flow;
    } else if(material>.5&&material<1.5) {
        // the sand stream and the sparkles in the hourglass
        float wake=clamp(Glow*2.,0.,1.);
        light=mix(base*.05,Accent*(1.2+Glow*1.6),wake)*(.6+Hold*.4);
    } else if(mote) {
        // round and soft, lit by the light it drifts through
        float r=1.-smoothstep(.12,.5,length(uv-.5));
        light=(base*.2+points(n,v,1.,1.,0.)*1.4)*r*2.5;
        alpha=r*tex.a;
    } else if(material>6.5&&material<7.5) {
        vec3 cell=floor(world*1900.);
        float grain=fract(sin(dot(cell,vec3(12.9898,78.233,45.164)))*43758.5453);
        float wake=clamp(Glow*2.,0.,1.);
        vec3 grey=vec3(dot(base,vec3(.3,.59,.11)));
        light=mix(grey,base,wake)*(Ambient+points(n,v,.3,1.,0.))*(.8+grain*.3)+mix(grey*.02,Accent*(.6+Glow*1.4),wake);
        light+=vec3(1)*smoothstep(.992,1.,grain)*(.04+Glow*.6)*mix(vec3(1),Accent,.6)*wake;
    } else if(material>7.5&&material<8.5) {
        // the floor: polished dark stone, pooling the light and fading to black away from the scene
        float fade=1.-smoothstep(1.4,5.5,length(world.xz-vec2(0.,.8)));
        vec3 p=points(n,v,0.,24.,1.1);
        light=(base*(Ambient+Key*nl*vis*.5+p)+Key*pow(max(dot(n,normalize(KeyDir+v)),0.),30.)*vis*.4)*fade;
    } else {
        // wood, cloth and paint
        vec3 p=points(n,v,.25,24.,.18);
        light=base*(Ambient+Key*nl*vis+p)+Key*pow(max(dot(n,normalize(KeyDir+v)),0.),20.)*vis*.05;
    }
    fragColor=vec4(light,alpha);
}
