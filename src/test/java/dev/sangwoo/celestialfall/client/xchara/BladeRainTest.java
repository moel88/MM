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
 * volley, borne on standing and straining with both hands on it (2.132.1); the knives come to be in the air all round
 * the one stabbed (none from where its user stands unless from high), aimed at them, and fly in, not too fast, to
 * stand in them a moment (2.132.3, as 2.132.0's); nothing jumps.
 */
class BladeRainTest {
    static final Vec3d FEET=new Vec3d(10,64,5),USER=new Vec3d(-2.7,0,0);static final float NEVER=Float.MAX_VALUE;static final int SEED=4242;
    static BladeRain.Pose pose(int k,float age){return pose(k,age,NEVER);}
    static BladeRain.Pose pose(int k,float age,float stop){return BladeRain.pose(k,SEED,age,stop,FEET,.3,1.8,USER);}
    /** The point knife {@code k} aims at in the one stabbed, and the way it comes from (out from them). */
    static Vec3d target(int k){return FEET.add(BladeRain.aim(k,SEED,.3,1.8));}
    static Vec3d way(int k){return BladeRain.from(k,SEED,USER);}

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
        var wound=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(.21f)));
        var in=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(stab+.02f)));
        assertTrue(in[0].z>wound[0].z+.8,"the hand driven out ahead: "+wound[0].z+" to "+in[0].z);
        assertTrue(clip.frame(.21f).yaw()<-25&&clip.frame(stab).yaw()>10,"wound one way and driven round the other");
        for(float t=stab+.05f;t<=RainRules.WITHDRAW/20f;t+=.05f){
            var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(t)));
            assertTrue(b[1].z>.9,"the knife ahead at "+t+": "+b[1].z);assertTrue(Math.abs(b[1].y)<.25,"level");
            Vec3d tip=b[0].add(b[1].multiply(ShotRules.TIP));
            assertTrue(tip.z>RainRules.KEEP+.6&&tip.z<RainRules.KEEP+1.3,"its point through the one they stopped short of at "+t+": "+tip.z);
            assertTrue(tip.y>.7&&tip.y<1.6,"at the body's height: "+tip.y);
        }
        // (2.132.1, user requests: "버틸때 양손으로 버티고 정자세로 서서 버텨", "서서 버티는데 버티고 있다는 자세를
        // 만들어줘 힘 줘서 버티고 있다는") borne on through the rain standing and straining: square to them and leaning
        // into the knife, the knees bent and set, the feet planted apart (the left behind to push from), the head down,
        // both hands on the knife's rod, the body shaking with the strain
        double shook=0;R6.Frame was=null;
        for(float t=stab+.3f;t<=RainRules.WITHDRAW/20f;t+=1/60f){
            var fr=clip.frame(t);var p=R6.poseOf(fr);
            assertTrue(fr.pitch()>9&&fr.pitch()<21,"leaning into it at "+t+": "+fr.pitch());
            assertTrue(Math.abs(fr.yaw())<5&&Math.abs(fr.roll())<3,"square to them at "+t+": "+fr.yaw()+", "+fr.roll());
            assertTrue(fr.up()<-.07&&fr.up()>-.17,"standing, a little sunk at "+t+": "+fr.up());
            assertTrue(p.rKnee>8&&p.rKnee<50&&p.lKnee>8&&p.lKnee<50,"the knees bent and set at "+t+": "+p.rKnee+", "+p.lKnee);
            double[] r=KnifeComboTest.foot(fr,0),l=KnifeComboTest.foot(fr,1);
            assertTrue(r[2]-l[2]>.35&&r[2]-l[2]<.8,"the left foot behind to push from at "+t+": "+r[2]+", "+l[2]);
            assertTrue(r[0]-l[0]>.38&&r[0]-l[0]<.62,"planted apart at "+t+": "+(r[0]-l[0]));
            assertTrue(p.headPitch+fr.pitch()>8,"the head down at them at "+t);
            var b=KagenoClips.blade(Vec3d.ZERO,0,p);Vec3d rod=b[0].subtract(b[1].multiply(GiantCleave.GRIP_BACK));
            assertTrue(KagenoClips.freeHand(Vec3d.ZERO,0,p).distanceTo(rod)<.04,"the other hand on the rod at "+t+": "+KagenoClips.freeHand(Vec3d.ZERO,0,p).distanceTo(rod));
            assertTrue(b[0].y>.8&&b[0].y<1.15&&Math.abs(b[0].x)<.15,"the hands low before the middle of the body at "+t+": "+b[0]);
            if(was!=null){
                double d=Math.abs(fr.pitch()-was.pitch())+Math.abs(fr.roll()-was.roll());shook=Math.max(shook,d);
                assertTrue(d<4,"shaking, not jumping at "+t+": "+d);
            }
            was=fr;
        }
        assertTrue(shook>.6,"the strain shakes them: "+shook);
        assertTrue(BladeRain.strain(stab)==0&&BladeRain.strain(stab+.4f)>.5f&&BladeRain.strain(RainRules.WITHDRAW/20f+.1f)==0,"shaking only while they bear on it");
        // the free arm thrown back as it goes in; off the rod again once it is torn out
        var thrown=R6.poseOf(clip.frame(stab+.05f));var drawn=R6.poseOf(clip.frame(RainRules.WITHDRAW/20f+.12f));
        for(var p:new R6.Pose[]{thrown,drawn}){
            var b=KagenoClips.blade(Vec3d.ZERO,0,p);
            assertTrue(KagenoClips.freeHand(Vec3d.ZERO,0,p).distanceTo(b[0].subtract(b[1].multiply(GiantCleave.GRIP_BACK)))>.5,"one hand on it");
        }
        assertEquals(0,BladeRain.both(stab),1e-6);assertEquals(1,BladeRain.both(stab+.3f),1e-6);assertEquals(1,BladeRain.both(RainRules.WITHDRAW/20f),1e-6);assertEquals(0,BladeRain.both(RainRules.WITHDRAW/20f+.1f),1e-6);
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
    @Test void theKnivesComeFromAllRoundThemButNotFromWhereTheUserStandsUnlessFromHigh(){
        // (2.132.3, user requests: "칼이 너무 빠르게 떨어져", "그냥 저 칼이 다시 플레이어를 향하게 바꾸자", answered
        // "2.132.0처럼 사방에서")
        assertEquals(480,RainRules.BLADES,"sixteen a volley, all into them");
        assertEquals(.6,BladeRain.SIZE,1e-9,"a little bigger than 2.132.0's (2.132.2: .6 of the knife in the hand)");
        int[] quarter=new int[4];int high=0;Vec3d u=USER.normalize();
        for(int k=0;k<RainRules.BLADES;k++){
            Vec3d w=way(k),t=target(k);
            assertEquals(1,w.length(),1e-9);
            double el=Math.toDegrees(Math.asin(w.y));
            assertTrue(el>=BladeRain.LOWEST-1e-6&&el<=BladeRain.HIGHEST+1e-6,"between low and high over them: "+el);
            // never from where its user stands, unless from high over them
            double toUser=Math.toDegrees(Math.acos((w.x*u.x+w.z*u.z)/Math.hypot(w.x,w.z)));
            if(toUser<BladeRain.CLEAR)assertTrue(el>=BladeRain.OVER-1e-6,"from their side only from high: knife "+k+" "+toUser+"° round, "+el+"° up");
            if(el>45)high++;
            // aimed at their middle: within their width, between their knees and their head
            assertTrue(Math.abs(t.x-FEET.x)<=.17&&Math.abs(t.z-FEET.z)<=.17&&t.y>=FEET.y+.5&&t.y<=FEET.y+1.52,"at their middle: "+t);
            quarter[(int)Math.floor((Math.atan2(w.z,w.x)+Math.PI)/(Math.PI/2))%4]++;
        }
        for(int q:quarter)assertTrue(q>=80,"from all round them: "+java.util.Arrays.toString(quarter));
        assertTrue(high>=RainRules.BLADES/8,"and from high: "+high);
        long own=java.util.stream.IntStream.range(0,RainRules.BLADES).filter(BladeRain::onThem).count();
        assertEquals(RainRules.VOLLEYS,own,"one of each volley its own (as many as the server's blows)");
        assertEquals(pose(5,RainRules.FIRST),pose(5,RainRules.FIRST),"the same on every client");
    }
    @Test void eachComesToBeOutInTheAirAimedAtThemAndFliesInToStandInThem(){
        for(int k:new int[]{0,1,31,62,119,240,359,479}){
            float lands=(float)RainRules.landsAt(k),comes=lands-BladeRain.BEFORE,goes=lands-BladeRain.FLY;
            assertNull(pose(k,comes-.05f),"nothing of it before it comes");
            var first=pose(k,comes+.01f);
            assertNotNull(first);assertFalse(first.flying()||first.in(),"it comes to be standing in the air");
            double out=first.tip().distanceTo(target(k));
            assertTrue(out>=BladeRain.NEAR-1e-6&&out<=BladeRain.FAR+1e-6,"out in the air round them: "+out);
            // aimed at them all the way: pointing at the point it aims at, its point on the line out to it
            double last=Double.MAX_VALUE,step=0;
            for(float t=comes+.25f;t<lands;t+=.25f){
                var p=pose(k,t);
                assertEquals(0,p.dir().distanceTo(way(k).negate()),1e-9,"pointing at them");
                Vec3d off=p.tip().subtract(target(k));
                assertEquals(0,off.crossProduct(way(k)).length(),1e-9,"on its line in");
                double d=off.dotProduct(way(k));
                if(t>goes+.01f){
                    assertTrue(p.flying());assertTrue(d<last,"flying in");
                    if(last!=Double.MAX_VALUE){assertTrue(last-d>=step-1e-6,"quicker and quicker");step=last-d;}
                }else assertFalse(p.flying(),"not before it goes");
                last=d;
            }
            var in=pose(k,lands+1.5f);
            assertTrue(in.in()&&!in.flying());
            assertEquals(BladeRain.IN_DEEP,in.tip().subtract(target(k)).dotProduct(way(k).negate()),.03,"its point a little past where it aims");
            assertNull(pose(k,lands+BladeRain.STUCK+.01f),"and gone");
        }
        // a great many at once through the rain
        int most=0;
        for(float t=RainRules.FIRST;t<=RainRules.LAST;t+=.5f){int n=0;for(int k=0;k<RainRules.BLADES;k++)if(pose(k,t)!=null)n++;most=Math.max(most,n);}
        assertTrue(most>=140,"pouring in: "+most+" at once");
    }
    @Test void aKnifeFliesInNotTooFastAndNoFurtherInAFrameThanItFlies(){
        // ("칼이 너무 빠르게 떨어져": 2.132.0's darted in over 2.4 ticks, its last at some 4.7 blocks a tick; 2.132.2's fell
        // over 6 ticks from 11 blocks up, its last at some 3.2 blocks a tick)
        double fastest=0;
        for(int k=0;k<RainRules.BLADES;k+=7){
            BladeRain.Pose last=null;
            for(float t=(float)RainRules.landsAt(k)-BladeRain.BEFORE;t<RainRules.landsAt(k)+BladeRain.STUCK;t+=1/3f){
                var p=pose(k,t);
                if(p!=null&&last!=null){double d=p.tip().distanceTo(last.tip());assertTrue(d<.8,"knife "+k+" at "+t+": "+d);fastest=Math.max(fastest,d*3);}
                last=p;
            }
        }
        assertTrue(fastest<1.8,"at most some 1.7 blocks a tick, even as it goes in: "+fastest);
        assertTrue(fastest>.8,"but flying: "+fastest);
    }
    @Test void stoppedShortTheKnivesNotYetInAreGoneAndNoMoreCome(){
        float stop=RainRules.FIRST+10.2f;
        for(int k=0;k<RainRules.BLADES;k++){
            float lands=(float)RainRules.landsAt(k),comes=lands-BladeRain.BEFORE;
            var after=pose(k,stop+BladeRain.VANISH+.01f,stop);
            if(lands>stop)assertNull(after,"knife "+k+" not yet in is gone");
            if(comes>=stop)assertNull(pose(k,comes+3,stop),"none comes after it stopped");
            if(lands>stop&&comes<stop){
                BladeRain.Pose a=pose(k,stop+.5f,stop),b=pose(k,stop+1.5f,stop);
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
    /** (2.132.2) Knives that come by a camera fade there, not cutting across the lens; they are drawn round a coarser outline of the knife. */
    @Test void aKnifeFadesByTheCameraNotAcrossTheLens(){
        Vec3d camera=new Vec3d(0,66,0);
        assertEquals(0,RainFx.near(new Vec3d(.5,60,0),new Vec3d(.5,70,0),camera),1e-6,"its line passing half a block from the lens: none of it");
        assertEquals(0,RainFx.near(new Vec3d(1.1,64,0),new Vec3d(1.1,67,0),camera),1e-6);
        assertEquals(0,RainFx.near(new Vec3d(1.4,64,0),new Vec3d(1.4,67,0),camera),1e-6);
        assertEquals(1,RainFx.near(new Vec3d(4.6,60,0),new Vec3d(4.6,70,0),camera),1e-6,"four and a half blocks off: all of it");
        assertEquals(1,RainFx.near(new Vec3d(0,71,0),new Vec3d(0,74,0),camera),1e-6,"over the camera by five: all of it (its line's nearest end)");
        float last=0;
        for(double x=0;x<=5;x+=.01){float v=RainFx.near(new Vec3d(x,60,0),new Vec3d(x,70,0),camera);assertTrue(v>=last-1e-6,"coming in as it is further off");last=v;}
        // drawn round the knife's own outline, with fewer points: the same shape to the eye
        double fine=Math.abs(HackKnife.area2(HackKnife.OUTLINE)),coarse=Math.abs(HackKnife.area2(RainFx.OUTLINE));
        assertTrue(RainFx.OUTLINE.length<=16&&Math.abs(coarse/fine-1)<.02,"fewer points, the same shape: "+RainFx.OUTLINE.length+" points, "+coarse/fine+" of its area");
        for(int i:new int[]{0,1,2,3})assertArrayEquals(HackKnife.OUTLINE[i],RainFx.OUTLINE[i],1e-12,"the rod and the spine's corners kept");
        assertArrayEquals(HackKnife.OUTLINE[HackKnife.OUTLINE.length-1],RainFx.OUTLINE[RainFx.OUTLINE.length-1],1e-12);
    }
    /** (2.132.1) The screen inside the rain is for whoever stands in it (their body, not their camera), and for no one else; it comes and goes smoothly. */
    @Test void theScreenInsideTheRainIsForWhoeverStandsInIt(){
        Vec3d feet=new Vec3d(10,64,5),user=new Vec3d(10-(RainRules.KEEP+.3),64,5);
        float mid=RainRules.FIRST+10;
        assertEquals(1,RainFx.inside(user,feet,mid,NEVER),1e-6,"the user stands in the rain where they stab from");
        assertEquals(1,RainFx.inside(feet,feet,mid,NEVER),1e-6,"so does the one stabbed");
        // (2.132.3: the air the knives come out of; it is their body that counts, and one just inside its edge is under it all)
        assertEquals(1,RainFx.inside(new Vec3d(10,64,5+RainFx.INSIDE-1.6),feet,mid,NEVER),1e-6,"one just inside its edge is under it all");
        assertTrue(RainFx.INSIDE-1.5>BladeRain.FAR-.01,"the air the knives come out of is all inside it");
        assertEquals(0,RainFx.inside(new Vec3d(10,64,5+RainFx.INSIDE+.6),feet,mid,NEVER),1e-6,"one standing outside it gets none");
        assertEquals(0,RainFx.inside(user.add(0,RainFx.INSIDE_UP+1.1,0),feet,mid,NEVER),1e-6,"nor one high over it");
        assertEquals(0,RainFx.inside(user,feet,RainRules.FIRST-BladeRain.BEFORE-.01f,NEVER),1e-6,"none before the first knife comes");
        assertEquals(0,RainFx.inside(user,feet,RainRules.LAST+10.01f,NEVER),1e-6,"none a little after the last lands");
        assertEquals(0,RainFx.inside(user,feet,RainRules.FIRST+15,RainRules.FIRST+4),1e-6,"stopped short, gone after it");
        float prev=0,most=0;
        for(float a=0;a<RainRules.LENGTH;a+=1/3f){float v=RainFx.inside(user,feet,a,NEVER);most=Math.max(most,Math.abs(v-prev));prev=v;}
        assertTrue(most<=.11,"it comes and goes over frames at 60 fps, not at once: at most "+most+" a frame");
    }
}
