package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.combat.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MotionRigTest {
    @Test void shoulderRotationPreservesTheAuthoredContactAngles() {
        var model=new MotionPlayerModel(false);var pose=new MotionPose();pose.rot(MotionPose.RIGHT_ARM,-82,39,-17.1f);
        MotionRig.apply(model,pose,0,0);
        assertEquals(MotionRig.rad(-82),model.rightArm.pitch,1e-5);
        assertEquals(MotionRig.rad(39),model.rightArm.yaw,1e-5);
        assertEquals(MotionRig.rad(-17.1f),model.rightArm.roll,1e-5);
    }
    @Test void firstPersonFistsStayInFrontOfCameraAndVisibleAtContact() throws Exception {
        Map<String,Object> shots=new LinkedHashMap<>();var model=new MotionPlayerModel(false);List<String> issues=new ArrayList<>();
        for(int strike=0;strike<=5;strike++) {
            MotionPose pose=strike==0?MotionClips.locomotion(0,0,0,0,0,0,0):strike==5?MotionClips.guard(0,0):MotionClips.attack(strike,CombatState.WINDUP[strike]);
            var snapshot=new CombatClient.Snapshot(0,strike>0&&strike<5?strike:0,0,strike==5,0,0,-1000,0,1,0,100);
            pose=CombatHands.firstPersonPose(pose,snapshot,100,0);
            MotionRig.reset(model);MotionRig.apply(model,pose,0,0);model.overlays();
            MatrixStack matrix=new MatrixStack();CombatHands.transform(matrix);
            List<Object> parts=new ArrayList<>();
            for(var arm:List.of(model.rightArm,model.leftArm)) {
                var capture=new AtomicRigTest.Capture();arm.render(matrix,capture,0,0);parts.add(capture.vertices);
                // Wrist centre, transformed exactly as in the first-person renderer.
                matrix.push();arm.rotate(matrix);arm.getChild("elbow").rotate(matrix);
                Vector3f wrist=new Vector3f(0,5/16f,0).mulPosition(matrix.peek().getPositionMatrix());matrix.pop();
                if(wrist.z>=-.1)issues.add("Behind camera: "+strike+" "+wrist);
                double sx=wrist.x/-wrist.z/(Math.tan(Math.toRadians(70)/2)*16/9),sy=wrist.y/-wrist.z/Math.tan(Math.toRadians(70)/2);
                if(Math.abs(sx)>=1.12||Math.abs(sy)>=1.12)issues.add("Outside frame: "+strike+" "+wrist+" angles="+arm.pitch+","+arm.yaw+","+arm.roll+" elbow="+arm.getChild("elbow").pitch);
            }
            shots.put(strike==0?"idle":strike==5?"guard":"hit"+strike,parts);
        }
        Path out=Path.of("build/botand-motion");Files.createDirectories(out);Files.writeString(out.resolve("hands.json"),new Gson().toJson(shots));assertTrue(issues.isEmpty(),issues.toString());
    }
    static Vector3f point(ModelPart part,float x,float y,float z) {
        var m=new MatrixStack();part.rotate(m);return new Vector3f(x/16,y/16,z/16).mulPosition(m.peek().getPositionMatrix());
    }
    @Test void waistShouldersAndSkinLayersStayAttachedThroughoutEveryPunch() {
        for(boolean slim:new boolean[]{false,true}) {
            var model=new MotionPlayerModel(slim);
            for(int strike=1;strike<=4;strike++)for(float time=0;time<=CombatState.DURATION[strike];time+=.25f) {
                MotionRig.reset(model);MotionRig.apply(model,MotionClips.attack(strike,time),0,0);model.overlays();
                Vector3f waist=point(model.body,0,12,0);
                assertEquals((model.leftLeg.pivotY+model.rightLeg.pivotY)/32,waist.y,1e-5);
                for(int side:new int[]{-1,1}) {
                    var arm=side<0?model.rightArm:model.leftArm;
                    Vector3f socket=point(model.body,side*5,2,0);
                    assertTrue(socket.distance(arm.pivotX/16,arm.pivotY/16,arm.pivotZ/16)<1e-5);
                }
                assertEquals(model.rightArm.getChild("elbow").pitch,model.rightSleeve.getChild("elbow").pitch);
                assertEquals(model.leftLeg.getChild("knee").pitch,model.leftPants.getChild("knee").pitch);
                var armor=new MotionArmorModel(1);model.copyBipedStateTo(armor);
                assertEquals(model.leftArm.getChild("elbow").pitch,armor.leftArm.getChild("elbow").pitch);
            }
        }
    }
    @Test void supportFeetArePlantedAndSwingFeetLiftInEightDirections() {
        var m=new MotionPlayerModel(false);
        for(int dir=0;dir<8;dir++)for(float phase=0;phase<Math.PI*2;phase+=.08f) {
            float forward=(float)Math.cos(dir*Math.PI/4),side=(float)Math.sin(dir*Math.PI/4);
            var pose=MotionClips.locomotion(phase,1,forward,side,0,0,0);MotionRig.reset(m);MotionRig.apply(m,pose,0,0);
            for(var leg:List.of(m.rightLeg,m.leftLeg)) {
                MatrixStack matrix=new MatrixStack();leg.rotate(matrix);leg.getChild("knee").rotate(matrix);
                Vector3f foot=new Vector3f(0,6/16f,0).mulPosition(matrix.peek().getPositionMatrix());
                assertTrue(foot.y<1.515 && foot.y>1.32,"Foot crossed the ground or lifted too high: "+foot);
            }
        }
    }
    @Test void exportActualSkinGeometryForMotionReview() throws Exception {
        Map<String,Object> clips=new LinkedHashMap<>();var model=new MotionPlayerModel(false);
        for(String clip:List.of("walk","run","strafe","backward","combo","guard","dash","break")) {
            List<Object> frames=new ArrayList<>();
            for(int f=0;f<72;f++) {
                float t=f/1.2f,phase=f*(float)Math.PI*2/36;MotionPose p;
                switch(clip) {
                    case "walk":p=MotionClips.locomotion(phase,1,1,0,0,0,t);break;
                    case "run":p=MotionClips.locomotion(phase*1.5f,1,1,0,1,0,t);break;
                    case "strafe":p=MotionClips.locomotion(phase,1,0,1,0,0,t);break;
                    case "backward":p=MotionClips.locomotion(phase,1,-1,0,0,0,t);break;
                    case "combo": {
                        int strike=1;float age=t;
                        while(strike<4&&age>=CombatState.DURATION[strike])age-=CombatState.DURATION[strike++];
                        p=MotionClips.attack(strike,Math.min(age,CombatState.DURATION[strike]));break;
                    }
                    case "guard":p=MotionPose.blend(MotionClips.ready(t),MotionClips.guard(t,70),MotionClips.smooth(Math.min(t/5,(60-t)/8)));break;
                    case "dash":p=MotionClips.dash(t%20,1,t<30?0:1);break;
                    default:p=MotionClips.broken(t%30);break;
                }
                MotionRig.reset(model);MotionRig.apply(model,p,0,0);model.overlays();
                MatrixStack matrix=new MatrixStack();matrix.translate(0,1.5,0);matrix.scale(1,-1,1);
                List<Object> parts=new ArrayList<>();
                for(ModelPart part:new ModelPart[]{model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg}) {
                    var capture=new AtomicRigTest.Capture();part.render(matrix,capture,0,0);
                    for(float[] vertex:capture.vertices)for(float v:vertex)assertTrue(Float.isFinite(v));
                    parts.add(capture.vertices);
                }
                frames.add(parts);
            }
            clips.put(clip,frames);
        }
        Path out=Path.of("build/botand-motion");Files.createDirectories(out);Files.writeString(out.resolve("meshes.json"),new Gson().toJson(clips));
        MotionRig.reset(model);MotionRig.apply(model,MotionClips.locomotion(0,0,1,0,0,0,0),0,0);model.overlays();
        MatrixStack idleMatrix=new MatrixStack();idleMatrix.translate(0,1.5,0);idleMatrix.scale(1,-1,1);
        List<Object> idleParts=new ArrayList<>();
        for(ModelPart part:new ModelPart[]{model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg}) {
            var capture=new AtomicRigTest.Capture();part.render(idleMatrix,capture,0,0);idleParts.add(capture.vertices);
        }
        Files.writeString(out.resolve("idle.json"),new Gson().toJson(idleParts));
    }
}
