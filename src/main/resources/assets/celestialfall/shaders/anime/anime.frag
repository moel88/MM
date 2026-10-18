#version 150
uniform sampler2D SceneTex;
uniform sampler2D BloomTex;
uniform int Mode;
uniform vec2 Texel;
uniform vec2 Resolution;
uniform vec2 Focus;
uniform float Strength;
uniform float Time;
uniform float Bloom;
uniform float Power;
uniform float Distortion;
uniform float Speed;
uniform float Mono;
uniform float AtomicMode;
uniform float AtomicTint;
uniform float AtomicFrame;
uniform float AtomicMorph;
uniform float AtomicGrade;
uniform vec3 AtomicLo;
uniform vec3 AtomicHi;
uniform float AtomicHorizon;
uniform float HeavenTone;
uniform float TimeStopMono;
uniform float KagenoTone;
in vec2 texCoord;
out vec4 fragColor;


// G impact frames (150.373-150.624 s of the review footage): flat two-tone silhouettes of the
// blast geometry. Four shape keyframes x six tapered polylines of nine points (x of width,
// y from the top, width in screen heights) plus one blob; AtomicMorph blends the keyframes.
const vec3 SHAPE[216]=vec3[216](vec3(0.,0.24,0.06),vec3(0.2,0.32,0.035),vec3(0.4,0.405,0.012),vec3(0.5,0.44,0.015),vec3(0.6,0.485,0.045),vec3(0.7,0.555,0.035),vec3(0.8,0.587,0.022),vec3(0.9,0.62,0.012),vec3(1.02,0.65,0.003),vec3(0.67,0.295,0.003),vec3(0.75,0.315,0.012),vec3(0.85,0.35,0.025),vec3(0.93,0.385,0.045),vec3(0.97,0.405,0.06),vec3(0.955,0.44,0.05),vec3(0.94,0.465,0.02),vec3(0.935,0.47,0.),vec3(0.935,0.47,0.),vec3(0.415,0.54,0.002),vec3(0.445,0.548,0.011),vec3(0.475,0.556,0.002),vec3(0.475,0.556,0.),vec3(0.475,0.556,0.),vec3(0.475,0.556,0.),vec3(0.475,0.556,0.),vec3(0.475,0.556,0.),vec3(0.475,0.556,0.),vec3(0.24,1.02,0.04),vec3(0.35,0.93,0.045),vec3(0.45,0.885,0.035),vec3(0.52,0.875,0.03),vec3(0.62,0.885,0.02),vec3(0.72,0.915,0.012),vec3(0.82,0.96,0.008),vec3(0.92,1,0.004),vec3(0.96,1.02,0.002),vec3(0.55,0.905,0.002),vec3(0.65,0.915,0.006),vec3(0.75,0.94,0.005),vec3(0.85,0.975,0.004),vec3(0.92,1,0.002),vec3(0.92,1,0.),vec3(0.92,1,0.),vec3(0.92,1,0.),vec3(0.92,1,0.),vec3(0.5,0.07,0.),vec3(0.55,0.065,0.),vec3(0.6,0.06,0.),vec3(0.6,0.06,0.),vec3(0.6,0.06,0.),vec3(0.6,0.06,0.),vec3(0.6,0.06,0.),vec3(0.6,0.06,0.),vec3(0.6,0.06,0.),vec3(0.,0.24,0.03),vec3(0.1,0.27,0.025),vec3(0.2,0.31,0.022),vec3(0.3,0.355,0.025),vec3(0.4,0.39,0.032),vec3(0.5,0.44,0.042),vec3(0.6,0.49,0.025),vec3(0.7,0.52,0.012),vec3(0.82,0.56,0.002),vec3(0.52,0.24,0.003),vec3(0.58,0.255,0.012),vec3(0.64,0.28,0.022),vec3(0.69,0.315,0.035),vec3(0.72,0.345,0.05),vec3(0.71,0.38,0.045),vec3(0.695,0.405,0.02),vec3(0.69,0.41,0.),vec3(0.69,0.41,0.),vec3(0.24,0.49,0.002),vec3(0.275,0.495,0.012),vec3(0.31,0.5,0.002),vec3(0.31,0.5,0.),vec3(0.31,0.5,0.),vec3(0.31,0.5,0.),vec3(0.31,0.5,0.),vec3(0.31,0.5,0.),vec3(0.31,0.5,0.),vec3(0.05,1.02,0.035),vec3(0.2,0.92,0.03),vec3(0.35,0.83,0.025),vec3(0.5,0.765,0.02),vec3(0.58,0.755,0.015),vec3(0.7,0.78,0.01),vec3(0.82,0.83,0.006),vec3(0.86,0.845,0.002),vec3(0.88,0.85,0.),vec3(0.4,0.8,0.),vec3(0.45,0.79,0.),vec3(0.5,0.78,0.),vec3(0.55,0.775,0.),vec3(0.6,0.775,0.),vec3(0.6,0.775,0.),vec3(0.6,0.775,0.),vec3(0.6,0.775,0.),vec3(0.6,0.775,0.),vec3(0.45,0.075,0.002),vec3(0.55,0.058,0.009),vec3(0.66,0.04,0.002),vec3(0.66,0.04,0.),vec3(0.66,0.04,0.),vec3(0.66,0.04,0.),vec3(0.66,0.04,0.),vec3(0.66,0.04,0.),vec3(0.66,0.04,0.),vec3(0.,0.245,0.015),vec3(0.1,0.27,0.018),vec3(0.2,0.3,0.022),vec3(0.3,0.33,0.025),vec3(0.4,0.37,0.03),vec3(0.45,0.4,0.035),vec3(0.55,0.465,0.025),vec3(0.65,0.505,0.012),vec3(0.75,0.525,0.002),vec3(0.46,0.215,0.003),vec3(0.51,0.235,0.01),vec3(0.56,0.26,0.02),vec3(0.61,0.295,0.03),vec3(0.645,0.33,0.045),vec3(0.64,0.36,0.04),vec3(0.63,0.385,0.018),vec3(0.625,0.39,0.),vec3(0.625,0.39,0.),vec3(0.14,0.475,0.002),vec3(0.19,0.48,0.012),vec3(0.24,0.488,0.002),vec3(0.24,0.488,0.),vec3(0.24,0.488,0.),vec3(0.24,0.488,0.),vec3(0.24,0.488,0.),vec3(0.24,0.488,0.),vec3(0.24,0.488,0.),vec3(0.,0.99,0.03),vec3(0.15,0.91,0.027),vec3(0.3,0.83,0.022),vec3(0.42,0.755,0.018),vec3(0.52,0.735,0.012),vec3(0.62,0.74,0.008),vec3(0.75,0.77,0.004),vec3(0.78,0.78,0.002),vec3(0.8,0.785,0.),vec3(0.33,0.785,0.002),vec3(0.42,0.75,0.006),vec3(0.5,0.73,0.004),vec3(0.55,0.725,0.002),vec3(0.58,0.725,0.),vec3(0.58,0.725,0.),vec3(0.58,0.725,0.),vec3(0.58,0.725,0.),vec3(0.58,0.725,0.),vec3(0.4,0.09,0.002),vec3(0.5,0.087,0.008),vec3(0.6,0.085,0.002),vec3(0.6,0.085,0.),vec3(0.6,0.085,0.),vec3(0.6,0.085,0.),vec3(0.6,0.085,0.),vec3(0.6,0.085,0.),vec3(0.6,0.085,0.),vec3(0.,0.235,0.015),vec3(0.1,0.26,0.018),vec3(0.2,0.29,0.022),vec3(0.3,0.32,0.025),vec3(0.4,0.36,0.03),vec3(0.45,0.39,0.035),vec3(0.55,0.455,0.025),vec3(0.65,0.495,0.012),vec3(0.72,0.52,0.002),vec3(0.44,0.2,0.003),vec3(0.49,0.22,0.01),vec3(0.54,0.245,0.02),vec3(0.59,0.28,0.03),vec3(0.62,0.31,0.045),vec3(0.615,0.34,0.04),vec3(0.605,0.365,0.018),vec3(0.6,0.37,0.),vec3(0.6,0.37,0.),vec3(0.1,0.465,0.002),vec3(0.15,0.47,0.012),vec3(0.2,0.475,0.002),vec3(0.2,0.475,0.),vec3(0.2,0.475,0.),vec3(0.2,0.475,0.),vec3(0.2,0.475,0.),vec3(0.2,0.475,0.),vec3(0.2,0.475,0.),vec3(0.,1,0.03),vec3(0.15,0.9,0.027),vec3(0.3,0.81,0.022),vec3(0.42,0.74,0.018),vec3(0.5,0.72,0.012),vec3(0.6,0.725,0.008),vec3(0.72,0.75,0.004),vec3(0.75,0.76,0.002),vec3(0.77,0.765,0.),vec3(0.3,0.77,0.002),vec3(0.4,0.735,0.006),vec3(0.48,0.715,0.004),vec3(0.53,0.71,0.002),vec3(0.56,0.71,0.),vec3(0.56,0.71,0.),vec3(0.56,0.71,0.),vec3(0.56,0.71,0.),vec3(0.56,0.71,0.),vec3(0.38,0.1,0.002),vec3(0.47,0.098,0.008),vec3(0.57,0.095,0.002),vec3(0.57,0.095,0.),vec3(0.57,0.095,0.),vec3(0.57,0.095,0.),vec3(0.57,0.095,0.),vec3(0.57,0.095,0.),vec3(0.57,0.095,0.));
const vec3 BLOB[4]=vec3[4](vec3(0.85,0.94,0.),vec3(0.845,0.935,0.032),vec3(0.76,0.865,0.028),vec3(0.735,0.845,0.026));

float atomicShapes(vec2 uv,float morph,float aspect){
    int k0=int(clamp(floor(morph),0.,3.)),k1=min(k0+1,3);float t=clamp(morph-float(k0),0.,1.);
    vec2 p=vec2(uv.x*aspect,1.-uv.y);
    float cover=0.;
    for(int s=0;s<6;s++){
        vec3 prev=mix(SHAPE[(k0*6+s)*9],SHAPE[(k1*6+s)*9],t);
        for(int i=1;i<9;i++){
            vec3 cur=mix(SHAPE[(k0*6+s)*9+i],SHAPE[(k1*6+s)*9+i],t);
            vec2 a=vec2(prev.x*aspect,prev.y),b=vec2(cur.x*aspect,cur.y),ba=b-a,pa=p-a;
            float h=clamp(dot(pa,ba)/max(dot(ba,ba),1e-6),0.,1.);
            float hw=mix(prev.z,cur.z,h)*.5;
            // Ragged end of the hook and a slight wave along the long band.
            if(s==1&&i>=5)hw*=1.+.45*sin(p.y*820.+p.x*310.);
            if(s==0)hw*=1.+.12*sin(p.x*41.);
            float d=length(pa-ba*h);
            cover=max(cover,1.-smoothstep(hw-.0012,hw+.0012,d));
            prev=cur;
        }
    }
    vec3 bl=mix(BLOB[k0],BLOB[k1],t);
    float bd=length(p-vec2(bl.x*aspect,bl.y))-bl.z*(1.+.08*sin(atan(p.y-bl.y,p.x-bl.x*aspect)*7.));
    cover=max(cover,1.-smoothstep(-.0012,.0012,bd));
    return cover;
}
vec3 bright(vec2 uv) {
    vec3 c=texture(SceneTex,uv).rgb;
    float peak=max(c.r,max(c.g,c.b));
    float energy=max(smoothstep(.88,.99,min(c.r,min(c.g,c.b))),smoothstep(.06,.30,min(c.r,c.b)-c.g)*smoothstep(.22,.65,peak));
    return c*mix(smoothstep(.52,.92,peak),energy,KagenoTone);
}
void main() {
    if(Mode<2) {
        vec3 sum=vec3(0.0);
        float total=0.0;
        vec2 direction=Mode==0?vec2(Texel.x*2.4,0.0):vec2(0.0,Texel.y*.9);
        for(int i=-8;i<=8;i++) {
            float weight=exp(-float(i*i)/18.0);
            vec2 sampleUv=texCoord+direction*float(i);
            sum+=(Mode==0?bright(sampleUv):texture(SceneTex,sampleUv).rgb)*weight;
            total+=weight;
        }
        fragColor=vec4(sum/total,1.0);
        return;
    }
    // R time stop: the world (not the HUD) desaturates to Rec.601 luma.
    if(TimeStopMono>.001){vec3 c=texture(SceneTex,texCoord).rgb;float gray=dot(c,vec3(.299,.587,.114));fragColor=vec4(mix(c,vec3(gray),TimeStopMono),1.0);return;}
    vec2 aspect=vec2(Resolution.x/max(Resolution.y,1.0),1.0);
    vec2 delta=(texCoord-Focus)*aspect;
    float radius=length(delta);
    vec2 normal=delta/max(radius,.0001)/aspect;
    float ring=sin(radius*62.0-Time*14.0)*exp(-abs(radius-.35)*3.0);
    vec2 uv=texCoord+normal*ring*(Power*.012+Speed*.003)*Distortion*Strength;
    vec2 chroma=normal*(Power*.004+Speed*.0015)*Distortion*Strength;
    vec3 base=texture(SceneTex,uv).rgb;
    base.r=texture(SceneTex,uv+chroma).r;
    base.b=texture(SceneTex,uv-chroma).b;
    float rush=Speed*Strength;
    vec3 streak=vec3(0.0);
    for(int i=1;i<=4;i++)streak+=texture(SceneTex,mix(uv,Focus,float(i)*.015*rush)).rgb;
    base=mix(base,streak*.25,rush*.38);
    vec3 glow=texture(BloomTex,texCoord).rgb*Bloom*Strength*(1.0+Power*.8+KagenoTone*.32);
    vec3 color=1.0-(1.0-base)*(1.0-clamp(glow*.72,0.0,.95));
    color=mix(color,color*mix(vec3(.91,.84,1.04),vec3(1.04,.98,.88),HeavenTone),Strength*.35*(1.0-AtomicMode));
    float angle=atan(delta.y,delta.x);
    float ray=pow(max(0.0,sin(angle*117.0+Time*.9)),45.0);
    ray*=smoothstep(.13,.74,radius)*rush;
    color+=mix(vec3(.66,.43,1.0),vec3(1.0,.69,.22),HeavenTone)*ray*.72;
    float edge=smoothstep(.18,.82,length((texCoord-.5)*aspect));
    color*=1.0-edge*Strength*.24*(1.0-AtomicMode);
    color+=mix(vec3(.31,.025,.56),vec3(.52,.22,.015),HeavenTone)*edge*Power*Strength*.22;
    float luma=dot(base,vec3(.2126,.7152,.0722));
    float ink=1.0-smoothstep(.20,.67,luma);
    color=mix(color,vec3(ink),Mono*Strength);
    // G chant: lavender haze, a little stronger in a band around the horizon.
    float band=exp(-pow((texCoord.y-AtomicHorizon)/.22,2.0));
    color=mix(color,vec3(.6,.56,.9),clamp((.26+.14*band)*AtomicTint,0.0,1.0));
    // G blast: the scene's luminance mapped between two flat colours (green stays exactly 0).
    float gl=dot(color,vec3(.2126,.7152,.0722));
    vec3 graded=mix(AtomicLo,AtomicHi,smoothstep(.08,.85,gl));
    // Very bright blast light (the final beam, swirl rings) lifts past the light shapes.
    graded+=(vec3(.54,.44,1.0)-AtomicHi)*smoothstep(.78,1.0,gl);
    color=mix(color,graded,AtomicGrade);
    if(AtomicFrame>1.5){
        float cover=atomicShapes(texCoord,AtomicMorph,aspect.x);
        bool white=AtomicFrame<2.5||AtomicFrame>4.5;
        vec3 bg=white?vec3(.992,.992,.98):vec3(.004);
        vec3 tone=AtomicFrame>2.5&&AtomicFrame<3.5?vec3(.639,.004,.784):vec3(.698,.624,.984);
        color=mix(bg,tone,cover);
        if(AtomicFrame>4.5){
            // Last white frame: pink bleeds in at the right and bottom, the floor shows faintly.
            float bleed=clamp(smoothstep(.8,1.,texCoord.x)*.9+smoothstep(.2,0.,texCoord.y)*.7,0.,1.)*(.75+.25*sin(texCoord.y*40.+texCoord.x*13.));
            color=mix(color,vec3(.957,.765,.937),bleed*(1.-cover*.6));
        }
    }
    fragColor=vec4(clamp(color,0.0,1.0),1.0);
}
