package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.map.MapState;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain Minecraft for one player on the Botand map (2.110.0, user request: "이제 명령어 하나 추가하자 명령어를 입력하면
 * 원래 크리에이티브 모드로 돌아오게 지금은 키배치나 모션이 전부 다르잖아 명령어 치면 키배치나 모션이나 전부 원래 바닐라
 * 크리에이티브 모드로 돌아오게"; answers: only whoever types it, "/botand vanilla", back to the game mode they had before).
 */
class VanillaModeTest {
    private static final String MODE="dev/sangwoo/celestialfall/map/VanillaMode",GATE="dev/sangwoo/celestialfall/map/MapGate";
    private static final String PER_PLAYER="(Lnet/minecraft/entity/player/PlayerEntity;)Z";

    /** "/botand vanilla" toggles, "on" and "off" set it; operators only (it gives creative). */
    @Test void theCommandIsTheOperators()throws Exception{
        var mode=node(MODE);var command=method(mode,"command");
        assertTrue(ldc(command,"vanilla")&&ldc(command,"on")&&ldc(command,"off"),"/botand vanilla [on|off]");
        boolean operators=false;
        for(var m:mode.methods)for(var x:m.instructions)if(x instanceof MethodInsnNode c&&c.name.equals("hasPermissionLevel")
            &&c.getPrevious() instanceof InsnNode i&&i.getOpcode()==Opcodes.ICONST_2)operators=true;
        assertTrue(operators,"permission level 2");
        boolean registered=false;
        for(var m:node("dev/sangwoo/celestialfall/CelestialFall").methods)if(calls(m,MODE,"command"))registered=true;
        assertTrue(registered,"under /botand");
    }
    /** The world keeps who plays as plain Minecraft and the game mode they had (so it holds through leaving and coming back). */
    @Test void theWorldKeepsWhoPlaysVanilla(){
        var id=UUID.fromString("0f0e0d0c-0b0a-0908-0706-050403020100");
        var state=new MapState();
        assertNull(state.vanilla(id));
        state.vanilla(id,"adventure");
        var read=MapState.read(state.writeNbt(new NbtCompound()));
        assertEquals("adventure",read.vanilla(id),"kept with its game mode");
        read.vanilla(id,null);
        assertNull(MapState.read(read.writeNbt(new NbtCompound())).vanilla(id),"off: gone");
    }
    /** On: the game mode they have remembered, creative; off: the game mode they had back. */
    @Test void creativeOnAndTheirOwnGameModeBack()throws Exception{
        var on=method(node(MODE),"turnOn");
        assertTrue(reads(on,"net/minecraft/world/GameMode","CREATIVE")&&calls(on,"net/minecraft/server/network/ServerPlayerEntity","changeGameMode"),"creative");
        assertTrue(calls(on,"net/minecraft/world/GameMode","getName")&&calls(on,"dev/sangwoo/celestialfall/map/MapState","vanilla"),"the game mode before remembered");
        var off=method(node(MODE),"turnOff");
        assertTrue(calls(off,"net/minecraft/world/GameMode","byName")&&calls(off,"net/minecraft/server/network/ServerPlayerEntity","changeGameMode"),"the game mode before given back");
        assertTrue(calls(on,GATE,"refresh")&&calls(off,GATE,"refresh"),"their client told at once, their health and combat as the map's or Minecraft's");
    }
    /** The map is off for them alone: their client hears so, and the map's rules leave them (no stand, no combo, Minecraft's health and death). */
    @Test void theMapIsOffForThemAlone()throws Exception{
        assertTrue(callsDesc(method(node(GATE),"sync"),GATE,"enabled",PER_PLAYER),"their client told the map is off for them");
        assertTrue(callsDesc(method(node(GATE),"enabled",PER_PLAYER),MODE,"on","(Lnet/minecraft/entity/player/PlayerEntity;)Z"),"their own vanilla mode");
        assertTrue(callsDesc(method(node("dev/sangwoo/celestialfall/stand/StandManager"),"has"),GATE,"enabled",PER_PLAYER),"no stand nor character");
        assertTrue(callsDesc(method(node("dev/sangwoo/celestialfall/progression/ProgressManager"),"applyHealth"),GATE,"enabled",PER_PLAYER),"Minecraft's health");
        assertTrue(callsDesc(method(node("dev/sangwoo/celestialfall/AutoRespawn"),"waits"),GATE,"enabled",PER_PLAYER),"Minecraft's death screen");
        assertTrue(callsDesc(method(node("dev/sangwoo/celestialfall/stand/KagenoManager"),"valid"),GATE,"enabled",PER_PLAYER),"no Kageno skill");
        boolean attack=false;
        for(var m:node("dev/sangwoo/celestialfall/combat/CombatManager").methods)if(m.name.startsWith("lambda$")&&callsDesc(m,GATE,"enabled",PER_PLAYER)&&calls(m,GATE,"botandItem"))attack=true;
        assertTrue(attack,"Minecraft's own attack");
        assertTrue(callsDesc(method(node("dev/sangwoo/celestialfall/combat/CutsceneGlow"),"begin"),GATE,"enabled",PER_PLAYER));
        assertTrue(callsDesc(method(node("dev/sangwoo/celestialfall/combat/HitCounter"),"count"),GATE,"enabled",PER_PLAYER));
    }
    /** Nobody's skills take them: PvP with them off both ways (every skill asks it), Kageno's holds, the time stop. */
    @Test void nobodysSkillsTakeThem()throws Exception{
        assertTrue(calls(method(node("dev/sangwoo/celestialfall/mixin/BossPvpMixin"),"botand$boss"),MODE,"on"),"PvP");
        assertTrue(calls(method(node("dev/sangwoo/celestialfall/stand/KagenoManager"),"frozen"),MODE,"on"),"never held");
        boolean near=false;
        for(var m:node("dev/sangwoo/celestialfall/stand/KagenoManager").methods)if(m.name.startsWith("lambda$near")&&calls(m,MODE,"on"))near=true;
        assertTrue(near,"Kageno's R and H take them not");
        assertTrue(calls(method(node("dev/sangwoo/celestialfall/stand/StarPlatinumTimeStop"),"passedBy"),MODE,"on"),"the time stop passes them by");
    }
    /** Every client draws their body as Minecraft's: the flag every client hears (a bit of the stand's byte, kept as the stand's change). */
    @Test void everyClientDrawsThemAsMinecraftsBody()throws Exception{
        assertTrue(calls(method(node("dev/sangwoo/celestialfall/client/CombatClient"),"animate"),MODE,"on"),"Minecraft's motions");
        assertTrue(calls(method(node(MODE),"on"),"dev/sangwoo/celestialfall/map/VanillaHolder","botand$isVanilla"),"a client reads the flag");
        var mixin=node("dev/sangwoo/celestialfall/mixin/PlayerStandMixin");
        assertTrue(mixin.interfaces.contains("dev/sangwoo/celestialfall/map/VanillaHolder"));
        assertTrue(calls(method(mixin,"botand$setStandOut"),"dev/sangwoo/celestialfall/mixin/PlayerStandMixin","vanillaBit")&&calls(method(mixin,"botand$callStand"),"dev/sangwoo/celestialfall/mixin/PlayerStandMixin","vanillaBit"),"the stand's bits keep it");
    }
    /** What it says, in both languages. */
    @Test void itSaysSo()throws Exception{
        for(String lang:new String[]{"ko_kr","en_us"})try(var in=VanillaModeTest.class.getResourceAsStream("/assets/celestialfall/lang/"+lang+".json")){
            assertNotNull(in,lang);var text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            for(String key:new String[]{"message.botand.vanilla_on","message.botand.vanilla_off","message.botand.vanilla_already_on","message.botand.vanilla_already_off"})assertTrue(text.contains("\""+key+"\""),lang+" "+key);
        }
    }

    private static boolean calls(MethodNode m,String owner,String name){
        for(var x:m.instructions)if(x instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))return true;
        return false;
    }
    private static boolean callsDesc(MethodNode m,String owner,String name,String desc){
        for(var x:m.instructions)if(x instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name)&&c.desc.equals(desc))return true;
        return false;
    }
    private static boolean reads(MethodNode m,String owner,String name){
        for(var x:m.instructions)if(x instanceof FieldInsnNode f&&f.owner.equals(owner)&&f.name.equals(name))return true;
        return false;
    }
    private static boolean ldc(MethodNode m,String text){
        for(var x:m.instructions)if(x instanceof LdcInsnNode l&&text.equals(l.cst))return true;
        return false;
    }
    private static MethodNode method(ClassNode node,String name){
        return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow(()->new AssertionError(node.name+"."+name));
    }
    private static MethodNode method(ClassNode node,String name,String desc){
        return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow(()->new AssertionError(node.name+"."+name+desc));
    }
    private static ClassNode node(String name)throws Exception{
        try(var stream=VanillaModeTest.class.getResourceAsStream("/"+name+".class")){
            assertNotNull(stream,name);var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
}
