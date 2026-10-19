package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

/**
 * The pull scenes (user request, 2.39.0; 2.40.0 brighter and grander). One pull: under a lamp high above, Bomi looks down
 * into the hourglass she carries in the intro (its crowns ringed with ticks and sapphires like a compass); a magic circle
 * wakes on the floor around her, sparks gather about the glass, and it lights up in the grade's colour: sky blue R,
 * purple SR, yellow SSR. Ten pulls (user requests, 2.40.0, 2.41.0): up on a step stool, Bomi sets the last hourglass into
 * the great cupboard, steps down, wipes her brow, walks back to her chair and sits ({@link GachaTen}); she looks up, the
 * camera swings round behind her to the cupboard, and its ten large hourglasses fill with light one after another,
 * slowly, each in its grade's colour. Everything is a smooth function of the scene's age in ticks.
 */
final class GachaStage {
    private GachaStage(){}
    static final float SECOND=20;
    /** The grades' light (linear RGB): sky blue, purple, yellow. Before a reveal the light is a pale neutral. */
    static final float[][] LIGHT={{.22f,.68f,1f},{.62f,.24f,1f},{1f,.74f,.16f}};
    static final float[] NEUTRAL={.62f,.78f,1f},LAMP={1f,.9f,.74f};
    private static GachaCinema cinema;
    private static final GachaCinema.Frame FRAME=new GachaCinema.Frame();
    private static float last;

    // ---- one pull ----
    /** When the hourglass shows its colour: later for the rarer grades, after a longer gathering of light. */
    static float reveal(Grade g){return (g==Grade.R?3.2f:g==Grade.SR?3.55f:4.0f)*SECOND;}
    /** The light gathers this long before the reveal (its sound swells with it). */
    static final float GATHER=2.2f*SECOND;
    /** The light settles this long after the reveal, then the scene fades to the results. */
    static final float AFTER=1.75f*SECOND,FADE=.5f*SECOND;
    // ---- ten pulls ----
    /** Seated (after {@link GachaTen}'s opening), Bomi raises her head; the camera swings round to the cupboard. */
    static final float LIFT=7.2f*SECOND,LIFT_TIME=.9f*SECOND,ORBIT=7.4f*SECOND,ORBIT_TIME=1.7f*SECOND;
    /** When the first hourglass is full of light, and how far apart they follow. */
    static final float FIRST=9.4f*SECOND,STEP=.5f*SECOND;
    /** How long an hourglass takes to fill with light ("서서히", user request): an SSR gathers longer. */
    static float ramp(Grade g){return (g==Grade.SSR?1.5f:.85f)*SECOND;}
    /** When hourglass {@code i} is full of light and shows its colour: in order, an SR a beat late and an SSR after a held breath. */
    static float lightAt(List<Grade> grades,int i){
        float t=FIRST;
        boolean held=false;
        for(int k=0;k<=i;k++){
            if(k>0)t+=STEP;
            if(grades.get(k)==Grade.SR)t+=.12f*SECOND;
            // the first SSR after a long held breath, any more after a short one
            if(grades.get(k)==Grade.SSR){t+=(held?.3f:.75f)*SECOND;held=true;}
        }
        return t;
    }
    static final float HOLD_TEN=1.6f*SECOND;
    /** How much bigger the 10-pull's cupboard is than 2.40.0's (2.41.0, user request: 1.5 times), and the lamp hung above it. */
    static final float CUPBOARD_GREW=1.5f,LAMP_HIGH=7;
    static float length(List<Grade> grades){
        if(grades.size()==1)return reveal(grades.get(0))+AFTER+FADE;
        return lightAt(grades,grades.size()-1)+HOLD_TEN+FADE;
    }

    static void begin(GachaScreen screen,List<Grade> grades){last=-1;GachaSound.scene(grades.size()==1?GachaSound.Cue.ONE_START:GachaSound.Cue.TEN_START,1,1);}
    /** The scene is over (or skipped): its sounds stop with it. */
    static void stop(){GachaSound.stopScene();}
    /** Readies the scenes' renderer a piece at a time while the gacha screen waits. */
    static void prepare(){if(cinema==null)cinema=new GachaCinema();cinema.prepare();}
    /** Frees the renderer (leaving the world); it stays between pulls and between visits to the gacha otherwise. */
    static void end(){if(cinema!=null){cinema.close();cinema=null;}}

    /** Fills {@code f} with the scene at {@code age}. */
    static void frame(GachaCinema.Frame f,List<Grade> grades,float age){
        reset(f);
        if(grades.size()==1)single(f,grades.get(0),age);else ten(f,grades,age);
    }
    static void render(DrawContext d,GachaScreen screen,List<Grade> grades,float age){
        boolean one=grades.size()==1;
        var f=FRAME;
        frame(f,grades,age);
        if(cinema==null)cinema=new GachaCinema();
        d.draw();
        boolean drawn=cinema.render(f);
        if(!drawn)GachaFlat.draw(d,screen,f,grades,age,one);
        GachaOverlay.draw(d,screen,drawn?cinema:null,f,grades,age,one);
        cues(grades,last,age);last=age;
    }
    private static void reset(GachaCinema.Frame f){
        f.spotColor[0]=f.spotColor[1]=f.spotColor[2]=0;f.beam=0;f.circle=0;f.sparks=0;f.lights=0;f.motes=1;
        f.shadowSpan=3.4f;set(f.shadowAt,0,.8f,.3f);f.lightReach=1;
    }

    /** The scene's sounds between its last frame ({@code from}) and this one ({@code to}). */
    static void cues(List<Grade> grades,float from,float to){
        float len=length(grades);
        if(crossed(from,to,len-1.1f*SECOND))GachaSound.scene(GachaSound.Cue.WHITEOUT,1,1);
        if(grades.size()==1){
            var g=grades.get(0);float r=reveal(g);
            if(crossed(from,to,r-GATHER))GachaSound.scene(GachaSound.Cue.GATHER,1,1);
            if(crossed(from,to,r))GachaSound.scene(g==Grade.R?GachaSound.Cue.REVEAL_R:g==Grade.SR?GachaSound.Cue.REVEAL_SR:GachaSound.Cue.REVEAL_SSR,1,1);
            return;
        }
        // Bomi's opening (2.41.0): the hourglass set down, her steps off the stool, a breath of relief, her footfalls to the
        // chair, the two small steps back to it and the cushion
        if(crossed(from,to,GachaTen.SET))GachaSound.scene(GachaSound.Cue.PLACE,1,1);
        if(crossed(from,to,GachaTen.STEP_ONE))GachaSound.scene(GachaSound.Cue.STEP,1.04f,1);
        if(crossed(from,to,GachaTen.LANDED))GachaSound.scene(GachaSound.Cue.STEP,.96f,1);
        if(crossed(from,to,GachaTen.WIPED-.45f*SECOND))GachaSound.scene(GachaSound.Cue.PHEW,1,1);
        int fell=(int)Math.floor(GachaTen.stride(Math.max(0,to))/Math.PI),before=(int)Math.floor(GachaTen.stride(Math.max(0,from))/Math.PI);
        if(fell>before&&fell>=1)GachaSound.scene(GachaSound.Cue.FOOT,fell%2==0?1.03f:.97f,1);
        if(crossed(from,to,GachaTen.SIT-.08f*SECOND))GachaSound.scene(GachaSound.Cue.FOOT,1.05f,.6f);
        if(crossed(from,to,GachaTen.SIT+.1f*SECOND))GachaSound.scene(GachaSound.Cue.FOOT,.95f,.6f);
        if(crossed(from,to,GachaTen.SEATED-.1f*SECOND))GachaSound.scene(GachaSound.Cue.SIT,1,1);
        if(crossed(from,to,LIFT))GachaSound.scene(GachaSound.Cue.RISE,1,1);
        for(int i=0;i<grades.size();i++)if(crossed(from,to,lightAt(grades,i))){
            var g=grades.get(i);
            if(g==Grade.SSR)GachaSound.scene(GachaSound.Cue.LIGHT_SSR,1,1);
            else GachaSound.scene(g==Grade.R?GachaSound.Cue.LIGHT_R:GachaSound.Cue.LIGHT_SR,(float)Math.pow(2,MELODY[i]/12.0),1);
        }
    }
    /** The hourglasses' bells climb as they light (semitones from the bell's own note). */
    static final int[] MELODY={-7,-5,-3,0,2,5,7,9,12,12};
    private static boolean crossed(float from,float to,float at){return from<at&&to>=at;}

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
        f.chair=f.cupboard=f.wall=f.stool=false;
        var center=BomiRig.apply(f.bones,BomiRig.BODY,GachaCinema.HOURGLASS_CENTER[0],GachaCinema.HOURGLASS_CENTER[1],GachaCinema.HOURGLASS_CENTER[2]);
        // its light on her and around: soft, so her face stays readable while the hourglass itself blooms
        f.lights=1;set(f.lightPos[0],center[0],center[1],center[2]);
        float shine=Math.min(glow,2.2f)*.42f;
        for(int k=0;k<3;k++)f.lightColor[0][k]=c[k]*shine;
        // a lamp far above her, its beam faint in the dark; the hourglass's light takes over as it flares
        float lamp=smooth(0,.6f,s)*(1-.55f*smooth(r-.3f,r+.4f,s));
        set(f.spotPos,0,4.4f,.25f);set(f.spotDir,0,-1,0);f.spotCone[0]=.962f;f.spotCone[1]=.985f;
        for(int k=0;k<3;k++)f.spotColor[k]=LAMP[k]*.95f*lamp;
        f.beam=.03f*lamp;f.beamRadius=1.15f;
        // the magic circle wakes under her as the light gathers and blazes in the grade's colour at the reveal
        // (it wakes faintly as the scene opens, so the wide first shot shows it round her feet)
        f.circle=.32f*smooth(.3f,1.5f,s)+.25f*gather+flare(age,at,new float[]{1.6f,2.2f,3.2f}[gi],new float[]{.55f,.75f,1.05f}[gi]);f.circleRadius=1.35f;f.circleAt[0]=0;f.circleAt[1]=.25f;
        for(int k=0;k<3;k++)f.circleColor[k]=c[k];
        // sparks gathering about the glass, then circling in its colour
        f.sparks=.4f*gather+flare(age,at,new float[]{1.2f,1.8f,2.8f}[gi],new float[]{.5f,.7f,1.0f}[gi]);
        set(f.sparkCenter,center[0],center[1],center[2]);f.sparkRadius=.3f+.06f*colour;f.sparkHeight=.55f;
        for(int k=0;k<3;k++)f.sparkColor[k]=c[k];
        // the camera: first wide, all of her standing in the lamp's light over the circle on the floor, then drawing in
        // low at her left front, looking a little up at her face over the hourglass; a small jolt at the reveal
        float push=smooth(0,r+.8f,s),kick=since>0?(float)Math.exp(-since*7)*(gi+1)/3f:0;
        float[] eye={mix(1.55f,.62f,push),mix(1.62f,1.36f,push),mix(3.7f,2.05f,push)};
        float shake=kick*.012f*(float)Math.sin(s*61);
        set(f.eye,eye[0]+shake,eye[1]+shake*.6f,eye[2]-kick*.05f);
        set(f.target,mix(.05f,.03f,push),mix(1.0f,1.5f,push)-start*.02f,mix(.25f,.36f,push));
        f.fov=mix(38,30,push)+kick*1.4f;f.focus=dist(f.eye,new float[]{0,mix(1.1f,1.45f,push),.2f});f.time=s;
        f.keyDir[0]=-.32f;f.keyDir[1]=.82f;f.keyDir[2]=-.48f;set(f.key,.07f,.085f,.15f);set(f.ambient,.005f,.006f,.011f);
        f.bloom=.46f+.28f*Math.min(1,glow/3);f.exposure=1;f.motes=1;
    }

    /**
     * The ten hourglasses' scale against Bomi's (user requests: 2.40.0 bigger hourglasses, 1.25; 2.40.1 a cupboard three
     * times bigger, its hourglasses with it; 2.41.0 the cupboard 1.5 times 2.40.0's, its hourglasses with it, about 1 m
     * tall, the last of them one she carries).
     */
    static final float BIG=1.25f*1.5f;
    /** The ten hourglasses' places on the cupboard's two shelves, in the order they light (screen left to right, top row first). */
    static float[] shelf(int i){
        int row=i/5,col=i%5;float half=.546f*BIG/2;
        // (2.41.0: a little nearer the shelves' front edge, where she can reach to set the last one home)
        float y=(row==0?GachaCinema.SHELF_HIGH:GachaCinema.SHELF_LOW)+half,x=(2-col)*GachaCinema.SHELF_X,z=GachaCinema.CUPBOARD_Z+.18f*GachaCinema.CUPBOARD_SCALE;
        return new float[]{x,y,z,BIG};
    }
    /** The middle of the cupboard's shelves, where she first looks up to. */
    static float[] cupboardMiddle(){return new float[]{0,(GachaCinema.SHELF_LOW+GachaCinema.SHELF_HIGH)/2+.546f*BIG/2,GachaCinema.CUPBOARD_Z};}
    /** Where Bomi looks during the ten pulls: up at the cupboard, then each hourglass as it fills, then the first SSR if one came. */
    static float[] watch(List<Grade> grades,float age){
        int n=grades.size(),ssr=grades.indexOf(Grade.SSR);
        float[] from=cupboardMiddle(),to=from;float at=0;
        for(int i=0;i<=n;i++){
            float t=i<n?lightAt(grades,i)-.45f*SECOND:lightAt(grades,n-1)+.45f*SECOND;
            if(i==n&&ssr<0)break;
            if(age<t)break;
            from=to;to=shelf(i<n?i:ssr);at=t;
        }
        float k=smooth(at,at+.3f*SECOND,age);
        return new float[]{mix(from[0],to[0],k),mix(from[1],to[1],k),to[2]};
    }
    /**
     * Seated before the cupboard (the 2.40.0 scene, after {@link GachaTen}'s opening): eyes ahead, then up at the cupboard
     * and each hourglass as it fills, leaning back a little where her neck gives out.
     */
    private static void seated(List<Grade> grades,float age,float[][] e){
        float s=age/SECOND;
        float seat=GachaCinema.SEAT+.03f-BomiRig.HIP;
        // her eyes, seated: the head's pivot plus a little
        float headY=seat+22.5f/16+.22f;
        float[] look=watch(grades,age);
        float lookYaw=(float)Math.toDegrees(Math.atan2(look[0],look[2]))*.8f,lookPitch=-(float)Math.toDegrees(Math.atan2(look[1]-headY,look[2]))*.85f;
        float neck=Math.max(lookPitch,-38),leanBack=Math.min(6,Math.max(0,-38-lookPitch)*.5f);
        float lift=smooth(LIFT,LIFT+LIFT_TIME,age),breathe=(float)Math.sin(s*Math.PI*2/3.8f);
        float settle=age>LIFT+LIFT_TIME?(float)Math.exp(-(age-LIFT-LIFT_TIME)/(.25f*SECOND)):0;
        e[BomiRig.ROOT]=new float[]{0,0,0};
        e[BomiRig.BODY]=new float[]{mix(3,1.5f,lift)+.5f*breathe-leanBack*lift,lookYaw*.12f*lift,0};
        e[BomiRig.HEAD]=new float[]{mix(10,neck,lift)+1.5f*settle*(float)Math.sin((age-LIFT-LIFT_TIME)*.6f),lookYaw*.85f*lift,0};
        e[BomiRig.RIGHT_ARM]=new float[]{mix(-30,-38,lift),0,mix(8,11,lift)};e[BomiRig.LEFT_ARM]=new float[]{mix(-30,-38,lift),0,mix(-8,-11,lift)};
        e[BomiRig.RIGHT_LEG]=new float[]{-72,-9,0};e[BomiRig.LEFT_LEG]=new float[]{-72,9,0};
    }
    /** Where her feet are, seated. */
    static final float[] SEATED_AT={0,GachaCinema.SEAT+.03f-BomiRig.HIP,-.04f};
    /** Bomi in the ten-pull: standing through the opening, sitting down (from her standing pose into her seated one), seated. */
    static float[] tenPose(List<Grade> grades,float age){
        float[][] e=new float[7][3];float[] root=new float[3];
        if(age<GachaTen.SIT)return GachaTen.standing(age,e,root);
        seated(grades,age,e);
        if(age>=GachaTen.SEATED)return BomiRig.pose(e,SEATED_AT[0],SEATED_AT[1],SEATED_AT[2],true);
        // sitting: hips back, then down; she leans forward over her knees as she goes, her hands reaching back for the
        // seat, and her legs come up to rest
        float[][] up=new float[7][3];GachaTen.standing(age,up,root);
        float w=smooth(GachaTen.SIT,GachaTen.SEATED,age),back=smooth(GachaTen.SIT,GachaTen.SEATED-.12f*SECOND,age),down=smooth(GachaTen.SIT+.1f*SECOND,GachaTen.SEATED,age);
        float stoop=GachaTen.bump(GachaTen.SIT,GachaTen.SEATED,age);
        for(int i=0;i<7;i++)e[i]=GachaTen.blend(up[i],e[i],i==BomiRig.RIGHT_LEG||i==BomiRig.LEFT_LEG?down:w);
        e[BomiRig.ROOT][1]=0;
        e[BomiRig.BODY][0]+=16*stoop;e[BomiRig.HEAD][0]-=8*stoop;
        e[BomiRig.RIGHT_ARM][0]+=30*stoop;e[BomiRig.LEFT_ARM][0]+=30*stoop;
        return BomiRig.pose(e,mix(root[0],SEATED_AT[0],w),mix(root[1],SEATED_AT[1],down),mix(root[2],SEATED_AT[2],back),true);
    }
    private static void ten(GachaCinema.Frame f,List<Grade> grades,float age){
        float s=age/SECOND;int n=grades.size();
        System.arraycopy(tenPose(grades,age),0,f.bones,0,f.bones.length);
        f.held=false;f.glasses=n;f.chair=f.cupboard=f.wall=f.stool=true;
        // the lamp high above her, following her from the stool to her chair (2.41.0), a narrow cone that leaves a pool of
        // light about her: on as the scene opens (a tiny flutter as it catches), giving way to the hourglasses as they fill
        float lampOn=smooth(.05f*SECOND,.45f*SECOND,age)*(1-.12f*(float)Math.exp(-Math.pow((age-.3f*SECOND)/(.08f*SECOND),2)));
        float dim=1-.6f*smooth(FIRST-ramp(grades.get(0)),lightAt(grades,n-1),age);
        float turned=smooth(ORBIT,ORBIT+ORBIT_TIME,age);
        float[] lamp=GachaTen.lamp(age);
        set(f.spotPos,lamp[0],LAMP_HIGH,lamp[1]);set(f.spotDir,0,-1,0);f.spotCone[0]=.982f;f.spotCone[1]=.9936f;
        for(int k=0;k<3;k++)f.spotColor[k]=LAMP[k]*1.6f*lampOn*dim*(1-.8f*turned);
        f.beam=.028f*lampOn*dim*(1-.7f*turned);f.beamRadius=1.3f;
        // the hourglasses: a faint stir as she looks up, then each filling with light in turn, flaring in its colour; their
        // light carries as far against the big cupboard as it did on the 2.40.0 one. The last is the one she carries in.
        float stir=.07f*smooth(LIFT,ORBIT+ORBIT_TIME,age);
        boolean ssr=false;float best=0,sum=0;f.lightReach=CUPBOARD_GREW;
        for(int i=0;i<n;i++){
            var p=shelf(i);var g=grades.get(i);int gi=g.ordinal();float at=lightAt(grades,i),ra=ramp(g);
            float[] c0=i==n-1&&age<GachaTen.PUSHED?GachaTen.carried(age):p;
            System.arraycopy(BomiRig.place(GachaCinema.HOURGLASS_CENTER,p[3],180+(i==n-1?GachaTen.twist(age):0),c0[0],c0[1],c0[2]),0,f.place[i],0,12);
            float wake=smooth(at-ra,at,age),tint=smooth(at-ra*.5f,at+.05f*SECOND,age);
            float[] c=new float[3];for(int k2=0;k2<3;k2++)c[k2]=mix(NEUTRAL[k2],LIGHT[gi][k2],tint);
            float rest=new float[]{.95f,1.25f,1.7f}[gi],peak=new float[]{2.0f,3.0f,4.8f}[gi];
            float glow=stir+wake*rest*(g==Grade.SSR?1+.25f*(float)Math.sin(s*15)*(1-smooth(at-.1f*SECOND,at,age)):1);
            if(age>=at)glow+=(peak-rest)*(float)Math.exp(-(age-at)/SECOND*3.2f)*Math.min(1,(age-at)/(.12f*SECOND));
            if(g==Grade.SSR){ssr=true;float pulse=smooth(lightAt(grades,n-1)+.4f*SECOND,lightAt(grades,n-1)+.9f*SECOND,age);glow+=pulse*(1.2f+.3f*(float)Math.sin(s*5));}
            f.accent[i]=c;f.glow[i]=glow;f.hold[i]=1;best=Math.max(best,glow);sum+=Math.min(glow,2.5f);
            if(f.lights<GachaCinema.MAX_LIGHTS&&glow>.01f){int l=f.lights++;set(f.lightPos[l],p[0],p[1],p[2]-.065f*p[3]);for(int k2=0;k2<3;k2++)f.lightColor[l][k2]=c[k2]*glow*.62f;}
        }
        // a soft cool light over the stool while she works there, so the cupboard's dark shelves show round her
        float fill=1-smooth(GachaTen.WALK,GachaTen.SEATED,age);
        if(fill>0&&f.lights<GachaCinema.MAX_LIGHTS){int l=f.lights++;set(f.lightPos[l],GachaCinema.STOOL_X-.6f,3.0f,GachaCinema.STOOL_BACK-1.3f);for(int k2=0;k2<3;k2++)f.lightColor[l][k2]=new float[]{.5f,.55f,.82f}[k2]*fill*lampOn;}
        // the hourglasses' light spilling out over the cupboard's doors and the floor before them, and up over its crown,
        // so the cupboard shows as a whole, more of it as more hourglasses fill
        float spill=.6f+.25f*sum,z=GachaCinema.CUPBOARD_Z,top=GachaCinema.CUPBOARD_TOP;
        float spilled=smooth(LIFT,ORBIT+ORBIT_TIME,age);
        if(f.lights<GachaCinema.MAX_LIGHTS){int l=f.lights++;set(f.lightPos[l],0,1.8f,z-1.0f);for(int k2=0;k2<3;k2++)f.lightColor[l][k2]=new float[]{.78f,.8f,1f}[k2]*spill*spilled*.55f;}
        if(f.lights<GachaCinema.MAX_LIGHTS){int l=f.lights++;set(f.lightPos[l],0,top-.2f,z-1.1f);for(int k2=0;k2<3;k2++)f.lightColor[l][k2]=new float[]{.78f,.8f,1f}[k2]*spill*spilled*.35f;}
        // the camera: through the opening it follows her (GachaTen), coming to rest at her right front as she sits;
        // as she looks up it swings round her right side to behind her and draws back until the whole cupboard stands over
        // her in the lamp's light; then it rises slowly toward the hourglasses as they fill
        float lift=smooth(LIFT,LIFT+LIFT_TIME,age),orbit=smooth(ORBIT,ORBIT+ORBIT_TIME,age),after=smooth(ORBIT+ORBIT_TIME,length(grades),age);
        float theta=(float)Math.toRadians(mix(60,168,orbit)),radius=mix(2.95f,4.6f,smooth(.25f,1,orbit)),height=mix(1.25f,1.95f,orbit);
        float[] start={-(float)Math.sin(theta)*radius,height,(float)Math.cos(theta)*radius};
        float[] mid=cupboardMiddle();float aim=smooth(ORBIT+.15f*ORBIT_TIME,ORBIT+ORBIT_TIME,age);
        float tx=mix(0,mid[0]+.2f-.15f*after,aim),ty=mix(.95f+.3f*lift,mix(3.1f,mid[1]+.12f,after),aim),tz=mix(.1f,mid[2],aim);
        if(age<ORBIT){GachaTen.camera(f,age,start,new float[]{tx,ty,tz});}
        else{
            set(f.eye,mix(start[0],-.62f,after),mix(start[1],2.75f,after),mix(start[2],-2.45f,after));
            int first=grades.indexOf(Grade.SSR);
            if(first>=0){
                // the SSR: the camera leans toward it once it has flared
                float lean=smooth(lightAt(grades,first)+.2f*SECOND,lightAt(grades,first)+1.6f*SECOND,age);
                float[] q=shelf(first);tx=mix(tx,q[0],.35f*lean);ty=mix(ty,q[1],.35f*lean);
                for(int k=0;k<3;k++)f.eye[k]=mix(f.eye[k],q[k],.08f*lean);
            }
            set(f.target,tx,ty,tz);
            f.fov=mix(mix(40,46,orbit),43,after);f.focus=mix(dist(f.eye,new float[]{0,1.15f,0}),dist(f.eye,mid),aim);
        }
        f.time=s;
        // the key light comes from the lamp, straight down, so the shadows are the lamp's (over the whole cupboard)
        set(f.keyDir,0,1,0);set(f.key,.012f,.014f,.025f);set(f.ambient,.004f,.0045f,.009f);
        f.shadowSpan=4.4f;set(f.shadowAt,-.4f,.6f,1.9f);
        f.bloom=.42f+.22f*Math.min(1,best/3)+(ssr?.06f:0);f.exposure=1.05f;f.motes=1;
    }
    static float dist(float[] a,float[] b){float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return (float)Math.sqrt(x*x+y*y+z*z);}
}
