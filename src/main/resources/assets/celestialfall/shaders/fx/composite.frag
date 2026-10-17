#version 150
// The effect layer back onto the screen: bloom, afterglow trails and light shafts added, light above white
// turned white-hot instead of clipping to a flat colour, and the finisher's impact frame.
uniform sampler2D HdrTex;
uniform sampler2D BloomTex;
uniform sampler2D HistTex;
uniform sampler2D LightTex;
uniform sampler2D ShaftTex;
uniform float BloomGain;
uniform float Afterglow;
uniform float ShaftGain;
uniform vec3 ShaftTint;
uniform float Impact;
uniform float ImpactMode;
uniform float Chroma;
uniform vec2 Focus;
in vec2 texCoord;
out vec4 fragColor;
vec3 hot(vec3 c){
    c=max(c,0.0);float m=max(c.r,max(c.g,c.b));
    if(m<=1.0)return c;
    return mix(c/m,vec3(1.0),1.0-exp(-(m-1.0)*1.15));
}
void main(){
    vec3 c=texture(HdrTex,texCoord).rgb;
    if(Chroma>.00001){vec2 d=(texCoord-Focus)*Chroma;c.r=texture(HdrTex,texCoord+d).r;c.b=texture(HdrTex,texCoord-d).b;}
    vec3 trail=max(texture(HistTex,texCoord).rgb-texture(LightTex,texCoord).rgb,0.0);
    c+=texture(BloomTex,texCoord).rgb*BloomGain+trail*Afterglow+texture(ShaftTex,texCoord).rgb*ShaftTint*ShaftGain;
    c=hot(c);
    if(Impact>.001){
        float l=dot(c,vec3(.2126,.7152,.0722));
        float ink=smoothstep(.40,.48,l);
        vec3 a=mix(vec3(.03,.0,.08),vec3(1.0,.94,1.0),ink);
        vec3 b=mix(vec3(.99,.96,1.0),vec3(.17,.02,.33),ink);
        c=mix(c,mix(a,b,clamp(ImpactMode,0.0,1.0)),clamp(Impact,0.0,1.0));
    }
    fragColor=vec4(c,1.0);
}
