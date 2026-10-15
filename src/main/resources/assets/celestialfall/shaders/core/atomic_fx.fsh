#version 150
// G "I am Atomic" world effects. Every look is analytic; no textures. Every mode writes
// premultiplied colour (blend ONE, ONE_MINUS_SRC_ALPHA): alpha 0 adds light, alpha > 0 also
// covers what is behind (the ground wash, curtains and the lavender glow of shafts).
// Mode 1 ground circuit decal, 2 sky circuit wall, 3 soft beam, 4 needle flare,
// 5 cast disc, 6 blast curtain, 7 orb, 8 striated beam, 9 light pillar.
uniform int Mode;
uniform float Age;
uniform float Seed;
uniform vec4 Params;
uniform vec4 ColorModulator;
in vec2 uv;
in vec4 tint;
in float viewDepth;
out vec4 fragColor;

const float PI=3.14159265;
const vec2 DIRS[8]=vec2[8](vec2(1,0),vec2(1,1),vec2(0,1),vec2(-1,1),vec2(-1,0),vec2(-1,-1),vec2(0,-1),vec2(1,-1));

float hash(vec2 p){p=fract(p*vec2(443.897,441.423));p+=dot(p,p.yx+19.19);return fract((p.x+p.y)*p.x);}
float seg(vec2 p,vec2 a,vec2 b){vec2 pa=p-a,ba=b-a;float h=clamp(dot(pa,ba)/dot(ba,ba),0.,1.);return length(pa-ba*h);}

// Printed-circuit traces. Every grid cell owns one trace piece from its centre to one of its
// eight neighbours. Cells that share a direction chain into long parallel traces, switched-off
// lanes split them into bundles, empty cells end a trace with a round cap and a one-step turn
// makes the 45 degree jogs. Inside `radial` blocks the pieces point at the origin (the feet).
int cellDir(vec2 c,float lane,float seed,float radial,float region,float empty){
    vec2 w=(c+.5)*lane;
    float h=hash(c*1.031+seed*17.31);
    if(h<empty)return -1;
    int d;
    if(length(w)<radial){
        float a=atan(-w.y,-w.x);
        d=int(mod(floor(a/(PI*.25)+.5),8.));
    }else{
        vec2 reg=floor(w/region+vec2(.37,.71)*hash(floor(w/region)+seed));
        float g=hash(reg*1.37+seed*3.1+.5);
        d=g<.36?0:g<.72?2:g<.86?1:3;
        float lid=d==0?c.y:d==2?c.x:d==1?c.x-c.y:c.x+c.y;
        if(hash(vec2(lid*.173,reg.x*7.1+reg.y*3.3)+seed*.61)<.3)return -1;
    }
    if(h>.95)d=(d+1)&7;else if(h>.9)d=(d+7)&7;
    return d;
}
float circuit(vec2 p,float lane,float seed,float radial,float region,float empty){
    vec2 c=floor(p/lane);float best=1e9;
    for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
        vec2 n=c+vec2(i,j);int d=cellDir(n,lane,seed,radial,region,empty);
        if(d<0)continue;vec2 a=(n+.5)*lane;
        best=min(best,seg(p,a,a+DIRS[d]*lane));
    }
    return best;
}
float lineMask(float d,float hw,float px){return (1.-smoothstep(hw,hw+px*1.25,d))*min(1.,(hw*2.+px*.35)/max(px,1e-5));}

void main(){
    vec4 c=vec4(0);
    if(Mode==1){
        // Params: x = pattern scale (reveal grows it outward), y = decal radius,
        // z = pulse wash radius (blocks, <0 none), w = tone (0 white, 1 magenta void, 2 pink).
        vec2 p=uv/max(Params.x,.05);
        float r=length(uv),R=Params.y*Params.x;
        float px=max(fwidth(p.x),fwidth(p.y));
        float wash=Params.z>0.?(1.-smoothstep(Params.z-1.2,Params.z,r)):0.;
        float d=circuit(p,.33,Seed,4.6,2.6,.1);
        float hw=.011*(1.+wash*.7),glowW=.032*(1.+wash*1.6);
        float core=lineMask(d,hw,px),glow=exp(-d*d/(glowW*glowW))*(.55+.25*wash);
        float edge=1.-smoothstep(R-2.5,R,r);
        if(Params.w<.5){
            vec3 emit=vec3(1.)*core+vec3(.73,.78,.94)*glow*.85+vec3(1.,1.,1.)*wash*(.16-.05*clamp(r/8.,0.,1.));
            float a=.5*edge;
            c=vec4(vec3(.82,.83,.97)*a+emit*edge,a);
        }else if(Params.w<1.5){
            float fog=1.-smoothstep(4.,13.,viewDepth);
            vec3 emit=vec3(.86,.45,1.)*core+vec3(.6,.2,.85)*glow;
            c=vec4(emit*edge*fog,0.);
        }else{
            vec3 emit=vec3(1.,.82,.98)*core+vec3(.95,.62,.93)*glow*.6;
            c=vec4(emit*edge,0.);
        }
        c*=tint.a;
    }else if(Mode==2){
        // Sky wall: uv = (arc, height) blocks. Params: x = reveal 0..1, y = fade start height.
        float px=max(fwidth(uv.x),fwidth(uv.y));
        vec2 cell=floor(uv/1.7);
        float d=circuit(uv,1.7,Seed+11.,0.,9.,.8);
        float appear=hash(cell*.71+3.3);
        float shown=step(appear,Params.x*1.35-.1)*step(.45,hash(cell*1.3+7.));
        float core=lineMask(d,.045,px),glow=exp(-d*d/(.15*.15));
        float fade=smoothstep(Params.y,Params.y+6.,uv.y)*shown*tint.a;
        // White core over a light cyan-blue glow that also tints a bright sky.
        float a=glow*.32*fade;
        c=vec4(vec3(.64,.8,.98)*a+vec3(.94,.97,1.)*core*fade,a);
    }else if(Mode==3){
        // Soft vertical beam: white at the top fading to translucent lavender-violet below.
        float x=uv.x*2.-1.;float prof=exp(-x*x*5.);
        vec3 col=mix(vec3(.537,.455,.773),vec3(.95,.99,.97),smoothstep(.05,.85,uv.y));
        float a=prof*mix(.45,1.,uv.y)*smoothstep(0.,.07,uv.y);
        c=vec4(col*a*tint.a,0.);
    }else if(Mode==4){
        // Needle flare: a lens tapering to points at both ends, white core, violet glow.
        float y=clamp(uv.y,0.,1.);float w=pow(sin(PI*y),.75)+1e-3;
        float x=abs(uv.x*2.-1.)/w;
        float core=1.-smoothstep(.0,.45,x),edge=1.-smoothstep(.35,1.,x),glow=exp(-x*x*1.3)*.5;
        float a=glow*.6*tint.a;
        vec3 col=(vec3(1.,.99,.99)*core+vec3(.85,.71,.92)*edge*.6)*tint.rgb*tint.a+vec3(.63,.44,.88)*a;
        c=vec4(col,a);
    }else if(Mode==5){
        // Cast disc: white core to 0.9 of the radius, lavender rim.
        float r=length(uv*2.-1.);
        vec3 col=mix(vec3(.984,.992,.973),mix(vec3(.843,.871,.992),vec3(.753,.784,.933),smoothstep(.9,1.,r)),smoothstep(.82,.95,r));
        float a=1.-smoothstep(.93,1.,r);
        c=vec4(col*a*tint.a,0.);
    }else if(Mode==6){
        // Blast curtain: blurred tall band with faint vertical striations (alpha blended).
        float x=abs(uv.x*2.-1.);
        float band=1.-smoothstep(.3,1.,x);
        float stri=.82+.18*sin(uv.x*31.+Seed*7.+Age*1.7)*sin(uv.x*13.-Age*.9+Seed);
        float v=smoothstep(0.,.12,uv.y)*(1.-smoothstep(.62,1.,uv.y));
        float a=band*v*tint.a;
        c=vec4(tint.rgb*stri*a,a);
    }else if(Mode==7){
        float r=length(uv*2.-1.);
        float core=exp(-r*r*16.),halo=exp(-r*r*4.)*.45*(1.-smoothstep(.85,1.,r));
        c=vec4((vec3(1.,.995,.99)*core+tint.rgb*halo)*tint.a,0.);
    }else if(Mode==8){
        // Final eruption beam: lighter column with vertical striations, fading toward the sky.
        float x=uv.x;
        float stri=.7+.3*sin(x*PI*2.*23.+Seed)*sin(x*PI*2.*7.-Age*2.);
        float v=1.-smoothstep(.55,1.,uv.y);
        c=vec4(tint.rgb*stri*v*tint.a,0.);
    }else if(Mode==9){
        // Light pillar: hairline white core, lavender glow.
        // Light pillar: hairline white core inside a lavender shaft that also shows against a bright sky.
        float x=uv.x*2.-1.;
        float core=exp(-x*x*60.),glow=exp(-x*x*12.);
        float v=smoothstep(0.,.004,uv.y)*(1.-smoothstep(.75,1.,uv.y))*tint.a;
        float a=glow*.4*v;
        c=vec4(vec3(.63,.59,.91)*a+vec3(1.,.97,1.)*core*v,a);
    }
    fragColor=c*ColorModulator;
}
