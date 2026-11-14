#version 150
// The effect layer back onto the screen: bloom, afterglow trails and light shafts added, light above white
// turned white-hot instead of clipping to a flat colour, the finisher's impact frame, and (2.132.1) the screen inside
// X Chara's rain of knives.
uniform sampler2D HdrTex;
uniform sampler2D BloomTex;
uniform sampler2D HistTex;
uniform sampler2D LightTex;
uniform sampler2D ShaftTex;
uniform sampler2D SceneTex;
uniform sampler2D BaseTex;
uniform float BloomGain;
uniform float Afterglow;
uniform float ShaftGain;
uniform vec3 ShaftTint;
uniform float Impact;
uniform float ImpactMode;
// the impact frame's colours (FxFrame.ImpactLook: Kageno's violet unless a skill asks for its own)
uniform vec3 ImpactDeep;
uniform vec3 ImpactShapes;
uniform vec3 ImpactPale;
uniform vec3 ImpactShade;
uniform vec3 ImpactHue;
uniform float Chroma;
uniform vec2 Focus;
// (2.132.1) the screen inside X Chara's rain of knives (FxFrame.rain): how much, its pulse as the knives land, a flash,
// a clock (game seconds), whether it may tear the screen (the screen's distortion setting), the screen's size
uniform float Rain;
uniform float RainBeat;
uniform float RainFlash;
uniform float RainTime;
uniform float RainTear;
uniform vec2 Screen;
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
// How far the screen is torn sideways here (screen fractions): slices of it slid one way or the other, a share of them
// at a time, a new set eighteen times a second, more and further as the knives land; now and then a block of it.
float tear(vec2 uv){
    if(Rain<.001||RainTear<.5)return 0.0;
    float t=floor(RainTime*18.0);
    float band=floor(uv.y*26.0+t*3.17);
    float torn=step(1.0-(.12+.26*RainBeat)*Rain,hash(vec2(band,t)));
    float slide=torn*(hash(vec2(band*1.7,t+3.0))-.5)*(.04+.06*RainBeat)*Rain;
    vec2 cell=floor(uv*vec2(7.0,5.0)+vec2(t*1.3,t*.7));
    float block=step(.982-.04*RainBeat,hash(cell+t*.37))*Rain;
    return slide+block*(hash(cell*2.3)-.5)*.09;
}
vec3 hot(vec3 c){
    c=max(c,0.0);float m=max(c.r,max(c.g,c.b));
    if(m<=1.0)return c;
    // Light a little over white keeps its colour; only light far over it burns white.
    return mix(c/m,vec3(1.0),1.0-exp(-(m-1.0)*.35));
}
void main(){
    float torn=tear(texCoord);
    vec2 uv=vec2(texCoord.x+torn,texCoord.y);
    vec3 c=texture(HdrTex,uv).rgb;
    if(Chroma>.00001){vec2 d=(uv-Focus)*Chroma;c.r=texture(HdrTex,uv+d).r;c.b=texture(HdrTex,uv-d).b;}
    // (a torn slice's colours split along its tear)
    if(abs(torn)>.0001){c.r=texture(HdrTex,uv+vec2(torn*.7,0.0)).r;c.b=texture(HdrTex,uv-vec2(torn*.7,0.0)).b;}
    vec3 trail=max(texture(HistTex,texCoord).rgb-texture(LightTex,texCoord).rgb,0.0);
    c+=texture(BloomTex,texCoord).rgb*BloomGain+trail*Afterglow+texture(ShaftTex,texCoord).rgb*ShaftTint*ShaftGain;
    c=hot(c);
    if(Rain>.001){
        // inside the rain of knives: sunk into violet (half the colour gone, the rest pushed to purple)
        float l=dot(c,vec3(.2126,.7152,.0722));
        vec3 graded=mix(vec3(l),c,.5)*vec3(.95,.66,1.28)+vec3(.035,0.0,.09);
        c=mix(c,graded,.72*Rain);
        // the edges dark and deep violet
        vec2 q=(texCoord-.5)*vec2(Screen.x/max(Screen.y,1.0),1.0);
        float edge=smoothstep(.32,1.02,length(q));
        c=mix(c,c*.22+vec3(.11,.0,.24)*.55,edge*.82*Rain);
        // light falling down the screen like rain: thin violet lines, white at their heads, each column its own speed
        float cols=150.0,col=floor(texCoord.x*cols),hc=hash(vec2(col,7.0));
        if(hc>.38){
            float across=abs(fract(texCoord.x*cols)-.5)*2.0;
            float y=fract(texCoord.y*(.55+.6*hc)+RainTime*(1.5+1.6*hc)+hc*9.0);
            // (the pattern runs down the screen: its head, the lowest of it, leads; the tail fades up behind it)
            float streak=smoothstep(.0,.015,y)*(1.0-smoothstep(.015,.3,y))*(1.0-smoothstep(.0,.45,across));
            c+=mix(vec3(.55,.18,1.0),vec3(1.0,.95,1.0),pow(max(0.0,1.0-y/.3),8.0))*streak*.55*Rain*(.55+.45*RainBeat);
        }
        // scanned, and pulsing brighter violet as the knives land
        c*=1.0-.08*Rain*(.5+.5*sin(texCoord.y*Screen.y*1.7));
        c+=vec3(.16,.03,.32)*RainBeat*Rain*.55;
        // and a flash of white-violet with the last
        c=mix(c,vec3(1.0,.9,1.0),clamp(RainFlash,0.0,1.0)*.6);
    }
    if(Impact>.001){
        // Flat impact frame: the effects' shapes (the light they added) and the dark silhouettes of the world
        // (bodies, pillars) over a flat ground. First white shapes on deep violet, then dark shapes on white
        // (violet unless the skill asks for its own colours, FxFrame.ImpactLook).
        vec3 added=max(texture(HdrTex,texCoord).rgb-texture(BaseTex,texCoord).rgb,0.0);
        float fx=smoothstep(.10,.18,dot(added,vec3(.2126,.7152,.0722)));
        float sil=1.0-smoothstep(.26,.34,dot(texture(SceneTex,texCoord).rgb,vec3(.2126,.7152,.0722)));
        vec3 a=mix(mix(ImpactDeep,vec3(.0),sil),ImpactShapes,fx);
        vec3 b=mix(mix(ImpactPale,ImpactShade,sil),ImpactHue,fx);
        c=mix(c,mix(a,b,clamp(ImpactMode,0.0,1.0)),clamp(Impact,0.0,1.0));
    }
    fragColor=vec4(c,1.0);
}
