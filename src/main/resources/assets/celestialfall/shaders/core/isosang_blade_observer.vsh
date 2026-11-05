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
uniform int ObjectSpace;
out vec3 pos;
out vec2 uv;
out vec4 tint;
out vec3 nrm;

// Observer-only silhouette: broad lower shoulders and a short, rounded point.
// The crown reaches its full thickness close to the end, so the side view
// has the same blunt termination as the front. Cinematic shaders are separate.
void main(){
 vec3 a=normalize(Across), up=normalize(Axis), n=normalize(cross(a,up));
 mat3 basis=mat3(a,up,n);
 vec3 q=ObjectSpace==1 ? Position : transpose(basis)*(Position-Tip);
 float t=clamp((q.y-3.5)/13.,0.,1.);
 float b=t*t*(3.-2.*t);
 float db=6.*t*(1.-t)/13.;
 float nose=clamp(q.y/6.,0.,1.);
 float crown=.5*(b+nose*nose*(3.-2.*nose));
 float crownDerivative=.5*(db+nose*(1.-nose));
 float depth=1.+2.65*crown;
 float integral=13.*(t*t*t-.5*t*t*t*t)+max(q.y-16.5,0.);
 // Zero axial derivative at the end gives a rounded nose. The correction
 // decays along the blade so its broad shoulders retain their proportions.
 float endRound=.7*(1.-exp(-max(q.y,0.)/1.4))*exp(-max(q.y,0.)/6.);
 float endDerivative=.5*exp(-max(q.y,0.)/6.)*(exp(-max(q.y,0.)/1.4)
                     -(1.4/6.)*(1.-exp(-max(q.y,0.)/1.4)));
 float axialDerivative=max(.025,1.+.93*b-endDerivative);
 float h=q.y+.93*integral-endRound;
 float narrowWidth=1.+clamp((h-5.534)*.43/3.8,0.,2.52);
 float narrowDerivative=(h>5.534 && h<27.804) ? .43*axialDerivative/3.8 : 0.;
 float width=.5*(narrowWidth+2.8+.72*b);
 float widthDerivative=.5*(narrowDerivative+.72*db);
 float roof=max(0.,1.-q.x*q.x/(3.8*3.8));
 float fold=1.45*roof*sign(q.z);
 pos=Tip+basis*vec3(q.x*width,h,q.z*depth+fold*crown);
 vec3 localNormal=ObjectSpace==1 ? Normal : transpose(basis)*Normal;
 float nz=localNormal.z/depth;
 float nx=(localNormal.x+nz*2.90*crown*q.x*sign(q.z)/(3.8*3.8))/width;
 float ny=(localNormal.y-nx*q.x*widthDerivative-nz*(q.z*2.65+fold)*crownDerivative)/axialDerivative;
 nrm=normalize(basis*vec3(nx,ny,nz));
 uv=UV0;tint=Color;
 if(ObjectSpace==1)tint.a=Exposure.x*(1.-smoothstep(Exposure.y,Exposure.z,q.y));
 tint.a=min(tint.a,Exposure.x*(1.-smoothstep(Exposure.y,Exposure.z,h)));
 gl_Position=ProjMat*ModelViewMat*vec4(pos,1.);
}
