package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.title.BotandTitleScreen;
import dev.sangwoo.celestialfall.stand.*;
import dev.sangwoo.celestialfall.progression.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real client capture for the H changes, in an isolated flat test save. */
public final class RemakeCapture implements ClientModInitializer {
    static RemakeCapture instance;MinecraftClient mc;int phase,age,renderFrames;long start=System.nanoTime();
    final Path out=Path.of(System.getProperty("botand.remake.proof"));
    final Set<String> saved=new HashSet<>();final Queue<String> shots=new ArrayDeque<>();
    final StringBuilder log=new StringBuilder();
    final StringBuilder frames=new StringBuilder("epoch_seconds,cast_seconds,forward,up,camera_yaw,camera_pitch\n");
    final StringBuilder actorFrames=new StringBuilder("cast_seconds,rendered_forward,rendered_right,facing_forward,velocity_dot_facing,root_error\n");
    org.joml.Matrix4f actorBase;org.joml.Vector3f lastActor;float lastActorTime;
    int renderedBackFrames;double minimumActor=Double.POSITIVE_INFINITY,maximumActor=Double.NEGATIVE_INFINITY,maxRootError;
    public static void beforeActor(net.minecraft.entity.LivingEntity p,net.minecraft.client.util.math.MatrixStack m){
        if(instance!=null&&instance.mc!=null&&p==instance.mc.player&&instance.phase==9)
            instance.actorBase=new org.joml.Matrix4f(m.peek().getPositionMatrix());
    }
    public static void afterActor(net.minecraft.entity.LivingEntity p,float delta,net.minecraft.client.util.math.MatrixStack m){
        if(instance==null||instance.mc==null||p!=instance.mc.player||instance.phase!=9||instance.actorBase==null)return;
        try{instance.measureActor(delta,m);}catch(Throwable t){instance.fail(t);}
    }
    void measureActor(float delta,net.minecraft.client.util.math.MatrixStack m){
        var c=cast(H);if(c==null)return;float t=c.age(delta)/20f;if(t<2.737f||t>3.52f)return;
        var relative=new org.joml.Matrix4f(actorBase).invert().mul(m.peek().getPositionMatrix());
        var point=relative.transformPosition(new org.joml.Vector3f(0,1.05f,0));
        var facing=relative.transformDirection(new org.joml.Vector3f(0,0,-1));facing.y=0;facing.normalize();
        var worldForward=Vec3d.fromPolar(0,c.yaw());var f=new org.joml.Vector3f((float)worldForward.x,0,(float)worldForward.z);
        double forward=point.dot(f),right=point.dot(new org.joml.Vector3f(-f.z,0,f.x));
        var expected=dev.sangwoo.celestialfall.client.kageno.KagenoClips.action(c,delta);
        double error=Math.abs(forward-expected.fw());maxRootError=Math.max(maxRootError,error);
        if(error>.002)throw new AssertionError("rendered root differs from authored root: "+error+" actual="+forward+" expected="+expected.fw()+" point="+point);
        double dot=0;
        if(lastActor!=null&&t>lastActorTime){
            dot=new org.joml.Vector3f(point).sub(lastActor).dot(facing)/(t-lastActorTime);
            if(t<=3.20f){
                if(dot>.02)throw new AssertionError("actual rendered actor moves in its facing direction during retreat: "+dot);
                renderedBackFrames++;
            }
        }
        if(t<=3.22f){minimumActor=Math.min(minimumActor,forward);maximumActor=Math.max(maximumActor,forward);}
        actorFrames.append(String.format(java.util.Locale.ROOT,"%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",t,forward,right,facing.dot(f),dot,error));
        lastActor=new org.joml.Vector3f(point);lastActorTime=t;
    }
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);log.append("PASS ").append(text).append('\n');}
    void server(Consumer<ServerPlayerEntity> task){mc.getServer().submit(()->task.accept(mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()))).join();}
    void reset(){server(p->{KagenoManager.clear(p.getServer());AtomicManager.clear(p.getServer());p.teleport(p.getServerWorld(),.5,81,.5,180,0);p.setVelocity(Vec3d.ZERO);p.setHealth(p.getMaxHealth());NoCooldownState.get(p.getServer()).set(p.getUuid(),true);
        for(int z=1;z<=8;z++)for(int x=-1;x<=1;x++){
            var block=net.minecraft.block.Blocks.STONE;
            if(Boolean.getBoolean("botand.review.fixed"))block=z==2?net.minecraft.block.Blocks.YELLOW_CONCRETE:z==7?net.minecraft.block.Blocks.BLUE_CONCRETE:z%2==0?net.minecraft.block.Blocks.WHITE_CONCRETE:net.minecraft.block.Blocks.GRAY_CONCRETE;
            p.getServerWorld().setBlockState(new net.minecraft.util.math.BlockPos(x,80,z),block.getDefaultState());
        }
    });BotandCamera.STATE.yaw=40;BotandCamera.STATE.pitch=12;BotandCamera.STATE.targetDistance=5;}
    void key(int key){mc.keyboard.onKey(mc.getWindow().getHandle(),key,0,GLFW_PRESS,0);mc.keyboard.onKey(mc.getWindow().getHandle(),key,0,GLFW_RELEASE,0);}
    void next(int n){phase=n;age=0;log.append("PHASE ").append(n).append(' ').append(java.time.Instant.now()).append('\n');System.out.println("REMAKE PHASE "+n);}
    StandClient.Cast cast(int skill){return StandClient.CASTS.values().stream().filter(c->c.skill()==skill).findFirst().orElse(null);}
    void shot(String s){if(saved.add(s))shots.add(s);}
    void fail(Throwable t){t.printStackTrace();try{Files.writeString(out.resolve("error.txt"),t+"\n"+log);}catch(Exception ignored){}mc.scheduleStop();phase=99;}
    public static void afterFrame(){if(instance==null)return;try{instance.frame();}catch(Throwable t){instance.fail(t);}}
    void frame()throws Exception{
        if(mc!=null&&mc.world!=null&&phase==9){
            var active=cast(H);if(active!=null){
                renderFrames++;float delta=mc.getTickDelta();var r=dev.sangwoo.celestialfall.client.kageno.KagenoClips.action(active,delta);
                var cam=mc.gameRenderer.getCamera();
                frames.append(String.format(java.util.Locale.ROOT,"%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",System.currentTimeMillis()/1000.0,active.age(delta)/20f,r.fw(),r.up(),cam.getYaw(),cam.getPitch()));
            }
        }
        if(mc!=null&&mc.world!=null&&phase==10&&Boolean.getBoolean("botand.review.atomic")){
            var active=AtomicClient.CASTS.values().stream().filter(c->c.caster.equals(mc.player.getUuid())).findFirst().orElse(null);
            if(active!=null){
                renderFrames++;float delta=mc.getTickDelta();var cam=mc.gameRenderer.getCamera();
                frames.append(String.format(java.util.Locale.ROOT,"%.6f,%.6f,0,0,%.6f,%.6f\n",System.currentTimeMillis()/1000.0,active.age(delta)/20f,cam.getYaw(),cam.getPitch()));
            }
        }
        while(!shots.isEmpty()){
            String name=shots.remove();try(var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){
                image.writeTo(out.resolve(name+".png"));
                if(name.equals("Cinematics_Veil")){
                    long purple=0,count=0;for(int y=40;y<image.getHeight()-40;y+=8)for(int x=0;x<image.getWidth();x+=8){int v=image.getColor(x,y);int r=v&255,g=(v>>8)&255,b=(v>>16)&255;if(r>g+15&&b>g+15)purple++;count++;}
                    check(purple>count/2,"Veil grade visibly reaches framebuffer");
                }
                if(name.startsWith("R_gray")){
                    int spread=0;for(int y=0;y<image.getHeight();y+=5)for(int x=0;x<image.getWidth();x+=5){int v=image.getColor(x,y);int r=v&255,g=(v>>8)&255,b=(v>>16)&255;spread=Math.max(spread,Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)));}
                    check(spread<=1,"R framebuffer grayscale with optional post processing disabled");
                }
            }
        }
    }
    public void onInitializeClient(){instance=this;
        if(Boolean.getBoolean("botand.review.tools"))foundry.veil.fabric.event.FabricVeilPostProcessingEvent.POST.register((id,pipeline,ctx)->{
            if(!id.equals(dev.sangwoo.celestialfall.client.integration.VeilBridge.PREVIEW)||dev.sangwoo.celestialfall.client.integration.VeilBridge.renderedFrames()!=1)return;
            System.out.println("VEIL_DEBUG initialError="+org.lwjgl.opengl.GL11.glGetError());
            var shader=foundry.veil.api.client.render.VeilRenderSystem.renderer().getShaderManager().getShader(id);
            System.out.println("VEIL_DEBUG program="+shader.getProgram()+" sampler="+shader.getInt("DiffuseSampler0")+" output="+org.lwjgl.opengl.GL30.glGetFragDataLocation(shader.getProgram(),"fragColor"));
            shader.getShaders().forEach((type,sh)->{try{Files.writeString(out.resolve("shader-"+type+".glsl"),org.lwjgl.opengl.GL20.glGetShaderSource(sh.id()));}catch(Exception ignored){}});
            int old=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
            for(String name:java.util.List.of("minecraft:main","veil:post","temp:swap")){
                var fb=ctx.getFramebuffer(new net.minecraft.util.Identifier(name));if(fb==null)continue;
                org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,fb.getId());
                var pixel=java.nio.ByteBuffer.allocateDirect(4);org.lwjgl.opengl.GL11.glReadPixels(fb.getWidth()/2,fb.getHeight()/2,1,1,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,pixel);
                System.out.println("VEIL_DEBUG "+name+" id="+fb.getId()+" pixel="+(pixel.get(0)&255)+","+(pixel.get(1)&255)+","+(pixel.get(2)&255)+" err="+org.lwjgl.opengl.GL11.glGetError());
            }
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,old);
        });ClientTickEvents.END_CLIENT_TICK.register(c->{mc=c;try{
        if(phase==99)return;if((System.nanoTime()-start)/1e9>300)throw new AssertionError("capture timeout phase "+phase);
        age++;mc.getToastManager().clear();
        switch(phase){
        case 0->{if(mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-start)/1e9>4){Files.createDirectories(out);mc.options.getViewDistance().setValue(Boolean.getBoolean("botand.review.atomicFar")?12:3);mc.options.getMaxFps().setValue(60);mc.options.hudHidden=true;mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);mc.createIntegratedServerLoader().start(new TitleScreen(),"Botand_Remake_QA");next(1);}}
        case 1->{if(mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);}if(mc.world!=null&&mc.currentScreen==null&&ProgressClient.ready){server(p->{p.changeGameMode(GameMode.SURVIVAL);ProgressManager.set(p,new ProgressProfile(1,0,0,"kageno",10,0));p.getInventory().setStack(0,new ItemStack(Items.STONE,17));p.getInventory().selectedSlot=0;});reset();next(2);}}
        case 2->{if(mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);}if(age>35&&StandClient.visible(mc.player)&&CombatClient.combatInput()){if(Boolean.getBoolean("botand.review.tools")){dev.sangwoo.celestialfall.client.integration.IntegrationCommands.cameraDemo();check(dev.sangwoo.celestialfall.client.integration.VeilBridge.preview(),"Veil preview starts alongside cinematic");next(22);break;}if(Boolean.getBoolean("botand.review.atomic")){reset();BotandCamera.STATE.yaw=0;BotandCamera.STATE.pitch=3;BotandCamera.STATE.targetDistance=3.2f;next(12);break;}if(Boolean.getBoolean("botand.review.highest")){reset();key(GLFW_KEY_H);next(9);break;}log.append("Hands main=").append(mc.player.getMainHandStack()).append(" off=").append(mc.player.getOffHandStack()).append(" arm=").append(mc.player.getMainArm()).append("\n");CelestialFall.CONFIG.postProcessing=false;key(GLFW_KEY_R);next(3);}}
        case 3->{var c0=cast(R);if(c0!=null&&c0.age(0)>=16&&c0.age(0)<24)shot("R_gray");if(age>85){check(saved.contains("R_gray"),"R rendered");CelestialFall.CONFIG.postProcessing=true;reset();key(GLFW_KEY_P);next(4);}}
        case 4->{if(age==20){check(cast(P)!=null,"P started");var input=new net.minecraft.client.input.Input();input.movementForward=1;StandClient.cancelPoseOnMovement(input);check(cast(P)==null,"forward movement immediately releases local P");}if(age==30){server(p->{check(KagenoManager.casts().stream().noneMatch(x->x.player==p&&x.skill==P),"movement releases server P");KagenoManager.input(p,POSE_CANCEL);check(KagenoManager.casts().stream().noneMatch(x->x.player==p&&x.skill==P),"duplicate cancel never starts P");});reset();key(GLFW_KEY_E);next(5);}}
        case 5->{var c0=cast(E);if(c0!=null){int a=(int)c0.age(0);if(a%6==0)shot("E_"+a);}if(age>90){check(saved.stream().anyMatch(n->n.startsWith("E_")),"E rendered");reset();key(GLFW_KEY_T);next(6);}}
        case 6->{var c0=cast(T);if(c0!=null){int a=(int)c0.age(0);if(a==35)shot("T_front");if(a==55)BotandCamera.STATE.yaw=130;if(a==65)shot("T_side");if(a==70){BotandCamera.STATE.targetDistance=12;BotandCamera.STATE.pitch=24;}if(a==82)shot("T_overview");}if(age>135){check(saved.contains("T_front")&&saved.contains("T_side"),"T rendered from both sides");reset();server(p->{KagenoManager.input(p,T_DOWN);});next(7);}}
        case 7->{var c0=cast(RUSH);if(c0!=null){int a=(int)c0.age(0);if(a%6==0)shot("T_hold_"+a);}if(age<12)server(p->KagenoManager.input(p,PULSE));if(age>105){check(saved.stream().anyMatch(n->n.startsWith("T_hold_")),"held T rendered");reset();mc.options.attackKey.setPressed(true);next(8);}}
        case 8->{var c0=StandClient.action(mc.player.getUuid());if(c0!=null&&c0.skill()==M1){int a=(int)c0.age(0);if(a==6)shot("M1_"+c0.combo());if(c0.combo()==4&&a>7)mc.options.attackKey.setPressed(false);}if(age>=90)mc.options.attackKey.setPressed(false);if(age>120){check(saved.stream().anyMatch(n->n.startsWith("M1_")),"M1 rendered");reset();key(GLFW_KEY_H);next(9);}}
        case 9->{var c0=cast(H);if(c0!=null){int a=(int)c0.age(0);if(!Boolean.getBoolean("botand.review.videoOnly")&&(a%6==0||a==55||a==58))shot("H_"+a);}if(age>135){log.append("H renderedFrames=").append(renderFrames).append(" retreatFrames=").append(renderedBackFrames).append(" rootRange=").append(maximumActor-minimumActor).append(" rootError=").append(maxRootError).append("\n");Files.writeString(out.resolve("frames.csv"),frames);Files.writeString(out.resolve("actor-frames.csv"),actorFrames);check(Boolean.getBoolean("botand.review.videoOnly")?renderFrames>100:saved.stream().filter(n->n.startsWith("H_")).count()>12,"H cutscene and landing rendered");if(Boolean.getBoolean("botand.review.highest")){check(renderedBackFrames>=10,"actual rendered root moves opposite rendered facing during retreat");check(maximumActor-minimumActor>4.4,"actual rendered retreat spans at least 4.4 sampled blocks of the authored 5-block path");log.append("Rendered root maximum error ").append(maxRootError).append("\n");Files.writeString(out.resolve("result.txt"),log);Files.writeString(out.resolve("frames.csv"),frames);Files.writeString(out.resolve("actor-frames.csv"),actorFrames);mc.scheduleStop();phase=99;}else{reset();key(GLFW_KEY_G);next(10);}}}
        case 12->{if(age==1){BotandCamera.prepareInteraction();mc.player.bodyYaw=mc.player.prevBodyYaw=mc.player.headYaw=mc.player.prevHeadYaw=mc.player.getYaw();}if(age>12&&mc.player.isOnGround()){key(GLFW_KEY_G);BotandCamera.STATE.yaw=mc.player.getYaw()+180;next(10);}}
        case 10->{if(age==40)check(AtomicClient.poseCast(mc.player.getUuid())!=null,"G pose rendered");if(!Boolean.getBoolean("botand.review.videoOnly")&&(age==8||age==40||age==100||age==180))shot("G_"+age);if(Boolean.getBoolean("botand.review.integration")){
                if(age==160){System.setProperty("botand.review.atomicFar","true");log.append("ATOMIC_FAR ").append(java.time.Instant.now()).append('\n');}
                if(age==180){check(dev.sangwoo.celestialfall.client.atomic.AtomicAmbience.dim(mc.getTickDelta())>.7f,"2.30.3 Atomic outside sky dim retained");shot("Atomic_far_active");}
                if(age==420)shot("Atomic_far_collapse");
                if(age==560){check(dev.sangwoo.celestialfall.client.atomic.AtomicAmbience.dim(mc.getTickDelta())==0,"2.30.3 Atomic outside sky restores");shot("Atomic_far_restored");}
            }
            if(age>((Boolean.getBoolean("botand.review.atomicFar")||Boolean.getBoolean("botand.review.integration"))?580:150)){check(AtomicClient.poseCast(mc.player.getUuid())==null,"G pose releases");
            if(Boolean.getBoolean("botand.review.integration")){
                System.setProperty("botand.review.atomicFar","false");
                check(dev.sangwoo.celestialfall.client.integration.PlayerAnimatorBridge.sampledFrames()>100,"Player Animator bridge sampled in gameplay");
                check(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("photon"),"Photon absent from runtime");
                check(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ldlib"),"LDLib absent from runtime");
                check(dev.sangwoo.celestialfall.client.integration.VeilBridge.ready(),"Veil pipeline and shader loaded");
                reset();server(p->{var d=dev.sangwoo.celestialfall.training.TrainingDummies.TYPES.get(dev.sangwoo.celestialfall.training.DummyKind.IDLE).create(p.getWorld());d.refreshPositionAndAngles(.5,81,-3,0,0);p.getWorld().spawnEntity(d);});
                BotandCamera.STATE.yaw=180;next(20);break;
            }
            Files.writeString(out.resolve("result.txt"),log);Files.writeString(out.resolve("frames.csv"),frames);mc.scheduleStop();phase=99;}}
        case 20->{if(age>30){check(dev.sangwoo.celestialfall.client.integration.GeckoDummyModel.renderedFrames()>0,"GeckoLib dummy rendered");shot("Gecko_dummy");dev.sangwoo.celestialfall.client.integration.IntegrationCommands.animationDemo();next(21);}}
        case 21->{if(age==12){check(dev.sangwoo.celestialfall.client.integration.PlayerAnimatorBridge.clips(mc.player).isActive(),"JSON animation clip active");shot("PlayerAnimator_clip");}if(age>60){dev.sangwoo.celestialfall.client.integration.IntegrationCommands.stop();dev.sangwoo.celestialfall.client.integration.IntegrationCommands.cameraDemo();check(dev.sangwoo.celestialfall.client.integration.VeilBridge.preview(),"Veil preview starts alongside cinematic");next(22);}}
        case 22->{if(age==30){check(dev.sangwoo.celestialfall.client.integration.CinematicsBridge.ownsCamera(),"Immersive Cinematics owns camera");check(!BotandCamera.controls(),"orbit controls yield to external cinematic");check(dev.sangwoo.celestialfall.client.integration.VeilBridge.active(),"Veil post processing active with cinematic camera");check(dev.sangwoo.celestialfall.client.integration.VeilBridge.renderedFrames()>0,"Veil pipeline rendered");shot("Cinematics_Veil");}if(age>160){check(!dev.sangwoo.celestialfall.client.integration.CinematicsBridge.active(),"external cinematic releases camera");check(!dev.sangwoo.celestialfall.client.integration.VeilBridge.active(),"Veil preview automatically releases");dev.sangwoo.celestialfall.client.integration.VeilBridge.preview();dev.sangwoo.celestialfall.client.integration.IntegrationCommands.stop();check(!dev.sangwoo.celestialfall.client.integration.VeilBridge.active(),"stop removes Veil pipeline");dev.sangwoo.celestialfall.client.integration.IntegrationCommands.cameraDemo();dev.sangwoo.celestialfall.client.integration.VeilBridge.preview();next(23);}}
        case 23->{if(age==20){check(dev.sangwoo.celestialfall.client.integration.CinematicsBridge.active(),"camera active before disconnect");check(dev.sangwoo.celestialfall.client.integration.VeilBridge.active(),"Veil active before disconnect");next(24);mc.world.disconnect();mc.disconnect(new TitleScreen());}}
        case 24->{if(age>20){check(!dev.sangwoo.celestialfall.client.integration.VeilBridge.active(),"disconnect clears Veil pipeline");check(!dev.sangwoo.celestialfall.client.integration.CinematicsBridge.active(),"disconnect clears camera");Files.writeString(out.resolve("result.txt"),log);Files.writeString(out.resolve("frames.csv"),frames);mc.scheduleStop();phase=99;}}
        }
    }catch(Throwable t){fail(t);}});}
}
