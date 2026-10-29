#version 150
// Star Platinum's time stop (2.67.0, client/stand/TimeStopView): the stopped world, drawn over the frame (or, for whom
// it stopped, over the still of the moment it stopped). A sphere (Center, Radius: blocks, camera-relative) is the reach
// of the stopped time: what is inside it is the stopped world, a cold violet-blue monochrome (Grade). A band of the
// negative runs behind the wave's front (Band blocks deep, Negative strong; BandSide 1 behind it as it spreads, -1
// outside it as it falls back), and the front glows where it meets the world (Rim). Flash whitens the whole frame.
// Since 2.68.0 ("더 월드 할때 이펙트 엄청 멋있게 만들어줘 이펙트 대폭 강화해주셈") the sphere is a bubble seen whole: each
// pixel's ray is met with it (against the scene's depth), its surface glowing at its rim, a clock's twelve hour lines
// and its rings of latitude drawn over it (Shell), its cracks spreading as time is about to move (Crack); what is seen
// through it takes on a little of the stopped look (Outer); the world bends at its front, out as it spreads and in as
// it closes (Refract, its colours split there); an impact frame, the world in negative, as time stops (Impact); a
// vignette for an eye in it (Vignette). Without the scene's depth (a shader pack draws the frame)
// the wave is a circle on the screen round where Center shows (CenterScreen, ScreenRadius) and there is no bubble.
// What moves in the stopped time keeps its colours (2.75.1): the bodies in KeepTex take neither its look nor its
// negative, and the auras round them (AuraTex) are lifted off the world behind them, which takes the look, and laid
// back over it as they were.
uniform sampler2D SceneTex;
uniform sampler2D DepthTex;
uniform sampler2D KeepTex;   // the bodies whose colours are kept, drawn again as they are (alpha: how fully)
uniform sampler2D AuraTex;   // the auras round them, alone (premultiplied)
uniform float KeepOn;
uniform float AuraOn;
uniform mat4 InvProjView;
uniform vec3 Center;
uniform float Radius;
uniform float Band;
uniform float BandSide;
uniform float Negative;
uniform float Rim;
uniform float Grade;
uniform float Outer;
uniform float Flash;
uniform float Shell;
uniform float Crack;
uniform float Impact;
uniform float Refract;
uniform float Time;
uniform float Vignette;
uniform float Pixel;
uniform float HasDepth;
uniform vec2 CenterScreen;
uniform float ScreenRadius;
uniform float Aspect;
in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA=vec3(.299,.587,.114);
const float PI=3.14159265;

// the stopped world: the luminance, a little harder, through a cold ramp (deep violet shadows, blue mids, white lights)
vec3 stopped(vec3 c){
    float l=clamp((dot(c,LUMA)-.5)*1.12+.5,0.,1.);
    vec3 lo=vec3(.07,.05,.18),mid=vec3(.40,.43,.68),hi=vec3(.93,.95,1.);
    return l<.5?mix(lo,mid,l*2.):mix(mid,hi,l*2.-1.);
}
// the negative of the wave's front: inverted, half drained of colour, tinted violet
vec3 negative(vec3 c){
    vec3 n=1.-c;
    return clamp(mix(vec3(dot(n,LUMA)),n,.55)*vec3(1.,.86,1.22),0.,1.);
}
vec3 world(vec2 uv,float d){
    vec4 p=InvProjView*vec4(uv*2.-1.,d*2.-1.,1.);
    return p.xyz/p.w;
}
float hash13(vec3 p){p=fract(p*.1031);p+=dot(p,p.zyx+31.32);return fract((p.x+p.y)*p.z);}
vec3 hash33(vec3 p){p=fract(p*vec3(.1031,.1030,.0973));p+=dot(p,p.yxz+33.33);return fract((p.xxy+p.yxx)*p.zyx);}

// The bubble's cracks at the point n of it (unit): the edges between cells over its surface, each cell cracking open
// in its turn as Crack runs to 1; px is a pixel's size there in the cells' units (so they stay sharp, never shimmer).
float cracks(vec3 n,float px){
    vec3 p=n*3.2,i=floor(p),f=fract(p);
    float d1=8.,d2=8.;vec3 c1=vec3(0.);
    for(int z=-1;z<=1;z++)for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
        vec3 g=vec3(float(x),float(y),float(z)),r=g+hash33(i+g)-f;float d=dot(r,r);
        if(d<d1){d2=d1;d1=d;c1=i+g;}else if(d<d2)d2=d;
    }
    float edge=sqrt(d2)-sqrt(d1);
    float open=step(hash13(c1+7.),Crack*1.25-.15);
    float w=.022+px*1.5;
    return open*(1.-smoothstep(w*.4,w,edge));
}
// The bubble's surface where the ray along dir meets it, t blocks on (camera-relative point q): its glow at its rim,
// its hour lines and rings, a band of light sweeping up it now and then, its cracks.
vec3 surface(vec3 q,vec3 dir,float t){
    vec3 n=(q-Center)/max(Radius,.001);
    float facing=abs(dot(n,dir)),fres=pow(1.-facing,3.);
    float px=t*Pixel;
    float ring=sqrt(max(0.,1.-n.y*n.y));
    // twelve hour lines (meridians, fading out at the top where they meet) and rings of latitude every 22.5°
    float lon=atan(n.z,n.x)/(2.*PI)*12.,lat=asin(clamp(n.y,-1.,1.))/(.5*PI)*4.;
    float dLon=abs(fract(lon+.5)-.5)*(2.*PI/12.)*Radius*ring,dLat=abs(fract(lat+.5)-.5)*(.5*PI/4.)*Radius;
    float lw=.07+px*.9;
    float lines=(1.-smoothstep(lw*.45,lw,dLon))*(1.-smoothstep(.85,.97,abs(n.y)))+(1.-smoothstep(lw*.45,lw,dLat));
    lines=min(lines,1.);
    float sweep=exp(-pow((n.y-(fract(Time*.3)*2.6-1.3))*4.5,2.));
    // (its lines in a light of their own: tinted like the rest, they hardly showed against the sky, 2.68.0's first look)
    vec3 tint=mix(vec3(.52,.40,1.),vec3(.95,.92,1.),clamp(fres*1.25,0.,1.));
    vec3 col=tint*(fres*1.15+.05+sweep*.22*(.25+fres))+vec3(.80,.74,1.)*lines*(.75+.25*fres);
    if(Crack>0.)col+=cracks(n,px*3.2/max(Radius,.001))*vec3(1.,.96,1.)*1.7;
    return col;
}

void main(){
    vec2 uv=texCoord;
    float dist,width,sceneDist=1e9;vec3 dir=vec3(0.,0.,-1.);
    if(HasDepth>.5){
        float d=texture(DepthTex,uv).r;
        dir=normalize(world(uv,1.));
        // the sky (nothing drawn there) is as far as can be: outside the sphere
        if(d>=1.)dist=1e6;
        else{vec3 p=world(uv,d);sceneDist=length(p);dist=length(p-Center);}
        width=1.;
    }else{
        // no depth: the wave as a circle on the screen, its radius a share of the screen's height
        vec2 q=(uv-CenterScreen)*vec2(Aspect,1.);
        dist=length(q);width=.012;
    }
    float r=HasDepth>.5?Radius:ScreenRadius;
    // the ray and the bubble: where it goes in and out (t0, t1), whether what it meets lies beyond it, how near the
    // bubble's edge as seen it passes (1 at its edge)
    bool bubble=HasDepth>.5&&r>.05;
    float b=dot(dir,Center),h=b*b-(dot(Center,Center)-r*r),t0=-1.,t1=-1.,through=0.,edge=0.;
    if(bubble&&h>0.){
        float sq=sqrt(h);t0=b-sq;t1=b+sq;
        through=t1>0.&&sceneDist>t1?1.:0.;
        edge=1.-clamp(sq/r,0.,1.);
    }
    // the world bends at the front, out from its middle as it spreads (in as it closes), its colours splitting there
    vec2 away=(uv-CenterScreen)*vec2(Aspect,1.);float al=length(away);
    away=al>1e-4?away/al:vec2(0.);away.x/=Aspect;
    float front=exp(-pow((dist-r)/(1.8*width),2.));
    float bend=Refract*(front+.8*pow(edge,5.)*step(0.,t1));
    vec2 off=away*bend*.02;
    vec3 c=vec3(texture(SceneTex,uv+off*1.3).r,texture(SceneTex,uv+off).g,texture(SceneTex,uv+off*.7).b);
    // what keeps its colours here, and the world behind the auras (the frame is theirs laid over it)
    float keep=KeepOn>.5?clamp(texture(KeepTex,uv+off).a,0.,1.):0.;
    vec4 au=AuraOn>.5?texture(AuraTex,uv+off):vec4(0.);
    float ua=clamp(au.a,0.,.95);
    vec3 base=ua>.001?clamp((c-au.rgb)/(1.-ua),0.,1.):c;
    // in the stopped world, or seen through its bubble (a little of its look), or (no depth) the whole frame for an eye in it
    float inside=1.-smoothstep(r-1.2*width,r+.4*width,dist);
    float w=inside*Grade;
    if(HasDepth>.5)w=max(w,through*Outer*(1.-inside));
    else w=max(w,Outer*step(length(Center),Radius));
    vec3 col=mix(base,stopped(base),clamp(w,0.,1.)*(1.-keep));
    // the negative behind the front (or just outside it as it falls back)
    float behind=(r-dist)*BandSide;
    float band=Negative*step(0.,behind)*(1.-smoothstep(Band*.6*width,Band*width,behind));
    col=mix(col,negative(base),clamp(band,0.,1.)*(1.-keep));
    // the auras laid back over it as they were
    col=au.rgb+col*(1.-ua);
    // the front glows where it meets the world
    float rim=Rim*exp(-pow((dist-r)/(.7*width),2.));
    col+=rim*vec3(.78,.62,1.);
    // the bubble's surface, in front of the world (its far side dimmer through its near side), laid on as light
    if(bubble&&Shell>0.&&h>0.){
        vec3 s=vec3(0.);
        if(t0>.05&&t0<sceneDist)s+=surface(dir*t0,dir,t0);
        if(t1>.05&&t1<sceneDist)s+=surface(dir*t1,dir,t1)*(t0>.05?.55:1.);
        s=clamp(s*Shell,0.,1.);
        col=1.-(1.-col)*(1.-s);
    }
    // an eye in the stopped world: its edges darken
    if(Vignette>0.){vec2 v=(uv-.5)*vec2(Aspect,1.);col*=1.-Vignette*smoothstep(.35,1.05,length(v));}
    // the impact frame as time stops: the world in negative, its contrast pushed (a two-tone cut by brightness left
    // a bright sky and a bright floor one black)
    if(Impact>0.){vec3 imp=clamp((negative(c)-.5)*1.6+.5,0.,1.);col=mix(col,imp,clamp(Impact,0.,1.));}
    col=mix(col,vec3(.96,.93,1.),clamp(Flash,0.,1.));
    fragColor=vec4(col,1.);
}
