package dev.sangwoo.celestialfall;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Prevent a cosmetic camera tour from acquiring world, player or persistent-option side effects. */
class TitleCameraIsolationTest {
    @Test void tourNeverMutatesAnEntityWorldOptionsOrSendsARequest() throws Exception {
        var forbidden=Set.of("teleport","requestTeleport","setPos","setPosition","setYaw","setPitch",
            "setVelocity","setCameraEntity","setPerspective","setBlockState","setValue","send",
            "sendPacket","loadChunk","addTicket","setChunkForced","write");
        for(String name:new String[]{"TitleMapCamera","TitleMapCamera$1","TitleCameraTour"}){
            var node=new ClassNode();
            try(var in=getClass().getResourceAsStream("/dev/sangwoo/celestialfall/client/title/"+name+".class")){
                assertNotNull(in);new ClassReader(in).accept(node,0);
            }
            for(var method:node.methods)for(var insn:method.instructions){
                if(insn instanceof MethodInsnNode m)assertFalse(forbidden.contains(m.name),name+" called "+m.owner+"."+m.name);
                if(insn instanceof FieldInsnNode f && (f.getOpcode()==Opcodes.PUTFIELD||f.getOpcode()==Opcodes.PUTSTATIC))
                    assertTrue(f.owner.startsWith("dev/sangwoo/celestialfall/client/title/"),"External state write: "+f.owner+"."+f.name);
            }
        }
    }
}
