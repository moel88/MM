package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.xchara.RainRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the rain of knives, as drawn (2.132.0, the user's words: a run, a stab at whoever is
 * ahead, the knife kept in them while a great many knives lit as the hack lights them pour in from every side): the
 * run leans far forward and stands up again; the stab ends with the knife out ahead and stays there until the last
 * volley; the knives come from all round the one stabbed (none from where its user stands), hang, fly in and stand in
 * them a moment; nothing jumps.
 */
class BladeRainTest {
    static final Vec3d FEET=new Vec3d(10,64,5),USER=new Vec3d(-2.7,0,0);static final float NEVER=Float.MAX_VALUE;static final int SEED=4242;
    static BladeRain.Pose pose(int k,float age){return BladeRain.pose(k,SEED,age,NEVER,FEET,.3,1.8,USER);}

    @Test void theRunLeansFarForwardAndStandsUpAgain(){
        var run=BladeRain.RUN;
        assertSame(run,KnifeCombo.clipOf(RUSH));assertSame(BladeRain.STAB,KnifeCombo.clipOf(RAIN));
        assertEquals(LENGTH[RUSH]/20f,run.length(),1e-4);assertEquals(LENGTH[RAIN]/20f,BladeRain.STAB.length(),1e-4);
        assertEquals(BladeRain.RUSH_LIFE,KnifeCombo.life(RUSH),1e-6);assertEquals(BladeRain.LIFE,KnifeCombo.life(RAIN),1e-6);
        var rest=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(KnifeCombo.AT_REST));
        for(var clip:new dev.sangwoo.celestialfall.client.kageno.Anim[]{run,BladeRain.STAB}){
            var end=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(clip.length())));
            assertEquals(0,rest[0].distanceTo(end[0]),1e-4,"back to the stance");assertEquals(0,rest[1].distanceTo(end[1]),1e-4);
        }
        var start=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(run.frame(0)));
        assertEquals(0,rest[0].distanceTo(start[0]),1e-4,"from the stance");
        for(float t=.2f;t<=.6f;t+=.05f){
            var f=run.frame(t);
            assertTrue(f.pitch()>40,"thrown far forward at "+t+": "+f.pitch());assertTrue(f.up()<-.2);
            var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(f));
            assertTrue(b[1].z<-.5,"the knife trails behind");
        }
        assertTrue(run.frame(RainRules.RUSH_HOLD/20f).pitch()<10,"stood up by the time they are free");
    }
    @Test void theKnifeGoesInAheadAndStaysThereUntilTheLastVolley(){
        var clip=BladeRain.STAB;float stab=RainRules.STAB/20f;
        var wound=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(.14f)));
        var in=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(stab+.02f)));
        assertTrue(in[0].z>wound[0].z+.8,"the hand driven out ahead: "+wound[0].z+" to "+in[0].z);
        assertTrue(clip.frame(.14f).yaw()<-25&&clip.frame(stab).yaw()>10,"wound one way and driven round the other");
        for(float t=stab+.05f;t<=RainRules.WITHDRAW/20f;t+=.05f){
            var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(t)));
            assertTrue(b[1].z>.9,"the knife ahead at "+t+": "+b[1].z);assertTrue(Math.abs(b[1].y)<.25,"level");
            Vec3d tip=b[0].add(b[1].multiply(ShotRules.TIP));
            assertTrue(tip.z>RainRules.KEEP+.6,"its point past the near side of the one they stopped short of at "+t+": "+tip.z);
            assertTrue(tip.y>.7&&tip.y<1.6,"at the body's height: "+tip.y);
            assertTrue(clip.frame(t).pitch()>18,"the body leaning on it");
        }
        // the free hand up over the shoulder through the rain, down as the last volley lands
        float mid=(RainRules.FIRST+RainRules.LAST)/40f;
        Vec3d up=KagenoClips.freeHand(Vec3d.ZERO,0,R6.poseOf(clip.frame(mid))),down=KagenoClips.freeHand(Vec3d.ZERO,0,R6.poseOf(clip.frame(RainRules.WITHDRAW/20f)));
        assertTrue(up.y>1.5,"the free hand up: "+up.y);assertTrue(down.y<up.y-.5,"and down at the end: "+down.y);
        // drawn out: the hand back
        var out=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(RainRules.HOLD/20f)));
        assertTrue(out[0].z<in[0].z-.6);
        // lit while it is in, black before and after
        assertNull(BladeRain.lit(stab-.01f,Float.NaN));
        assertEquals(1,BladeRain.lit(stab+.3f,Float.NaN)[0]);assertEquals(.3f,BladeRain.lit(stab+.3f,Float.NaN)[1],1e-5);
        assertEquals(0,BladeRain.lit(RainRules.WITHDRAW/20f+.1f,Float.NaN)[0]);assertNull(BladeRain.lit(RainRules.WITHDRAW/20f+.5f,Float.NaN));
        assertEquals(0,BladeRain.lit(1f,.9f)[0],"stopped short: it goes out then");assertNull(BladeRain.lit(.1f,.05f));
    }
    @Test void nothingOfTheBodyJumpsAndTheKnifeKeepsOffTheGround(){
        for(var clip:new dev.sangwoo.celestialfall.client.kageno.Anim[]{BladeRain.RUN,BladeRain.STAB}){
            Vec3d lastFist=null,lastHand=null;float lastYaw=0,lastPitch=0;
            for(float t=0;t<=clip.length();t+=1/60f){
                var fr=clip.frame(t);var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(fr));Vec3d hand=KagenoClips.freeHand(Vec3d.ZERO,0,R6.poseOf(fr));
                assertTrue(b[0].add(b[1].multiply(ShotRules.TIP)).y>.1,"the knife's point clear of the ground at "+t);
                if(lastFist!=null){
                    assertTrue(b[0].distanceTo(lastFist)<.45,"the knife's hand at "+t+": "+b[0].distanceTo(lastFist));
                    assertTrue(hand.distanceTo(lastHand)<.45,"the free hand at "+t+": "+hand.distanceTo(lastHand));
                    assertTrue(Math.abs(fr.yaw()-lastYaw)<16&&Math.abs(fr.pitch()-lastPitch)<12,"the body at "+t);
                }
                lastFist=b[0];lastHand=hand;lastYaw=fr.yaw();lastPitch=fr.pitch();
            }
        }
    }
    @Test void theBodyIsDrawnAlongTheRunAndTheStabsLastWay(){
        Vec3d from=new Vec3d(0,64,0),to=new Vec3d(0,64,8);
        assertEquals(0,BladeRain.ran(from,to,0).distanceTo(from),1e-9);
        Vec3d last=from;
        for(float t=0;t<=30;t+=.25f){
            Vec3d at=BladeRain.ran(from,to,t);
            assertTrue(at.z>=last.z-1e-9&&at.z<=8+1e-9);assertEquals(RainRules.ran(t,8),at.z,1e-6);
            assertTrue(at.distanceTo(last)<=RainRules.SPEED*.25+1e-6,"no jump");last=at;
        }
        assertEquals(from,BladeRain.ran(from,from,9),"nowhere to run: where they are");
        Vec3d a=new Vec3d(0,64,5),b=new Vec3d(0,64.5,7);
        assertEquals(0,BladeRain.lunged(a,b,0).distanceTo(a),1e-9);assertEquals(0,BladeRain.lunged(a,b,RainRules.ARRIVE).distanceTo(b),1e-9);
        assertEquals(1,BladeRain.held(RainRules.ARRIVE,RainRules.ARRIVE));assertEquals(0,BladeRain.held(RainRules.ARRIVE+10,RainRules.ARRIVE));
    }
    @Test void theKnivesComeFromAllRoundAndFromOverThemButNotFromWhereTheUserStands(){
        int[] quarter=new int[4];int high=0;Vec3d u=USER.normalize();
        for(int k=0;k<RainRules.BLADES;k++){
            Vec3d w=BladeRain.from(k,SEED,USER);
            assertEquals(1,w.length(),1e-9);
            double el=Math.toDegrees(Math.asin(w.y));
            assertTrue(el>=BladeRain.LOWEST-1e-6&&el<=BladeRain.HIGHEST+1e-6,"over the ground: "+el);
            Vec3d flat=new Vec3d(w.x,0,w.z).normalize();
            if(flat.dotProduct(u)>Math.cos(Math.toRadians(BladeRain.CLEAR)))assertTrue(el>=BladeRain.OVER-1e-6,"none through where its user stands: knife "+k+" at "+el);
            quarter[(int)Math.floor((Math.atan2(w.z,w.x)+Math.PI)/(Math.PI/2))%4]++;if(el>40)high++;
            double out=BladeRain.out(k,SEED);assertTrue(out>=BladeRain.NEAR&&out<=BladeRain.FAR);
            Vec3d aim=BladeRain.aim(k,SEED,.3,1.8);
            assertTrue(Math.abs(aim.x)<=.3&&Math.abs(aim.z)<=.3&&aim.y>.3&&aim.y<1.6,"aimed into the body");
            if(k>0){Vec3d was=BladeRain.from(k-1,SEED,USER);assertTrue(new Vec3d(w.x,0,w.z).normalize().dotProduct(new Vec3d(was.x,0,was.z).normalize())<.6,"each from far off the last");}
        }
        for(int q:quarter)assertTrue(q>=12,"from every side: "+java.util.Arrays.toString(quarter));
        assertTrue(high>=15,"and from over them: "+high);
        assertEquals(BladeRain.from(5,SEED,USER),BladeRain.from(5,SEED,USER),"the same on every client");
    }
    @Test void eachComesHangsFliesInAndStandsInThemAMoment(){
        for(int k:new int[]{0,1,31,62,89}){
            float lands=(float)RainRules.landsAt(k),comes=lands-BladeRain.BEFORE;
            assertNull(pose(k,comes-.05f),"nothing of it before it comes");
            Vec3d way=BladeRain.from(k,SEED,USER),target=FEET.add(BladeRain.aim(k,SEED,.3,1.8));
            var hang=pose(k,comes+BladeRain.IN+.2f);
            assertNotNull(hang);assertFalse(hang.flying());assertFalse(hang.in());assertEquals(1,hang.shown(),1e-4);
            assertEquals(0,hang.dir().distanceTo(way.negate()),1e-9,"pointing at them");
            double far=hang.tip().distanceTo(target);
            assertEquals(BladeRain.out(k,SEED),far,.1,"out on the shell");
            assertEquals(BladeRain.TIP,hang.tip().distanceTo(hang.grip()),1e-6);
            // in flight: nearer and nearer, quicker and quicker, along its own line
            double last=Double.MAX_VALUE,step=0;
            for(float t=lands-BladeRain.FLY+.2f;t<lands;t+=.2f){
                var p=pose(k,t);assertTrue(p.flying());
                double d=p.tip().subtract(target).dotProduct(way);
                assertTrue(d<last,"nearer");if(last!=Double.MAX_VALUE){assertTrue(last-d>=step-1e-6,"quicker");step=last-d;}
                last=d;
                assertEquals(0,p.tip().subtract(target).crossProduct(way).length(),1e-6,"on its line");
            }
            var in=pose(k,lands+.5f);
            assertTrue(in.in()&&!in.flying());
            assertEquals(-BladeRain.IN_DEEP,in.tip().subtract(target).dotProduct(way),1e-6,"its point in them");
            assertTrue(in.shown()>.99f);
            assertNull(pose(k,lands+BladeRain.STUCK+.01f),"and gone");
        }
        // a great many at once through the rain
        int most=0;
        for(float t=RainRules.FIRST;t<=RainRules.LAST;t+=.5f){int n=0;for(int k=0;k<RainRules.BLADES;k++)if(pose(k,t)!=null)n++;most=Math.max(most,n);}
        assertTrue(most>=30,"pouring: "+most+" at once");
    }
    @Test void aKnifeMovesNoFurtherInAFrameThanItFlies(){
        for(int k=0;k<RainRules.BLADES;k+=7){
            BladeRain.Pose last=null;
            for(float t=(float)RainRules.landsAt(k)-BladeRain.BEFORE;t<RainRules.landsAt(k)+BladeRain.STUCK;t+=1/3f){
                var p=pose(k,t);
                if(p!=null&&last!=null)assertTrue(p.tip().distanceTo(last.tip())<2.2,"knife "+k+" at "+t+": "+p.tip().distanceTo(last.tip()));
                last=p;
            }
        }
    }
    @Test void stoppedShortTheKnivesNotYetInAreGoneAndNoMoreCome(){
        float stop=RainRules.FIRST+10.2f;
        for(int k=0;k<RainRules.BLADES;k++){
            float lands=(float)RainRules.landsAt(k),comes=lands-BladeRain.BEFORE;
            var after=BladeRain.pose(k,SEED,stop+BladeRain.VANISH+.01f,stop,FEET,.3,1.8,USER);
            if(lands>stop)assertNull(after,"knife "+k+" not yet in is gone");
            if(comes>=stop)assertNull(BladeRain.pose(k,SEED,comes+3,stop,FEET,.3,1.8,USER),"none comes after it stopped");
            if(lands>stop&&comes<stop){
                BladeRain.Pose a=BladeRain.pose(k,SEED,stop+.5f,stop,FEET,.3,1.8,USER),b=BladeRain.pose(k,SEED,stop+1.5f,stop,FEET,.3,1.8,USER);
                if(a!=null&&b!=null){assertEquals(0,a.tip().distanceTo(b.tip()),.3,"it hangs where it was");assertTrue(b.shown()<a.shown());}
            }
        }
    }
    @Test void itsSoundsAreOnesTheGameHasThroughTheRain(){
        var cues=BladeRain.cues();int come=0,strike=0;
        for(var c:cues){
            int at=(Integer)c[1];float part=(Float)c[2];
            assertTrue(at>RainRules.STAB&&at<=RainRules.LAST,"after the knife is in, by the last volley: "+at);assertTrue(part>0&&part<=1);
            if(c[0].equals(BladeRain.COME_SOUND))come++;else{assertEquals(BladeRain.STRIKE_SOUND,c[0]);strike++;}
        }
        assertTrue(come>=8&&strike>=10,"through all of it: "+come+" and "+strike);
        var last=cues.get(cues.size()-1);
        assertEquals(BladeRain.STRIKE_SOUND,last[0]);assertEquals(RainRules.LAST-1,(Integer)last[1]);assertEquals(BladeRain.LAST_PART,(Float)last[2]);
        assertEquals("xchara_jab",BladeRain.STAB_SOUND);
    }
}
