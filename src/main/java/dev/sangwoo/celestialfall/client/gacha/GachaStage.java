package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

/**
 * The pull scenes (user request, 2.39.0). One pull: in a dark place Bomi looks down into the hourglass she carries in the
 * intro (its crowns ringed with ticks and sapphires like a compass), and it lights up in the grade's colour: sky blue R,
 * purple SR, yellow SSR. Ten pulls: she sits on a chair before a cupboard holding ten hourglasses, and they light up one
 * after another in their grades' colours. Everything is a smooth function of the scene's age in ticks.
 */
final class GachaStage {
    private GachaStage(){}
    static final float SECOND=20;
    /** The grades' light (linear RGB): sky blue, purple, yellow. Before a reveal the light is a pale neutral. */
    static final float[][] LIGHT={{.22f,.68f,1f},{.62f,.24f,1f},{1f,.74f,.16f}};
    static final float[] NEUTRAL={.62f,.78f,1f};
    private static GachaCinema cinema;
    private static final GachaCinema.Frame FRAME=new GachaCinema.Frame();
    private static float last;

    // ---- one pull ----
    /** When the hourglass shows its colour: later for the rarer grades, after a longer gathering of light. */
    static float reveal(Grade g){return (g==Grade.R?3.2f:g==Grade.SR?3.55f:4.0f)*SECOND;}
    /** The light settles this long after the reveal, then the scene fades to the results. */
    static final float AFTER=1.75f*SECOND,FADE=.5f*SECOND;
    // ---- ten pulls ----
    static final float FIRST=1.6f*SECOND,STEP=.46f*SECOND;
    /** When hourglass {@code i} lights: in order, an SR a beat late and an SSR after a held breath. */
    static float lightAt(List<Grade> grades,int i){
        float t=FIRST;
        for(int k=0;k<=i;k++){if(k>0)t+=STEP;if(grades.get(k)==Grade.SR)t+=.1f*SECOND;if(grades.get(k)==Grade.SSR)t+=.5f*SECOND;}
        return t;
    }
    static final float HOLD_TEN=1.55f*SECOND;
    static float length(List<Grade> grades){
        if(grades.size()==1)return reveal(grades.get(0))+AFTER+FADE;
        return lightAt(grades,grades.size()-1)+HOLD_TEN+FADE;
    }

    static void begin(GachaScreen screen,List<Grade> grades){last=-1;GachaSound.begin(grades);}
    /** Readies the scenes' renderer a piece at a time while the gacha screen waits. */
    static void prepare(){if(cinema==null)cinema=new GachaCinema();cinema.prepare();}
    /** Frees the renderer (leaving the world); it stays between pulls and between visits to the gacha otherwise. */
    static void end(){if(cinema!=null){cinema.close();cinema=null;}}

    static void render(DrawContext d,GachaScreen screen,List<Grade> grades,float age){
        boolean one=grades.size()==1;
        var f=FRAME;
        if(one)single(f,grades.get(0),age);else ten(f,grades,age);
        if(cinema==null)cinema=new GachaCinema();
        d.draw();
        boolean drawn=cinema.render(f);
        if(!drawn)GachaFlat.draw(d,screen,f,grades,age,one);
        GachaOverlay.draw(d,screen,drawn?cinema:null,f,grades,age,one);
        GachaSound.cues(grades,last,age);last=age;
    }

    static float smooth(float a,float b,float x){float t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*t*(t*(t*6-15)+10);}
    static float mix(float a,float b,float t){return a+(b-a)*t;}
    static void set(float[] out,float x,float y,float z){out[0]=x;out[1]=y;out[2]=z;}

    /** How strongly hourglass light glows over a reveal at {@code at}: a flare to {@code peak}, settling to {@code rest}. */
    static float flare(float age,float at,float peak,float rest){
        if(age<at)return 0;float t=(age-at)/SECOND;
        float rise=Math.min(1,t/.12f);
        return rise*(rest+(peak-rest)*(float)Math.exp(-t*3.2f));
    }

    private static void single(GachaCinema.Frame f,Grade g,float age){
        float s=age/SECOND,at=reveal(g),r=at/SECOND,since=s-r;
        int gi=g.ordinal();
        // Bomi leans in and looks down into the hourglass; at the reveal she lifts her head a little, as if dazzled
        float peer=smooth(.2f,2.3f,s),start=since>0?smooth(0,.35f,since)*(1-.55f*smooth(.6f,1.6f,since)):0;
        float breathe=(float)Math.sin(s*Math.PI*2/3.6f);
        float[][] e=new float[7][3];
        e[BomiRig.ROOT]=new float[]{0,0,0};
        e[BomiRig.BODY]=new float[]{.15f*breathe+5*peer-3.5f*start*(gi+1)/3f,0,0};
        e[BomiRig.HEAD]=new float[]{6+22*peer-12*start,8*(1-peer)+3,2*peer+.25f*breathe};
        e[BomiRig.RIGHT_ARM]=BomiRig.CARRY_RIGHT.clone();e[BomiRig.LEFT_ARM]=BomiRig.CARRY_LEFT.clone();
        e[BomiRig.RIGHT_LEG]=new float[]{0,0,0};e[BomiRig.LEFT_LEG]=new float[]{0,0,0};
        System.arraycopy(BomiRig.pose(e,0,0,0),0,f.bones,0,f.bones.length);
        // the light: a pale glow gathering, then the grade's colour flaring and settling
        float gather=smooth(1.6f,r,s),dip=g==Grade.SSR?smooth(r-.75f,r-.45f,s)*(1-smooth(r-.2f,r,s)):0;
        float colour=smooth(r-.04f,r+.12f,s);
        float[] c=new float[3];for(int k=0;k<3;k++)c[k]=mix(NEUTRAL[k],LIGHT[gi][k],colour);
        float neutral=(.35f+.75f*gather)*(1-.85f*dip)*(1+.12f*(float)Math.sin(s*9)*gather);
        float glow=mix(neutral,0,colour)+flare(age,at,new float[]{2.6f,3.6f,5.2f}[gi],new float[]{1.25f,1.6f,2.2f}[gi]);
        f.held=true;f.glasses=1;f.accent[0]=c;f.glow[0]=glow;f.hold[0]=1;
        f.chair=f.cupboard=f.wall=false;
        var center=BomiRig.apply(f.bones,BomiRig.BODY,GachaCinema.HOURGLASS_CENTER[0],GachaCinema.HOURGLASS_CENTER[1],GachaCinema.HOURGLASS_CENTER[2]);
        // its light on her and around: soft, so her face stays readable while the hourglass itself blooms
        f.lights=1;set(f.lightPos[0],center[0],center[1],center[2]);
        float shine=Math.min(glow,2.2f)*.42f;
        for(int k=0;k<3;k++)f.lightColor[0][k]=c[k]*shine;
        // the camera: low at her left front, looking a little up at her face over the hourglass, pushing slowly in;
        // a small jolt at the reveal
        float push=smooth(0,r+.8f,s),kick=since>0?(float)Math.exp(-since*7)*(gi+1)/3f:0;
        float[] eye={mix(.92f,.62f,push),mix(1.28f,1.36f,push),mix(2.6f,2.05f,push)};
        float shake=kick*.01f*(float)Math.sin(s*61);
        set(f.eye,eye[0]+shake,eye[1]+shake*.6f,eye[2]-kick*.05f);
        set(f.target,mix(.05f,.03f,push),mix(1.43f,1.5f,push)-start*.02f,mix(.3f,.36f,push));
        f.fov=mix(33,30,push)+kick*1.2f;f.focus=dist(f.eye,new float[]{0,1.45f,.2f});f.time=s;
        f.keyDir[0]=-.32f;f.keyDir[1]=.82f;f.keyDir[2]=-.48f;set(f.key,.09f,.11f,.19f);set(f.ambient,.005f,.006f,.011f);
        f.bloom=.42f+.25f*Math.min(1,glow/3);f.exposure=1;f.motes=1;
    }

    /** The ten hourglasses' places on the cupboard's two shelves, in the order they light (screen left to right, top row first). */
    static float[] shelf(int i){
        int row=i/5,col=i%5;float scale=.78f,half=.546f*scale/2;
        float y=(row==0?GachaCinema.SHELF_HIGH:GachaCinema.SHELF_LOW)+half,x=(2-col)*GachaCinema.SHELF_X,z=GachaCinema.CUPBOARD_Z+.24f;
        return new float[]{x,y,z,scale};
    }
    /** Where Bomi looks during the ten pulls: each hourglass just before it lights, then the first SSR if one came. */
    static float[] watch(List<Grade> grades,float age){
        int n=grades.size(),ssr=grades.indexOf(Grade.SSR);
        float[] from=shelf(0),to=shelf(0);float at=0;
        for(int i=0;i<=n;i++){
            float t=i<n?lightAt(grades,i)-.34f*SECOND:lightAt(grades,n-1)+.45f*SECOND;
            if(i==n&&ssr<0)break;
            if(age<t)break;
            from=to;to=shelf(i<n?i:ssr);at=t;
        }
        float k=smooth(at,at+.28f*SECOND,age);
        return new float[]{mix(from[0],to[0],k),mix(from[1],to[1],k),to[2]};
    }
    private static void ten(GachaCinema.Frame f,List<Grade> grades,float age){
        float s=age/SECOND;int n=grades.size();
        float[] look=watch(grades,age);
        // her eyes, seated: the head's pivot plus a little
        float headY=(GachaCinema.SEAT+.03f-BomiRig.HIP)+22.5f/16+.22f;
        float yaw=(float)Math.toDegrees(Math.atan2(look[0],look[2]))*.8f,pitch=-(float)Math.toDegrees(Math.atan2(look[1]-headY,look[2]))*.85f;
        float breathe=(float)Math.sin(s*Math.PI*2/3.8f);
        float[][] e=new float[7][3];
        e[BomiRig.ROOT]=new float[]{0,0,0};
        e[BomiRig.BODY]=new float[]{2+.5f*breathe,yaw*.12f,0};
        e[BomiRig.HEAD]=new float[]{pitch,yaw*.85f,0};
        e[BomiRig.RIGHT_ARM]=new float[]{-38,0,11};e[BomiRig.LEFT_ARM]=new float[]{-38,0,-11};
        e[BomiRig.RIGHT_LEG]=new float[]{-72,-9,0};e[BomiRig.LEFT_LEG]=new float[]{-72,9,0};
        System.arraycopy(BomiRig.pose(e,0,GachaCinema.SEAT+.03f-BomiRig.HIP,-.04f),0,f.bones,0,f.bones.length);
        f.held=false;f.glasses=n;f.lights=0;f.chair=f.cupboard=f.wall=true;
        boolean ssr=false;float best=0;
        for(int i=0;i<n;i++){
            var p=shelf(i);var g=grades.get(i);int gi=g.ordinal();float at=lightAt(grades,i);
            System.arraycopy(BomiRig.place(GachaCinema.HOURGLASS_CENTER,p[3],180,p[0],p[1],p[2]),0,f.place[i],0,12);
            float on=smooth(at-.02f*SECOND,at+.1f*SECOND,age);
            float[] c=new float[3];for(int k2=0;k2<3;k2++)c[k2]=mix(NEUTRAL[k2],LIGHT[gi][k2],on);
            float glow=.06f*(1-on)+flare(age,at,new float[]{2.2f,3.2f,4.6f}[gi],new float[]{.95f,1.25f,1.7f}[gi]);
            if(g==Grade.SSR){ssr=true;float pulse=smooth(lightAt(grades,n-1)+.4f*SECOND,lightAt(grades,n-1)+.9f*SECOND,age);glow+=pulse*(1.2f+.3f*(float)Math.sin(s*5));}
            f.accent[i]=c;f.glow[i]=glow;f.hold[i]=1;best=Math.max(best,glow);
            if(f.lights<GachaCinema.MAX_LIGHTS&&glow>.01f){int l=f.lights++;set(f.lightPos[l],p[0],p[1],p[2]-.05f);for(int k2=0;k2<3;k2++)f.lightColor[l][k2]=c[k2]*glow*.55f;}
        }
        // the camera: to her right and a little behind, so she sits at the left in profile, never in front of the shelves,
        // and the whole cupboard stands at the right; drifting slowly in
        float drift=smooth(0,length(grades)/SECOND,s);
        set(f.eye,mix(-2.35f,-2.1f,drift),mix(1.85f,1.8f,drift),mix(-2.2f,-1.85f,drift));
        set(f.target,mix(.05f,.05f,drift),mix(1.25f,1.3f,drift),mix(1.5f,1.6f,drift));
        f.fov=mix(37,35,drift);f.focus=dist(f.eye,new float[]{0,1.3f,2.3f});f.time=s;
        f.keyDir[0]=-.38f;f.keyDir[1]=.8f;f.keyDir[2]=-.46f;set(f.key,.075f,.09f,.16f);set(f.ambient,.006f,.007f,.012f);
        f.bloom=.4f+.2f*Math.min(1,best/3)+(ssr?.05f:0);f.exposure=1;f.motes=1;
    }
    static float dist(float[] a,float[] b){float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return (float)Math.sqrt(x*x+y*y+z*z);}
}
