package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;

/**
 * X Chara's G's strange space (2.112.0 to 2.112.2), laid out from the user's video: where its two hang, its camera's
 * shots, the knives round its user and the glowing pink panels flying through it, on its plain dark ground (2.112.2, user
 * request: "이상한 공간 배경 그냥 이펙트 없이 단색 공간으로 만들어 보고": the violet spikes of 2.112.0 and the depths of
 * 2.112.1, stars, glows, a ring, embers, streaks, are gone). All of it is where its user stood ({@code from}) along the way they
 * faced the one marked ({@code f}, horizontal), the one marked across from them GAP away at their height (the video's two
 * at the edges of its side view: as far apart as fifteen times their height); every moment of it a function of its clock
 * ({@code t}, ticks from the press), the same on every client.
 * <p>
 * The shots (2.112.1, user request: "시전자가 칼을 세워드는 저거 없애고 곧바로 서로 거꾸로 마주보는 컷씬부터 시작": 2.112.0
 * opened on its user raising the knife, seen from behind them): the two upside down facing each other, seen from the
 * side, its user at the right of the frame (WARN to SHOT_C, the panels flying); the camera pulled back the same way, the
 * knives round its user aimed at the one marked (from SHOT_C), until the world is back (WORLD). At VANISH the two are gone
 * from it, the knives left.
 */
public final class XCharaSpace {
    private XCharaSpace(){}
    /** Its dark ground (the video's: rgb 0, 0, 17). */
    public static final float[] SKY={0f,0f,17/255f};
    /** How far apart the two hang (blocks between their feet), and how high their middles are over their feet. */
    public static final double GAP=26,MIDDLE=.9;
    /** The side shots' distance from the middle between them (the video's: the two at its edges, then pulled back to a sixth of the frame in from them) and their lens. */
    public static final double SIDE_B=10.9,SIDE_C=15.4,FOV_SIDE=70;

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

    /** Its camera at {@code t}: from the side the whole time, pulled back from SHOT_C. */
    public static Shot shot(Vec3d from,float yaw,float t){
        Vec3d mid=middle(from,yaw);
        double side=t<SHOT_C?SIDE_B:SIDE_C;
        return new Shot(mid.add(left(f(yaw)).multiply(side)),mid,FOV_SIDE);
    }
    static Shot shot(XCharaClient.Dance d,float t){return shot(d.from,d.yaw,t);}
    /** Whether one of the two is in the frame at {@code t}: both from the strange space until they vanish. */
    public static boolean shown(boolean target,float t){return t>=WARN&&t<VANISH;}
    static boolean shown(XCharaClient.Dance d,boolean target,float t){return shown(target,t);}
    /** Whether the two hang upside down at {@code t}: from the strange space's first moment (2.112.1). */
    public static boolean inverted(float t){return t>=WARN;}

    // ───────────────────────────── the knives ─────────────────────────────

    /**
     * The knives round its user (2.112.1, user request: "검들을 골고루 배치해줘 원본 영상 보면 검들이 골고루 배치되어 있잖아":
     * 2.112.0 laid them on a crescent). Spread evenly over a band about them as the video's are, seen from the side: rows
     * KNIFE_ROW apart from KNIFE_TOP over its user's middle down, three to a row KNIFE_ACROSS apart, every other row half
     * of that further on (a hexagonal lattice, a little more of it ahead of them than behind, KNIFE_SHIFT); none on its
     * user's body (the one there left out, and the last row's ahead too: 22, the video's), each a little off its place
     * and nearer or further from the side cameras.
     */
    public static final int KNIVES=22;
    public static final double KNIFE_TOP=9.4,KNIFE_ROW=2.55,KNIFE_ACROSS=2.8,KNIFE_SHIFT=.1,KNIFE_LENGTH=2.0;
    /** How far each knife is off its place: along the way, up and down, and toward the side cameras or away (blocks, either way). */
    public static final double KNIFE_JITTER=.35,KNIFE_JITTER_UP=.3,KNIFE_DEPTH=.8;
    /** Each knife's place before it is shaken off it: {ahead of its user's middle, over it} (blocks). */
    static final double[][] SLOTS=slots();
    private static double[][] slots(){
        var out=new java.util.ArrayList<double[]>();
        for(int row=0;row<8;row++)for(int c=0;c<3;c++){
            double ahead=(c-1)*KNIFE_ACROSS+(row%2==0?-.25:.25)*KNIFE_ACROSS+KNIFE_SHIFT,up=KNIFE_TOP-row*KNIFE_ROW;
            // its user's body (the knife's length either way of its middle): left out
            if(Math.abs(ahead)<1.5&&Math.abs(up)<1.3)continue;
            // the last row two: the one ahead left out
            if(row==7&&c==2)continue;
            out.add(new double[]{ahead,up});
        }
        return out.toArray(new double[0][]);
    }
    /** Where knife {@code k} is (its middle) and the way its point goes (at the one marked's middle). */
    public static Vec3d[] knife(Vec3d from,float yaw,int k){
        Vec3d f=f(yaw),l=left(f),c=from.add(0,MIDDLE,0);
        double[] slot=SLOTS[k];
        double ahead=slot[0]+2*KNIFE_JITTER*(hash(k,1)-.5),up=slot[1]+2*KNIFE_JITTER_UP*(hash(k,2)-.5),across=2*KNIFE_DEPTH*(hash(k,3)-.5);
        Vec3d at=c.add(f.multiply(ahead)).add(0,up,0).add(l.multiply(across));
        Vec3d aim=targetFeet(from,yaw).add(0,MIDDLE,0).subtract(at).normalize();
        return new Vec3d[]{at,aim};
    }
    /** How big knife {@code k} is (of KNIFE_LENGTH). */
    public static double knifeSize(int k){return .9+.22*hash(k,4);}
    /** How strongly the knives show at {@code t}: there as the pulled-back shot cuts in, until the world is back. */
    public static float knives(float t){return t<SHOT_C||t>=WORLD?0:Math.min(1,(t-SHOT_C+1)/2f);}

    // ───────────────────────────── the panels ─────────────────────────────

    /**
     * The glowing pink panels flying through the first shot: panel {@code k}'s middle, its half width and height, its
     * turn about the way it faces, and how strongly it shows at {@code t} (0 when it does not).
     */
    public static Panel panel(Vec3d from,float yaw,int k,float t){
        Vec3d f=f(yaw),l=left(f),mid=middle(from,yaw);
        float t0=WARN+9*(float)hash(k,21),life=15+10*(float)hash(k,22),s=t-t0;
        float shown=s<0||s>life||t>=WARN+PANEL_LIFE?0:Math.min(1,s/2f)*Math.min(1,(life-s)/4f)*(1-smooth((t-(WARN+PANEL_LIFE-4))/4f));
        Vec3d at=mid.add(f.multiply(-11+22*hash(k,23))).add(0,-3.5+6*hash(k,24),0).add(l.multiply(1+5*hash(k,25)));
        Vec3d v=f.multiply(.5*(hash(k,26)-.5)).add(0,.12+.3*hash(k,27),0);
        return new Panel(at.add(v.multiply(Math.max(0,s))),.32+.3*hash(k,28),.55+.45*hash(k,29),(float)(hash(k,30)*6.28+s*.04*(hash(k,31)-.5)),shown);
    }
    public record Panel(Vec3d at,double halfWidth,double halfHeight,float turn,float shown){}
    public static final int PANELS=10;
    /** The panels fly until this long into the strange space (the video's: gone by 2.75 s, 1.4 s into its side shot). */
    public static final int PANEL_LIFE=29;

    static float smooth(float x){x=x<0?0:x>1?1:x;return x*x*(3-2*x);}
    /** A steady hash in [0, 1) of {@code i} and {@code salt} (the same on every client). */
    public static double hash(int i,int salt){
        long h=i*0x9E3779B97F4A7C15L+salt*0xC2B2AE3D27D4EB4FL;
        h^=h>>>31;h*=0xBF58476D1CE4E5B9L;h^=h>>>27;h*=0x94D049BB133111EBL;h^=h>>>33;
        return (h>>>11)*0x1.0p-53;
    }
}
