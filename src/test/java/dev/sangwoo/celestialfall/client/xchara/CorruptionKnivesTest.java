package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.xchara.KnivesRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's T with their hack off, the three small knives thrown, as drawn (2.131.0, the user's video and words: "다른
 * 손으로 작은 나이프 3개를 던지는 기술이야"): the body throws with the hand that holds no knife (raised back by the
 * shoulder, then thrown forward, the body going down into it) and comes back to its stance; the knives are in that hand
 * until each leaves it, from where the server has it leave; in flight each has its point where the server has it and a
 * line behind it; it stops in whom it struck and is gone; one never thrown is gone with the throw.
 */
class CorruptionKnivesTest {
    static final Vec3d STOOD=new Vec3d(3,64,-2);static final float YAW=30,NEVER=Float.MAX_VALUE;
    static BladeShot.Blade told(int k,Vec3d want,double reach){
        var b=new BladeShot.Blade();double[] s=KnivesRules.spot(new double[]{STOOD.x,STOOD.y,STOOD.z},YAW,k);b.from=new Vec3d(s[0],s[1],s[2]);
        double[] w=ShotRules.way(s,new double[]{STOOD.x,STOOD.y+1.62,STOOD.z},new double[]{want.x,want.y,want.z});
        b.dir=new Vec3d(w[0],w[1],w[2]);b.reach=reach;return b;
    }
    static Vec3d ahead(double far){return STOOD.add(Vec3d.fromPolar(0,YAW).multiply(far)).add(0,1.1,0);}
    static float fire(int k){return KnivesRules.FIRE[k]/20f;}

    @Test void theBodyThrowsWithTheOtherHandAndComesBackToItsStance(){
        var clip=CorruptionKnives.CLIP;
        assertSame(clip,KnifeCombo.clipOf(KNIVES));assertEquals(LENGTH[KNIVES]/20f,clip.length(),1e-4);
        assertSame(BladeShot.CLIP,KnifeCombo.clipOf(SHOT),"the knives shot are still theirs");
        assertEquals(CorruptionKnives.LIFE,KnifeCombo.life(KNIVES),1e-6);
        assertTrue(CorruptionKnives.LIFE*20>=KnivesRules.over(2)+8,"drawn until the last knife's flight and what it leaves are over");
        Vec3d f=Vec3d.fromPolar(0,YAW),right=new Vec3d(-f.z,0,f.x);
        Vec3d rest=CorruptionKnives.hand(STOOD,YAW,0),end=CorruptionKnives.hand(STOOD,YAW,clip.length());
        assertEquals(0,rest.distanceTo(end),1e-4,"from the stance and back to it");
        var restBlade=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(KnifeCombo.AT_REST));var startBlade=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(0)));
        assertEquals(0,restBlade[0].distanceTo(startBlade[0]),1e-4);assertEquals(0,restBlade[1].distanceTo(startBlade[1]),1e-4);
        // raised: the hand up by the shoulder's height or over it, behind the body's middle, on the left
        Vec3d up=CorruptionKnives.hand(STOOD,YAW,KnivesRules.RAISED/20f).subtract(STOOD);
        assertTrue(up.y>1.3,"raised to the shoulder: "+up.y);assertTrue(up.dotProduct(f)<-.2,"drawn back");assertTrue(up.dotProduct(right)<-.3,"the left hand");
        // thrown: ahead of the body, lower, as the first leaves; the body gone forward and down into it
        Vec3d out=CorruptionKnives.hand(STOOD,YAW,fire(0)).subtract(STOOD);
        assertTrue(out.dotProduct(f)>.6,"thrown out ahead: "+out.dotProduct(f));assertTrue(out.y<up.y-.2);
        var low=clip.frame(fire(2));
        assertTrue(low.up()<-.2,"the body goes down into the throw");assertTrue(low.pitch()>15,"leaning forward");assertTrue(low.fw()>.1,"and forward");
        assertTrue(clip.frame(KnivesRules.RAISED/20f).yaw()>15&&low.yaw()<-15,"wound one way and thrown round the other");
        // the knife in the other hand never goes into the ground, and its hand stays the knife's
        for(float t=0;t<=clip.length();t+=1/120f){
            var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(t)));
            assertTrue(b[0].add(b[1].multiply(ShotRules.TIP)).y>.2,"the knife's point clear of the ground at "+t);assertTrue(b[0].y>.3);
        }
    }
    @Test void nothingOfTheBodyJumps(){
        var clip=CorruptionKnives.CLIP;Vec3d lastHand=null,lastFist=null;float lastYaw=0,lastPitch=0;
        for(float t=0;t<=clip.length();t+=1/60f){
            var fr=clip.frame(t);Vec3d hand=CorruptionKnives.hand(Vec3d.ZERO,0,t),fist=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(fr))[0];
            if(lastHand!=null){
                assertTrue(hand.distanceTo(lastHand)<.75,"the throwing hand at "+t+": "+hand.distanceTo(lastHand));
                assertTrue(fist.distanceTo(lastFist)<.3,"the knife's hand at "+t+": "+fist.distanceTo(lastFist));
                assertTrue(Math.abs(fr.yaw()-lastYaw)<14&&Math.abs(fr.pitch()-lastPitch)<10,"the body at "+t);
            }
            lastHand=hand;lastFist=fist;lastYaw=fr.yaw();lastPitch=fr.pitch();
        }
    }
    @Test void eachLeavesTheHandFromWhereTheServerHasItLeave(){
        for(int k=0;k<3;k++){
            Vec3d hand=CorruptionKnives.hand(STOOD,YAW,fire(k));double[] s=KnivesRules.spot(new double[]{STOOD.x,STOOD.y,STOOD.z},YAW,k);
            assertTrue(hand.distanceTo(new Vec3d(s[0],s[1],s[2]))<.12,"knife "+k+" leaves the hand where the hand is: "+hand.distanceTo(new Vec3d(s[0],s[1],s[2])));
        }
    }
    @Test void theyAreInTheHandUntilEachIsThrown(){
        float appear=KnivesRules.APPEAR/20f;
        for(int k=0;k<3;k++){
            assertNull(CorruptionKnives.pose(STOOD,YAW,k,appear-.001f,NEVER,null),"nothing of it before they come");
            var p=CorruptionKnives.pose(STOOD,YAW,k,fire(k)-.01f,NEVER,null);
            assertNotNull(p);assertFalse(p.flying());assertEquals(1,p.shown(),1e-4);
            assertEquals(0,p.tail().distanceTo(CorruptionKnives.hand(STOOD,YAW,fire(k)-.01f)),1e-6,"in the hand");
            assertEquals(1,p.dir().length(),1e-6);assertEquals(KnivesRules.TIP,p.tip().distanceTo(p.tail()),1e-6);
            // thrown and not told of: nothing of it
            assertNull(CorruptionKnives.pose(STOOD,YAW,k,fire(k)+.01f,NEVER,null));
        }
        // the three fanned, not one on another
        float t=KnivesRules.RAISED/20f;
        Vec3d a=CorruptionKnives.held(STOOD,YAW,0,t),b=CorruptionKnives.held(STOOD,YAW,1,t),c=CorruptionKnives.held(STOOD,YAW,2,t);
        assertEquals(CorruptionKnives.FAN,Math.toDegrees(Math.acos(a.dotProduct(b))),.5);assertEquals(CorruptionKnives.FAN,Math.toDegrees(Math.acos(c.dotProduct(b))),.5);
        assertEquals(2*CorruptionKnives.FAN,Math.toDegrees(Math.acos(a.dotProduct(c))),.5);
        // while the first is gone the others are still held
        var told=told(0,ahead(9),KnivesRules.RANGE);float mid=(fire(0)+fire(1))/2;
        assertTrue(CorruptionKnives.pose(STOOD,YAW,0,mid,NEVER,told).flying());assertFalse(CorruptionKnives.pose(STOOD,YAW,1,mid,NEVER,null).flying());
    }
    @Test void inFlightItsPointIsWhereTheServerHasItWithALineBehind(){
        for(int k=0;k<3;k++){
            var b=told(k,ahead(12),KnivesRules.RANGE);
            for(float a=0;a<4.5f;a+=.25f){
                var p=CorruptionKnives.pose(STOOD,YAW,k,fire(k)+a/20f,NEVER,b);assertNotNull(p,"knife "+k+" "+a+" ticks out");
                assertTrue(p.flying());
                assertEquals(0,p.tip().distanceTo(b.from.add(b.dir.multiply(KnivesRules.head(a)))),1e-3,"its point where the server has it");
                assertEquals(0,p.dir().distanceTo(b.dir),1e-9);
                double line=p.tail().distanceTo(p.lineFrom());
                assertTrue(line<=CorruptionKnives.LINE+1e-6,"the line no longer than it may be");
                assertTrue(p.lineFrom().subtract(b.from).dotProduct(b.dir)>=-1e-6,"and never behind where it left");
                if(a*KnivesRules.SPEED>1)assertTrue(line>1,"a line drawn out behind it");
            }
        }
    }
    @Test void itStopsInWhomItStruckAndIsGoneAndItsLineAfterIt(){
        int k=1;var b=told(k,ahead(6),KnivesRules.RANGE);b.struck=7;b.far=5.2;b.took=true;
        float landed=CorruptionKnives.landed(b,k);
        assertEquals((KnivesRules.FIRE[k]+KnivesRules.reached(5.2))/20,landed,1e-5);
        var before=CorruptionKnives.pose(STOOD,YAW,k,landed-.004f,NEVER,b);
        assertTrue(before.tip().subtract(b.from).dotProduct(b.dir)<5.2+1e-6);
        var in=CorruptionKnives.pose(STOOD,YAW,k,landed+.01f,NEVER,b);
        assertEquals(5.2,in.tip().subtract(b.from).dotProduct(b.dir),1e-3,"stopped in them, no further");assertTrue(in.shown()>.9f);
        var after=CorruptionKnives.pose(STOOD,YAW,k,landed+CorruptionKnives.STUCK+.005f,NEVER,b);
        assertTrue(after==null||after.shown()<=0,"the knife gone");assertNotNull(after,"its line a moment longer");assertTrue(after.line()>0);
        assertNull(CorruptionKnives.pose(STOOD,YAW,k,landed+CorruptionKnives.LINE_AFTER+.01f,NEVER,b),"then nothing");
        assertTrue(Float.isNaN(CorruptionKnives.ended(b,k)));
        // at a block: gone there
        var wall=told(2,ahead(6),3.5);
        float ended=CorruptionKnives.ended(wall,2);assertEquals((KnivesRules.FIRE[2]+KnivesRules.reached(3.5))/20,ended,1e-5);assertTrue(Float.isNaN(CorruptionKnives.landed(wall,2)));
        var there=CorruptionKnives.pose(STOOD,YAW,2,ended+.01f,NEVER,wall);
        assertTrue(there==null||there.shown()<=0);
        assertNull(CorruptionKnives.pose(STOOD,YAW,2,ended+CorruptionKnives.LINE_AFTER+.01f,NEVER,wall));
    }
    @Test void oneNeverThrownIsGoneWithTheThrow(){
        float stop=.22f;
        for(int k=0;k<3;k++){
            assertNotNull(CorruptionKnives.pose(STOOD,YAW,k,stop+.02f,stop,null),"breaking up in the hand a moment");
            assertNull(CorruptionKnives.pose(STOOD,YAW,k,stop+CorruptionKnives.VANISH+.001f,stop,null));
            assertNull(CorruptionKnives.pose(STOOD,YAW,k,fire(k)+.2f,stop,told(k,ahead(8),KnivesRules.RANGE)),"and never flies");
        }
        // stopped after the first went: the first flies on, the others are gone
        float later=fire(0)+.02f;var b=told(0,ahead(12),KnivesRules.RANGE);
        assertTrue(CorruptionKnives.pose(STOOD,YAW,0,later+.03f,later,b).flying());
        assertNull(CorruptionKnives.pose(STOOD,YAW,2,later+CorruptionKnives.VANISH+.01f,later,null));
    }
    @Test void theBurstsGrowWithEachKnife(){
        assertEquals(HACK_SLASH,CorruptionKnives.burstOf(0));assertEquals(HACK_SLASH,CorruptionKnives.burstOf(1));assertEquals(HACK_LUNGE,CorruptionKnives.burstOf(2),"the last the biggest (the video's)");
        assertTrue(CorruptionKnives.burstSize(0)<CorruptionKnives.burstSize(1));
        assertTrue(CorruptionKnives.SHAKE[2]>CorruptionKnives.SHAKE[0]);
    }
}
