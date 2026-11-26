package dev.sangwoo.celestialfall.client.samurai;

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

/**
 * A look at the samurai's clip without the game (2.144.0; for whoever writes it, not a check of it; as the knife's
 * ClipPreviewDump): with the environment's BOTAND_CLIP_DUMP naming a folder, the body is written there as points over
 * the clip (hit_17.csv: a row every 1/120 s; the head, both arms' shoulder, elbow and hand, both legs' hip, knee and
 * foot, the sword's hand, guard and point, and the scabbard's mouth and end, each as right, up and forward of the feet
 * in blocks), for tools/samurai/preview_kata.py to draw as stick figures. Its pictures are not game screens and are
 * never sent as a check of the game. Without the variable it does nothing.
 */
class KataPreviewDump {
    static final float K=(float)Math.PI/180;
    static double[] world(R6.Pose p,double rt,double up,double fw){
        Vec3d v=KagenoClips.onTorso(Vec3d.ZERO,0,p,rt,KagenoClips.PIVOT+up,fw);
        return new double[]{-v.x,v.y,v.z};
    }
    static double[][] limb(double[] root,float[] e,float bend,double upper,double lower){
        var q=new Quaternionf().rotationZYX(e[2]*K,e[1]*K,e[0]*K);
        var a=q.transform(new Vector3f(0,(float)upper,0));
        var b=new Quaternionf(q).rotateX(bend*K).transform(new Vector3f(0,(float)lower,0));
        double[] hinge={root[0]-a.x,root[1]-a.y,root[2]-a.z};
        return new double[][]{hinge,{hinge[0]-b.x,hinge[1]-b.y,hinge[2]-b.z}};
    }
    /** The body's points at {@code t}: head, sR, eR, hR, sL, eL, hL, hipR, kR, fR, hipL, kL, fL. */
    static double[][] points(float t){
        R6.Frame f=SamuraiKata.CLIP.frame(t);R6.Pose p=R6.poseOf(f);MotionPose m=f.joints();
        double[] sR={.3125,.325,0},sL={-.3125,.325,0},hipR={.12,-.3,0},hipL={-.12,-.3,0};
        var armR=limb(sR,m.joint[MotionPose.RIGHT_ARM],m.elbow[0],R6.ARM_UPPER,R6.ARM_LOWER);
        var armL=limb(sL,m.joint[MotionPose.LEFT_ARM],m.elbow[1],R6.ARM_UPPER,R6.ARM_LOWER);
        var legR=limb(hipR,m.joint[MotionPose.RIGHT_LEG],m.knee[0],R6.LEG_UPPER,R6.LEG_LOWER);
        var legL=limb(hipL,m.joint[MotionPose.LEFT_LEG],m.knee[1],R6.LEG_UPPER,R6.LEG_LOWER);
        return new double[][]{world(p,0,.7,0),world(p,sR[0],sR[1],sR[2]),world(p,armR[0][0],armR[0][1],armR[0][2]),world(p,armR[1][0],armR[1][1],armR[1][2]),
            world(p,sL[0],sL[1],sL[2]),world(p,armL[0][0],armL[0][1],armL[0][2]),world(p,armL[1][0],armL[1][1],armL[1][2]),
            world(p,hipR[0],hipR[1],hipR[2]),world(p,legR[0][0],legR[0][1],legR[0][2]),world(p,legR[1][0],legR[1][1],legR[1][2]),
            world(p,hipL[0],hipL[1],hipL[2]),world(p,legL[0][0],legL[0][1],legL[0][2]),world(p,legL[1][0],legL[1][1],legL[1][2])};
    }
    @Test void dump() throws Exception {
        String out=System.getenv("BOTAND_CLIP_DUMP");
        if(out==null||out.isBlank())return;
        Path dir=Path.of(out);Files.createDirectories(dir);
        StringBuilder cuts=new StringBuilder();
        for(float[] c:SamuraiKata.CUTS)cuts.append(cuts.length()==0?"":"/").append(c[0]);
        try(var w=new PrintWriter(Files.newBufferedWriter(dir.resolve("hit_17.csv")))){
            w.println("t,yaw,pitch,roll,head,sR,eR,hR,sL,eL,hL,hipR,kR,fR,hipL,kL,fL,rod,heel,tip,mouth,end,blows="+cuts+",step=0,hold=0");
            for(double t=0;t<=SamuraiKata.LENGTH+1e-6;t+=1/120.0){
                float sec=(float)t;R6.Frame f=SamuraiKata.CLIP.frame(sec);
                var hb=SamuraiKata.blade(Vec3d.ZERO,0,sec);var sh=SamuraiKata.sheath(Vec3d.ZERO,0,sec);
                Vec3d guard=hb[0].add(hb[1].multiply(SamuraiKata.GUARD)),tip=hb[0].add(hb[1].multiply(SamuraiKata.TIP)),end=sh[0].add(sh[1].multiply(SamuraiKata.SHEATH));
                StringBuilder s=new StringBuilder(String.format(Locale.ROOT,"%.5f,%.3f,%.3f,%.3f",t,f.yaw(),f.pitch(),f.roll()));
                for(double[] q:points(sec))s.append(String.format(Locale.ROOT,",%.4f %.4f %.4f",q[0],q[1],q[2]));
                for(Vec3d v:new Vec3d[]{hb[0],guard,tip,sh[0],end})s.append(String.format(Locale.ROOT,",%.4f %.4f %.4f",-v.x,v.y,v.z));
                w.println(s);
            }
        }
    }
}
