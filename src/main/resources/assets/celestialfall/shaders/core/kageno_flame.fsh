#version 150
in vec2 uv;
in vec4 tint;
uniform vec4 ColorModulator;
uniform float Age;
uniform int Mode;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);}
float fbm(vec2 p){return .53*noise(p)+.28*noise(p*2.03+17.7)+.13*noise(p*4.11+8.3)+.06*noise(p*8.23);}
void main(){
 vec2 p=uv*2.0-1.0;float seed=tint.r*31.7,t=Age*.075+seed;
 vec3 rgb;float alpha;
 if(Mode==1){
  float r=length(p);float n=fbm(p*6.0+vec2(t*.12,-t*.18));
  float edge=1.0-smoothstep(.72+n*.17,.96+n*.10,r);
  float swirl=sin(atan(p.y,p.x)*5.0+r*16.0-t*.8+fbm(p*5.0+t*.2)*7.0)*.5+.5;
  float veins=pow(swirl,7.0)*smoothstep(.15,.7,r);
  rgb=mix(vec3(.004,.001,.012),vec3(.32,.008,.78),veins*.75);
  rgb+=vec3(.38,.07,.65)*pow(n,4.0)*2.0;
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
  rgb=mix(vec3(.40,.005,1.0),vec3(.96,.18,1.0),smoothstep(-.05,.13,shape));
  rgb=mix(rgb,vec3(1.0,.96,1.0),clamp(pow(heat,1.8)*.90+initial*.75,0.0,1.0));
  float glow=smoothstep(-.24,-.015,shape)*(1.0-body)*.16;
  alpha=max(body,glow)*tint.a;
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
