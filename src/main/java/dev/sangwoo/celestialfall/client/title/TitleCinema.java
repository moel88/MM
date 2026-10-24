package dev.sangwoo.celestialfall.client.title;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Live rigged 3D welcome scene; GPU resources belong only to this screen. */
public final class TitleCinema implements AutoCloseable {
    private static final Logger LOG=LoggerFactory.getLogger("Botand/TitleCinema");
    private final CinemaTimeline clock=new CinemaTimeline();
    private final CinemaSound sound=new CinemaSound();
    private CinemaAssets.Performance performance;
    private CinemaGpu gpu;
    private CinemaCompat compat;
    private Boolean desktop;
    private float[] pose;
    private boolean failed;
    public boolean available(){return !failed;}
    public void advance(double dt,boolean animated){var cues=clock.advance(dt,animated);if(!failed)sound.update(cues,clock.musicReady());}
    public boolean render(DrawContext draw,int width,int height){
        if(failed)return false;
        draw.draw();
        try{
            if(desktop==null){
                String reason=CinemaSupport.incompatibility();desktop=reason==null;
                LOG.info("Title renderer: {}{}",desktop?"desktop":"Minecraft compatibility",reason==null?"":" ("+reason+")");
            }
            if(performance==null){performance=CinemaAssets.performance();pose=new float[performance.stride];}
            performance.sample(clock.poseTime(),pose);
            if(desktop){
                // Never enter Scope on an unsupported context: JNI aborts bypass Java catch.
                try(var state=new CinemaGpu.Scope()){
                    if(gpu==null)gpu=new CinemaGpu();
                    var window=MinecraftClient.getInstance().getWindow();
                    gpu.render(pose,performance.bones,clock.elapsed(),window.getFramebufferWidth(),window.getFramebufferHeight(),state);
                }catch(Exception e){
                    LOG.warn("Desktop title effects unavailable; switching to Minecraft compatibility rendering",e);
                    if(gpu!=null){gpu.close();gpu=null;}desktop=false;
                }
            }
            if(!desktop){if(compat==null)compat=new CinemaCompat();compat.render(draw,width,height,pose,performance.bones,clock.elapsed());}
            return true;
        }catch(Exception e){failed=true;LOG.error("Title scene unavailable; keeping map welcome controls",e);close();return false;}
    }
    @Override public void close(){sound.close();if(gpu!=null){gpu.close();gpu=null;}if(compat!=null){compat.close();compat=null;}performance=null;pose=null;}
}
