#version 150
// Lee So-sang's G's giant sword (2.83.0, user request: "검 퀄리티도 다르고"): the blade lit as the videos' is, warm and
// glossy: a warm key light from beside the camera (Light), a cool fill from the sky, a bright rim where it turns away, a
// soft shine; its carving by its normals (Sampler1, in the blade's frame: Across, Axis and the face's normal); its colour
// and where it glows when hot (Sampler0); Heat how hot its point burns, Dark how dark it has gone. tint.r which face
// (1 its front, 0 its back: the back's texture is mirrored), tint.g 1 its guard, grip and pommel (bronze, no carving),
// tint.a how much of it shows. Edge 1 draws its thin edges. Hole (2.83.1, in the storm): the way to the storm's eye and the
// hole's radius in its ring of clouds (w, radians; 0 none), Far the way the sword goes away into the storm: on that side,
// past the hole's edge, the clouds take it (it whitens into their haze and is lost in their wisps, as isosang_stage.fsh
// draws them: the same edge); its point and its near edges stay clear of them.
in vec3 pos;
in vec2 uv;
in vec4 tint;
in vec3 nrm;
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform vec3 Across;
uniform vec3 Axis;
uniform vec3 Light;
uniform float Heat;
uniform float Dark;
uniform int Edge;
uniform vec4 Hole;
uniform vec3 Far;
uniform float Age;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);}
float fbm(vec2 p){float s=0.0,a=.5;for(int i=0;i<5;i++){s+=a*noise(p);p=p*2.03+vec2(17.7,9.2);a*=.5;}return s/.97;}
// how much of it shows through the storm's clouds (x) and how deep in their haze it is (y)
vec2 clouds(){
 if(Hole.w<=0.0)return vec2(1.0,0.0);
 vec3 d=normalize(pos),E=normalize(Hole.xyz);float R=Hole.w,k=R/.3665;
 vec3 b1=normalize(cross(E,vec3(0,1,0)));vec3 b2=cross(E,b1);
 float th=acos(clamp(dot(d,E),-1.0,1.0)),ph=atan(dot(d,b2),dot(d,b1)),lt=log(th+.02);
 float edge=R*(1.0+.08*(fbm(vec2(cos(ph)*3.0+Age*.05,sin(ph)*3.0+lt*2.0))-.5));
 vec3 tg=d-E*dot(d,E);float far=smoothstep(-.2,.6,dot(tg/max(length(tg),1e-5),normalize(Far)));
 // (2.83.1, user: "왼쪽은 검이 더 멀리까지 보이는데 너는 끝부분 밖에 안 보여": the videos' sword goes on to the hole's round
 // edge, 28 to 32 degrees from the eye, before the clouds take it; no wisps reaching in over it)
 float cut=edge+.012*k;
 // (only on its far side: near its point the ring's clouds are behind it)
 float shown=1.0-far*smoothstep(cut-.025*k,cut+.006*k,th);
 // (its thin edges glow: past the hole's edge, on any side, they are lost in the clouds too)
 if(Edge==1)shown*=1.0-smoothstep(edge-.02*k,edge+.01*k,th);
 return vec2(shown,smoothstep(cut-.12*k,cut,th)*far);
}
void main(){
 vec2 cl=clouds();
 float shown=tint.a*cl.x;
 if(shown<.004)discard;
 vec4 tx=texture(Sampler0,uv);
 vec3 Ng=normalize(nrm);
 vec3 V=normalize(-pos);
 vec3 N=Ng;
 vec3 albedo=tx.rgb;float hotMask=tx.a;
 if(tint.g>.5){
  albedo=vec3(.62,.46,.28);hotMask=0.0;
 }else if(Edge==0){
  float side=tint.r>.5?1.0:-1.0;
  vec3 T=normalize(Across*side),A=normalize(Axis);
  T=normalize(T-Ng*dot(T,Ng));A=normalize(A-Ng*dot(A,Ng));
  vec3 m=texture(Sampler1,uv).xyz*2.0-1.0;
  N=normalize(T*m.x+A*m.y+Ng*m.z);
 }else{
  albedo=vec3(.80,.47,.29);hotMask=.35;
 }
 if(dot(N,V)<0.0)N=-N;
 vec3 L=normalize(Light);
 float ndl=dot(N,L);
 float wrap=max((ndl+.4)/1.4,0.0);
 vec3 H=normalize(L+V);
 float spec=pow(max(dot(N,H),0.0),26.0);
 float rim=pow(1.0-max(dot(N,V),0.0),3.0);
 float sky=.5+.5*N.y;
 vec3 col=albedo*(vec3(.20,.21,.27)*sky+vec3(.08,.06,.05)+vec3(1.0,.86,.70)*wrap*.86);
 col+=vec3(1.0,.86,.66)*spec*.3+vec3(1.0,.72,.46)*rim*.38;
 // (2.83.1: as muted as the videos' sky shot: a pinkish terracotta, less orange; measured against it)
 col*=vec3(.81,.84,1.04);
 // its point burning: a hot glow over it, whitening where it is hottest (the videos' is nearly white there)
 float hot=Heat*hotMask;
 col+=vec3(1.0,.72,.48)*hot*.45+vec3(1.0,.97,.92)*hot*hot*.38;
 col=mix(col,col*vec3(.26,.22,.21),Dark);
 col=mix(col,vec3(.80,.84,.90),.6*cl.y);
 fragColor=vec4(col,shown)*ColorModulator;
}
