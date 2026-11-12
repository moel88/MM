package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.CloneRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's R with the hack on, the double, as drawn (2.125.0, the user's video and words: "옆에 적이랑 똑같은 분신인데
 * 얼굴에 X자가 쳐져있는 분신이 적을 날리는 모션을 취하고 적이 벽으로 날아가 박히는"): the double comes blinking, is there
 * through its throw and blinks out; its body stands, winds up against the throw and throws at the server's tick, nothing
 * of it stepping from one frame to the next; the wall is a fan of spikes, the tallest in the middle, up before the one
 * thrown reaches it and down again as it lets them go; the one seized hangs with their limbs loose.
 */
class CloneClientTest {
    static final float NONE=Float.NaN;

    @Test void theDoubleBlinksInIsThereThroughItsThrowAndBlinksOut(){
        assertFalse(CloneClient.cloneShown(CLONE-.01f,NONE),"not before its tick");
        assertTrue(CloneClient.cloneShown(CLONE,NONE));
        boolean off=false;for(float t=CLONE;t<CLONE+2.4f;t+=.05f)off|=!CloneClient.cloneShown(t,NONE);
        assertTrue(off,"blinking as it comes");
        for(float t=CLONE+2.4f;t<CloneClient.GOING;t+=.05f)assertTrue(CloneClient.cloneShown(t,NONE),"there at "+t);
        assertTrue(CloneClient.GOING>THROW+3,"it is there as it throws, and a moment after");
        // going: on for less and less of each blink
        int early=0,late=0;float mid=(CloneClient.GOING+CLONE_GONE)/2;
        for(float t=CloneClient.GOING;t<CLONE_GONE;t+=.02f){if(CloneClient.cloneShown(t,NONE)){if(t<mid)early++;else late++;}}
        assertTrue(early>late*2&&late>0,"on "+early+" then "+late);
        assertFalse(CloneClient.cloneShown(CLONE_GONE,NONE));
        // called off, it is gone within a few ticks
        assertTrue(CloneClient.cloneShown(10,10));assertFalse(CloneClient.cloneShown(10+CloneClient.CUT_GONE,10));
        assertFalse(CloneClient.cloneShown(20,10));
    }
    @Test void itsBodyStandsWindsUpAgainstTheThrowAndThrowsOnTheServersTick(){
        var clip=CloneClient.CLIP;
        assertEquals(CLONE_GONE/20f,clip.length(),1e-4);
        R6.Frame stand=clip.frame(CLONE/20f),wound=clip.frame(.74f),thrown=clip.frame(THROW/20f+.05f);
        assertEquals(0,stand.yaw(),1e-3);assertEquals(0,stand.fw(),1e-3,"standing as it comes");
        assertTrue(wound.yaw()<-25,"wound up turned to the right: "+wound.yaw());assertTrue(wound.fw()<-.05,"its weight back");
        assertTrue(thrown.yaw()>25,"thrown round to the left: "+thrown.yaw());assertTrue(thrown.fw()>.2,"over its front foot");
        assertTrue(thrown.pitch()>8,"leaning into it");
        // its hand: drawn back behind it wound up, out ahead and up as it throws
        double[] back=CloneClient.hand(.74f),out=CloneClient.hand(THROW/20f+.05f);
        assertTrue(back[2]<0,"the hand behind it wound up: "+back[2]);assertTrue(out[2]>.5,"out ahead thrown: "+out[2]);
        assertTrue(out[1]>1.2,"at shoulder height or over");
    }
    @Test void nothingOfTheDoubleStepsFromOneFrameToTheNext(){
        var clip=CloneClient.CLIP;R6.Frame before=clip.frame(0);
        for(float s=1/240f;s<=clip.length();s+=1/240f){
            R6.Frame f=clip.frame(s);
            // (a quarter of a 60 fps frame: a limb turns at most some 14 degrees in it even through the throw)
            for(int j=0;j<f.joints().joint.length;j++)for(int k=0;k<3;k++)
                assertTrue(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(f.joints().joint[j][k]-before.joints().joint[j][k]))<14,"joint "+j+"/"+k+" at "+s);
            assertTrue(Math.abs(f.yaw()-before.yaw())<8&&Math.abs(f.fw()-before.fw())<.05&&Math.abs(f.up()-before.up())<.05,"its body at "+s);
            before=f;
        }
    }
    @Test void theOneSeizedHangsLooseAndIsStillerInTheWall(){
        var d=(CloneClient.Throw)null;
        // (the limbs only need the seed: a throw's other fields are not read)
        MotionPose hung=CloneClient.struckAt(seeded(7),THROW+10),hung2=CloneClient.struckAt(seeded(7),THROW+13),in=CloneClient.struckAt(seeded(7),IMPACT+8),in2=CloneClient.struckAt(seeded(7),IMPACT+11);
        double loose=0,still=0;
        for(int j=MotionPose.RIGHT_ARM;j<=MotionPose.LEFT_LEG;j++){loose+=Math.abs(hung.joint[j][0]-hung2.joint[j][0]);still+=Math.abs(in.joint[j][0]-in2.joint[j][0]);}
        assertTrue(loose>still*2,"flailing as they hang ("+loose+"), less in the wall ("+still+")");
        assertTrue(hung.elbow[0]<-10&&hung.knee[0]>10,"their elbows and knees bent");
        // no step between frames as they are flung and go in
        MotionPose before=CloneClient.struckAt(seeded(7),THROW);
        for(float t=THROW+.25f;t<FREE;t+=.25f){
            MotionPose p=CloneClient.struckAt(seeded(7),t);
            for(int j=0;j<p.joint.length;j++)assertTrue(turn(p.joint[j],before.joint[j])<16,"joint "+j+" turns "+turn(p.joint[j],before.joint[j])+" at "+t);
            before=p;
        }
    }
    /** The angle between two turns of a limb given as the rig's angles (degrees). */
    static double turn(float[] a,float[] b){
        float k=(float)Math.PI/180;
        var qa=new org.joml.Quaternionf().rotationZYX(a[2]*k,a[1]*k,a[0]*k);var qb=new org.joml.Quaternionf().rotationZYX(b[2]*k,b[1]*k,b[0]*k);
        return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(qa.dot(qb)))));
    }
    static CloneClient.Throw seeded(int seed){
        var cast=new dev.sangwoo.celestialfall.client.StandClient.Cast(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),null,SKILL,0,seed,seed,0,net.minecraft.util.math.Vec3d.ZERO,0,java.util.List.of());
        return new CloneClient.Throw(cast.caster(),1,java.util.UUID.randomUUID(),0,net.minecraft.util.math.Vec3d.ZERO,new net.minecraft.util.math.Vec3d(0,0,5),cast,1);
    }
    @Test void theDoubleStandsBesideItsUserAndTheWallBehindTheOneThrown(){
        var d=seeded(3);
        var at=d.cloneAt();
        // facing +z (yaw 0), their left is +x
        assertEquals(CloneClient.BESIDE,at.x,1e-6,"to its user's left");assertEquals(CloneClient.AHEAD,at.z,1e-6,"a little ahead");
        d.wall=new net.minecraft.util.math.Vec3d(0,1,12.4);
        assertEquals(7.4,d.far(),1e-6);assertEquals(1,d.rise(),1e-6);
        assertEquals(12.4+CloneClient.WALL_BEHIND,d.wallBase().z,1e-6,"the wall's middle behind them");
    }
    @Test void itsUserPressesThePanelAndWatches(){
        float[] at=CloneClient.pressAt(.1f,NONE);
        assertEquals(1,at[0],.01,"the arm up");assertEquals(1,at[1],.05,"the hand on it a tenth of a second in");assertTrue(at[2]>.9,"lit");assertEquals(1,at[3],"violet, not the restore's lime");
        assertEquals(0,CloneClient.pressAt(.6f,NONE)[0],1e-3,"the arm down again: they watch the double");
        assertTrue(CloneClient.pressAt(THROW/20f+.02f,NONE)[2]>.8,"lit again as the double throws");
        assertEquals(0,CloneClient.pressAt(.25f,.1f)[0],1e-3,"called off, the arm is down at once");assertEquals(0,CloneClient.pressAt(.2f,.1f)[2],1e-6);
    }
    @Test void theWallIsAFanOfSpikesTallestInTheMiddle(){
        int seed=11;
        double[] mid=CloneFling.spike(CloneFling.FAN/2,seed),left=CloneFling.spike(0,seed),right=CloneFling.spike(CloneFling.FAN-1,seed);
        assertTrue(Math.abs(mid[2])<6,"the middle one upright");assertTrue(mid[4]>3.8,"and the tallest: "+mid[4]);
        assertTrue(left[2]<-55&&right[2]>55,"splayed out to both sides");
        assertTrue(left[0]<-.9&&right[0]>.9);
        assertTrue(left[4]<mid[4]-1.5&&right[4]<mid[4]-1.5&&left[5]<mid[5]&&right[5]<mid[5],"shorter and thinner outward");
        for(int k=0;k<CloneFling.FAN;k++){assertTrue(CloneFling.spike(k,seed)[3]>0,"the fan leans away from its user");assertTrue(Math.abs(CloneFling.spike(k,seed)[1])<.5,"behind the one held");}
        // the shorter ones to either side before it, three a side, leaning back toward its user
        int l=0,r=0;
        for(int k=CloneFling.FAN;k<CloneFling.SPIKES;k++){
            double[] s=CloneFling.spike(k,seed);
            assertTrue(s[1]<-.4,"before the fan");assertTrue(s[3]<0);assertTrue(s[4]<2.4);assertTrue(Math.abs(s[0])>.7,"clear of the one thrown between them");
            if(s[0]<0)l++;else r++;
        }
        assertEquals(3,l);assertEquals(3,r);
    }
    @Test void theWallIsUpBeforeTheyReachItAndDownAgainAsItLetsThemGo(){
        int seed=5;
        for(int k=0;k<CloneFling.SPIKES;k++){
            double[] sp=CloneFling.spike(k,seed);
            assertEquals(0,CloneFling.grown(sp,CloneFling.RISE,FREE),1e-9);
            assertTrue(CloneFling.grown(sp,IMPACT-.01f,FREE)>.6,"spike "+k+" most of the way up as they reach it");
            assertEquals(1,CloneFling.grown(sp,IMPACT+12,FREE),.01,"standing");
            assertEquals(0,CloneFling.grown(sp,(float)(FREE+CloneFling.SINK_TICKS+sp[6]+1),FREE),1e-6,"down again");
            // called off before the wall, it goes down from then
            assertEquals(0,CloneFling.grown(sp,IMPACT+5+CloneFling.SINK_TICKS+(float)sp[6],IMPACT+4),1e-6);
            double before=0;
            for(float t=CloneFling.RISE;t<FREE+20;t+=.25f){double g=CloneFling.grown(sp,t,FREE);assertTrue(Math.abs(g-before)<.3,"spike "+k+" steps at "+t);assertTrue(g<1.2);before=g;}
        }
        assertTrue(CloneFling.RISE<FLING&&CloneFling.RISE>THROW,"it comes up as they are flung, not before");
    }
    @Test void barsAreTornAcrossTheDoubleNowAndThen(){
        int shown=0,all=0;
        for(float t=CLONE;t<CLONE_GONE;t+=.5f)for(int j=0;j<CloneFling.BARS;j++){
            all++;double[] b=CloneFling.tear(j,9,t,1.8,.6,1);if(b==null)continue;shown++;
            assertTrue(b[0]>.1&&b[0]<.95,"on the body");assertTrue(Math.abs(b[1])<.3);assertTrue(b[2]>.15&&b[2]<.6);assertTrue(b[3]<.07,"thin");
        }
        assertTrue(shown>all*.35&&shown<all*.75,shown+" of "+all);
        // (changing some fourteen times a second: the same within one of them)
        assertArrayEquals(CloneFling.tear(0,9,20.1f,1.8,.6,1),CloneFling.tear(0,9,20.3f,1.8,.6,1));
    }
}
