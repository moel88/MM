package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's basic attack as drawn (2.77.0): four hits after the user's video, each blade cutting through the server's
 * reach on the server's blows, no joint jumping or flipping between frames, the combo handing over smoothly, and every
 * slash over before its swing is.
 */
class IsosangSlashTest {
    static Quaternionf q(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    static double turn(R6.Frame a,R6.Frame b,int j){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(q(a.joints().joint[j]).dot(q(b.joints().joint[j]))))));}
    /** The sword's point in the swing's frame (forward, up, right) for hit {@code combo} at {@code sec}, the feet at the origin. */
    static double[] tip(int combo,float sec){
        var hb=IsosangSlash.blade(Vec3d.ZERO,0,combo,sec);
        Vec3d t=hb[0].add(hb[1].multiply(IsosangSlash.TIP));return new double[]{t.z,t.y,-t.x};
    }
    static float blow(int combo,int i){return IsosangRules.blowAt(combo,i)/20f;}

    @Test void noClipJumpsOrFlipsItsJoints(){
        for(int combo=1;combo<=4;combo++){
            Anim a=IsosangSlash.hit(combo);double h=1/960.0;
            for(double t=h;t+2*h<=a.length();t+=h){
                var p0=a.frame((float)(t-h));var p1=a.frame((float)t);var p2=a.frame((float)(t+h));var p3=a.frame((float)(t+2*h));
                for(int j=0;j<6;j++){
                    double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                    assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"hit "+combo+" joint "+j+" jumps "+now+" deg at "+t);
                }
            }
            for(double t=0;t+1/60.0<=a.length();t+=1/60.0)
                for(int j=0;j<6;j++)assertTrue(turn(a.frame((float)t),a.frame((float)(t+1/60.0)),j)<125,"hit "+combo+" joint "+j+" flips at "+t);
        }
    }
    @Test void hitStopsSitOnBlowsAndAreOverBeforeTheNext(){
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);
            for(var s:a.stops()){
                assertEquals(s.sec(),a.warp(s.sec()),1e-6,"hit "+combo+": the impact itself is on time");
                boolean on=false;for(int i=0;i<IsosangRules.blows(combo);i++)on|=Math.abs(blow(combo,i)-s.sec())<1e-4;
                assertTrue(on,"hit "+combo+": hit-stop at "+s.sec()+" is not on a blow");
                for(int i=0;i<IsosangRules.blows(combo);i++)if(blow(combo,i)>s.sec()+1e-4)assertTrue(s.sec()+s.hold()+s.recover()<=blow(combo,i),"hit "+combo+": still catching up at the next blow");
            }
        }
    }
    @Test void everyClipStartsAndEndsInTheStanceAndRunsAsLongAsTheHit(){
        var stance=IsosangSlash.stance();
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);var first=a.frame(0);var last=a.frame(a.length());
            for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"hit "+combo+" starts in the stance");assertTrue(turn(last,stance,j)<.5,"hit "+combo+" ends in the stance");}
            assertEquals(0,MathHelper.wrapDegrees(last.yaw()),1e-3,"hit "+combo+" ends facing the way it began");
            assertEquals(0,last.fw(),1e-6);assertEquals(0,last.up(),1e-6);
            assertEquals(stance.grip(),last.grip(),.01,"hit "+combo+" gives the sword back to the game's hold");
            assertEquals(IsosangRules.LENGTH[combo]/20f,a.length(),.11,"hit "+combo+" runs as long as the server says");
        }
        // the stance is how the game holds the sword (a turn of 12° about the wrist past R6's grip: DISPLAY_TILT)
        assertEquals(IsosangSlash.DISPLAY_TILT,stance.grip(),.5);
    }
    @Test void everyBladeCutsThroughTheServersReachOnItsBlow(){
        for(int combo=1;combo<=4;combo++)for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i);double[] p=tip(combo,at);
            assertTrue(p[1]>.4&&p[1]<2.2,"hit "+combo+" blow "+i+" at a body's height: "+p[1]);
            if(combo==2)continue;
            assertTrue(p[0]>1.4,"hit "+combo+" blow "+i+" ahead: "+p[0]);
            assertTrue(IsosangRules.lane(combo,i,p[0],p[2],0,.3),"hit "+combo+" blow "+i+" inside its way: "+java.util.Arrays.toString(p));
        }
    }
    @Test void eachHitCutsItsOwnWay(){
        // 1: the great diagonal, down from the high right to the low left
        double[] a=tip(1,.258f),b=tip(1,.37f);
        assertTrue(a[1]>b[1]+1.8&&a[2]>b[2]+1.5,"hit 1 comes down from the high right to the low left");
        // 2: a whole turn: the point goes all the way round them at about their middle
        double swept=0,prev=Double.NaN;
        for(float t=.17f;t<=.5f;t+=1/240f){
            double[] p=tip(2,t);double az=Math.toDegrees(Math.atan2(p[2],p[0]));
            assertTrue(Math.hypot(p[0],p[2])>1.5,"hit 2 holds the blade out at "+t);
            if(!Double.isNaN(prev))swept+=MathHelper.wrapDegrees(az-prev);prev=az;
        }
        assertTrue(swept>320,"hit 2 turns the blade round them: "+swept);
        // 3: the thrust points straight ahead; the cross cuts down from the right and from the left; the sweep rises
        var hb=IsosangSlash.blade(Vec3d.ZERO,0,3,blow(3,0));
        assertTrue(hb[1].z>.97,"the thrust points ahead: "+hb[1]);
        a=tip(3,.39f);b=tip(3,.475f);
        assertTrue(a[1]>b[1]+1.5&&a[2]>b[2]+1.5,"the cross's first cut comes down from the right to the left");
        a=tip(3,.515f);b=tip(3,.575f);
        assertTrue(a[1]>b[1]+1.5&&b[2]>a[2]+1.5,"the second comes down from the left to the right");
        a=tip(3,.64f);b=tip(3,.76f);
        assertTrue(b[1]>a[1]+1.5&&a[2]>b[2]+1.5,"the sweep rises from the low right to the high left");
        // 4: the twirl goes round over the head; the great cut goes level across the front from the right to the left
        prev=Double.NaN;swept=0;
        for(float t=.085f;t<=.225f;t+=1/240f){
            double[] p=tip(4,t);assertTrue(p[1]>2,"the twirl is over the head at "+t);
            double az=Math.toDegrees(Math.atan2(p[2],p[0]));if(!Double.isNaN(prev))swept+=MathHelper.wrapDegrees(az-prev);prev=az;
        }
        assertTrue(Math.abs(swept)>330,"the twirl goes all the way round: "+swept);
        a=tip(4,.455f);b=tip(4,.62f);
        assertTrue(a[2]>b[2]+3&&Math.abs(tip(4,blow(4,0))[1]-1.2)<.4,"the great cut goes level from the right to the left");
    }
    @Test void theCombosHandOverWithoutJumps(){
        // the next hit begins where the one before stands as it may begin (IsosangRules.STEP; held or pressed early,
        // the server starts it then): its clip begins with the body as far ahead, and turned no more than KagenoClips'
        // 0.08 s blend from the pose on screen can carry smoothly
        for(int combo=1;combo<4;combo++){
            var now=IsosangSlash.hit(combo).frame(IsosangRules.STEP[combo]/20f);var next=IsosangSlash.hit(combo+1).frame(0);
            assertEquals(now.fw(),next.fw(),.03,"hit "+combo+" hands its step on to hit "+(combo+1));
            assertEquals(now.up(),next.up(),.3,"hit "+combo+" height");
            assertEquals(0,MathHelper.wrapDegrees(now.yaw()),75,"hit "+combo+" facing");
        }
        // within a clip the body never jumps: no faster than the thrust's lunge (blocks a second at 60 fps; a jump of half a
        // block in a frame is 30)
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);
            for(float t=0;t+1/60f<=a.length();t+=1/60f){
                double v=Math.abs(a.frame(t+1/60f).fw()-a.frame(t).fw())*60;
                assertTrue(v<32,"hit "+combo+" jumps "+v+" blocks/s at "+t);
            }
        }
    }
    @Test void everySlashIsOverBeforeItsSwingIs(){
        float life=new IsosangSlash().lifetime();
        for(int combo=1;combo<=4;combo++)for(var s:IsosangSlash.slashes(combo)){
            assertTrue(s.from()<s.to()&&s.to()<=s.eat1()&&s.eat0()<s.eat1()&&s.ink0()<s.ink1(),"hit "+combo);
            assertTrue(s.ink1()+.75f<=life,"hit "+combo+"'s blots are gone in time");
        }
        assertEquals(0,IsosangSlash.beamLit(IsosangSlash.BEAM_OFF),1e-6);
        float step=0;for(float t=0;t<1;t+=1/240f)step=Math.max(step,Math.abs(IsosangSlash.beamLit(t+1/60f)-IsosangSlash.beamLit(t)));
        assertTrue(step<.75f,"the beam pops in a single frame: "+step);
    }
}
