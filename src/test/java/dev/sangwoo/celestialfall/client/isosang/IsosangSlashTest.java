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
 * Lee So-sang's basic attack as drawn (2.77.0; remade after the user's video of each hit in 2.77.1): four hits, each blade
 * cutting through the server's reach on the server's blows, the strokes the videos show, no joint jumping or flipping
 * between frames, each hit beginning exactly where the one before stands as it hands over, every slash over before its
 * swing is, and the finisher's camera drawn back and in smoothly.
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
    static double azimuth(double[] p){return Math.toDegrees(Math.atan2(p[2],p[0]));}

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
                if(combo<4)assertTrue(s.sec()+s.hold()+s.recover()<=IsosangRules.STEP[combo]/20f,"hit "+combo+": still catching up as the next takes over");
            }
        }
    }
    @Test void theFirstStartsAndEveryOneEndsInTheStanceAndRunsAsLongAsTheHit(){
        var stance=IsosangSlash.stance();
        var first=IsosangSlash.hit(1).frame(0);
        for(int j=0;j<6;j++)assertTrue(turn(first,stance,j)<.5,"hit 1 starts in the stance");
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);var last=a.frame(a.length());
            for(int j=0;j<6;j++)assertTrue(turn(last,stance,j)<.5,"hit "+combo+" ends in the stance");
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
            assertTrue(p[1]>.1&&p[1]<2.6,"hit "+combo+" blow "+i+" at a body's height: "+p[1]);
            if(combo==2){
                if(i==0){assertTrue(p[1]<.8,"the turn sweeps low over the ground: "+p[1]);assertTrue(p[0]>2.4,"and passes the front far out: "+p[0]);}
                continue;
            }
            assertTrue(p[0]>1.4,"hit "+combo+" blow "+i+" ahead: "+p[0]);
            assertTrue(IsosangRules.lane(combo,i,p[0],p[2],0,.3),"hit "+combo+" blow "+i+" inside its way: "+java.util.Arrays.toString(p));
        }
    }
    @Test void eachHitCutsTheWayItsVideoShows(){
        // 1: flipped upright at the right side; the great diagonal down from the high right to the low left; the cut
        // rising straight back up to the right; held out there
        double[] a=tip(1,.05f);assertTrue(a[1]>2.2&&a[2]>.2,"hit 1 begins with the sword upright at the right: "+java.util.Arrays.toString(a));
        a=tip(1,.25f);double[] b=tip(1,.345f);
        assertTrue(a[1]>b[1]+1.4&&a[2]>b[2]+1.5,"hit 1 comes down from the high right to the low left");
        a=tip(1,.39f);b=tip(1,.46f);
        assertTrue(b[1]>a[1]+.8&&b[2]>a[2]+2.5,"hit 1 cuts back up to the right");
        b=tip(1,.6f);assertTrue(b[2]>1.4&&b[1]>1&&b[1]<2,"hit 1 holds the sword out to the right: "+java.util.Arrays.toString(b));
        // 2: flipped upright, then a whole turn low over the ground, far out
        a=tip(2,.04f);assertTrue(a[1]>2.4,"hit 2 flips the sword upright first: "+a[1]);
        double swept=0,prev=Double.NaN;
        for(float t=.17f;t<=.32f;t+=1/240f){
            double[] p=tip(2,t);
            assertTrue(Math.hypot(p[0]-.36,p[2])>2.2,"hit 2 holds the blade far out round them at "+t);assertTrue(p[1]<.9,"hit 2 sweeps low at "+t+": "+p[1]);
            if(!Double.isNaN(prev))swept+=MathHelper.wrapDegrees(azimuth(p)-prev);prev=azimuth(p);
        }
        assertTrue(Math.abs(swept)>340,"hit 2 turns the blade all the way round them: "+swept);
        // 3: the thrust straight ahead; the long cut rising to the high left; the quick cut back down to the low right;
        // the great cut rising up the right side; the sweep low across from the left to the right
        var hb=IsosangSlash.blade(Vec3d.ZERO,0,3,blow(3,0));
        assertTrue(hb[1].z>.97,"the thrust points ahead: "+hb[1]);
        a=tip(3,.41f);b=tip(3,.49f);
        assertTrue(b[1]>a[1]+1.2&&a[2]>b[2]+1.5,"the long cut rises from the low right to the high left");
        a=tip(3,.49f);b=tip(3,.585f);
        assertTrue(a[1]>b[1]+1.2&&b[2]>a[2]+1.5,"the quick cut comes back down to the low right");
        a=tip(3,.61f);b=tip(3,.7f);
        assertTrue(b[1]>a[1]+1.5&&a[2]>.5&&tip(3,blow(3,3))[2]>.8,"the great cut rises up the right side");
        a=tip(3,.89f);b=tip(3,1.02f);
        assertTrue(b[2]>a[2]+2.5&&tip(3,blow(3,4))[1]<.9,"the sweep goes low across from the left to the right");
        // 4: the twirl goes round over the head; the giant cut goes level and low across the front from the right to the left
        prev=Double.NaN;swept=0;
        for(float t=.08f;t<=.2f;t+=1/240f){
            double[] p=tip(4,t);assertTrue(p[1]>2,"the twirl is over the head at "+t);
            if(!Double.isNaN(prev))swept+=MathHelper.wrapDegrees(azimuth(p)-prev);prev=azimuth(p);
        }
        assertTrue(Math.abs(swept)>300,"the twirl goes round: "+swept);
        // (after the blow's hit-stop has caught up)
        a=tip(4,.52f);b=tip(4,.76f);
        assertTrue(a[2]>b[2]+3&&tip(4,blow(4,0))[1]<1.2,"the giant cut goes level and low from the right to the left");
    }
    @Test void eachHitBeginsWhereTheOneBeforeStandsAsItHandsOver(){
        // the next hit begins where the one before stands as it may begin (IsosangRules.STEP; held or pressed early,
        // the server starts it then): the same pose, the body as far ahead, turned the same way
        for(int combo=1;combo<4;combo++){
            var now=IsosangSlash.hit(combo).frame(IsosangRules.STEP[combo]/20f);var next=IsosangSlash.hit(combo+1).frame(0);
            for(int j=0;j<6;j++)assertTrue(turn(now,next,j)<1,"hit "+combo+" hands joint "+j+" on to hit "+(combo+1)+": "+turn(now,next,j));
            assertEquals(now.fw(),next.fw(),.01,"hit "+combo+" hands its step on to hit "+(combo+1));
            assertEquals(now.up(),next.up(),.01,"hit "+combo+" height");
            assertEquals(0,MathHelper.wrapDegrees(now.yaw()-next.yaw()),1,"hit "+combo+" facing");
            assertEquals(now.grip(),next.grip(),.5,"hit "+combo+" grip");
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
    @Test void theBladeAndItsSlashesStayAboveTheGround(){
        // the point never goes into the floor; the slashes (well past it) graze it at most
        for(int combo=1;combo<=4;combo++){
            var a=IsosangSlash.hit(combo);
            for(float t=0;t<=a.length();t+=1/120f)assertTrue(tip(combo,t)[1]>.02,"hit "+combo+"'s point in the ground at "+t+": "+tip(combo,t)[1]);
            for(var s:IsosangSlash.slashes(combo)){
                var sw=IsosangSlash.swept(Vec3d.ZERO,0,combo,s.from(),s.to(),s.inner(),s.outer());
                for(int i=0;i<sw.n();i++){
                    assertTrue(sw.inner()[i].y>-.08,"hit "+combo+"'s slash from "+s.from()+" under the ground: "+sw.inner()[i].y);
                    assertTrue(sw.outer()[i].y>-.08,"hit "+combo+"'s slash from "+s.from()+" under the ground: "+sw.outer()[i].y);
                }
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
    @Test void theFinishersCameraDrawsBackAndComesInSmoothly(){
        for(int combo=1;combo<=3;combo++)for(float t=0;t<2;t+=.05f)assertEquals(0,IsosangSlash.pull(combo,t),1e-9);
        double most=0,jump=0;
        for(float t=0;t<2;t+=1/60f){most=Math.max(most,IsosangSlash.pull(4,t));jump=Math.max(jump,Math.abs(IsosangSlash.pull(4,t+1/60f)-IsosangSlash.pull(4,t)));}
        assertEquals(IsosangSlash.PULL,most,.01,"all the way back while the giant cut spreads");
        assertTrue(jump<.2,"no jump in a frame: "+jump);
        assertEquals(0,IsosangSlash.pull(4,0),1e-9);assertEquals(0,IsosangSlash.pull(4,IsosangRules.LENGTH[4]/20f),1e-9);
        assertEquals(IsosangSlash.PULL,IsosangSlash.pull(4,blow(4,0)+.2f),.05,"drawn back by the time the cut lands");
    }
}
