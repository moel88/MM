#version 150
// Meteor effects (2.49.0). Mode 1 (alpha blend) draws the fire and smoke puffs on fixed planes, Mode 5 the dust of the
// shock ring; the others are light added over the scene (meteor_fx_add): 2 glow, 3 the flame sheath round the falling
// rock, 4 its trail, 6 the landing mark, 7 sparks, 8 glints, 9 the glow cast on the ground.
in vec2 uv;
in vec4 tint;
uniform vec4 ColorModulator;
uniform float Age;
uniform int Mode;
// The scene's depth (a copy, Sampler0) where Soft is 1: puffs and light melt away where they meet the ground, the rock
// or anything else instead of being cut off along it. The decals on the ground (5, 6, 9) do not.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform mat4 ProjMat;
uniform float Soft;
// How much daylight there is (1 at noon, about .3 at night): smoke and dust are lit by it, fire and light are not.
uniform float Ambient;
out vec4 fragColor;
float viewDepth(float d){return ProjMat[3][2]/((d*2.0-1.0)+ProjMat[2][2]);}
float soft(float range){
 if(Soft<.5)return 1.0;
 float scene=texture(Sampler0,gl_FragCoord.xy/ScreenSize).r;
 return clamp((viewDepth(scene)-viewDepth(gl_FragCoord.z))/range,0.0,1.0);
}
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);}
float fbm(vec2 p){return .53*noise(p)+.28*noise(p*2.03+17.7)+.13*noise(p*4.11+8.3)+.06*noise(p*8.23);}
// Fire from black smoke (0) through ember red, orange and yellow to white heat (1).
vec3 fire(float h){
 vec3 c0=vec3(.07,.04,.035),c1=vec3(.38,.055,.02),c2=vec3(.80,.14,.02),c3=vec3(.98,.37,.04),c4=vec3(1.0,.62,.11),c5=vec3(1.0,.84,.38),c6=vec3(1.0,.97,.87);
 h=clamp(h,0.0,1.25);
 return h<.12?mix(c0,c1,h/.12):h<.30?mix(c1,c2,(h-.12)/.18):h<.48?mix(c2,c3,(h-.30)/.18):h<.66?mix(c3,c4,(h-.48)/.18):h<.84?mix(c4,c5,(h-.66)/.18):mix(c5,c6,clamp((h-.84)/.2,0.0,1.0));
}
void main(){
 vec2 p=uv*2.0-1.0;float code=floor(tint.r*255.0+.5);float seed=mod(code,128.0)*.731;
 vec3 rgb;float alpha;
 if(Mode==1){
  if(code<128.0){
   // A fire billow: lobed, crisp edge, a darker rim and a hot core (tint.g heat, tint.b how much it curls into a wisp).
   float heat=tint.g,wisp=tint.b;
   vec2 q=p;
   q.x+=wisp*sin(q.y*2.6+seed+Age*.13)*.18*(q.y+1.0)*.5;
   q.x*=1.0+wisp*max(0.0,q.y+.2)*1.4;
   vec2 flow=q*1.55+vec2(seed*.37,-Age*.09);
   vec2 warp=vec2(noise(flow+3.7),noise(flow-6.1));
   float n=fbm(flow*1.25+warp*1.9);
   float r=length(q*vec2(1.0,.94));
   // never out to the plane's own edge (a lobe reaching it would be cut off in a straight line)
   float edge=min(.86,.70+(n-.5)*.62+.07*sin(atan(q.y,q.x)*5.0+seed+Age*.07));
   float aa=max(fwidth(r),.002)*1.5;
   float body=1.0-smoothstep(edge-.06-aa,edge-.06+aa,r);
   float core=1.0-smoothstep(.0,edge,r);
   float h=clamp(heat+(n-.5)*.32+core*.25*heat-max(0.0,-q.y)*.06,0.0,1.2);
   rgb=fire(h);
   float rim=smoothstep(edge-.24,edge-.07,r);
   rgb*=1.0-rim*.42*smoothstep(.15,.5,heat);
   alpha=body*tint.a*soft(.7);
  }else{
   // A smoke puff: a heap of round billows (a big one and four smaller round it, laid out by the seed and slowly
   // rolling), each shaded like a ball lit from above in three flat tones, with a crisp lobed edge and a darker rim:
   // tint.g how dark (0 pale dust, 1 black smoke), tint.b the fire's light on its belly; holes open in it as it fades.
   // The heap stays well inside its plane, so no edge of it is ever cut straight.
   float dark=tint.g,ember=tint.b,thin=1.0-tint.a;
   vec2 flow=p*1.7+vec2(seed*.31,-Age*.03);
   float n=fbm(flow+vec2(noise(flow+2.9),noise(flow-4.3))*1.4);
   vec2 w=p+(vec2(noise(flow*1.3+5.0),noise(flow*1.3-3.0))-.5)*.14;
   float sd=1e3,top=-1.0;vec3 nrm=vec3(0.0,0.0,1.0);
   for(int i=0;i<5;i++){
    float fi=float(i);
    float ang=seed*2.3+fi*2.39996+Age*.004*(mod(fi,2.0)*2.0-1.0);
    float off=i==0?0.0:.24+.16*hash(vec2(seed,fi));
    float R=i==0?.50:.27+.12*hash(vec2(fi,seed+3.1));
    vec2 c=vec2(cos(ang),sin(ang))*off;
    vec2 d=(w-c)/R;float q=1.0-dot(d,d);
    sd=min(sd,length(w-c)-R);
    // the billow standing out furthest is the one seen
    if(q>0.0){float z=sqrt(q)*R+(i==0?0.0:.05);if(z>top){top=z;nrm=vec3(d,sqrt(q));}}
   }
   float aa=max(fwidth(sd),.002)*1.5;
   float body=1.0-smoothstep(-aa,aa,sd);
   body*=1.0-smoothstep(.56-thin*.34,.62-thin*.34,fbm(flow*1.8+7.0))*thin;
   float lit=dot(nrm,vec3(-.2,.85,.5))+(n-.5)*.25;float ab=max(fwidth(lit),.002)*1.5;
   vec3 shade=mix(vec3(.52,.47,.42),vec3(.075,.065,.07),dark),midc=mix(vec3(.72,.66,.59),vec3(.16,.14,.14),dark),high=mix(vec3(.90,.86,.80),vec3(.29,.27,.27),dark);
   rgb=mix(shade,midc,smoothstep(.12-ab,.12+ab,lit));
   rgb=mix(rgb,high,smoothstep(.62-ab,.62+ab,lit));
   rgb*=(1.0-.2*smoothstep(-.07,-.015,sd))*Ambient;
   rgb=mix(rgb,fire(.52+.3*n),ember*smoothstep(.3,-.65,p.y+(n-.5)*.3)*.9);
   alpha=body*tint.a*soft(.7);
  }
 }else if(Mode==2){
  // Light: a soft halo (tint.b from deep orange to pale gold) round a white-hot core (tint.g).
  float r=length(p);
  vec3 col=mix(vec3(1.0,.40,.06),vec3(1.0,.80,.40),tint.b);
  float halo=pow(max(0.0,1.0-r),2.4);
  float core=pow(max(0.0,1.0-r*2.3),1.8)*tint.g;
  rgb=mix(col,vec3(1.0,.97,.90),clamp(core*1.6,0.0,1.0));
  alpha=(halo*.85+core)*tint.a*soft(.5);
 }else if(Mode==3){
  // The flame sheath round the falling rock, on planes along its way: uv.x from its front (0) back, uv.y across.
  float x=uv.x,y=uv.y*2.0-1.0;
  float n=fbm(vec2(x*6.0-Age*.9,uv.y*5.0+seed));
  float n2=noise(vec2(x*15.0-Age*1.7,uv.y*11.0-seed));
  float width=sqrt(clamp(x*7.0,0.0,1.0))*(1.0-smoothstep(.12,1.0,x)*.78)*(.82+.32*n);
  float d=abs(y)/max(width,.03);
  float body=1.0-smoothstep(.5,1.0,d+(n2-.5)*.4);
  float heat=(1.0-x)*(1.0-d*.65)+(n-.5)*.3;
  rgb=fire(.38+heat*.85);
  alpha=body*tint.a*soft(.4)*smoothstep(0.0,.03,x)*(1.0-smoothstep(.78,1.0,x+(n-.5)*.2));
 }else if(Mode==4){
  // The trail: uv.x from the head (0) to the tail (1), uv.y across; tint.g its heat (it cools once the rock is down).
  float x=uv.x,y=uv.y*2.0-1.0;
  float n=fbm(vec2(x*9.0+seed-Age*.05,uv.y*3.0));
  float fine=noise(vec2(x*40.0+seed,uv.y*9.0-Age*.2));
  float core=1.0-smoothstep(0.0,.32+.3*n,abs(y));
  float body=1.0-smoothstep(.35,1.0,abs(y)+(n-.5)*.5+(fine-.5)*.25);
  float heat=pow(1.0-x,1.5)*tint.g;
  rgb=mix(fire(.28+heat*.62),vec3(1.0,.96,.86),core*heat*.7);
  alpha=(body*.6+core*.6)*(1.0-smoothstep(.5,1.0,x+(n-.5)*.3))*tint.a*soft(.4);
 }else if(Mode==5){
  // The shock ring's dust, flat on the ground: uv.x round it, uv.y from its inner edge (0) out; tint.g how far it is
  // gone (it breaks up), a bright crisp front.
  float a=uv.x*6.2832,rr=uv.y;
  float n=fbm(vec2(a*2.4+seed,rr*3.0-Age*.05));
  float f=.80+(n-.5)*.14;
  float aa=max(fwidth(rr),.002)*1.5;
  float front=1.0-smoothstep(.04-aa,.04+aa,abs(rr-f));
  float body=smoothstep(.0,.55,rr)*(1.0-smoothstep(f-aa,f+aa,rr));
  float thin=tint.g;
  body*=1.0-smoothstep(.45-thin*.3,.52-thin*.3,fbm(vec2(a*3.1,rr*4.0)+seed*2.0))*thin;
  rgb=mix(vec3(.58,.52,.46),vec3(.86,.82,.76),smoothstep(.45,.55,n+rr*.3));
  rgb=mix(rgb,vec3(1.0,.97,.92),front*.7)*Ambient;
  alpha=max(body*.5,front*.9)*tint.a;
 }else if(Mode==6){
  // Where it will land: a ring of dashes turning, a thinner ring inside and a faint fill (uv as the shock ring's).
  float rr=uv.y;
  float aa=max(fwidth(rr),.002)*1.5;
  float ring=1.0-smoothstep(.03-aa,.03+aa,abs(rr-.86));
  float dash=smoothstep(.45,.5,fract(uv.x*18.0-Age*.03))*(1.0-smoothstep(.95,1.0,fract(uv.x*18.0-Age*.03)));
  float inner=1.0-smoothstep(.02-aa,.02+aa,abs(rr-.64));
  float fill=smoothstep(.0,.86,rr)*(1.0-smoothstep(.84,.88,rr))*.22;
  float pulse=.7+.3*sin(Age*.55);
  rgb=vec3(1.0,.46,.10);
  alpha=(ring*.9+inner*dash*.7+fill)*pulse*tint.a*tint.g;
 }else if(Mode==7){
  // A spark's streak: uv.x from its tail (0) to its head (1); tint.g its heat.
  float x=uv.x,y=abs(uv.y*2.0-1.0);
  float core=1.0-smoothstep(.0,.45+.55*x,y);
  float h=tint.g;
  rgb=mix(fire(.35+h*.6),vec3(1.0,.98,.9),pow(x,3.0)*h);
  alpha=core*smoothstep(0.0,.6,x)*tint.a*soft(.12);
 }else if(Mode==8){
  // A glint off the metal: a four-pointed star and a little halo (tint.g whiter).
  float r=length(p);
  float star=max(0.0,1.0-abs(p.x)*9.0-abs(p.y)*1.1)+max(0.0,1.0-abs(p.y)*9.0-abs(p.x)*1.1);
  float halo=pow(max(0.0,1.0-r),3.0);
  rgb=mix(vec3(1.0,.76,.28),vec3(1.0,.98,.88),tint.g);
  alpha=(star*.9+halo*.45)*tint.a*soft(.12);
 }else{
  // The glow the fire casts on the ground round it (tint.g hotter).
  float r=length(p);
  float n=fbm(p*3.0+seed);
  float g=pow(max(0.0,1.0-r),1.8)*(.75+.5*n);
  rgb=fire(.45+tint.g*.4);
  alpha=g*tint.a;
 }
 if(alpha<.004)discard;
 fragColor=vec4(rgb,alpha)*ColorModulator;
}
