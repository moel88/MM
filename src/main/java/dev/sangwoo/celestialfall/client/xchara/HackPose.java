package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.client.model.ModelPart;

/**
 * X Chara's other arm while their hack is on (2.115.0, their Q): up from the shoulder a little forward and out, its
 * forearm bent level before them, the panel floating over its palm (HackPanel puts it there from the hand's frame).
 * It comes up as the hack comes on and goes down as it goes (HackLook.arm), keeps a quarter of its walk's swing as the
 * knife's arm does, and breathes a little. A blow, a guard or anything else that moves the arms has it (the panel goes
 * with the hand).
 */
public final class HackPose {
    private HackPose(){}
    /** The upper arm forward (its pitch), turned (its yaw) and out (its roll), and the forearm's bend: radians, for the left arm (the right mirrors). */
    static final float PITCH=-.38f,YAW=.10f,ROLL=-.22f,ELBOW=-1.05f;
    /** Poses {@code arm} (the left one if {@code left}) as far as the hack {@code f} has it up; {@code clock} the model's own (ticks). */
    public static void apply(ModelPart arm,boolean left,HackLook.Frame f,float clock){
        if(f==null)return;
        float w=f.arm();if(w<=0)return;
        float side=left?1:-1,breath=.025f*(float)Math.sin(clock*.11f);
        var elbow=arm.getChild("elbow");
        arm.pitch=lerp(w,arm.pitch,PITCH+arm.pitch*.25f+breath);
        arm.yaw=lerp(w,arm.yaw,side*YAW);
        arm.roll=lerp(w,arm.roll,side*ROLL);
        elbow.pitch=lerp(w,elbow.pitch,ELBOW-breath*.6f);
    }
    static float lerp(float w,float a,float b){return a+(b-a)*w;}
}
