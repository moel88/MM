package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.kageno.R6.Pose;
import dev.sangwoo.celestialfall.combat.MotionPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Keyframed R6 clip. Key times are in seconds of the cast clock (the 60 fps reference footage).
 *
 * Every key is solved once into joint rotations. Between keys each joint follows a cubic Bezier curve on the
 * rotation sphere and the root and the wrist follow cubic Hermite curves, so a frame is a pure function of time
 * that is evaluated per render frame. A key's ease shapes the segment that ends at it by fixing the speed at its
 * ends, in units of the segment's mean speed: SMOOTH starts and settles at rest, LINEAR keeps an even speed, OUT
 * bursts out (3x) and settles, IN starts at rest and arrives at speed (3x); with nothing else these reproduce the
 * classic ease curves exactly. Where the motion carries on through a key, the key is passed through at a shared
 * speed instead of stopping there: between two flowing segments (SMOOTH or LINEAR) each channel takes a clamped
 * auto tangent that is zero where that channel reverses (the extremes still settle, nothing overshoots), and a
 * SMOOTH side next to a fixed one inherits its speed when both run the same way (an arrival at speed carries on into
 * the follow-through; LINEAR keeps its own even speed). HOLD and SNAP keep the previous pose and then move into the key within the last two frames
 * before it, so nothing jumps; CUT jumps at the key and is only for camera cuts. Hit-stops ({@link Stop}) let the
 * clip nearly freeze for a moment right after an impact and catch up again, leaving the impact itself on time.
 */
public final class Anim {
    public enum Ease{LINEAR,SMOOTH,OUT,IN,HOLD,SNAP,CUT}
    public record Key(float sec,Pose pose,Ease ease){}
    /** A hit-stop: from {@code sec} the clip runs at {@link #CREEP} speed for {@code hold} s, then catches up over {@code recover} s. */
    public record Stop(float sec,float hold,float recover){}
    /** HOLD and SNAP move into their key within this long (two frames at 60 fps). */
    static final float QUICK=1/30f;
    /** Speed of the clip during a hit-stop. */
    static final float CREEP=.12f;
    private static final int JOINTS=6,SCALARS=7;
    private final Key[] keys;
    private final float[] times;
    private final R6.Frame[] frames;
    /** Joint rotations per key (each on the same hemisphere as the key before) and the scalars grip, fw, up, rt, yaw, pitch, roll. */
    private final Quaternionf[][] rot;
    private final float[][] val;
    /** Segment i runs from key i-1 to key i: its Bezier controls per joint and Hermite tangents (per second) per scalar. */
    private final Quaternionf[][] c1,c2;
    private final float[][] m0,m1;
    private final Stop[] stops;
    /** Applied to every frame sampled, or null ({@link #after}). */
    private final java.util.function.UnaryOperator<R6.Frame> after;
    private float memoSec=Float.NaN;private R6.Frame memo;

    private Anim(Key[] keys){this(keys,null);}
    private Anim(Key[] keys,R6.Frame[] solved){
        this.keys=keys.clone();int n=keys.length;
        times=new float[n];frames=new R6.Frame[n];rot=new Quaternionf[n][JOINTS];val=new float[n][];
        for(int k=0;k<n;k++){
            times[k]=keys[k].sec;frames[k]=solved!=null?solved[k]:R6.solve(keys[k].pose);var f=frames[k];
            for(int j=0;j<JOINTS;j++){
                rot[k][j]=quat(f.joints().joint[j]);
                if(k>0&&rot[k][j].dot(rot[k-1][j])<0)rot[k][j].set(-rot[k][j].x,-rot[k][j].y,-rot[k][j].z,-rot[k][j].w);
            }
            val[k]=new float[]{f.grip(),(float)f.fw(),(float)f.up(),(float)f.rt(),f.yaw(),f.pitch(),f.roll()};
        }
        // Mean speeds of every segment: scalar slopes and joint angular velocities (rotation vectors per second).
        float[][] slope=new float[n][SCALARS];Vector3f[][] omega=new Vector3f[n][JOINTS];
        for(int i=1;i<n;i++){
            float h=times[i]-times[i-1];
            for(int c=0;c<SCALARS;c++)slope[i][c]=h>1e-6f?(val[i][c]-val[i-1][c])/h:0;
            for(int j=0;j<JOINTS;j++)omega[i][j]=h>1e-6f?log(new Quaternionf(rot[i][j]).mul(new Quaternionf(rot[i-1][j]).conjugate())).div(h):new Vector3f();
        }
        m0=new float[n][SCALARS];m1=new float[n][SCALARS];
        Vector3f[][] w0=new Vector3f[n][JOINTS],w1=new Vector3f[n][JOINTS];
        // Resolve the speed at every key from the eases on both sides of it.
        for(int k=0;k<n;k++){
            boolean in=k>=1,out=k+1<n;Ease ei=in?keys[k].ease:null,eo=out?keys[k+1].ease:null;
            boolean inFixed=in&&fixed(ei),outFixed=out&&fixed(eo),both=in&&out;
            float hi=in?times[k]-times[k-1]:0,ho=out?times[k+1]-times[k]:0;
            boolean takeIn=both&&inFixed&&eo==Ease.SMOOTH,takeOut=both&&outFixed&&ei==Ease.SMOOTH;
            for(int c=0;c<SCALARS;c++){
                float si=in?slope[k][c]:0,so=out?slope[k+1][c]:0,end=in?endScale(ei)*si:0,start=out?startScale(eo)*so:0;
                if(both&&!inFixed&&!outFixed)end=start=auto(si,so,hi,ho);
                else if(takeIn){Float f=carry(end,so);if(f!=null)start=f;}
                else if(takeOut){Float f=carry(start,si);if(f!=null)end=f;}
                if(in)m1[k][c]=end;if(out)m0[k+1][c]=start;
            }
            for(int j=0;j<JOINTS;j++){
                Vector3f si=in?omega[k][j]:new Vector3f(),so=out?omega[k+1][j]:new Vector3f();
                Vector3f end=new Vector3f(si).mul(in?endScale(ei):0),start=new Vector3f(so).mul(out?startScale(eo):0);
                if(both&&!inFixed&&!outFixed){end=auto(si,so);start=new Vector3f(end);}
                else if(takeIn){Vector3f f=carry(end,so);if(f!=null)start=f;}
                else if(takeOut){Vector3f f=carry(start,si);if(f!=null)end=f;}
                if(in)w1[k][j]=end;if(out)w0[k+1][j]=start;
            }
        }
        c1=new Quaternionf[n][JOINTS];c2=new Quaternionf[n][JOINTS];
        for(int i=1;i<n;i++){
            float h=times[i]-times[i-1];
            for(int j=0;j<JOINTS;j++){
                c1[i][j]=exp(new Vector3f(w0[i][j]).mul(h/3)).mul(rot[i-1][j]);
                c2[i][j]=exp(new Vector3f(w1[i][j]).mul(-h/3)).mul(rot[i][j]);
            }
        }
        stops=new Stop[0];after=null;
    }
    private Anim(Anim a,Stop[] stops,java.util.function.UnaryOperator<R6.Frame> after){
        keys=a.keys;times=a.times;frames=a.frames;rot=a.rot;val=a.val;c1=a.c1;c2=a.c2;m0=a.m0;m1=a.m1;this.stops=stops;this.after=after;
    }
    public static Key k(double sec,Pose pose){return new Key((float)sec,pose,Ease.SMOOTH);}
    public static Key k(double sec,Pose pose,Ease ease){return new Key((float)sec,pose,ease);}
    public static Anim of(Key... keys){return new Anim(keys);}
    public static Anim joints(Key... keys){return new Anim(keys);}
    /** A clip straight from solved frames at the given times, every key SMOOTH (breakdowns flow, extremes settle). */
    static Anim frames(float[] times,R6.Frame[] frames){
        Key[] keys=new Key[times.length];for(int i=0;i<keys.length;i++)keys[i]=new Key(times[i],null,Ease.SMOOTH);
        return new Anim(keys,frames.clone());
    }
    public static Stop stop(double sec,double hold,double recover){return new Stop((float)sec,(float)hold,(float)recover);}
    /** The same clip with hit-stops (impacts at their own times, in order, not overlapping). */
    public Anim stops(Stop... stops){
        var s=stops.clone();java.util.Arrays.sort(s,(a,b)->Float.compare(a.sec,b.sec));
        for(int i=1;i<s.length;i++)if(s[i].sec<s[i-1].sec+s[i-1].hold+s[i-1].recover)throw new IllegalArgumentException("hit-stops overlap at "+s[i].sec);
        return new Anim(this,s,after);
    }
    /**
     * The same clip with {@code fix} applied to every frame it gives (it must not change the frame it is given; 2.77.3:
     * Lee So-sang's feet kept out of the ground between keys).
     */
    public Anim after(java.util.function.UnaryOperator<R6.Frame> fix){return new Anim(this,stops,fix);}
    public Stop[] stops(){return stops.clone();}
    public float length(){return times[times.length-1];}
    /** Key times (seconds). */
    public float[] times(){return times.clone();}
    public Ease ease(int key){return keys[key].ease;}
    /** The authored pose of key {@code i}, solved (a copy). */
    public R6.Frame key(int i){return copy(frames[i]);}

    /** The classic ease curves, kept for callers that shape their own timing. */
    static float ease(Ease e,float t){
        t=Math.max(0,Math.min(1,t));
        return switch(e){
            case LINEAR->t;
            case SMOOTH->t*t*(3-2*t);
            case OUT->1-(1-t)*(1-t)*(1-t);
            case IN->t*t*t;
            case HOLD,CUT->0;
            case SNAP->t<1?0:1;
        };
    }
    /** Speed at the start / end of a segment with this ease, in units of its mean speed (the fallback when nothing flows through). */
    private static float startScale(Ease e){return e==Ease.LINEAR?1:e==Ease.OUT?3:0;}
    private static float endScale(Ease e){return e==Ease.LINEAR?1:e==Ease.IN?3:0;}
    /** Eases whose end speeds are part of the look (bursts, arrivals, holds, cuts); SMOOTH and LINEAR ends may flow. */
    private static boolean fixed(Ease e){return e!=Ease.SMOOTH&&e!=Ease.LINEAR;}

    /** Monotone (Fritsch-Carlson) tangent through a key: zero where the channel turns back, never overshooting. */
    static float auto(float s1,float s2,float h1,float h2){
        if(s1*s2<=0)return 0;
        float w1=2*h2+h1,w2=h2+2*h1;
        return (w1+w2)/(w1/s1+w2/s2);
    }
    /** The same for an angular velocity: zero where the joint turns back, slower where its axis bends. */
    static Vector3f auto(Vector3f a,Vector3f b){
        float la=a.length(),lb=b.length(),d=a.dot(b);
        if(la<1e-6f||lb<1e-6f||d<=0)return new Vector3f();
        float cos=d/(la*lb);
        Vector3f dir=new Vector3f(a).div(la).add(new Vector3f(b).div(lb)).normalize();
        return dir.mul(2*la*lb/(la+lb)*cos);
    }
    /** A fixed speed carried into a soft neighbour that runs the same way, limited to 3x that neighbour's mean speed; null if it turns back. */
    static Float carry(float v,float s){return v*s>0?Math.copySign(Math.min(Math.abs(v),3*Math.abs(s)),v):null;}
    static Vector3f carry(Vector3f v,Vector3f s){
        if(v.dot(s)<=0)return null;
        float lv=v.length(),ls=s.length();
        return lv>3*ls?new Vector3f(v).mul(3*ls/lv):new Vector3f(v);
    }

    static Quaternionf quat(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}
    /** Rotation vector (axis times angle, the short way) of a unit quaternion. */
    static Vector3f log(Quaternionf q){
        float w=q.w,x=q.x,y=q.y,z=q.z;if(w<0){w=-w;x=-x;y=-y;z=-z;}
        float s=(float)Math.sqrt(x*x+y*y+z*z);
        if(s<1e-7f)return new Vector3f(2*x,2*y,2*z);
        float angle=2*(float)Math.atan2(s,w);
        return new Vector3f(x,y,z).mul(angle/s);
    }
    static Quaternionf exp(Vector3f v){
        float a=v.length();if(a<1e-9f)return new Quaternionf();
        return new Quaternionf().rotationAxis(a,v.x/a,v.y/a,v.z/a);
    }
    /** Cubic Bezier on the rotation sphere (de Casteljau with slerps). */
    static Quaternionf bezier(Quaternionf p0,Quaternionf p1,Quaternionf p2,Quaternionf p3,float u){
        var a=new Quaternionf(p0).slerp(p1,u);var b=new Quaternionf(p1).slerp(p2,u);var c=new Quaternionf(p2).slerp(p3,u);
        a.slerp(b,u);b.slerp(c,u);return a.slerp(b,u).normalize();
    }
    static float hermite(float p0,float v0,float p1,float v1,float u){
        float u2=u*u,u3=u2*u;
        return (2*u3-3*u2+1)*p0+(u3-2*u2+u)*v0+(-2*u3+3*u2)*p1+(u3-u2)*v1;
    }

    /** Clip time at cast time {@code sec}: equal outside the hit-stops, held back during them. */
    public float warp(float sec){
        for(Stop s:stops){
            float a=sec-s.sec;if(a<0||a>=s.hold+s.recover)continue;
            float x=s.recover>0?Math.min(1,(a-s.hold)/s.recover):1,lag=a<s.hold?a*(1-CREEP):s.hold*(1-CREEP)*(1-x*x*(3-2*x));
            return sec-lag;
        }
        return sec;
    }
    /** Pose at {@code sec}, as the renderer shows it (for effects that read limb directions). */
    public Pose pose(float sec){return R6.poseOf(frame(sec));}
    /** Solved frame at {@code sec}; a fresh copy every call. */
    public R6.Frame frame(float sec){
        if(sec!=memoSec){memo=sample(warp(sec));if(after!=null)memo=after.apply(memo);memoSec=sec;}
        return copy(memo);
    }
    private R6.Frame sample(float t){
        int n=times.length;
        if(t<=times[0])return frames[0];
        if(t>=times[n-1])return frames[n-1];
        int lo=1,hi=n-1;
        while(lo<hi){int mid=(lo+hi)>>>1;if(times[mid]>t)hi=mid;else lo=mid+1;}
        int i=lo;float t0=times[i-1],t1=times[i],h=t1-t0;Ease e=keys[i].ease;
        if(e==Ease.CUT||h<=1e-6f)return frames[i-1];
        MotionPose m=new MotionPose();float[] s=new float[SCALARS];
        if(e==Ease.HOLD||e==Ease.SNAP){
            float q=Math.min(QUICK,h);if(t<t1-q)return frames[i-1];
            float u=(t-(t1-q))/q,w=u*u*(3-2*u);
            for(int j=0;j<JOINTS;j++)m.joint[j]=R6.euler(new Quaternionf(rot[i-1][j]).slerp(rot[i][j],w));
            for(int c=0;c<SCALARS;c++)s[c]=val[i-1][c]+(val[i][c]-val[i-1][c])*w;
        }else{
            float u=(t-t0)/h;
            for(int j=0;j<JOINTS;j++)m.joint[j]=R6.euler(bezier(rot[i-1][j],c1[i][j],c2[i][j],rot[i][j],u));
            for(int c=0;c<SCALARS;c++)s[c]=hermite(val[i-1][c],m0[i][c]*h,val[i][c],m1[i][c]*h,u);
        }
        return new R6.Frame(m,s[0],s[1],s[2],s[3],s[4],s[5],s[6]);
    }
    private static R6.Frame copy(R6.Frame f){
        return new R6.Frame(f.joints().copy(),f.grip(),f.fw(),f.up(),f.rt(),f.yaw(),f.pitch(),f.roll());
    }
}
