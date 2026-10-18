package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.combat.*;
import dev.sangwoo.celestialfall.training.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import com.google.gson.Gson;
import static org.junit.jupiter.api.Assertions.*;

class TrainingDummyModelTest {
    @Test void healthAndRolesMatchPlayerAndTenTimesPlayer() {
        assertEquals(4,DummyKind.values().length);
        for(var kind:List.of(DummyKind.IDLE,DummyKind.ATTACK,DummyKind.GUARD))assertEquals(200,kind.health);
        assertEquals(10*DummyKind.IDLE.health,DummyKind.COMBO.health);
    }
    @Test void sharedRigKeepsAllAnimatedLimbsAttachedAndExportsExactDummyMesh() throws Exception {
        var model=new TrainingDummyModel();Map<String,Object> clips=new LinkedHashMap<>();
        for(var kind:DummyKind.values()) {
            List<Object> frames=new ArrayList<>();
            for(int f=0;f<72;f++) {
                float time=f/1.2f;MotionPose pose;
                if(kind==DummyKind.ATTACK) {
                    int strike=1;float t=time;
                    while(strike<4&&t>=CombatState.DURATION[strike])t-=CombatState.DURATION[strike++];
                    pose=MotionClips.attack(strike,Math.min(t,CombatState.DURATION[strike]));
                } else if(kind==DummyKind.GUARD)pose=time<40?MotionClips.guard(time,time*2.5f):MotionClips.broken(time-40);
                else if(kind==DummyKind.COMBO)pose=MotionClips.hitReaction(time%12,(f/18)%4+1);
                else pose=MotionClips.locomotion(0,0,0,0,0,0,time);
                MotionRig.reset(model);MotionRig.apply(model,pose,0,0);
                for(int side:new int[]{-1,1}) {
                    var socket=MotionRigTest.point(model.body,side*5,2,0);var arm=side<0?model.rightArm:model.leftArm;
                    assertTrue(socket.distance(arm.pivotX/16,arm.pivotY/16,arm.pivotZ/16)<1e-5);
                }
                assertFalse(model.hat.visible);
                var matrix=new MatrixStack();matrix.translate(0,1.5,0);matrix.scale(1,-1,1);List<Object> parts=new ArrayList<>();
                for(ModelPart part:new ModelPart[]{model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg}) {
                    var capture=new AtomicRigTest.Capture();part.render(matrix,capture,0,0);
                    for(float[] vertex:capture.vertices)for(float value:vertex)assertTrue(Float.isFinite(value));
                    parts.add(capture.vertices);
                }
                frames.add(parts);
            }
            clips.put(kind.id,frames);
        }
        var out=Path.of("build/botand-dummies");Files.createDirectories(out);Files.writeString(out.resolve("meshes.json"),new Gson().toJson(clips));
    }
}
