package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * A look at the knife's clips without the game (2.117.0; for whoever writes a clip, not a check of one): with the
 * environment's BOTAND_CLIP_DUMP naming a folder, every hit's body is written there as points over its length
 * (hit_N.csv: a row every 1/120 s; the head, both arms' shoulder, elbow and hand, both legs' hip, knee and foot, the
 * knife's rod end, heel and point, each as right, up and forward of its user's feet in blocks, and the body's turn),
 * for tools/xchara/preview_clip.py to draw as stick figures from the side, from behind and from above, and for
 * tools/xchara/clip_numbers.py to print as numbers (the blade's way, where it crosses the foe, how fast its point goes,
 * whether its edge leads, where the feet stand). The points are
 * the model's own (the joints' angles through the rig's two-piece limbs: R6, MotionPose), so what is drawn is what the
 * game poses, without the game's camera, skin or lighting: its pictures are not game screens and are never sent as a
 * check of the game. Without the variable it does nothing.
 */
class ClipPreviewDump {
    static final float K=(float)Math.PI/180;
    /** A point of the torso's frame (right, up from the pivot, forward) in the hit's frame (right, up from the feet, forward). */
    static double[] world(R6.Pose p,double rt,double up,double fw){
        Vec3d v=KagenoClips.onTorso(Vec3d.ZERO,0,p,rt,KagenoClips.PIVOT+up,fw);
        return new double[]{-v.x,v.y,v.z};
    }
    /** A two-piece limb from its root (the torso's frame): its hinge and its end, the upper piece turned by {@code e}, the lower bent {@code bend}. */
    static double[][] limb(double[] root,float[] e,float bend,double upper,double lower){
        var q=new Quaternionf().rotationZYX(e[2]*K,e[1]*K,e[0]*K);
        var a=q.transform(new Vector3f(0,(float)upper,0));
        var b=new Quaternionf(q).rotateX(bend*K).transform(new Vector3f(0,(float)lower,0));
        double[] hinge={root[0]-a.x,root[1]-a.y,root[2]-a.z};
        return new double[][]{hinge,{hinge[0]-b.x,hinge[1]-b.y,hinge[2]-b.z}};
    }
    @Test void dump() throws Exception {
        String out=System.getenv("BOTAND_CLIP_DUMP");
        if(out==null||out.isBlank())return;
        Path dir=Path.of(out);Files.createDirectories(dir);
        for(int hit=CUT;valid(hit);hit++){
            Anim a=KnifeCombo.clipOf(hit);
            try(var w=new PrintWriter(Files.newBufferedWriter(dir.resolve("hit_"+hit+".csv")))){
                w.println("t,yaw,pitch,roll,head,sR,eR,hR,sL,eL,hL,hipR,kR,fR,hipL,kL,fL,rod,heel,tip,blows="+blowsOf(hit)+",step="+STEP[hit]/20f+",hold="+HOLD[hit]/20f);
                for(double t=0;t<=a.length()+1e-6;t+=1/120.0){
                    R6.Frame f=a.frame((float)t);R6.Pose p=R6.poseOf(f);MotionPose m=f.joints();
                    double[] sR={.3125,.325,0},sL={-.3125,.325,0},hipR={.12,-.3,0},hipL={-.12,-.3,0};
                    var armR=limb(sR,m.joint[MotionPose.RIGHT_ARM],m.elbow[0],R6.ARM_UPPER,R6.ARM_LOWER);
                    var armL=limb(sL,m.joint[MotionPose.LEFT_ARM],m.elbow[1],R6.ARM_UPPER,R6.ARM_LOWER);
                    var legR=limb(hipR,m.joint[MotionPose.RIGHT_LEG],m.knee[0],R6.LEG_UPPER,R6.LEG_LOWER);
                    var legL=limb(hipL,m.joint[MotionPose.LEFT_LEG],m.knee[1],R6.LEG_UPPER,R6.LEG_LOWER);
                    var hb=KagenoClips.blade(Vec3d.ZERO,0,p);
                    Vec3d rod=hb[0].subtract(hb[1].multiply(KnifeCombo.HEEL)),heel=hb[0].add(hb[1].multiply(KnifeCombo.HEEL)),tip=hb[0].add(hb[1].multiply(KnifeCombo.TIP));
                    StringBuilder s=new StringBuilder(String.format(Locale.ROOT,"%.5f,%.3f,%.3f,%.3f",t,f.yaw(),f.pitch(),f.roll()));
                    for(double[] q:new double[][]{world(p,0,.7,0),world(p,sR[0],sR[1],sR[2]),world(p,armR[0][0],armR[0][1],armR[0][2]),world(p,armR[1][0],armR[1][1],armR[1][2]),
                        world(p,sL[0],sL[1],sL[2]),world(p,armL[0][0],armL[0][1],armL[0][2]),world(p,armL[1][0],armL[1][1],armL[1][2]),
                        world(p,hipR[0],hipR[1],hipR[2]),world(p,legR[0][0],legR[0][1],legR[0][2]),world(p,legR[1][0],legR[1][1],legR[1][2]),
                        world(p,hipL[0],hipL[1],hipL[2]),world(p,legL[0][0],legL[0][1],legL[0][2]),world(p,legL[1][0],legL[1][1],legL[1][2]),
                        {-rod.x,rod.y,rod.z},{-heel.x,heel.y,heel.z},{-tip.x,tip.y,tip.z}})
                        s.append(String.format(Locale.ROOT,",%.4f %.4f %.4f",q[0],q[1],q[2]));
                    w.println(s);
                }
            }
        }
    }
    private static String blowsOf(int hit){StringBuilder s=new StringBuilder();for(int i=0;i<blows(hit);i++)s.append(i==0?"":"/").append(blowAt(hit,i)/20f);return s.toString();}
}
