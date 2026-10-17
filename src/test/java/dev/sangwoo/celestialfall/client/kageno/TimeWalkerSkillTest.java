package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** R rebuilt from the user's recordings: a standing cast with seven cuts, and a walking cast over the walk cycle. */
class TimeWalkerSkillTest {
    static Quaternionf q(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    static double angle(float[] a,float[] b){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(q(a).dot(q(b))))));}
    static double between(R6.Dir a,R6.Dir b){return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,a.norm().dot(b.norm())))));}
    /** Where the blade points in the cast frame (the torso's turn included). */
    static R6.Dir sword(float sec){
        var f=TimeWalkerSkill.CLIP.frame(sec);var d=R6.bladeOf(f);double y=Math.toRadians(f.yaw());
        // A turn to the left (positive yaw) carries the right side toward the front.
        return new R6.Dir(d.rt()*Math.cos(y)-d.fw()*Math.sin(y),d.up(),d.fw()*Math.cos(y)+d.rt()*Math.sin(y));
    }
    @Test void everyKeyIsReachedOnItsTime(){
        var clip=TimeWalkerSkill.CLIP;float[] t=clip.times();
        for(int k=0;k<t.length;k++){
            var f=clip.frame(t[k]);var key=clip.key(k);
            for(int j=0;j<6;j++)assertTrue(angle(f.joints().joint[j],key.joints().joint[j])<.1,"joint "+j+" at key "+k);
            assertEquals(key.up(),f.up(),1e-3);assertEquals(key.yaw(),f.yaw(),.1);assertEquals(key.pitch(),f.pitch(),.1);
        }
    }
    @Test void movesWithoutJumps(){
        var clip=TimeWalkerSkill.CLIP;float h=1/960f;var prev=clip.frame(0);
        for(float t=h;t<=clip.length();t+=h){
            var next=clip.frame(t);
            for(int j=0;j<6;j++)assertTrue(angle(prev.joints().joint[j],next.joints().joint[j])<12,"joint "+j+" jumps at "+t);
            assertTrue(Math.abs(next.up()-prev.up())<.02&&Math.abs(next.yaw()-prev.yaw())<3,"root jumps at "+t);
            prev=next;
        }
    }
    /** Four fast cuts and one big sweep while the world is gray, each to the other side: 5 hits with the final cut's release. */
    @Test void fourCutsThenOneBigSweep(){
        assertEquals(KagenoRules.R_CUTS+1,TimeWalkerSkill.CUTS.length);assertEquals(4,KagenoRules.R_CUTS);
        assertEquals(KagenoRules.R_GRAY/20f,TimeWalkerSkill.GRAY,1e-6);assertEquals(KagenoRules.R_STORED/20f,TimeWalkerSkill.STORED,1e-6);assertEquals(KagenoRules.R_RELEASE/20f,TimeWalkerSkill.RELEASE,1e-6);
        float last=TimeWalkerSkill.GRAY;int side=0;double[] sweep=new double[TimeWalkerSkill.CUTS.length];
        for(int i=0;i<TimeWalkerSkill.CUTS.length;i++){
            float cock=TimeWalkerSkill.COCKS[i],cut=TimeWalkerSkill.CUTS[i];boolean big=i==TimeWalkerSkill.CUTS.length-1;
            assertTrue(cock>last&&cut>cock&&cut<TimeWalkerSkill.COLOR,"cut "+cut+" inside the gray");last=cut;
            // From the wind-up the sword sweeps well over 90 degrees in 0.08 s or less (the big one in 0.12 s): fast slashes.
            assertTrue(cut-cock<=(big?.12f:.08f)+1e-6,"strike "+i+" takes "+(cut-cock));
            for(float t=cock;t<cut;t+=1/240f)sweep[i]+=between(sword(t),sword(Math.min(cut,t+1/240f)));
            assertTrue(sweep[i]>110,"strike "+i+" sweeps "+sweep[i]);
            // Halfway it passes in front of the body.
            assertTrue(sword(cock+.5f*(cut-cock)).fw()>.5,"strike "+i+" crosses the front");
            int s=sword(cut).rt()>0?1:-1;assertNotEquals(side,s,"strike "+i+" ends on the same side as the one before");side=s;
        }
        // The big one is wound up longest, sweeps furthest and leaves the longest, widest cut.
        int n=sweep.length-1;double[] bigCut=TimeWalkerSkill.SPEC[n];
        for(int i=0;i<n;i++){
            assertTrue(sweep[n]>sweep[i],"big sweep "+sweep[n]+" vs "+sweep[i]);
            assertTrue(TimeWalkerSkill.CUTS[n]-TimeWalkerSkill.COCKS[n]>TimeWalkerSkill.CUTS[i]-TimeWalkerSkill.COCKS[i]);
            assertTrue(bigCut[2]>TimeWalkerSkill.SPEC[i][2]&&bigCut[6]>TimeWalkerSkill.SPEC[i][6]);
        }
        assertEquals(TimeWalkerSkill.CUTS.length,TimeWalkerSkill.SPEC.length);
    }
    /** The cuts land as purple slashes with the server's hits: the four stored ones one after another from 1.8 s, the big one with the final hit. */
    @Test void slashesLandWithTheHits(){
        int n=TimeWalkerSkill.CUTS.length-1;
        for(int i=0;i<n;i++){
            assertTrue(TimeWalkerSkill.slashAt(i)>=TimeWalkerSkill.STORED&&TimeWalkerSkill.slashAt(i)<TimeWalkerSkill.RELEASE-.03f,"stored slash "+i);
            if(i>0)assertTrue(TimeWalkerSkill.slashAt(i)>TimeWalkerSkill.slashAt(i-1),"one after another");
            assertTrue(TimeWalkerSkill.slashLife(n)>TimeWalkerSkill.slashLife(i));
        }
        assertEquals(TimeWalkerSkill.RELEASE,TimeWalkerSkill.slashAt(n),.02);
    }
    /** The cuts are long lines lying across the front, crossing at many angles (never straight at the caster). */
    @Test void cutsLieAcrossTheFrontAndCross(){
        double low=0,high=0;
        for(double[] s:TimeWalkerSkill.SPEC){
            assertTrue(Math.abs(s[0])<=25&&Math.abs(s[1])<=35&&s[2]>=10,"cut "+java.util.Arrays.toString(s));
            low=Math.min(low,s[1]);high=Math.max(high,s[1]);
        }
        assertTrue(low<=-20&&high>=20,"both diagonals");
    }
    /** Walking, the body stays over the walking legs: no dip, no crouch, no step of the root; standing it is the authored clip. */
    @Test void walkingCastKeepsTheBodyOverTheLegs(){
        var clip=TimeWalkerSkill.CLIP;double deepest=0;
        for(float t=0;t<=clip.length();t+=1/120f){
            var f=clip.frame(t);var w=TimeWalkerSkill.walking(f,1);deepest=Math.min(deepest,f.up());
            assertTrue(w.up()>=-.06&&w.fw()==0&&w.rt()==0,"walking root at "+t);
            assertTrue(Math.abs(w.pitch())<=Math.abs(f.pitch())*.4+1e-4&&Math.abs(w.yaw())<=Math.abs(f.yaw())*.55+1e-4,"walking lean at "+t);
            assertSame(f,TimeWalkerSkill.walking(f,0));
        }
        assertTrue(deepest<-.35,"standing, the body dips low");
    }
    /** The walk cycle's legs come in with the walk weight and are turned back against the body's turn. */
    @Test void walkingLegsBlendInAndFaceTheWalk(){
        var clip=new MotionPose();clip.rot(MotionPose.RIGHT_LEG,0,0,10).rot(MotionPose.LEFT_LEG,0,0,-10);
        var walk=new MotionPose();walk.rot(MotionPose.RIGHT_LEG,30,0,0).rot(MotionPose.LEFT_LEG,-30,0,0);walk.knee[0]=12;walk.bob=.5f;
        var none=clip.copy();KagenoClips.walkLegs(none,walk,0,40);
        for(int i=4;i<6;i++)assertTrue(angle(none.joint[i],clip.joint[i])<1e-3);
        var full=clip.copy();KagenoClips.walkLegs(full,walk,1,0);
        for(int i=4;i<6;i++)assertTrue(angle(full.joint[i],walk.joint[i])<.1);
        assertEquals(12,full.knee[0],1e-4);assertEquals(.5f,full.bob,1e-4);
        // With the body turned 30 degrees to the left the legs turn 30 degrees back about the vertical (model y).
        var turned=clip.copy();KagenoClips.walkLegs(turned,walk,1,30);
        var expect=new Quaternionf().rotationY((float)Math.toRadians(30)).mul(q(walk.joint[4]));
        assertTrue(Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(q(turned.joint[4]).dot(expect)))))<.1);
        // Halfway in, halfway between.
        var half=clip.copy();KagenoClips.walkLegs(half,walk,.5f,0);
        assertEquals(angle(clip.joint[4],walk.joint[4])/2,angle(half.joint[4],clip.joint[4]),.05);
    }

    /** The impact frame lands only with the final cut, comes in and goes out over frames, and turns from white-on-black to dark-on-white. */
    @Test void theImpactFrameHitsOnlyTheFinisherAndRampsOverFrames(){
        float r=TimeWalkerSkill.RELEASE;
        for(float t=0;t<4;t+=1/240f)if(t<r-.011f||t>r+.126f)assertEquals(0,TimeWalkerSkill.impact(t),1e-6,"at "+t);
        assertTrue(TimeWalkerSkill.impact(r+.05f)>.99f&&TimeWalkerSkill.impact(r+.085f)>.99f);
        for(float t=r-.05f;t<r+.15f;t+=1/240f){
            assertTrue(Math.abs(TimeWalkerSkill.impact(t+1/60f)-TimeWalkerSkill.impact(t))<.8f,"impact jumps at "+t);
            assertTrue(Math.abs(TimeWalkerSkill.impactMode(t+1/60f)-TimeWalkerSkill.impactMode(t))<.8f,"mode jumps at "+t);
        }
        assertTrue(TimeWalkerSkill.impactMode(r+.03f)<.01f&&TimeWalkerSkill.impactMode(r+.08f)>.99f);
    }
    /** Every chunk of floor thrown at the release has landed and lies still before it starts to shrink away. */
    @Test void floorChunksLandBeforeTheyShrinkAway(){
        net.minecraft.util.math.Vec3d right=new net.minecraft.util.math.Vec3d(1,0,0),fw=new net.minecraft.util.math.Vec3d(0,0,1),floor=new net.minecraft.util.math.Vec3d(0,64,0);
        for(int k=0;k<TimeWalkerSkill.CHUNKS;k++){
            assertTrue(TimeWalkerSkill.chunkBorn(k)<=.015f);
            double size=TimeWalkerSkill.chunkSize(k,0),ground=floor.y+size*.5;
            var start=TimeWalkerSkill.chunkStart(right,fw,floor,k);var v=TimeWalkerSkill.chunkVelocity(right,fw,k);
            assertTrue(v.y>=5,"thrown up");
            assertTrue(Math.abs(start.x-floor.x)<=7.5+1e-9,"along the band");
            var still=dev.sangwoo.celestialfall.client.vfx.FxSolids.flight(start,v,ground,1.55);
            assertEquals(ground,still.y,1e-9,"chunk "+k+" still in the air when it starts to shrink");
            assertTrue(still.distanceTo(dev.sangwoo.celestialfall.client.vfx.FxSolids.flight(start,v,ground,3))<.05,"chunk "+k+" still sliding far");
            assertEquals(size,TimeWalkerSkill.chunkSize(k,1.5f),1e-9);assertEquals(0,TimeWalkerSkill.chunkSize(k,1.95f),1e-9);
        }
    }
}
