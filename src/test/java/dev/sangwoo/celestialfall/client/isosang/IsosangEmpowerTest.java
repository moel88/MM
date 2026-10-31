package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's E as drawn (2.80.0): the empowered first (three swords thrown) and fourth (a leap back and the giant
 * sword thrown, 2.80.1) move as the other hits do (no joint jumping or flipping, facing the front, the feet on the ground, hit-stops on
 * the blows), hand over to the second and take over from the third, the thrust reaches out ahead on its blow, the giant
 * sword lies along the thrust with its grip toward them and its point at the thrust's tip, and the camera draws back for
 * it and comes in again smoothly.
 */
class IsosangEmpowerTest {
    static final int[] KINDS={IsosangRules.THROWS,IsosangRules.THRUST};
    static float blow(int combo,int i){return IsosangRules.blowAt(combo,i)/20f;}

    @Test void neitherJumpsNorFlipsItsJoints(){
        for(int combo:KINDS){
            Anim a=IsosangSlash.hit(combo);double h=1/960.0;
            for(double t=h;t+2*h<=a.length();t+=h){
                var p0=a.frame((float)(t-h));var p1=a.frame((float)t);var p2=a.frame((float)(t+h));var p3=a.frame((float)(t+2*h));
                for(int j=0;j<6;j++){
                    double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                    assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"kind "+combo+" joint "+j+" jumps "+now+" deg at "+t);
                }
            }
            for(double t=0;t+1/60.0<=a.length();t+=1/60.0)
                for(int j=0;j<6;j++)assertTrue(turn(a.frame((float)t),a.frame((float)(t+1/60.0)),j)<125,"kind "+combo+" joint "+j+" flips at "+t);
        }
    }
    @Test void bothFaceTheFront(){
        for(int combo:KINDS){
            var a=IsosangSlash.hit(combo);
            for(double t=0;t<=a.length();t+=1/240.0){
                float yaw=MathHelper.wrapDegrees(a.frame((float)t).yaw());
                assertTrue(Math.abs(yaw)<=32,"kind "+combo+" turns "+yaw+" deg away from the front at "+t);
            }
        }
    }
    @Test void hitStopsSitOnTheBlows(){
        for(int combo:KINDS){
            var a=IsosangSlash.hit(combo);
            for(var s:a.stops()){
                assertEquals(s.sec(),a.warp(s.sec()),1e-6);
                boolean on=false;for(int i=0;i<IsosangRules.blows(combo);i++)on|=Math.abs(blow(combo,i)-s.sec())<1e-4;
                assertTrue(on,"kind "+combo+": hit-stop at "+s.sec()+" is not on a blow");
                for(int i=0;i<IsosangRules.blows(combo);i++)if(blow(combo,i)>s.sec()+1e-4)assertTrue(s.sec()+s.hold()+s.recover()<=blow(combo,i),"kind "+combo+": still catching up at the next throw");
            }
        }
        assertEquals(3,IsosangSlash.hit(IsosangRules.THROWS).stops().length,"a hit-stop on each throw");
    }
    @Test void theyStartAndEndInTheStanceAndHandOverAsTheOthersDo(){
        var stance=IsosangSlash.stance();
        for(int combo:KINDS){
            var a=IsosangSlash.hit(combo);var first=a.frame(0);var last=a.frame(a.length());
            for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"kind "+combo+" starts in the stance");assertTrue(turn(last,stance,j)<.5,"kind "+combo+" ends in the stance");}
            assertEquals(IsosangRules.LENGTH[combo]/20f,a.length(),.11,"kind "+combo+" runs as long as the server says");
            assertEquals(stance.grip(),last.grip(),.01);
        }
        // the empowered first hands over to the second as the first does; the empowered fourth takes over from the third (in the stance)
        var now=IsosangSlash.hit(IsosangRules.THROWS).frame(IsosangRules.STEP[IsosangRules.THROWS]/20f);var next=IsosangSlash.hit(2).frame(0);
        for(int j=0;j<6;j++)assertTrue(turn(now,next,j)<1,"the empowered first hands joint "+j+" on to the second: "+turn(now,next,j));
        assertEquals(now.fw(),next.fw(),.01);assertEquals(now.up(),next.up(),.01);assertEquals(now.grip(),next.grip(),.5);
        var third=IsosangSlash.hit(3).frame(IsosangRules.STEP[3]/20f);var thrust=IsosangSlash.hit(IsosangRules.THRUST).frame(0);
        for(int j=0;j<6;j++)assertTrue(turn(third,thrust,j)<1,"the third hands joint "+j+" on to the empowered fourth");
    }
    @Test void theFeetStayOnTheGround(){
        for(int combo:KINDS){
            var a=IsosangSlash.hit(combo);
            for(float t=0;t<=a.length();t+=1/120f){
                double[] f=IsosangSlash.feet(a.frame(t));
                assertTrue(f[0]>-.02&&f[1]>-.02,"kind "+combo+" puts a foot into the ground at "+t+": "+f[0]+" "+f[1]);
                assertTrue(Math.min(f[0],f[1])<.05,"kind "+combo+" has no foot on the ground at "+t);
            }
        }
    }
    @Test void theThrustReachesOutAheadOnItsBlow(){
        double[] p=IsosangSlashTest.tip(IsosangRules.THRUST,blow(IsosangRules.THRUST,0));
        assertTrue(p[0]>1.2,"the sword out ahead at the thrust: "+IsosangSlashTest.s(p));
        assertTrue(p[1]>.8&&p[1]<1.8,"at the chest: "+IsosangSlashTest.s(p));
        assertTrue(Math.abs(p[2])<.9,"straight ahead: "+IsosangSlashTest.s(p));
        // drawn back before it (the point nearer them than at the thrust)
        assertTrue(IsosangSlashTest.tip(IsosangRules.THRUST,.1f)[0]<p[0]-.4,"drawn back before it");
    }
    static dev.sangwoo.celestialfall.client.StandClient.Cast giantCast(){
        return new dev.sangwoo.celestialfall.client.StandClient.Cast(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),new net.minecraft.util.Identifier("minecraft","overworld"),
            IsosangRules.SKILL,IsosangRules.THRUST,0,0,0,new Vec3d(10,64,10),30,java.util.List.of());
    }
    @Test void theGiantSwordShowsBesideThemThenFliesOnAndHangsGripTowardThem(){
        // (2.80.1, user request: "검 안 날리는 거 같은데?"; 2.80.2: "2칸 뒤로") thrown from where the 2-block leap takes them
        var c=giantCast();Vec3d origin=new Vec3d(10,64,10),f=Vec3d.fromPolar(0,30),right=new Vec3d(-f.z,0,f.x);
        Vec3d from=IsosangEmpower.throwFrom(c);
        assertEquals(IsosangRules.backstep(IsosangRules.blowAt(IsosangRules.THRUST,0)),origin.subtract(from).length(),1e-6,"thrown from where the leap has carried them");
        assertTrue(origin.subtract(from).dotProduct(f)>1.6,"most of the 2 blocks back by the throw");
        float at=blow(IsosangRules.THRUST,0),arr=IsosangEmpower.arrive(c);
        assertNull(IsosangEmpower.giant(c,0),"not yet as the hit begins");
        // beside them as they leap: at their left, its grip low behind them, its point high ahead
        var g=IsosangEmpower.giant(c,.1f);Vec3d feet=IsosangEmpower.leapt(c,.1f);
        assertNotNull(g);
        Vec3d grip=g.grip().subtract(feet),point=g.point().subtract(feet);
        assertTrue(grip.dotProduct(right)<-.6&&point.dotProduct(right)<-.6,"at their left: "+grip+" "+point);
        assertTrue(grip.dotProduct(f)<0&&point.dotProduct(f)>1.5,"its grip behind them, its point ahead");
        assertTrue(point.y>grip.y+1.5,"tilted up ahead");
        // thrown: no jump as it goes, its point on along its way at the server's pace, and it stops grip toward them
        Vec3d prev=null;double back=0;
        for(float t=at-.05f;t<arr+.2f;t+=1/60f){
            var now=IsosangEmpower.giant(c,t);assertNotNull(now,"there at "+t);
            // (before the throw it goes back with them as they leap)
            if(prev!=null){double step=now.point().distanceTo(prev);assertTrue(step<IsosangRules.GIANT_SPEED*20/60*1.6,"no jump at "+t+": "+step);if(t>at)back=Math.max(back,-now.point().subtract(prev).dotProduct(f));}
            prev=now.point();
        }
        assertTrue(back<.25,"never back along its way but a little jolt as it stops: "+back);
        var mid=IsosangEmpower.giant(c,at+(arr-at)*.5f);assertTrue(mid.point().subtract(from).dotProduct(f)>IsosangRules.GIANT_START+1,"on its way halfway through");
        var hung=IsosangEmpower.giant(c,arr+.12f);
        double ahead=hung.point().subtract(from).dotProduct(f);
        assertTrue(ahead<=IsosangRules.GIANT_RANGE+1e-6&&ahead>IsosangRules.GIANT_RANGE-.35,"its point where it stopped, at its range with nothing in its way: "+ahead);
        assertTrue(hung.grip().subtract(from).dotProduct(f)<ahead-3,"its grip toward them");
        // (the user's videos: from behind its blade rises up the screen over its guard, from above it lies long along the way)
        assertTrue(hung.point().y>hung.grip().y+1.5,"tilted up ahead, its point high: "+hung.grip()+" "+hung.point());
        assertTrue(hung.grip().y-from.y<1.2,"its grip low");
        assertEquals(0,hung.point().subtract(from).dotProduct(right),.05,"on the way they faced");
        assertEquals(0,hung.grip().subtract(from).dotProduct(right),.05,"in from their left to the way they faced");
        Vec3d burst=IsosangEmpower.end(c);
        assertTrue(burst.distanceTo(hung.grip())<IsosangEmpower.GIANT_LEN&&burst.distanceTo(hung.point())<IsosangEmpower.GIANT_LEN,"the burst on its blade");
        // then it fades, no pop
        assertNull(IsosangEmpower.giant(c,arr+IsosangEmpower.GIANT_HANG+IsosangEmpower.GIANT_FADE+.01f),"gone after");
        float last=0;for(float t=0;t<arr+1;t+=1/60f){var x=IsosangEmpower.giant(c,t);float a=x==null?0:x.alpha();assertTrue(Math.abs(a-last)<.35,"it comes and goes, no pop at "+t);last=a;}
    }
    @Test void theGiantSwordsWayMatchesTheServers(){
        // its point as drawn is where the server's way has reached (IsosangRules.giantHead) from its throw on
        var c=giantCast();Vec3d from=IsosangEmpower.throwFrom(c),f=Vec3d.fromPolar(0,30);float at=blow(IsosangRules.THRUST,0);
        // (while it flies: from where it stops on, a little jolt back)
        double flies=IsosangRules.giantArrives(IsosangRules.GIANT_RANGE)-.5;
        for(double k=0;k<=flies;k+=.25){
            double head=IsosangRules.giantHead(k,IsosangRules.GIANT_RANGE),drawn=IsosangEmpower.giant(c,at+(float)(k/20)).point().subtract(from).dotProduct(f);
            assertEquals(head,drawn,.2,k+" ticks after the throw");
        }
        // where it stops, but for the little jolt back
        double stopped=IsosangEmpower.giant(c,IsosangEmpower.arrive(c)).point().subtract(from).dotProduct(f);
        assertTrue(stopped<=IsosangRules.GIANT_RANGE+1e-6&&stopped>IsosangRules.GIANT_RANGE-.35,"stopped at its range: "+stopped);
    }
    @Test void aThrownSwordFliesAtItsSpeedAndStops(){
        var w=new IsosangEmpower.Way(Vec3d.ZERO,new Vec3d(0,0,1),7,.1f,false);
        assertEquals(0,w.flown(.1f),1e-9);
        assertEquals(IsosangRules.SWORD_SPEED,w.flown(.15f),1e-6,"a tick on, a tick's flight");
        assertEquals(7,w.flown(2),1e-9,"stopped where it ends");
        assertEquals(.1f+7/IsosangRules.SWORD_SPEED/20,w.reach(7),1e-6);
    }
    @Test void theCameraDrawsBackForTheGiantSwordAndComesInSmoothly(){
        double most=0,jump=0;
        for(float t=0;t<2;t+=1/60f){most=Math.max(most,IsosangSlash.pull(IsosangRules.THRUST,t));jump=Math.max(jump,Math.abs(IsosangSlash.pull(IsosangRules.THRUST,t+1/60f)-IsosangSlash.pull(IsosangRules.THRUST,t)));}
        assertEquals(IsosangEmpower.PULL,most,.01);assertTrue(jump<.2,"no jump in a frame: "+jump);
        assertEquals(0,IsosangSlash.pull(IsosangRules.THRUST,0),1e-9);assertEquals(0,IsosangSlash.pull(IsosangRules.THRUST,IsosangRules.LENGTH[IsosangRules.THRUST]/20f),1e-9);
        assertEquals(0,IsosangSlash.pull(IsosangRules.THROWS,.5f),1e-9);
    }
}
