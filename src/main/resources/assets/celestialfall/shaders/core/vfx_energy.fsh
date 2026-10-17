#version 150
// Botand VFX energy materials. Sampler0 is a tileable, mipmapped noise texture (r smooth fbm, g finer
// fbm, b ridged lines, a coarse fbm) scrolled in several layers at different speeds; Sampler1 is a
// copy of the scene colour for the refraction modes. Output is premultiplied (blend ONE,
// ONE_MINUS_SRC_ALPHA): alpha 0 adds light, alpha 1 covers what is behind.
// Modes: 1 tower body, 2 tower skin, 3 tower cap, 4 ground glow, 5 wave front, 6 wave refraction,
// 7 heat column, 8 spark sprite, 9 flare, 10 smoke puff, 11 ground heat haze, 12 solid (debris),
// 13 glow around the pillar, 14 cloud ring, 15 air shock band, 16 light pool on the ground,
// 17 energy streak (quads moved in the vertex shader), 18 leading edge on the ground, 19 sheet of light at the wall's foot.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform int Mode;
uniform float Time;
uniform vec4 Params;
uniform vec4 Params2;
uniform vec4 Params3;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
in vec2 uv;
in vec4 tint;
in vec3 viewPos;
in vec3 viewNrm;
in vec3 local;
out vec4 fragColor;

const float PI=3.14159265;
const vec3 WHITE=vec3(1.0,0.98,1.0);
// Light violet: deepest at the pillar's silhouette, palest toward the viewer. Picked so the pillar still
// reads light violet (not white) under the bloom outside viewers get; the white comes from the lines.
const vec3 DEEP=vec3(0.40,0.25,0.90);
const vec3 VIOLET=vec3(0.53,0.37,0.98);
const vec3 PURPLE=vec3(0.65,0.50,1.0);
const vec3 LAVENDER=vec3(0.86,0.79,1.0);

vec4 N(vec2 p){return texture(Sampler0,p);}
float facing(){return abs(dot(normalize(viewNrm),normalize(-viewPos)));}
// Tower coordinates. Params: x radius, y mesh height (blocks, from 2 below the ground), z blast radius R,
// w white-out. Params2: x flow speed, y ignition heat, z opacity, w pattern offset around the tower.
float heightR(){return (local.y*Params.y-2.0)/Params.z;}
// Top of the dark smoke ring at the tower's foot, in blast radii: lumpy and slowly churning.
float smokeTop(float u){return 0.12+0.08*(N(vec2(u*9.0,Time*0.05)).r*0.65+N(vec2(u*23.0+0.3,Time*0.08)).g*0.35);}
// The surge racing up the pillar (in step with the quake): a sharp front, a long tail below it.
// Params3: x front height above the base (blocks, < 0 when none), y strength.
float surgeBand(float Y){
    if(Params3.x<0.0)return 0.0;
    float d=(Params3.x-2.0)/Params.z-Y;
    return Params3.y*exp(-max(0.0,-d)/0.05)*exp(-max(0.0,d)/0.3);
}

void main(){
    vec4 c=vec4(0.0);
    if(Mode==1){
        // Pillar body (opaque): light violet energy, palest toward the viewer and deeper toward the
        // silhouette, cut by crisp white flow lines racing upward at several speeds, by rising wave crests
        // and by the surges. White stays in lines and crests; it never thins into a film over the face.
        float f=facing(),u=uv.x+Params2.w,t=Time*Params2.x,Y=heightR();
        float warp=N(vec2(u*2.0,Y*0.1-t*0.22)).a;
        float wave=abs(sin((u*20.0+warp*1.5)*PI));
        // Ripples about 0.9 R apart climbing 2.2 R/s, each crest followed by a faint violet trough broken by hatching (5 R/s).
        float ph=fract((Y-t*2.2)/0.9+warp*0.5+N(vec2(u*5.0,Y*0.06-t*0.2)).g*0.3-wave*0.22);
        float hatch=smoothstep(0.25,0.8,N(vec2(u*56.0,Y*0.4-t*2.0)).b);
        // White flow lines stretched along the rise: 4.5 R/s, 7 R/s, hairlines at 10 R/s and glints at 12 R/s.
        float med=smoothstep(0.54,0.84,N(vec2(u*14.0,Y*0.2-t*0.9)).g);
        float thin=smoothstep(0.58,0.88,N(vec2(u*38.0+0.2,Y*0.1-t*0.7)).r);
        float hair=smoothstep(0.62,0.92,N(vec2(u*72.0+0.6,Y*0.06-t*0.6)).b);
        float glint=smoothstep(0.7,0.95,N(vec2(u*110.0+0.3,Y*0.045-t*0.54)).b);
        float lane=N(vec2(u*4.0+0.37,Y*0.12-t*0.2)).a;
        // Long vertical filaments of brighter and deeper light.
        float fil=smoothstep(0.3,0.8,N(vec2(u*26.0+0.71,Y*0.025-t*0.25)).a);
        float sg=surgeBand(Y+warp*0.25);
        // Seen from far the lines are thinner than a pixel; the mipmaps average them out, so they fade
        // toward their mean coverage instead of vanishing (the pillar keeps its brightness at range).
        float far=smoothstep(0.4,1.4,fwidth(u*72.0));
        hair=mix(hair,0.2,far);thin=mix(thin,0.22,far);med=mix(med,0.3,far);hatch=mix(hatch,0.5,far);glint=mix(glint,0.12,far);
        float trough=(1.0-smoothstep(0.0,0.22,ph))*(0.5+0.5*(1.0-hatch));
        float crest=smoothstep(0.88,0.985,ph)*(1.0-smoothstep(0.985,1.0,ph));
        // Light violet under the white; the slow lanes and the troughs deepen it a little.
        vec3 base=mix(DEEP,VIOLET,smoothstep(0.0,0.35,f));
        base=mix(base,PURPLE,smoothstep(0.4,0.95,f));
        base=mix(base,DEEP,(1.0-smoothstep(0.15,0.7,lane))*0.4*(1.0-smoothstep(0.5,0.9,f)));
        base=mix(base,DEEP,(1.0-fil)*0.3);
        base=mix(base,VIOLET,trough*0.2*smoothstep(0.3,0.8,f));
        float lines=max(max(max(hair,glint),thin*0.9),med*0.7);
        float w=(lines*(1.0-trough*0.25)*(0.55+0.45*fil)+crest*0.35+hatch*0.06)*smoothstep(0.02,0.22,f);
        // The surge lights up the flow lines it passes and washes the face between them a little.
        w+=sg*(0.1+0.6*max(lines,hatch*0.5));
        vec3 col=mix(base,WHITE,clamp(w,0.0,1.0));
        // Foot: a ring of dark lumpy smoke, and a glowing contact line on the ground.
        float top=smokeTop(u);
        float smoke=1.0-smoothstep(top-0.012,top+0.004,Y);
        vec3 smokeCol=mix(vec3(0.07,0.04,0.16),vec3(0.34,0.22,0.58),N(vec2(u*17.0,Y*6.0-Time*0.15)).r*(0.35+0.65*f));
        col=mix(col,smokeCol,smoke);
        col=mix(col,vec3(1.0,0.8,1.0),(1.0-smoothstep(0.0,0.022,Y))*0.9);
        col=mix(col,WHITE,max(Params2.y,Params.w));
        // Torn silhouette.
        float rag=N(vec2(u*30.0,Y*0.5-t*1.5)).g;
        float a=smoothstep(0.0,0.06,f-0.025+0.05*(rag-0.5))*Params2.z;
        if(a<0.004)discard;
        c=vec4(col*a,a);
    }else if(Mode==2){
        // Tower skin (added light): the fastest hairlines (10 and 8 R/s), the surge's sheen and a light violet rim.
        float f=facing(),u=uv.x+Params2.w,t=Time*Params2.x,Y=heightR();
        float hair=smoothstep(0.72,0.96,N(vec2(u*70.0,Y*0.08-t*0.8)).b);
        float hair2=smoothstep(0.7,0.95,N(vec2(u*44.0+0.5,Y*0.12-t*0.96)).g);
        float top=smokeTop(u),clear=smoothstep(top,top+0.04,Y);
        float rim=pow(1.0-f,2.2);
        vec3 col=WHITE*((hair*0.5+hair2*0.32)*(0.2+0.8*f)+surgeBand(Y)*0.15)*clear+vec3(0.72,0.56,1.0)*rim*0.55;
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
        vec3 col=mix(VIOLET,vec3(1.0,0.8,1.0),smoothstep(1.0,0.3,rr))*(0.55+0.75*ray)+WHITE*waves*0.5;
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
    }else if(Mode==13){
        // Glow around the pillar (added light) on a camera-facing strip through its axis: uv.x across, uv.y up.
        // Params: x pillar radius / strip half width, y strength, z where the pillar's top falls on the strip (0..1).
        float x=abs(uv.x*2.0-1.0),edge=Params.x;
        float beyond=max(0.0,x-edge)/max(1.0-edge,0.001);
        float g=exp(-beyond*beyond*4.5)*(1.0-smoothstep(0.8,1.0,x));
        float above=max(0.0,uv.y-Params.z)/max(1.0-Params.z,0.001);
        g*=(1.0-smoothstep(0.0,1.0,above))*smoothstep(0.0,0.02,uv.y);
        g*=0.85+0.3*N(vec2(uv.x*2.0,uv.y*3.0-Time*0.3)).r;
        vec3 col=mix(vec3(0.6,0.44,1.0),vec3(0.92,0.86,1.0),exp(-beyond*4.0));
        c=vec4(col*g*Params.y,0.0);
    }else if(Mode==14){
        // Cloud ring (covers) on a disc: a wide band of cloud racing outward, lit violet by the pillar and brightest
        // on its inner edge. Params: x inner / outer radius, y opacity, z light, w 1 for white condensation mist.
        // Params2: x pass (+1 near half, -1 far half), yz unit direction to the camera.
        vec2 d=local.xz;float rr=length(d);if(rr>1.0||rr<Params.x)discard;
        bool near=dot(d,Params2.yz)>0.0;
        if((Params2.x>0.5&&!near)||(Params2.x<-0.5&&near))discard;
        float w=(rr-Params.x)/max(1.0-Params.x,0.001);
        float ang=atan(d.y,d.x)/(2.0*PI);
        float puff=N(vec2(ang*24.0,w*1.5-Time*0.05)).r*0.6+N(vec2(ang*61.0+0.3,w*3.0)).g*0.4;
        float band=smoothstep(0.0,0.35,w)*(1.0-smoothstep(0.45,1.0,w));
        float shape=smoothstep(0.3,0.7,band*0.8+puff*0.45);
        float lit=clamp(exp(-w*2.2)*Params.z,0.0,1.0);
        vec3 cloud=mix(vec3(0.34,0.24,0.54),vec3(0.82,0.68,1.0),lit)+vec3(1.0,0.92,1.0)*lit*lit*lit*0.5;
        vec3 mist=mix(vec3(0.8,0.75,0.95),vec3(1.0),lit);
        float a=shape*Params.y*(1.0-0.45*Params.w);
        c=vec4(mix(cloud,mist,Params.w)*a,a);
    }else if(Mode==15){
        // Air shock: the low band of a dome rolling out over the ground. Where the view grazes it and along its top
        // edge it bends the scene and catches a faint sheen. Params: x strength, y sheen, z 1 when the scene copy is
        // bound, w band height (fraction of the way from the ground to the dome's top).
        float cap=Params.w;if(uv.y>cap)discard;
        float f=facing(),rim=pow(1.0-f,3.0);
        float edge=exp(-pow((uv.y-cap*0.8)/(cap*0.18),2.0)),fade=1.0-smoothstep(cap*0.8,cap,uv.y);
        vec2 dir=normalize(viewNrm.xy+vec2(0.00001,0.0));
        float bend=(rim*0.7+edge*0.6)*fade*Params.x*(0.6+0.4*N(vec2(uv.x*40.0,uv.y*6.0-Time*2.0)).r);
        vec3 sheen=vec3(0.9,0.85,1.0)*(pow(1.0-f,6.0)*0.5+edge*0.25)*fade*Params.y;
        if(Params.z>0.5){
            float a=min(1.0,bend*6.0);
            if(a<0.004&&dot(sheen,sheen)<0.00001)discard;
            c=vec4(texture(Sampler1,gl_FragCoord.xy/ScreenSize+dir*bend*0.03).rgb*a+sheen,a);
        }else{
            if(dot(sheen,sheen)<0.00001)discard;
            c=vec4(sheen,0.0);
        }
    }else if(Mode==16){
        // Light pool (added light) on the ground around the pillar, falling off past its radius.
        // Params: x disc radius in R, y strength, z pillar radius in R.
        float d=length(local.xz)*Params.x;if(d>Params.x)discard;
        float past=max(0.0,d-Params.z);
        float g=Params.y/(1.0+past*past*1.6)*(1.0-smoothstep(Params.x*0.75,Params.x,d))*smoothstep(Params.z*0.9,Params.z,d);
        c=vec4(vec3(0.6,0.44,1.0)*g,0.0);
    }else if(Mode==17){
        // Energy streak (added light): a white-hot head, a lavender tail tapering to nothing, a soft edge.
        // tint: x 1 for bolts, y surge boost, a brightness; local.y the streak's length in widths.
        float x=uv.x*2.0-1.0,y=uv.y;
        // Dimmed through the smoke ring at the foot (the base is 2 blocks under the ground).
        float foot=smoothstep(2.0+0.12*Params.z,2.0+0.3*Params.z,mix(local.x,local.z,y));
        float across=exp(-x*x*4.0);
        float along=pow(y,1.5)*(1.0-smoothstep(1.0-min(0.5,1.5/max(local.y,1.0)),1.0,y)*0.7);
        float core=exp(-x*x*14.0)*smoothstep(0.6,1.0,y);
        // A bright tip within a couple of widths of the head: the streaks sparkle as they race.
        float tip=exp(-pow((1.0-y)*local.y*0.5,2.0))*exp(-x*x*3.0);
        vec3 col=(mix(LAVENDER,WHITE,smoothstep(0.3,0.9,y))*across*along+WHITE*(core*0.6+tip*0.8))*(1.0+0.5*tint.x+0.6*tint.y);
        c=vec4(col*tint.a*foot,0.0);
    }else if(Mode==18){
        // Leading edge of the opening pillar (added light) on a ground disc reaching a few blocks past its wall: a
        // white-hot contact line at the wall's foot, violet light and jagged rays thrown outward, bright cracks
        // splitting the ground ahead, all flickering. Params: x pillar radius / disc radius, y strength, z disc radius
        // (blocks), w crack strength. Params2: x pass (+1 near half, -1 far half), yz unit direction to the camera.
        vec2 d=local.xz;float rr=length(d);if(rr>1.0||rr<Params.x-0.002)discard;
        bool near=dot(d,Params2.yz)>0.0;
        if((Params2.x>0.5&&!near)||(Params2.x<-0.5&&near))discard;
        float dist=max(0.0,rr-Params.x)*Params.z,reach=max((1.0-Params.x)*Params.z,0.5);
        float ang=atan(d.y,d.x)/(2.0*PI);
        float flick=0.7+0.3*N(vec2(ang*9.0,Time*2.7)).r;
        float contact=exp(-dist*2.6);
        float glow=exp(-dist/2.2);
        // Rays racing outward at about 6 blocks a second, broken up along their length.
        float ray=smoothstep(0.6,0.9,N(vec2(ang*96.0,dist*0.07-Time*0.4)).b)*smoothstep(0.3,0.7,N(vec2(ang*31.0+0.4,dist*0.05-Time*0.25)).r);
        // Cracks: ridged lines wandering outward, lit from the wall and dying out toward the rim.
        float warp=N(vec2(ang*6.0,dist*0.03)).r;
        float crack=smoothstep(0.88,0.975,N(vec2(ang*38.0+warp*0.8,dist*0.02-Time*0.02)).b);
        float far=1.0-smoothstep(reach*0.55,reach,dist);
        vec3 col=WHITE*contact*1.5+mix(VIOLET,LAVENDER,glow)*glow*0.8+LAVENDER*ray*exp(-dist/3.5)*1.1
            +vec3(1.0,0.86,1.0)*crack*Params.w*(0.4+0.6*exp(-dist/3.0))*1.3;
        c=vec4(col*far*flick*Params.y,0.0);
    }else if(Mode==19){
        // Sheet of light hugging the wall's foot (added light) on a cylinder just outside it: brightest low down and
        // toward the silhouette, wisps rising and tearing. Params: x strength.
        float f=facing(),y=uv.y;
        float low=exp(-y*3.2);
        float wisp=smoothstep(0.55,0.9,N(vec2(uv.x*52.0,y*1.4-Time*2.4)).r)*(1.0-y);
        float tear=0.55+0.45*N(vec2(uv.x*18.0,y*0.7-Time*0.9)).g;
        float rim=pow(1.0-f,1.6);
        vec3 col=mix(VIOLET,WHITE,low*0.65)*(low*0.75+wisp*0.85+rim*low*0.5)*tear;
        c=vec4(col*(1.0-smoothstep(0.55,1.0,y))*Params.x,0.0);
    }
    fragColor=c*ColorModulator;
}
