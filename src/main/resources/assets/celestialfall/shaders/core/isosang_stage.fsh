#version 150
// Lee So-sang's G's own space (2.83.0, user request: "컷씬은 그냥 다른 공간 보여줘서 검 안보이게 하고 박히는 순간 원래 월드에는
// 박히는 효과만 남게"): its cutscene's shots taken somewhere of their own, as the user's videos' are. Mode 0 black (round
// her hand), 1 a pale sky over far ink mountains (her face), 2 a storm whirling round its eye (Eye: where the sword comes
// out of it, over her), 3 a dusky sky over dark hills (the sword coming down), 5 the dusky ground (uv: blocks from where
// it strikes). The sky's pos is the way it looks. (The sword is drawn with isosang_blade, lit.)
in vec3 pos;
in vec2 uv;
in vec4 tint;
uniform vec4 ColorModulator;
uniform float Age;
uniform int Mode;
uniform vec3 Eye;
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
vec3 storm(vec3 d){
 vec3 E=normalize(Eye);
 float th=acos(clamp(dot(d,E),-1.0,1.0));
 vec3 b1=normalize(cross(E,abs(E.y)<.95?vec3(0,1,0):vec3(1,0,0)));vec3 b2=cross(E,b1);
 float ph=atan(dot(d,b2),dot(d,b1));
 // the arms wind in toward the eye, turning
 float tw=ph+1.9*log(th+.04)-Age*.32;
 vec2 q=vec2(cos(tw),sin(tw))*(.6+th*2.2);
 float n=fbm(q*2.2+3.1),fine=fbm(q*6.5-1.7);
 float arms=.5+.5*cos(tw*3.0);
 float dens=clamp(.5*arms+.9*n+.25*fine-.45,0.0,1.0);
 float lit=1.0-smoothstep(.05,1.1,th);
 vec3 col=mix(vec3(.10,.14,.22),vec3(.24,.31,.42),smoothstep(0.0,.35,dens));
 col=mix(col,vec3(.52,.60,.72),smoothstep(.35,.7,dens)*(.45+.55*lit));
 col=mix(col,vec3(.93,.95,.98),smoothstep(.62,.95,dens)*lit);
 // rays out of the eye
 float ray=pow(noise(vec2(cos(ph),sin(ph))*9.0+vec2(Age*.05,0.0)),5.0);
 col+=vec3(.95,.92,.85)*ray*smoothstep(1.3,.15,th)*.5;
 // the eye, open and bright
 col=mix(col,vec3(1.0,.97,.90),1.0-smoothstep(.05,.18,th));
 return col;
}
vec3 dusk(vec3 d){
 float e=asin(clamp(d.y,-1.0,1.0)),az=atan(d.z,d.x);
 vec3 col=mix(vec3(.31,.35,.42),vec3(.13,.16,.21),smoothstep(-.02,.75,e));
 float c=fbm(vec2(cos(az),sin(az))*2.4+vec2(Age*.01,e*9.0));
 col=mix(col,vec3(.19,.22,.28),smoothstep(.5,.8,c)*smoothstep(.02,.12,e)*(1.0-smoothstep(.3,.6,e))*.7);
 // three ranges of hills, the farther paler in the haze, their feet in mist
 float h0=crest(az,.035,.06,2.0,11.0),h1=crest(az,.018,.05,2.7,23.0),h2=crest(az,.004,.035,3.6,37.0);
 col=mix(col,mix(vec3(.30,.34,.41),vec3(.24,.28,.35),smoothstep(-.01,h0,e)),inside(e,h0));
 col=mix(col,mix(vec3(.24,.27,.33),vec3(.16,.19,.25),smoothstep(-.01,h1,e)),inside(e,h1));
 col=mix(col,mix(vec3(.12,.14,.18),vec3(.09,.11,.15),smoothstep(-.01,h2,e)),inside(e,h2));
 if(e<0.0)col=mix(col,vec3(.08,.095,.125),smoothstep(0.0,-.02,e));
 return col;
}
void main(){
 vec3 rgb;float alpha=1.0;
 if(Mode==0){rgb=vec3(0.0);}
 else if(Mode==1){rgb=grey(normalize(pos));}
 else if(Mode==2){rgb=storm(normalize(pos));}
 else if(Mode==3){rgb=dusk(normalize(pos));}
 else if(Mode==5){
  float r=length(uv);
  float n=fbm(uv*.35),f2=fbm(uv*2.1+5.0);
  rgb=mix(vec3(.065,.075,.10),vec3(.10,.11,.14),n)*(.9+.2*f2);
  rgb=mix(rgb,vec3(.08,.095,.125),smoothstep(12.0,58.0,r));
  alpha=1.0-smoothstep(52.0,64.0,r);
 }else{rgb=vec3(0.0);alpha=0.0;}
 if(alpha<.004)discard;
 fragColor=vec4(rgb,alpha)*ColorModulator;
}
