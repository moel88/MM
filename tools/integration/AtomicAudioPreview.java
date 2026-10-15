package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AtomicTimeline;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import java.io.*;
import java.nio.file.*;

/** Exports the actual baked actor for an offline audio-sync review, not gameplay capture. */
public final class AtomicAudioPreview {
    private static ModelPart part(String name) throws Exception {
        var field=AtomicActor.class.getDeclaredField(name);field.setAccessible(true);return (ModelPart)field.get(null);
    }
    public static void main(String[] args) throws Exception {
        var root=part("ROOT");var body=root.getChild("body");var arm=body.getChild("rightArm");
        Path out=Path.of("build/audio-remake/atomic-motion.bin");Files.createDirectories(out.getParent());
        try(var data=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out)))) {
            data.writeInt(225);data.writeInt(30);
            for(int frame=0;frame<225;frame++) {
                float age=frame*20f/30;AtomicActor.applyPose(age,true);
                MatrixStack m=new MatrixStack();m.translate(0,1.5,0);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));m.scale(-1,-1,1);
                var cloth=new AtomicRigTest.Capture();var face=new AtomicRigTest.Capture();var sword=new AtomicRigTest.Capture();
                root.render(m,cloth,0,0);
                m.push();body.rotate(m);body.getChild("head").rotate(m);part("FACE").render(m,face,0,0);part("HOOD").render(m,cloth,0,0);m.pop();
                m.push();body.rotate(m);arm.rotate(m);arm.getChild("forearm").rotate(m);m.translate(0,5.5/16.,0);part("SWORD").render(m,sword,0,0);m.pop();
                data.writeFloat(age);
                for(var mesh:new AtomicRigTest.Capture[]{cloth,face,sword}) {
                    data.writeInt(mesh.vertices.size());
                    for(float[] vertex:mesh.vertices)for(float value:vertex) {
                        if(!Float.isFinite(value))throw new IllegalStateException("Invalid pose");data.writeFloat(value);
                    }
                }
            }
        }
        System.out.println("Exported 225 actual AtomicActor pose frames");
    }
}
