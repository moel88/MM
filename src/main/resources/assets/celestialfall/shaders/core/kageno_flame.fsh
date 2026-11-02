#version 150
in vec2 uv;
in vec4 tint;
uniform vec4 ColorModulator;
uniform float Age;
uniform int Mode;
uniform float Hot;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);}
float fbm(vec2 p){return .53*noise(p)+.28*noise(p*2.03+17.7)+.13*noise(p*4.11+8.3)+.06*noise(p*8.23);}
// Thin streaks of light along a family of n spiral lines (Lee So-sang's H whirlpool, mode 16 style 15; 2.82.12, user
// request: "소용돌이를 포현하는 하늘색 부분이 너무 두껍다고 해야하나 결이 더 많아야 할듯"). k the line coordinate (a line where
// it is whole, n of them a turn), kw how much it changes across a pixel, u the place along each line (the log of the
// radius, so no seam), w a line's half width (a share of the gap between lines), d the dashes a unit of u, fill the
// share of dashes drawn, s a seed. Each line its own width and brightness, each dash its own length, tapering at both
// ends, its head (outward, leading the turn) the fuller; too fine to draw, a family fades away rather than shimmer.
float streaks(float k,float kw,float u,float n,float w,float d,float fill,float s){
 float i=floor(k+.5),f=k-i,id=mod(i,n);
 float h1=hash(vec2(id+.31,s)),h2=hash(vec2(id*1.37+5.1,s+2.3));
 float x=u*d+h1*13.0,j=floor(x),g=x-j;
 float h3=hash(vec2(j+id*17.0,s+4.7)),h4=hash(vec2(j*1.7+id*5.0,s+8.9));
 float tt=g/mix(.5,.97,h3);
 float taper=smoothstep(0.0,.7,tt)*(1.0-smoothstep(.7,1.0,tt))*step(1.0-fill,h4);
 float wid=w*mix(.7,1.3,h2)*(.3+.7*taper),ww=max(wid,kw*.75);
 return exp(-f*f/(ww*ww))*wid/ww*taper*mix(.7,1.0,h2)*(1.0-smoothstep(.22,.45,kw));
}
void main(){
 vec2 p=uv*2.0-1.0;float seed=tint.r*31.7,t=Age*.075+seed;
 vec3 rgb;float alpha;
 if(Mode==1){
  float r=length(p);float n=fbm(p*6.0+vec2(t*.12,-t*.18));
  float edge=1.0-smoothstep(.72+n*.17,.96+n*.10,r);
  float swirl=sin(atan(p.y,p.x)*5.0+r*16.0-t*.8+fbm(p*5.0+t*.2)*7.0)*.5+.5;
  float veins=pow(swirl,7.0)*smoothstep(.15,.7,r);
  rgb=mix(vec3(.004,.001,.012),vec3(.32,.008,.78),veins*.3);
  rgb+=vec3(.25,.04,.5)*pow(n,4.0)*1.2;
  alpha=edge*tint.a*.91;
 }else if(Mode==2){
  float r=length(p);float n=fbm(p*5.0+vec2(t*.4,-t*.7));
  float rim=1.0-smoothstep(.05,.16,abs(r-(.68+n*.16)));
  float core=1.0-smoothstep(.65,.85,r);
  rgb=mix(vec3(.12,.002,.36),vec3(.72,.05,1.0),n);
  rgb+=rim*vec3(.75,.40,.9);alpha=max(core*.92,rim)*tint.a;
 }else if(Mode==3){
  float r=length(p);float core=1.0-smoothstep(.16,.65,r);
  rgb=mix(vec3(.65,.08,1.0),vec3(1.0,.96,1.0),core);
  alpha=(1.0-smoothstep(.1,1.0,r))*tint.a*.88;
 }else if(Mode==4){
  float y=uv.y;
  float n=fbm(vec2(uv.x*13.0,y*4.0-Age*.035));
  float streak=pow(max(0.0,1.0-abs(sin(uv.x*34.0+y*5.0-Age*.06+n*4.0))),9.0);
  float wisp=smoothstep(.47,.72,n)*pow(sin(3.14159*y),.8);
  rgb=mix(vec3(.22,.015,.68),vec3(.68,.32,1.0),streak);
  rgb=mix(rgb,vec3(.93,.79,1.0),streak*streak*.35);
  alpha=(wisp*.45+streak*.90)*smoothstep(0.0,.15,y)*(1.0-smoothstep(.74,1.0,y))*tint.a;
 }else if(Mode==5){
  // Broad rolling lobes, white cores and magenta edges sampled from the T footage.
  vec2 q=vec2(p.x,p.y*.88);
  vec2 flow=uv*3.4+vec2(seed,-Age*.085);
  vec2 warp=vec2(fbm(flow+4.7),fbm(flow-13.2));
  float n=fbm(flow+warp*2.1);
  float lobes=.11*sin(atan(q.y,q.x)*5.0+seed+Age*.08);
  float shape=.78+lobes+(n-.48)*.88-length(q+vec2(sin(Age*.07+seed)*.10,0));
  float body=smoothstep(-.06,.08,shape);
  float holes=smoothstep(.52,.67,fbm(flow*1.45+warp*3.4));
  body*=1.0-holes*smoothstep(.02,.18,shape)*.92;
  float heat=smoothstep(.02,.28,shape)*smoothstep(.25,.62,n+.14);
  float initial=1.0-smoothstep(2.0,11.0,Age);
  rgb=mix(vec3(.55,.0,1.0),vec3(1.0,.42,1.0),smoothstep(-.05,.13,shape));
  rgb=mix(rgb,vec3(1.0,.93,1.0),clamp(pow(heat,2.4)*.45+initial*.75,0.0,1.0));
  float glow=smoothstep(-.24,-.015,shape)*(1.0-body)*.16;
  alpha=max(body,glow)*tint.a;
 }else if(Mode==6){
  // Anime brush stroke: uv.x runs tail(0)->head(1), uv.y across the band.
  float x=uv.x,y=uv.y*2.0-1.0;
  float streak=fbm(vec2(x*5.0+seed,uv.y*15.0));
  float fine=noise(vec2(x*18.0-seed,uv.y*42.0));
  float width=pow(sin(3.14159*clamp(x,0.0,1.0)),.55)*(.78+.22*streak);
  float d=abs(y)/max(width,.02);
  float body=1.0-smoothstep(.55+.25*fine,1.0,d);
  float core=1.0-smoothstep(.0,.55,d+(1.0-x)*.35);
  float tail=smoothstep(0.0,.55,x)*(1.0-smoothstep(.97,1.0,x));
  vec3 deep=vec3(.20,.0,.55),mid=vec3(.62,.12,1.0),hot=vec3(1.0,.86,1.0);
  rgb=mix(deep,mid,smoothstep(.95,.35,d));
  rgb=mix(rgb,hot,core*(.55+.45*x));
  alpha=body*tail*(.55+.45*streak)*tint.a;
 }else if(Mode==8){
  // Brush crescent: uv.x start(0)->end(1) along the arc, uv.y inner(0)->outer(1) across the band.
  // tint.r = style*16+seed, tint.g = erased fraction from the start, tint.b = taper
  // (0 widest at the start, .5 even, 1 widest at the end), tint.a = alpha.
  float code=floor(tint.r*255.0+.5),style=floor(code/16.0),sd=mod(code,16.0)*3.7;
  float x=clamp(uv.x,0.0,1.0),y=uv.y,taper=tint.b;
  float w=mix(mix(1.0,.18,x),mix(.18,1.0,x),taper);
  w=taper>.4&&taper<.6?1.0:w;
  w*=smoothstep(0.0,.06,x)*(1.0-smoothstep(.9,1.0,x))*.75+.25;
  float strand=fbm(vec2(x*4.0+sd,y*38.0));
  float serr=(noise(vec2(x*60.0+sd,3.0))-.5)*.18;
  float d=(1.0-y+serr*w)/max(w,.02);
  float body=(1.0-smoothstep(.92,1.0,d))*smoothstep(-.06,.0,1.0-y);
  // Split strands at the thin end: gaps open between the hairs of the brush.
  float split=smoothstep(.55,.9,1.0-w);
  body*=1.0-split*smoothstep(.42,.58,strand);
  // Erosion from the start end with a ragged front.
  float front=tint.g*1.08-.04+(strand-.5)*.12;
  body*=smoothstep(front,front+.05,x);
  // Measured k-means stops (M1 hit 1): blue-violet rim, violet, pink core, violet, blue-violet,
  // dark navy along the inner edge; white-pink highlights only on the brightest hairs.
  vec3 rim,vio,core,hi,navy;
  if(style<.5){rim=vec3(.29,.16,.92);vio=vec3(.48,.27,.97);core=vec3(.70,.46,.98);hi=vec3(.90,.80,.98);navy=vec3(.08,.09,.41);}
  else if(style<1.5){rim=vec3(.25,.14,.90);vio=vec3(.42,.21,.96);core=vec3(.66,.42,.98);hi=vec3(.92,.82,.98);navy=vec3(.15,.17,.65);}
  else if(style<2.5){rim=vec3(.34,.17,.89);vio=vec3(.52,.27,.96);core=vec3(.74,.48,.98);hi=vec3(.87,.85,.96);navy=vec3(.02,.02,.10);}
  else if(style<3.5){rim=vec3(.48,.39,.94);vio=vec3(.65,.27,.98);core=vec3(.93,.54,.98);hi=vec3(.99,.85,.98);navy=vec3(.07,.05,.47);}
  else if(style<4.5){rim=vec3(.54,.58,.88);vio=vec3(.72,.64,.95);core=vec3(.88,.76,.98);hi=vec3(.98,.94,.98);navy=vec3(.54,.58,.88);}
  else if(style<5.5){rim=vec3(.64,.35,.75);vio=vec3(.84,.32,.96);core=vec3(.93,.6,.97);hi=vec3(.98,.89,.98);navy=vec3(.73,.53,.93);}
  else if(style<6.5){rim=vec3(.15,.1,.66);vio=vec3(.5,.23,.96);core=vec3(.79,.44,.98);hi=vec3(.97,.9,.98);navy=vec3(.86,.53,.98);}
  // T+ blue (k-means of the T+ reference): deep blue body, electric blue-violet core, white-pink hairs.
  else if(style<7.5){rim=vec3(.06,.01,.51);vio=vec3(.10,.0,.70);core=vec3(.40,.06,.95);hi=vec3(.96,.86,.99);navy=vec3(.04,.0,.30);}
  // M1 ring (k-means of the M1 reference): violet rim, magenta body, hot pink core, a dark inner edge.
  else if(style<8.5){rim=vec3(.40,.0,.62);vio=vec3(.82,.04,.93);core=vec3(.93,.22,.96);hi=vec3(.99,.72,.99);navy=vec3(.13,.03,.23);}
  // M1 ring as it forms: black-violet with a violet core.
  else if(style<9.5){rim=vec3(.30,.0,.60);vio=vec3(.25,.07,.37);core=vec3(.61,.10,.89);hi=vec3(.75,.35,.95);navy=vec3(.08,.02,.14);}
  // M1 ring fading into blue-violet streaks.
  else if(style<10.5){rim=vec3(.26,.13,.53);vio=vec3(.38,.18,.73);core=vec3(.64,.24,.86);hi=vec3(.85,.70,.98);navy=vec3(.21,.10,.36);}
  else{rim=vec3(.74,.46,1.0);vio=vec3(.58,.24,.99);core=vec3(.92,.62,1.0);hi=vec3(.98,.88,1.0);navy=vec3(.27,.06,.70);}
  rgb=d<.08?rim:d<.3?mix(rim,vio,(d-.08)/.22):d<.45?mix(vio,core,(d-.3)/.15):d<.7?mix(core,vio,(d-.45)/.25):d<.88?mix(vio,rim,(d-.7)/.18):mix(rim,navy,clamp((d-.88)/.1,0.0,1.0));
  // E finisher (measured stops, outer → inner): bright violet rim, pink highlight, electric-violet
  // body, a hot white-pink streak, violet again and a dark indigo inner edge.
  if(style>2.5&&style<3.5){
   vec3 r0=vec3(.54,.26,.96),pk=vec3(.96,.61,.98),b0=vec3(.65,.27,.98),b1=vec3(.41,.13,.99),h0=vec3(.84,.45,1.0),h1=vec3(.99,.92,.98),v1=vec3(.59,.29,.92),i0=vec3(.19,.11,.74),i1=vec3(.07,.05,.47);
   rgb=d<.06?r0:d<.14?mix(r0,pk,(d-.06)/.08):d<.22?mix(pk,b0,(d-.14)/.08):d<.42?mix(b0,b1,(d-.22)/.2):d<.5?mix(b1,h0,(d-.42)/.08):d<.56?mix(h0,h1,(d-.5)/.06):d<.64?mix(h1,h0,(d-.56)/.08):d<.74?mix(h0,v1,(d-.64)/.1):d<.86?mix(v1,i0,(d-.74)/.12):mix(i0,i1,clamp((d-.86)/.12,0.0,1.0));
  }
  // Spin ring: a narrow bright band outside, then an opaque dark-navy band inside it.
  if(style>1.5&&style<2.5)rgb=d<.06?rim:d<.18?mix(rim,vio,(d-.06)/.12):d<.28?mix(vio,core,(d-.18)/.1):d<.4?mix(core,vio,(d-.28)/.12):d<.52?mix(vio,navy,(d-.4)/.12):navy;
  float hair=noise(vec2(x*9.0+sd,y*95.0));
  rgb=mix(rgb,hi,smoothstep(.72,.9,hair)*smoothstep(.12,.3,d)*(1.0-smoothstep(.5,.7,d)));
  alpha=body*(.86+.14*strand)*tint.a;
  if(style>14.5){
   // T+ (어둠을 가로지르다, 2.38.0) fire slashes: an anime flame crescent, widest in the middle when even (taper .5)
   // so one flying has pointed tips. A crisp cutting edge outlined in ink on the outer side and one thin white-hot
   // core lane just inside it, then flat cel tones of hot pink, magenta and violet, nested, whose inner side breaks
   // into pointed flame tongues licking back toward the centre (behind a flying slash), outlined in ink and running
   // toward the tail as the fire burns; the tail is eaten with a glowing ragged edge. Violet carries it; only the
   // core lane and the eaten edge burn white on the effect layer.
   float wf=taper>.4&&taper<.6?pow(sin(3.14159*x),.65):mix(mix(1.0,.12,x),mix(.12,1.0,x),taper)*(smoothstep(0.0,.05,x)*.8+.2);
   wf=max(wf,.02);
   float df=(1.0-y)/wf-.05;
   float aa=min(max(fwidth(df),.0015)*1.3,.08);
   float run=Age*.09;
   float tx=x*(8.0+6.0*wf)+run*2.3+sd+df*.6*(noise(vec2(x*5.0+sd,run*.8))-.35);
   float tri=abs(fract(tx)-.5)*2.0;
   float tn=noise(vec2(tx*.41+sd,run*1.4));
   float lick=pow(1.0-tri,1.6)*(.45+.75*tn);
   float edge=.5+.55*lick;
   body=smoothstep(-aa,aa,df)*(1.0-smoothstep(edge-aa,edge+aa,df));
   float eat=smoothstep(front-.004,front+.012,x);body*=eat;
   vec3 ink=vec3(.10,.01,.24),pinkc=vec3(1.0,.40,.92),mag=vec3(.78,.14,1.0),vio=vec3(.46,.07,.92),hotc=vec3(1.0,.84,1.0);
   float pinkZone=1.0-smoothstep(.22+.2*lick-aa,.22+.2*lick+aa,df);
   float magZone=1.0-smoothstep(.36+.38*lick-aa,.36+.38*lick+aa,df);
   float line=smoothstep(edge-.07-aa,edge-.07+aa,df);
   rgb=mix(vio,mag,magZone);
   rgb=mix(rgb,pinkc,pinkZone);
   rgb=mix(rgb,ink,max(line,1.0-smoothstep(.05-aa,.05+aa,df)));
   float headFade=taper>.4&&taper<.6?1.0-smoothstep(.75,.97,x):1.0;
   float whiteCore=(1.0-smoothstep(.022,.022+aa*2.0+.012*hair,abs(df-.11-.012*sin(x*11.0+sd))))*smoothstep(.04,.3,x)*headFade;
   rgb=mix(rgb,hotc,whiteCore);
   rgb=mix(rgb,vec3(1.0),whiteCore*.6);
   float ember=(1.0-smoothstep(.0,.06,x-front))*eat*step(.001,tint.g);
   rgb=mix(rgb,vec3(1.0,.6,.95),ember*.9);
   rgb*=1.0+Hot*(whiteCore*.9+ember*.6+pinkZone*(1.0-line)*.18);
   alpha=body*tint.a;
  }else if(style>12.5){
   // M1 (검술의 대가) slashes swept along the blade's own path, style 14 the launching cut: crisp anime edges, an
   // ink line on the outer (tip) edge, a bright lavender rim inside it, an electric violet body grading to deep
   // purple with pale speed hairs, one thin white core near the tip edge, a thin magenta line on the inner edge
   // and a glowing ragged edge where the tail is being eaten. Violet carries it; only the core and the eaten edge
   // burn white on the effect layer.
   float aa=max(fwidth(d),.0015)*1.3;
   body=(1.0-smoothstep(1.0-aa,1.0+aa,d))*smoothstep(-aa,aa,1.0-y);
   body*=1.0-split*smoothstep(.46,.54,strand);
   float eat=smoothstep(front-.004,front+.012,x);body*=eat;
   bool big=style>13.5;
   vec3 ink=vec3(.08,.01,.26),lav=big?vec3(.86,.6,1.0):vec3(.78,.5,1.0),mid=big?vec3(.64,.22,1.0):vec3(.55,.17,1.0),deep=vec3(.22,.03,.62),rimc=vec3(.98,.34,.9),hotc=vec3(1.0,.8,1.0);
   rgb=mix(ink,lav,smoothstep(.03,.03+aa*2.0,d));
   rgb=mix(rgb,mid,smoothstep(.1,.22,d));
   rgb=mix(rgb,deep,smoothstep(.5,.9,d));
   float lane=big?.2:.18,wob=.03*sin(x*9.0+sd)+.025*(strand-.5);
   float whiteCore=1.0-smoothstep(big?.035:.025,(big?.035:.025)+aa*2.0+.02*hair,abs(d-lane-wob));
   whiteCore*=smoothstep(.05,.35,x);
   rgb=mix(rgb,hotc,whiteCore);
   rgb=mix(rgb,vec3(1.0),whiteCore*smoothstep(.4,.9,x)*.8);
   float innerLine=(1.0-smoothstep(.0,aa*2.0+.015,abs(d-.94)))*smoothstep(.1,.5,x);
   rgb=mix(rgb,rimc,innerLine*.8);
   rgb=mix(rgb,vec3(.92,.78,1.0),smoothstep(.74,.92,hair)*smoothstep(.24,.34,d)*(1.0-smoothstep(.6,.8,d))*.5);
   float ember=(1.0-smoothstep(.0,.06,x-front))*eat*step(.001,tint.g);
   rgb=mix(rgb,vec3(1.0,.62,.96),ember*.9);
   rgb*=1.0+Hot*(whiteCore*smoothstep(.2,.8,x)+ember*.6+(big?innerLine*.25:0.0));
   alpha=body*tint.a;
  }else if(style>10.5){
   // R (시간을 걷는 자) slashes, style 12 the big final one: crisp anime edges (anti-aliased over a pixel), a dark
   // violet ink line along the outer edge, a bright violet body with pale hairs, a white-hot streak riding inside
   // it and a glowing ragged edge where the tail is being eaten.
   float aa=max(fwidth(d),.0015)*1.3;
   body=(1.0-smoothstep(1.0-aa,1.0+aa,d))*smoothstep(-aa,aa,1.0-y);
   body*=1.0-split*smoothstep(.46,.54,strand);
   float eat=smoothstep(front-.004,front+.012,x);body*=eat;
   bool big=style>11.5;
   vec3 ink=vec3(.12,.02,.34),edge=vec3(.76,.48,1.0),mid=big?vec3(.64,.22,1.0):vec3(.58,.24,.99),deep=vec3(.27,.06,.70),hotc=vec3(1.0,.78,.99);
   rgb=mix(ink,edge,smoothstep(.03,.03+aa*2.0,d));
   rgb=mix(rgb,mid,smoothstep(.12,.26,d));
   rgb=mix(rgb,deep,smoothstep(.6,.97,d));
   float lane=big?.38:.42,wob=.05*sin(x*8.0+sd)+.04*(strand-.5);
   float streak=1.0-smoothstep(.04,.04+aa*2.0+.03*hair,abs(d-lane-wob));
   rgb=mix(rgb,hotc,streak);
   rgb=mix(rgb,vec3(1.0),streak*smoothstep(.2,.85,x));
   rgb=mix(rgb,vec3(.96,.82,1.0),smoothstep(.75,.92,hair)*smoothstep(.15,.3,d)*(1.0-smoothstep(.5,.7,d))*.6);
   float ember=(1.0-smoothstep(.0,.06,x-front))*eat;
   rgb=mix(rgb,vec3(1.0,.62,.96),ember*.9);
   // On the effect layer the streak and the eaten edge burn above white (Hot), so only they turn white-hot.
   rgb*=1.0+Hot*(streak*smoothstep(.15,.7,x)+ember*.6);
   alpha=body*tint.a;
  }
 }else if(Mode==12){
  // UV wraps a real 3D surface. Periodic angular noise keeps its seam invisible.
  float angle=uv.x*6.2831853;
  vec2 flow=vec2(cos(angle),sin(angle))*2.1+vec2(seed+uv.y*1.9,uv.y*4.8-Age*.115);
  float n=fbm(flow),f=noise(flow*2.7+Age*.027);
  float heat=clamp(.68-uv.y*.18+(n-.46)*1.65+.09*sin(angle*3.-Age*.14+uv.y*8.),0.,1.);
  rgb=mix(vec3(.28,.035,.85),vec3(.72,.14,.98),smoothstep(.18,.43,heat));
  rgb=mix(rgb,vec3(.97,.47,.99),smoothstep(.43,.60,heat));
  rgb=mix(rgb,vec3(1.,.91,1.),smoothstep(.60,.78,heat));
  // Broken hot pockets scroll up through the rounded volume, with a dark violet foot.
  rgb=mix(vec3(.26,.055,.65),rgb,smoothstep(.015,.20,uv.y));
  rgb+=vec3(.04,.015,.05)*f;
  alpha=smoothstep(0.,.035,uv.y)*(1.-smoothstep(.94,1.,uv.y))*.96*tint.a;
 }else if(Mode==11){
  // T fire (k-means of the review): white cores, light pink, pink, magenta, vivid violet and a
  // deep violet body, an indigo base and a thin blue-violet line along the silhouette. It turns
  // pinker late in its life.
  vec2 q=vec2(p.x,p.y*.88);
  vec2 flow=uv*3.4+vec2(seed,-Age*.085);
  vec2 warp=vec2(fbm(flow+4.7),fbm(flow-13.2));
  float n=fbm(flow+warp*2.1);
  float lobes=.11*sin(atan(q.y,q.x)*5.0+seed+Age*.08);
  float shape=.8+lobes+(n-.48)*.9-length(q+vec2(sin(Age*.07+seed)*.10,0));
  float body=smoothstep(-.06,.06,shape);
  float holes=smoothstep(.55,.7,fbm(flow*1.45+warp*3.4));
  body*=1.0-holes*smoothstep(.04,.2,shape)*.85;
  float heat=smoothstep(.04,.3,shape)*smoothstep(.25,.62,n+.14);
  float late=smoothstep(36.0,60.0,Age);
  vec3 deep=vec3(.69,.05,.98),vivid=vec3(.82,.17,.98),mag=vec3(.91,.36,.98),pink=vec3(.95,.56,.98),light=vec3(.97,.72,.98),white=vec3(.99,.91,.98);
  // Most of the body sits in the magenta-pink range; light pink only in the hottest cores.
  float h=clamp(max(shape,0.0)*.78+late*.15,0.0,1.0);
  rgb=h<.25?mix(deep,vivid,h/.25):h<.5?mix(vivid,mag,(h-.25)/.25):h<.75?mix(mag,pink,(h-.5)/.25):mix(pink,light,(h-.75)/.25);
  rgb=mix(rgb,white,clamp(pow(heat,3.2)*.45,0.0,1.0));
  rgb=mix(vec3(.23,.12,.82),rgb,smoothstep(.0,.22,uv.y));
  float rim=smoothstep(-.1,-.03,shape)*(1.0-smoothstep(-.03,.02,shape));
  rgb=mix(rgb,vec3(.45,.36,1.0),rim*.7);
  alpha=max(body*.97,rim*.55)*tint.a;
 }else if(Mode==9){
  // T blast cloud: billowy white cumulus with lobed edges, pink rim and a thin lilac outline.
  vec2 flow=p*1.6+vec2(seed,-Age*.03);
  float n=fbm(flow+vec2(fbm(flow+2.3),fbm(flow-4.1))*1.3);
  float r=length(p);
  float edge=.62+n*.42+.08*sin(atan(p.y,p.x)*6.0+seed);
  float body=1.0-smoothstep(edge-.12,edge,r);
  float rim=smoothstep(edge-.3,edge-.08,r);
  rgb=mix(vec3(.99,.99,.97),vec3(.98,.82,.98),rim*.7);
  rgb=mix(rgb,vec3(.84,.69,.98),smoothstep(edge-.1,edge,r)*.8);
  alpha=body*tint.a;
 }else if(Mode==10){
  // T dark orb: churning dark ink ball with glowing lavender bubble holes and a violet rim.
  float r=length(p);vec2 flow=p*2.6+vec2(seed+Age*.05,-Age*.07);
  float n=fbm(flow+vec2(fbm(flow+1.7),fbm(flow-6.3))*1.6);
  float edge=.78+(n-.5)*.28;
  float body=1.0-smoothstep(edge-.06,edge+.06,r);
  rgb=mix(vec3(.086,.024,.5),vec3(.2,.08,.71),smoothstep(.3,.7,n));
  rgb=mix(rgb,vec3(.59,.35,.93),smoothstep(edge-.18,edge,r)*.6);
  float holes=smoothstep(.72,.8,noise(p*7.0+vec2(Age*.06,seed)));
  rgb=mix(rgb,vec3(.95,.86,.96),holes*(1.0-smoothstep(.6,.75,r)));
  alpha=body*tint.a*.96;
 }else if(Mode==13){
  // Dark-spark fire cell (k-means of the T reference): a lobed cartoon billow coloured by tint.g from
  // black smoke through indigo and vivid violet to magenta, pink and white-pink cores; tint.b turns
  // the round billow into a narrow curling wisp.
  float heat=tint.g,wisp=tint.b;
  vec2 q=p;
  q.x+=wisp*sin(q.y*2.6+seed+Age*.11)*.3*(q.y+1.0)*.5;
  q.x*=1.0+wisp*max(0.0,q.y+.2)*1.5;
  vec2 flow=q*1.55+vec2(seed*.37,-Age*.07);
  vec2 warp=vec2(noise(flow+3.7),noise(flow-6.1));
  float n=fbm(flow*1.25+warp*1.9);
  float r=length(q*vec2(1.0,.94));
  float edge=.70+(n-.5)*.62+.07*sin(atan(q.y,q.x)*5.0+seed+Age*.06);
  float body=1.0-smoothstep(edge-.12,edge,r);
  float core=1.0-smoothstep(.0,edge,r);
  float h=clamp(heat+(n-.5)*.3+core*.22*heat-max(0.0,-q.y)*.08,0.0,1.25);
  vec3 c0=vec3(.02,.01,.05),c1=vec3(.21,.09,.37),c2=vec3(.27,.05,.62),c3=vec3(.39,.0,.91),c4=vec3(.60,.0,.94),c5=vec3(.79,.02,.95),c6=vec3(.92,.23,.95),c7=vec3(.96,.89,.98);
  rgb=h<.15?mix(c0,c1,h/.15):h<.32?mix(c1,c2,(h-.15)/.17):h<.47?mix(c2,c3,(h-.32)/.15):h<.62?mix(c3,c4,(h-.47)/.15):h<.78?mix(c4,c5,(h-.62)/.16):h<.95?mix(c5,c6,(h-.78)/.17):mix(c6,c7,clamp((h-.95)/.3,0.0,1.0));
  // A deeper rim keeps overlapping billows apart.
  float rim=smoothstep(edge-.2,edge-.03,r);
  rgb*=1.0-rim*.35*smoothstep(.2,.5,heat);
  // On the effect layer (T) the hottest magenta-pink cores burn above white, so they glow, and the body leans back
  // to magenta: the layer's bloom is the light added over the grey floor, which comes out bluer than the fire.
  rgb*=1.0+Hot*smoothstep(.72,1.1,h)*(1.0-rim*.6);
  rgb.r*=1.0+.13*Hot;
  alpha=body*tint.a;
 }else if(Mode==14){
  // Anime dust puff (on the fire cells' fixed planes): three flat tones (shadowed base, middle, lit top), a lobed
  // crisp edge, a violet cast from the effects' light (tint.g) and holes opening as it thins out (tint.b).
  float thin=tint.b,violet=tint.g;
  vec2 flow=p*1.7+vec2(seed*.31,-Age*.035);
  float n=fbm(flow+vec2(noise(flow+2.9),noise(flow-4.3))*1.4);
  float r=length(p*vec2(1.0,1.08));
  float edge=.74+(n-.5)*.5+.06*sin(atan(p.y,p.x)*6.0+seed);
  float aa=max(fwidth(r),.002)*1.5;
  float body=1.0-smoothstep(edge-aa,edge+aa,r);
  body*=1.0-smoothstep(.52-thin*.3,.58-thin*.3,fbm(flow*1.8+7.0))*thin;
  float lit=p.y+(n-.5)*.45;
  float ab=max(fwidth(lit),.002)*1.5;
  vec3 shade=vec3(.47,.42,.50),midc=vec3(.72,.68,.73),top=vec3(.93,.91,.94);
  rgb=mix(shade,midc,smoothstep(-.18-ab,-.18+ab,lit));
  rgb=mix(rgb,top,smoothstep(.22-ab,.22+ab,lit));
  rgb=mix(rgb,rgb*vec3(.84,.68,1.1),violet);
  alpha=body*tint.a;
 }else if(Mode==16){
  // Lee So-sang's slashes (2.77.0; light outside and ink inside since 2.77.3, as the user put it: "원본은 바깥쪽이 밝은
  // 파랑이고 안쪽이 검정이야"). uv.x tail(0)->head(1) along, uv.y inner(0)->outer(1) across; tint.r = style*16+seed,
  // tint.g = the share eaten (from the tail; the wave from both tips; a stain: how far it has dried away), tint.b = how
  // far it has turned to ink, tint.a = alpha. Styles: 0 a cut (a crescent, widest past its middle): a white-hot cutting
  // edge outermost in bright blue light, the band deepening inward through royal blue and navy to black brush ink along
  // its inner edge (ragged as a dry brush's); as it turns to ink the ink spreads out toward the edge until a thin bright
  // rim is left, then blots open in it and it breaks up; 1 the ring (even; the same, cracking into black lines as it
  // dries); 2 the thrust's beam (pointed both ends): a white core in blue light; 3 the thrown slash (a full crescent): a
  // white leading edge, a royal blue body streaked the way it flies, black ink at its back edge dripping off it, spreading
  // forward as it dries; 4 a stroke of ink drawn in the air (thick and thin, tapered at both ends, dry gaps); 5 a stain
  // of ink soaked into the ground (the uv square: a soft see-through blot darker in its middle, a few drops round it,
  // shrinking and fading as it dries); 6 the thin cut rising back (its widest part nearer its head); 7 a thin bright arc
  // (as the beam, in a band); 8-11 the crescents (below); 12 a splash of ink on the ground (the uv square: sharp-edged,
  // see-through black, arms flung out, a ragged gap or two, drops round it). On the effect layer the light burns past white and
  // blooms; the ink adds none.
  float code=floor(tint.r*255.0+.5),style=floor(code/16.0),sd=mod(code,16.0)*3.7;
  float x=clamp(uv.x,0.0,1.0),y=clamp(uv.y,0.0,1.0),erase=tint.g,ink=tint.b;
  vec3 inkc=vec3(.008,.010,.034),navy=vec3(.026,.046,.21),deep=vec3(.05,.16,.80),blue=vec3(.16,.46,1.0),pale=vec3(.66,.88,1.0),edge=vec3(.96,.99,1.0);
  if(style>11.5&&style<12.5){
   // 12 a splash of ink on the ground (2.79.2, the user's video of the whole combo at half speed: big see-through black
   // splashes with sharp edges, arms flung out, holes and drops round them; they spread in a blink, then fade and break
   // up as they dry). The uv square; tint.g how far it has dried.
   vec2 q=uv*2.0-1.0;float r=length(q);
   vec2 wq=q+vec2(fbm(q*2.0+sd)-.5,fbm(q*2.0-sd+7.0)-.5)*.34;
   float shape=.40+.18*(fbm(wq*3.0+sd*1.7)-.5)-length(wq);
   for(int i=0;i<7;i++){
    float fi=float(i);
    float a0=6.2832*hash(vec2(fi*1.3,sd+.7)),reach=.42+.36*hash(vec2(sd*1.1,fi+3.0)),wid=.05+.07*hash(vec2(fi+5.0,sd*.9));
    vec2 dir=vec2(cos(a0),sin(a0));
    float tt=clamp(dot(q,dir)/reach,0.0,1.0);
    // an arm flung out, tapering, its edge a little ragged
    shape=max(shape,wid*pow(1.0-tt,.8)+.006-length(q-dir*(tt*reach))+.025*(noise(q*11.0+fi*3.1)-.5));
    // and a drop flung off its end
    shape=max(shape,(.022+.03*hash(vec2(fi,sd*2.1)))-length(q-dir*(reach+.05+.07*hash(vec2(sd,fi*2.7)))));
   }
   for(int i=0;i<14;i++){
    float fi=float(i),a1=6.2832*hash(vec2(fi+20.0,sd)),rr=.5+.4*hash(vec2(sd,fi+40.0));
    shape=max(shape,(.012+.03*hash(vec2(fi+60.0,sd)))-length(q-vec2(cos(a1),sin(a1))*rr));
   }
   // a gap or two where the ground shows through: small and ragged (round ones read as eyes)
   for(int i=0;i<2;i++){
    float fi=float(i);vec2 h=(vec2(hash(vec2(fi+80.0,sd)),hash(vec2(sd,fi+90.0)))*2.0-1.0)*.3;
    vec2 e=(wq-h)*vec2(1.0,.45+.5*hash(vec2(fi+97.0,sd)));
    shape=min(shape,length(e)*(1.0+.9*(noise(q*9.0+fi*5.3+sd)-.5))-(.012+.022*hash(vec2(fi+95.0,sd))));
   }
   // drying: it fades, nibbled away from its edges and thin parts (holes opening in its middle read as eyes)
   shape-=erase*(.03+.17*fbm(q*4.5+sd))*(.35+.65*smoothstep(.12,.55,r));
   float aa=max(fwidth(shape),.002)*1.2;
   float cov=smoothstep(-aa,aa,shape),rim=(1.0-smoothstep(0.0,.05,shape))*smoothstep(.2,.45,r);
   rgb=mix(inkc,navy*.45,.3*fbm(q*5.0-sd));
   // see-through, a little darker where the ink gathers at its edge
   alpha=cov*(.74+.18*rim)*(.86+.14*fbm(q*7.0+sd*2.0))*(1.0-smoothstep(.94,1.0,r))*tint.a;
  }else if(style>12.5){
   // 13 the ink of Lee So-sang's H lying on the ground as it gathers (2.82.6, user request: "잉크 모였다가 퍼지는게 너무
   // 부자연스러운데 이거만 예쁘게 다듬어줘"), a liquid sheet: uv is the offset from its middle over its radius (its edge near 1,
   // wavering slowly), tint.g the formation's clock (seconds over 2), tint.b how far it has soaked in (0..127). Dense and
   // black inside, thin and glassy toward its edge (deep blue showing through), mottled, with streaks flowing in toward its
   // middle, turning as they go; its edge a thin bright line, white at its heart, in a wide blue glow just outside it.
   // 14 the formation's blue ground (seeds 0-7: the same square, solid to .88 of it, fading to its rim; tint.a; its middle
   // cleared by the spreading whirlpool's front, tint.g its reach over 1.25 of the square, 2.82.10), or (seeds 8-15) the
   // navy water lying round its middle as it stands, soft dark patches going round in it (2.82.13, below).
   // 15 the whirlpool once the gathered ink has gone into the middle and burst there (2.82.7, user request: "잉크가 모였다가
   // 사라지고 검정색 물줄기가 회오리 처럼 흐르는거임", "이거처럼 이펙트가 크게 나옴" with their still of the video): streams of
   // black water swirling out of the middle as wide as the ink first soaked in. uv the offset from its middle over its outer
   // radius (the swirl spreads with it, as the video's does), tint.g the formation's clock, tint.b its eye (the black
   // middle no stream crosses: a share of its radius, over .6). Seen from above its streams curl counterclockwise as they
   // run out (the video put back on the ground plane: some ten degrees off the round) and all of it turns slowly the same
   // way; theta here grows clockwise seen from above, so a stream keeps th+TWIST*lr. Crescents of a see-through pale
   // blue on black water (2.82.8: no white, some third of 2.82.7's brightness), broken along their length so they taper
   // at both ends; fine streaks running out along them; a blue sheen round the eye; its rim soft and ragged, the streams'
   // tails reaching a little past it. As the swords strike it spreads out from its middle to the rim, turning blue, and
   // turns into the blue smoke lying along the rim, which thins away (2.82.10; the smoke since 2.82.8).
   vec2 q=uv;float r=length(q);
   if(style>14.5){
    float tsec=tint.g*2.0,eye=tint.b*.6;
    // as the swords strike it spreads out and turns into the smoke at the rim (2.82.10, user request: "퍼진다는 느낌이 안
    // 나잖아 ... 소용돌이가 퍼져서 연기가 되는거처럼 만들어"): a clean front opens in its middle and runs out to the rim
    // (SwordFormation.front: SPREAD0 1.0 s to SPREAD1 1.33 s, easing in, fast through the middle and slowing, RIM_R 11.2
    // blocks over its radius 14.5), the water and its streams squeezed out between it and its edge (pushed SPREAD_PUSH .06
    // further out): rO is
    // the radius each bit of it was at before it spread, so the black eye piles up just outside the front and the
    // crescents pack in ahead of it.
    float fr=pow(smoothstep(1.0,1.33,tsec),.7)*(11.2/14.5);
    float outerE=1.0+.06*smoothstep(1.0,1.33,tsec);
    float rO=max(r-fr,0.0)/max(outerE-fr,1e-3);
    float clean=fr>1e-4?smoothstep(fr-.012,fr+.003,r):1.0;
    vec2 dir=r>1e-4?q/r:vec2(1.0,0.0);
    vec2 qW=dir*rO;float rW=rO;
    float th=r>1e-4?atan(q.y,q.x):0.0,lr=log(max(rW,.035)),aa=max(fwidth(rW),.0015);
    // the eye: black, round, its edge wavering only a little (2.82.12: the video's eye is a clean round; 2.82.7-2.82.11's
    // wavered lumpily)
    float eyeR=eye*(1.0+.012*sin(3.0*th+sd+.8*tsec)+.006*sin(5.0*th-1.3*sd-1.4*tsec));
    float inEye=eye>.01?1.0-smoothstep(eyeR-aa,eyeR+aa,rW):0.0;
    // the streams (TWIST 5.2, turning SPIN .38 a second; SwordFormation.WHIRL_TWIST, WHIRL_SPIN)
    float warp=fbm(qW*2.3+vec2(sd*.7,-sd))-.5;
    float psi=th+5.2*lr+.38*tsec+.6*warp;
    // (how fast psi changes across a pixel, leaving out the seam theta has behind the middle)
    float psiW=fwidth(5.2*lr+.6*warp)+fwidth(r)/max(r,1e-3);
    // where its light gathers: two broad arms and three thinner ones, each sharp on its inner side and trailing off
    // outward, broken by looser families running across them, the pieces carried out along the streams
    float p2=fract((2.0*psi+sd)/6.2831853),p3=fract((3.0*psi+1.7*sd+2.1)/6.2831853);
    float e2=smoothstep(0.0,.07,p2)*(1.0-smoothstep(.07,.62,p2)),e3=smoothstep(0.0,.08,p3)*(1.0-smoothstep(.08,.5,p3));
    float cut2=smoothstep(-.75,.45,cos(2.0*(th-1.3*lr)+2.4*tsec+.7*sd));
    float cut3=smoothstep(-.45,.7,cos(3.0*(th-1.0*lr)+3.1*tsec+1.3*sd+1.0));
    // only between the eye and the rim (their tails a little past where the water thins out), not in the very middle
    float band=smoothstep(eyeR+.004,eyeR+.05,rW)*(1.0-smoothstep(.86,1.04,rW))*smoothstep(.04,.14,rW);
    float gath=max(e2*cut2,.8*e3*cut3)*band;
    // its light (2.82.12, user request: "소용돌이를 포현하는 하늘색 부분이 너무 두껍다고 해야하나 결이 더 많아야 할듯",
    // "그냥 영상 따라서 만드는게 너는 힘든가?"; the video: thin swirling lines of pale light, many of them, gathering into
    // the arms; 2.82.7-2.82.11 drew thick crescents): thin streaks along the streams, four families finer and finer,
    // broken into dashes tapering at both ends that run out along them, the finer ones mostly where the arms gather
    float u=lr-.55*tsec;
    float sA=streaks(8.0*psi/6.2831853,8.0*psiW/6.2831853,u,8.0,.085,2.2,.85,sd);
    float sB=streaks(14.0*psi/6.2831853+.37,14.0*psiW/6.2831853,u*1.15+.4,14.0,.07,3.0,.75,sd+3.3);
    float sC=streaks(24.0*psi/6.2831853+.71,24.0*psiW/6.2831853,u*1.3+.9,24.0,.06,4.0,.7,sd+7.1);
    float sD=streaks(40.0*psi/6.2831853+.13,40.0*psiW/6.2831853,u*1.45+1.3,40.0,.05,5.0,.65,sd+11.3);
    float lines=(1.0-(1.0-sA*(.6+.4*gath))*(1.0-sB*(.3+.7*gath))*(1.0-sC*(.2+.8*gath))*(1.0-.8*sD*(.1+.9*gath)))*band;
    // its water a dark navy (the video's), the eye darker; it settles a little toward a dark blue as the swords strike,
    // a faint see-through blue haze where the arms gather, and it turns a dark blue as it spreads (into smoke)
    float settle=smoothstep(.95,1.2,tsec),conv=smoothstep(1.1,1.33,tsec);
    float mott=fbm(qW*3.1+vec2(-sd,sd)+tsec*vec2(.11,-.08));
    vec3 water=mix(vec3(.012,.018,.07),vec3(.03,.05,.18),.35+.45*mott);
    water=mix(water,mix(inkc,navy*.6,.3*mott),inEye);
    water=mix(water,navy*.9,.25*settle*(1.0-inEye));
    water=mix(water,vec3(.18,.27,.62),.45*gath);
    water=mix(water,vec3(.05,.09,.3),.5*conv);
    // the streaks a pale periwinkle, no white (2.82.8, user request: "회오리 하늘색 부분 더 더 연하게"), a deeper blue
    // where faint; the water showing through them
    vec3 lc=mix(vec3(.3,.42,.86),vec3(.66,.78,.98),smoothstep(.2,.8,lines));
    lc=mix(lc,vec3(.2,.32,.7),.5*conv);
    rgb=mix(water,lc,min(1.0,lines*1.05));
    float sheen=eye>.01?exp(-max(rW-eyeR,0.0)/.05)*(1.0-inEye)*band:0.0;
    rgb=mix(rgb,mix(deep,blue,.5),sheen*.2);
    // dense in the eye, thinning out to a soft, ragged rim; the ground clean behind the front; thinning as it turns into
    // smoke, gone once what reached the rim has (SwordFormation.whirlShown: WATER_OUT0 1.27 s to WATER_OUT1 1.45 s)
    float n=fbm(qW*2.0+vec2(sd*1.3,sd*.7));
    float rim=1.0-smoothstep(.7,1.0,rW+.14*(n-.5));
    float shownW=1.0-smoothstep(1.27,1.45,tsec);
    float a=max(mix(.86,.95,inEye)*rim,lines*.75)*clean*(1.0-.3*conv)*shownW;
    float glow=(lines*(.28+.2*conv)+sheen*.15+gath*.04)*clean*shownW;
    // its front as it runs out: a thin, faint line of blue where the clean ground meets the water, ragged along it (the
    // edge of the water, not a lit rim round a puddle: 2.82.6's), and a thin blue mist lifting just behind it (the
    // whirlpool turning into smoke as it goes; the puffs rise off the water ahead of it)
    float fK=smoothstep(1.0,1.03,tsec)*(1.0-smoothstep(1.3,1.42,tsec));
    if(fK>0.0){
     float d=r-fr,rag=.6+.4*fbm(dir*5.0+vec2(sd,tsec*1.3));
     float wA=(d<0.0?exp(d/.035):0.0)*fK*rag*.2;
     float aw=a;a=wA+aw*(1.0-wA);rgb=(vec3(.05,.09,.3)*wA+rgb*aw*(1.0-wA))/max(a,1e-4);
     float line=exp(-abs(d)/.0035)*fK*rag;
     float lA=min(1.0,line*.4);aw=a;a=lA+aw*(1.0-lA);rgb=(vec3(.18,.3,.72)*lA+rgb*aw*(1.0-lA))/max(a,1e-4);
     glow+=line*.2+wA*.05;
    }
    // the smoke at the rim (2.82.8, user request: "끝으로 퍼지면서 이상한 아우라가 테두리 부분으로 퍼지면서 사라지거든", the
    // video's smoke round the formation's rim, read on a marked still and "일단 그렇게 해봐"; 2.82.10: "연기 파란색이여함
    // 연기를 더 진하게": blue and thick, what the spreading whirlpool turns into as it reaches the rim): along the rim
    // (SwordFormation.RIM_R, 11.2 blocks, out to RIM_OUT 12.2 as it goes), fixed in the world (drawn from q), on the
    // ground, in wisps streaming round it the way the whirlpool turned (counterclockwise seen from above). It comes up as
    // the whirlpool reaches the rim (SMOKE_IN 1.22 s to SMOKE_FULL 1.36 s), drifts a little further out and thins away by
    // patches, its thin wisps first (SMOKE_OUT0 1.64 s to SMOKE_OUT1 1.88 s). The smoke billowing up off it is
    // SwordFormation.puffs (mode 17).
    float life=smoothstep(1.22,1.36,tsec)*(1.0-smoothstep(1.64,1.88,tsec));
    if(life>0.0){
     float lr0=log(max(r,.035));
     float ca=cos(.45*tsec),sa_=sin(.45*tsec);
     vec2 dS=vec2(ca*dir.x-sa_*dir.y,sa_*dir.x+ca*dir.y);
     float rimR=mix(11.2,12.2,smoothstep(1.36,1.88,tsec))/14.5;
     // swelling and thinning round the rim, ragged at its edges
     float lo=fbm(dS*1.6+vec2(sd*.6,4.1)),wv=fbm(dS*2.6+vec2(sd*.9,1.7));
     float hw=(1.5+.5*smoothstep(1.36,1.72,tsec))*(.65+.7*lo)/14.5;
     float dr=(r-rimR-.05*(wv-.5))/max(hw,1e-3);
     float body=exp(-dr*dr*1.2);
     // soft wisps streaming round it the way the whirlpool turned, smeared along it like mist
     float wisp=fbm(dS*3.4+vec2(lr0*9.0,-lr0*6.0)+sd);
     float strk=.5+.5*cos(17.0*lr0+2.0*th-1.2*tsec+2.6*wisp);
     float smoke=body*(.5+.5*strk)*(.55+.45*wisp);
     // thinning away by patches, its thin wisps first
     float nS=fbm(dS*3.3+vec2(lr0*5.0,sd*1.9));
     float gone=smoothstep(1.62,1.88,tsec);
     smoke*=(1.0-smoothstep(nS-.14,nS+.14,gone*1.2-.1+.15*(1.0-smoke)))*life;
     // a dark navy, see-through and not to catch the eye, barely lit (2.82.12, user request: "더 진하고 어두운 남색",
     // "연기는 눈에 잘 안 띄이게 만들어야함 살짝 투명하고"; 2.82.10-2.82.11 a bright, thick electric blue)
     vec3 sc=mix(vec3(.025,.04,.15),vec3(.07,.11,.33),smoothstep(.3,.8,smoke));
     float sA=min(.85,smoke*.8);
     float aw=a;a=sA+aw*(1.0-sA);
     rgb=(sc*sA+rgb*aw*(1.0-sA))/max(a,1e-4);
     glow+=smoke*.05;
    }
    rgb*=1.0+Hot*glow;
    alpha=a*tint.a;
   }else if(style>13.5){
    if(mod(code,16.0)>7.5){
     // seeds 8-15: the water lying on the formation's ground as it stands (2.82.13, user request: "소용돌이가 끝나고 H가
     // 끝날때까지 바닥 지속적으로 흐르는 물 같은 이펙트를 만들어야함", "물 색은 남색", "원본은 그냥 검정색 결이 순환하는데
     // 너가 만든건 소용돌이야"; then the video and the build side by side, read and confirmed: the water leaves the middle
     // bare and lies round it, faint, the ground showing well through it; darker patches flow in it, soft with clear
     // edges, not brushed streaks; strands of pale water only at the far rim): a see-through navy round the formation's
     // middle, its inner edge soft and wandering; soft dark patches in it going round and round (no arms curling in or
     // out, as a whirlpool's); a few thin pale strands along the rim. All of it turns counterclockwise seen from above
     // (theta here grows clockwise, so the turn is added to it). tint.g and tint.b the turn, sixteen bits of a whole one
     // (SwordFormation.flowPhase: drawn on the circle, so a whole turn brings it back exactly); uv the offset over its
     // radius (SwordFormation.FLOW_R, 11 blocks); tint.a how strongly it lies.
     float ph=(floor(tint.g*255.0+.5)*256.0+floor(tint.b*255.0+.5))*(6.2831853/65536.0);
     float th=r>1e-4?atan(q.y,q.x):0.0,ang=th+ph;
     vec2 cA=vec2(cos(ang),sin(ang)),cB=vec2(cos(2.0*ang+1.3),sin(2.0*ang+1.3));
     float edge=1.0-smoothstep(.9,1.0,r);
     // the navy water round the middle, its inner edge soft and wandering
     float ri=.52+.16*(fbm(cA*1.3+vec2(sd*.4,2.9))-.5);
     float ring=smoothstep(ri-.07,ri+.09,r)*edge;
     float m=fbm(cA*1.1+vec2(r*2.2,sd*.6));
     vec3 navyW=mix(vec3(.04,.065,.26),vec3(.07,.1,.34),smoothstep(.35,.75,m));
     float wA=.46*ring*(.85+.15*m)*tint.a;
     // soft dark patches going round in it, clear at their edges
     float pf=fbm(cA*1.5+vec2(r*3.2,1.7+sd*.37))+.1*(fbm(cB*2.6+vec2(r*5.0,-sd))-.5);
     float body=smoothstep(.53,.58,pf);
     float bA=.6*body*(.8+.2*smoothstep(.58,.7,pf))*smoothstep(ri-.02,ri+.12,r)*edge*tint.a;
     vec3 blk=vec3(.012,.016,.05);
     // a few thin strands of pale water along the far rim (some .06 of a block either side of a contour wherever it
     // lies: its distance found from the field's slope on the screen against the ground's)
     float sf=fbm(cA*2.1+vec2(r*7.0,sd*.7+7.3));
     float px=length(vec2(dFdx(r),dFdy(r)))*11.0,gf=length(vec2(dFdx(sf),dFdy(sf)));
     float df=abs(sf-.5)/max(gf,1e-5)*px;
     float strand=exp(-df*df/.004)*smoothstep(.3,.65,fbm(cA*4.0+vec2(r*9.0,sd)))*smoothstep(.8,.88,r)*(1.0-smoothstep(.96,1.0,r));
     float sA=.38*strand*tint.a;
     // the navy water, the patches over it, the strands over all
     vec3 col=navyW;float a=wA;
     col=(blk*bA+col*a*(1.0-bA))/max(bA+a*(1.0-bA),1e-4);a=bA+a*(1.0-bA);
     col=(vec3(.62,.76,1.0)*sA+col*a*(1.0-sA))/max(sA+a*(1.0-sA),1e-4);a=sA+a*(1.0-sA);
     // the strands catch the light a little on the effect layer
     rgb=col*(1.0+Hot*.25*sA/max(a,1e-4));
     alpha=a;
    }else{
     // (2.82.10: its middle cleared by the spreading whirlpool's front, tint.g its reach over 1.25 of the square)
     rgb=vec3(.043,.078,.275);
     float hole=tint.g*1.25,keep=tint.g>0.0?smoothstep(hole-.02,hole+.03,r):1.0;
     alpha=(1.0-smoothstep(.88,1.0,r))*keep*tint.a;
    }
   }else{
    float tsec=tint.g*2.0,share=min(floor(tint.b*255.0+.5),127.0)/127.0;
    vec2 dir=r>1e-4?q/r:vec2(1.0,0.0);
    float th=r>1e-4?atan(q.y,q.x):0.0,lr=log(max(r,.015)),aa=max(fwidth(r),.0015);
    // its edge wavers: slow lobes drifting round it and finer ones (a small pool reads rounder: they are shares of it)
    float wob=.042*sin(3.0*th+sd+1.1*tsec)+.026*sin(5.0*th-1.7*sd-1.9*tsec)+.014*sin(9.0*th+2.3*sd+3.3*tsec)
             +.06*(fbm(dir*1.4+vec2(sd*.37,tsec*.6))-.5);
    float edgeR=1.0+wob,d=r-edgeR;
    float depth=smoothstep(0.0,.11,-d),inside=1.0-smoothstep(-aa,aa*.5,d);
    float mott=fbm(q*2.6+vec2(sd,-sd)+tsec*vec2(.13,-.09));
    // the streaks, in toward the middle, turning round it as they go
    float sw=th-1.1*lr+.7*tsec;
    float st=.5+.5*sin(13.0*sw+(4.0*lr-7.0*tsec)+2.6*fbm(dir*1.9+vec2(lr*.5,sd)));
    // broken into dashes running in along them, never whole spokes
    float dash=.5+.5*sin(9.0*lr+14.0*tsec+3.0*sin(7.0*sw+sd));
    float streak=smoothstep(.84,.97,st)*smoothstep(.25,.75,dash)*(.55+.45*mott)*smoothstep(.05,.25,r);
    // soaking in, in patches all over at once, never spreading out from one place
    float n=fbm(q*2.2+vec2(sd*1.3,sd*.7));
    float cover=smoothstep(n-.035,n+.035,share*1.3-.15);
    vec3 thinC=mix(navy*1.5,deep*.75,.45);
    rgb=mix(thinC,mix(inkc,navy*.7,.3*mott),depth);
    rgb=mix(rgb,mix(deep*.8,navy*1.5,depth),streak*.3);
    float a=inside*cover*mix(.62,.93,depth)*(.9+.1*mott);
    // the edge lit: a wide blue glow outside, a thin bright line along the ink, white at its heart
    float glow=d>0.0?exp(-d/.075)*smoothstep(0.0,aa*2.0+.004,d)*cover:0.0;
    if(d>0.0){rgb=mix(blue,deep,.4);a=.42*glow;}
    float lw=max(aa*1.5,.01);
    float line=(1.0-smoothstep(lw*.4,lw*1.7,abs(d)))*cover;
    float core=1.0-smoothstep(0.0,lw*.6,abs(d));
    rgb=mix(rgb,mix(pale,edge,core),line);a=max(a,line*.95);
    float lit=line*2.6+glow*.45;
    rgb*=1.0+Hot*lit;
    alpha=a*tint.a;
   }
  }else if(style>4.5&&style<5.5){
   // a stain soaked into the ground: a soft, lumpy, see-through blot, darker in its middle, a few drops round it
   vec2 q=uv*2.0-1.0;float r=length(q);
   vec2 wq=q+vec2(fbm(q*1.6+sd)-.5,fbm(q*1.6-sd+4.0)-.5)*.55;
   float shape=.50+.30*(fbm(wq*2.3+sd*1.3)-.5)-length(wq);
   for(int i=0;i<6;i++){
    float fi=float(i);vec2 c=(vec2(hash(vec2(fi,sd*2.3)),hash(vec2(sd,fi*1.7)))*2.0-1.0)*.8;
    shape=max(shape,(.045+.05*hash(vec2(fi+9.0,sd)))-length(q-c));
   }
   shape-=erase*.42;
   float soft=smoothstep(-.09,.10,shape),core=smoothstep(.02,.30,shape);
   float mottle=.82+.18*fbm(q*6.0+sd);
   rgb=mix(navy*.55,inkc,core);
   alpha=soft*(.45+.5*core)*mottle*(1.0-smoothstep(.86,1.0,r))*tint.a;
  }else if(style>3.5&&style<4.5){
   // a stroke of ink drawn in the air: thick and thin, tapered at both ends, ragged, with dry gaps; eaten from its tail
   float w=pow(max(sin(3.14159*x),0.0),.45)*(.45+.55*fbm(vec2(x*4.0+sd,2.0)));
   w=max(w,.02);
   float d=abs(y-.5)*2.0/w;
   float aa=max(fwidth(d),.0015)*1.4;
   float hair=noise(vec2(x*40.0+sd,y*6.0));
   float front=erase*1.05-.03,keep=smoothstep(front,front+.03,x);
   float body=1.0-smoothstep(.70-aa,.98+aa,d+.24*(hair-.5));
   body*=1.0-smoothstep(.80,.96,noise(vec2(x*22.0+sd,y*3.0)))*.75;
   rgb=mix(inkc,navy*.75,smoothstep(.45,1.0,d)*.6);
   alpha=body*keep*tint.a;
  }else{
   // 0 a spin cut (2.78.0, the video from above: a thin line at its tail, where the blade began, thickening all the way to
   // its rounded head), 1 the ring, 2 the beam, 3 the thrown slash, 6 the thin cut rising back, 7 a thin bright arc
   float hd=smoothstep(.86,1.0,x);
   // 8 hit 1's great crescent (2.79.1, the user's video): thin only where it began, thick from its middle to its head
   // 9 hit 1's crescent draft (2.79.2): pointed at both tips, thin along the start, thickest at its belly (70% along)
   // 10 hit 2's front cut (2.79.2 draft): the same crescent, light all through while fresh, as the ring was
   // 11 hit 3's cuts down (2.79.2 draft): the same crescent thickest near its start
   float w=(style>10.5&&style<11.5)?pow(max(sin(3.14159*pow(1.0-x,1.94)),0.0),1.6)
          :(style>8.5&&style<10.5)?pow(max(sin(3.14159*pow(x,1.94)),0.0),1.6)
          :(style>7.5&&style<8.5)?(.06+.94*smoothstep(0.0,.5,x))*(1.0-hd*hd)
          :style<.5?(.08+.92*pow(x,1.3))*(1.0-hd*hd)
          :style<1.5?.9+.1*sin(x*31.0+sd)
          :style<2.5?pow(max(sin(3.14159*x),0.0),.5)
          :style<3.5?pow(max(sin(3.14159*x),0.0),.36)
          :style<6.5?pow(max(sin(3.14159*pow(x,.62)),0.0),.55)
          :pow(max(sin(3.14159*x),0.0),.3);
   w=max(w,.02);
   bool beam=(style>1.5&&style<2.5)||(style>6.5&&style<7.5),wave=style>2.5&&style<3.5,ring=(style>.5&&style<1.5)||(style>9.5&&style<10.5),cracks=style>.5&&style<1.5;
   float d=beam?abs(y-.5)*2.0/w:(1.0-y)/w;
   float aa=max(fwidth(d),.0015)*1.4;
   float strand=fbm(vec2(x*6.0+sd,y*24.0)),hair=noise(vec2(x*12.0+sd,y*90.0)),streak=noise(vec2(x*70.0+sd,y*2.5));
   // eaten from the tail with a ragged edge (the thrown slash from both tips)
   float front=erase*1.1-.05+(strand-.5)*.15;
   float keep=wave?smoothstep(front,front+.04,1.0-abs(x*2.0-1.0)):smoothstep(front,front+.03,x);
   // breaking up: a few blots open as it turns to ink, most as it is eaten (2.77.6: they opened all over as soon as it
   // was all ink, so the black was gone the moment it was black)
   float blob=fbm(vec2(x*6.5+sd*1.7,y*3.2+sd*.6)),open=ink*.35+erase*.5;
   float holes=1.0-smoothstep(1.0-open,1.0-open+.07,blob)*step(.02,ink);
   float body,lit;
   if(beam){
    float core=1.0-smoothstep(.05,.2,d),glow=1.0-smoothstep(.2,1.0,d);
    rgb=mix(blue,pale,smoothstep(.6,.2,d));rgb=mix(rgb,edge,core);
    // a thin line of ink along its sides (2.77.6: a touch of black in the light)
    float rim=smoothstep(.55,.7,d)*(1.0-smoothstep(.86,.98,d));
    rgb=mix(rgb,inkc,rim*.75);
    body=max(max(core,glow*.75),rim*.5)*(1.0-ink);
    bool thin=style>6.5;
    lit=thin?core*2.0+glow*.5:core*3.6+glow*1.1*(1.0-rim);holes=1.0;
   }else{
    // the light outermost (a white-hot edge, wide while the cut is fresh, bright blue, royal blue), the ink inside it:
    // filling the thick parts and barely touching the thin (2.78.0, the video from above), spreading out as it dries
    // (the ring is a broad white band as it is drawn, the video's, and narrows to a blue one within a tenth of a second)
    float whiteEnd=wave?mix(.11,.04,ink):ring?mix(.6,.08,smoothstep(0.0,.12,ink)):mix(.24,.06,smoothstep(0.0,.3,ink));
    float spread=1.0-(1.0-ink)*(1.0-ink);
    // (the ring is light all through while fresh, the video's: its ink comes only as it dries)
    float inkAt=(wave?mix(.85,.4,spread):ring?mix(.9,.3,spread):max(.2,.92-.55*smoothstep(.15,.9,w)-.25*spread))+.08*(strand-.5)+.03*(streak-.5);
    // the band thins as it dries, its inner edge drawing out toward the light (the video: the blue narrows to a line
    // before it goes); the inner edge a dry brush's, ragged, strands falling short; the thrown slash's back edge drips
    float limit=wave?1.0:mix(1.0,.45,smoothstep(.55,1.0,ink));
    float drip=wave?.30*smoothstep(.55,.95,noise(vec2(x*46.0+sd,3.0)))+.06*strand:0.0;
    float rag=wave?0.0:.13*smoothstep(.45,.9,strand)+.05*hair;
    float inBand=1.0-smoothstep(limit+drip-rag-aa*2.0,limit+drip-rag+aa*2.0,d);
    float outBand=smoothstep(-aa,aa,d);
    float inkMask=smoothstep(inkAt-aa*2.0,inkAt+aa*2.0+.03,d);
    vec3 g=mix(edge,pale,smoothstep(whiteEnd*.55,whiteEnd+.03,d));
    g=mix(g,blue,smoothstep(whiteEnd+.02,whiteEnd+.15,d));
    g=mix(g,deep,smoothstep(whiteEnd+.12,max(whiteEnd+.2,inkAt-.08),d));
    g=mix(g,navy,smoothstep(inkAt-.18,inkAt,d));
    // the thrown slash's body streaked the way it flies
    if(wave)g=mix(g,deep*.8,smoothstep(.55,.85,streak)*smoothstep(whiteEnd+.1,whiteEnd+.3,d)*.55);
    // fine black hairs of the brush drawn through the light along it, more as it turns to ink (2.77.6: a touch of black
    // in the light too; the white edge kept clear)
    float hairN=noise(vec2(x*8.0+sd*2.3,y*30.0));
    g=mix(g,inkc,smoothstep(.64,.82,hairN)*smoothstep(whiteEnd+.06,whiteEnd+.18,d)*(.4+.35*ink));
    // the black softens toward navy as the band dries down to a line
    vec3 inkNow=mix(inkc,navy*1.2,smoothstep(.75,1.0,ink)*.55);
    rgb=mix(g,inkNow,inkMask);
    // the brush's grain in the ink: navy strands along it
    rgb=mix(rgb,navy*1.3,inkMask*smoothstep(.6,.86,streak)*.55);
    // the ring cracks into black lines across it as it dries
    float crack=0.0;
    if(cracks){float c=abs(sin((x*11.0+sd)*3.14159+2.4*fbm(vec2(x*4.0+sd,d*1.5))));crack=(1.0-smoothstep(0.0,.07+aa*3.0,c))*smoothstep(.2,.5,ink);}
    rgb=mix(rgb,inkc,crack);
    // opaque at the edge, glassier in the light's middle, the ink solid
    float a=mix(.97,.82,smoothstep(whiteEnd,inkAt,d)*(1.0-ink*.4));
    // the thrown slash's body lets the ground show through (2.78.0, the video from above)
    if(wave)a*=mix(.62,.9,max(ink,1.0-smoothstep(.0,.1,d)));
    body=outBand*inBand*max(mix(a,.97,inkMask),crack);
    // the light dims as it dries; the white edge burns hottest (not so hot that it blooms over the crescent's shape)
    float edgeLine=1.0-smoothstep(whiteEnd*.6,whiteEnd+.04,d);
    lit=(1.0-max(inkMask,crack))*(1.0-ink*.75)*(edgeLine*2.6+(1.0-edgeLine)*.45);
   }
   body*=keep*holes;
   rgb*=1.0+Hot*lit;
   alpha=body*tint.a;
  }
 }else if(Mode==17){
  // Blue mist standing round Lee So-sang's sword formation (2.82.11, user request with their marked screenshot of the
  // game: "아니 내가 사진에 표시한 애들처럼 나오게 해달라고 연기를": a see-through, deep blue mist hugging the ground round
  // the formation's rim, in streaks running round it, rising a little before the mountains' feet; 2.82.10 drew it as
  // opaque billows). A ring of upright quads fixed in the world (KagenoFire.mist): uv.x the share of a turn round it
  // (so its noise is drawn on the circle and has no seam), uv.y the share of its height; tint.g how far it has thinned
  // away. Streaks lying along it, running round the way the whirlpool turned (counterclockwise seen from above) and
  // rising; dense at its foot, wisps up to an uneven top; a deep electric blue with brighter streaks, glowing a little
  // on the effect layer, never white.
  float th=uv.x*6.2831853,v=clamp(uv.y,0.0,1.0),thin=tint.g,tm=Age*.05;
  vec2 rg=vec2(cos(th+.3*tm),sin(th+.3*tm));
  float big=fbm(rg*2.6+vec2(seed*.37,1.3));
  float mid=fbm(rg*8.0+vec2(v*2.4-tm*.8,seed*.53));
  float fine=noise(rg*23.0+vec2(v*6.0-tm*1.7,-seed));
  float lay=.5+.5*sin(v*9.0-tm*2.2+mid*5.5+big*4.0);
  float top=.42+.5*big+.3*(mid-.5);
  float body=smoothstep(0.0,.05,v)*(1.0-smoothstep(top*.45,top,v));
  float dens=body*(.5+.5*lay)*(.55+.45*mid)*(.8+.4*fine);
  dens*=1.0-smoothstep(.45-thin*.4,.6-thin*.4,fbm(rg*6.0+vec2(v*3.0+tm*.3,seed*.7)))*thin;
  // (2.82.12, user request: "더 진하고 어두운 남색", "연기는 눈에 잘 안 띄이게 만들어야함 살짝 투명하고": a dark navy,
  // see-through, unlit; 2.82.11 a bright electric blue near opaque at its foot, its light added again)
  rgb=mix(vec3(.025,.04,.15),vec3(.06,.1,.32),smoothstep(.12,.5,dens));
  rgb=mix(rgb,vec3(.11,.18,.46),smoothstep(.55,.95,dens)*.6);
  alpha=clamp(dens*1.05,0.0,.62)*tint.a;
 }else if(Mode==7){
  // Dark violet smoke puff with ragged rim.
  vec2 flow=p*2.2+vec2(seed,-Age*.04);
  float n=fbm(flow+vec2(fbm(flow+3.1),fbm(flow-5.7))*1.7);
  float r=length(p);
  float body=1.0-smoothstep(.45+n*.45,.95,r);
  rgb=mix(vec3(.02,.0,.05),vec3(.30,.04,.62),smoothstep(.45,.85,n)*.8);
  alpha=body*(.55+.45*n)*tint.a;
 }else{
  float y=uv.y;
  vec2 flow=vec2(uv.x*3.0+seed, y*3.7-t*2.1);
  vec2 warp=vec2(fbm(flow+13.0),fbm(flow-7.0));
  float n=fbm(flow+warp*2.5);
  float bend=sin(y*7.0-t*2.4+seed)*(.10+y*.25);
  float envelope=(1.0-y)*.68+.10;
  float field=envelope-abs(p.x+bend)+(n-.48)*.92;
  // Broken, curling tongues instead of solid cones: flowing holes and detached embers.
  field-=smoothstep(.63,.78,fbm(flow*1.3+warp*3.0))*y*.32;
  float body=smoothstep(.025,.10,field)*(1.0-smoothstep(.84,1.0,y));
  float heat=smoothstep(.06,.28,field)*(1.0-y*.43);
  vec3 violet=vec3(.39,.005,1.0),pink=vec3(.64,.08,1.0),white=vec3(1.0,.92,1.0);
  rgb=mix(violet,pink,smoothstep(.05,.22,field));
  rgb=mix(rgb,white,pow(heat,2.4)*.95);
  float glow=smoothstep(-.14,.035,field)*(1.0-smoothstep(.82,1.0,y));
  rgb=mix(vec3(.56,.02,1.0),rgb,body);
  alpha=max(body*.96,glow*.14)*tint.a;
 }
 if(alpha<.008)discard;
 fragColor=vec4(rgb,alpha)*ColorModulator;
}
