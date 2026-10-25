package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.kageno.Ease;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.stand.StandBeatdown.*;

/**
 * The shots of Star Platinum's beatdown's cutscene (2.64.0; 2.65.0 reworked, user request: "카메라를 더 잘 이용해야하고
 * ... 컷씬을 더 찰지게 만들어야하고 카메라도 컷씬도 더 잘 써봐"), cut on the beats of the user's recording, as its user
 * and the one struck see it: where the eye is, what it looks at, its lens (vertical field of view, degrees) and its roll,
 * at each moment of the beatdown's clock. Each shot moves within itself (pushing in, pulling back, turning round the
 * two, tilting up after what flies, following them as they fly) and cuts hard to the next, as a cutscene's shots do;
 * what it looks at goes with the bodies (the one struck as the cutscene shows them, StandBeatdown.victim, and the stand
 * just in front of them). On top of each, every blow jolts the camera ({@link #jolt}: a shake, its roll and a punch in
 * of the lens, each coming in over two frames and dying away), hardest for the opening blow, the uppercut, the last
 * blow and the fall. Laid out on the blow's line: its user's feet, the way it went and its right.
 * <ol>
 * <li>The blow lands (CUT): low beside the one struck as they are knocked back, the lens opening out.</li>
 * <li>Pulled back wide over its user's left shoulder: its user, the stand going back into them, the one struck reeling.</li>
 * <li>"Yare yare daze" (SOUND): on its user from the chest up, the hand on their head, pushing in slowly.</li>
 * <li>"Star Platinum!": low in front of them to their right, looking up as the stand bursts out of them, the lens
 * closing in.</li>
 * <li>The grab (ENGAGE): over the stand's right shoulder at the face of the one it grabs, pushing in.</li>
 * <li>The uppercut: low under them, tilting up after them as they fly up.</li>
 * <li>The rush, three shots: over the stand's shoulder, turning slowly, the frame tipped; from behind the one struck,
 * close, the fists coming at the camera past them; turning fast round the two from the one side to the other.</li>
 * <li>"裁くのは" (JUDGE): straight down its user's arm as they point at the one struck, at their face.</li>
 * <li>The pause after it: over its user's shoulder, down their pointing arm at the rush up in the air.</li>
 * <li>"俺の" (MINE): close on the stand's face as it rushes, the fists flying past the camera.</li>
 * <li>"スタンド" (the rush's last blows past): from the side as it draws back for the last blow, pushing in.</li>
 * <li>"DA!" (FINAL): close in front of the one struck to their right, low, as the blow lands, holds and sends them
 * flying out of the frame.</li>
 * <li>Their flight and fall: off to their side, travelling after them half as far, turning to follow them down to where they lie.</li>
 * </ol>
 */
public final class StandBeatdownCamera {
    public record Shot(Vec3d eye,Vec3d look,double fov,float roll){}
    /** A blow's jolt on the camera: how far the eye is shaken (blocks, to the right and up of the view), its roll (degrees) and how far the lens closes in (degrees). */
    public record Jolt(double right,double up,float roll,double lens){
        static final Jolt NONE=new Jolt(0,0,0,0);
    }
    private StandBeatdownCamera(){}

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** How far through the shot from {@code a} to {@code b} {@code t} is, eased. */
    static float u(float t,float a,float b){return smooth((t-a)/(b-a));}

    /** The stand's feet where it floats in front of the one struck (roughly, for the shots: StandBeatdown.shape places it). */
    static Vec3d stand(Beat b,Body v){return v.feet().subtract(b.f.multiply(1.55)).add(0,-.45,0);}

    /** The shot at {@code t} on the beatdown's clock (before any blow's jolt). */
    public static Shot at(Beat b,float t){
        Vec3d o=b.from,f=b.f,r=b.r;
        Body v=victim(b,Math.min(t,FINAL-1e-4f));
        // the one struck's chest as shown, and the stand roughly where it floats in front of them
        Vec3d chest=v.feet().add(0,1.15,0);
        double d=v.feet().subtract(o).dotProduct(f);
        Vec3d stand=stand(b,v),head=stand.add(0,1.75,0);
        if(t<CUTS[1]){
            // the blow lands: low beside the one struck on their left, the lens opening as they are knocked back
            float k=u(t,CUTS[0],CUTS[1]);double from=b.victimFrom.subtract(o).dotProduct(f);
            return new Shot(b.at(o,from*.6,.7,-2.5+.3*k),b.at(o,from*.6+.6*k+.2,1.25,0),50+10*k,-5*(1-k));
        }
        if(t<CUTS[2]){
            // pulled back wide over its user's left shoulder: its user, the stand going back into them, the one struck reeling
            float k=u(t,CUTS[1],CUTS[2]);
            return new Shot(b.at(o,-1.5-.5*k,2.2+.3*k,-3.6-.6*k),b.at(o,d*.55,1.2,0),58,0);
        }
        if(t<CUTS[3]){
            // yare yare: on its user from the chest up, the hand on their head in the shot (the bars take a quarter of
            // it), a little to their right, the side of the hand (from their left, the first capture showed the hand
            // half behind their head), and a little low, pushing in slowly
            float k=u(t,CUTS[2],CUTS[3]);
            return new Shot(b.at(o,2.55-.4*k,1.5,.65-.1*k),b.at(o,0,1.72,0),44-5*k,0);
        }
        if(t<CUTS[4]){
            // Star Platinum!: low in front of them to their right, looking up at them and the stand bursting out, the
            // lens closing in through the shout, the frame tipped and coming level
            float k=u(t,CUTS[3],CUTS[4]),z=Ease.out(t,REVEAL+.02f,REVEAL+.45f);
            return new Shot(b.at(o,2.35-.3*k,.4,1.5),b.at(o,-.25,1.75+.1*k,.4),70-18*z,6*(1-k));
        }
        if(t<CUTS[5]){
            // the grab: over the stand's right shoulder at the face of the one it grabs, pushing in
            float k=u(t,CUTS[4],CUTS[5]);
            return new Shot(b.at(o,d*.3+.5*k,2.0,1.35-.15*k),b.at(o,d,1.5,0),52-6*k,0);
        }
        if(t<CUTS[6]){
            // the uppercut: low under them on their left, tilting up after the one struck as they fly
            float k=u(t,CUTS[5],CUTS[6]);
            Vec3d look=b.at(o,d-.3,1.3,0).lerp(chest,Ease.out(t,UPPER,UPPER+.3f));
            return new Shot(b.at(o,d-1.0,.3,-2.3-.3*k),look,72-6*k,-4*k);
        }
        if(t<CUTS[7]){
            // the rush: past the stand's right shoulder at them, high enough to see past its mane, turning slowly, tipped
            float k=u(t,CUTS[6],CUTS[7]);
            Vec3d mid=chest.lerp(head,.3);
            return new Shot(stand.add(f.multiply(-1.2+.3*k)).add(r.multiply(2.3-.7*k)).add(0,2.6,0),mid,54-4*k,5);
        }
        if(t<CUTS[8]){
            // from behind them, off to their side, close: the fists coming at the camera past them, the stand's face beyond
            float k=u(t,CUTS[7],CUTS[8]);
            Vec3d mid=chest.lerp(head,.55);
            return new Shot(chest.add(f.multiply(2.4-.35*k)).add(r.multiply(-1.5)).add(0,.35,0),mid,58-4*k,-4);
        }
        if(t<CUTS[9]){
            // turning fast round the two, from their left over to their right, close in
            float k=u(t,CUTS[8],CUTS[9]);
            Vec3d mid=chest.lerp(head,.5);
            double a=Math.toRadians(-75+125*k),rad=4.6-.6*Math.sin(Math.PI*k);
            Vec3d eye=mid.add(f.multiply(Math.cos(a)*rad)).add(r.multiply(Math.sin(a)*rad)).add(0,.5,0);
            return new Shot(eye,mid,54,3*(float)Math.sin(Math.PI*k));
        }
        if(t<CUTS[10]){
            // "裁くのは": straight down its user's arm as they point at the one struck, at their face, pushing in
            float k=u(t,CUTS[9],CUTS[10]);
            return new Shot(b.at(o,1.9-.25*k,1.95,.35),b.at(o,0,1.62,.05),46-5*k,0);
        }
        if(t<CUTS[11]){
            // the pause after it: over its user's left shoulder, down their pointing arm at the rush up in the air
            float k=u(t,CUTS[10],CUTS[11]);
            return new Shot(b.at(o,-1.5+.3*k,2.05,-.8),chest.lerp(head,.4),48-4*k,0);
        }
        if(t<CUTS[12]){
            // "俺の": close on the stand's face as it rushes, from in front of it to its right and a little low, its fists
            // flying past the camera at the one struck behind it
            float k=u(t,CUTS[11],CUTS[12]);
            return new Shot(head.add(f.multiply(1.3-.15*k)).add(r.multiply(.95)).add(0,-.35,0),head.add(0,-.05,0),48-6*k,0);
        }
        if(t<CUTS[13]){
            // "スタンド": from its left side as it draws back for the last blow, pushing in, the lens closing as it strikes
            float k=u(t,CUTS[12],CUTS[13]);
            Vec3d mid=chest.lerp(head,.5);
            return new Shot(mid.add(r.multiply(-4.2+.8*k)).add(f.multiply(-.3)).add(0,.35,0),mid,52-5*k-6*u(t,FINAL-.2f,FINAL),0);
        }
        if(t<CUTS[14]){
            // "DA!": close in front of the one struck to their right and low, as the blow lands, holds them and sends them
            // flying out of the frame
            Vec3d at=launch(b).feet().add(0,1.15,0);
            return new Shot(at.add(f.multiply(1.8)).add(r.multiply(2.3)).add(0,-.55,0),at,58,-6);
        }
        // their flight and fall: off to their left and above, the eye travelling half as far as they fly and a moment
        // behind them (so it never moves as fast as they do, turning after them instead), coming to rest beside where
        // they lie
        Vec3d body=flight(b,Math.min(t,END)).feet().add(0,.6,0),from=launch(b).feet().add(0,.6,0);
        Vec3d behind=flight(b,Math.max(FINAL,Math.min(t,END)-.15f)).feet().add(0,.6,0);
        return new Shot(from.lerp(behind,.5).add(r.multiply(-5.4)).subtract(f.multiply(.6)).add(0,1.5,0),body,58,0);
    }

    /** Every blow's jolt on the camera at {@code t}: the shake, its roll and the lens punching in, each in over two frames. */
    public static Jolt jolt(Beat b,float t){
        // how hard each moment shakes it (blocks) and punches in the lens (degrees)
        float shake=.07f*kick(t,CUT,.18f)+.035f*kick(t,REVEAL,.25f)+.03f*kick(t,GRAB,.12f)+.09f*kick(t,UPPER,.2f)
            +.16f*kick(t,FINAL,.26f)+.07f*kick(t,LAND,.2f);
        double lens=6*kick(t,CUT,.2f)+3*kick(t,GRAB,.15f)+6*kick(t,UPPER,.2f)+11*kick(t,FINAL,.3f)+4*kick(t,LAND,.15f);
        int n=blows(t);
        for(int k=Math.max(0,n-3);k<n;k++){float j=kick(t,blowAt(k),.06f);shake+=.022f*j;lens+=1.1*j;}
        if(shake<=1e-5&&lens<=1e-5)return Jolt.NONE;
        double s=t*Math.PI*2;
        double x=.6*Math.sin(s*13.1+1.3)+.4*Math.sin(s*7.3+.4),y=.6*Math.sin(s*11.3+2.1)+.4*Math.sin(s*8.9+.9),z=Math.sin(s*9.7+.2);
        return new Jolt(shake*x,shake*y,(float)(shake*z*25),lens);
    }
}
