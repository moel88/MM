#version 150
// Lee So-sang's G's giant sword (2.83.0, user request: "검 퀄리티도 다르고"): the blade lit as the videos' is, warm and
// glossy: a warm key light from beside the camera (Light), a cool fill from the sky, a bright rim where it turns away, a
// soft shine; its carving by its normals (Sampler1, in the blade's frame: Across, Axis and the face's normal); its colour
// and where it glows when hot (Sampler0); Heat how hot its point burns, Dark how dark it has gone. tint.r which face
// (1 its front, 0 its back: the back's texture is mirrored), tint.a how much of it shows. Edge 1 draws its thin edges.
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
out vec4 fragColor;
void main(){
 if(tint.a<.004)discard;
 vec4 tx=texture(Sampler0,uv);
 vec3 Ng=normalize(nrm);
 vec3 V=normalize(-pos);
 vec3 N=Ng;
 vec3 albedo=tx.rgb;float hotMask=tx.a;
 if(Edge==0){
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
 // its point burning: a hot glow over it, whitening where it is hottest
 float hot=Heat*hotMask;
 col+=vec3(1.0,.60,.26)*hot*.8+vec3(1.0,.93,.76)*hot*hot*.55;
 col=mix(col,col*vec3(.26,.22,.21),Dark);
 fragColor=vec4(col,tint.a)*ColorModulator;
}
