package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Z (2.93.0, user request: "H 스킬에서 박혀있는 모든 검이 전부 상호작용 되게 바꿔줘 ... 쳐다보는 검(쉬프락은 가운데
 * 십자가로 쳐다보는 검)에 자신에게만 보이는 흰색 테두리가 생기고 아래에는 푸른색 꽃이 피어나고"): every sword of her
 * formation but the one she stands on may be the one she looks at, the one nearest the middle of her view; none when she
 * looks at none; and where the client draws each sword is where the server takes her (one reckoning).
 */
class SwordStepTest {
    @Test void everySwordButHersIsTheOneSheLooksAtWhenSheLooksAtIt(){
        var rnd=new java.util.Random(11);
        for(int n=0;n<20;n++){
            var id=new UUID(rnd.nextLong(),rnd.nextLong());Vec3d centre=new Vec3d(rnd.nextInt(200)-100,64,rnd.nextInt(200)-100);float yaw=rnd.nextFloat()*360-180;
            var all=SwordFormation.layout(id);int on=IsosangRules.standSword();
            for(int stand=0;stand<all.length;stand+=7){
                // her eyes, up on the pommel she stands on (whichever Z has taken her to)
                Vec3d eye=SwordFormation.planted(centre,yaw,all[stand]).add(0,1.62,0);
                for(int k=0;k<all.length;k++){
                    Vec3d mid=SwordFormation.foot(centre,yaw,all[k]).lerp(SwordFormation.planted(centre,yaw,all[k]),.5);
                    int got=SwordStep.aimed(id,centre,yaw,stand,eye,mid.subtract(eye).normalize());
                    if(k==stand)assertNotEquals(stand,got,"never the one she stands on");
                    else assertEquals(k,got,"looking right at sword "+k+" from sword "+stand);
                }
                // a little off it still is, a lot off is none: up at the sky, down at her own sword's foot
                assertEquals(-1,SwordStep.aimed(id,centre,yaw,stand,eye,new Vec3d(0,1,0)),"the sky");
                assertEquals(-1,SwordStep.aimed(id,centre,yaw,stand,eye,new Vec3d(0,-1,0)),"her own sword under her");
            }
            assertEquals(IsosangRules.standSword(),on);
        }
    }
    @Test void theSwordSheLooksAtIsTheOneNearestTheMiddleOfHerView(){
        var id=new UUID(3,4);Vec3d centre=new Vec3d(0,70,0);float yaw=0;var all=SwordFormation.layout(id);int on=IsosangRules.standSword();
        Vec3d eye=SwordFormation.planted(centre,yaw,all[on]).add(0,1.62,0);
        for(int k=0;k<all.length;k++){
            if(k==on)continue;
            Vec3d mid=SwordFormation.foot(centre,yaw,all[k]).lerp(SwordFormation.planted(centre,yaw,all[k]),.5);
            Vec3d look=mid.subtract(eye).normalize();
            // turned off it by a third of the aim, it is still the one (unless another lies nearer that way)
            Vec3d side=look.crossProduct(new Vec3d(0,1,0)).normalize();
            Vec3d off=look.add(side.multiply(Math.tan(SwordStep.AIM/3))).normalize();
            int got=SwordStep.aimed(id,centre,yaw,on,eye,off);
            assertTrue(got>=0,"still one");
            assertTrue(got==k||angle(id,centre,yaw,got,eye,off)<=angle(id,centre,yaw,k,eye,off)+1e-9,"the nearest the middle");
            // and past the aim, it is not that one
            Vec3d far=look.add(side.multiply(Math.tan(SwordStep.AIM*1.6))).normalize();
            int other=SwordStep.aimed(id,centre,yaw,on,eye,far);
            assertTrue(other!=k||angle(id,centre,yaw,k,eye,far)<SwordStep.AIM,"not past the aim");
        }
    }
    /** How far off the line of sight sword {@code k} is (radians, its nearest of the points aimed checks). */
    static double angle(UUID id,Vec3d centre,float yaw,int k,Vec3d eye,Vec3d look){
        var b=SwordFormation.layout(id)[k];Vec3d foot=SwordFormation.foot(centre,yaw,b),top=SwordFormation.planted(centre,yaw,b);double near=Math.PI;
        for(int i=0;i<=6;i++){Vec3d d=foot.lerp(top,i/6.0).subtract(eye);near=Math.min(near,Math.acos(Math.min(1,d.dotProduct(look)/d.length())));}
        return near;
    }
    @Test void eachSwordIsWhereTheServerTakesHer(){
        // the client draws sword k where IsosangRules.formationSwords has it (the server's Formation.place: forward and right
        // of the middle the way she faced), so she lands on the pommel she looked at
        var rnd=new java.util.Random(5);
        for(int n=0;n<20;n++){
            var id=new UUID(rnd.nextLong(),rnd.nextLong());Vec3d centre=new Vec3d(rnd.nextDouble()*50,64,rnd.nextDouble()*50);float yaw=rnd.nextFloat()*360-180;
            var all=SwordFormation.layout(id);var places=IsosangRules.formationSwords(IsosangRules.formationSeed(id));
            Vec3d fw=Vec3d.fromPolar(0,yaw),r=new Vec3d(-fw.z,0,fw.x);
            for(int k=0;k<all.length;k++){
                Vec3d server=centre.add(fw.multiply(places[k][0])).add(r.multiply(places[k][1]));
                assertEquals(0,SwordFormation.foot(centre,yaw,all[k]).distanceTo(server),1e-9,"sword "+k);
                // upright: its pommel right over its foot, as high as she is lifted onto it
                Vec3d top=SwordFormation.planted(centre,yaw,all[k]);
                assertEquals(0,Math.hypot(top.x-server.x,top.z-server.z),1e-9);
                assertEquals(all[k].height(),top.y-centre.y,1e-9);
            }
        }
    }
}
