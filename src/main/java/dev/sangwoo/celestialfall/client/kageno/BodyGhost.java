package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Speed afterimages: when a body clip moves fast (the sword tip sweeping or the body dashing), a few fading,
 * glowing silhouettes of the R6 body trail behind it, each the same clip a little earlier at the place the body was
 * then. Proportions follow {@link KagenoClips} (the torso pivot, shoulders, hips, limb lengths), so a silhouette lines
 * up with the rendered body and the sword effects.
 */
public final class BodyGhost {
    private BodyGhost(){}
    /** Copies drawn behind the body and the time between them (seconds). */
    static final int COPIES=4;
    static final float GAP=.035f;
    /** Sword-tip and body speeds (blocks per second) at which the afterimages start and are fully on. */
    static final float TIP_FROM=9,TIP_FULL=20,BODY_FROM=6,BODY_FULL=14;
    /** Sword tip, blocks along the blade from the hand. */
    static final double TIP=1.35;

    /** How strongly the afterimages show at {@code sec}: from the sword tip's and the body's speed, unless the skill says otherwise. */
    static float strength(Fx.Skill skill,Anim clip,float sec,Vec3d velocity){
        float own=skill.afterimages(sec);if(own>=0)return Math.min(1,own);
        Vec3d[] now=tip(clip,sec),then=tip(clip,sec-.03f);
        double tipSpeed=now[0].add(now[1].multiply(TIP)).distanceTo(then[0].add(then[1].multiply(TIP)))/.03;
        double body=velocity.horizontalLength();
        return Math.max(Ease.smooth((float)tipSpeed,TIP_FROM,TIP_FULL),Ease.smooth((float)body,BODY_FROM,BODY_FULL));
    }
    /** Hand and blade direction of the clip at {@code sec} for a body at the origin facing +z. */
    private static Vec3d[] tip(Anim clip,float sec){return KagenoClips.blade(Vec3d.ZERO,0,clip.pose(Math.max(0,sec)));}

    public static void draw(Fx c,EffectMesh m){
        var skill=KagenoSkills.of(c.cast.skill());if(skill==null)return;
        var clip=KagenoClips.clip(c.cast.skill(),c.cast.combo());float t=c.sec;
        if(t<0||t>clip.length()+.15f||skill.hidden(t))return;
        PlayerEntity owner=c.mc.world==null?null:c.mc.world.getPlayerByUuid(c.cast.caster());
        Vec3d velocity=owner==null?Vec3d.ZERO:new Vec3d(owner.getX()-owner.prevX,0,owner.getZ()-owner.prevZ).multiply(20);
        float s=strength(skill,clip,Math.min(t,clip.length()),velocity);if(s<=.01f)return;
        int rgb=skill.afterimageColor();
        for(int k=1;k<=COPIES;k++){
            float back=k*GAP,at=t-back;if(at<0||skill.hidden(at))break;
            float a=s*.2f*(1-(k-1f)/COPIES);
            silhouette(m,c.pos.subtract(velocity.multiply(back)),c.cast.yaw(),clip.pose(Math.min(at,clip.length())),rgb,a);
        }
    }
    /** A glowing R6 body for pose {@code p} of a body standing at {@code feet} facing {@code yaw}. */
    static void silhouette(EffectMesh m,Vec3d feet,float yaw,R6.Pose p,int rgb,float alpha){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x),left=right.negate();float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationAxis(p.yaw*k,0,1,0).rotateAxis(p.pitch*k,(float)left.x,0,(float)left.z).rotateAxis(p.roll*k,(float)f.x,0,(float)f.z);
        Vec3d pivot=feet.add(f.multiply(p.fw)).add(0,p.up+KagenoClips.PIVOT,0).add(right.multiply(p.rt));
        Vec3d up=rotate(q,new Vec3d(0,1,0)),fw=rotate(q,f),rt=rotate(q,right);
        // Torso and head ride the torso rotation; limbs run from their sockets along their own directions.
        box(m,pivot.add(up.multiply(.075)),up,rt,fw,.375,.25,.125,rgb,alpha);
        box(m,pivot.add(up.multiply(.7)),up,rt,fw,.25,.25,.25,rgb,alpha);
        limb(m,pivot.add(rt.multiply(.3125)).add(up.multiply(.325)),rotate(q,world(p.rArm,f,right)),.125,.625,rgb,alpha);
        limb(m,pivot.add(rt.multiply(-.3125)).add(up.multiply(.325)),rotate(q,world(p.lArm,f,right)),.125,.625,rgb,alpha);
        limb(m,pivot.add(rt.multiply(.119)).add(up.multiply(-.3)),rotate(q,world(p.rLeg,f,right)),0,.75,rgb,alpha);
        limb(m,pivot.add(rt.multiply(-.119)).add(up.multiply(-.3)),rotate(q,world(p.lLeg,f,right)),0,.75,rgb,alpha);
    }
    private static Vec3d world(R6.Dir d,Vec3d f,Vec3d right){return right.multiply(d.rt()).add(0,d.up(),0).add(f.multiply(d.fw()));}
    private static Vec3d rotate(Quaternionf q,Vec3d v){var r=q.transform(new Vector3f((float)v.x,(float)v.y,(float)v.z));return new Vec3d(r.x,r.y,r.z);}
    /** A limb box from {@code back} blocks behind its socket to {@code reach} along {@code d}, 0.25 blocks thick. */
    private static void limb(EffectMesh m,Vec3d socket,Vec3d d,double back,double reach,int rgb,float alpha){
        d=d.normalize();Vec3d side=d.crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<1e-4)side=d.crossProduct(new Vec3d(1,0,0));side=side.normalize();
        Vec3d other=d.crossProduct(side).normalize();double half=(reach+back)/2;
        box(m,socket.add(d.multiply(half-back)),d,side,other,half,.125,.125,rgb,alpha);
    }
    /** Box centred at {@code c} with half sizes along the unit axes {@code a}, {@code b}, {@code n}. */
    private static void box(EffectMesh m,Vec3d c,Vec3d a,Vec3d b,Vec3d n,double ha,double hb,double hn,int rgb,float alpha){
        Vec3d A=a.multiply(ha),B=b.multiply(hb),N=n.multiply(hn);
        Vec3d[] v=new Vec3d[8];
        for(int i=0;i<8;i++)v[i]=c.add(A.multiply((i&1)==0?-1:1)).add(B.multiply((i&2)==0?-1:1)).add(N.multiply((i&4)==0?-1:1));
        int[][] faces={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
        for(int[] q:faces)m.quad(v[q[0]],v[q[1]],v[q[2]],v[q[3]],rgb,alpha);
    }
}
