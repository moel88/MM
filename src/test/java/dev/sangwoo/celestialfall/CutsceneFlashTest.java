package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.client.CutsceneFlash;
import dev.sangwoo.celestialfall.combat.CutsceneGlow;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A skill with a cutscene glows first and is cast as the glow ends (2.109.0, user request: "빛나는 메커니즘을 바꾸자 몸이
 * 하늘색으로 빛나고 빛이 끝나고 스킬이 시전되게 바꾸자 카게노 H 스타플래티나 비트다운 이소상 G"; answers: "80% 둔화 상태로
 * 걸을 수 있음 + 빛나는 순간에 때리면 그 스킬 끊기고 다시 쿨타임 기다려야함", "끊기고 쿨타임도 다시 흐름", "G 누름 → 빛 →
 * 여는 주먹"): Kageno's H, Star Platinum's beatdown and Lee So-sang's G (CutsceneGlow on the server, CutsceneFlash drawing it).
 */
class CutsceneFlashTest {
    private static final String GLOW="dev/sangwoo/celestialfall/combat/CutsceneGlow";

    /** Half a second: in over two frames, full, then out from 0.3 s; no jump from one frame to the next. */
    @Test void itGlowsHalfASecond(){
        assertEquals(0,CutsceneFlash.strength(-.01f),0);
        assertEquals(1,CutsceneFlash.strength(CutsceneFlash.IN),1e-6,"in over two frames (60 fps)");
        assertEquals(1,CutsceneFlash.strength(.2f),1e-6);
        assertEquals(0,CutsceneFlash.strength(CutsceneFlash.DURATION),0,"gone at half a second");
        assertEquals(.5f,CutsceneFlash.DURATION,0);
        float before=CutsceneFlash.strength(CutsceneFlash.IN);
        for(float t=CutsceneFlash.IN;t<=CutsceneFlash.DURATION;t+=1/60f){float now=CutsceneFlash.strength(t);assertTrue(Math.abs(now-before)<.25f,"no jump at "+t);before=now;}
    }
    /** The skill is cast as its glow ends ("빛이 끝나고 스킬이 시전"): the glow's whole half second, and none of it left then. */
    @Test void theSkillIsCastAsTheGlowEnds(){
        assertEquals(Math.round(CutsceneFlash.DURATION*20),CutsceneGlow.TICKS,"the glow's length in ticks");
        assertFalse(CutsceneGlow.over(100,100+CutsceneGlow.TICKS-1),"not a tick before");
        assertTrue(CutsceneGlow.over(100,100+CutsceneGlow.TICKS));
        assertEquals(0,CutsceneFlash.strength(CutsceneGlow.TICKS/20f),0,"no glow left as it is cast");
        assertTrue(CutsceneFlash.strength((CutsceneGlow.TICKS-3)/20f)>0,"still glowing just before");
    }
    /** While it glows its user walks at a fifth of their speed, as Kageno E's thrusts ("80% 둔화 상태로 걸을 수 있음"). */
    @Test void itsUserWalksAFifthAsFast(){
        assertEquals(.8,CutsceneGlow.SLOW,0);
        assertEquals(KagenoRules.E_SLOW,CutsceneGlow.SLOW,0);
    }
    /** Cut short (a blow as it glows), it fades out at once from however strong it was, with no jump. */
    @Test void aGlowCutShortFadesAtOnce(){
        float cut=.2f;
        assertEquals(CutsceneFlash.strength(cut),CutsceneFlash.strength(cut,cut),1e-6,"no jump as it is cut");
        assertEquals(0,CutsceneFlash.strength(cut+CutsceneFlash.CUT_FADE,cut),1e-6,"gone a tenth of a second on");
        assertEquals(CutsceneFlash.strength(.15f),CutsceneFlash.strength(.15f,cut),0,"as before until it is cut");
        assertEquals(CutsceneFlash.strength(.3f),CutsceneFlash.strength(.3f,Float.NaN),0,"not cut: as ever");
        float before=CutsceneFlash.strength(cut,cut);
        for(float t=cut;t<=cut+CutsceneFlash.CUT_FADE+.05f;t+=1/60f){float now=CutsceneFlash.strength(t,cut);assertTrue(now<=before+1e-6&&before-now<.5f,"fades smoothly at "+t);before=now;}
        assertTrue(CutsceneFlash.CUT_FADE<CutsceneFlash.DURATION-CutsceneFlash.OUT,"quicker than its own fade");
    }
    /** Every skill with a cutscene glows first at its key, and is cast by the glow once it is over. */
    @Test void everySkillWithACutsceneGlowsFirst()throws Exception{
        // Kageno's H: its key glows (CutsceneGlow.begin), the glow's end casts it (glowed)
        assertTrue(calls(method("dev/sangwoo/celestialfall/stand/KagenoManager","input"),GLOW,"begin"),"Kageno's H glows at its key");
        assertTrue(calls(method("dev/sangwoo/celestialfall/stand/KagenoManager","glowed"),"dev/sangwoo/celestialfall/stand/KagenoManager","begin"),"and is cast as it ends");
        // the beatdown: its key glows, its opening punch is thrown as the glow ends ("G 누름 → 빛 → 여는 주먹")
        var beatdown=method("dev/sangwoo/celestialfall/stand/StarPlatinumAttack","beatdown");
        assertTrue(calls(beatdown,GLOW,"begin"),"the beatdown glows at its key");
        assertFalse(calls(beatdown,"dev/sangwoo/celestialfall/stand/StarPlatinumAttack","beginSkill"),"its punch is not thrown at its key");
        assertTrue(calls(method("dev/sangwoo/celestialfall/stand/StarPlatinumAttack","beatdownGlowed"),"dev/sangwoo/celestialfall/stand/StarPlatinumAttack","beginSkill"),"its punch is thrown as the glow ends");
        // Lee So-sang's G: its key glows, the glow's end casts it
        var g=method("dev/sangwoo/celestialfall/isosang/IsosangAttack","ultimate");
        assertTrue(calls(g,GLOW,"begin"),"Lee So-sang's G glows at its key");
        assertFalse(news(g,"dev/sangwoo/celestialfall/isosang/IsosangAttack$Ultimate"),"it is not cast at its key");
        assertTrue(news(method("dev/sangwoo/celestialfall/isosang/IsosangAttack","ultimateGlowed"),"dev/sangwoo/celestialfall/isosang/IsosangAttack$Ultimate"),"it is cast as the glow ends");
        // the glow's end casts each, and a glow cut short runs each one's cooldown
        var cast=method(GLOW,"cast");var cool=method(GLOW,"cool");
        assertTrue(calls(cast,"dev/sangwoo/celestialfall/stand/KagenoManager","glowed")&&calls(cast,"dev/sangwoo/celestialfall/stand/StarPlatinumAttack","beatdownGlowed")&&calls(cast,"dev/sangwoo/celestialfall/isosang/IsosangAttack","ultimateGlowed"));
        assertTrue(calls(cool,"dev/sangwoo/celestialfall/stand/KagenoManager","glowCut")&&calls(cool,"dev/sangwoo/celestialfall/stand/StarPlatinumAttack","beatdownGlowCut")&&calls(cool,"dev/sangwoo/celestialfall/isosang/IsosangAttack","ultimateGlowCut"));
        // a cut beatdown's cooldown runs as for one that struck no one (beatdownOver: its whole cooldown from now, kept with the world)
        assertTrue(calls(method("dev/sangwoo/celestialfall/stand/StarPlatinumAttack","beatdownGlowCut"),"dev/sangwoo/celestialfall/stand/StarPlatinumAttack","beatdownOver"));
        // Kageno's H's and Lee So-sang's G's cooldowns run in full from the cut, told to their clients
        var h=method("dev/sangwoo/celestialfall/stand/KagenoManager","glowCut");
        assertTrue(reads(h,"dev/sangwoo/celestialfall/stand/KagenoRules","COOLDOWN")&&calls(h,"dev/sangwoo/celestialfall/stand/KagenoManager","status"));
        var gc=method("dev/sangwoo/celestialfall/isosang/IsosangAttack","ultimateGlowCut");
        // (G_COOLDOWN is a constant: its five minutes are written in)
        assertTrue(constant(gc,dev.sangwoo.celestialfall.isosang.IsosangRules.G_COOLDOWN)&&calls(gc,"dev/sangwoo/celestialfall/isosang/IsosangAttack","send"));
    }
    /** A blow landing as they glow cuts it short; while they glow they may only walk (no other skill, attack, guard, dash, nor the stand put away). */
    @Test void aBlowCutsItShortAndTheyMayOnlyWalk()throws Exception{
        boolean struck=false;
        for(var m:node("dev/sangwoo/celestialfall/mixin/LivingCombatMixin").methods)if(calls(m,GLOW,"struck"))struck=true;
        assertTrue(struck,"LivingEntity.damage's blows reach the glow");
        assertTrue(calls(method("dev/sangwoo/celestialfall/combat/CombatManager","busyOtherwise"),GLOW,"glowing"),"busy");
        assertTrue(calls(method("dev/sangwoo/celestialfall/combat/CombatManager","actionLocked"),GLOW,"glowing"),"action locked");
        assertTrue(calls(method("dev/sangwoo/celestialfall/stand/StandSummon","toggle"),GLOW,"glowing"),"the stand stays as it is");
        // their client: actions locked (only walking), the body facing the camera, the lens held still through the slow walk
        assertTrue(calls(method("dev/sangwoo/celestialfall/client/CombatClient","actionLocked"),"dev/sangwoo/celestialfall/client/CutsceneFlash","localGlowing"));
        assertTrue(calls(method("dev/sangwoo/celestialfall/client/BotandCamera","input"),"dev/sangwoo/celestialfall/client/CutsceneFlash","localGlowing"));
        assertTrue(reads(method("dev/sangwoo/celestialfall/client/StandClient","skillSlowed"),GLOW,"SLOW_ID"));
    }
    /** The clients glow as the server tells (its key), and no more as the skill is cast (2.64.0 to 2.108.0's way). */
    @Test void theGlowIsTheServersNews()throws Exception{
        for(String owner:new String[]{"dev/sangwoo/celestialfall/client/StandClient","dev/sangwoo/celestialfall/client/stand/StandAttack","dev/sangwoo/celestialfall/client/stand/StandBeatdown","dev/sangwoo/celestialfall/client/isosang/IsosangClient"})
            for(var m:node(owner).methods)assertFalse(calls(m,"dev/sangwoo/celestialfall/client/CutsceneFlash","flash"),owner+"."+m.name+" glows as the skill is cast");
        assertEquals("celestialfall:cutscene_glow_v1",CutsceneGlow.EVENT.toString());
        assertNotEquals(CutsceneGlow.BEGIN,CutsceneGlow.CUT);assertNotEquals(CutsceneGlow.CUT,CutsceneGlow.CAST);
    }
    /** A boss reads one glowing (a blow cuts it short: Jotaro jabs first), and his own beatdown waits through its glow. */
    @Test void aBossReadsTheGlow()throws Exception{
        assertTrue(calls(method("dev/sangwoo/celestialfall/boss/BossThreats","against"),"dev/sangwoo/celestialfall/boss/BossThreats","glow"));
        assertTrue(calls(method("dev/sangwoo/celestialfall/boss/JotaroBrain","skill"),GLOW,"glowing"),"his beatdown's plan waits through its glow");
    }

    private static boolean calls(MethodNode m,String owner,String name){
        for(var x:m.instructions)if(x instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))return true;
        return false;
    }
    private static boolean reads(MethodNode m,String owner,String name){
        for(var x:m.instructions)if(x instanceof FieldInsnNode f&&f.owner.equals(owner)&&f.name.equals(name))return true;
        return false;
    }
    private static boolean constant(MethodNode m,long value){
        for(var x:m.instructions){
            if(x instanceof org.objectweb.asm.tree.LdcInsnNode l&&l.cst instanceof Number n&&n.longValue()==value)return true;
            if(x instanceof org.objectweb.asm.tree.IntInsnNode i&&i.operand==value)return true;
        }
        return false;
    }
    private static boolean news(MethodNode m,String type){
        for(var x:m.instructions)if(x instanceof TypeInsnNode t&&t.getOpcode()==org.objectweb.asm.Opcodes.NEW&&t.desc.equals(type))return true;
        return false;
    }
    private static MethodNode method(String owner,String name)throws Exception{
        return node(owner).methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow(()->new AssertionError(owner+"."+name));
    }
    private static ClassNode node(String name)throws Exception{
        try(var stream=CutsceneFlashTest.class.getResourceAsStream("/"+name+".class")){
            assertNotNull(stream,name);var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
}
