#version 150
// G "I am Atomic" world effects. Every look is analytic; no textures. Every mode writes
// premultiplied colour (blend ONE, ONE_MINUS_SRC_ALPHA): alpha 0 adds light, alpha > 0 also
// covers what is behind (the ground wash, curtains and the lavender glow of shafts).
// Mode 1 ground circuit decal, 2 sky circuit wall, 3 soft beam, 4 needle flare,
// 5 cast disc, 6 blast curtain, 7 orb, 8 striated beam, 9 light pillar,
// 10 blast dome, 11 shockwave ring, 12 light column, 13 smoke puff, 14 parted cloud ring, 15 core flash.
uniform int Mode;
uniform float Age;
uniform float Seed;
uniform vec4 Params;
uniform vec4 ColorModulator;
in vec2 uv;
in vec4 tint;
in float viewDepth;
in vec3 rel;
in vec3 vpos;
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
float hash3(vec3 p){p=fract(p*vec3(443.897,441.423,437.195));p+=dot(p,p.yzx+19.19);return fract((p.x+p.y)*p.z);}
// Smooth value noise in three dimensions (0..1).
float noise3(vec3 p){
    vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
    return mix(mix(mix(hash3(i),hash3(i+vec3(1,0,0)),f.x),mix(hash3(i+vec3(0,1,0)),hash3(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash3(i+vec3(0,0,1)),hash3(i+vec3(1,0,1)),f.x),mix(hash3(i+vec3(0,1,1)),hash3(i+vec3(1,1,1)),f.x),f.y),f.z);
}

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
    }else if(Mode==10){
        // Blast dome: a translucent violet shell with churning lobes, a bright silhouette rim and
        // lightning ridges crawling over it. rel = offset from the shell centre, vpos = from the camera.
        // Params: x = brightness, y = white-out (collapse), z = lightning strength, w = body opacity.
        vec3 n=normalize(rel);float facing=abs(dot(n,normalize(-vpos)));
        float rim=pow(1.-facing,2.4);
        float t=Age*.33;
        float lobes=.55*noise3(n*3.+vec3(0.,-t,0.)+Seed)+.3*noise3(n*7.+vec3(t*.3,-t*1.7,0.)+Seed*2.)+.15*noise3(n*15.+vec3(0.,-t*2.5,0.));
        float ridge=1.-smoothstep(0.,.05,abs(noise3(n*5.5+vec3(0.,-t*1.2,0.)+Seed*5.)-.5));
        float ridge2=1.-smoothstep(0.,.035,abs(noise3(n*11.+vec3(t*.7,0.,-t*.9)+Seed*9.)-.5));
        float flick=.55+.45*step(.5,hash3(vec3(floor(Age*14.),floor(n.x*6.),floor(n.z*6.))));
        float bolt=(ridge*.9+ridge2*.5)*Params.z*flick;
        vec3 violet=vec3(.46,.12,.96),magenta=vec3(.88,.34,1.),white=vec3(1.,.94,1.);
        vec3 col=mix(violet,magenta,lobes)*(.75+.6*lobes)*Params.x;
        col=mix(col,white,rim*.9);
        col=mix(col,white,Params.y);
        float foot=smoothstep(0.,.05,uv.y);
        float a=clamp((Params.w*(.35+.5*lobes)+rim*.6)*foot*tint.a,0.,1.);
        vec3 add=white*(bolt*(.7+rim)*.6+rim*.35*(1.+Params.y))*foot*tint.a;
        c=vec4(col*a*tint.rgb+add,a);
    }else if(Mode==11){
        // Shockwave ring: bright leading edge near the outer rim and a streaked violet trail.
        // uv.x around, uv.y inner 0 → outer 1. Params: x = coverage, y = white mix, z = edge position, w = trail length.
        float y=uv.y,e=Params.z;
        float edge=exp(-pow((y-e)*9.,2.))+.35*exp(-pow((y-e)*3.,2.));
        float streak=.55+.45*noise3(vec3(uv.x*48.,y*5.-Age*2.,Seed));
        float trail=smoothstep(0.,Params.w,y)*(1.-smoothstep(e-.15,e+.05,y))*streak;
        float ahead=1.-smoothstep(e,1.,y);
        vec3 col=mix(vec3(.55,.18,1.),mix(vec3(1.,.72,1.),vec3(1.),Params.y),clamp(edge,0.,1.));
        float lum=(edge*1.1+trail*.5)*ahead*tint.a;
        c=vec4(col*lum*tint.rgb,clamp(lum*Params.x,0.,1.));
    }else if(Mode==12){
        // Light column: white core, violet flanks with streaks racing upward, fading into the sky.
        // Params: x = core sharpness, y = top fade start, z = streak speed, w = core cover.
        float x=uv.x*2.-1.;
        float core=exp(-x*x*Params.x),flank=exp(-x*x*2.4);
        float streak=.65+.35*noise3(vec3(uv.x*26.,uv.y*70.-Age*Params.z,Seed));
        float top=1.-smoothstep(Params.y,1.,uv.y),foot=smoothstep(0.,.015,uv.y);
        float v=top*foot*tint.a;
        vec3 col=(vec3(1.,.97,1.)*core*1.1+vec3(.62,.3,1.)*flank*streak*.75)*tint.rgb;
        c=vec4(col*v,core*Params.w*v);
    }else if(Mode==13){
        // Smoke puff: lumpy disc lit from below by the blast, dark violet above. The integer part of
        // uv.x picks the puff's own noise. Params: x = opacity, y = heat.
        vec2 q=fract(uv)*2.-1.;float r=length(q);float ps=Seed+floor(uv.x)*7.31;
        float lump=noise3(vec3(q*2.2+ps,Age*.22))*.6+noise3(vec3(q*5.5+ps*3.,Age*.35))*.3;
        float shape=1.-smoothstep(.35+lump*.55,1.,r*1.08);
        float shade=.3+.7*smoothstep(.9,-.7,q.y);
        float inner=noise3(vec3(q*3.5+ps*7.,Age*.3));
        vec3 col=mix(vec3(.14,.05,.32),vec3(.74,.3,1.),shade*(.45+.55*inner));
        col=mix(col,vec3(1.,.72,1.),Params.y*shade*inner);
        float a=clamp(shape*Params.x*tint.a,0.,1.);
        c=vec4(col*a*tint.rgb,a);
    }else if(Mode==14){
        // Parted cloud ring: dark violet overcast torn open, its inner edge lit pink by the pillar.
        // Params: x = opacity, y = inner light.
        float y=uv.y;
        float lump=noise3(vec3(uv.x*28.,y*3.,Seed))*.6+noise3(vec3(uv.x*64.,y*8.+Age*.05,Seed*3.))*.35;
        float band=smoothstep(0.,.22+.1*lump,y)*(1.-smoothstep(.5+lump*.45,1.,y));
        float lit=exp(-y*7.)*(.6+.8*lump)*Params.y;
        vec3 col=mix(vec3(.11,.04,.26),vec3(.92,.48,1.),clamp(lit,0.,1.));
        float a=clamp(band*(.5+.35*lump)*Params.x*tint.a,0.,1.);
        c=vec4(col*a*tint.rgb+vec3(.9,.5,1.)*lit*band*.25*tint.a,a);
    }else if(Mode==15){
        // Core flash: white-hot centre, violet halo and a few soft rays. Params: x = brightness, y = core cover.
        vec2 q=uv*2.-1.;float r=length(q),ang=atan(q.y,q.x);
        float core=exp(-r*r*16.),halo=exp(-r*r*3.)*.65;
        float rays=pow(max(0.,sin(ang*9.+Seed)*sin(ang*5.-Seed*2.+Age*.5)),3.)*exp(-r*2.6)*.6;
        vec3 col=vec3(1.)*core+vec3(.74,.36,1.)*halo+vec3(1.,.62,1.)*rays;
        c=vec4(col*tint.a*tint.rgb*Params.x,core*Params.y*tint.a);
    }
    fragColor=c*ColorModulator;
}
