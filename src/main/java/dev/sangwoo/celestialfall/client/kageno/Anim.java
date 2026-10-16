package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.kageno.R6.Pose;

/** Keyframed R6 clip. Key times are in seconds of the 60 fps reference footage. */
public final class Anim {
    public enum Ease{LINEAR,SMOOTH,OUT,IN,HOLD,SNAP}
    public record Key(float sec,Pose pose,Ease ease){}
    private final Key[] keys;
    private final R6.Frame[] frames;
    private final boolean jointBlend;
    private Anim(Key[] keys,boolean jointBlend){
        this.jointBlend=jointBlend;
        this.keys=keys.clone();frames=new R6.Frame[keys.length];
        for(int i=0;i<keys.length;i++)frames[i]=R6.solve(keys[i].pose);
    }
    public static Key k(double sec,Pose pose){return new Key((float)sec,pose,Ease.SMOOTH);}
    public static Key k(double sec,Pose pose,Ease ease){return new Key((float)sec,pose,ease);}
    /** Original direction-space sampling, retained for skills whose motion was not requested to change. */
    public static Anim of(Key... keys){return new Anim(keys,false);}
    /** Explicit opt-in for the remade M1/E/T/H/G clips only. */
    public static Anim joints(Key... keys){return new Anim(keys,true);}
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
            float t=ease(b.ease,(sec-a.sec)/(b.sec-a.sec));
            return jointBlend?Pose.lerp(a.pose,b.pose,t):Pose.lerpLegacy(a.pose,b.pose,t);
        }
        return keys[keys.length-1].pose;
    }
    public R6.Frame frame(float sec){
        if(!jointBlend)return R6.solve(pose(sec));
        if(sec<=keys[0].sec)return R6.blend(frames[0],frames[0],0);
        for(int i=1;i<keys.length;i++)if(sec<keys[i].sec){
            Key a=keys[i-1],b=keys[i];
            return R6.blend(frames[i-1],frames[i],ease(b.ease,(sec-a.sec)/(b.sec-a.sec)));
        }
        return R6.blend(frames[frames.length-1],frames[frames.length-1],0);
    }
}
