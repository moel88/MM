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
uniform vec3 SpotPos;
uniform vec3 SpotDir;
uniform vec2 SpotCone;
uniform vec3 SpotColor;
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
// The spotlight from far above (2.40.0): a cone of light, soft at its rim, casting the key light's shadows (its direction).
vec3 spot(vec3 n,vec3 v,float vis,float wrap,float shine,float specular) {
    if(SpotColor.r+SpotColor.g+SpotColor.b<=0.)return vec3(0);
    vec3 l=SpotPos-world;float d=length(l);l/=max(d,1e-4);
    float cone=smoothstep(SpotCone.x,SpotCone.y,dot(-l,SpotDir));
    float diffuse=max((dot(n,l)+wrap)/(1.+wrap),0.);
    float spec=pow(max(dot(n,normalize(l+v)),0.),shine)*specular;
    return SpotColor*cone*vis*(diffuse+spec)/(1.+d*d*.012);
}
float ring(float r,float r0,float w,float fw){return 1.-smoothstep(w,w+fw,abs(r-r0));}
float segment(vec2 p,vec2 a,vec2 b){vec2 pa=p-a,ba=b-a;float h=clamp(dot(pa,ba)/dot(ba,ba),0.,1.);return length(pa-ba*h);}
float hash(float x){return fract(sin(x*127.1)*43758.5453);}
// The magic circle under Bomi (2.40.0): rings, ticks and a band of runes turning, a six-pointed star within.
float circle(vec2 p) {
    float r=length(p),a=atan(p.y,p.x),fw=max(fwidth(r),1e-4)*1.3;
    float lines=ring(r,.965,.006,fw)+ring(r,.925,.0025,fw)+ring(r,.655,.004,fw)+ring(r,.62,.002,fw)+ring(r,.33,.004,fw)+ring(r,.12,.003,fw);
    float turn=a/6.2831853+Time*.012;
    float t=abs(fract(turn*96.)-.5)*2.,tick=(1.-smoothstep(.75,.92,t))*step(.86,r)*step(r,.915);
    float big=abs(fract(turn*12.)-.5)*2.;tick+=(1.-smoothstep(.9,.97,big))*step(.8,r)*step(r,.915);
    // the runes: in each of 40 cells, a stroke or two at hashed places
    float back=a/6.2831853-Time*.018,cell=floor(back*40.),f=fract(back*40.),k=hash(cell);
    float rune=0.;
    if(r>.69&&r<.88){
        float rr=(r-.69)/.19;
        rune+=(1.-smoothstep(.0,.06,abs(f-(.2+.6*k))))*step(.15,rr)*step(rr,.85);
        rune+=(1.-smoothstep(.0,.05,abs(rr-(.3+.4*hash(cell+3.)))))*step(.15,f)*step(f,.85)*step(.35,k);
        rune+=(1.-smoothstep(.0,.08,length(vec2(f-.5,rr-.5)*vec2(1.,1.))-.12*hash(cell+7.)))*step(.7,k);
    }
    // the star: two triangles in the inner ring, turning the other way
    float star=1e3,spin=-Time*.05;
    for(int i=0;i<6;i++) {
        float a0=spin+float(i)*1.0471976,a1=a0+2.0943951;
        star=min(star,segment(p,vec2(cos(a0),sin(a0))*.62,vec2(cos(a1),sin(a1))*.62));
    }
    float starLine=1.-smoothstep(.003,.003+fw,star);
    return clamp(lines+tick*.8+rune*.9+starLine*.9,0.,1.6);
}
void main() {
    bool mote=material>5.5&&material<6.5;
    bool drawn=material>8.5;
    vec4 tex=(mote||drawn)?tint:texture(Atlas,uv)*tint;
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
        light=base*(Ambient+Key*nl*vis+points(n,v,.45,8.,0.)+spot(n,v,vis,.35,8.,0.)+vec3(.05,.055,.07)*max(dot(n,v),0.));
        light+=base*Key*.9*pow(1.-max(dot(n,v),0.),3.)*max(dot(n,-KeyDir)*.5+.5,0.);
    } else if(metal) {
        vec3 p=points(n,v,.15,70.,2.4)+spot(n,v,vis,.15,70.,2.);
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
        light=(base*.2+points(n,v,1.,1.,0.)*1.4+spot(n,v,1.,1.,1.,0.)*1.8)*r*2.5;
        alpha=r*tex.a;
    } else if(material>6.5&&material<7.5) {
        vec3 cell=floor(world*1900.);
        float grain=fract(sin(dot(cell,vec3(12.9898,78.233,45.164)))*43758.5453);
        float wake=clamp(Glow*2.,0.,1.);
        vec3 grey=vec3(dot(base,vec3(.3,.59,.11)));
        light=mix(grey,base,wake)*(Ambient+points(n,v,.3,1.,0.))*(.8+grain*.3)+mix(grey*.02,Accent*(.6+Glow*1.4),wake);
        light+=vec3(1)*smoothstep(.992,1.,grain)*(.04+Glow*.6)*mix(vec3(1),Accent,.6)*wake;
    } else if(material>8.5&&material<9.5) {
        // the spotlight's beam (2.40.0): a cone of light seen through, brightest where it faces the eye, fading at its rim,
        // at the lamp and into the floor, dust drifting in it
        // (how squarely the eye looks through it: the cone's own outward direction against the view, across the floor)
        vec2 out_=normalize(world.xz-SpotPos.xz+vec2(1e-5)),view=normalize(Camera.xz-world.xz+vec2(1e-5));
        float facing=pow(abs(dot(out_,view)),3.),h=clamp(uv.y,0.,1.);
        float dust=.78+.22*sin(world.y*7.-Time*.5+world.x*11.)*sin(world.x*6.+world.z*9.+Time*.35);
        light=Accent*Glow*facing*smoothstep(0.,.3,h)*(1.-smoothstep(.82,1.,h))*dust;
        alpha=1.;
    } else if(material>9.5&&material<10.5) {
        vec2 p=uv*2.-1.;float r=length(p);
        if(r>1.)discard;
        float c=circle(p);
        light=Accent*Glow*(c*1.4+.35*pow(1.-r,2.)+.25*smoothstep(.2,0.,abs(r-.96)));
        alpha=1.;
    } else if(material>10.5&&material<11.5) {
        float r=1.-smoothstep(.08,.5,length(uv-.5));
        light=Accent*Glow*r*r*3.;
        alpha=r;
    } else if(material>7.5&&material<8.5) {
        // the floor: polished dark stone, pooling the light and fading to black away from the scene
        float fade=1.-smoothstep(1.4,5.5,length(world.xz-vec2(0.,.8)));
        vec3 p=points(n,v,0.,24.,1.1)+spot(n,v,vis,0.,24.,.6);
        light=(base*(Ambient+Key*nl*vis*.5+p)+Key*pow(max(dot(n,normalize(KeyDir+v)),0.),30.)*vis*.4)*fade;
    } else {
        // wood, cloth and paint
        vec3 p=points(n,v,.25,24.,.18)+spot(n,v,vis,.25,24.,.18);
        light=base*(Ambient+Key*nl*vis+p)+Key*pow(max(dot(n,normalize(KeyDir+v)),0.),20.)*vis*.05;
    }
    fragColor=vec4(light,alpha);
}
