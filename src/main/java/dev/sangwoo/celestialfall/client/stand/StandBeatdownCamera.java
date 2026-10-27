package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.kageno.Ease;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.stand.StandBeatdown.*;

/**
 * The shots of Star Platinum's beatdown's cutscene (2.64.0; 2.65.0 reworked, user request: "카메라를 더 잘 이용해야하고
 * ... 컷씬을 더 찰지게 만들어야하고 카메라도 컷씬도 더 잘 써봐"; 2.66.0 its rush and last blow from the user's video,
 * "영상처럼 저런 카메라 구도나 모션으로는 못 만듬?"), cut on the beats of the user's recording, as its user and the one
 * struck see it: where the eye is, what it looks at, its lens (vertical field of view, degrees), its roll, whether the
 * eye is inside the one struck (their own view: they are not drawn) and how hard the blows shake it, at each moment of
 * the beatdown's clock. Each shot moves within itself (pushing in, pulling back, tilting up after what flies, following
 * them as they fly) and cuts hard to the next, as a cutscene's shots do; what it looks at goes with the bodies (the one
 * struck as the cutscene holds them, without each fist's jolt, and the stand where it floats in front of them). On top
 * of each, every blow jolts the camera ({@link #jolt}: a shake, its roll and a punch in of the lens, each coming in over
 * two frames and dying away), hardest for the opening blow, the uppercut, the last blow and the fall, harder in the shots
 * from the one struck's eyes. Laid out on the blow's line: its user's feet, the way it went and its right.
 * <ol>
 * <li>The blow lands (CUT): low beside the one struck as they are knocked back, the lens opening out.</li>
 * <li>Pulled back wide over its user's left shoulder: its user, the stand going back into them, the one struck reeling.</li>
 * <li>"Yare yare daze" (SOUND): on its user from the chest up, the hand on their head, pushing in slowly.</li>
 * <li>"Star Platinum!": low in front of them to their right, looking up as the stand bursts out of them, the lens
 * closing in.</li>
 * <li>The grab (ENGAGE): over the stand's right shoulder at the face of the one it grabs, pushing in.</li>
 * <li>The uppercut: low under them, tilting up after them as they fly up.</li>
 * <li>The rush begins (the video's first shots): close on the stand's face from in front of it to its left, its
 * shoulder near, the frame tipped; its fists straight into the lens from the one struck's eyes; from just behind their
 * eyes, the stand from its knees up rushing them, its fists flying at the camera from every side, tipped and rocking.</li>
 * <li>High over the stand's right shoulder, down onto its fists hammering them.</li>
 * <li>From beside the stand's head: their face, their head snapping with every fist.</li>
 * <li>"裁くのは" (JUDGE): over the back of the one struck, at its face glaring over its fists as they come in on them.
 * Through the rush and the line its user is not drawn for this camera, and the shots after this one do not even look
 * at where they stand (2.66.1, user request: "그냥 심판하는건 나의 스탠드다 할때도 플레이어 보여주지마셈").</li>
 * <li>From the side, the two of them across the frame, tipped hard; "俺の" (MINE) closer on their two faces, tipped the other way.</li>
 * <li>"スタンド": close on the stand drawing back for its last blow, from in front of it to its left, low, tipped.</li>
 * <li>"DA!" (FINAL): its fist into their face, close from their side.</li>
 * <li>Sent flying: low off to the stand's left, the stand near with its arm out after them, them flying off.</li>
 * <li>Their flight and fall: off to their side, travelling after them half as far, turning to follow them down to where they lie.</li>
 * </ol>
 */
public final class StandBeatdownCamera {
    /** A shot at a moment: the eye, what it looks at, its lens, its roll, whether the eye is inside the one struck (they are not drawn) and how hard the blows shake it. */
    public record Shot(Vec3d eye,Vec3d look,double fov,float roll,boolean inside,float shake){
        Shot(Vec3d eye,Vec3d look,double fov,float roll){this(eye,look,fov,roll,false,1);}
    }
    /** A blow's jolt on the camera: how far the eye is shaken (blocks, to the right and up of the view), its roll (degrees) and how far the lens closes in (degrees). */
    public record Jolt(double right,double up,float roll,double lens){
        static final Jolt NONE=new Jolt(0,0,0,0);
    }
    private StandBeatdownCamera(){}

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** How far through the shot from {@code a} to {@code b} {@code t} is, eased. */
    static float u(float t,float a,float b){return smooth((t-a)/(b-a));}

    /** The one struck's head as shown (blocks: their eyes' height, leaning back about their middle as they lean). */
    static Vec3d head(Beat b,Body v){
        double l=Math.toRadians(v.lean());
        return v.feet().add(0,MIDDLE+(EYES-MIDDLE)*Math.cos(l),0).add(b.f.multiply((EYES-MIDDLE)*Math.sin(l)));
    }
    /** How high a body's eyes are (blocks over its feet). */
    static final double EYES=1.62;
    /** The stand's face (where its mouth would be, as its rush's stances have it) and its head's middle, over its feet. */
    static Vec3d face(Beat b,Vec3d feet){return feet.add(b.f.multiply(.45)).add(0,1.7,0);}
    static Vec3d crown(Beat b,Vec3d feet){return feet.add(b.f.multiply(.3)).add(0,1.85,0);}

    /** The shot at {@code t} on the beatdown's clock (before any blow's jolt). */
    public static Shot at(Beat b,float t){
        Vec3d o=b.from,f=b.f,r=b.r;
        Body v=victim(b,Math.min(t,FINAL-1e-4f),false);
        // the one struck's chest as shown
        Vec3d chest=v.feet().add(0,1.15,0);
        double d=v.feet().subtract(o).dotProduct(f);
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
        // from the rush on: where the stand floats in front of them (its feet, face and head) and their head and chest
        Vec3d s=standFeet(b,t),face=face(b,s),crown=crown(b,s),vh=head(b,v),vc=v.feet().add(0,1.2,0);
        if(t<CUTS[7]){
            // "ORA!", its first fists: close on its face from in front of it to its left and a little low, its shoulder
            // near in the frame, tipped (the video's first shot; out to the side of its afterimages' fists), pushing in
            float k=u(t,CUTS[6],CUTS[7]);
            return new Shot(s.add(f.multiply(1.0)).add(r.multiply(-1.45+.15*k)).add(0,1.4,0),face.add(0,-.05,0),50-4*k,-12-3*k,false,1.2f);
        }
        if(t<CUTS[8]){
            // its fists into the lens: from the one struck's eyes (they are not drawn), a little behind them, the fists
            // stopping short of the camera as they land on the face it is in
            float k=u(t,CUTS[7],CUTS[8]);
            return new Shot(vh.add(f.multiply(.12)).add(0,.02,0),face.add(0,-.1,0),74-6*k,-8,true,2f);
        }
        if(t<CUTS[9]){
            // from just behind their eyes and a little over them (its face seen over its fists at their face): the
            // stand from its knees to its hair rushing them, its fists flying at the camera from every side, the frame
            // tipped and rocking, pushing in
            float k=u(t,CUTS[8],CUTS[9]),rock=(float)Math.sin((t-CUTS[8])*Math.PI*2/.9f);
            return new Shot(vh.add(f.multiply(.42-.2*k)).add(r.multiply(.06)).add(0,.22,0),face.add(0,-.12,0),64-6*k,-13+4*rock,true,2.2f);
        }
        if(t<CUTS[10]){
            // high over the stand's right shoulder (above its afterimages' arms), down onto its fists hammering them
            float k=u(t,CUTS[9],CUTS[10]);
            return new Shot(s.add(f.multiply(.05+.15*k)).add(r.multiply(.8-.1*k)).add(0,2.55,0),vc.subtract(f.multiply(.25)),52-4*k,6,false,1.3f);
        }
        if(t<CUTS[11]){
            // from beside the stand's head (above its arms, out past its mane): their face being beaten, the head
            // snapping with every fist
            float k=u(t,CUTS[10],CUTS[11]);
            return new Shot(crown.add(r.multiply(.62)).add(f.multiply(.2+.1*k)).add(0,.4,0),vh.add(0,-.15,0),54-6*k,10,false,1.4f);
        }
        if(t<CUTS[12]){
            // "裁くのは": over the back of the one struck (their head in the near corner), down at its face beyond them,
            // glaring over its fists as they all come in on them, pushing in slowly (2.66.1, user request: "그냥
            // 심판하는건 나의 스탠드다 할때도 플레이어 보여주지마셈": its user, behind the stand, is not drawn
            // (StandBeatdown.hidesUser); 2.66.0 showed them pointing up from beside them. First tries at the stand
            // from its sides and from under it showed little but its afterimages' arms: they close in on the one
            // struck, so from behind them its face is clear)
            float k=u(t,CUTS[11],CUTS[12]);
            return new Shot(vh.add(f.multiply(.95-.12*k)).add(r.multiply(.62)).add(0,.95,0),face.add(0,.05,0),50-4*k,6,false,1f);
        }
        if(t<CUTS[13]){
            // from the side, the two of them across the frame (the one struck on the left), the frame tipped hard; out
            // past its afterimages' arms, a longer lens; from a little further back along the blow, looking a little on
            // (2.66.1: its user, on the ground behind the stand, out past the edge of the frame)
            float k=u(t,CUTS[12],CUTS[13]);
            Vec3d mid=face.lerp(vh,.5);
            return new Shot(mid.add(r.multiply(-3.4+.3*k)).subtract(f.multiply(.8)).add(0,-.2,0),mid,46-3*k,22,false,1f);
        }
        if(t<CUTS[14]){
            // "俺の": closer on their two faces from the side, tipped the other way (still out past its afterimages'
            // arms: a long lens, their fists in front of it a blur)
            float k=u(t,CUTS[13],CUTS[14]);
            Vec3d mid=face.lerp(vh,.5);
            return new Shot(mid.add(r.multiply(-2.5+.15*k)).add(f.multiply(.1)).add(0,.2,0),mid.add(0,.1,0),38-4*k,-16,false,1.2f);
        }
        if(t<CUTS[15]){
            // "スタンド": close on the stand drawing back for its last blow, from in front of it to its right and low
            // (its left arm, out at them, hid its face from the left), tipped further as it coils, the lens closing as it strikes
            float k=u(t,CUTS[14],CUTS[15]);
            return new Shot(s.add(f.multiply(1.1)).add(r.multiply(1.25)).add(0,1.1,0),s.add(f.multiply(.1)).add(0,1.75,0),52-4*k-4*u(t,FINAL-.2f,FINAL),14+6*k,false,1f);
        }
        if(t<CUTS[16]){
            // "DA!": its fist into their face, close from their right (its head comes forward on its left as it
            // throws it), turning a little after them as they go
            Vec3d at=head(b,launch(b)),fly=head(b,flight(b,t));
            return new Shot(at.add(r.multiply(1.45)).subtract(f.multiply(.45)).add(0,.12,0),at.subtract(f.multiply(.2)).lerp(fly,.2),50,8,false,1f);
        }
        if(t<CUTS[17]){
            // sent flying: low beside the stand on its left, its arm out after them across the frame, them flying off
            // away to the left (the video's last shot)
            Vec3d fly=flight(b,t).feet().add(0,.9,0);
            return new Shot(s.add(r.multiply(-2.6)).add(0,.9,0),s.add(f.multiply(1.6)).add(0,1.3,0).lerp(fly,.25*u(t,CUTS[16],CUTS[17])),60,0,false,1f);
        }
        // their flight and fall: off to their left and above, the eye travelling half as far as they fly and a moment
        // behind them (so it never moves as fast as they do, turning after them instead), coming to rest beside where
        // they lie; looking at them no lower than a little over the ground (lying, the middle of their body is shown
        // lowered into it, and the eye, kept out of the walls along the way from what it looks at, was pulled right
        // down onto the floor: 2.66.0's first capture)
        Vec3d body=flight(b,Math.min(t,END)).feet().add(0,.6,0),from=launch(b).feet().add(0,.6,0);
        body=new Vec3d(body.x,Math.max(body.y,b.land.y+.45),body.z);
        Vec3d behind=flight(b,Math.max(FINAL,Math.min(t,END)-.15f)).feet().add(0,.6,0);
        return new Shot(from.lerp(behind,.5).add(r.multiply(-5.4)).subtract(f.multiply(.6)).add(0,1.5,0),body,58,0);
    }

    /**
     * Every blow's jolt on the camera at {@code t}: the shake, its roll and the lens punching in, each in over two
     * frames; the rush's at each fist as it lands, shaking the shots from the one struck's eyes harder (the shot's shake).
     */
    public static Jolt jolt(Beat b,float t){
        // how hard each moment shakes it (blocks) and punches in the lens (degrees)
        float shake=.07f*kick(t,CUT,.18f)+.035f*kick(t,REVEAL,.25f)+.03f*kick(t,GRAB,.12f)+.09f*kick(t,UPPER,.2f)
            +.16f*kick(t,FINAL,.26f)+.07f*kick(t,LAND,.2f);
        double lens=6*kick(t,CUT,.2f)+3*kick(t,GRAB,.15f)+6*kick(t,UPPER,.2f)+11*kick(t,FINAL,.3f)+4*kick(t,LAND,.15f);
        int n=fists(t);
        if(n>0&&t<RUSH_TO+.3f){
            float hard=at(b,t).shake(),rush=0;
            for(int k=Math.max(0,n-3);k<n;k++)rush+=kick(t,fistAt(k),.06f);
            shake+=.022f*hard*rush;lens+=1.1*Math.sqrt(hard)*rush;
        }
        if(shake<=1e-5&&lens<=1e-5)return Jolt.NONE;
        double s=t*Math.PI*2;
        double x=.6*Math.sin(s*13.1+1.3)+.4*Math.sin(s*7.3+.4),y=.6*Math.sin(s*11.3+2.1)+.4*Math.sin(s*8.9+.9),z=Math.sin(s*9.7+.2);
        return new Jolt(shake*x,shake*y,(float)(shake*z*25),lens);
    }
}
