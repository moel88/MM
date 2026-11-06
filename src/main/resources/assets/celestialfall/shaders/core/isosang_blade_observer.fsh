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
 vec2 sampleUV=uv;float cavityLight=1.,rune=0.;
 vec2 texel=vec2(textureSize(Sampler0,0));
 float footprint=max(length(dFdx(uv)*texel),length(dFdy(uv)*texel));
 float coarse=smoothstep(9.,23.,footprint);
 float lod=max(0.,log2(max(1.,footprint))-.35+1.05*coarse);
 vec4 tx=readSurface(Sampler0,sampleUV,lod,coarse);
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
  vec4 relief=readSurface(Sampler1,sampleUV,lod,coarse);
  vec3 mapNormal=relief.xyz*2.-1.;
  vec3 faceNormal=normalize(cross(Across,Axis))*side;
  float ridge=.5*(smoothstep(3.5,16.5,(1.-uv.y)*96.)+smoothstep(0.,6.,(1.-uv.y)*96.));
  faceNormal=normalize(faceNormal+normalize(Across)*(uv.x-.5)*side*.285*ridge);
  vec3 fine=normalize(normalize(Across)*side*mapNormal.x+normalize(Axis)*mapNormal.y+faceNormal*mapNormal.z);
  float y=(1.-uv.y)*96.,x=(uv.x-.5)*7.6;
  float h=3.8*(1.-pow(1.-clamp(y/5.534,0.,1.),1.503));
  N=normalize(mix(Ng,fine,smoothstep(.20,.25,h-abs(x))*smoothstep(.35,.5,y)));
  cavityLight=1.-clamp((.8-relief.a)/.5,0.,1.)*.18;
  rune=runeGlow(clamp((.8-relief.a)/.5,0.,1.),y,smoothstep(.12,.3,h-abs(x)));
 }else{
  albedo=vec3(.80,.47,.29);hotMask=.35;
 }
 if(dot(N,V)<0.0)N=-N;
 vec3 L=normalize(Light);
 float ndl=dot(N,L);
 float wrap=max((ndl+.4)/1.4,0.0);
 vec3 H=normalize(L+V);
 float ndv=max(dot(N,V),.02),ndh=max(dot(N,H),0.),vdh=max(dot(V,H),0.);
 float rough=.48,aa=rough*rough*rough*rough;
 float distribution=aa/(3.14159*pow(ndh*ndh*(aa-1.)+1.,2.));
 float k=pow(rough+1.,2.)/8.;
 float geometry=ndv/(ndv*(1.-k)+k)*max(ndl,0.)/(max(ndl,0.)*(1.-k)+k);
 vec3 f0=mix(vec3(.04),albedo,.68);
 vec3 fresnel=f0+(1.-f0)*pow(1.-vdh,5.);
 vec3 spec=distribution*geometry*fresnel/max(4.*ndv*max(ndl,0.),.03);
 float rim=pow(1.0-max(dot(N,V),0.0),3.0);
 float sky=.5+.5*N.y;
 vec3 col=albedo*(vec3(.24,.29,.36)*sky+vec3(.25,.19,.13)+vec3(1.0,.83,.66)*wrap*.85*cavityLight);
 col+=vec3(1.0,.89,.72)*spec*max(ndl,0.)*.40*cavityLight;
 vec3 reflected=reflect(-V,N);
 float skyReflection=pow(max(dot(reflected,normalize(L+vec3(0,.3,0))),0.),5.);
 col+=f0*vec3(.43,.53,.68)*skyReflection*.24*cavityLight;
 col+=vec3(1.0,.72,.46)*rim*.12;
 // its point burning: a hot glow over it, whitening where it is hottest
 float bladeY=(1.-uv.y)*96.;
 float contact=step(.001,Buried)*exp(-max(0.,bladeY-Buried)/1.35)*.72;
 float hot=Heat*max(hotMask,contact);
 col+=vec3(1.0,.60,.26)*hot*.45+vec3(1.0,.93,.83)*hot*hot*.62;
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
  float turn=dot(normalize(Across),V)*.15;
  float s=acrossFace*sideLight;
  float horizon=smoothstep(-.44,.40,s+turn);
  float streak=exp(-pow((s-.64+turn+.08*sin(bladeY*.16))/.20,2.));
  float visibility=max(coarse,.82*smoothstep(3.5,16.5,bladeY))*smoothstep(9.,28.,length(pos));
  vec3 metal=col*mix(vec3(.66,.73,.85),vec3(1.02,.97,.88),horizon);
  metal+=vec3(.29,.21,.105)*horizon+vec3(.52,.39,.19)*streak;
  float crest=exp(-pow(acrossFace/.17,2.))*smoothstep(3.5,13.,bladeY);
  metal+=vec3(.72,.54,.28)*crest;
  col=mix(col,metal,visibility);
 }
 col+=coarse*vec3(.86,.69,.41)*bevel*(.56+.44*Heat);
 col=mix(col,col*vec3(.26,.22,.21),Dark);
 // The distant engraving loses warm contrast over tens of metres, while the
 // approaching lower blade retains its bronze relief and white-hot edge.
 col=mix(col,vec3(.62,.70,.77),smoothstep(5.,22.,bladeY)*.65*Storm);
 // (2.88.0) the burning inscriptions, as the caster's cutscene shows them (burning on as the metal darkens)
 col+=runeLight(rune);

 fragColor=vec4(col,shown)*ColorModulator;
}
