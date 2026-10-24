package dev.sangwoo.botandverify;

import dev.sangwoo.celestialfall.client.title.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;

/** Review harness only, NEVER included in the distributable mod. Uses the real Minecraft renderer. */
public final class CinemaClientCapture implements ClientModInitializer {
    private static final boolean COMPAT=Boolean.getBoolean("botand.cinema.compatibility");
    private static final boolean MAP=Boolean.getBoolean("botand.capture.map");
    private net.minecraft.client.sound.SoundInstance trackedMusic;private boolean checkedSilentIntro;
    private boolean mapStarted;private Object enteredWorld;private TitleCinema cinema;
    private BotandTitleScreen screen;private int shot,waiting;private CinemaTimeline clock;private long start,closedAt;
    public void onInitializeClient(){
        start=System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            try{
                if((System.nanoTime()-start)/1e9>180)throw new IllegalStateException("Capture timed out");
                if(screen==null){
                    if(mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-start)/1e9>6){
                        if(MAP){
                            if(!mapStarted){mc.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MUSIC).setValue(1.0);mc.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER).setValue(1.0);mapStarted=true;mc.createIntegratedServerLoader().start(new TitleScreen(),"Botand_Cinema_QA");return;}
                            if(!(mc.currentScreen instanceof BotandTitleScreen))return;
                            screen=(BotandTitleScreen)mc.currentScreen;enteredWorld=mc.world;
                            if(!dev.sangwoo.celestialfall.client.MapClient.enabled())throw new IllegalStateException("Map entry gate missing");
                        }else{screen=new BotandTitleScreen();mc.setScreen(screen);}
                        var field=BotandTitleScreen.class.getDeclaredField("cinema");field.setAccessible(true);cinema=(TitleCinema)field.get(screen);
                        field=TitleCinema.class.getDeclaredField("clock");field.setAccessible(true);clock=(CinemaTimeline)field.get(cinema);
                    }return;
                }
                var sf=TitleCinema.class.getDeclaredField("sound");sf.setAccessible(true);Object audio=sf.get(cinema);
                var mf=audio.getClass().getDeclaredField("music");mf.setAccessible(true);
                var activeMusic=(net.minecraft.client.sound.SoundInstance)mf.get(audio);
                if(clock.elapsed()>.3&&clock.elapsed()<CinemaTimeline.TURN_START){
                    if(activeMusic!=null)throw new IllegalStateException("Music started before the turn");checkedSilentIntro=true;
                }
                if(clock.elapsed()>5.5&&shot<9){
                    if(activeMusic==null||!mc.getSoundManager().isPlaying(activeMusic))throw new IllegalStateException("Turn BGM is not streaming");
                    if(trackedMusic==null)trackedMusic=activeMusic;
                    else if(trackedMusic!=activeMusic)throw new IllegalStateException("BGM restarted during idle/resize");
                }
                double[] times={.3,2.2,7.3,11.7};
                if(shot<times.length&&clock.elapsed()>=times[shot]){capture(mc,"minecraft-"+shot+".png");shot++;}
                if(shot==4){var r=screen.layout().screen(screen.layout().actions().get(2).rect());screen.mouseClicked(r.x()+10,r.y()+10,0);if(!screen.modal().equals("credits"))throw new IllegalStateException("Credits button failed");shot++;}
                else if(shot==5&&++waiting>4){capture(mc,"minecraft-credits.png");probeState(mc);screen.keyPressed(256,0,0);if(!screen.modal().isEmpty())throw new IllegalStateException("Back failed");shot++;waiting=0;}
                else if(shot==6&&++waiting>3){
                    double before=clock.elapsed();screen.resize(mc,640,360);if(clock.elapsed()!=before)throw new IllegalStateException("Resize rewound intro");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getHandle(),960,540);shot++;waiting=0;
                }else if(shot==7&&++waiting>4){capture(mc,"minecraft-resized.png");screen.keyPressed(256,0,0);if(!screen.modal().equals("leave"))throw new IllegalStateException("Leave dialog failed");shot++;waiting=0;}
                else if(shot==8&&++waiting>4){capture(mc,"minecraft-leave.png");
                    if(MAP){screen.keyPressed(256,0,0);var r=screen.layout().screen(screen.layout().actions().get(0).rect());screen.mouseClicked(r.x()+10,r.y()+10,0);}
                    else mc.setScreen(new TitleScreen());shot++;waiting=0;}
                else if(shot==9){
                    if(MAP&&mc.currentScreen!=null)return;
                    if(closedAt==0){closedAt=System.nanoTime();return;}
                    // Several catch-up ticks may share the last title framebuffer.
                    // Wait for real wall time so the proof includes a newly rendered world frame.
                    if((System.nanoTime()-closedAt)/1e9<2)return;
                    if(MAP&&(mc.world!=enteredWorld||!dev.sangwoo.celestialfall.client.MapClient.enabled()))throw new IllegalStateException("Play did not return to same authorized map");
                    if(!checkedSilentIntro||trackedMusic==null||mc.getSoundManager().isPlaying(trackedMusic))throw new IllegalStateException("Menu BGM lifecycle failed");
                    capture(mc,"minecraft-after-close.png");Files.writeString(output().resolve("native-client-result.txt"),"PASS 2.13.3: real Minecraft 1.20.1 renderer="+(COMPAT?"Minecraft compatibility (forced on desktop)":"desktop PC effects")+"; silent intro, turn-triggered streamed Satellites, same music instance across idle/resize, music stopped on Play; GL state restored, intro/idle, credits click, Esc/back, physical framebuffer resize, clock preserved, menu removal and world rendering resumed. Desktop Mesa. Map entry and Play integration="+MAP+".\n");mc.scheduleStop();}
            }catch(Throwable e){e.printStackTrace();try{Files.writeString(output().resolve("native-client-error.txt"),e.toString());}catch(Exception ignored){}mc.scheduleStop();}
        });
    }
    private static Path output()throws Exception{Path p=Path.of("../build/title-cinema"+(MAP?(COMPAT?"/native-compat":"/native-map"):""));Files.createDirectories(p);return p;}
    private void probeState(MinecraftClient mc)throws Exception {
        var draw=new net.minecraft.client.gui.DrawContext(mc,mc.getBufferBuilders().getEntityVertexConsumers());draw.draw();
        int[] before=glState();
        var projection=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        var modelView=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        var color=com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
        if(!cinema.render(draw,screen.width,screen.height))throw new IllegalStateException("PC shader fell back");
        int[] after=glState();
        if(COMPAT){
            var gf=TitleCinema.class.getDeclaredField("gpu");gf.setAccessible(true);
            var cf=TitleCinema.class.getDeclaredField("compat");cf.setAccessible(true);
            if(gf.get(cinema)!=null||cf.get(cinema)==null)throw new IllegalStateException("Unsupported desktop renderer was allocated");
            // Vanilla owns transient program/vertex/texture bindings; compare persistent state and matrices.
            for(int i=0;i<before.length;i++)if(i!=0&&i!=1&&i!=2&&i!=6&&before[i]!=after[i])throw new IllegalStateException("Compatibility leaked persistent GL state "+i);
            if(!projection.equals(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())||!modelView.equals(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix())||!java.util.Arrays.equals(color,com.mojang.blaze3d.systems.RenderSystem.getShaderColor()))throw new IllegalStateException("Compatibility leaked GUI transform/color");
        }else if(!java.util.Arrays.equals(before,after))throw new IllegalStateException("Cinema leaked GL state");
        int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)throw new IllegalStateException("OpenGL error "+error);
    }
    private static int[] glState(){
        int[] enums={org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM,org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING,org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING,
            org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING,org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING,org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE,
            org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D,org.lwjgl.opengl.GL11.GL_DEPTH_FUNC,org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK,
            org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB,org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB,org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA,org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA};
        int[] a=new int[enums.length+10];for(int i=0;i<enums.length;i++)a[i]=org.lwjgl.opengl.GL11.glGetInteger(enums[i]);
        int[] caps={org.lwjgl.opengl.GL11.GL_DEPTH_TEST,org.lwjgl.opengl.GL11.GL_BLEND,org.lwjgl.opengl.GL11.GL_CULL_FACE,org.lwjgl.opengl.GL11.GL_SCISSOR_TEST,org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB,org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL};
        for(int i=0;i<caps.length;i++)a[enums.length+i]=org.lwjgl.opengl.GL11.glIsEnabled(caps[i])?1:0;
        int[] vp=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,vp);System.arraycopy(vp,0,a,enums.length+6,4);return a;
    }
    private static void capture(MinecraftClient mc,String name)throws Exception{try(var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){image.writeTo(output().resolve(name));}}
}
