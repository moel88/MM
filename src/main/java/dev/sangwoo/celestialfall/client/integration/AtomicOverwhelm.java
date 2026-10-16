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
 * G's screen pass as the Veil post pipeline {@code celestialfall:atomic_overwhelm}. For every viewer
 * outside the damage column who sees the pillar its light pours into the view: the bright pillar is
 * cut out, blurred into a wide lavender glow and sheen, and gathered into light shafts streaming from
 * the middle of its visible axis. Close to the pillar the view is also overwhelmed: radial blur toward
 * it, split light, a lavender flood and a closing vignette. Both pulse with each surge.
 *
 * <p>The pillar renderer offers each frame how strong the light and the overwhelm are for this camera
 * and where the pillar stands on screen; the strongest offer of the frame wins. The pipeline is added
 * only while it is needed and removed as soon as no cast offers it (the hit view never does). All
 * values come from the cast clock the server syncs, so every client shows it in step with the same
 * cast; nothing here changes the camera, input or damage. If Veil cannot link the shaders the pass is
 * skipped.
 */
public final class AtomicOverwhelm {
    public static final Identifier PIPELINE=CelestialFall.id("atomic_overwhelm");
    private static float strength,shine,surge,centerX=.5f,centerY=.5f,facing,width,bar;
    private static float next=-1,nextStrength,nextShine,nextSurge,nextX,nextY,nextFacing,nextWidth;
    private static final float[] axis=new float[7],nextAxis=new float[7];
    /** The shader programs of the pipeline's stages: the final pass, the bright cut, the two blurs and the light shafts. */
    private static final Identifier[] PROGRAMS={PIPELINE,CelestialFall.id("atomic_bright"),CelestialFall.id("atomic_blur_h"),
        CelestialFall.id("atomic_blur_v"),CelestialFall.id("atomic_rays")};
    private static long renderedFrames,lastOffer;
    private static final int[] checkedPrograms={-1,-1,-1,-1,-1};
    private static final boolean[] linked=new boolean[PROGRAMS.length];
    private static boolean warned;
    private AtomicOverwhelm(){}

    private static PostProcessingManager manager(){return VeilRenderSystem.renderer().getPostProcessingManager();}

    public static void initialize(){
        FabricVeilPostProcessingEvent.PRE.register((id,pipeline,context)->{
            if(!PIPELINE.equals(id))return;
            pipeline.setFloat("AtomicStrength",strength);
            pipeline.setFloat("AtomicRadiance",shine);
            pipeline.setFloat("AtomicSurge",surge);
            pipeline.setVector("AtomicAxisA",axis[0],axis[1]);
            pipeline.setVector("AtomicAxisB",axis[2],axis[3]);
            pipeline.setVector("AtomicLight",axis[4],axis[5]);
            pipeline.setFloat("AtomicLightOn",axis[6]);
            pipeline.setVector("AtomicCenter",centerX,centerY);
            pipeline.setFloat("AtomicFacing",facing);
            pipeline.setFloat("AtomicWidth",width);
            pipeline.setFloat("AtomicBar",bar);
        });
        FabricVeilPostProcessingEvent.POST.register((id,pipeline,context)->{if(PIPELINE.equals(id))renderedFrames++;});
        // Each rendered frame either renews or drops the pipeline; if the world stops rendering altogether, drop it after
        // a second (by time, not ticks: a slow machine may render fewer frames than it runs ticks).
        ClientTickEvents.END_CLIENT_TICK.register(mc->{if(System.nanoTime()-lastOffer>1_000_000_000L&&(strength>0||shine>0||pipelineActive()))stop();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(AtomicOverwhelm::stop));
    }

    /** Starts a frame's offers (called before the casts are drawn). */
    public static void beginFrame(){next=-1;}
    /**
     * One cast's offer for this frame: overwhelm strength 0..1, light 0..1.3, surge pulse 0..1, the pillar's
     * axis at eye height on screen (0..1, y up) with 1 for facing when it is in front of the camera, its half
     * width as a fraction of the screen width, and its axis from the ground to its top with the light centre
     * ({@code AtomicPillar.screenAxis}; null when it is behind the camera).
     */
    public static void offer(float strength,float shine,float surge,float x,float y,float facing,float halfWidth,float[] screenAxis){
        float weight=Math.max(strength,shine);
        if(!(weight>next))return;
        next=weight;nextStrength=strength;nextShine=shine;nextSurge=surge;nextX=x;nextY=y;nextFacing=facing;nextWidth=halfWidth;
        if(screenAxis!=null)System.arraycopy(screenAxis,0,nextAxis,0,7);
        else{java.util.Arrays.fill(nextAxis,.5f);nextAxis[6]=0;}
    }
    /** Applies the strongest offer of the frame (or removes the pipeline when there was none); bar is the letterbox height. */
    public static void endFrame(float letterbox){
        if(next<=.002f){if(strength>0||shine>0)stop();return;}
        if(!ready()){stop();return;}
        strength=Math.min(1,nextStrength);shine=nextShine;surge=nextSurge;centerX=nextX;centerY=nextY;facing=nextFacing;width=nextWidth;bar=letterbox;
        System.arraycopy(nextAxis,0,axis,0,7);
        lastOffer=System.nanoTime();
        if(!manager().isActive(PIPELINE))manager().add(PIPELINE);
    }
    public static void stop(){
        strength=0;shine=0;
        try{manager().remove(PIPELINE);}catch(RuntimeException ignored){}
    }
    public static boolean active(){return (strength>0||shine>0)&&pipelineActive();}
    private static boolean pipelineActive(){
        try{var renderer=VeilRenderSystem.renderer();return renderer!=null&&renderer.getPostProcessingManager().isActive(PIPELINE);}
        catch(RuntimeException|LinkageError e){return false;}
    }
    public static float strength(){return strength;}
    public static float radiance(){return shine;}
    public static long renderedFrames(){return renderedFrames;}

    /** The pipeline is loaded and the shaders of all its stages linked. */
    public static boolean ready(){
        try{
            var shaders=VeilRenderSystem.renderer().getShaderManager();
            boolean ok=manager().getPipeline(PIPELINE)!=null;
            for(int i=0;i<PROGRAMS.length&&ok;i++){
                var shader=shaders.getShader(PROGRAMS[i]);
                if(shader==null){ok=false;break;}
                if(shader.getProgram()!=checkedPrograms[i]){
                    checkedPrograms[i]=shader.getProgram();
                    linked[i]=checkedPrograms[i]!=0&&GL20.glGetProgrami(checkedPrograms[i],GL20.GL_LINK_STATUS)!=0;
                }
                ok=linked[i];
            }
            if(!ok&&!warned){warned=true;CelestialFall.LOGGER.warn("Botand: the Veil pipeline {} is not available; G's screen light and close-range treatment are skipped",PIPELINE);}
            return ok;
        }catch(RuntimeException|LinkageError e){
            if(!warned){warned=true;CelestialFall.LOGGER.warn("Botand: Veil post-processing is unavailable; G's screen light and close-range treatment are skipped",e);}
            return false;
        }
    }
}
