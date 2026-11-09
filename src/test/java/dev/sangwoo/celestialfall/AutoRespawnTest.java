package dev.sangwoo.celestialfall;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Back to life by itself (2.107.0, user request: "일단 죽으면 리스폰 화면이 뜨지 않고 자동으로 부활되게 하는데 죽으면 그 자리에서
 * 5초 동안 화면만 움직일수 있게 하다가 자동 부활"): five seconds (100 server ticks) from the tick the server first finds
 * them dead, at once with doImmediateRespawn; and the paths of Minecraft's own where its death screen opens and where
 * a respawn is answered are the ones the mixins take.
 */
class AutoRespawnTest {
    @Test void fiveSecondsFromTheTickFoundDead(){
        assertEquals(100,AutoRespawn.DELAY,"five seconds");
        assertFalse(AutoRespawn.due(1000,1000,AutoRespawn.DELAY),"the tick they die");
        assertFalse(AutoRespawn.due(1000,1099,AutoRespawn.DELAY),"a tick short");
        assertTrue(AutoRespawn.due(1000,1100,AutoRespawn.DELAY),"five seconds on");
        assertTrue(AutoRespawn.due(1000,1350,AutoRespawn.DELAY),"later still (a server that could not bring them back on time)");
    }
    @Test void atOnceWithImmediateRespawn(){assertTrue(AutoRespawn.due(1000,1000,0));}
    @Test void aClockGoneBackHoldsNobody(){assertTrue(AutoRespawn.due(5000,20,AutoRespawn.DELAY));}

    /** setScreen(null) with the player dead opens the death screen or asks to come back, only past its one isDead (redirected). */
    @Test void closingAScreenOpensTheDeathScreenOnlyPastItsOneDeathCheck()throws Exception{
        var calls=calls(method("net/minecraft/client/MinecraftClient","setScreen","(Lnet/minecraft/client/gui/screen/Screen;)V"));
        assertEquals(1,count(calls,"isDead"),"one isDead in setScreen");
        int dead=index(calls,"isDead"),screen=index(calls,"<init>","net/minecraft/client/gui/screen/DeathScreen"),ask=index(calls,"requestRespawn");
        assertTrue(dead>=0&&dead<screen&&dead<ask,"the death screen and the ask both behind it");
    }
    /** Each tick with nothing open and the player dead the game opens it of itself, past its one isDead in tick (redirected). */
    @Test void eachTickTheGameOpensItPastItsOneDeathCheck()throws Exception{
        var calls=calls(method("net/minecraft/client/MinecraftClient","tick","()V"));
        assertEquals(1,count(calls,"isDead"),"one isDead in tick");
        int dead=index(calls,"isDead");
        assertEquals("setScreen",calls.get(dead+1).name,"right after it, setScreen (with no screen: the death screen)");
    }
    /** The death message opens the death screen, or asks to come back, after the packet is on the game's thread (cancelled there). */
    @Test void theDeathMessageOpensItAfterTheGamesThread()throws Exception{
        var calls=calls(method("net/minecraft/client/network/ClientPlayNetworkHandler","onDeathMessage","(Lnet/minecraft/network/packet/s2c/play/DeathMessageS2CPacket;)V"));
        int thread=index(calls,"forceMainThread");
        assertEquals(0,thread,"first the game's thread");
        assertTrue(index(calls,"<init>","net/minecraft/client/gui/screen/DeathScreen")>thread);
        assertTrue(index(calls,"requestRespawn")>thread);
    }
    /** A respawn asked for is answered after the packet is on the server's thread (held there while too soon). */
    @Test void theServerAnswersTheAskAfterItsThread()throws Exception{
        var calls=calls(method("net/minecraft/server/network/ServerPlayNetworkHandler","onClientStatus","(Lnet/minecraft/network/packet/c2s/play/ClientStatusC2SPacket;)V"));
        int thread=index(calls,"forceMainThread");
        assertTrue(thread>=0&&calls.subList(0,thread).stream().allMatch(c->c.name.equals("getServerWorld")),"first the server's thread");
        assertEquals(2,count(calls,"respawnPlayer"),"leaving the End and coming back to life");
        assertTrue(index(calls,"respawnPlayer")>thread);
    }
    /**
     * Minecraft takes the dead out of the world a second (20 ticks) after they die, on the server (LivingEntity, in its
     * puff of smoke: status 60) and on their own client (ClientPlayerEntity, of itself). Taken out on the server, the five
     * seconds would show an emptied world, so DeadBodyMixin keeps them in it (2.107.0); their body still goes as
     * Minecraft's does (2.107.1, user request: "죽고 시체가 원래 처럼 소멸하는 건 똑같게 해줘"): the same second, the same
     * puff ({@link AutoRespawn#VANISH}), and out of everyone else's world (AutoRespawn.hide).
     */
    @Test void theDeadAreTakenOutASecondOnInAPuffOfSmoke()throws Exception{
        for(String owner:new String[]{"net/minecraft/entity/LivingEntity","net/minecraft/client/network/ClientPlayerEntity"}){
            var m=method(owner,"updatePostDeath","()V");boolean twenty=false,killed=false,removed=false;
            for(var i:m.instructions){
                if(i instanceof IntInsnNode n&&n.operand==AutoRespawn.VANISH)twenty=true;
                if(i instanceof FieldInsnNode f&&f.name.equals("KILLED"))killed=true;
                if(i instanceof MethodInsnNode c&&c.name.equals("remove"))removed=true;
            }
            assertTrue(twenty&&killed&&removed,owner+": out at 20 ticks, killed");
        }
        assertEquals(20,AutoRespawn.VANISH,"Minecraft's second");
        boolean puff=false;
        for(var i:method("net/minecraft/entity/LivingEntity","updatePostDeath","()V").instructions)
            if(i instanceof IntInsnNode n&&n.operand==net.minecraft.entity.EntityStatuses.ADD_DEATH_PARTICLES)puff=true;
        assertTrue(puff,"the server's puff of smoke is the one DeadBodyMixin sends");
    }
    /**
     * (2.107.1) Their own client takes their body out of their world as Minecraft does, a second on: no client mixin
     * keeps it (2.107.0's ClientPlayerMixin did; not to come back).
     */
    @Test void theirOwnClientTakesTheBodyOutAsMinecraftDoes()throws Exception{
        var client=JsonParser.parseReader(new InputStreamReader(AutoRespawnTest.class.getResourceAsStream("/celestialfall.client.mixins.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        String pkg=client.get("package").getAsString().replace('.','/');
        for(var name:client.getAsJsonArray("client")){
            try(var stream=AutoRespawnTest.class.getResourceAsStream("/"+pkg+"/"+name.getAsString().replace('.','/')+".class")){
                assertNotNull(stream,name.getAsString());var node=new ClassNode();new ClassReader(stream).accept(node,0);
                for(var m:node.methods)if(m.visibleAnnotations!=null)for(var a:m.visibleAnnotations)
                    if(a.desc.contains("/injection/")&&String.valueOf(a.values).contains("updatePostDeath"))
                        fail(name.getAsString()+"."+m.name+" keeps the body on their own client");
            }
        }
    }
    /**
     * (2.107.1, user request: "화면이 기울어 지는 건 고치고") The roll of the view while dead is Minecraft's tiltViewWhenHurt
     * (its death roll reads the death time): GameRendererMixin cancels it while dead, waiting to come back.
     */
    @Test void theDeathRollIsTheHurtTilt()throws Exception{
        var m=method("net/minecraft/client/render/GameRenderer","tiltViewWhenHurt","(Lnet/minecraft/client/util/math/MatrixStack;F)V");
        boolean deathTime=false;
        for(var i:m.instructions){
            if(i instanceof FieldInsnNode f&&f.name.equals("deathTime"))deathTime=true;
        }
        assertTrue(deathTime,"the death roll grows with the death time");
        var mixin=new ClassNode();
        try(var stream=AutoRespawnTest.class.getResourceAsStream("/dev/sangwoo/celestialfall/mixin/client/GameRendererMixin.class")){new ClassReader(stream).accept(mixin,0);}
        var steady=mixin.methods.stream().filter(x->x.visibleAnnotations!=null&&x.visibleAnnotations.stream().anyMatch(a->String.valueOf(a.values).contains("tiltViewWhenHurt"))).findFirst().orElseThrow();
        boolean asks=false;
        for(var i:steady.instructions)if(i instanceof MethodInsnNode c&&c.owner.endsWith("client/DeathView")&&c.name.equals("active"))asks=true;
        assertTrue(asks,"cancelled while DeathView is active");
    }
    /**
     * (2.110.1, user request: "맞을때 화면 흔들리는 거 없애줘", the answer: Minecraft's tilt alone) On the map no blow
     * tilts the view, alive or dead (GameRendererMixin asks MapClient.enabled: off the map and in one's vanilla mode it is
     * Minecraft's); the skills' own shakes are left (CameraMixin still adds KagenoCamera.shake).
     */
    @Test void noBlowTiltsTheViewOnTheMap()throws Exception{
        var mixin=new ClassNode();
        try(var stream=AutoRespawnTest.class.getResourceAsStream("/dev/sangwoo/celestialfall/mixin/client/GameRendererMixin.class")){new ClassReader(stream).accept(mixin,0);}
        var steady=mixin.methods.stream().filter(x->x.visibleAnnotations!=null&&x.visibleAnnotations.stream().anyMatch(a->String.valueOf(a.values).contains("tiltViewWhenHurt"))).findFirst().orElseThrow();
        boolean map=false;
        for(var i:steady.instructions)if(i instanceof MethodInsnNode c&&c.owner.endsWith("client/MapClient")&&c.name.equals("enabled"))map=true;
        assertTrue(map,"cancelled on the map, alive too");
        var camera=new ClassNode();
        try(var stream=AutoRespawnTest.class.getResourceAsStream("/dev/sangwoo/celestialfall/mixin/client/CameraMixin.class")){new ClassReader(stream).accept(camera,0);}
        boolean shakes=false;
        for(var m:camera.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.endsWith("kageno/KagenoCamera")&&c.name.equals("shake"))shakes=true;
        assertTrue(shakes,"the skills' own shakes stay");
    }
    /**
     * (2.107.2, user request: "화면은 왜 확대되는데", then to take it away) Minecraft narrows a dead body's view
     * (GameRenderer.getFov: divided by (1-500/(min(deathTime+delta,20)+500))*2+1, some 7% at a second); DeathView
     * gives the same factor and GameRendererMixin multiplies the view back by it while dead, waiting to come back.
     */
    @Test void theDeathZoomIsTakenBackOut()throws Exception{
        var m=method("net/minecraft/client/render/GameRenderer","getFov","(Lnet/minecraft/client/render/Camera;FZ)D");
        boolean dead=false,time=false,cap=false;int fives=0;
        for(var i:m.instructions){
            if(i instanceof MethodInsnNode c&&c.name.equals("isDead"))dead=true;
            if(i instanceof FieldInsnNode f&&f.name.equals("deathTime"))time=true;
            if(i instanceof LdcInsnNode l&&Float.valueOf(20f).equals(l.cst))cap=true;
            if(i instanceof LdcInsnNode l&&Float.valueOf(500f).equals(l.cst))fives++;
        }
        assertTrue(dead&&time&&cap&&fives==2,"Minecraft's narrowing of a dead body's view");
        assertEquals(1,dev.sangwoo.celestialfall.client.DeathView.narrowing(0,0),1e-9,"none as they die");
        assertEquals(1+2*(1-500/520.0),dev.sangwoo.celestialfall.client.DeathView.narrowing(20,0),1e-6,"some 7.7% a second on");
        assertEquals(dev.sangwoo.celestialfall.client.DeathView.narrowing(19,1),dev.sangwoo.celestialfall.client.DeathView.narrowing(57,0.5f),0,"no further after");
        for(int t=0;t<20;t++)assertTrue(dev.sangwoo.celestialfall.client.DeathView.narrowing(t+1,0)>dev.sangwoo.celestialfall.client.DeathView.narrowing(t,0),"closing in through the second");
        var mixin=new ClassNode();
        try(var stream=AutoRespawnTest.class.getResourceAsStream("/dev/sangwoo/celestialfall/mixin/client/GameRendererMixin.class")){new ClassReader(stream).accept(mixin,0);}
        var fov=mixin.methods.stream().filter(x->x.visibleAnnotations!=null&&x.visibleAnnotations.stream().anyMatch(a->String.valueOf(a.values).contains("getFov"))).findFirst().orElseThrow();
        boolean back=false;
        for(var i:fov.instructions)if(i instanceof MethodInsnNode c&&c.owner.endsWith("client/DeathView")&&c.name.equals("unzoom"))back=true;
        assertTrue(back,"multiplied back while dead");
    }
    @Test void theMixinsAreListed()throws Exception{
        var common=list("/celestialfall.mixins.json","mixins");
        assertTrue(common.contains("AutoRespawnMixin")&&common.contains("DeadBodyMixin"));
        assertTrue(list("/celestialfall.client.mixins.json","client").contains("DeathMessageMixin"));
    }

    private static MethodNode method(String owner,String name,String desc)throws Exception{
        try(var stream=AutoRespawnTest.class.getResourceAsStream("/"+owner+".class")){
            assertNotNull(stream,owner);var node=new ClassNode();new ClassReader(stream).accept(node,0);
            return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow(()->new AssertionError(owner+"."+name));
        }
    }
    private static List<MethodInsnNode> calls(MethodNode m){
        var out=new ArrayList<MethodInsnNode>();
        for(var i:m.instructions)if(i instanceof MethodInsnNode call)out.add(call);
        return out;
    }
    private static int count(List<MethodInsnNode> calls,String name){return (int)calls.stream().filter(c->c.name.equals(name)).count();}
    private static int index(List<MethodInsnNode> calls,String name){for(int i=0;i<calls.size();i++)if(calls.get(i).name.equals(name))return i;return -1;}
    private static int index(List<MethodInsnNode> calls,String name,String owner){for(int i=0;i<calls.size();i++)if(calls.get(i).name.equals(name)&&calls.get(i).owner.equals(owner))return i;return -1;}
    private static String list(String path,String key)throws Exception{
        try(var in=AutoRespawnTest.class.getResourceAsStream(path)){
            assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().get(key).toString();
        }
    }
}
