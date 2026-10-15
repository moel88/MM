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
uniform float AtomicCharge;
uniform float AtomicViolet;
uniform float AtomicFlash;
uniform float AtomicInk;
uniform float HeavenTone;
uniform float TimeStopMono;
uniform float KagenoTone;
in vec2 texCoord;
out vec4 fragColor;

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
    if(TimeStopMono>.5){vec3 c=texture(SceneTex,texCoord).rgb;float gray=dot(c,vec3(.2126,.7152,.0722));fragColor=vec4(vec3(gray),1.0);return;}
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
    color=mix(color,color*mix(vec3(.91,.84,1.04),vec3(1.04,.98,.88),HeavenTone),Strength*.35);
    color*=1.0-AtomicCharge*Strength*.48;
    color+=glow*AtomicCharge*.28;
    float angle=atan(delta.y,delta.x);
    float ray=pow(max(0.0,sin(angle*117.0+Time*.9)),45.0);
    ray*=smoothstep(.13,.74,radius)*rush;
    color+=mix(vec3(.66,.43,1.0),vec3(1.0,.69,.22),HeavenTone)*ray*.72;
    float edge=smoothstep(.18,.82,length((texCoord-.5)*aspect));
    color*=1.0-edge*Strength*.24;
    color+=mix(vec3(.31,.025,.56),vec3(.52,.22,.015),HeavenTone)*edge*Power*Strength*.22;
    float luma=dot(base,vec3(.2126,.7152,.0722));
    float ink=1.0-smoothstep(.20,.67,luma);
    color=mix(color,vec3(ink),Mono*Strength);
    // Dark moving debris over the visible scene; never replace it with a white field.
    float sector=floor((angle+3.141593)*39.0);
    float seed=fract(sin(sector*91.17)*43758.5453);
    float flight=fract(radius*2.1-Time*(.8+seed)+seed*5.0);
    float shard=step(.53+seed*.23,flight)*step(.26,fract((angle+3.141593)*39.0));
    float scratch=step(.972,fract((angle+3.141593)*117.0+sin(radius*12.0+Time)*.15));
    color*=1.0-max(shard*.96,scratch*.8)*AtomicInk*Strength*.75;
    float atomicLuma=dot(color,vec3(.2126,.7152,.0722));
    // The reference interior is a saturated blue-violet volume with broad dark
    // moving walls, not fine neon stripes painted across the screen.
    vec2 flowUv=texCoord+vec2(sin(texCoord.y*5.0+Time*1.7),cos(texCoord.x*4.0-Time*1.1))*.075;
    float curtains=.5+.5*sin(flowUv.x*9.0+sin(flowUv.y*3.1-Time*1.8)*1.4+Time*1.2);
    float second=.5+.5*sin(flowUv.x*16.0-flowUv.y*2.0-Time*1.6);
    float wall=smoothstep(.48,.94,curtains)*(.55+.45*second);
    float shock=.5+.5*sin(Time*8.7+flowUv.y*2.2);
    vec3 violet=mix(vec3(.30,.003,.98),vec3(.075,.001,.41),wall*.82);
    violet+=vec3(.045,.002,.055)*shock;
    violet*=.78+atomicLuma*.35;
    color=mix(color*.12+violet*.88,violet,AtomicViolet*.82)*AtomicViolet+color*(1.0-AtomicViolet);
    color=mix(color,vec3(1.0,.70,1.0),AtomicFlash*.95);
    fragColor=vec4(clamp(color,0.0,1.0),1.0);
}
