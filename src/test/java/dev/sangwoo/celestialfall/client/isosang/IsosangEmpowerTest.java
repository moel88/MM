package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's E as drawn (2.80.0): the empowered first (three swords thrown) and fourth (a step back and the giant
 * sword) move as the other hits do (no joint jumping or flipping, facing the front, the feet on the ground, hit-stops on
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
    @Test void theGiantSwordLiesAlongTheThrustItsPointAtTheTip(){
        var c=new dev.sangwoo.celestialfall.client.StandClient.Cast(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),new net.minecraft.util.Identifier("minecraft","overworld"),
            IsosangRules.SKILL,IsosangRules.THRUST,0,0,0,new Vec3d(10,64,10),30,java.util.List.of());
        Vec3d from=IsosangEmpower.thrustFrom(c),f=Vec3d.fromPolar(0,30);
        assertEquals(IsosangRules.BACKSTEP,new Vec3d(10,64,10).subtract(from).length(),1e-6,"they thrust from where the step back takes them");
        Vec3d[] ends=IsosangEmpower.giantEnds(c);
        double grip=ends[0].subtract(from).dotProduct(f),point=ends[1].subtract(from).dotProduct(f);
        assertTrue(grip>.6&&grip<1.6,"its grip toward them: "+grip);
        assertEquals(IsosangRules.THRUST_REACH,point,.6,"its point at the thrust's tip: "+point);
        assertTrue(ends[1].y<ends[0].y,"dipping toward its point");
        assertTrue(IsosangEmpower.giantAlpha(blow(IsosangRules.THRUST,0)+.05f)>.99,"there as the thrust strikes");
        assertEquals(0,IsosangEmpower.giantAlpha(IsosangEmpower.GIANT_GONE+.01f),1e-6,"gone after");
        for(float t=0;t<1;t+=1/60f)assertTrue(Math.abs(IsosangEmpower.giantAlpha(t+1/60f)-IsosangEmpower.giantAlpha(t))<.35,"it fades in and out, no pop");
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
