package dev.sangwoo.celestialfall.client.boss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.CelestialFall;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * A boss's outfit (2.69.0, user request for Kujo Jotaro: "옷이나 모자나 장식을 더 꾸며서 입체감있게"): boxes worn over
 * its skin that stand out of it, following its limbs. Each kind's outfit is bosses/KIND_outfit.json and its texture
 * textures/entity/boss/KIND_outfit.png (tools/boss/make_jotaro.py makes both). Its boxes hang off the body's own bones
 * (the head, the body, the arms and their forearms, the legs and their shins), whose places and turns it takes from the
 * player's model every frame; the coat's skirt flaps swing with the legs under them, flare out behind a running boss
 * and stir a little as it goes.
 */
public final class BossOutfit {
    /** An outfit built: its boxes on the bones, the bones, its flaps and how each swings, its texture. */
    public static final class Model {
        final ModelPart root,head,body,rightArm,leftArm,rightElbow,leftElbow,rightLeg,leftLeg,rightKnee,leftKnee;
        final List<ModelPart> flaps=new ArrayList<>();final List<String> swings=new ArrayList<>();
        public final Identifier texture;
        Model(ModelPart root,Identifier texture,Map<String,String> swingOf){
            this.root=root;this.texture=texture;
            head=root.getChild("head");body=root.getChild("body");
            rightArm=root.getChild("right_arm");leftArm=root.getChild("left_arm");rightElbow=rightArm.getChild("elbow");leftElbow=leftArm.getChild("elbow");
            rightLeg=root.getChild("right_leg");leftLeg=root.getChild("left_leg");rightKnee=rightLeg.getChild("knee");leftKnee=leftLeg.getChild("knee");
            for(var e:swingOf.entrySet()){flaps.add(body.getChild(e.getKey()));swings.add(e.getValue());}
        }
        public ModelPart root(){return root;}
        /**
         * Puts the outfit's bones where the player's model has its own this frame, and swings the skirt: each flap
         * goes with the leg under it (forward, back, out), the back flaps lifted further by a bent knee, all of them
         * flaring out behind as it runs ({@code limbDistance}) and stirring as it goes ({@code age}, ticks).
         */
        public void pose(PlayerEntityModel<AbstractClientPlayerEntity> m,float limbDistance,float age){
            head.copyTransform(m.head);body.copyTransform(m.body);
            rightArm.copyTransform(m.rightArm);leftArm.copyTransform(m.leftArm);rightLeg.copyTransform(m.rightLeg);leftLeg.copyTransform(m.leftLeg);
            elbow(rightElbow,m.rightArm,"elbow");elbow(leftElbow,m.leftArm,"elbow");elbow(rightKnee,m.rightLeg,"knee");elbow(leftKnee,m.leftLeg,"knee");
            // the legs' swing as the body sees it (the flaps hang off the body, the legs off the hips)
            float l=m.leftLeg.pitch-m.body.pitch,r=m.rightLeg.pitch-m.body.pitch;
            float kl=m.leftLeg.hasChild("knee")?m.leftLeg.getChild("knee").pitch:0,kr=m.rightLeg.hasChild("knee")?m.rightLeg.getChild("knee").pitch:0;
            float run=MathHelper.clamp(limbDistance,0,1),stir=MathHelper.sin(age*.31f)*.05f*run+MathHelper.sin(age*.13f+1.7f)*.02f;
            for(int i=0;i<flaps.size();i++){
                var f=flaps.get(i);f.resetTransform();
                switch(swings.get(i)){
                    case "back_left"->{f.pitch=Math.max(0,l)+Math.max(0,kl)*.3f+run*.32f+stir;f.roll=Math.min(0,m.leftLeg.roll)*.5f;}
                    case "back_right"->{f.pitch=Math.max(0,r)+Math.max(0,kr)*.3f+run*.32f+stir*.8f;f.roll=Math.max(0,m.rightLeg.roll)*.5f;}
                    case "front_left"->{f.pitch=Math.min(0,l)-run*.06f;f.roll=Math.min(0,m.leftLeg.roll)*.6f;}
                    case "front_right"->{f.pitch=Math.min(0,r)-run*.06f;f.roll=Math.max(0,m.rightLeg.roll)*.6f;}
                    case "side_left"->{f.pitch=l*.65f+run*.1f;f.roll=Math.min(0,m.leftLeg.roll)-run*.05f-Math.abs(stir)*.5f;}
                    case "side_right"->{f.pitch=r*.65f+run*.1f;f.roll=Math.max(0,m.rightLeg.roll)+run*.05f+Math.abs(stir)*.5f;}
                    default->{}
                }
            }
        }
        private static void elbow(ModelPart into,ModelPart limb,String joint){if(limb.hasChild(joint))into.copyTransform(limb.getChild(joint));else into.resetTransform();}
    }
    private static final Map<String,Model> MODELS=new HashMap<>();
    private BossOutfit(){}
    static void initialize(){
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            @Override public Identifier getFabricId(){return CelestialFall.id("boss_outfits");}
            @Override public void reload(ResourceManager manager){MODELS.clear();for(String kind:new String[]{"jotaro"}){var m=load(manager,kind);if(m!=null)MODELS.put(kind,m);}}
        });
    }
    /** The outfit of a boss of {@code kind}, if it has one. */
    public static Model model(String kind){return kind==null?null:MODELS.get(kind);}

    static Model load(ResourceManager manager,String kind){
        var file=CelestialFall.id("bosses/"+kind+"_outfit.json");
        var resource=manager.getResource(file);if(resource.isEmpty())return null;
        try(var in=new InputStreamReader(resource.get().getInputStream(),StandardCharsets.UTF_8)){
            return build(JsonParser.parseReader(in).getAsJsonObject(),CelestialFall.id("textures/entity/boss/"+kind+"_outfit.png"));
        }catch(Exception e){CelestialFall.LOGGER.warn("Could not read the outfit {}: {}",file,e.toString());return null;}
    }
    /** The outfit from its description: the bones as the player's model has them, each box on its bone. */
    public static Model build(JsonObject json,Identifier texture){
        var data=new ModelData();var root=data.getRoot();
        Map<String,ModelPartData> bones=new HashMap<>();
        bones.put("head",root.addChild("head",ModelPartBuilder.create(),ModelTransform.NONE));
        bones.put("body",root.addChild("body",ModelPartBuilder.create(),ModelTransform.NONE));
        for(String side:new String[]{"right","left"}){
            var arm=root.addChild(side+"_arm",ModelPartBuilder.create(),ModelTransform.NONE);bones.put(side+"_arm",arm);
            bones.put(side+"_elbow",arm.addChild("elbow",ModelPartBuilder.create(),ModelTransform.NONE));
            var leg=root.addChild(side+"_leg",ModelPartBuilder.create(),ModelTransform.NONE);bones.put(side+"_leg",leg);
            bones.put(side+"_knee",leg.addChild("knee",ModelPartBuilder.create(),ModelTransform.NONE));
        }
        var tex=json.getAsJsonArray("texture");
        Map<String,String> swings=new java.util.LinkedHashMap<>();
        for(var e:json.getAsJsonArray("parts")){
            var p=e.getAsJsonObject();
            var bone=bones.get(p.get("bone").getAsString());if(bone==null)throw new IllegalArgumentException("no bone "+p.get("bone"));
            float[] pivot=floats(p,"pivot"),rot=floats(p,"rotation"),from=floats(p,"from"),size=floats(p,"size"),uv=floats(p,"uv");
            String name=p.get("name").getAsString();
            bone.addChild(name,ModelPartBuilder.create().uv((int)uv[0],(int)uv[1]).cuboid(from[0],from[1],from[2],size[0],size[1],size[2],Dilation.NONE),
                ModelTransform.of(pivot[0],pivot[1],pivot[2],rot[0]*MathHelper.RADIANS_PER_DEGREE,rot[1]*MathHelper.RADIANS_PER_DEGREE,rot[2]*MathHelper.RADIANS_PER_DEGREE));
            if(p.has("swing")&&!p.get("swing").isJsonNull()){
                if(!"body".equals(p.get("bone").getAsString()))throw new IllegalArgumentException("a flap hangs off the body: "+name);
                swings.put(name,p.get("swing").getAsString());
            }
        }
        return new Model(TexturedModelData.of(data,tex.get(0).getAsInt(),tex.get(1).getAsInt()).createModel(),texture,swings);
    }
    private static float[] floats(JsonObject o,String key){var a=o.getAsJsonArray(key);var out=new float[a.size()];for(int i=0;i<out.length;i++)out[i]=a.get(i).getAsFloat();return out;}
}
