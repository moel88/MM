import com.google.gson.GsonBuilder;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Build-time export of Minecraft's OWN slim player model and unmodified skin UVs.
 * No authored face, hair, fingers, clothing geometry or extra body joints. */
public final class ExportVanillaBomi {
    private static final float SCALE=.9375f; // PlayerEntityRenderer's normal scale
    private record Bone(String name,String parent,float[] pivot){}
    private record Part(String name,String bone,int vertices){}
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        ModelPart root=PlayerEntityModel.getTexturedModelData(Dilation.NONE,true).getRoot().createPart(64,64);
        String[] names={"body","head","right_arm","left_arm","right_leg","left_leg"};
        String[] layers={"jacket","hat","right_sleeve","left_sleeve","right_pants","left_pants"};
        var bones=new ArrayList<Bone>();bones.add(new Bone("root",null,new float[]{0,0,0}));
        var parts=new ArrayList<Part>();var vertices=new ArrayList<float[]>();
        var field=ModelPart.class.getDeclaredField("cuboids");field.setAccessible(true);
        for(int i=0;i<names.length;i++){
            ModelPart part=root.getChild(names[i]);
            String parent=i==0||i>=4?"root":"body";
            bones.add(new Bone(names[i],parent,new float[]{part.pivotX*SCALE,(24-part.pivotY)*SCALE,-part.pivotZ*SCALE}));
            for(String name:new String[]{names[i],layers[i]}){
                ModelPart p=root.getChild(name);int start=vertices.size();
                @SuppressWarnings("unchecked") var cuboids=(List<ModelPart.Cuboid>)field.get(p);
                for(var cube:cuboids)for(var face:cube.sides)for(int index:new int[]{0,1,2,0,2,3}){
                    var v=face.vertices[index];var n=face.direction;
                    vertices.add(new float[]{(v.pos.x+p.pivotX)*SCALE/16,(24-v.pos.y-p.pivotY)*SCALE/16,-(v.pos.z+p.pivotZ)*SCALE/16,
                        n.x,-n.y,-n.z,v.u,v.v,1,1,1,1,i+1,0});
                }
                parts.add(new Part(name,names[i],vertices.size()-start));
            }
        }
        ByteBuffer buffer=ByteBuffer.allocate(8+vertices.size()*56).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'B','M','S','1'}).putInt(vertices.size());for(var v:vertices)for(float f:v)buffer.putFloat(f);
        Files.write(out.resolve("bomi.mesh"),buffer.array());
        Map<String,Object> meta=new LinkedHashMap<>();meta.put("source","Minecraft 1.20.1 PlayerEntityModel.getTexturedModelData(Dilation.NONE, true)");
        meta.put("arm_model","slim");meta.put("skin_size",List.of(64,64));meta.put("player_renderer_scale",SCALE);
        meta.put("bones",bones);meta.put("parts",parts);meta.put("custom_geometry",false);meta.put("skin_layers",true);
        Files.writeString(out.resolve("bomi.native.json"),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(meta));
        if(vertices.size()!=432)throw new AssertionError("Expected twelve native player cuboids");
        System.out.println("Vanilla slim player: 6 body parts + 6 original skin overlay layers, "+vertices.size()/3+" triangles");
    }
}
