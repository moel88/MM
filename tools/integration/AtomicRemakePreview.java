package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.AtomicTimeline;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Exports the exact game actor and column meshes; the renderer adds only a review stage. */
public final class AtomicRemakePreview {
    private static ModelPart part(String name)throws Exception {
        var field=AtomicActor.class.getDeclaredField(name);field.setAccessible(true);return (ModelPart)field.get(null);
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of("build/atomic-remake");Files.createDirectories(out);
        var root=part("ROOT");var body=root.getChild("body");var arm=body.getChild("rightArm");
        try(var data=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out.resolve("scene.bin"))))) {
            data.writeInt(889);data.writeInt(24);
            for(int frame=0;frame<889;frame++) {
                float age=frame*20f/24;data.writeFloat(age);var shot=AtomicTimeline.shot(age,28);
                for(double v:new double[]{shot.radius(),shot.height(),shot.focusHeight(),shot.azimuth(),shot.fov(),AtomicTimeline.blastRadius(age,28)})data.writeFloat((float)v);
                data.writeBoolean(AtomicTimeline.whiteout(age));
                var cloth=new AtomicRigTest.Capture();var face=new AtomicRigTest.Capture();var sword=new AtomicRigTest.Capture();
                if(AtomicTimeline.posed(age)) {
                    AtomicActor.applyPose(age,true);var m=new MatrixStack();m.translate(0,1.5,0);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));m.scale(-1,-1,1);
                    root.render(m,cloth,0,0);
                    m.push();body.rotate(m);body.getChild("head").rotate(m);part("FACE").render(m,face,0,0);part("HOOD").render(m,cloth,0,0);m.pop();
                    m.push();body.rotate(m);arm.rotate(m);arm.getChild("forearm").rotate(m);m.translate(0,5.5/16.,0);part("SWORD").render(m,sword,0,0);m.pop();
                }
                for(var mesh:List.of(cloth,face,sword))write(data,mesh.vertices);
                for(int layer=0;layer<3;layer++) {
                    var vertices=new ArrayList<float[]>();
                    AtomicColumnGeometry.Quads sink=(a,b,c,d,color,alpha)->{
                        for(Vec3d p:new Vec3d[]{a,b,c,d})vertices.add(new float[]{(float)p.x,(float)p.y,(float)p.z,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha});
                    };
                    if(layer==0)AtomicColumnGeometry.body(sink,age,28,false);
                    else if(layer==1)AtomicColumnGeometry.halo(sink,age,28,false);
                    else AtomicColumnGeometry.energy(sink,age,28,false);
                    write(data,vertices);
                }
            }
        }
        System.out.println("Exported 889 frames of actual actor/column/camera data");
    }
    private static void write(DataOutputStream out,List<float[]> vertices)throws IOException {
        out.writeInt(vertices.size());for(var v:vertices)for(float f:v){if(!Float.isFinite(f))throw new IOException("Nonfinite geometry");out.writeFloat(f);}
    }
}
