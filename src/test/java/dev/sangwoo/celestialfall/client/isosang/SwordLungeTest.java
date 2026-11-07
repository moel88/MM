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
 * and the three slashes crossing on the foe (through it, as long as they are, each a way of its own).
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
            // across the way she dashed, not along it
            for(var s:d)assertTrue(Math.abs(s.dotProduct(way))<.35,"across her way: "+s.dotProduct(way));
            assertTrue(d[0].y<-.4&&d[1].y<-.4,"the diagonals cut down");assertTrue(Math.abs(d[2].y)<.25,"one near level");
        }
        assertTrue(SwordLunge.SLASH>4&&SwordLunge.SLASH<5.5,"as long as the video's strokes over her foe");
    }
    @Test void herCameraStaysBeyondTheFoeThroughTheSlashesThenGlidesOnToHer(){
        // (the video: the camera by the foe as the swords and the slashes come down, on its way to her by 1 s, there by
        // about 1.3 s) held from her flight on until after the last slash cuts, back with her a moment after T is over
        assertTrue(SwordLunge.CAMERA_HOLD>IsosangRules.lastBlow(IsosangRules.LUNGE),"held through the last slash");
        assertTrue(SwordLunge.CAMERA_HOLD>=IsosangRules.LUNGE_HOP+IsosangRules.Z_TRAVEL,"and until she stands on the far sword");
        assertTrue(SwordLunge.CAMERA_BACK>SwordLunge.CAMERA_HOLD+8&&SwordLunge.CAMERA_BACK<=IsosangRules.LUNGE_LENGTH+8,"glided on to her over half a second or so, about as T ends");
        assertTrue(SwordLunge.CAMERA_BACK/20f<SwordLunge.LIFE,"while T is still drawn");
        // held, then with her
        Vec3d held=new Vec3d(0,0,0),her=new Vec3d(1,1.7,-19),foe=new Vec3d(0,1.2,-3);
        assertEquals(0,SwordLunge.cameraAt(SwordLunge.CAMERA_HOLD,held,her,foe).distanceTo(held),1e-9);
        assertEquals(0,SwordLunge.cameraAt(SwordLunge.CAMERA_BACK,held,her,foe).distanceTo(her),1e-9);
    }
    @Test void herCameraGoesRoundTheFoeNotThroughIt(){
        // her far sword mostly on the far side of the foe from where she landed (3 blocks beyond it): the camera's way bows
        // out round it, smoothly, and is back on her at its end
        Vec3d held=Vec3d.ZERO;
        for(double side:new double[]{-1.2,-.4,0,.3,1.1})for(double far:new double[]{9,14,22,28}){
            Vec3d her=new Vec3d(side*.5,1.7,-far),foe=new Vec3d(side,1.2,-3);
            double nearest=1e9;Vec3d last=null;
            for(float a=SwordLunge.CAMERA_HOLD;a<=SwordLunge.CAMERA_BACK;a+=.02f){
                Vec3d at=SwordLunge.cameraAt(a,held,her,foe);
                nearest=Math.min(nearest,Math.hypot(at.x-foe.x,at.z-foe.z));
                // no jump between frames even at 2.5 times the fastest a 60 fps frame would see
                if(last!=null)assertTrue(at.distanceTo(last)<.6,"a jump of "+at.distanceTo(last)+" at "+a);
                last=at;
            }
            assertTrue(nearest>SwordLunge.CAMERA_CLEAR-.35,"through the foe: "+nearest+" (side "+side+", far "+far+")");
        }
        // with no foe (it all comes down where she stands): straight on to her
        Vec3d her=new Vec3d(3,1.7,-12);
        assertEquals(0,SwordLunge.cameraAt((SwordLunge.CAMERA_HOLD+SwordLunge.CAMERA_BACK)/2,Vec3d.ZERO,her,null).distanceTo(Vec3d.ZERO.lerp(her,.5)),1e-9);
    }
}
