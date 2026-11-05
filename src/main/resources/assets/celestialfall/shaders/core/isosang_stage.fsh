#version 150
// Distant environments only; the storm clouds are a separate world-space volume.
in vec3 pos;
in vec2 uv;
in vec4 tint;
uniform vec4 ColorModulator;
uniform float Age;
uniform int Mode;
uniform vec4 Eye;
uniform sampler2D Sampler0;
uniform vec3 StormForward;
uniform vec3 StormRight;
uniform vec3 StormUp;
uniform vec3 GroundOrigin;
out vec4 fragColor;

float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);}
float fbm(vec2 p){float s=0.0,a=.5;for(int i=0;i<5;i++){s+=a*noise(p);p=p*2.03+vec2(17.7,9.2);a*=.5;}return s/.97;}
// noise round the horizon, the same all the way round (sampled on a circle)
float ring(float az,float scale,float seed){return fbm(vec2(cos(az),sin(az))*scale+vec2(seed,seed*.37));}
// a range of hills or mountains: its crest's height (radians over the horizon) round the horizon
float crest(float az,float base,float amp,float scale,float seed){
 float n=ring(az,scale,seed);return base+amp*(n*n*1.6)+.012*ring(az,scale*4.0,seed+3.3);
}
// how far a point (elevation e) is inside a range, eased over a few pixels (its edge drawn clean)
float inside(float e,float h){float w=max(fwidth(e),.0006)*1.2;return 1.0-smoothstep(h-w,h+w,e);}

vec3 grey(vec3 d){
 float e=asin(clamp(d.y,-1.0,1.0)),az=atan(d.z,d.x);
 vec3 col=mix(vec3(.86,.85,.82),vec3(.69,.70,.72),smoothstep(0.0,.9,e));
 float c=fbm(vec2(cos(az),sin(az))*2.6*(1.3-e)+vec2(Age*.02,e*5.0));
 col=mix(col,vec3(.62,.64,.68),smoothstep(.48,.82,c)*smoothstep(-.02,.25,e)*.55);
 // far mountains in the haze, then nearer ones in dark ink; their feet lost in mist
 float hf=crest(az,.03,.07,2.2,3.1),hn=crest(az,.008,.06,3.1,7.7);
 float mf=inside(e,hf),mn=inside(e,hn);
 vec3 far=mix(vec3(.74,.74,.73),vec3(.47,.49,.53),smoothstep(-.02,hf,e));
 vec3 near=mix(vec3(.66,.67,.67),vec3(.22,.24,.27),smoothstep(-.03,hn*.8,e));
 near*=.9+.2*fbm(vec2(az*30.0,e*60.0));
 col=mix(col,far,mf);col=mix(col,near,mn);
 if(e<0.0)col=mix(col,vec3(.64,.65,.65),smoothstep(0.0,-.06,e));
 return col;
}
// A distant matte environment; the sword, clouds, wind, earth and debris are
// independent depth-tested 3D effects. No recorded animation is played.
vec3 dusk(vec3 d){
 float z=max(dot(d,StormForward),.15);
 vec2 st=vec2(.5+dot(d,StormRight)/z/2.4024,.5-dot(d,StormUp)/z/1.1086);
 return texture(Sampler0,clamp(st,vec2(.002),vec2(.998))).rgb;
}
vec3 daylight(vec3 d){
 vec3 sun=normalize(Eye.xyz);
 float glint=pow(max(0.,dot(d,sun)),28.);
 vec3 col=mix(vec3(.39,.60,.80),vec3(.16,.36,.62),smoothstep(0.,1.,d.y));
 return col+vec3(.54,.42,.22)*glint+vec3(.16,.19,.18)*pow(max(0.,dot(d,sun)),4.);
}
void main(){
 vec3 rgb;float alpha=1.0;
 if(Mode==0){rgb=vec3(0.0);}
 else if(Mode==1){rgb=grey(normalize(pos));}
 else if(Mode==2){rgb=daylight(normalize(pos));}
 else if(Mode==3){rgb=dusk(normalize(pos));}
 else if(Mode==5){
  float r=length(uv);
  vec3 p=pos-GroundOrigin;
  vec3 earth=vec3(.028,.031,.036)*(.75+.55*fbm(p.xz*2.));
  rgb=mix(earth,dusk(normalize(pos)),smoothstep(6.,14.,r));
  float impact=max(Age-3.1,0.);
  float x=dot(p,StormRight),z=dot(p,StormForward);
  float drive=smoothstep(0.,.08,impact)*(1.-smoothstep(2.05,2.4,impact));
  float heat=drive*exp(-x*x/18.-z*z/2.);
  rgb+=vec3(.50,.205,.035)*heat;
  // Fractures propagate out from the ongoing cut. Their path is fixed in world
  // space; only their exposed length and heat change, so they do not swim.
  float ph=atan(p.z,p.x),reach=6.*(1.-exp(-impact*2.8));
  float angular=ph*12.+.45*sin(r*3.2)+.30*sin(r*7.+ph*3.);
  float split=abs(sin(angular))*(r+.15)/12.;
  float branch=abs(sin(ph*19.+.65*sin(r*2.1+ph*4.)+1.7))*(r+.15)/19.;
  float crack=min(split,branch+.012);
  float opened=(1.-smoothstep(.012,.027,crack))*(1.-smoothstep(reach-.45,reach,r))*smoothstep(.35,.9,r);
  float ember=opened*drive*(.38+.32*noise(p.xz*8.));
  rgb=mix(rgb,vec3(.006,.004,.003),opened*.65);
  rgb+=vec3(.65,.23,.032)*ember;
  alpha=1.0-smoothstep(12.0,16.0,r);
 }else{rgb=vec3(0.0);alpha=0.0;}
 if(alpha<.004)discard;
 fragColor=vec4(rgb,alpha)*ColorModulator;
}
