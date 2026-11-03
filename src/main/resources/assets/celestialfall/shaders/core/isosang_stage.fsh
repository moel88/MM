#version 150
// Lee So-sang's G's own space (2.83.0, user request: "컷씬은 그냥 다른 공간 보여줘서 검 안보이게 하고 박히는 순간 원래 월드에는
// 박히는 효과만 남게"): its cutscene's shots taken somewhere of their own, as the user's videos' are. Mode 0 black (round
// her hand), 1 a pale sky over far ink mountains (her face), 2 a storm whirling round its eye (Eye: the way to it, and
// its hole's radius in radians: where the sword comes down through it, over her), 3 a dusky sky over dark hills (the sword coming down), 5 the dusky ground (uv: blocks from where
// it strikes), 4 the ring's clouds in front of the sword's tip. The sky's pos is the way it looks. (The sword is drawn with isosang_blade, lit.)
in vec3 pos;
in vec2 uv;
in vec4 tint;
uniform vec4 ColorModulator;
uniform float Age;
uniform int Mode;
uniform vec4 Eye;
// (the way the sword goes away into the storm, square to Eye: its far side, where the clouds take it)
uniform vec3 Far;
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
vec3 C(float r,float g,float b){return vec3(r,g,b)/255.0;}
// the storm's clouds round its eye: big painted masses (q: round the eye, radians)
float clouds(vec2 q){vec2 w=vec2(fbm(q*1.7+vec2(3.1,1.7)),fbm(q*1.7+vec2(-4.3,8.1)));return fbm(q*2.3+2.2*w+vec2(Age*.01,0.0));}
// where its ring's clouds begin round the hole (isosang_blade.fsh fades the sword out by the same edge)
float holeEdge(float ph,float th){return Eye.w*(1.0+.08*(fbm(vec2(cos(ph)*3.0+Age*.05,sin(ph)*3.0+log(th+.02)*2.0))-.5));}
vec3 storm(vec3 d){
 // (2.83.1, the videos' sky shot: a ring of bright clouds wound round a hole the sword comes down through, wide on
 // the left and over the top, a narrow band on the right; dark slate clouds round it lit from the eye; a few thin rays)
 vec3 E=normalize(Eye.xyz);float R=Eye.w;
 vec3 b1=normalize(cross(E,vec3(0,1,0)));vec3 b2=cross(E,b1);
 float th=acos(clamp(dot(d,E),-1.0,1.0)),ph=atan(dot(d,b2),dot(d,b1));
 float lt=log(th+.02);
 float s=ph+1.35*lt-Age*.12;vec2 cs=vec2(cos(s),sin(s));
 vec2 q=vec2(cos(ph),sin(ph))*th;
 // the clouds round it, brighter on the side facing the eye
 float n=clouds(q),nl=clouds(q*(1.0-.06/max(th,1e-3)));
 float lit=clamp((n-nl)*9.0,0.0,1.0),fine=fbm(q*11.0+vec2(5.5,-2.5));
 vec3 col=mix(C(24.,40.,68.),C(48.,66.,98.),smoothstep(.36,.56,n));
 col=mix(col,C(82.,100.,128.),smoothstep(.5,.72,n)*(.55+.45*fine));
 col=mix(col,C(150.,162.,180.),lit*smoothstep(.45,.6,n)*.75);
 // the ring
 // (its sizes round the hole drawn for a hole of 21 degrees, grown with it: the sky shot's lens is wider now)
 float k=R/.3665;
 float edge=holeEdge(ph,th);
 float left=.5-.5*cos(ph);
 float reach=R+k*(.07+.2*left+.1*(.5-.5*sin(ph))*left+.14*fbm(cs*2.2+vec2(5.0,lt*1.5)));
 float rt=clamp((th-edge)/max(reach-edge,1e-3),0.0,1.5);
 float puff=fbm(q*7.0+vec2(1.3,4.4));
 float streak=fbm(vec2(cs.x*1.5+3.3+.6*puff,cs.y*1.5+lt*4.0));
 float streak2=fbm(vec2(cs.x*3.2+lt*2.0,cs.y*3.2+lt*9.0));
 float rd=1.0-smoothstep(.55,1.05,rt+.45*(puff-.5)+.2*(streak-.5));
 float v=streak*.65+streak2*.45;
 vec3 rc=mix(C(92.,114.,146.),C(150.,163.,182.),smoothstep(.3,.62,v));
 rc=mix(rc,C(198.,201.,205.),smoothstep(.5,.78,v)*(1.0-.6*clamp(rt,0.0,1.0)));
 float lip=exp(-pow((th-edge)/(.025*k),2.0))*(.6+.4*streak);
 rc=mix(rc,C(198.,201.,205.),lip*.8);
 col=mix(col,rc,rd);
 // inside the hole: the tunnel, pale, its streaks winding in
 float tun=fbm(vec2(cs.x*2.4+1.1,cs.y*2.4+lt*9.0));
 col=mix(mix(C(132.,154.,182.),C(198.,203.,208.),smoothstep(.35,.7,tun)),col,smoothstep(edge-.025*k,edge+.004*k,th));
 // a few thin rays out of the eye over the clouds round it
 float N=56.0,a=(ph/6.2831853+.5)*N,i=floor(a),f=a-i;
 float on=step(.7,hash(vec2(i,3.7))),at=.2+.6*hash(vec2(i,9.1));
 float w=abs(f-at)*(6.2831853/N)*sin(th),thick=.002+.0016*hash(vec2(i,5.3));
 float ray=on*(1.0-smoothstep(0.0,thick+fwidth(w),w))*(.35+.65*hash(vec2(i,7.7)));
 ray*=smoothstep(R+.12*k,R+.4*k,th)*(1.0-smoothstep(1.0,1.6,th));
 col+=C(205.,212.,224.)*ray*.32;
 return col;
}
// the ring's clouds in front of the sword (mode 4, drawn over it): round the hole's edge, reaching in over its tip in
// wisps (isosang_slab.fsh fades it out by the same edge), thinning out into the ring drawn behind
vec4 front(vec3 d){
 // (the same clouds as the ring drawn behind (storm), so they show only where they cover the sword)
 vec3 E=normalize(Eye.xyz);float R=Eye.w,k=R/.3665;
 vec3 b1=normalize(cross(E,vec3(0,1,0)));vec3 b2=cross(E,b1);
 float th=acos(clamp(dot(d,E),-1.0,1.0)),ph=atan(dot(d,b2),dot(d,b1)),lt=log(th+.02);
 float s=ph+1.35*lt-Age*.12;vec2 cs=vec2(cos(s),sin(s));
 vec2 q=vec2(cos(ph),sin(ph))*th;
 float edge=holeEdge(ph,th);
 vec3 tg=d-E*dot(d,E);float far=smoothstep(-.2,.6,dot(tg/max(length(tg),1e-5),normalize(Far)));
 float wisp=fbm(vec2(cos(ph)*7.0-Age*.08,sin(ph)*7.0+lt*9.0));
 float cut=edge+.012*k-far*.07*k*wisp;
 float left=.5-.5*cos(ph);
 float reach=R+k*(.07+.2*left+.1*(.5-.5*sin(ph))*left+.14*fbm(cs*2.2+vec2(5.0,lt*1.5)));
 float rt=clamp((th-edge)/max(reach-edge,1e-3),0.0,1.5);
 float puff=fbm(q*7.0+vec2(1.3,4.4));
 float streak=fbm(vec2(cs.x*1.5+3.3+.6*puff,cs.y*1.5+lt*4.0));
 float streak2=fbm(vec2(cs.x*3.2+lt*2.0,cs.y*3.2+lt*9.0));
 float rd=1.0-smoothstep(.55,1.05,rt+.45*(puff-.5)+.2*(streak-.5));
 float v=streak*.65+streak2*.45;
 vec3 rc=mix(C(92.,114.,146.),C(150.,163.,182.),smoothstep(.3,.62,v));
 rc=mix(rc,C(198.,201.,205.),smoothstep(.5,.78,v)*(1.0-.6*clamp(rt,0.0,1.0)));
 rc=mix(rc,C(198.,201.,205.),exp(-pow((th-edge)/(.025*k),2.0))*(.6+.4*streak)*.8);
 // its wisps over the sword's far side: bright cloud reaching in
 float a=smoothstep(cut-.025*k,cut+.006*k,th)*max(rd,far*(1.0-smoothstep(edge,edge+.05*k,th)));
 rc=mix(rc,C(206.,210.,214.),far*(1.0-smoothstep(cut,edge+.02*k,th))*.6);
 return vec4(rc,a);
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
 else if(Mode==4){vec4 f=front(normalize(pos));rgb=f.rgb;alpha=f.a;}
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
