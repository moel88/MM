package dev.sangwoo.gachashots;

import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.ProgressClient;
import dev.sangwoo.celestialfall.client.gacha.GachaClient;
import dev.sangwoo.celestialfall.client.gacha.GachaScreen;
import dev.sangwoo.celestialfall.client.menu.BotandMenuScreen;
import dev.sangwoo.celestialfall.client.menu.MenuClient;
import dev.sangwoo.celestialfall.client.menu.MenuLayout;
import dev.sangwoo.celestialfall.gacha.GachaRules;
import dev.sangwoo.celestialfall.gacha.GachaState;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Visual QA of the gacha (2.39.0), not shipped: a flat world on the dedicated-map rules, then each entry of
 * -Dbotand.gacha.list runs as a player would through the real screens, clicking the real buttons, and every rendered
 * frame is saved as OUT/ENTRY/INDEX_TICKS.png on the entry's own clock (ticks, smooth between ticks).
 * MENU: the menu's dock, the 뽑기 button, the gacha screen, its details. STAND: the gacha with a stand equipped.
 * R1, SR1, SSR1: a single pull whose count makes that grade likely (R) or certain (SR, SSR). TEN: a ten-pull with an SR
 * certain on its 4th pull and an SSR on its 6th. A pull's grades are logged to log.txt.
 */
public final class GachaShots implements ClientModInitializer {
    static GachaShots instance;
    static final Path OUT=Path.of(System.getProperty("botand.shots.out","gacha-shots"));
    static final int SLOW=Integer.getInteger("botand.shots.slow",1);
    private final ExecutorService writer=Executors.newFixedThreadPool(3,r->{var t=new Thread(r,"shot-writer");t.setDaemon(true);return t;});
    /** Frames waiting to be written; past a few, frames are dropped rather than queued (each holds a full screenshot). */
    private final java.util.concurrent.atomic.AtomicInteger waiting=new java.util.concurrent.atomic.AtomicInteger();
    private float lastSaved=-1;
    private final List<Future<?>> jobs=new ArrayList<>();
    private MinecraftClient mc;private int phase,age,index;private long base;private boolean slowed;
    private final Deque<String> queue=new ArrayDeque<>(Arrays.asList(System.getProperty("botand.gacha.list","MENU,STAND,R1,SR1,SSR1,TEN").split(",")));
    private String current;private final StringBuilder log=new StringBuilder();private int end,retries,dropped;private boolean redo;
    private final long started=System.nanoTime();

    @Override public void onInitializeClient(){
        instance=this;
        ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{tick();}catch(Throwable t){fail(t);}});
    }
    public static void afterFrame(){if(instance!=null)try{instance.frame();}catch(Throwable t){instance.fail(t);}}
    private void fail(Throwable t){t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("error.txt"),phase+" "+current+" "+t+"\n"+log);}catch(Exception ignored){}mc.scheduleStop();phase=99;}
    private void server(Consumer<ServerPlayerEntity> f){mc.getServer().submit(()->f.accept(mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()))).join();}
    private float clock(){return (mc.world.getTime()-base)+mc.getTickDelta();}

    /**
     * Saves this frame: at most one per 0.15 tick of the entry's clock (twice what film.py's 60 fps needs, one per
     * third), and none while too many are still being written, so memory never fills.
     */
    private void frame()throws Exception{
        if(phase!=4||current==null||mc.world==null)return;
        float t=clock();if(t<0||t>end)return;
        if(index>0&&t-lastSaved<.15f)return;
        if(waiting.get()>12){dropped++;return;}
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());lastSaved=t;waiting.incrementAndGet();
        String name=String.format(Locale.ROOT,"%s/%05d_%07.2f.png",current,index++,t);
        jobs.add(writer.submit(()->{try(image){Path p=OUT.resolve(name);Files.createDirectories(p.getParent());image.writeTo(p);}catch(Exception e){throw new RuntimeException(e);}finally{waiting.decrementAndGet();}}));
        jobs.removeIf(Future::isDone);
    }
    private void setup(ServerPlayerEntity p){
        var w=p.getServerWorld();MapGate.designate(w,true);
        w.setTimeOfDay(6000);w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,w.getServer());w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,w.getServer());
        w.setWeather(999999,0,false,false);p.changeGameMode(GameMode.SURVIVAL);
        int y=p.getBlockY()-1;
        for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)w.setBlockState(new BlockPos(x,y,z),((x>>2)+(z>>2)&1)==0?Blocks.LIGHT_GRAY_CONCRETE.getDefaultState():Blocks.POLISHED_ANDESITE.getDefaultState());
        for(int x=-14;x<=14;x+=7)for(int h=0;h<12;h++)w.setBlockState(new BlockPos(x,y+1+h,-26),Blocks.LIGHT_BLUE_TERRACOTTA.getDefaultState());
        ProgressManager.set(p,new ProgressProfile(12,0,0,"none",0,0));
        p.getInventory().clear();p.currentScreenHandler.syncState();
        p.networkHandler.requestTeleport(.5,y+1,.5,180,8);
    }
    private void count(int sinceSsr,int sinceSr){server(p->{var s=GachaState.get(p.getServer());s.set(p.getUuid(),new GachaRules.Count(sinceSsr,sinceSr,s.get(p.getUuid()).pulls()));});}
    private void stand(String id){server(p->ProgressManager.set(p,ProgressManager.profile(p).withStand(id)));}
    /** Calls one of the gacha screen's own layout methods (the shape of a button) and returns its centre. */
    private double[] centre(String shape)throws Exception{
        var m=GachaScreen.class.getDeclaredMethod(shape);m.setAccessible(true);float[] p=(float[])m.invoke(mc.currentScreen);
        double x=0,y=0;for(int i=0;i<p.length;i+=2){x+=p[i];y+=p[i+1];}return new double[]{x/(p.length/2),y/(p.length/2)};
    }
    private void click(String shape)throws Exception{
        if(!(mc.currentScreen instanceof GachaScreen s))throw new IllegalStateException("no gacha screen for "+shape+": "+mc.currentScreen);
        double[] c=centre(shape);cursor(c[0],c[1]);s.mouseClicked(c[0],c[1],0);log.append("  click ").append(shape).append(" at ").append(Math.round(c[0])).append(',').append(Math.round(c[1])).append('\n');
    }
    private void hover(String shape)throws Exception{if(mc.currentScreen instanceof GachaScreen){double[] c=centre(shape);cursor(c[0],c[1]);}}
    /** Moves the real pointer (GUI units), so the screens see it as a player's. */
    private void cursor(double x,double y){var w=mc.getWindow();org.lwjgl.glfw.GLFW.glfwSetCursorPos(w.getHandle(),x*w.getScaleFactor(),y*w.getScaleFactor());}
    private static final int LEAD=30;

    private void tick()throws Exception{
        age++;if((System.nanoTime()-started)/1e9>1500*SLOW)throw new IllegalStateException("timeout");
        if(mc.getToastManager()!=null)mc.getToastManager().clear();
        if(phase>=1&&phase<99&&mc.world!=null&&mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen)mc.setScreen(null);
        if(SLOW>1&&!slowed&&phase>=3){var f=MinecraftClient.class.getDeclaredField("renderTickCounter");f.setAccessible(true);var counter=f.get(mc);var t=counter.getClass().getDeclaredField("tickTime");t.setAccessible(true);t.setFloat(counter,50f*SLOW);slowed=true;}
        switch(phase){
            case 0->{if(mc.currentScreen!=null&&mc.getOverlay()==null&&age>100){
                Files.createDirectories(OUT);var o=mc.options;o.getViewDistance().setValue(6);o.getMaxFps().setValue(260);o.getGuiScale().setValue(2);o.getBobView().setValue(false);
                mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                var info=new LevelInfo("GachaFlat"+System.nanoTime(),GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),DataConfiguration.SAFE_MODE);
                mc.createIntegratedServerLoader().createAndStart(info.getLevelName(),info,new GeneratorOptions(1,false,false),r->r.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder());
                phase=1;age=0;}}
            case 1->{
                if(mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);}
                if(mc.player!=null&&mc.world!=null&&mc.currentScreen==null&&age>60){server(this::setup);phase=2;age=0;}}
            case 2->{if(age>80&&MapClient.enabled()&&MenuClient.available()&&"none".equals(ProgressClient.profile.stand())){phase=3;age=0;}else if(age>600)throw new IllegalStateException("map never enabled: "+MapClient.enabled());}
            case 3->{
                if(age==1){
                    if(queue.isEmpty()){for(var j:jobs)j.get();Files.writeString(OUT.resolve("log.txt"),log);mc.scheduleStop();phase=99;return;}
                    current=queue.poll();index=0;lastSaved=-1;mc.setScreen(null);
                    switch(current){
                        case "STAND"->stand("kageno");
                        case "R1"->{stand("none");count(0,0);}
                        case "SR1"->{stand("none");count(0,9);}
                        case "SSR1"->{stand("none");count(99,0);}
                        case "TEN"->{stand("none");count(94,6);}
                        default->stand("none");
                    }
                }
                if(age>=LEAD){base=mc.world.getTime();end=switch(current){case "MENU"->220;case "STAND"->90;default->600;};log.append(current).append(" at ").append(base).append('\n');phase=4;age=0;
                    if(!current.equals("MENU"))GachaClient.open();else MenuClient.open();}
            }
            case 4->{
                float t=clock();
                if(current.equals("MENU")){
                    if(age==14&&mc.currentScreen instanceof BotandMenuScreen menu)for(var d:MenuLayout.dock(menu.width,menu.height,true))if(d.id().equals("draw"))cursor(d.rect().x()+d.rect().w()/2,d.rect().y()+d.rect().h()/2);
                    if(age==30&&mc.currentScreen instanceof BotandMenuScreen menu){
                        for(var d:MenuLayout.dock(menu.width,menu.height,true))if(d.id().equals("draw")){double x=d.rect().x()+d.rect().w()/2,y=d.rect().y()+d.rect().h()/2;cursor(x,y);menu.mouseClicked(x,y,0);log.append("  dock 뽑기 enabled=").append(d.enabled()).append('\n');}
                    }
                    if(age==100)hover("detailButton");
                    if(age==112)click("detailButton");
                    if(age==190)click("panelClose");
                }
                if(current.equals("STAND")&&age==40)hover("pullOne");
                if(current.endsWith("1")||current.equals("TEN")){
                    String button=current.equals("TEN")?"pullTen":"pullOne";
                    if(age==24)hover(button);
                    if(age==36)click(button);
                    // the scene, then the results for a while
                    if(end==600&&mc.currentScreen instanceof GachaScreen s&&String.valueOf(stageOf(s)).equals("RESULT")){end=(int)t+70;log.append("  results at ").append(Math.round(t)).append(' ').append(grades(s)).append(" stand=").append(ProgressClient.profile.stand()).append('\n');
                        // R1 films an R: a count with no guarantee gives one 94% of the time; another roll films the R
                        if(current.equals("R1")&&!String.valueOf(grades(s)).equals("[R]")&&retries++<4){log.append("  not an R: again\n");queue.addFirst("R1");end=(int)t+2;redo=true;}}
                }
                if(t>end+2){
                    log.append(current).append(" frames ").append(index).append(" dropped ").append(dropped).append('\n');dropped=0;
                    if(redo){for(var j:jobs)j.get();try(var files=Files.walk(OUT.resolve(current))){files.sorted(Comparator.reverseOrder()).forEach(f->f.toFile().delete());}redo=false;}
                    if(current.equals("STAND"))stand("none");
                    phase=3;age=0;
                }
            }
        }
    }
    private static Object stageOf(GachaScreen s)throws Exception{var m=GachaScreen.class.getDeclaredMethod("stage");m.setAccessible(true);return m.invoke(s);}
    private static Object grades(GachaScreen s)throws Exception{var f=GachaScreen.class.getDeclaredField("grades");f.setAccessible(true);return f.get(s);}
}
