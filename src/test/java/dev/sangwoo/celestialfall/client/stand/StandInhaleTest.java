package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import java.util.List;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.stand.StandRushTest.FRAME;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's inhale (2.61.0; held down since 2.62.0, its catch whenever someone is in its way, one at a time; since
 * 2.63.0 it sucks with its breath drawn in, the pose in the user's picture, as in the original): smooth at 60 fps
 * through its gasp, its suction with its heaving and trembling, a catch at any moment and its jerk, Y let up at any
 * moment, taking over from a punch stopped short, from the rush and a punch taking over from it; its stances in their
 * order, its mouth where the air goes in, its head flung back clear of its user's and whom it holds clear of it, and its
 * sound fading out as it ends.
 */
class StandInhaleTest {
    /** Sets this client's clock (the inhale's, the rush's) to {@code t} seconds; returns the frame's part of a tick. */
    static float clock(float t){long whole=(long)Math.floor(t*20+1e-4);StandInhale.ticks=whole;StandRush.ticks=whole;return Math.max(0,t*20-whole);}
    /** An inhale heard as it starts {@code start} ticks into this client's clock. */
    static StandInhale.Inhale inhale(long start){return new StandInhale.Inhale(start,0,start);}
    /** A catch heard as it comes, of entity {@code id} (the one before let go). */
    static void caught(StandInhale.Inhale i,int id){
        var before=i.holding();if(before!=null)before.let=i.age(0);
        i.caught.add(new StandInhale.Caught(id,Vec3d.ZERO,Math.max(INHALE_CATCH/20f,i.age(0))));
    }
    /**
     * The server's words as they come on this client's clock: a catch {@code catchAt} ticks in (none if it is below 0 or
     * past its end), and its end at tick {@code end} on its clock (Y let up; or as it has run its longest).
     */
    static void words(StandInhale.Inhale i,long catchAt,long end){
        long in=i.ticksIn();
        if(catchAt>=INHALE_CATCH&&catchAt<Math.min(end,INHALE_LENGTH)&&in>=catchAt&&!i.caughtAny()&&i.on())caught(i,7);
        long over=Math.min(end,INHALE_LENGTH);
        if(i.on()&&in>=over)i.ended=Math.min(Math.max(0,i.age(0)),i.ends());
    }

    @Test void heldToTheEndItBreathesInSucksAndHoldsWithoutAJumpAt60fps()throws Exception{
        var body=new StandRushTest.Watch(bones());var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        var i=inhale(0);float lastEngage=0;boolean front=false;
        for(int f=0;f<=(int)((INHALE_LENGTH/20f+StandInhale.LINGER+StandInhale.RETURN+.2f)/FRAME);f++){
            float t=f*FRAME,delta=clock(t);words(i,INHALE_CATCH,Long.MAX_VALUE);
            var s=StandInhale.inhale(i,delta,0,shape);StandRushTest.look(s,look);
            if(s!=null){
                assertTrue(Math.abs(s.engage-lastEngage)<.25f,"comes round in a jump at "+t);lastEngage=s.engage;
                if(Math.abs(t-INHALE_CATCH/20f)<FRAME)front=s.engage>.95f;
            }
            body.see(look,t,"held to the end");
        }
        assertTrue(front,"in front of its user as it catches");
        assertFalse(i.on());assertEquals(INHALE_LENGTH/20f,i.ends(),1e-6,"over as it has run its longest");
        System.out.println("the inhale's fastest turn in a frame: "+body.fastest+" ("+body.where+")");
    }
    @Test void endedAtAnyMomentItTurnsBackToTheGuardAndGoesBack()throws Exception{
        var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        // Y let up at any moment, having caught someone as it began to suck, a while into it, or no one
        for(int catchAt:new int[]{INHALE_CATCH,31,-1})for(int stop:new int[]{1,2,4,6,7,8,9,10,12,16,23,24,32,33,34,40,60,90,107,108}){
            var i=inhale(0);var body=new StandRushTest.Watch(names);float lastEngage=0;String what=(catchAt<0?"caught no one":"caught someone "+catchAt+" ticks in")+", let up at "+stop;
            for(int f=0;f<=(int)((stop/20f+StandInhale.SETTLE+StandInhale.LINGER+StandInhale.RETURN+.3f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);words(i,catchAt,stop);
                var s=StandInhale.inhale(i,delta,0,shape);StandRushTest.look(s,look);
                if(s!=null){assertTrue(Math.abs(s.engage-lastEngage)<.25f,"goes back in a jump at "+t+" ("+what+")");lastEngage=s.engage;}
                body.see(look,t,what);
            }
            assertFalse(i.on(),what);assertEquals(stop/20f,i.ends(),1e-6,what);
            StandInhale.inhale(i,clock(i.ends()+StandInhale.SETTLE),0,shape);
            assertEquals(1,shape.weights[GUARD],1e-4,"back in its guard ("+what+")");assertTrue(shape.over>=1-1e-4,"turned back ("+what+")");
            assertNull(StandInhale.inhale(i,clock(i.ends()+StandInhale.LINGER+StandInhale.RETURN+.05f),0,shape),"gone back to its place ("+what+")");
        }
    }
    @Test void itTakesOverFromAPunchStoppedShortAndAPunchTakesOverFromIt()throws Exception{
        // Y as a punch is in flight: the server stops the punch there and starts the inhale once the stand has turned back
        // to its guard (StarPlatinumRules.STOP_RETURN); the inhale ends a while later, and a jab asked for after it
        var names=bones();var look=new StarPlatinumLook();var into=new StandPunch.Shape();
        for(int combo=1;combo<=4;combo++)for(int at=1;at<=12;at++)for(boolean anyone:new boolean[]{true,false}){
            float yAt=at/20f;long startTick=Math.round(yAt*20)+STOP_RETURN;float inhaleAt=startTick/20f;
            int end=anyone?20+7*at:12+3*at;float jabAt=inhaleAt+end/20f+(at%4)/20f;
            var before=new StandPunch.Punch(combo,0,Float.NaN,Float.NaN).stop(yAt);var jab=new StandPunch.Punch(1,0,Float.NaN,Float.NaN);
            var i=inhale(startTick);var body=new StandRushTest.Watch(names);float lastEngage=0;
            String what="punch "+combo+" stopped by Y at "+yAt+", the inhale at "+inhaleAt+" ("+(anyone?"caught someone":"caught no one")+", over "+end+" ticks in), a jab at "+jabAt;
            for(int f=0;f<=(int)((jabAt+1.3f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                if(t>=inhaleAt)words(i,anyone?INHALE_CATCH+at:-1,end);
                StandPunch.Shape punch=null;float pt;
                if(t>=jabAt){pt=t-jabAt;if(pt<=StandPunch.length(jab))punch=StandPunch.at(jab,pt,before,t,into);}
                else{pt=t;if(t<=StandPunch.length(before))punch=StandPunch.at(before,t,null,0,into);}
                var s=t<inhaleAt?punch:StandInhale.over(punch,pt,i,delta,0,into);
                StandRushTest.look(s,look);
                if(s!=null){assertTrue(Math.abs(s.engage-lastEngage)<.25f,"in front in a jump at "+t+" ("+what+")");lastEngage=s.engage;}
                body.see(look,t,what);
            }
        }
    }
    @Test void itTakesOverFromTheRushOnceItsFistsAreBackInTheGuard()throws Exception{
        // Y just after the rush is let go: the inhale starts once the rush's fists are back in the guard (RUSH_SETTLE)
        var names=bones();var look=new StarPlatinumLook();var into=new StandPunch.Shape();var rushShape=new StandPunch.Shape();
        for(int stop:new int[]{3,9,20,41,60,83})for(int late=0;late<=6;late+=2){
            var r=new StandRush.Rush(0,0,0,311+stop);long startTick=stop+RUSH_SETTLE+late;var i=inhale(startTick);
            var body=new StandRushTest.Watch(names);String what="the rush let go "+stop+" ticks in, the inhale "+late+" ticks after its fists are back";
            for(int f=0;f<=(int)((startTick/20f+INHALE_LENGTH/20f+1)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                if(r.on()&&StandRush.ticks>=stop)r.ended=r.age(0);
                if(t>=startTick/20f)words(i,INHALE_CATCH,Long.MAX_VALUE);
                var base=StandRush.rush(r,delta,0,rushShape);
                var s=t<startTick/20f?base:StandInhale.over(base==null?null:into.set(base),r.age(delta),i,delta,0,into);
                StandRushTest.look(s,look);body.see(look,t,what);
            }
        }
    }
    @Test void aCatchAtAnyMomentAndOneAfterAnotherJerkItWithoutAJump()throws Exception{
        // (2.62.0) whoever walks into its way is caught then; one let go (gone), it may catch another
        var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        for(int catchAt=INHALE_CATCH;catchAt<INHALE_LENGTH;catchAt+=7)for(int second:new int[]{-1,catchAt+5,catchAt+23}){
            var i=inhale(0);var body=new StandRushTest.Watch(names);String what="caught "+catchAt+" ticks in"+(second<0?"":", let go and another caught "+second+" ticks in");
            float lastPlace=Float.NaN,lastRaise=0;
            for(int f=0;f<=(int)((INHALE_LENGTH/20f+StandInhale.SETTLE+StandInhale.LINGER+StandInhale.RETURN+.2f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                words(i,catchAt,Long.MAX_VALUE);
                if(second>0&&second<INHALE_LENGTH&&i.ticksIn()>=second&&i.caught.size()==1&&i.on())caught(i,8);
                var s2=StandInhale.inhale(i,delta,0,shape);StandRushTest.look(s2,look);
                if(s2!=null){
                    // where it stands in front of its user: out as it rears back and back as it ends (0.8 blocks over a
                    // fifth of a second or so), quivering and jerked up by a catch, never in a jump
                    float place=s2.ahead+s2.lunge;
                    if(!Float.isNaN(lastPlace))assertTrue(Math.abs(place-lastPlace)<.1f,"moves in a jump at "+t+" ("+what+"): "+(place-lastPlace));
                    // (a catch lifts it JERK over GULP_IN: quick, over a few frames)
                    assertTrue(Math.abs(s2.raise-lastRaise)<.04f||lastRaise==0,"jerked up in a jump at "+t+" ("+what+")");
                    lastPlace=place;lastRaise=s2.raise;
                }
                body.see(look,t,what);
            }
        }
    }
    @Test void itRearsBackWithAGaspAndSucksWithItsBreathDrawnInHeavingAndTrembling(){
        // (2.63.0, the user's picture: "빨아 들이는 모션을 이걸로", as in the original) it sucks with its breath drawn in,
        // reared back with its head flung to the sky; it rears back into it with a gasp, and the gasp is where it heaves back to
        var w=new float[NAMES.size()];
        java.util.Arrays.fill(w,0);StandInhale.stances(0,0,w,1);assertEquals(1,w[GUARD],1e-6,"from the guard");
        java.util.Arrays.fill(w,0);StandInhale.stances(StandInhale.DRAW_FROM,0,w,1);assertEquals(1,w[GUARD],1e-6,"its guard until it is round in front");
        assertTrue(StandPunch.burst(StandInhale.DRAW_FROM/StandInhale.RISE)>.98f,"round in front before it draws its breath");
        java.util.Arrays.fill(w,0);StandInhale.stances(StandInhale.DRAW,0,w,1);assertEquals(1,w[INHALE],1e-6,"reared back with a gasp");
        java.util.Arrays.fill(w,0);StandInhale.stances(StandInhale.BEND_TO,0,w,1);assertEquals(1,w[SUCTION],1e-6,"settled into its breath drawn in");
        assertTrue(StandInhale.BEND_FROM<INHALE_CATCH/20f&&StandInhale.BEND_TO>INHALE_CATCH/20f,"it settles into it as it begins to suck");
        assertTrue(StandInhale.BEND_TO-StandInhale.BEND_FROM<=.16f,"quickly");
        assertTrue(StandInhale.DRAW-StandInhale.DRAW_FROM<=.16f,"it rears back hard");
        float least=1,most=0;double sum=0;int n=0;
        for(float t=StandInhale.BEND_TO;t<INHALE_LENGTH/20f;t+=.005f){
            java.util.Arrays.fill(w,0);StandInhale.stances(t,0,w,1);
            float total=0;for(float v:w)total+=v;assertEquals(1,total,1e-5,"its stances add up at "+t);
            least=Math.min(least,w[SUCTION]+w[STRAIN]);most=Math.max(most,w[STRAIN]);sum+=w[STRAIN];n++;
        }
        assertEquals(1-StandInhale.HEAVE,least,.01,"it heaves through the suction");
        assertTrue(most>.42f,"it shudders hard toward the strain: "+most);assertTrue(sum/n>.15,"all the while");
        // a catch jerks it toward its gasp (it gulps them down), quick in, slow out, from still
        assertEquals(0,StandInhale.gulp(0),1e-9);assertEquals(1,StandInhale.gulp(StandInhale.GULP_IN),1e-6);
        assertTrue(StandInhale.gulp(.02f)<.2f,"from still");assertTrue(StandInhale.gulp(.6f)<.4f&&StandInhale.gulp(.6f)>0,"and slowly out");
        java.util.Arrays.fill(w,0);StandInhale.stances(2,1,w,1);assertTrue(w[INHALE]>=StandInhale.GULP-1e-4,"jerked toward its gasp");
        // how hard it sucks: a little as it rears back, full from the suction on until it ends, nothing after
        assertEquals(0,StandInhale.suction(0,5),1e-6);
        assertTrue(StandInhale.suction(.25f,5)>.1f&&StandInhale.suction(.25f,5)<.4f,"the air begins to move");
        assertEquals(1,StandInhale.suction(INHALE_CATCH/20f+.02f,5),1e-3,"full as it begins to suck");
        assertEquals(0,StandInhale.suction(5.2f,5),1e-6,"none once it ends");
    }
    @Test void itSucksWithItsHeadFlungBackToTheSkyWhereTheAirGoesIn(){
        // (2.63.0, the user's picture: its breath drawn in. Under its scarf: it has no mouth; blocks ahead, to its right
        // and up from its feet) its mouth is up at its face flung back to the sky, back over its hips
        float[] gasp=StandInhale.MOUTH[INHALE],suck=StandInhale.MOUTH[SUCTION],strain=StandInhale.MOUTH[STRAIN],guard=StandInhale.MOUTH[GUARD];
        for(float[] m:new float[][]{gasp,suck,strain}){
            assertTrue(m[2]>1.6f&&m[2]<2.1f,"up at its face flung back: "+m[2]);
            assertTrue(m[0]<-.2f,"flung back over its hips: "+m[0]);
            assertEquals(0,m[1],.05,"in the middle");
        }
        assertTrue(gasp[0]<suck[0],"further back still as it gasps: "+gasp[0]+" against "+suck[0]);
        assertTrue(guard[0]-suck[0]>.6f,"flung far back from its guard: "+guard[0]+" against "+suck[0]);
        // its mouth moves with its head: fast as it rears back, never in a jump
        var i=inhale(0);float[] last=null;double fastest=0;
        for(float t=0;t<6;t+=FRAME){
            clock(t);words(i,INHALE_CATCH,Long.MAX_VALUE);
            float[] m=StandInhale.mouth(i,t);
            if(last!=null){double d=Math.sqrt(Math.pow(m[0]-last[0],2)+Math.pow(m[1]-last[1],2)+Math.pow(m[2]-last[2],2));
                assertTrue(d<.3,"its mouth jumps "+d+" blocks at "+t);fastest=Math.max(fastest,d);}
            last=m;
        }
        System.out.println("its mouth's fastest move in a frame: "+fastest+" blocks");
    }
    /** How far a stance reaches in front of where it stands and behind it (blocks, at the size it is drawn), in bands of height up from its feet, as the generator writes it (2.63.0). */
    record Reach(double step,double[] front,double[] back){}
    static Reach reach(String stance)throws Exception{
        try(var in=StandInhaleTest.class.getResourceAsStream("/assets/celestialfall/stands/star_platinum_fists.json")){
            var p=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject(stance).getAsJsonObject("profile");
            var f=p.getAsJsonArray("front");var b=p.getAsJsonArray("back");double[] front=new double[f.size()],back=new double[b.size()];
            // model units (its front -z) to blocks ahead
            for(int k=0;k<f.size();k++)front[k]=f.get(k).isJsonNull()?Double.NaN:-f.get(k).getAsDouble()*StandPunch.SCALE;
            for(int k=0;k<b.size();k++)back[k]=b.get(k).isJsonNull()?Double.NaN:-b.get(k).getAsDouble()*StandPunch.SCALE;
            return new Reach(p.get("step").getAsDouble()*StandPunch.SCALE,front,back);
        }
    }
    @Test void itsHeadFlungBackClearsItsUsersAndWhomItHoldsAreClearOfItInFront()throws Exception{
        // (2.63.0) it sucks reared back, its head flung to the sky and its mane spread out behind it: it stands out in front
        // of its user (AHEAD) far enough that its head stays clear of theirs (their head from 1.4 to 1.9 blocks up, a
        // quarter of a block in front of their middle with its outer layer) however it gasps, trembles, gulps and quivers;
        // and whom it holds (INHALE_AHEAD in front of its user: their body an eighth of a block deep, their head a
        // quarter, from 1.4 up) are clear of it, just in front of it. Heights from its user's feet: it floats UP and RAISE
        // over them, and a gulp lifts it JERK more.
        double lift=StandPresence.UP+StandInhale.RAISE;
        for(int s:new int[]{INHALE,SUCTION,STRAIN}){
            var r=reach(NAMES.get(s));
            for(double up:new double[]{lift,lift+StandInhale.JERK}){
                double behind=Double.MAX_VALUE,body=-Double.MAX_VALUE,head=-Double.MAX_VALUE;
                for(int k=0;k<r.back().length;k++){
                    double lo=up+k*r.step(),hi=lo+r.step();
                    if(hi>1.4&&lo<1.9&&!Double.isNaN(r.back()[k]))behind=Math.min(behind,r.back()[k]);
                    if(lo<1.9&&!Double.isNaN(r.front()[k])){if(lo<1.4)body=Math.max(body,r.front()[k]);if(hi>1.4)head=Math.max(head,r.front()[k]);}
                }
                double clear=StandInhale.AHEAD-StandInhale.QUIVER*.5+behind-.25;
                assertTrue(clear>.04,NAMES.get(s)+": its head flung back clears its user's head by "+clear);
                double gap=INHALE_AHEAD-.125-(StandInhale.AHEAD+StandInhale.QUIVER*.5+body),gapHead=INHALE_AHEAD-.25-(StandInhale.AHEAD+StandInhale.QUIVER*.5+head);
                assertTrue(gap>.02,NAMES.get(s)+": whom it holds clear of it by "+gap);assertTrue(gapHead>.02,NAMES.get(s)+": their head clear of it by "+gapHead);
                assertTrue(gap<.4,NAMES.get(s)+": but just in front of it: "+gap);
                if(up==lift)System.out.printf(java.util.Locale.ROOT,"%s: its head %.3f clear of its user's, whom it holds %.3f clear of it (their head %.3f)%n",NAMES.get(s),clear,gap,gapHead);
            }
        }
        // turned back to its guard once it ends, it has drawn back near its user, its fists clear of whom it held
        var g=reach(NAMES.get(GUARD));double fists=-Double.MAX_VALUE;
        for(int k=0;k<g.front().length;k++)if(!Double.isNaN(g.front()[k])&&lift+k*g.step()<1.9)fists=Math.max(fists,g.front()[k]);
        double free=INHALE_AHEAD-.125-(StandInhale.GUARD_AHEAD+fists);
        assertTrue(free>.1,"its fists in its guard clear of whom it held: "+free);
    }
    @Test void itsSoundFadesOutAsItEndsAndOneOverBeforeItBeganNeverPlays(){
        var playing=new StandInhale.Sound(new Identifier("celestialfall","star_platinum_inhale"),.9f);
        playing.tick();assertEquals(.9f,playing.loudness(),1e-6);playing.fade();
        float lv=playing.loudness();
        for(int k=0;k<StandInhale.FADE;k++){
            assertTrue(playing.canPlay(),"still fading out, not cut off ("+k+")");assertFalse(playing.isDone());
            playing.tick();assertTrue(playing.loudness()<lv,"quieter every tick");lv=playing.loudness();
        }
        assertEquals(0,playing.loudness(),1e-6);assertTrue(playing.isDone());
        var early=new StandInhale.Sound(new Identifier("celestialfall","star_platinum_inhale"),.9f);
        early.fade();assertFalse(early.canPlay(),"over before it began: it never plays");
    }
    static List<String> bones()throws Exception{
        try(var in=StandInhaleTest.class.getResourceAsStream("/assets/celestialfall/geo/entity/star_platinum.geo.json")){
            var list=new java.util.ArrayList<String>();
            for(var b:com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    @Test void whomItHoldsAreDrawnInSmoothlyAndKeptBeforeItAsItsUserWalks(){
        // its user at the origin facing south (+z): whom it caught 3.3 blocks ahead, a little to the side
        double[] at={.6,3.3};double[] last=at.clone();double fastest=0;
        for(int k=1;k<=INHALE_PULL+40;k++){
            // its user walks on at half a walk (about a tenth of a block a tick) and turns a little
            double ux=0,uz=k>INHALE_PULL?(k-INHALE_PULL)*.11:0;float yaw=k>INHALE_PULL?(k-INHALE_PULL)*1.5f:0;
            double[] a=inhaleAnchor(ux,uz,yaw);
            double u=inhalePull(k/(double)INHALE_PULL);
            double[] p={at[0]+(a[0]-at[0])*u,at[1]+(a[1]-at[1])*u};
            if(k>=INHALE_PULL)p=a;
            double step=Math.hypot(p[0]-last[0],p[1]-last[1]);fastest=Math.max(fastest,step);
            assertTrue(step<StandInhale.STEP,"no fling: "+step+" blocks in tick "+k);
            last=p;
        }
        assertTrue(fastest<.5,"drawn in at most half a block a tick: "+fastest);
        double[] a=inhaleAnchor(0,0,0);assertEquals(0,a[0],1e-9);assertEquals(INHALE_AHEAD,a[1],1e-9,"held in front of its user");
    }
}
