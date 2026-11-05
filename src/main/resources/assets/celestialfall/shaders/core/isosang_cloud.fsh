#version 150
in vec2 screen;
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InverseViewProjection;
uniform vec3 Origin;
uniform vec3 BasisX;
uniform vec3 BasisY;
uniform vec3 BasisZ;
uniform vec3 BladePoint;
uniform vec3 Sun;
uniform vec3 BladeAcross;
uniform vec3 BladeAxis;
uniform vec3 BladeNormal;
uniform float Age;
uniform int Mode;
uniform float DistantView;
uniform float BurstRadius;
uniform vec4 ArenaRect;
out vec4 fragColor;

vec3 coordinates(vec3 p){return vec3(dot(p,BasisX),dot(p,BasisY),dot(p,BasisZ));}
vec3 noise3(vec3 p){
 vec3 q=fract(p)*128.;float iz=floor(q.z),iz2=mod(iz+1.,128.);
 vec2 tile=vec2(mod(iz,16.),floor(iz/16.))*130.;
 vec2 tile2=vec2(mod(iz2,16.),floor(iz2/16.))*130.;
 vec2 xy=q.xy+vec2(1.);
 return mix(texture(Sampler0,(tile+xy)/vec2(2080.,1040.)).rgb,
            texture(Sampler0,(tile2+xy)/vec2(2080.,1040.)).rgb,fract(q.z));
}
vec3 bladeCoordinates(vec3 p){
 vec3 q=p-BladePoint;
 float determinant=dot(BladeAcross,cross(BladeAxis,BladeNormal));
 return vec3(dot(q,cross(BladeAxis,BladeNormal)),dot(q,cross(BladeNormal,BladeAcross)),dot(q,cross(BladeAcross,BladeAxis)))/determinant;
}
float bladeDistance(vec3 p){
 vec3 q=bladeCoordinates(p);float x=q.x,y=q.y,z=q.z;
 float h=3.8*(1.-pow(1.-clamp(y/5.534,0.,1.),1.503));
 return max(abs(z)-.375*clamp(y/.45,0.,1.),max(abs(x)-h,max(-y,y-96.)));
}
// The density and illumination of a finite, optically thick cloud bank. All lookups
// use cast-local space. A camera behind the shelf samples its back and side walls.
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform vec3 CloudCenter;

const vec2 projectionScale = vec2(.40066, -.86915);
const vec2 eyeCenter = vec2(.546, .399);

vec2 cloudUV(vec3 p) {
    vec2 uv = .5 + projectionScale * p.xy / max(p.z, .5);
    vec2 d = uv - eyeCenter;
    float elapsed = clamp(Age - 2.367, -.75, 1.);
    vec2 velocity=(texture(Sampler3,clamp(uv,vec2(.002),vec2(.998))).gb-.5)*.24;
    vec2 shift=velocity*elapsed;
    float gather = 1. - smoothstep(.40, 1.63, Age);
    float angle = .27*gather + elapsed*.010*exp(-dot(d,d)*15.);
    float c = cos(angle), s = sin(angle);
    return eyeCenter + mat2(c,s,-s,c)*(d-shift)*(1.-.28*gather);
}

vec2 foldedUV(vec2 uv) {
    // Beyond the reference sector the weather continues around the back of each bank.
    return 1.-abs(mod(uv, 2.)-1.);
}

vec3 daylight(vec3 properties) {
    float y = properties.r, blue = properties.g-.30;
    // Reconstruct the cool daylight spectrum from fitted luminance and blue-red contrast.
    float middle=clamp(.4876+.2150*y-1.6231*blue+.5286*y*blue-.2773*y*y+4.2537*blue*blue,.19,.68);
    float red=y-(.7152*middle+.0722)*blue;
    return red+vec3(0.,middle*blue,blue);
}

vec4 sampleCloud(vec3 p, vec3 ray, float opening, float gathering) {
    vec2 uv = cloudUV(p);
    vec2 off = (uv-eyeCenter)*vec2(2.167,1.);
    float r = length(off), theta=atan(off.y,off.x);
    float d = length((p.xy-vec2(1.,.8))/vec2(28.,23.));
    float border = d + .072*sin(theta*3.+.3) + .039*cos(theta*7.+.9);
    float extent = 1.-smoothstep(.85,1.12,border);
    if (extent<.001 || p.z<1.2 || p.z>36.) return vec4(0.);

    vec3 flow = p + vec3(.12*Age,-.17*Age,-.25*Age);
    vec3 coarse = noise3(flow*.032+vec3(.12,.5,.34));
    vec2 drift = (coarse.rg-.5)*vec2(.002,.003);
    vec4 bank=texture(Sampler2,foldedUV(uv+drift));
    vec3 prop=bank.rgb;
    float light = prop.r, ridge=prop.b;
    // Near scalloped banks, then the backlit turned lip, then the partly open core.
    float outer = smoothstep(.26,.88,r);
    float ridgeDepth = 16.9 - 6.8*outer + 1.8*sin(3.*theta+.6)*outer
                      - (ridge-.53)*9.8 + (coarse.r-.5)*1.4;
    float mouth = 1.-smoothstep(.19,.44,r);
    // Oblique views expose the domed crown and the billows curling under its edges.
    float lip= smoothstep(.63,1.06,border);
    float sideRelief=noise3(flow*.067+vec3(.73,.14,.61)).r;
    float front = ridgeDepth+2.8*mouth+lip*(2.2+4.8*(sideRelief-.5));
    front+=2.3*(1.-gathering)*cos(theta*3.+r*3.2);
    float roll = sin(2.8*r-2.2*theta);
    float back = front + (4.8+4.*ridge+1.2*roll)*(1.-.70*lip)+2.1*(coarse.g-.5);
    float approach=front-p.z;
    float shell=(1.-smoothstep(-1.35,1.45,approach))*(1.-smoothstep(back-1.1,back+1.3,p.z));
    float channel=mix(1.,.64,mouth*opening);
    float erosion=noise3(flow*.125+vec3(.61,.19,.58)).g;
    float density=shell*channel*(.76+.37*ridge+.25*(erosion-.5));
    // Radial inrush before the first blade shot: compact, isolated rolls coalesce.
    float clump=.5+.22*sin(3.*theta+r*7.)+.15*cos(5.*theta-r*4.)+.26*(coarse.r-.5);
    density*=smoothstep(.71*(1.-gathering),.72*(1.-gathering)+.20,clump);
    float edge=smoothstep(-1.7,1.35,approach);
    vec3 col = daylight(prop) + vec3(.044,.043,.038)*(erosion-.5)*edge;
    col += vec3(.016,.015,.012)*edge*max(0.,dot(normalize(Sun),normalize(vec3(coarse.xy-.5,-.8))));
    col += vec3(.027,.025,.017)*edge*(coarse.g-.5);
    vec3 backlight = vec3(.39,.49,.62)+vec3(.20,.23,.24)*ridge;
    col=mix(col, backlight, .53*smoothstep(.1,.9,-ray.z));
    // Detached white condensate shears forwards around the shoulders of the blade.
    vec3 q=bladeCoordinates(p);
    float wind=Age-1.64;
    float az=atan(q.z*.9,q.x);
    float pressure=length(vec2(q.x*.92,q.z*1.12));
    float at=3.18+.44*sin(q.y*.49-az*2.-wind*3.2)+.54*(erosion-.5);
    float veil=exp(-pow((pressure-at)/.64,2.))*smoothstep(4.2,10.9,q.y)
              *(1.-smoothstep(17.,25.,q.y))*smoothstep(1.51,1.67,Age);
    veil*=.33+.67*smoothstep(.16,.81,erosion+.23*sin(az+q.y*.58-wind*2.4));
    vec2 throat=(uv-vec2(.542,.416))/vec2(.146,.296);
    float rim=length(throat)+.055*(erosion-.5)+.024*sin(atan(throat.y,throat.x)*9.+wind*.9);
    float shoulder=smoothstep(.75,1.18,rim)*(1.-smoothstep(1.32,1.82,rim));
    shoulder*=1.-smoothstep(.49,.66,uv.y);
    float nearLip=7.5+2.9*(1.-smoothstep(.08,.50,uv.y))+2.1*(coarse.g-.5);
    float scallop=exp(-pow((p.z-nearLip)/(1.05+.52*erosion),2.));
    float grain=noise3(flow*.28+vec3(.14,.31,-wind*.085)).g;
    float thickness=-log(max(.033,1.-clamp(bank.a+.11*(grain-.5)*bank.a*(1.-bank.a),0.,1.)));
    float frost=(.15*shoulder+thickness*.55)*scallop*smoothstep(1.48,1.69,Age)*(.80+.28*erosion);
    vec3 lipColor=mix(daylight(prop),vec3(.78,.85,.91),.045+.070*edge);
    col=(col*density+lipColor*frost)/max(.0001,density+frost);density+=frost;

    // A transonic front crosses the banks in roughly a quarter-second. The table
    // stores its scattering versus azimuth and signed distance from the wave.
    vec2 shock=(uv-vec2(.527,.390))*vec2(2.167,1.);
    float waveRadius=.425+3.51*(Age-1.66);
    float sdist=length(shock)-waveRadius;
    float waveUV=sdist/.78+.5;
    float pulse=0.;
    if(abs(sdist)<.39 && Age>1.55 && Age<1.99) {
        float azimuth=atan(shock.y,shock.x)/6.2831853+.5;
        pulse=texture(Sampler3,vec2(azimuth,clamp(waveUV,.01,.99))).r-.30;
        pulse*=smoothstep(1.57,1.66,Age)*(1.-smoothstep(1.89,2.02,Age))*exp(-max(0.,Age-1.76)*4.5);
    }
    if(Age>1.60 && Age<2.367) {
        vec2 at=clamp(uv,vec2(.002),vec2(.998));
        vec4 response=texture(Sampler4,at);
        float peak=1.60+.50*response.r, age=Age-peak;
        float width=.01+.15*response.b;
        float bell=exp(-.5*pow(age/(age<0.?width:1.4*width),2.));
        float scattering=(response.g*.96-.2)*bell+(response.a*.56-.28)*exp(-max(0.,Age-1.68)/.225);
        float calibrated=texture(Sampler3,at).a;
        calibrated*=1.-smoothstep(.002,.04,length(uv-at));
        pulse=mix(pulse,scattering*smoothstep(1.60,1.65,Age)*(1.-smoothstep(2.18,2.367,Age)),calibrated);
    }
    col+=vec3(.98,1.,1.02)*pulse;
    float warm=exp(-max(0.,bladeDistance(p))*.78)*(1.-smoothstep(2.,11.,q.y))*opening;
    col+=vec3(.16,.093,.025)*warm;
    // Thin condensate rides the lower blade's silhouette, where the distant
    // bank cannot hide it. Two broken helical sheets shear upwards as steel
    // falls through them. Depth integration occludes the back half correctly.
    float halfBlade=3.8*(1.-pow(1.-clamp(q.y/5.534,0.,1.),1.503));
    halfBlade*=1.+clamp((q.y-5.534)*.43/3.8,0.,2.52);
    float frontDepth=.8+2.65*smoothstep(3.5,16.5,q.y);
    vec2 swept=vec2(q.x/max(.65,halfBlade+.65),q.z/(frontDepth+.85));
    float airAngle=atan(swept.y,swept.x);
    float airRadius=1.+.06*sin(q.y*1.1-airAngle*2.-wind*5.2);
    float skin=exp(-pow((length(swept)-airRadius)/.12,2.));
    float thread=.5+.5*sin(airAngle*2.-q.y*1.22+wind*6.2);
    float slipstream=skin*smoothstep(.30,.88,thread+.24*(grain-.5));
    slipstream*=smoothstep(.15,1.4,q.y)*(1.-smoothstep(6.5,10.5,q.y))
               *smoothstep(1.51,1.72,Age)*(.70+.30*erosion);
    float vapor=.27*veil+.36*slipstream;
    col=(col*density+vec3(.81,.87,.92)*vapor)/max(.0001,density+vapor);
    // Oblique rays see the folds from the side. Blend into the light transported
    // through their depth so the reference-direction material does not stretch across the flank.
    float facing=dot(ray,normalize(p));
    vec3 up=vec3(BasisX.y,BasisY.y,BasisZ.y);
    float facingSun=.45+.24*dot(normalize(p-CloudCenter+.01),Sun)+.43*(coarse.g-.5)+.42*(erosion-.5)
                    +.22*clamp(dot(p-CloudCenter,up)/12.,-1.,1.);
    vec3 flank=mix(vec3(.34,.44,.60),vec3(.94,.955,.975),smoothstep(.05,.90,facingSun));
    col=mix(flank,col,smoothstep(.90,.995,facing));
    return vec4(col,(density+vapor)*extent*smoothstep(.35,1.03,Age));
}

// Broken, horizontally stratified cumulus around the opening. These banks extend
// around the far side of the sword. Their silhouettes depend only on world position.
vec4 outerBanks(vec3 p, vec3 ray, float gathering) {
    vec3 up=vec3(BasisX.y,BasisY.y,BasisZ.y);
    vec3 east=normalize(BladeAcross-up*dot(BladeAcross,up));
    vec3 south=cross(east,up);
    vec3 v=p-CloudCenter;
    vec2 h=vec2(dot(v,east),dot(v,south));
    float height=dot(v,up);
    if(abs(height-5.)>13.) return vec4(0.);
    float len=length(h), ph=atan(h.y,h.x);
    float a=ph+.13*gathering;
    float edge=18.+2.9*cos(3.*a-.7)+1.7*sin(5.*a+.3)+9.*(1.-gathering);
    float lobes=.50+.28*cos(3.*a+.6)+.16*sin(5.*a-.5)+.10*cos(9.*a);
    float width=3.9+6.2*lobes, high=3.0+3.8*lobes;
    if(abs(len-edge)>width*1.65) return vec4(0.);
    vec3 adv=vec3(h.x*.064,height*.105,h.y*.064)+vec3(.17,.48,-.21)*Age*.06;
    vec3 macro=noise3(adv+.23),detail=noise3(adv*2.5+.66);
    float crown=5.4+2.7*sin(2.*a+.8)+1.7*cos(5.*a)+2.9*(macro.g-.5);
    vec2 q=vec2((len-edge)/width,(height-crown)/high);
    float bank=1.-dot(q,q)+1.3*(macro.r-.50)+.57*(detail.g-.5);
    float den=smoothstep(-.22,.62,bank)*smoothstep(.06,.48,lobes+.20*gathering);
    // Do not fill the excavated throat. The back bank is visible through its thin parts.
    vec2 uv=.5+projectionScale*p.xy/max(p.z,.6);
    vec2 outside=max(-uv,uv-1.);
    float beyond=smoothstep(-.025,.18,max(outside.x,outside.y));
    float inFront=smoothstep(.3,2.8,p.z)*(1.-beyond);
    den*=1.-inFront*(1.-smoothstep(22.,33.,p.z));
    if(den<.001) return vec4(0.);
    float sunFace=clamp(.47+.32*q.y+.40*(macro.r-.50)+.40*(detail.r-.50)
                    +.22*q.x*dot(normalize(h+.001),vec2(-.8,.6)),0.,1.);
    vec3 color=mix(vec3(.35,.46,.63),vec3(.95,.965,.975),smoothstep(.17,.87,sunFace));
    float silver=exp(-2.3*max(0.,bank))*(.48+.52*sunFace);
    color+=vec3(.12,.125,.12)*silver;
    return vec4(color,den*smoothstep(.35,1.06,Age));
}

vec4 marchCanopy(vec3 origin, vec3 ray, float limit) {
    vec3 safeRay = mix(ray, vec3(.00001), lessThan(abs(ray),vec3(.00001)));
    vec3 a=(vec3(-50.,-48.,-42.)-origin)/safeRay;
    vec3 b=(vec3(50.,48.,54.)-origin)/safeRay;
    vec3 lo=min(a,b),hi=max(a,b);
    float start=max(0.,max(lo.x,max(lo.y,lo.z)));
    float end=min(limit,min(hi.x,min(hi.y,hi.z)));
    if(start>=end) return vec4(0.);
    float opening=smoothstep(1.42,1.83,Age);
    float gathering=smoothstep(.35,1.50,Age);
    float stepSize=max(.21,(end-start)/167.);
    float jitter=fract(dot(gl_FragCoord.xy,vec2(.754877666,.569840296)));
    vec3 radiance=vec3(0.);
    float trans=1.;
    for(int i=0;i<168;i++) {
        float t=start+(float(i)+jitter)*stepSize;
        if(t>=end || trans<.008) break;
        vec3 point=origin+ray*t;
        vec4 cloud=sampleCloud(point,ray,opening,gathering);
        vec4 bank=outerBanks(point,ray,gathering);
        cloud.rgb=(cloud.rgb*cloud.a+bank.rgb*bank.a)/max(.0001,cloud.a+bank.a);
        cloud.a+=bank.a;
        float alpha=1.-exp(-max(0.,cloud.a)*stepSize*1.28);
        radiance+=trans*alpha*cloud.rgb;trans*=1.-alpha;
    }
    float coverage=1.-trans;
    return coverage>.0005?vec4(radiance/coverage,coverage):vec4(0.);
}

// Pressure, dust, hot jets, and soil have distinct timing and geometry.
// They are not a single white expanding cloud.
float square(float x) { return x * x; }
// In a free view the whole cast must read against the landscape. Sample a shallow
// horizontal cloud deck, a hollow moving wake and ground pressure in cast space.
// Three offset banks approach from different directions and roll out along the
// opening. The low torn bank overlaps the upper blade in real world depth.
float bankShape(vec3 p,vec3 dimensions){
 vec3 v=p/dimensions;return 1.-dot(v,v);
}
vec4 observerMedium(vec3 p,float gather,float breach){
 vec3 wind=vec3(.68,.07,-.46)*Age;
 vec3 n=noise3((p-wind)*vec3(.038,.075,.036)+vec3(.3,.2,.45));
 vec3 fine=noise3((p-1.6*wind)*.134+vec3(.53,.24,.65));
 vec2 rim=p.xz;float r=length(rim),a=atan(rim.y,rim.x);
 float curl=1.9*sin(2.*a-.8*Age)+1.35*cos(3.*a+Age*.45);
 float escort=1.-gather,slide=11.*escort+2.8*breach;
 vec3 drift=p+vec3(5.*(n.r-.5),3.*(n.b-.5),5.*(n.g-.5));
 float left=bankShape(drift-vec3(-23.-slide,6.,-7.),vec3(28.,6.3,18.));
 float right=bankShape(drift-vec3(23.+slide,12.,4.+slide*.4),vec3(34.,8.2,22.));
 float high=bankShape(drift-vec3(-2.,17.,-21.-slide),vec3(45.,7.,26.));
 float outline=max(max(left,right),high)+1.14*(n.r-.5)+.48*(fine.g-.5);
 float eye=smoothstep(3.6+6.2*breach,12.+7.6*breach,r+curl+4.*(n.g-.5));
 float deck=smoothstep(-.25,.58,outline)*(.018+.982*eye);
 float skirtR=11.6+6.3*breach+curl;
 float skirt=1.-square((r-skirtR)/6.3)-square((p.y-1.3-2.8*sin(a+.5))/3.3);
 float tear=smoothstep(.12,.74,.50+.35*sin(2.*a+.7)+.48*(n.b-.5));
 float low=smoothstep(-.22,.65,skirt+.98*(fine.r-.5))*tear;
 float density=(deck+.78*low)*gather;
 float sunFace=clamp(.26+.36*clamp((p.y-6.)/12.,0.,1.)+.18*cos(a+.75)+.68*(n.r-.5)+.45*(fine.g-.5),0.,1.);
 vec3 bankColor=mix(vec3(.49,.60,.75),vec3(.98,.98,.955),smoothstep(.06,.90,sunFace));
 float silver=exp(-square((r-skirtR)/6.6))*breach*(.7+.3*cos(a+.8));
 bankColor+=vec3(.25,.16,.053)*silver;
 // Sidelit vapor condenses along both shoulders, then is thrown outwards
 // on contact. Large loose sheets briefly cross the steel, never sheath it.
 vec3 q=bladeCoordinates(p);float ax=atan(q.z,q.x),height=q.y;
 float halfW=3.8*(1.-pow(1.-clamp(height/5.534,0.,1.),1.503))*(1.+clamp((height-5.534)*.43/3.8,0.,2.52));
 float winding=ax*1.64+height*.145-Age*3.1;
 float radial=length(vec2(q.x,q.z*1.65));
 float spiral=exp(-square((radial-(halfW+1.4+.08*height+1.3*sin(winding)))/1.27));
 float arc=smoothstep(.18,.86,sin(winding)+.73*(fine.g-.5));
 float sleeve=spiral*arc*smoothstep(1.,5.,height)*(1.-smoothstep(33.,48.,height));
 sleeve*=.48*smoothstep(1.56,2.4,Age)*(1.-smoothstep(3.10,3.72,Age));
 float z=p.y;
 vec2 beamCoord=p.xz-vec2(.38*z,-.22*z);
 float beams=exp(-square(length((beamCoord-vec2(-8.,2.))*vec2(.81,1.))/3.2))
            +.65*exp(-square(length((beamCoord-vec2(3.,6.))*vec2(.67,1.))/2.6));
 beams*=breach*.069*smoothstep(-41.,-17.,z)*(1.-smoothstep(4.,13.,z))*(.7+.3*n.b);
 float lit=density+sleeve+beams;
 return vec4((bankColor*density+vec3(.91,.95,1.)*sleeve+vec3(1.,.85,.57)*beams)/max(lit,.0001),lit);
}
vec4 observerSky(vec3 origin, vec3 ray, float limit) {
    vec3 d=mix(ray,vec3(.00001),lessThan(abs(ray),vec3(.00001)));
    vec3 a=(vec3(-68.,-42.,-68.)-origin)/d,b=(vec3(68.,29.,68.)-origin)/d;
    vec3 lo=min(a,b),hi=max(a,b);
    float near=max(0.,max(lo.x,max(lo.y,lo.z))),far=min(limit,min(hi.x,min(hi.y,hi.z)));
    float gather=smoothstep(.35,1.42,Age),breach=smoothstep(1.42,2.34,Age);
    if(far<=near || gather<.001) return vec4(0.);
    float stepSize=max(.30,(far-near)/179.);
    float j=fract(dot(gl_FragCoord.xy,vec2(.75487767,.56984029)));
    float tr=1.;vec3 light=vec3(0.);
    for(int i=0;i<180;i++) {
        float t=near+(float(i)+j)*stepSize;
        if(t>far || tr<.009) break;
        vec4 cloud=observerMedium(origin+t*ray,gather,breach);
        float k=1.-exp(-cloud.a*stepSize*.78);
        light+=tr*k*cloud.rgb;tr*=1.-k;
    }
    return vec4(light/max(1.-tr,.0001),1.-tr);
}

// Dust driven across the arena has a low leading edge and an irregular
// curling back wall. Two higher lobes are aligned with the blade's plane.
vec4 distantImpact(vec3 p,float t){
 if(t<=0. || p.y<=0.)return vec4(0.);
 float r=length(p.xz),a=atan(p.z,p.x);
 vec2 side=normalize(BladeAcross.xz);
 vec2 polar=p.xz/max(r,.01);
 float face=abs(dot(polar,side));
 vec3 n=noise3(p*vec3(.11,.16,.11)-vec3(t*.28,t*.36,0.));
 vec3 breakup=noise3(p*.41-vec3(t*.34,t*.65,t*.16)+vec3(.24,.68,.31));
 vec3 grain=noise3(p*.93-vec3(t*.61,t*.91,t*.45)+vec3(.52,.21,.63));
 float lobes=.50+.20*cos(5.*a+.7)+.19*sin(3.*a-.35)+.11*sin(9.*a+1.);
 float front=(31.+3.*sin(3.*a)+2.*cos(5.*a))*(1.-exp(-1.75*t));
 float s=smoothstep(.015,.11,t),decay=1.-smoothstep(.65,2.34,t);
 float crest=exp(-square((r-front)/(.65+.80*t))-square((p.y-.43)/(.42+.22*t)));
 float arc=crest*s*(1.-smoothstep(.48,1.34,t))*(.24+.49*lobes)
           *smoothstep(.16,.67,lobes+.36*(n.b-.5));
 float plumeRadius=(.55+.18*lobes)*front;
 float height=(1.1+6.8*lobes+2.7*face)*sin(3.14159*clamp(t/2.45,0.,1.));
 float push=(r-plumeRadius)/(1.1+1.75*t+1.7*lobes);
 float crestY=height*exp(-square(push+.38))+.60;
 float shape=1.-square(push)-square((p.y-crestY)/(.85+1.75*lobes));
 shape+=1.18*(n.g-.5)+.81*(breakup.r-.5)+.30*(grain.g-.5);
 float cap=smoothstep(-.16,.76,shape);
 float veil=exp(-square(push+.4))*smoothstep(0.,1.2,p.y)
           *(1.-smoothstep(crestY-.5,crestY+1.4,p.y+1.3*(breakup.b-.5)))
           *smoothstep(.22,.74,breakup.g);
 float textureAmount=smoothstep(.20,.76,.31+.49*n.g+.28*(breakup.r-.5));
 float dust=(cap*.80+veil*.32)*textureAmount*s*decay;
 float drive=smoothstep(.12,.39,t)*(1.-smoothstep(2.07,2.42,t));
 float shove=fract(t*.89+.07*sin(3.*a));float rad=2.8+9.3*shove;
 dust+=drive*(.13+.31*n.g)*sin(3.14159*shove)*exp(-square((r-rad)/1.55)-square((p.y-1.12)/1.30));
 // Oblique fans of ejecta diverge along the actual edge, leaving the face visible.
 float x=dot(p.xz,side),z=dot(p.xz,vec2(-side.y,side.x));
 float fan=exp(-square((abs(x)-(.60+.91*p.y))/(.45+.064*p.y))-square(z/(1.+.055*p.y)));
 float burst=exp(-t*3.8)*(1.-smoothstep(7.,14.,p.y));
 float root=exp(-square((abs(x)-(1.+t*1.0))/.55)-square(z/.77)-p.y/1.93);
 float fire=s*fan*(burst*(.65+1.15*smoothstep(.3,.72,n.b))+.26*drive*exp(-p.y/2.3))+.83*drive*root;
 float soil=cap*s*decay*textureAmount*(.13+.3*lobes)*exp(-p.y/3.);
 return vec4(arc,dust,fire,soil)*smoothstep(0.,.06,p.y);
}

// A shadow before impact and radial fissures after it register the weight on
// opaque map geometry. This pass never changes blocks or combat hitboxes.
vec4 arenaMark(vec3 p,float age){
 float r=length(p.xz);if(r>36. || abs(p.y)>.85)return vec4(0.);
 float t=age-3.10;
 if(t<0.)return vec4(0.);
 float a=atan(p.z,p.x);float count=11.;
 float branch=floor((a+3.141593)*count/6.283185);
 float reach=(13.+14.*fract(sin(branch*53.12+1.78)*342.56))*(1.-exp(-2.7*t));
 float angle=(a+3.141593)*count/6.283185;
 angle+=.12*sin(r*.81+branch)+.074*sin(r*1.63+branch*.5);
 float gap=abs(fract(angle)-.52)*r*6.283185/count;
 float sideGap=abs(fract(angle+.18*sin(r*.15))-.58)*r*6.283185/count;
 float envelope=(1.-smoothstep(reach-2.2,reach,r))*smoothstep(.9,2.8,r)*(1.-smoothstep(2.38,3.45,t));
 float crack=exp(-square(gap/(.25+.16*exp(-r/11.))))+.44*exp(-square(sideGap/.15));
 float core=exp(-square(gap/.115))*(.65+.35*sin(r*.72-t*4.+branch))*(1.-smoothstep(1.9,2.95,t));
 return vec4(mix(vec3(.18,.13,.115),vec3(1.,.77,.34),core),clamp(crack*envelope*.92,0.,.91));
}
vec4 impactField(vec3 p){
 float t=max(Age-3.1,0.),r=length(p.xz),ph=atan(p.z,p.x);
 if(t<=0.||p.y<=0.)return vec4(0);
 if(Mode==4 && DistantView>=1.)return distantImpact(p,t);
 float burst=smoothstep(.015,.10,t),fade=1.-smoothstep(2.65,3.9,t);
 float drive=burst*(1.-smoothstep(2.05,2.4,t));
 vec3 w=p+vec3(0,-t*3.8,0);
 float n=noise3(w*.25+vec3(.27,.13,.71)).r;
 float detail=noise3(w*.72+vec3(t*.12,0,0)).g;
 float radius=60.*t/(1.+1.8*t)+.22*sin(ph*5.)+.3*(n-.5),width=.5+t*.55;
 float wave=exp(-pow((r-radius)/width,2.)-pow((p.y-.10)/(.085+t*.11),2.));
 wave+=.30*exp(-pow((r-radius*.73)/(width*.8),2.)-pow((p.y-.07)/.08,2.));
 wave*=smoothstep(0.,.025,t)*(1.-smoothstep(.14,.32,t))*(.35+.65*n);
 float spread=1.5+4.6*(1.-exp(-t*8.)),height=.45+1.9*sqrt(min(t,.6));
 float billow=1.-length(vec2(r/spread,(p.y-(.30+.45*sqrt(t)))/height));
 float dust=clamp((billow-.28+(n-.48)*1.5+(detail-.5)*.35)*1.65,0.,1.)*burst*fade*(.48+.52*exp(-t*2.));
 vec3 ax=normalize(vec3(BladeAcross.x,0,BladeAcross.z));
 float x=dot(p,ax),z=dot(p,BladeNormal);
 float jetHeight=5.7*(1.-exp(-t*7.))*(.92+.08*sin(t*17.));
 float bend=.035*(n-.5);
 float jetWidth=.035+.40*max(0.,1.-p.y/jetHeight);
 float jet=exp(-pow((x-(.65+p.y*.70)+bend)/jetWidth,2.)-pow((z-.75)/.45,2.));
 jet+=.85*exp(-pow((x+(.50+p.y*.60)+bend)/(jetWidth*.9),2.)-pow((z-.8)/.52,2.));
 float fan=exp(-pow((x-(.45+p.y*1.2))/.095,2.)-pow((z-.9)/.32,2.));
 fan+=exp(-pow((x+(.4+p.y*1.0))/.085,2.)-pow((z-.8)/.30,2.));
 jet+=.55*fan*(1.-smoothstep(jetHeight*.3,jetHeight*.65,p.y));
 jet+=.65*exp(-x*x/.28-pow((z-.45)/.5,2.)-p.y*p.y/1.2);
 jet*=1.-smoothstep(jetHeight*.55,jetHeight,max(p.y,0.));
 jet*=drive*(.55+.9*detail);
 // New channels keep tearing open along the buried blade's widening section.
 // Each thin core climbs separately; the broad base remains at the contact line.
 float streams=0.;
 for(int j=0;j<10;j++){
  float id=float(j),sg=mod(id,2.)<1.?-1.:1.;
  float height=2.0+2.0*fract(sin(id*73.7)*491.3);
  float slope=.26+.64*fract(sin(id*31.9)*179.3);
  float pace=.73+.27*sin(t*(13.+id)+id*4.-p.y*5.);
  float center=sg*(.42+.20*id+p.y*slope)+.07*sin(p.y*5.-t*16.+id);
  float taper=max(.012,(.09+.07*fract(id*.73))*(1.-p.y/height));
  streams+=exp(-pow((x-center)/taper,2.)-pow((z-(.3+.12*id))/.20,2.))
           *(1.-smoothstep(height*.55,height,p.y))*pace;
 }
 jet+=streams*drive*(.5+detail);
 float soilHeight=5.8*(1.-exp(-t*2.8));
 float lobes=pow(max(0.,.5+.5*cos(ph*11.+1.1*sin(ph*3.)+.6*n)),5.);
 float soil=exp(-pow((r-(.65+p.y*.48+t*.22+.16*(n-.5)))/(.34+.11*p.y),2.))*lobes;
 soil*=1.-smoothstep(soilHeight*.50,soilHeight,max(p.y,0.));
 soil*=smoothstep(.10,.42,t)*fade*(.4+1.5*detail)*smoothstep(.65,1.65,abs(x));
 float erupt=max(Age-5.15,0.);
 if(erupt>0.){
  float grow=1.-exp(-erupt*14.),end=1.-smoothstep(.25,1.55,erupt);
  float pulse=exp(-erupt*5.5);
  float front=1.2+erupt*20.;
  wave+=exp(-pow((r-front)/(.35+erupt*.8),2.)-pow((p.y-.18)/.18,2.))*pulse*2.;
  float tower=exp(-pow((r-(.5+p.y*.23))/(.6+p.y*.075),2.))*(1.-smoothstep(10.*grow,14.*grow,p.y));
  float tongues=pow(.5+.5*sin(ph*13.+p.y*.45),3.);
  jet+=tower*tongues*pulse*3.5;
  float bank=1.-length(vec2(r/(3.+erupt*6.),(p.y-(1.+erupt*2.))/(1.2+erupt*2.)));
  dust+=clamp((bank-.25+(n-.5)*1.3+(detail-.5)*.4)*1.5,0.,1.)*end*grow;
 }
 if(Mode==1){
   // A second, broken compression front follows the initial sharp contact.
   // Low rolling banks move outwards, keeping the central engraving readable.
   float travel=17.*t/(1.+.50*t);
   float lobe=.5+.5*sin(ph*5.+.5*sin(ph*3.)+.3*t);
   float rollY=.25+.52*lobe+.31*(detail-.5);
   float roll=exp(-square((r-travel-.7*(n-.5))/(.55+.32*t))
                  -square((p.y-rollY)/(.32+.48*lobe)));
   float broken=smoothstep(.19,.73,.48+.37*(n-.5)+.58*(detail-.5));
   dust+=.55*roll*broken*smoothstep(.04,.16,t)*(1.-smoothstep(.85,1.65,t));
   wave+=.34*exp(-square((r-travel*1.17)/(.21+.14*t))-square((p.y-.12)/.12))
         *smoothstep(.025,.10,t)*(1.-smoothstep(.30,.68,t));
   // Continue grinding along the widening contact, with a short hotter onset.
   jet*=1.12+.18*exp(-t*5.);
   soil*=.78;
 }
 if(Mode==4){
   float travel=23.*t/(1.+.70*t);
   float lobe=.5+.5*sin(7.*ph+.7*cos(ph*3.)+.35*t);
   float radius=travel+1.6*(lobe-.5)+2.4*(n-.5);
   float lift=.18+.78*lobe+.95*(detail-.5);
   float thickness=.50+.55*lobe+.28*t;
   float bank=exp(-square((r-radius)/(1.0+.60*t))-square((p.y-lift)/thickness));
   float clumps=smoothstep(.18,.79,.49+.28*cos(3.*ph-r*.39)+.63*(detail-.5));
   dust+=1.2*bank*clumps*smoothstep(.035,.15,t)*(1.-smoothstep(.83,1.9,t));
   wave+=.47*exp(-square((r-travel*1.16)/.46)-square((p.y-.16)/.20))*smoothstep(.02,.09,t)*(1.-smoothstep(.38,.92,t));
 }
 vec4 closeField=vec4(wave,dust,jet,soil)*smoothstep(0.,.06,p.y);
 if(Mode==4 && DistantView>0.)return mix(closeField,distantImpact(p,t),DistantView);
 return closeField;
}
// Final G detonation: world-space, radius 20, at the 97th server tick.
// A white-gold ignition, upward ejecta and rolling dust replace the old thin ring.
float burstHash(float x){return fract(sin(x*127.13+31.71)*43758.5453);}
float arenaFade(vec3 p){
 return smoothstep(0.,.65,min(min(p.x-ArenaRect.x,ArenaRect.z-p.x),min(p.z-ArenaRect.y,ArenaRect.w-p.z)));
}
float finishRadius(float age){
 float t=max(age-4.85,0.);
 return 20.*(1.-exp(-8.0*t));
}

vec4 finishMedium(vec3 p,float age){
 float clip=arenaFade(p);if(clip<=0.)return vec4(0.);
 p.xz*=20./BurstRadius;
 float t=age-4.85,r=length(p.xz);
 if(t<=0. || p.y<0. || p.y>16. || r>=20.)return vec4(0.);
 float fade=(1.-smoothstep(1.25,2.10,t))*smoothstep(0.,.025,t);
 float bound=1.-smoothstep(19.25,20.,r);
 float theta=atan(p.z,p.x),radius=finishRadius(age);
 vec3 flow=p*vec3(.115,.17,.115)-vec3(0.,t*.53,0.);
 vec3 macro=noise3(flow+vec3(.23,.51,.17));
 vec3 detail=noise3(flow*2.63+vec3(.61,.13,.42));
 float grain=noise3(flow*5.7+vec3(.11,.54,.32)).g;
 float lobes=.5+.24*sin(theta*9.+.48*sin(theta*3.))+.22*cos(theta*13.+1.1);
 float rollRadius=1.2+14.4*(1.-exp(-3.8*t));
 float rollWidth=1.25+1.7*(1.-exp(-4.*t))+.8*lobes;
 float top=(3.2+7.6*lobes+1.5*(macro.b-.5))*(1.-exp(-11.*t));
 float rise=1.15+top*.50;
 float roll=1.-square((r-rollRadius-1.2*(macro.r-.5))/rollWidth)
             -square((p.y-rise)/(1.25+top*.54));
 roll+=1.18*(macro.g-.5)+.65*(detail.g-.5)+.21*(grain-.5);
 float dust=smoothstep(-.25,.65,roll)*(.63+.37*macro.r);
dust*=smoothstep(.10,.30,t)*smoothstep(.12,.83,.50+.31*sin(theta*5.+.4)+.21*cos(theta*9.)+.33*(macro.b-.5));
 // Broken oblique fans lift from the contact point and fold into the low wall.
 float fans=pow(max(0.,.5+.5*cos(theta*11.+.36*sin(theta*4.))),5.);
 float cone=exp(-square((r-(.8+p.y*.74+2.2*t))/(1.15+.18*p.y)));
 float fan=cone*fans*(1.-smoothstep(6.,11.5,p.y))
           *smoothstep(.10,.30,t)*(1.-smoothstep(.65,1.40,t));
 fan*=smoothstep(.24,.78,macro.g+.40*(detail.r-.5));
 float wake=exp(-square((r-rollRadius+2.8)/3.6)-square((p.y-.45)/1.3));
 wake*=.20*smoothstep(.09,.34,t)*smoothstep(.22,.76,detail.b);
 // A shallow advancing pressure front stays within the same 20-block footprint.
 float ripple=.20*sin(theta*13.)+.32*(detail.r-.5);
 float wave=exp(-square((r-radius-ripple)/(.22+.15*t))-square((p.y-.3)/(.26+.14*t)));
 wave*=1.-smoothstep(.48,.95,t);
 float domeR=1.1+18.*(1.-exp(-10.*t));
 float ell=length(vec3(p.x,p.y*1.75,p.z));
 float ignition=exp(-square((ell-domeR)/(.5+.4*t)))*exp(-12.*t);
 float core=exp(-r*r/(3.5+120.*t)-p.y*p.y/(1.8+65.*t))*exp(-13.*t);
 // Unequal narrow golden jets. Their axes diverge, rather than forming a pillar.
 float sector=floor((theta+3.14159265)*12./6.2831853);
 float jetAngle=(sector+.28+.44*burstHash(sector+3.))*6.2831853/12.-3.14159265;
 float da=atan(sin(theta-jetAngle),cos(theta-jetAngle));
 float height=(6.+7.*burstHash(sector+8.))*(1.-exp(-23.*t));
 float spine=1.2+2.2*burstHash(sector+18.)+p.y*(.19+.35*burstHash(sector+5.));
 float width=max(.045,(.26+.20*burstHash(sector+1.))*(1.-p.y/max(height,.01)));
 float jet=exp(-square((r-spine)/width)-square(da*r/(width*.85)));
 float glow=exp(-square((r-spine)/(width*3.4))-square(da*r/(width*3.0)));
 float jetEnd=(1.-smoothstep(height*.68,height,p.y))*(1.-smoothstep(.23,.58,t));
 jet*=jetEnd;glow*=jetEnd;
 float smoke=dust*.34+fan*.29+wake*.65;
 float light=wave*.70+ignition*.80+core*1.9+jet*.75+glow*.16;
 float density=(smoke+light)*fade*bound*smoothstep(0.,.025,p.y);
 float sunlit=clamp(.28+.36*macro.r+.22*detail.g+.10*p.y,0.,1.);
 vec3 stone=mix(vec3(.24,.205,.17),vec3(.69,.62,.49),sunlit);
 float internal=.55*exp(-t*5.3)*exp(-r/15.)*(.6+.4*detail.b);
 stone+=vec3(.70,.36,.055)*internal;
 vec3 color=(stone*smoke+vec3(1.48,.98,.52)*(wave*.70+ignition*.80)
             +vec3(1.8,.82,.20)*(core*1.9+glow*.16)+vec3(2.3,1.85,1.10)*jet*.75)
             /max(smoke+light,.0001);
 return vec4(color,density*clip);
}

vec4 finishSurface(vec3 p,float age){
 float clip=arenaFade(p);if(clip<=0.)return vec4(0.);
 p.xz*=20./BurstRadius;
 float t=age-4.85,r=length(p.xz);
 if(t<=0. || r>=20. || abs(p.y)>.7)return vec4(0.);
 float angle=atan(p.z,p.x),radius=finishRadius(age);
 float wave=exp(-square((r-radius)/(.4+.12*t)))*(1.-smoothstep(.48,.95,t));
 float flash=exp(-r*r/150.)*exp(-t*8.2);
 float spoke=abs(sin(angle*7.+.12*sin(r*.9)))*r;
 float crack=exp(-square(spoke/.13))*(1.-smoothstep(11.,18.,r));
 float fade=smoothstep(0.,.025,t)*(1.-smoothstep(.60,1.5,t))*(1.-smoothstep(19.25,20.,r));
 float opacity=clamp((wave*.72+flash*.68+crack*.32)*fade,0.,.94);
 return vec4(1.28,.90,.40,opacity*clip);
}

vec4 finishPass(vec3 origin,vec3 ray,float limit,bool hasSurface){
 if(Age<4.85 || Age>=6.96)return vec4(0.);
 vec3 safeRay=mix(ray,vec3(.00001),lessThan(abs(ray),vec3(.00001)));
 vec3 a=(vec3(ArenaRect.x,0.,ArenaRect.y)-origin)/safeRay;
 vec3 b=(vec3(ArenaRect.z,16.,ArenaRect.w)-origin)/safeRay;
 vec3 lo=min(a,b),hi=max(a,b);
 float start=max(0.,max(lo.x,max(lo.y,lo.z))),end=min(limit,min(hi.x,min(hi.y,hi.z)));
 vec4 ground=hasSurface?finishSurface(origin+ray*limit,Age):vec4(0.);
 if(start>=end)return ground;
 float stepSize=max(.07,(end-start)/255.);
 float jitter=fract(dot(gl_FragCoord.xy,vec2(.754877666,.569840296)));
 vec3 sum=vec3(0.);float trans=1.;
 for(int i=0;i<256;i++){
   float d=start+(float(i)+jitter)*stepSize;
   if(d>end || trans<.012)break;
   vec4 medium=finishMedium(origin+ray*d,Age);
   float alpha=1.-exp(-medium.a*stepSize*1.35*smoothstep(.4,7.0,d));
   sum+=trans*alpha*medium.rgb;trans*=1.-alpha;
 }
 sum+=trans*ground.rgb*ground.a;
 float alpha=1.-trans+trans*ground.a;
 return alpha<.002?vec4(0.):vec4(sum/max(alpha,.001),alpha);
}


void main(){
 vec4 far=InverseViewProjection*vec4(screen*2.-1.,1.,1.);
 vec3 direction=normalize(far.xyz/far.w);
 float z=texture(Sampler1,screen).r;
 vec4 hit=InverseViewProjection*vec4(screen*2.-1.,z*2.-1.,1.);
 float limit=z>.99999?320.:length(hit.xyz/hit.w);
 vec3 origin=coordinates(-Origin),ray=coordinates(direction);
 if(Mode==0 || Mode==2 || Mode==3){
   if(Age>=4.82){fragColor=vec4(0.);return;}
   if(Mode==3){
    fragColor=observerSky(origin,ray,limit);
    if(z<.99999 && Age>1.51 && Age<4.78){
      vec3 p=origin+ray*limit, q=bladeCoordinates(p),dl=bladeCoordinates(p+Sun)-q;
      float along=-q.z/(abs(dl.z)>.001?dl.z:.001);
      vec3 edge=q+dl*along;
      float w=3.8*(1.-pow(1.-clamp(edge.y/5.534,0.,1.),1.503))*(1.+clamp((edge.y-5.534)*.43/3.8,0.,2.52));
      float shade=(1.-smoothstep(w,w+1.25+along*.018,abs(edge.x)))*smoothstep(0.,1.,edge.y)
                 *(1.-smoothstep(43.,61.,edge.y))*smoothstep(0.,2.,along)
                 *(1.-smoothstep(-24.,-12.,p.y))*smoothstep(1.51,1.88,Age)*(1.-smoothstep(4.60,4.78,Age))*.39;
      float a=fragColor.a+(1.-fragColor.a)*shade;
      fragColor=vec4((fragColor.rgb*fragColor.a+vec3(.14,.18,.25)*shade*(1.-fragColor.a))/max(.0001,a),a);
    }
   }
   else fragColor=marchCanopy(origin,ray,limit);
   // Steel has cleared at 4.78 s; the last sky vapour clears before camera return.
   fragColor.a*=1.-smoothstep(4.73,4.81,Age);
   return;
 }
 // Replace the whole contact volume. In particular the old 5.15 s fire tower
 // and centre dust cannot be evaluated after the sword has disappeared.
 if(Age>=4.76){fragColor=finishPass(origin,ray,limit,z<.99999);return;}
 // Both viewers intersect exactly the same finite volume and opaque depth.
 float start=0.,end=min(limit,320.);
 vec3 safeRay=vec3(abs(ray.x)<.00001?.00001:ray.x,abs(ray.y)<.00001?.00001:ray.y,abs(ray.z)<.00001?.00001:ray.z);
 vec3 lower=Mode==4 ? vec3(-36.,0.,-36.) : vec3(-28.,0.,-28.);
 vec3 upper=Mode==4 ? vec3(36.,19.,36.) : vec3(28.,18.,28.);
 vec3 a=(lower-origin)/safeRay,b=(upper-origin)/safeRay;
 vec3 lo=min(a,b),hi=max(a,b);
 start=max(0.,max(lo.x,max(lo.y,lo.z)));end=min(end,min(hi.x,min(hi.y,hi.z)));
 vec4 surface=(Mode==4 && z<.99999) ? arenaMark(origin+ray*limit,Age) : vec4(0.);
 if(start>=end){fragColor=surface;return;}
 // Full-resolution PC pass. More samples preserve thin moving jets and distant
 // cloud edges; no mobile-resolution reduction is applied to this cinematic.
 float stepSize=max(.042,(end-start)/767.);
 float jitter=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453);
 vec3 sum=vec3(0);float trans=1.;
 for(int i=0;i<768;i++){
  if(trans<.012)break;
  float t=start+(float(i)+jitter)*stepSize;
  if(t>end)break;
  vec3 p=origin+ray*t;
  vec4 field=impactField(p);
  float den=dot(field,vec4(.23,1.,1.4,2.3));
  if(den<.0015)continue;
  float sun=exp(-(field.y*.9+field.w*.65)*1.7);
  float edge=0.;
  vec3 color;
  {
   float age=max(Age-3.1,0.),r=length(p.xz);
   float heat=exp(-r*r/5.)*exp(-p.y*.5)*(1.-smoothstep(2.05,2.4,age));
   vec3 dustColor=vec3(.36,.34,.31)+vec3(.30,.28,.26)*(sun*.7+edge*.12)+vec3(.22,.11,.04)*heat;
   if(Mode==1){
    float detail=noise3(p*.61-vec3(age*.17,age*1.15,age*.23)).g;
    float lit=clamp(.24+.60*sun+.38*(detail-.5),0.,1.);
    dustColor=mix(vec3(.30,.32,.36),vec3(.76,.74,.68),lit)+vec3(.23,.13,.045)*heat;
   }
   if(Mode==4){
    vec3 grain=noise3(p*.43-vec3(age*.27,age*.56,age*.17));
    float relief=clamp(sun*.7+.36*(grain.r-.5),0.,1.);
    dustColor=mix(vec3(.37,.39,.42),vec3(.78,.77,.71),relief)+vec3(.22,.10,.025)*heat;
   }
   float gx=dot(p,normalize(vec3(BladeAcross.x,0,BladeAcross.z))),gz=dot(p,BladeNormal);
   float firelight=exp(-pow((gx-(.65+p.y*.70))/.8,2.))+exp(-pow((gx+(.50+p.y*.60))/.8,2.));
   firelight*=exp(-pow((gz-.75)/1.25,2.)-p.y/3.)*smoothstep(.025,.12,age)*(1.-smoothstep(2.05,2.4,age));
   float core=exp(-r*r/3.-p.y*p.y/1.8)*smoothstep(.025,.12,age)*(1.-smoothstep(2.05,2.4,age));
   dustColor+=vec3(2.2,1.35,.45)*(firelight*.8+core*.95);
   vec3 soilColor=vec3(.095,.060,.032)+vec3(.19,.125,.065)*sun+vec3(.23,.065,.01)*heat;
   vec3 fireColor=mix(vec3(1.4,.65,.18),vec3(3.5,3.1,2.0),smoothstep(.30,.90,field.z));
   color=(field.x*.23*mix(vec3(.72,.82,.94),vec3(1.04,.97,.79),DistantView)+field.y*dustColor+field.z*1.4*fireColor+field.w*2.3*soilColor)/max(den,.001);
  }
  float alpha=1.-exp(-den*stepSize*3.5);
  sum+=trans*alpha*color;trans*=1.-alpha;
 }
 float alpha=1.-trans;
 sum+=trans*surface.rgb*surface.a;alpha+=trans*surface.a;
 fragColor=alpha<.003?vec4(0):vec4(sum/max(alpha,.001),alpha*(1.-smoothstep(4.60,4.76,Age)));
}
