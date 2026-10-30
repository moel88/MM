package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.List;

/** Per-frame context for one Kageno cast. All effects are pure functions of the cast clock. */
public final class Fx {
    public static final Vec3d UP=new Vec3d(0,1,0);
    public final MinecraftClient mc;
    public final StandClient.Cast cast;
    /** Cast clock in ticks and in seconds of the reference footage. */
    public final float age,sec,delta;
    /** Caster feet now (follows movement) and at the cast (world anchored). */
    public final Vec3d pos,origin,camera;
    /** The way the body is drawn facing ({@link KagenoClips#facing}, degrees): the cast facing, or the caster's aim for the aimed casts. */
    public final float yaw;
    /** That facing, the character's right and left (horizontal unit vectors). */
    public final Vec3d f,right,left;
    public final Matrix4f matrix;
    public final MatrixStack matrices;
    public final List<LivingEntity> targets;
    Fx(MinecraftClient mc,StandClient.Cast cast,float delta,Vec3d pos,Vec3d camera,Matrix4f matrix,MatrixStack matrices,List<LivingEntity> targets){
        this.mc=mc;this.cast=cast;this.delta=delta;this.age=cast.age(delta);this.sec=age/20f;this.pos=pos;this.origin=cast.origin();this.camera=camera;
        this.yaw=KagenoClips.facing(cast,delta);this.f=Vec3d.fromPolar(0,yaw);this.left=new Vec3d(f.z,0,-f.x);this.right=left.negate();this.matrix=matrix;this.matrices=matrices;this.targets=targets;
    }
    /** Point relative to the moving caster: forward, up, right (blocks). */
    public Vec3d at(double fw,double up,double rt){return pos.add(f.multiply(fw)).add(0,up,0).add(right.multiply(rt));}
    /** Point relative to the cast origin (world anchored). */
    public Vec3d at0(double fw,double up,double rt){return origin.add(f.multiply(fw)).add(0,up,0).add(right.multiply(rt));}
    /** Direction in the cast frame from elevation/azimuth (azimuth from forward toward the right). */
    public Vec3d dir(double elevation,double azimuth){
        double e=Math.toRadians(elevation),a=Math.toRadians(azimuth);
        return f.multiply(Math.cos(e)*Math.cos(a)).add(right.multiply(Math.cos(e)*Math.sin(a))).add(0,Math.sin(e),0);
    }
    /** Vector expressed in the cast frame (forward, up, right). */
    public Vec3d vec(double fw,double up,double rt){return f.multiply(fw).add(0,up,0).add(right.multiply(rt));}
    public boolean between(double fromSec,double toSec){return sec>=fromSec&&sec<toSec;}

    /** Hooks a skill implements; every pass is optional. */
    public interface Skill {
        /** Body clip; {@code combo} is the M1 hit number. */
        default Anim clip(int combo){return null;}
        /** The caster model is hidden (e.g. off screen in a cutscene shot). */
        default boolean hidden(float sec){return false;}
        default void alpha(Fx c,EffectMesh m){}
        default void additive(Fx c,EffectMesh m){}
        default void strokes(Fx c){}
        /** Brush crescents ({@link dev.sangwoo.celestialfall.client.KagenoFire#crescent}). */
        default void crescents(Fx c){}
        /** Ink ribbons ({@link dev.sangwoo.celestialfall.client.KagenoFire#inkRibbon}: Lee So-sang's slashes, 2.77.0). */
        default void ink(Fx c){}
        default void shaders(Fx c){}
        default void blocks(Fx c){}
        /** Full-screen flash strength 0..1 and colour. */
        default float flash(Fx c){return 0;}
        default int flashColor(Fx c){return 0xfff8ff;}
        /** Cutscene camera at {@code sec}, or null for the gameplay camera. */
        default KagenoCamera.Shot shot(float sec){return null;}
        /** 0 = gameplay camera, 1 = cutscene camera. */
        default float shotBlend(float sec){return shot(sec)==null?0:1;}
        /** Gameplay-camera shake strength in degrees. */
        default float shake(float sec){return 0;}
        /** Authored gameplay-camera motion {yaw, pitch, roll} in degrees (pitch + looks down), or null. */
        default float[] kick(float sec){return null;}
        /** Shake for one cast (M1's four hits differ); the skill's own by default. */
        default float shake(StandClient.Cast cast,float sec){return shake(sec);}
        /** Camera motion for one cast; the skill's own by default. */
        default float[] kick(StandClient.Cast cast,float sec){return kick(sec);}
        /** Geometry drawn over the full-screen flash: the alpha pass, then the additive pass. */
        default void overFlash(Fx c,EffectMesh m,boolean additive){}
        default float pulse(float sec){return 0;}
        /** Seconds after the cast during which this skill still draws. */
        default float lifetime(){return 6;}
        /** Strength of the body's speed afterimages at {@code sec} (0..1), or -1 to follow the sword tip's and the body's speed. */
        default float afterimages(float sec){return -1;}
        /** Colour of the speed afterimages. */
        default int afterimageColor(){return 0x8a5cff;}
        /**
         * Drawn through the HDR effect layer ({@link dev.sangwoo.celestialfall.client.vfx.FxLayer}): light adds up past
         * white, bloom from the effects only, soft edges where effects meet the world, afterglow, and the hooks below.
         */
        default boolean layered(){return false;}
        /** Lights on the world, ground decals, a light-shaft source and the impact frame for the effect layer. */
        default void frame(Fx c,dev.sangwoo.celestialfall.client.vfx.FxFrame f){}
        /** Refraction bands for the effect layer. */
        default void distort(Fx c,dev.sangwoo.celestialfall.client.vfx.FxDistort d){}
        /** Solid world pieces (block chunks, lifted ground) drawn before the effects. */
        default void solids(Fx c,dev.sangwoo.celestialfall.client.vfx.FxSolids s){}
    }
}
