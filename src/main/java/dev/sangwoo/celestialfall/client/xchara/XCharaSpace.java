package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;

/**
 * X Chara's G's strange space (2.112.0), laid out from the user's video: where its two hang, its camera's shots, the
 * knives round its user, the violet spikes rising out of the dark and the glowing pink panels flying through it. All of
 * it is where its user stood ({@code from}) along the way they faced the one marked ({@code f}, horizontal), the one
 * marked across from them GAP away at their height (the video's two at the edges of its side view: as far apart as
 * fifteen times their height); every moment of it a function of its clock ({@code t}, ticks from the press).
 * <p>
 * The shots: its user raising the knife upright, from behind them (WARN to SHOT_B, the one marked not in it yet); the
 * two upside down facing each other, seen from the side, its user at the right of the frame (SHOT_B to SHOT_C, the panels
 * flying); the camera pulled back the same way, the knives round its user aimed at the one marked (from SHOT_C), until
 * the world is back (WORLD). At VANISH the two are gone from it, the knives left.
 */
public final class XCharaSpace {
    private XCharaSpace(){}
    /** Its dark ground (the video's: rgb 0, 0, 17). */
    public static final float[] SKY={0f,0f,17/255f};
    /** How far apart the two hang (blocks between their feet), and how high their middles are over their feet. */
    public static final double GAP=26,MIDDLE=.9;
    /** The side shots' distance from the middle between them (the video's: the two at its edges, then pulled back to a sixth of the frame in from them) and their lens. */
    public static final double SIDE_B=10.9,SIDE_C=15.4,FOV_SIDE=70,FOV_RAISE=64;
    /** The knives round its user: how many, and the ellipse they lie on (ahead of its user's middle, behind, up and down: the video's crescent, its middle just ahead of them, its horns far up and down behind). */
    public static final int KNIVES=22;
    public static final double KNIFE_AHEAD=6,KNIFE_BACK=4.9,KNIFE_UP=9,KNIFE_SPAN=84,KNIFE_LENGTH=2.0;

    /** A shot: where the eye is, what it looks at, its lens (degrees, up and down). */
    public record Shot(Vec3d eye,Vec3d look,double fov){}

    static Vec3d f(float yaw){return Vec3d.fromPolar(0,yaw);}
    /** Its user's left (the side the side shots are from: the one marked at the left of the frame, its user at the right). */
    static Vec3d left(Vec3d f){return new Vec3d(f.z,0,-f.x);}
    /** The middle between the two (their middles' height). */
    public static Vec3d middle(Vec3d from,float yaw){return from.add(f(yaw).multiply(GAP/2)).add(0,MIDDLE,0);}
    /** Where the one marked hangs: across from its user, at their height. */
    public static Vec3d targetFeet(Vec3d from,float yaw){return from.add(f(yaw).multiply(GAP));}
    static Vec3d targetFeet(XCharaClient.Dance d){return targetFeet(d.from,d.yaw);}

    /** Its camera at {@code t}. */
    public static Shot shot(Vec3d from,float yaw,float t){
        Vec3d f=f(yaw),l=left(f),r=l.negate(),up=new Vec3d(0,1,0);
        if(t<SHOT_B){
            // behind its user's right shoulder, looking up past them at the knife raised upright (pushing in a little)
            float u=smooth((t-WARN)/(SHOT_B-WARN));
            Vec3d eye=from.add(f.multiply(-1.75+.3*u)).add(r.multiply(1.05)).add(up.multiply(1.25));
            Vec3d look=from.add(f.multiply(1.3)).add(l.multiply(.35)).add(up.multiply(2.55));
            return new Shot(eye,look,FOV_RAISE);
        }
        Vec3d mid=middle(from,yaw);
        double side=t<SHOT_C?SIDE_B:SIDE_C;
        return new Shot(mid.add(l.multiply(side)),mid,FOV_SIDE);
    }
    static Shot shot(XCharaClient.Dance d,float t){return shot(d.from,d.yaw,t);}
    /** Whether one of the two is in the frame at {@code t}: its user from the strange space, the one marked from the second shot, both until they vanish. */
    public static boolean shown(boolean target,float t){return t>=(target?SHOT_B:WARN)&&t<VANISH;}
    static boolean shown(XCharaClient.Dance d,boolean target,float t){return shown(target,t);}
    /** Whether the two hang upside down at {@code t} (from the second shot). */
    public static boolean inverted(float t){return t>=SHOT_B;}
    /** How far its user has raised the knife upright (0 to 1): up as the strange space comes, down again as the second shot cuts in. */
    public static float raise(float t){return smooth((t-WARN)/4f)*(1-smooth((t-SHOT_B)/3f));}

    /** How strongly the knives show at {@code t}: there as the third shot cuts in, until the world is back. */
    public static float knives(float t){return t<SHOT_C||t>=WORLD?0:Math.min(1,(t-SHOT_C+1)/2f);}
    /** Where knife {@code k} is (its middle) and the way its point goes (at the one marked's middle). */
    public static Vec3d[] knife(Vec3d from,float yaw,int k){
        Vec3d f=f(yaw),l=left(f),c=from.add(0,MIDDLE,0);
        double jitter=hash(k,1)-.5,phi=Math.toRadians(-KNIFE_SPAN+2*KNIFE_SPAN*k/(KNIVES-1.0)+5*jitter);
        Vec3d at=c.add(f.multiply(KNIFE_AHEAD*Math.cos(phi)-KNIFE_BACK)).add(0,KNIFE_UP*Math.sin(phi),0).add(l.multiply(1.4*(hash(k,2)-.5)));
        Vec3d aim=targetFeet(from,yaw).add(0,MIDDLE,0).subtract(at).normalize();
        return new Vec3d[]{at,aim};
    }
    /** How big knife {@code k} is (of KNIFE_LENGTH). */
    public static double knifeSize(int k){return .9+.22*hash(k,3);}

    /** The violet spikes rising out of the dark: {x, base y, z, height, half its width}, around the middle, none by the cameras or the two. */
    public static double[][] spikes(Vec3d from,float yaw){
        Vec3d f=f(yaw),l=left(f),mid=middle(from,yaw);
        var out=new java.util.ArrayList<double[]>();
        for(int i=0;out.size()<SPIKES&&i<400;i++){
            double a=hash(i,11)*Math.PI*2,rad=8+36*Math.sqrt(hash(i,12));
            Vec3d p=mid.add(f.multiply(Math.cos(a)*rad)).add(l.multiply(Math.sin(a)*rad));
            boolean near=false;
            for(Vec3d keep:new Vec3d[]{mid.add(l.multiply(SIDE_B)),mid.add(l.multiply(SIDE_C)),from,targetFeet(from,yaw)})
                if(Math.hypot(p.x-keep.x,p.z-keep.z)<4.5)near=true;
            // nor between the side cameras and the two (they would stand across the frame)
            Vec3d rel=p.subtract(mid);double across=rel.dotProduct(l),along=rel.dotProduct(f);
            if(across>1&&across<SIDE_C+1&&Math.abs(along)<GAP*.5+2)near=true;
            if(near)continue;
            out.add(new double[]{p.x,from.y-3-6*hash(i,13),p.z,8+11*hash(i,14),.3+.65*hash(i,15)});
        }
        return out.toArray(new double[0][]);
    }
    public static final int SPIKES=46;
    /** How strongly the spikes show at {@code t}: from the strange space until the world is back. */
    public static float spikesShown(float t){return t<WARN||t>=WORLD?0:1;}

    /**
     * The glowing pink panels flying through the second shot: panel {@code k}'s middle, its half width and height, its
     * turn about the way it faces, and how strongly it shows at {@code t} (0 when it does not).
     */
    public static Panel panel(Vec3d from,float yaw,int k,float t){
        Vec3d f=f(yaw),l=left(f),mid=middle(from,yaw);
        float t0=SHOT_B+9*(float)hash(k,21),life=15+10*(float)hash(k,22),s=t-t0;
        float shown=s<0||s>life||t>=SHOT_B+PANEL_LIFE?0:Math.min(1,s/2f)*Math.min(1,(life-s)/4f)*(1-smooth((t-(SHOT_B+PANEL_LIFE-4))/4f));
        Vec3d at=mid.add(f.multiply(-11+22*hash(k,23))).add(0,-3.5+6*hash(k,24),0).add(l.multiply(1+5*hash(k,25)));
        Vec3d v=f.multiply(.5*(hash(k,26)-.5)).add(0,.12+.3*hash(k,27),0);
        return new Panel(at.add(v.multiply(Math.max(0,s))),.32+.3*hash(k,28),.55+.45*hash(k,29),(float)(hash(k,30)*6.28+s*.04*(hash(k,31)-.5)),shown);
    }
    public record Panel(Vec3d at,double halfWidth,double halfHeight,float turn,float shown){}
    public static final int PANELS=10;
    /** The panels fly until this long into the second shot (the video's: gone by 2.75 s). */
    public static final int PANEL_LIFE=29;

    static float smooth(float x){x=x<0?0:x>1?1:x;return x*x*(3-2*x);}
    /** A steady hash in [0, 1) of {@code i} and {@code salt} (the same on every client). */
    public static double hash(int i,int salt){
        long h=i*0x9E3779B97F4A7C15L+salt*0xC2B2AE3D27D4EB4FL;
        h^=h>>>31;h*=0xBF58476D1CE4E5B9L;h^=h>>>27;h*=0x94D049BB133111EBL;h^=h>>>33;
        return (h>>>11)*0x1.0p-53;
    }
}
