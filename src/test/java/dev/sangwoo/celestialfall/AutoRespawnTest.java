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
     * Minecraft takes the dead out of the world a second (20 ticks) after they die, on the server (LivingEntity) and on
     * their own client (ClientPlayerEntity, of itself): the five seconds would show an emptied world, so DeadBodyMixin
     * and ClientPlayerMixin keep the body until they come back.
     */
    @Test void theDeadAreTakenOutASecondOnUnlessKept()throws Exception{
        for(String owner:new String[]{"net/minecraft/entity/LivingEntity","net/minecraft/client/network/ClientPlayerEntity"}){
            var m=method(owner,"updatePostDeath","()V");boolean twenty=false,killed=false,removed=false;
            for(var i:m.instructions){
                if(i instanceof IntInsnNode n&&n.operand==20)twenty=true;
                if(i instanceof FieldInsnNode f&&f.name.equals("KILLED"))killed=true;
                if(i instanceof MethodInsnNode c&&c.name.equals("remove"))removed=true;
            }
            assertTrue(twenty&&killed&&removed,owner+": out at 20 ticks, killed");
        }
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
