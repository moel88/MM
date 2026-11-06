#version 150
// Lit bronze relief. Occlusion by the storm is resolved in the depth-aware volume pass.
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
uniform float Buried;
uniform float Storm;
uniform int Edge;
out vec4 fragColor;
// (2.88.0) The inscriptions burn: molten gold in the carved channels, lit from the point up to Rune.y blocks,
// a light running up through them (Rune.z, seconds), whitening as Rune.w rises. Rune.x is how strongly (0 none).
uniform vec4 Rune;
float runeGlow(float cavity,float y,float inside){
 if(Rune.x<=0.)return 0.;
 float groove=smoothstep(.25,.75,cavity)*inside;
 float lit=1.-smoothstep(Rune.y-1.,Rune.y+1.5,y);
 float run=fract((y-Rune.z*11.)/16.);
 float flow=.62+.26*sin(y*.55-Rune.z*6.5)+.55*exp(-pow((run-.5)*5.,2.));
 return Rune.x*groove*lit*flow;
}
vec3 runeLight(float rune){
 // gold, its hottest parts going white-gold; the flare whitens the whole of it
 return vec3(1.,.58,.18)*rune*1.15+vec3(1.,.86,.55)*rune*rune*.7+vec3(1.,.96,.86)*rune*Rune.w*.9;
}
// Resolve the same inscription at every screen scale. Subpixel grooves should
// contribute their average bronze/occlusion, rather than alternating black and white.
vec4 readSurface(sampler2D surface, vec2 coord, float lod, float coarse) {
 ivec2 size=textureSize(surface,0);
 ivec2 i=clamp(ivec2(coord*vec2(size)),ivec2(0),size-1);
 return mix(texelFetch(surface,i,0),textureLod(surface,coord,lod),coarse);
}
void main(){
 float shown=tint.a;
 if(shown<.004)discard;
 vec2 sampleUV=uv;float cavityLight=1.,cavity=0.,rune=0.;
 vec2 texel=vec2(textureSize(Sampler0,0));
 float footprint=max(length(dFdx(uv)*texel),length(dFdy(uv)*texel));
 float coarse=smoothstep(9.,23.,footprint);
 float filterWeight=smoothstep(1.,4.,footprint);
 float lod=max(0.,log2(max(1.,footprint))-.35+1.05*coarse);
 vec4 tx=readSurface(Sampler0,sampleUV,lod,filterWeight);
 vec3 Ng=normalize(nrm);
 vec3 V=normalize(-pos);
 vec3 N=Ng;
 vec3 albedo=tx.rgb*.86;float hotMask=tx.a;
 if(tint.g>.5){
  albedo=vec3(.62,.46,.28);hotMask=0.0;
 }else if(Edge==0){
  // The mesh owns the cut depth. Sampling it a second time with parallax
  // displaced the normal map twice and produced sparkling jagged channel lips.
  sampleUV=uv;
  albedo=tx.rgb*.86;hotMask=tx.a;
  float side=tint.r>.5?1.:-1.;
  vec4 relief=readSurface(Sampler1,sampleUV,lod,filterWeight);
  vec3 mapNormal=relief.xyz*2.-1.;
  // Match the point's normal transform; do not light a fictitious raised ridge.
  if(Storm<.5)mapNormal.y/=.86+.14*clamp((1.-uv.y)*96.-5.534,0.,1.);
  vec3 faceNormal=normalize(cross(Across,Axis))*side;
  float ridge=Storm*smoothstep(3.5,16.5,(1.-uv.y)*96.);
  faceNormal=normalize(faceNormal+normalize(Across)*sign(uv.x-.5)*side*.235*ridge);
  vec3 fine=normalize(normalize(Across)*side*mapNormal.x+normalize(Axis)*mapNormal.y+faceNormal*mapNormal.z);
  float y=(1.-uv.y)*96.,x=(uv.x-.5)*7.6;
  float h=3.8*(1.-pow(1.-clamp(y/5.534,0.,1.),1.503));
  N=normalize(mix(Ng,fine,smoothstep(.20,.25,h-abs(x))*smoothstep(.35,.5,y)));
  cavity=clamp((.8-relief.a)/.5,0.,1.);
  cavityLight=1.-cavity*.29;
  rune=runeGlow(cavity,y,smoothstep(.12,.3,h-abs(x)));
 }else{
  albedo=vec3(.80,.47,.29);hotMask=.35;
 }
 if(dot(N,V)<0.0)N=-N;
 vec3 L=normalize(Light);
 float ndl=dot(N,L);
 float wrap=max((ndl+.4)/1.4,0.0);
 vec3 H=normalize(L+V);
 float ndv=max(dot(N,V),.02),ndh=max(dot(N,H),0.),vdh=max(dot(V,H),0.);
 // Object-locked, filtered brushing: a broad satin face and polished lips.
 // The engraving remains the original mesh and eight-kilopixel relief map.
 vec2 metalUV=vec2((uv.x-.5)*7.6,(1.-uv.y)*96.);
 float brushPhase=metalUV.x*240.+.45*sin(metalUV.y*1.7);
 float brush=sin(brushPhase)*exp(-.5*pow(fwidth(brushPhase),2.));
 float temper=.5+.5*sin(metalUV.x*2.7+sin(metalUV.y*.63));
 albedo*=1.+.018*brush+.025*(temper-.5);
 float rough=.32+.13*cavity+.035*temper;
 // Normal variance widens highlights before the relief becomes subpixel.
 float normalVariance=max(dot(dFdx(N),dFdx(N)),dot(dFdy(N),dFdy(N)));
 rough=clamp(sqrt(rough*rough+min(.16,normalVariance*.32)),.30,.57);
 float aa=rough*rough*rough*rough;
 float distribution=aa/(3.14159*pow(ndh*ndh*(aa-1.)+1.,2.));
 float k=pow(rough+1.,2.)/8.;
 float geometry=ndv/(ndv*(1.-k)+k)*max(ndl,0.)/(max(ndl,0.)*(1.-k)+k);
 vec3 f0=mix(vec3(.04),albedo,.82);
 vec3 fresnel=f0+(1.-f0)*pow(1.-vdh,5.);
 vec3 spec=distribution*geometry*fresnel/max(4.*ndv*max(ndl,0.),.03);
 float rim=pow(1.0-max(dot(N,V),0.0),3.0);
 float sky=.5+.5*N.y;
 vec3 col=albedo*(vec3(.23,.29,.38)*sky+vec3(.20,.15,.10)+vec3(1.0,.85,.68)*wrap*.81)*cavityLight;
 vec3 sunSpec=spec*max(ndl,0.);
 col+=vec3(1.0,.92,.79)*(sunSpec/(1.+sunSpec*1.8))*.92*cavityLight;
 vec3 reflected=reflect(-V,N);
 float skyReflection=pow(max(dot(reflected,normalize(L+vec3(0,.3,0))),0.),5.);
 col+=f0*vec3(.54,.66,.86)*skyReflection*.42*cavityLight;
 col+=vec3(1.0,.83,.60)*rim*.17*cavityLight;
 // its point burning: a hot glow over it, whitening where it is hottest
 float bladeY=(1.-uv.y)*96.;
 float contactY=bladeY;
 if(Storm<.5){
  float join=clamp(bladeY-5.534,0.,1.);
  contactY=.86*bladeY+.07*join*join+.14*max(bladeY-6.534,0.);
 }
 float contact=step(.001,Buried)*exp(-max(0.,contactY-Buried)/1.65)*.76;
 float hot=Heat*max(hotMask,contact);
 col+=vec3(1.0,.58,.21)*hot*.49+vec3(1.0,.94,.82)*hot*hot*.64;
 // Grazing light catches the same bevel that the close shot resolves. Keep
 // a subpixel bevel legible; its width comes from the projected footprint.
 float localX=abs((uv.x-.5)*7.6);
 float halfWidth=3.8*(1.-pow(1.-clamp(bladeY/5.534,0.,1.),1.503));
 float width=max(.11,min(.43,1.15*fwidth(localX)));
 float bevel=1.-smoothstep(.0,width+.14,abs(halfWidth-localX));
 if(Edge==0 && tint.g<.5) {
  // Sky and sun reflected by the polished face. Resolve the large light-to-
  // shadow transition independently from the finer, filtered relief.
  float acrossFace=(uv.x-.5)*7.6/max(halfWidth,.2);
  float sideLight=dot(normalize(Across),L)>=0.?1.:-1.;
  float turn=dot(normalize(Across),V)*.28;
  float s=acrossFace*sideLight;
  float horizon=smoothstep(-.44,.40,s+turn);
  float streak=exp(-pow((s-.64+turn+.08*sin(bladeY*.16))/.20,2.));
  // Wide reflected sky/sun bands read as metal even in the stationary close
  // shot. Their intensity is occluded by the actual recessed inscription.
  float visibility=mix(.78,.92,Storm)*(1.-.28*cavity);
  vec3 metal=col*mix(vec3(.67,.77,.97),vec3(1.06,.99,.88),horizon);
  float coldBand=exp(-pow((s+.38+turn)/.34,2.));
  metal+=vec3(.10,.16,.25)*coldBand*cavityLight;
  metal+=(vec3(.16,.115,.055)*horizon+vec3(.48,.38,.23)*streak)*cavityLight;
  float crest=exp(-pow(acrossFace/.11,2.))*smoothstep(3.5,13.,bladeY);
  metal+=vec3(.52,.43,.28)*crest*Storm*cavityLight;
  col=mix(col,metal,visibility);
 }
 col+=vec3(.86,.74,.50)*bevel*(.12+.36*coarse)*(.56+.44*Heat);
 col=mix(col,col*vec3(.26,.22,.21),Dark);
 // The distant engraving loses warm contrast over tens of metres, while the
 // approaching lower blade retains its bronze relief and white-hot edge.
 col=mix(col,vec3(.62,.70,.77),smoothstep(5.,22.,bladeY)*.65*Storm);
 // (2.88.0) the burning inscriptions light through the storm's haze, a little dimmed by it far up the blade
 col+=runeLight(rune)*(1.-.45*smoothstep(5.,22.,bladeY)*Storm)*(1.-Dark);

 fragColor=vec4(col,shown)*ColorModulator;
}
