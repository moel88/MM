package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.kageno.R6.Pose;

/** Keyframed R6 clip. Key times are in seconds of the 60 fps reference footage. */
public final class Anim {
    public enum Ease{LINEAR,SMOOTH,OUT,IN,HOLD,SNAP}
    public record Key(float sec,Pose pose,Ease ease){}
    private final Key[] keys;
    private Anim(Key[] keys){this.keys=keys;}
    public static Key k(double sec,Pose pose){return new Key((float)sec,pose,Ease.SMOOTH);}
    public static Key k(double sec,Pose pose,Ease ease){return new Key((float)sec,pose,ease);}
    public static Anim of(Key... keys){return new Anim(keys);}
    public float length(){return keys[keys.length-1].sec;}
    static float ease(Ease e,float t){
        t=Math.max(0,Math.min(1,t));
        return switch(e){
            case LINEAR->t;
            case SMOOTH->t*t*(3-2*t);
            case OUT->1-(1-t)*(1-t)*(1-t);
            case IN->t*t*t;
            case HOLD->0;
            case SNAP->t<1?0:1;
        };
    }
    /** Pose at {@code sec}; the ease of the destination key shapes each segment. */
    public Pose pose(float sec){
        if(sec<=keys[0].sec)return keys[0].pose;
        for(int i=1;i<keys.length;i++)if(sec<keys[i].sec){
            Key a=keys[i-1],b=keys[i];
            return Pose.lerp(a.pose,b.pose,ease(b.ease,(sec-a.sec)/(b.sec-a.sec)));
        }
        return keys[keys.length-1].pose;
    }
    public R6.Frame frame(float sec){return R6.solve(pose(sec));}
}
