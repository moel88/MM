package dev.sangwoo.celestialfall.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.joml.Quaternionf;

/**
 * Crossfades the articulated player model whenever the source of its pose changes (a skill starts or ends, a
 * punch, a dash, a hit reaction, the feet leave or touch the ground, an item swing), so no motion pops into the
 * next. The fade starts from the pose that was actually on screen and runs on the entity's own clock, so several
 * model updates in one frame agree.
 */
public final class PoseBlend {
    private PoseBlend(){}
    /** Head, body, right arm, left arm, right leg, left leg: pitch, yaw, roll (radians) and pivot x, y, z; then the elbows and knees. */
    private static final int PARTS=6,BENDS=4;
    private static final class State{Object source;float switched,duration,lastAt=Float.NEGATIVE_INFINITY;float[][] from,last;}
    private static final Map<UUID,State> STATES=new HashMap<>();
    public static void clear(){STATES.clear();}
    public static void forget(UUID id){STATES.remove(id);}

    /**
     * Shows the pose now in {@code m} for {@code source}; after a change of source it fades in from the pose shown
     * last over {@code duration} ticks. {@code now} is the entity's age plus the frame's tick fraction.
     */
    public static void apply(UUID id,BipedEntityModel<?> m,Object source,float duration,float now){
        float[][] current=capture(m);
        State s=STATES.computeIfAbsent(id,k->new State());
        if(!Objects.equals(source,s.source)){
            // Only fade from a pose that was on screen a moment ago (not one left over from long before).
            s.from=s.last!=null&&now-s.lastAt>=0&&now-s.lastAt<5?s.last:null;
            s.switched=now;s.source=source;s.duration=duration;
        }
        float[][] out=current;
        if(s.from!=null&&now>=s.switched&&now-s.switched<s.duration){
            float u=(now-s.switched)/s.duration,w=u*u*(3-2*u);
            out=blend(s.from,current,w);write(m,out);
        }else s.from=null;
        s.last=out;s.lastAt=now;
    }
    private static ModelPart[] parts(BipedEntityModel<?> m){return new ModelPart[]{m.head,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg};}
    private static ModelPart[] bends(BipedEntityModel<?> m){
        return new ModelPart[]{m.rightArm.getChild("elbow"),m.leftArm.getChild("elbow"),m.rightLeg.getChild("knee"),m.leftLeg.getChild("knee")};
    }
    /** Every part's angles and pivot, then the elbows' and knees' bend, as shown now. */
    public static float[][] capture(BipedEntityModel<?> m){
        float[][] out=new float[PARTS+BENDS][];var p=parts(m);var b=bends(m);
        for(int i=0;i<PARTS;i++)out[i]=new float[]{p[i].pitch,p[i].yaw,p[i].roll,p[i].pivotX,p[i].pivotY,p[i].pivotZ};
        for(int i=0;i<BENDS;i++)out[PARTS+i]=new float[]{b[i].pitch};
        return out;
    }
    /** Puts angles and pivots taken by {@link #capture} back on the model. */
    public static void write(BipedEntityModel<?> m,float[][] v){
        var p=parts(m);var b=bends(m);
        for(int i=0;i<PARTS;i++){p[i].pitch=v[i][0];p[i].yaw=v[i][1];p[i].roll=v[i][2];p[i].pivotX=v[i][3];p[i].pivotY=v[i][4];p[i].pivotZ=v[i][5];}
        for(int i=0;i<BENDS;i++)b[i].pitch=v[PARTS+i][0];
    }
    private static float[][] blend(float[][] a,float[][] b,float w){
        float[][] out=new float[a.length][];
        for(int i=0;i<PARTS;i++){
            // Rotations along the short arc (no wrap-around spins), pivots straight across.
            var q=new Quaternionf().rotationZYX(a[i][2],a[i][1],a[i][0]).slerp(new Quaternionf().rotationZYX(b[i][2],b[i][1],b[i][0]),w);
            float[] e=euler(q);
            out[i]=new float[]{e[0],e[1],e[2],a[i][3]+(b[i][3]-a[i][3])*w,a[i][4]+(b[i][4]-a[i][4])*w,a[i][5]+(b[i][5]-a[i][5])*w};
        }
        for(int i=PARTS;i<a.length;i++)out[i]=new float[]{a[i][0]+(b[i][0]-a[i][0])*w};
        return out;
    }
    /** Rz·Ry·Rx angles (radians) of q, read off its matrix with a gimbal-lock branch. */
    private static float[] euler(Quaternionf q){
        double w=q.w,x=q.x,y=q.y,z=q.z;
        double r00=1-2*(y*y+z*z),r01=2*(x*y-w*z),r10=2*(x*y+w*z),r11=1-2*(x*x+z*z),r20=2*(x*z-w*y),r21=2*(y*z+w*x),r22=1-2*(x*x+y*y);
        if(Math.abs(r20)<.99999)return new float[]{(float)Math.atan2(r21,r22),(float)Math.asin(-r20),(float)Math.atan2(r10,r00)};
        return r20<0?new float[]{(float)Math.atan2(r01,r11),(float)(Math.PI/2),0}:new float[]{(float)Math.atan2(-r01,r11),(float)(-Math.PI/2),0};
    }
}
