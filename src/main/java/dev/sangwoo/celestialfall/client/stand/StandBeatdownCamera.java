package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.kageno.Ease;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.stand.StandBeatdown.*;

/**
 * The shots of Star Platinum's beatdown's cutscene (2.64.0), cut on the beats of the user's recording, as its user and
 * the one struck see it: where the eye is, what it looks at, its lens (vertical field of view, degrees) and its roll,
 * at each moment of the beatdown's clock. Each shot moves within itself (a slow push in, a tilt after what flies) and
 * cuts hard to the next, as a cutscene's shots do; what it looks at goes with the bodies (the one struck as the
 * cutscene shows them, StandBeatdown.victim). Laid out on the blow's line: its user's feet, the way it went and its
 * right.
 * <ol>
 * <li>The blow lands (from CUT): low from the side, pushing in as the one struck is knocked back and the stand goes
 * back into its user.</li>
 * <li>"Yare yare daze" (1.12 s): close on its user's face, a hand on their head, shaking it; a slow push in.</li>
 * <li>"Star Platinum!" (2.6 s): low in front of them to their right, looking up as the stand bursts out of them, the
 * lens closing in through the shout.</li>
 * <li>The grab (ENGAGE): from the side, the stand coming round and gripping the collar.</li>
 * <li>The uppercut (UPPER): low beside them, tilting up after the one struck as they fly up.</li>
 * <li>The rush, three shots: over the stand's shoulder at them; from behind them, the fists coming at the camera; a
 * wide slow turn round the two.</li>
 * <li>The big ORAs (8.4 s): from the side, the lens punching in at each; the fourth from in front, low.</li>
 * <li>The last blow (FINAL - 0.3 s): low behind the stand, down its punch; they fly off from it.</li>
 * <li>Their flight and fall (FINAL + 0.28 s): wide from the side of the way they fly, following them down.</li>
 * </ol>
 */
public final class StandBeatdownCamera {
    public record Shot(Vec3d eye,Vec3d look,double fov,float roll){}
    private StandBeatdownCamera(){}

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** How far through the shot from {@code a} to {@code b} {@code t} is, eased. */
    static float u(float t,float a,float b){return smooth((t-a)/(b-a));}

    /** The shot at {@code t} on the beatdown's clock. */
    public static Shot at(Beat b,float t){
        Vec3d o=b.from;
        Body v=victim(b,Math.min(t,FINAL-1e-4f));
        // the one struck's chest as shown, and the stand roughly where it stands in front of them
        Vec3d chest=v.feet().add(0,1.15,0);
        double d=v.feet().subtract(o).dotProduct(b.f);
        if(t<CUTS[1]){
            // the blow lands: low from its left side, pushing in
            float k=u(t,CUTS[0],CUTS[1]);double mid=Math.max(1.4,d*.55);
            return new Shot(b.at(o,mid,.95,-3.7+.7*k),b.at(o,mid+.15,1.25,0),62-6*k,0);
        }
        if(t<CUTS[2]){
            // yare yare: close on its user's face, a little to their left, pushing in
            float k=u(t,CUTS[1],CUTS[2]);
            return new Shot(b.at(o,1.55-.2*k,1.62,-.42+.05*k),b.at(o,0,1.6,0),42-4*k,0);
        }
        if(t<CUTS[3]){
            // Star Platinum!: low in front of them to their right, looking up at them and the stand bursting out
            float k=u(t,CUTS[2],CUTS[3]),z=Ease.out(t,CUTS[2]+.05f,CUTS[2]+.5f);
            return new Shot(b.at(o,2.45-.25*k,.5,1.55),b.at(o,-.25,1.72+.08*k,.45),66-14*z,4*(1-k));
        }
        if(t<CUTS[4]){
            // the grab: from its right side, a little ahead
            float k=u(t,CUTS[3],CUTS[4]);
            return new Shot(b.at(o,d*.62,1.45,4.3-.4*k),b.at(o,d*.72,1.5,0),56-3*k,0);
        }
        if(t<CUTS[5]){
            // the uppercut: low beside them on their left, tilting up after the one struck
            float k=u(t,CUTS[4],CUTS[5]);
            Vec3d look=b.at(o,d-.3,1.4,0).lerp(chest,Ease.out(t,CUTS[4],CUTS[4]+.3f));
            return new Shot(b.at(o,d-.9,.35,-2.4-.3*k),look,72-4*k,-3*k);
        }
        // the stand in front of the one struck, up in the air with them
        Vec3d stand=v.feet().subtract(b.f.multiply(1.5)).add(0,-.4,0);
        if(t<CUTS[6]){
            // the rush: over the stand's right shoulder at them
            float k=u(t,CUTS[5],CUTS[6]);
            return new Shot(stand.add(b.f.multiply(-1.45+.2*k)).add(b.r.multiply(1.0)).add(0,2.25,0),chest,58-3*k,0);
        }
        if(t<CUTS[7]){
            // from behind them: the fists coming at the camera, the stand's face beyond
            float k=u(t,CUTS[6],CUTS[7]);
            return new Shot(chest.add(b.f.multiply(1.85-.15*k)).add(b.r.multiply(-.85)).add(0,.35,0),stand.add(0,1.75,0),60-2*k,0);
        }
        if(t<CUTS[8]){
            // wide, turning slowly round the two from their left
            float k=u(t,CUTS[7],CUTS[8]);
            Vec3d mid=chest.lerp(stand.add(0,1.5,0),.5);
            double a=Math.toRadians(-62-50*k),rad=5.4;
            Vec3d eye=mid.add(b.f.multiply(Math.cos(a)*rad)).add(b.r.multiply(Math.sin(a)*rad)).add(0,.7,0);
            return new Shot(eye,mid,55,0);
        }
        // the big ORAs and the last blow: the lens punches in at each blow, eased in over two frames
        double punch=0;
        for(float at:BIG)punch+=5*StandBeatdown.kick(t,at,.18f);
        if(t<CUTS[9]){
            // from the left, at mid distance, following them back
            float k=u(t,CUTS[8],CUTS[9]);
            Vec3d mid=chest.lerp(stand.add(0,1.5,0),.45);
            return new Shot(mid.add(b.r.multiply(-5.2+.5*k)).add(0,.45,0),mid,56-punch,0);
        }
        if(t<CUTS[10]){
            // the fourth big ORA: low in front of them to their right
            float k=u(t,CUTS[9],CUTS[10]);
            Vec3d mid=chest.lerp(stand.add(0,1.5,0),.5);
            return new Shot(mid.add(b.f.multiply(1.6)).add(b.r.multiply(2.6+.3*k)).add(0,-1.0,0),mid.add(0,.2,0),60-punch,-3);
        }
        if(t<CUTS[11]){
            // the last blow: low behind the stand, down its punch at them; they fly off from it
            float k=u(t,CUTS[10],CUTS[11]);
            Vec3d at=launch(b).feet();
            Vec3d eye=at.subtract(b.f.multiply(3.6-.3*k)).add(b.r.multiply(1.25)).add(0,.35,0);
            Vec3d look=at.add(0,1.1,0);
            if(t>FINAL)look=look.lerp(StandBeatdown.flight(b,t).feet().add(0,.9,0),Ease.out(t,FINAL,FINAL+.25f));
            return new Shot(eye,look,62-6*StandBeatdown.kick(t,FINAL,.25f),0);
        }
        // their flight and fall: wide from the side of their way, following them down to where they end
        Vec3d at=launch(b).feet();
        Vec3d end=b.land;
        Vec3d mid=at.lerp(end,.55);
        double far=Math.max(6,at.distanceTo(end)*.55);
        Vec3d eye=mid.add(b.r.multiply(-far)).add(0,2.6,0);
        Vec3d body=StandBeatdown.flight(b,Math.min(t,END)).feet().add(0,.6,0);
        Vec3d look=at.add(0,1.1,0).lerp(body,Ease.out(t,CUTS[11],CUTS[11]+.2f));
        return new Shot(eye,look,56,0);
    }
}
