package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's T as drawn (2.96.0): the body's motion (from the stance back to it as long as the server holds her, no
 * joint jumping or flipping between frames, never a foot in the ground, low in a crouch where she lands beyond the foe),
 * and the three slashes crossing on the foe (through it, as long as they are, each a way of its own). 2.96.1: her camera
 * stays with her (the hold beyond the foe and the glide round it are gone), and the slashes are long enough to be seen
 * from her far sword.
 */
class SwordLungeTest {
    static final Anim CLIP=SwordLunge.CLIP;
    @Test void itRunsAsLongAsTheServerHoldsHerFromTheStanceBackToIt(){
        var stance=IsosangSlash.stance();
        var first=CLIP.frame(0);var last=CLIP.frame(CLIP.length());
        for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"T starts in the stance");assertTrue(turn(last,stance,j)<.5,"T ends in the stance (the formation's goes on from it)");}
        assertEquals(IsosangRules.LENGTH[IsosangRules.LUNGE]/20f,CLIP.length(),1e-6);
        assertSame(CLIP,IsosangSlash.hit(IsosangRules.LUNGE),"its swing is drawn with its own clip");
        assertEquals(IsosangRules.LUNGE_LENGTH/20f+.45f,SwordLunge.LIFE,1e-6);
    }
    @Test void noJointJumpsOrFlips(){
        double h=1/960.0;
        for(double t=h;t+2*h<=CLIP.length();t+=h){
            var p0=CLIP.frame((float)(t-h));var p1=CLIP.frame((float)t);var p2=CLIP.frame((float)(t+h));var p3=CLIP.frame((float)(t+2*h));
            for(int j=0;j<6;j++){
                double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
            }
        }
        for(double t=0;t+1/60.0<=CLIP.length();t+=1/60.0)
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<125,"joint "+j+" flips at "+t);
    }
    @Test void neverAFootInTheGroundAndLowWhereSheLands(){
        for(float t=0;t<=CLIP.length();t+=1/120f){
            double[] f=IsosangSlash.feet(CLIP.frame(t));
            assertTrue(f[0]>-.02&&f[1]>-.02,"a foot in the ground at "+t+": "+f[0]+" "+f[1]);
        }
        // seen in a low crouch beyond the foe (the video: down, her sword swept through)
        for(float t=IsosangRules.LUNGE_LAND/20f;t<IsosangRules.LUNGE_HOP/20f;t+=.01f)assertTrue(CLIP.frame(t).up()<-.2,"crouched where she lands at "+t+": "+CLIP.frame(t).up());
    }
    @Test void itsThreeSlashesCrossOnTheFoe(){
        for(float yaw=0;yaw<360;yaw+=37){
            Vec3d way=Vec3d.fromPolar(0,yaw);
            Vec3d[] d=new Vec3d[IsosangRules.LUNGE_SLASHES];
            for(int j=0;j<d.length;j++){d[j]=SwordLunge.slash(way,j);assertEquals(1,d[j].length(),1e-9);}
            // each a way of its own (two crossing diagonals and a cut near level): well apart, a crossing on the foe
            for(int a=0;a<d.length;a++)for(int b=a+1;b<d.length;b++){
                double deg=Math.toDegrees(Math.acos(Math.min(1,Math.abs(d[a].dotProduct(d[b])))));
                assertTrue(deg>25,"slashes "+a+" and "+b+" "+deg+" deg apart");
            }
            // across the way they are seen along (2.96.1: from the sword she flies on to, to the foe, SwordLunge.slashWay;
            // her camera is with her there), not along it: each is seen near its full length
            for(var s:d)assertTrue(Math.abs(s.dotProduct(way))<.35,"across the way seen: "+s.dotProduct(way));
            assertTrue(d[0].y<-.4&&d[1].y<-.4,"the diagonals cut down");assertTrue(Math.abs(d[2].y)<.25,"one near level");
        }
        // (2.96.1, user request: "3참격이 안 보이는 거 같아") near twice the video's strokes: seen from her far sword, 10 to
        // 20 blocks off, not from beside the foe
        assertTrue(SwordLunge.SLASH>7.5&&SwordLunge.SLASH<10,"long enough to be seen from her far sword: "+SwordLunge.SLASH);
        assertTrue(SwordLunge.HOLD_LIGHT>=.12f&&SwordLunge.INK_GONE<SwordLunge.LIFE-IsosangRules.lastBlow(IsosangRules.LUNGE)/20f+.01f,"lit a while, gone while T is drawn");
    }
}
