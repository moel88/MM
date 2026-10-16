#version 150
// Botand VFX energy materials. Sampler0 is a tileable, mipmapped noise texture (r smooth fbm, g finer
// fbm, b ridged lines, a coarse fbm) scrolled in several layers at different speeds; Sampler1 is a
// copy of the scene colour for the refraction modes. Output is premultiplied (blend ONE,
// ONE_MINUS_SRC_ALPHA): alpha 0 adds light, alpha 1 covers what is behind.
// Modes: 1 tower body, 2 tower skin, 3 tower cap, 4 ground glow, 5 wave front, 6 wave refraction,
// 7 heat column, 8 spark sprite, 9 flare, 10 smoke puff, 11 ground heat haze, 12 solid (debris).
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform int Mode;
uniform float Time;
uniform vec4 Params;
uniform vec4 Params2;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
in vec2 uv;
in vec4 tint;
in vec3 viewPos;
in vec3 viewNrm;
in vec3 local;
out vec4 fragColor;

const float PI=3.14159265;
const vec3 WHITE=vec3(1.0,0.97,1.0);
const vec3 DEEP=vec3(0.25,0.02,0.68);
const vec3 VIOLET=vec3(0.50,0.05,1.0);
const vec3 PURPLE=vec3(0.62,0.1,1.0);

vec4 N(vec2 p){return texture(Sampler0,p);}
float facing(){return abs(dot(normalize(viewNrm),normalize(-viewPos)));}
// Tower coordinates. Params: x radius, y mesh height (blocks, from 2 below the ground), z blast radius R,
// w white-out. Params2: x flow speed, y ignition heat, z opacity, w pattern offset around the tower.
float heightR(){return (local.y*Params.y-2.0)/Params.z;}
// Top of the dark smoke ring at the tower's foot, in blast radii: lumpy and slowly churning.
float smokeTop(float u){return 0.12+0.08*(N(vec2(u*9.0,Time*0.05)).r*0.65+N(vec2(u*23.0+0.3,Time*0.08)).g*0.35);}
// A bright surge racing up every 1.6 s.
float surge(float Y,float t){float s=fract(Y/7.0-t*0.62);return smoothstep(0.0,0.03,s)*(1.0-smoothstep(0.03,0.14,s));}

void main(){
    vec4 c=vec4(0.0);
    if(Mode==1){
        // Pillar body (opaque). The side facing the viewer burns white with thin vivid wave lines; toward
        // the silhouette the white thins to streaks over saturated violet, deep violet at the very edge.
        float f=facing(),u=uv.x+Params2.w,t=Time*Params2.x,Y=heightR();
        // Thick irregular wave bands climbing 1 R/s: rows of rounded crests whose shape drifts, each followed
        // by a vivid violet trough broken into drips by fine vertical hatching (2.6 R/s) and the thin streaks.
        float warp=N(vec2(u*2.0,Y*0.12-t*0.1)).a;
        float wave=abs(sin((u*20.0+warp*1.5)*PI));
        float ph=fract((Y-t)/0.34+warp*0.35+N(vec2(u*7.0,Y*0.1-t*0.12)).g*0.18-wave*0.3);
        float hatch=smoothstep(0.25,0.8,N(vec2(u*56.0,Y*0.8-t*2.1)).b);
        // Medium streaks at 2 R/s, long thin streaks at 3.3 R/s, slow lanes, the surges.
        float med=smoothstep(0.5,0.82,N(vec2(u*14.0,Y*0.55-t*1.1)).g);
        float thin=smoothstep(0.56,0.88,N(vec2(u*38.0,Y*0.22-t*0.73)).r);
        float lane=N(vec2(u*4.0+0.37,Y*0.12-t*0.09)).a;
        float sg=surge(Y+warp*0.4,t);
        // Seen from far the streaks are thinner than a pixel; the mipmaps average them out, so they fade
        // toward their mean coverage instead of vanishing (the pillar keeps its brightness at range).
        float far=smoothstep(0.4,1.4,fwidth(u*38.0));
        thin=mix(thin,0.22,far);med=mix(med,0.3,far);hatch=mix(hatch,0.5,far);
        float trough=(1.0-smoothstep(0.0,0.3,ph))*(0.55+0.45*(1.0-hatch))*(1.0-thin*0.6);
        float crest=smoothstep(0.8,0.985,ph)*(1.0-smoothstep(0.985,1.0,ph));
        // Colour under the white: deep violet at the silhouette, vivid violet, then violet-magenta; the
        // slow lanes deepen it (toward deep violet, never toward grey).
        vec3 base=mix(DEEP,VIOLET,smoothstep(0.0,0.3,f));
        base=mix(base,PURPLE,smoothstep(0.35,0.9,f));
        base=mix(base,DEEP,(1.0-smoothstep(0.15,0.7,lane))*0.45*(1.0-smoothstep(0.5,0.9,f)));
        // White: solid toward the centre except the troughs; streaks and crests toward the edges.
        float centre=smoothstep(0.32,0.78,f);
        float wc=1.0-trough*0.8;
        float we=(thin*0.75+med*0.4+crest*0.8+hatch*0.12)*smoothstep(0.02,0.25,f);
        float w=mix(we,wc,centre)+sg*0.32*(0.4+0.6*hatch);
        vec3 col=mix(base,WHITE,clamp(w,0.0,1.0));
        // Foot: a ring of dark lumpy smoke, and a glowing contact line on the ground.
        float top=smokeTop(u);
        float smoke=1.0-smoothstep(top-0.012,top+0.004,Y);
        vec3 smokeCol=mix(vec3(0.05,0.02,0.13),vec3(0.24,0.09,0.46),N(vec2(u*17.0,Y*6.0-Time*0.15)).r*(0.35+0.65*f));
        col=mix(col,smokeCol,smoke);
        col=mix(col,vec3(1.0,0.58,1.0),(1.0-smoothstep(0.0,0.022,Y))*0.9);
        col=mix(col,WHITE,max(Params2.y,Params.w));
        // Torn silhouette.
        float rag=N(vec2(u*30.0,Y*0.5-t*1.5)).g;
        float a=smoothstep(0.0,0.06,f-0.025+0.05*(rag-0.5))*Params2.z;
        if(a<0.004)discard;
        c=vec4(col*a,a);
    }else if(Mode==2){
        // Tower skin (added light): the fastest hairlines and a saturated violet rim.
        float f=facing(),u=uv.x+Params2.w,t=Time*Params2.x,Y=heightR();
        float hair=smoothstep(0.72,0.96,N(vec2(u*70.0,Y*0.16-t*0.75)).b);
        float hair2=smoothstep(0.7,0.95,N(vec2(u*44.0+0.5,Y*0.3-t*1.2)).g);
        float top=smokeTop(u),clear=smoothstep(top,top+0.04,Y);
        float rim=pow(1.0-f,2.2);
        vec3 col=WHITE*(hair*0.5+hair2*0.32)*(0.2+0.8*f)*clear+vec3(0.55,0.08,1.0)*rim*0.6;
        c=vec4(col*Params2.z,0.0);
    }else if(Mode==3){
        // Tower cap seen from above: white centre, violet edge, swirling.
        float rr=length(local.xz);if(rr>1.0)discard;
        float ang=atan(local.z,local.x)/(2.0*PI);
        float sw=N(vec2(ang*8.0+rr*0.6-Time*0.3,rr*1.4-Time*1.1)).r;
        vec3 col=mix(WHITE,PURPLE,smoothstep(0.15,0.95,rr));
        col=mix(col,WHITE,smoothstep(0.55,0.9,sw)*0.6);
        col=mix(col,WHITE,max(Params2.y,Params.w));
        c=vec4(col*Params2.z,Params2.z);
    }else if(Mode==4){
        // Ground glow on a disc 1.1 R wide: the floor of the blast lit magenta with rays and waves running
        // outward, a faint spill of light past the edge. Params: y intensity.
        vec2 d=local.xz*1.1;float rr=length(d);if(rr>1.1)discard;
        float ang=atan(d.y,d.x)/(2.0*PI);
        float ray=N(vec2(ang*24.0,rr*1.2-Time*0.35)).r;
        float waves=pow(max(0.0,sin((rr*6.0-Time*2.4)*PI)),8.0)*smoothstep(0.05,0.3,rr);
        float inside=1.0-smoothstep(0.95,1.0,rr),spill=(1.0-smoothstep(1.0,1.1,rr))*smoothstep(0.9,1.0,rr)*0.25;
        vec3 col=mix(VIOLET,vec3(1.0,0.5,1.0),smoothstep(1.0,0.3,rr))*(0.55+0.75*ray)+WHITE*waves*0.5;
        float a=(inside+spill)*Params.y;
        c=vec4(col*a,a*0.35);
    }else if(Mode==5||Mode==6){
        // Wave front on a unit disc scaled to the front radius. Params: x band width (fraction of the
        // radius), y white mix, z opacity. Params2: x pass (+1 only the near side outside the tower,
        // -1 everything else, 0 all), yz unit direction to the camera, w tower radius / front radius.
        vec2 d=local.xz;float rr=length(d);if(rr>1.0)discard;
        bool shown=dot(d,Params2.yz)>0.0&&rr>=Params2.w;
        if((Params2.x>0.5&&!shown)||(Params2.x<-0.5&&shown))discard;
        float x=(rr-(1.0-Params.x))/max(Params.x,0.001);if(x<0.0)discard;
        float ang=atan(d.y,d.x)/(2.0*PI);
        float lead=exp(-pow((1.0-x)*7.0,2.0));
        if(Mode==5){
            float brk=N(vec2(ang*40.0,x*0.6-Time*0.8)).r;
            float trail=smoothstep(0.0,0.85,x)*(0.4+0.6*brk);
            vec3 col=mix(VIOLET,WHITE,lead*(0.45+0.55*Params.y));
            float lum=(lead*1.25+trail*0.55)*Params.z;
            c=vec4(col*lum,clamp(lum*0.45,0.0,1.0));
        }else{
            // The ground behind the front ripples.
            float prof=exp(-pow((1.0-x)*3.0,2.0))*Params.z;
            vec2 off=(N(vec2(ang*30.0,x*0.8-Time*1.6)).rg-0.5)*0.03*prof;
            c=vec4(texture(Sampler1,gl_FragCoord.xy/ScreenSize+off).rgb*prof,prof);
        }
    }else if(Mode==7){
        // Heat column where the pillar stood (cylinder of radius R): the scene behind the space it left
        // shimmers and rises, strongest low down. Params2: z strength, w pattern offset.
        float u=uv.x+Params2.w,Y=heightR();
        vec2 n=N(vec2(u*10.0,Y*1.2-Time*0.8)).rg-0.5;
        float k=Params2.z*(1.0-smoothstep(0.6,2.6,Y))*smoothstep(0.0,0.25,facing());
        if(k<0.004)discard;
        float a=min(1.0,k*4.0);
        c=vec4(texture(Sampler1,gl_FragCoord.xy/ScreenSize+n*0.014*k).rgb*a,a);
    }else if(Mode==8){
        // Spark (added light): white core, coloured halo; stretched along its quad for motion.
        vec2 d=uv*2.0-1.0;float r2=dot(d,d);
        float core=exp(-r2*14.0),halo=exp(-r2*3.5)*0.45;
        c=vec4((tint.rgb*halo+WHITE*core)*tint.a,0.0);
    }else if(Mode==9){
        // Flare (added light): white core, violet halo, four long rays and a few faint ones.
        vec2 d=uv*2.0-1.0;float r=length(d),ang=atan(d.y,d.x);
        float fade=1.0-smoothstep(0.8,1.0,r);
        float core=exp(-r*r*22.0),halo=exp(-r*r*3.0)*0.55*fade;
        float rays=(pow(abs(cos(ang)),80.0)*0.9+pow(abs(sin(ang)),80.0)*0.9+pow(abs(cos(ang*3.0+0.4)),120.0)*0.4)*exp(-r*2.2)*fade;
        c=vec4((WHITE*core+VIOLET*halo+vec3(1.0,0.82,1.0)*rays)*tint.a,0.0);
    }else if(Mode==10){
        // Smoke puff (covers): lumpy outline, lit from below, darker on top. tint.r seeds the shape.
        // Params: x opacity, y lightness (0 dark violet smoke, 1 pale lavender dust).
        vec2 d=uv*2.0-1.0;float r=length(d),s=tint.r*9.7;
        float lump=N(d*0.35+vec2(s,Time*0.02)).r*0.65+N(d*0.8+vec2(s*1.7,Time*0.03)).g*0.35;
        float shape=1.0-smoothstep(0.35+lump*0.5,1.0,r*1.06);
        float shade=0.3+0.7*smoothstep(0.9,-0.6,d.y);
        vec3 dark=mix(vec3(0.08,0.03,0.18),vec3(0.45,0.41,0.55),Params.y);
        vec3 lit=mix(vec3(0.42,0.16,0.74),vec3(0.86,0.8,0.96),Params.y);
        vec3 col=mix(dark,lit,shade*(0.5+0.5*lump));
        float a=shape*tint.a*Params.x;
        c=vec4(col*a,a);
    }else if(Mode==11){
        // Heat haze over the blast floor between the pillar and the edge, near half only (the far half is
        // behind the pillar). Params: x pillar radius / R, y strength. Params2: yz unit direction to the camera.
        vec2 d=local.xz;float rr=length(d);if(rr>1.0||rr<Params.x||dot(d,Params2.yz)<=0.0)discard;
        float ang=atan(d.y,d.x)/(2.0*PI);
        float edge=smoothstep(Params.x,Params.x+0.05,rr)*(1.0-smoothstep(0.9,1.0,rr));
        vec2 off=(N(vec2(ang*16.0+Time*0.1,rr*3.0-Time*1.4)).rg-0.5)*0.012*edge*Params.y;
        float a=edge*Params.y;
        c=vec4(texture(Sampler1,gl_FragCoord.xy/ScreenSize+off).rgb*a,a);
    }else if(Mode==12){
        // Solid debris: the vertex colour with a little grain, opaque.
        float grain=N(uv*vec2(0.37,0.29)).r;
        c=vec4(tint.rgb*(0.8+0.4*grain)*tint.a,tint.a);
    }
    fragColor=c*ColorModulator;
}
