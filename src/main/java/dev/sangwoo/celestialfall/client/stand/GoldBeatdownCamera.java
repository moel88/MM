package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.stand.GoldBeatdown.Beat;
import dev.sangwoo.celestialfall.client.stand.StandBeatdown.Body;
import dev.sangwoo.celestialfall.client.stand.StandBeatdownCamera.Jolt;
import dev.sangwoo.celestialfall.client.stand.StandBeatdownCamera.Shot;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.stand.GoldBeatdown.*;

/**
 * The shots of Gold Experience's beatdown's cutscene (2.150.0), the video's ("비트다운도 영상을 참고해서 만들고"; its
 * first shot, the close-up of the eyes, left out as the user answered), cut where the video cuts, as its user and the
 * one struck see it: where the eye is, what it looks at, its lens, its roll, whether the eye is inside the one struck
 * (they are not drawn) and how hard the fists shake it (StandBeatdownCamera's Shot and Jolt). Each shot moves within
 * itself and cuts hard to the next. Laid out on the blow's line: its user's feet, the way it went and its right.
 * <ol>
 * <li>The punch lands (CUT): low beside the one struck, a moment.</li>
 * <li>"Muda muda" (the video's 0.63 to 4 s: the stand's fists and the face they strike, cut back and forth every few
 * frames): close on the one struck from their side, and the stand from in front of it, a cut with every fist.</li>
 * <li>From behind the one struck (4 to 7.5 s): over their shoulder, the stand throwing its fists at the lens.</li>
 * <li>Close on the one struck (7.5 to 10.5 s): their chest from over the stand's shoulder, then their face.</li>
 * <li>Close on the stand from the one struck's eyes (10.5 to 15.3 s): its fists and their afterimages straight into the lens.</li>
 * <li>"WRYYY" (15.3 to 20.2 s): low on the ground beside its user, looking up as the stand goes under the one struck and drives them up.</li>
 * <li>From over the one struck (20.2 to 28.3 s): down past their back at the top of the stand's head and its fists coming up; then closer.</li>
 * <li>The last blow (28.3 to 29.6 s): the stand from its side, small, its fist drawn far back; close on its face; its fist into the lens.</li>
 * <li>Sent flying (29.6 s on): from behind the stand after them, then beside their fall to where they lie.</li>
 * </ol>
 */
public final class GoldBeatdownCamera {
    private GoldBeatdownCamera(){}
    /** The shots' cuts (seconds on the beatdown's clock): the video's, a cut 0.633 s earlier than in it. */
    static final float C1=CUT+.1f,C3=rec(3.37f),C4=rec(6.87f),C4B=rec(8.6f),C5=rec(9.87f),C6=WRY,C7=WRY_END,C7B=rec(24.0f),C8=WIND,C8B=rec(28.27f),
        C8C=FINAL-.1f,C9=FINAL+.12f,C9B=FINAL+.7f;
    static float u(float t,float a,float b){return smooth((t-a)/(b-a));}
    /** The cuts in order (the first shot from CUT). */
    static final float[] CUTS={CUT,C1,C3,C4,C4B,C5,C6,C7,C7B,C8,C8B,C8C,C9,C9B};
    /** Which shot shows at {@code t}: its number among the cuts, the second ("muda muda") counted anew with every fist (each is a cut). */
    static int shot(float t){
        int shot=0;while(shot+1<CUTS.length&&t>=CUTS[shot+1])shot++;
        return shot==1?1000+fists(t):shot;
    }

    /** The shot at {@code t} on the beatdown's clock (before any blow's jolt). */
    public static Shot at(Beat b,float t){
        Vec3d o=b.from,f=b.f,r=b.r;
        Body v=victim(b,Math.min(t,FINAL-1e-4f),false);
        Vec3d vh=head(b,v),vc=chest(b,v);
        // (their head as they stand upright: the shots of the first of the rush are laid out by it, so the punch's snap
        // of their head back does not throw the camera with it)
        Vec3d vu=v.feet().add(0,EYES,0);
        if(t<C1){
            // the punch lands: low beside the one struck on their left
            double from=b.victimFrom.subtract(o).dotProduct(f);float k=u(t,CUT,C1);
            return new Shot(b.at(o,from*.6,.8,-2.3+.2*k),b.at(o,from*.6+.4,1.3,0),52+6*k,-5,false,1);
        }
        // the stand where it floats: its face, the top of its head and its chest
        Vec3d face=standPoint(b,t,.42,1.72),crown=standPoint(b,t,.2,1.95),sc=standPoint(b,t,.25,1.35);
        if(t<C3){
            // muda muda: a cut with every fist, between the face they strike and the stand throwing them
            float k=u(t,C1,C3);
            if((fists(t)&1)==0)return new Shot(vu.add(r.multiply(1.2-.1*k)).subtract(f.multiply(.8)).add(0,.05,0),vu.add(0,-.2,0),44-3*k,-9,false,1.5f);
            return new Shot(sc.add(f.multiply(1.15)).add(r.multiply(-1.35+.1*k)).add(0,.05,0),sc.add(0,.15,0),50-3*k,12,false,1.2f);
        }
        if(t<C4){
            // from behind the one struck, over their shoulder: the stand throwing its fists at the lens
            float k=u(t,C3,C4);
            // (the first take sat right behind their head: it filled half the frame, the stand small under it)
            // (and then behind their back, the stand hidden by it: now from over their right shoulder, further out)
            return new Shot(vu.add(f.multiply(1.5-.2*k)).add(r.multiply(1.55)).add(0,.75,0),face.add(0,-.05,0).lerp(vu,.2),56-5*k,-6,false,1.3f);
        }
        if(t<C4B){
            // their chest, from over the stand's right shoulder
            float k=u(t,C4,C4B);
            return new Shot(standPoint(b,t,.3,1.8).add(r.multiply(.72-.08*k)),vc,42-4*k,7,false,1.5f);
        }
        if(t<C5){
            // their face, close from beside the stand's fists
            float k=u(t,C4B,C5);
            return new Shot(vu.subtract(f.multiply(1.0-.1*k)).add(r.multiply(-.6)).add(0,.1,0),vu.add(0,-.05,0),36-3*k,-8,false,1.6f);
        }
        if(t<C6){
            // the stand from their eyes (they are not drawn): its fists and their afterimages straight into the lens
            float k=u(t,C5,C6),rock=(float)Math.sin((t-C5)*Math.PI*2/1.1f);
            return new Shot(vh.add(f.multiply(.14)).add(0,-.22,0),face.add(0,-.12,0),72-8*k,-6+4*rock,true,2f);
        }
        if(t<C7){
            // WRYYY: low on the ground beyond the one struck, looking back and up: the stand under them driving them up
            // and up, its user below arched back, falling out of the frame as they rise (the first take looked on from
            // behind its user: the two of them small in the sky past their back)
            float k=u(t,C6,C7);
            double d=v.feet().subtract(o).dotProduct(f);
            Vec3d look=sc.lerp(vc,.5);
            return new Shot(b.at(o,d+1.9+.4*k,.35,1.2),look,66-6*k,-10+5*k,false,1.3f);
        }
        if(t<C7B){
            // from over the one struck: down past their back at the top of the stand's head, its fists coming up
            float k=u(t,C7,C7B),rock=(float)Math.sin((t-C7)*Math.PI*2/1.6f);
            // (from past their feet and off to one side, so the stand shows past them: straight over them, the first
            // take showed their back and nothing of it)
            return new Shot(vc.add(f.multiply(1.9-.2*k)).add(r.multiply(1.5)).add(0,1.9,0),crown.lerp(vc,.3),62-4*k,6*rock,false,1.5f);
        }
        if(t<C8){
            // closer, off to one side
            float k=u(t,C7B,C8);
            return new Shot(vc.add(f.multiply(1.5)).add(r.multiply(-1.45+.1*k)).add(0,1.6-.2*k,0),crown.lerp(vc,.3),54-4*k,-10,false,1.6f);
        }
        if(t<C8B){
            // the last blow: the stand from its left side, small in the frame, its fist drawn far back, the frame tipped
            float k=u(t,C8,C8B);
            return new Shot(sc.add(r.multiply(-4.4+.4*k)).add(f.multiply(.6)).add(0,.2,0),sc.lerp(vc,.3),46-2*k,-18,false,1);
        }
        if(t<C8C){
            // close on its face over its drawn fist, from in front of it to its left
            float k=u(t,C8B,C8C);
            return new Shot(standPoint(b,t,1.15,1.35).add(r.multiply(-1.05+.1*k)),face,44-5*k,12+4*k,false,1);
        }
        if(t<C9){
            // its fist into the lens: from the one struck's eyes (looking where its face was before it dashed in: the
            // dash would swing the view)
            return new Shot(vh.add(f.multiply(.14)).add(0,.02,0),standPoint(b,Math.min(t,FINAL-.03f),.42,1.72).add(0,-.15,0),70,-6,true,2.4f);
        }
        if(t<C9B){
            // sent flying: from behind the stand on its left, after them
            Vec3d fly=flight(b,t).feet().add(0,.9,0);
            return new Shot(standPoint(b,t,-1.3,1.3).add(r.multiply(-1.5)),sc.lerp(fly,.35+.5*u(t,C9,C9B)),64,6,false,1);
        }
        // their flight and fall: off to their left, the eye travelling half as far as they fly and a moment behind them,
        // looking at them no lower than a little over the ground
        Vec3d body=flight(b,Math.min(t,END)).feet().add(0,.6,0),from=launch(b).feet().add(0,.6,0);
        body=new Vec3d(body.x,Math.max(body.y,b.land.y+.45),body.z);
        Vec3d behind=flight(b,Math.max(FINAL,Math.min(t,END)-.15f)).feet().add(0,.6,0);
        Vec3d eye=from.lerp(behind,.75).add(r.multiply(-5)).subtract(f.multiply(.6));
        return new Shot(new Vec3d(eye.x,Math.max(b.land.y+1.6,Math.min(eye.y+1.2,b.land.y+5)),eye.z),body,60,0,false,1);
    }

    /** Every blow's jolt on the camera at {@code t}: the punch, each of the rush's fists as it lands (harder in the shots from the one struck's eyes), the last blow, the fall. */
    public static Jolt jolt(Beat b,float t){
        float shake=.07f*kick(t,CUT,.18f)+.14f*kick(t,FINAL,.28f)+.07f*kick(t,LAND,.2f);
        double lens=6*kick(t,CUT,.2f)+12*kick(t,FINAL,.3f)+4*kick(t,LAND,.15f);
        int n=fists(t);
        if(n>0&&t<WIND+.3f){
            float hard=at(b,t).shake(),rush=0;
            for(int k=Math.max(0,n-3);k<n;k++)rush+=kick(t,fistAt(k),.06f);
            shake+=.02f*hard*rush;lens+=1.0*Math.sqrt(hard)*rush;
        }
        if(shake<=1e-5&&lens<=1e-5)return Jolt.NONE;
        double s=t*Math.PI*2;
        double x=.6*Math.sin(s*13.1+1.3)+.4*Math.sin(s*7.3+.4),y=.6*Math.sin(s*11.3+2.1)+.4*Math.sin(s*8.9+.9),z=Math.sin(s*9.7+.2);
        return new Jolt(shake*x,shake*y,(float)(shake*z*25),lens);
    }
}
