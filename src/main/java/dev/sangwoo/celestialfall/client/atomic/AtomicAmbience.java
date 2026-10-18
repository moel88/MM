package dev.sangwoo.celestialfall.client.atomic;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.AtomicClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What G does to the world around a viewer outside its damage column: the sky, fog, clouds and
 * world light dim toward deep violet so the pillar blazes against them, bloom for distant viewers,
 * the FOV punch, the letterbox and the boom when the air shock arrives. Viewers in any damage
 * column keep the hit view untouched: everything here is zero for them.
 */
public final class AtomicAmbience {
    private static final Set<UUID> BOOMED=new HashSet<>();
    private AtomicAmbience(){}

    /** The cast dressing this camera's world (the nearest one seen from outside), with its clock and distance. */
    record View(AtomicClient.Cast cast,float sec,double distance){double radius(){return cast.radius;}}

    static View view(Vec3d cam,float delta){
        var mc=MinecraftClient.getInstance();
        if(mc.world==null||AtomicClient.CASTS.isEmpty()||!AtomicPillar.available())return null;
        var dim=mc.world.getRegistryKey().getValue();
        View best=null;
        for(var c:AtomicClient.CASTS.values()){
            if(!c.dimension.equals(dim))continue;
            float sec=c.age(delta)/20f;
            if(sec<AtomicLook.IMPACT||sec>=AtomicWorld.BLAST_END+1.5f)continue;
            // Inside any damage column the hit view owns the screen.
            if(AtomicPillar.interior(c,cam,sec))return null;
            double d=cam.distanceTo(c.origin);
            if(AtomicStage.reach(d,c.radius)<=0)continue;
            if(best==null||d<best.distance())best=new View(c,sec,d);
        }
        return best;
    }
    private static Vec3d camera(){return MinecraftClient.getInstance().gameRenderer.getCamera().getPos();}

    /** How far the sky and world light are dimmed toward violet (0..1). */
    public static float dim(Vec3d cam,float delta){
        var v=view(cam,delta);
        return v==null?0:AtomicStage.sky(v.sec())*AtomicStage.reach(v.distance(),v.radius());
    }
    public static float dim(float delta){return dim(camera(),delta);}

    /** Sky colour pulled toward a deep navy violet. */
    public static Vec3d sky(Vec3d color,Vec3d cam,float delta){return mix(color,.035,.02,.11,dim(cam,delta)*.92f);}
    /** Clouds turn dark, faintly violet (the cloud ring is the lit part). */
    public static Vec3d clouds(Vec3d color,float delta){return mix(color,.2,.12,.34,dim(delta)*.85f);}
    /** Fog and clear colour, so the horizon matches the sky; null when unchanged. */
    public static float[] fog(float r,float g,float b,Vec3d cam,float delta){
        float k=dim(cam,delta)*.9f;
        return k<=0?null:new float[]{r+(.05f-r)*k,g+(.025f-g)*k,b+(.13f-b)*k};
    }
    /** World light (lightmap pixel, ABGR): darker and violet. */
    public static int light(int abgr,float k){
        if(k<=0)return abgr;
        float r=(abgr&255)/255f,g=(abgr>>8&255)/255f,b=(abgr>>16&255)/255f,dark=1-.38f*k;
        r*=dark*(1+(.82f-1)*k);g*=dark*(1+(.6f-1)*k);b*=dark*(1+(1.22f-1)*k);
        return (abgr&0xff000000)|(q(b)<<16)|(q(g)<<8)|q(r);
    }
    private static int q(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
    private static Vec3d mix(Vec3d c,double r,double g,double b,float k){
        return k<=0?c:new Vec3d(c.x+(r-c.x)*k,c.y+(g-c.y)*k,c.z+(b-c.z)*k);
    }

    /** FOV offset (degrees) for the slowed ignition and the shock's arrival. */
    public static double fov(float delta){
        var cam=camera();var v=view(cam,delta);
        return v==null?0:AtomicStage.fov(v.sec(),v.distance(),v.radius());
    }

    /** Height of the letterbox bars as a fraction of the screen (0 when none). */
    public static float bar(float delta){
        var v=view(camera(),delta);
        return v==null?0:AtomicStage.letterbox(v.sec(),v.distance(),v.radius());
    }
    /** Black bars top and bottom, drawn over the world before post-processing and the HUD. */
    public static void letterbox(float delta){
        float bar=bar(delta);if(bar<=.001f)return;
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,1,1,0,-1,1),VertexSorter.BY_Z);
        var stack=RenderSystem.getModelViewStack();stack.push();stack.loadIdentity();RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();RenderSystem.disableBlend();RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var buffer=Tessellator.getInstance().getBuffer();buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        for(float[] y:new float[][]{{0,bar},{1-bar,1}}){
            buffer.vertex(0,y[0],0).color(0,0,0,255).next();buffer.vertex(0,y[1],0).color(0,0,0,255).next();
            buffer.vertex(1,y[1],0).color(0,0,0,255).next();buffer.vertex(1,y[0],0).color(0,0,0,255).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        stack.pop();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix();
        RenderSystem.enableDepthTest();RenderSystem.enableCull();
    }

    /** The boom reaches each outside viewer when the shock does: later and quieter farther away. */
    static void boom(AtomicClient.Cast c,float sec,Vec3d cam){
        BOOMED.retainAll(AtomicClient.CASTS.keySet());
        if(BOOMED.contains(c.id))return;
        double d=cam.distanceTo(c.origin);float reach=AtomicStage.reach(d,c.radius);
        if(sec<AtomicStage.arrival(d,c.radius))return;
        BOOMED.add(c.id);
        // Too late to be heard as the blast (a viewer who arrived mid-cast): stay silent.
        if(reach<=0||sec>AtomicStage.arrival(d,c.radius)+.5f)return;
        float volume=(float)(CelestialFall.CONFIG.effectVolume*(.35+.65*reach));
        if(volume<=.01f)return;
        var sounds=MinecraftClient.getInstance().getSoundManager();
        sounds.play(PositionedSoundInstance.master(SoundEvents.ENTITY_GENERIC_EXPLODE,.5f,volume));
        sounds.play(PositionedSoundInstance.master(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,.55f,volume*.8f));
    }
}
