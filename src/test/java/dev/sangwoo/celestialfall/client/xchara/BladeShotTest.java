package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.xchara.ShotRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's Y with their hack off, the four knives shot, as drawn (2.130.0, the user's video and words: "q활성화 상태의
 * 검을 4개를 적에게 날리는 스킬이야 … 그냥 나가면 돼"): the body stands as it stood; each knife comes to be where it hangs,
 * hangs pointing ahead, and as it is let go flies its way with its point where the server has it, never jumping;
 * it stops in whom it struck and is gone, is gone at a block, and thins out at the end of its range; one never let go
 * (the skill stopped short) breaks up where it hung.
 */
class BladeShotTest {
    static final Vec3d STOOD=new Vec3d(3,64,-2);static final float YAW=30,NEVER=Float.MAX_VALUE;
    static BladeShot.Blade told(int k,Vec3d want,double reach){
        var b=new BladeShot.Blade();b.from=BladeShot.spot(STOOD,YAW,k);
        double[] w=ShotRules.way(new double[]{b.from.x,b.from.y,b.from.z},new double[]{STOOD.x,STOOD.y+1.62,STOOD.z},new double[]{want.x,want.y,want.z});
        b.dir=new Vec3d(w[0],w[1],w[2]);b.reach=reach;return b;
    }
    static Vec3d ahead(double far){return STOOD.add(Vec3d.fromPolar(0,YAW).multiply(far)).add(0,1.1,0);}

    @Test void theBodyStandsAsItStood(){
        assertSame(BladeShot.CLIP,KnifeCombo.clipOf(SHOT));assertEquals(LENGTH[SHOT]/20f,BladeShot.CLIP.length(),1e-4);
        assertSame(EdgeCutter.CLIP,KnifeCombo.clipOf(CUTTER),"the cuts are still the cuts");assertSame(OverwriteSlam.CLIP,KnifeCombo.clipOf(SLAM));
        var rest=dev.sangwoo.celestialfall.client.kageno.KagenoClips.blade(Vec3d.ZERO,0,dev.sangwoo.celestialfall.client.kageno.R6.poseOf(KnifeCombo.AT_REST));
        for(float t=0;t<=BladeShot.CLIP.length();t+=.05f){
            var f=BladeShot.CLIP.frame(t);var hb=dev.sangwoo.celestialfall.client.kageno.KagenoClips.blade(Vec3d.ZERO,0,dev.sangwoo.celestialfall.client.kageno.R6.poseOf(f));
            assertEquals(0,hb[0].distanceTo(rest[0]),1e-6,"no motion of the body (the video's): the hand");assertEquals(0,hb[1].distanceTo(rest[1]),1e-6,"nor of the knife");
            assertEquals(0,Math.abs(f.fw())+Math.abs(f.up())+Math.abs(f.rt())+Math.abs(f.yaw())+Math.abs(f.pitch()),1e-6);
        }
        assertEquals(BladeShot.LIFE,KnifeCombo.life(SHOT),1e-6);
        assertTrue(BladeShot.LIFE*20>=ShotRules.over(3)+8,"drawn until the last knife's flight and what it leaves are over");
    }
    @Test void eachComesToBeWhereItHangsAndPointsAhead(){
        Vec3d f=Vec3d.fromPolar(0,YAW);
        for(int k=0;k<4;k++){
            assertNull(BladeShot.pose(STOOD,YAW,k,ShotRules.APPEAR[k]-.01f,NEVER,null),"nothing of it before it comes");
            var first=BladeShot.pose(STOOD,YAW,k,ShotRules.APPEAR[k]+.01f,NEVER,null);
            assertNotNull(first);assertTrue(first.shown()<.05f,"it comes in from nothing");
            var p=BladeShot.pose(STOOD,YAW,k,ShotRules.FIRE[k]-.5f,NEVER,null);
            assertEquals(1,p.shown(),1e-4);assertFalse(p.flying());
            assertTrue(p.grip().distanceTo(BladeShot.spot(STOOD,YAW,k))<.06,"where it hangs (it bobs a little)");
            assertTrue(p.dir().dotProduct(f)>.9,"pointing ahead");assertEquals(1,p.dir().length(),1e-6);
            assertEquals(ShotRules.TIP,p.tip().distanceTo(p.grip()),1e-6);
            assertEquals((ShotRules.FIRE[k]-.5f-ShotRules.APPEAR[k])/20f,p.lit(),1e-5,"its light's clock runs from when it came");
        }
    }
    @Test void letGoItFliesItsWayWithoutAJump(){
        for(int k=0;k<4;k++){
            var b=told(k,ahead(14),ShotRules.RANGE);Vec3d last=null,lastTip=null;
            for(float age=ShotRules.FIRE[k]-2;age<ShotRules.FIRE[k]+8;age+=1/3f){
                var p=BladeShot.pose(STOOD,YAW,k,age,NEVER,b);assertNotNull(p,"knife "+k+" at "+age);
                if(last!=null){
                    assertTrue(p.grip().distanceTo(last)<ShotRules.SPEED/3+.08,"no jump of knife "+k+" at "+age+": "+p.grip().distanceTo(last));
                    assertTrue(p.tip().distanceTo(lastTip)<ShotRules.SPEED/3+.5,"its point does not jump either");
                }
                last=p.grip();lastTip=p.tip();
                if(age>=ShotRules.FIRE[k]){
                    assertTrue(p.flying());
                    assertEquals(ShotRules.head(age-ShotRules.FIRE[k]),p.head(),1e-4);
                }
                if(age>=ShotRules.FIRE[k]+BladeShot.TURN){
                    Vec3d point=b.from.add(b.dir.multiply(p.head()));
                    assertTrue(p.tip().distanceTo(point)<.06,"its point where the server has it, knife "+k+": "+p.tip().distanceTo(point));
                    assertTrue(p.dir().dotProduct(b.dir)>.9999,"turned to its way");
                }
            }
        }
    }
    @Test void toldOfLateItHangsOnAMomentAndIsGoneIfTheWordNeverComes(){
        assertNotNull(BladeShot.pose(STOOD,YAW,1,ShotRules.FIRE[1]+2,NEVER,null));
        assertFalse(BladeShot.pose(STOOD,YAW,1,ShotRules.FIRE[1]+2,NEVER,null).flying());
        assertNull(BladeShot.pose(STOOD,YAW,1,ShotRules.FIRE[1]+BladeShot.UNTOLD,NEVER,null));
    }
    @Test void itStopsInWhomItStruckAndIsGone(){
        var b=told(0,ahead(8),ShotRules.RANGE);b.struck=7;b.far=9.3;b.took=true;
        float landed=BladeShot.landed(b,0);
        assertEquals(ShotRules.FIRE[0]+ShotRules.reached(9.3),landed,1e-4);
        var before=BladeShot.pose(STOOD,YAW,0,landed-.2f,NEVER,b);assertTrue(before.head()<9.3);
        var in=BladeShot.pose(STOOD,YAW,0,landed+.3f,NEVER,b);assertNotNull(in);assertEquals(9.3,in.head(),1e-6,"it goes no further");
        assertNull(BladeShot.pose(STOOD,YAW,0,landed+BladeShot.STUCK,NEVER,b));
        assertTrue(Float.isNaN(BladeShot.ended(b,0)));
        // struck at once, nearer than its point: there as it is let go
        var near=told(2,ahead(8),ShotRules.RANGE);near.struck=7;near.far=1.2;near.took=true;
        assertEquals(ShotRules.FIRE[2],BladeShot.landed(near,2),1e-4);
        var at=BladeShot.pose(STOOD,YAW,2,ShotRules.FIRE[2]+.4f,NEVER,near);assertNotNull(at);assertEquals(ShotRules.TIP,at.head(),1e-6);
    }
    @Test void atABlockItIsGoneAndAtTheEndOfItsRangeItThinsOut(){
        var wall=told(1,ahead(14),6.5);
        float ended=BladeShot.ended(wall,1);assertEquals(ShotRules.FIRE[1]+ShotRules.reached(6.5),ended,1e-4);
        assertNotNull(BladeShot.pose(STOOD,YAW,1,ended-.1f,NEVER,wall));
        assertEquals(1,BladeShot.pose(STOOD,YAW,1,ended-.1f,NEVER,wall).shown(),1e-4,"whole until it meets the block");
        assertNull(BladeShot.pose(STOOD,YAW,1,ended+.01f,NEVER,wall));
        var far=told(1,ahead(14),ShotRules.RANGE);float end=BladeShot.ended(far,1);
        float last=1;
        for(float age=end-3;age<end;age+=.25f){
            var p=BladeShot.pose(STOOD,YAW,1,age,NEVER,far);float shown=p==null?0:p.shown();
            assertTrue(shown<=last+1e-5,"thinning");last=shown;
        }
        assertTrue(last<.2);assertNull(BladeShot.pose(STOOD,YAW,1,end+.01f,NEVER,far));
    }
    @Test void oneNeverLetGoBreaksUpWhereItHung(){
        // stopped short 11 ticks in: the first has gone (it flies on), the second and third are there and go out, the fourth too
        float stop=11;
        var b=told(0,ahead(14),ShotRules.RANGE);
        assertTrue(BladeShot.pose(STOOD,YAW,0,stop+2,stop,b).flying(),"the one let go flies on");
        for(int k=1;k<4;k++){
            var p=BladeShot.pose(STOOD,YAW,k,stop+.5f,stop,told(k,ahead(14),ShotRules.RANGE));
            assertNotNull(p);assertFalse(p.flying());assertTrue(p.shown()<1);
            assertNull(BladeShot.pose(STOOD,YAW,k,stop+BladeShot.VANISH,stop,told(k,ahead(14),ShotRules.RANGE)),"gone, never let go");
            assertNull(BladeShot.pose(STOOD,YAW,k,ShotRules.FIRE[k]+1,stop,told(k,ahead(14),ShotRules.RANGE)));
        }
        // stopped before one came to be: nothing of it at all
        assertNull(BladeShot.pose(STOOD,YAW,3,ShotRules.APPEAR[3]+1,5,null)==null?null:BladeShot.pose(STOOD,YAW,3,5+BladeShot.VANISH,5,null));
    }
    @Test void itsSpineIsUpAcrossItsWay(){
        for(int k=0;k<4;k++){
            Vec3d dir=told(k,ahead(10),ShotRules.RANGE).dir,s=BladeShot.spine(dir,BladeShot.ROLL[k]);
            assertEquals(1,s.length(),1e-6);assertEquals(0,s.dotProduct(dir),1e-6);assertTrue(s.y>.7,"up, rolled a little");
        }
        assertEquals(1,BladeShot.spine(new Vec3d(0,1,0),0).length(),1e-6,"straight up: still a spine");
    }
}
