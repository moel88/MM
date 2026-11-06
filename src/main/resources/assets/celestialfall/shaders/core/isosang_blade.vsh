#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Tip;
uniform vec3 Across;
uniform vec3 Axis;
uniform vec3 Exposure;
uniform float Storm;
// (2.87.5) the sky close-up's widening: x how long its start and its end are rounded over (blocks along the
// blade, 0 for the sharp corners of 2.87.4), y and z how much the side at negative and positive across widens
// (1 as before, 0 not at all)
uniform vec3 SkyShape;
out vec3 pos;
out vec2 uv;
out vec4 tint;
out vec3 nrm;

// Both sides, the relief and the perimeter use this single object-space profile.
// The engraved point stays true to the close shot; the fuller opens into a
// much wider, ridged blade. Its inscriptions follow the forged surface.
void main(){
 vec3 a=normalize(Across), up=normalize(Axis), n=normalize(cross(a,up));
 mat3 basis=mat3(a,up,n);
 vec3 q=transpose(basis)*(Position-Tip);
 // Ground close-up: a straight, symmetric blade with a short curved point.
 // Compress only the point, easing back to an unchanged engraving period.
 // The sky close-up retains its established profile below this branch.
 if(Storm<.5){
  float join=clamp(q.y-5.534,0.,1.);
  float h=.86*q.y+.07*join*join+.14*max(q.y-6.534,0.);
  float dy=.86+.14*join;
  pos=Tip+basis*vec3(q.x,h,q.z);
  vec3 localNormal=transpose(basis)*Normal;
  nrm=normalize(basis*vec3(localNormal.x,localNormal.y/dy,localNormal.z));
  uv=UV0;tint=Color;
  tint.a=min(Color.a,Exposure.x*(1.-smoothstep(Exposure.y,Exposure.z,h)));
  gl_Position=ProjMat*ModelViewMat*vec4(pos,1.);
  return;
 }

 float t=clamp((q.y-3.5)/13.,0.,1.);
 float b=t*t*(3.-2.*t);
 float db=6.*t*(1.-t)/13.;
 float depth=1.+2.65*b;
 float integral=13.*(t*t*t-.5*t*t*t*t)+max(q.y-16.5,0.);
 float h=q.y+.93*integral;
 // The blade widens past its point, by .43 of its half width a block, up to 3.52 times. (2.87.5) Its start
 // and its end are rounded over SkyShape.x blocks: begun at once, the outline and the light turned a corner
 // where the point's curve meets the widening (5.534), and the sky shot showed a crooked flap there.
 float L=SkyShape.x,k=.43/3.8,s=h-5.534,ramp,rampSlope;
 if(L<=0.){ramp=max(s,0.);rampSlope=s>0.?1.:0.;}
 else if(s<=-L){ramp=0.;rampSlope=0.;}
 else if(s>=L){ramp=s;rampSlope=1.;}
 else{ramp=(s+L)*(s+L)/(4.*L);rampSlope=(s+L)/(2.*L);}
 float spread=k*ramp,cap=2.52,c=k*L,spreadSlope=k*rampSlope;
 if(c<=0.){spreadSlope=spread<cap?spreadSlope:0.;spread=min(spread,cap);}
 else if(spread>=cap+c){spread=cap;spreadSlope=0.;}
 else if(spread>cap-c){spreadSlope*=1.-(spread-cap+c)/(2.*c);spread-=(spread-cap+c)*(spread-cap+c)/(4.*c);}
 float sideWidening=q.x<0.?SkyShape.y:SkyShape.z;
 float width=1.+sideWidening*spread;
 float widthDerivative=sideWidening*spreadSlope*(1.+.93*b);
 float roof=max(0.,1.-abs(q.x)/3.8);
 float fold=2.65*roof*sign(q.z);
 pos=Tip+basis*vec3(q.x*width,h,q.z*depth+fold*b);
 vec3 localNormal=transpose(basis)*Normal;
 float nz=localNormal.z/depth;
 float nx=(localNormal.x+nz*2.65*b*sign(q.x)*sign(q.z)/3.8)/width;
 float ny=(localNormal.y-nx*q.x*widthDerivative-nz*(q.z*2.65+fold)*db)/(1.+.93*b);
 nrm=normalize(basis*vec3(nx,ny,nz));
 uv=UV0;tint=Color;
 tint.a=min(Color.a,Exposure.x*(1.-smoothstep(Exposure.y,Exposure.z,h)));
 gl_Position=ProjMat*ModelViewMat*vec4(pos,1.);
}
