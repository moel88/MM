package dev.sangwoo.celestialfall.client.integration;

import dev.sangwoo.celestialfall.CelestialFall;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.fabric.event.FabricVeilPostProcessingEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL20;

/**
 * G's overwhelming screen treatment for viewers close outside the damage column, as the Veil post
 * pipeline {@code celestialfall:atomic_overwhelm}: radial blur toward the pillar, split light, a
 * lavender flood from the pillar and a closing vignette, pulsing with each surge.
 *
 * <p>The pillar renderer offers each frame how strong the treatment is for this camera and where the
 * pillar stands on screen; the strongest offer of the frame wins. The pipeline is added only while it
 * is needed and removed as soon as no cast offers it (the hit view never does). All values come from
 * the cast clock the server syncs, so every client shows it in step with the same cast; nothing here
 * changes the camera, input or damage. If Veil cannot link the shader the treatment is skipped.
 */
public final class AtomicOverwhelm {
    public static final Identifier PIPELINE=CelestialFall.id("atomic_overwhelm");
    private static float strength,surge,centerX=.5f,centerY=.5f,facing,width,bar;
    private static float next=-1,nextSurge,nextX,nextY,nextFacing,nextWidth;
    private static long renderedFrames;
    private static int idleTicks,checkedProgram=-1;
    private static boolean warned,linked;
    private AtomicOverwhelm(){}

    private static PostProcessingManager manager(){return VeilRenderSystem.renderer().getPostProcessingManager();}

    public static void initialize(){
        FabricVeilPostProcessingEvent.PRE.register((id,pipeline,context)->{
            if(!PIPELINE.equals(id))return;
            pipeline.setFloat("AtomicStrength",strength);
            pipeline.setFloat("AtomicSurge",surge);
            pipeline.setVector("AtomicCenter",centerX,centerY);
            pipeline.setFloat("AtomicFacing",facing);
            pipeline.setFloat("AtomicWidth",width);
            pipeline.setFloat("AtomicBar",bar);
        });
        FabricVeilPostProcessingEvent.POST.register((id,pipeline,context)->{if(PIPELINE.equals(id))renderedFrames++;});
        // A cast that ends, a dimension change or a stalled world render leaves no offers: drop the pipeline.
        ClientTickEvents.END_CLIENT_TICK.register(mc->{if(++idleTicks>3&&(strength>0||pipelineActive()))stop();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(AtomicOverwhelm::stop));
    }

    /** Starts a frame's offers (called before the casts are drawn). */
    public static void beginFrame(){next=-1;}
    /**
     * One cast's offer for this frame: strength 0..1, surge pulse 0..1, the pillar's axis on screen (0..1,
     * y up) with 1 for facing when it is in front of the camera, and its half width as a fraction of the
     * screen width.
     */
    public static void offer(float strength,float surge,float x,float y,float facing,float halfWidth){
        if(!(strength>next))return;
        next=strength;nextSurge=surge;nextX=x;nextY=y;nextFacing=facing;nextWidth=halfWidth;
    }
    /** Applies the strongest offer of the frame (or removes the pipeline when there was none); bar is the letterbox height. */
    public static void endFrame(float letterbox){
        if(next<=.002f){if(strength>0)stop();return;}
        if(!ready()){stop();return;}
        strength=Math.min(1,next);surge=nextSurge;centerX=nextX;centerY=nextY;facing=nextFacing;width=nextWidth;bar=letterbox;
        idleTicks=0;
        if(!manager().isActive(PIPELINE))manager().add(PIPELINE);
    }
    public static void stop(){
        strength=0;
        try{manager().remove(PIPELINE);}catch(RuntimeException ignored){}
    }
    public static boolean active(){return strength>0&&pipelineActive();}
    private static boolean pipelineActive(){
        try{var renderer=VeilRenderSystem.renderer();return renderer!=null&&renderer.getPostProcessingManager().isActive(PIPELINE);}
        catch(RuntimeException|LinkageError e){return false;}
    }
    public static float strength(){return strength;}
    public static long renderedFrames(){return renderedFrames;}

    /** The pipeline is loaded and its shader linked. */
    public static boolean ready(){
        try{
            var shader=VeilRenderSystem.renderer().getShaderManager().getShader(PIPELINE);
            if(shader!=null&&shader.getProgram()!=checkedProgram){
                checkedProgram=shader.getProgram();linked=checkedProgram!=0&&GL20.glGetProgrami(checkedProgram,GL20.GL_LINK_STATUS)!=0;
            }
            boolean ok=manager().getPipeline(PIPELINE)!=null&&shader!=null&&linked;
            if(!ok&&!warned){warned=true;CelestialFall.LOGGER.warn("Botand: the Veil pipeline {} is not available; G's close-range screen treatment is skipped",PIPELINE);}
            return ok;
        }catch(RuntimeException|LinkageError e){
            if(!warned){warned=true;CelestialFall.LOGGER.warn("Botand: Veil post-processing is unavailable; G's close-range screen treatment is skipped",e);}
            return false;
        }
    }
}
