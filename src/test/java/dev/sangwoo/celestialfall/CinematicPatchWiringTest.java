package dev.sangwoo.celestialfall;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks compiled integration paths without pretending to launch a Minecraft client. */
class CinematicPatchWiringTest {
    private static ClassNode read(String path) throws Exception {
        try(var stream=CinematicPatchWiringTest.class.getResourceAsStream("/"+path+".class")) {
            assertNotNull(stream);var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
    @Test void cooldownResetCannotCancelOrModifyAnActiveCast() throws Exception {
        for(String manager:new String[]{"SpellManager","AtomicManager","HeavenManager"}) {
            var c=read("dev/sangwoo/celestialfall/"+manager);
            var method=c.methods.stream().filter(m->m.name.equals("resetCooldown")).findFirst().orElseThrow();
            boolean ledger=false,itemCooldown=false;
            for(var instruction:method.instructions) {
                if(instruction instanceof FieldInsnNode f) {
                    assertNotEquals("ACTIVE",f.name);
                    if(f.name.equals("COOLDOWNS"))ledger=true;
                }
                if(instruction instanceof MethodInsnNode m) {
                    assertFalse(m.name.equals("clear") || m.name.equals("cancel") || m.name.equals("send"));
                    if(m.name.equals("getItemCooldownManager"))itemCooldown=true;
                }
            }
            assertTrue(ledger && itemCooldown,manager+" must reset server and item cooldowns");
        }
    }
    @Test void cinematicBarsDrawAfterHudAndBeforeMenusWithGuiProjection() throws Exception {
        var c=read("net/minecraft/client/render/GameRenderer");
        var method=c.methods.stream().filter(m->m.name.equals("render") && m.desc.equals("(FJZ)V")).findFirst().orElseThrow();
        int projection=-1,hud=-1,overlay=-1,screen=-1,index=0;
        for(var instruction:method.instructions) {
            if(instruction instanceof MethodInsnNode m) {
                if(m.name.equals("setProjectionMatrix"))projection=index;
                if(m.owner.endsWith("/InGameHud") && m.name.equals("render"))hud=index;
                if(m.name.equals("getOverlay") && overlay<0)overlay=index;
                if(m.name.equals("renderWithTooltip"))screen=index;
            }
            index++;
        }
        assertTrue(projection>=0 && projection<hud && hud<overlay && overlay<screen);
        var director=read("dev/sangwoo/celestialfall/client/CinematicDirector");
        for(var m:director.methods)for(var instruction:m.instructions)
            if(instruction instanceof FieldInsnNode f && f.name.equals("hudHidden"))
                assertNotEquals(org.objectweb.asm.Opcodes.PUTFIELD,f.getOpcode(),"F1 preference must not be overwritten");
    }
    @Test void permanentCooldownModeReachesAllThreeManagersAndKeepsTheirActiveGuards() throws Exception {
        for(String manager:new String[]{"SpellManager","AtomicManager","HeavenManager"}) {
            var c=read("dev/sangwoo/celestialfall/"+manager);
            var cast=c.methods.stream().filter(m->m.name.equals("cast")).findFirst().orElseThrow();
            boolean toggle=false,busy=false;
            for(var instruction:cast.instructions)if(instruction instanceof MethodInsnNode m) {
                if(m.owner.endsWith("/NoCooldownState") && m.name.equals("enabled"))toggle=true;
                if(m.name.equals("hasActive"))busy=true;
            }
            assertTrue(toggle && busy,manager);
        }
        var state=read("dev/sangwoo/celestialfall/NoCooldownState");
        for(var method:state.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode m)
            assertFalse(m.name.equals("clear") || m.name.equals("cancel"),"Mode switch must leave active effects intact");
    }
}
