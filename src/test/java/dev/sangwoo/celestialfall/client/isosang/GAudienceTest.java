package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.isosang.GArenaBounds;
import dev.sangwoo.celestialfall.isosang.GExplosionRules;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Who sees and hears Lee So-sang's G (2.92.0, user request: "62 × 47 범위에 있는 사람은 모두 시전자의 컷씬이 보이는 대신 검이
 * 하늘에서 떨어지는 순간부터 보이게 해줘 그대신 시전하는 소리는 처음부터 끝까지 들리게 하고", then with a frame of the sky's
 * shot: "다른 사람들도 이 시점부터 보이게"): the others in its blast's ground watch its cutscene from the very first frame
 * of its sky's shot (the sword coming out of the storm) to its cut, and keep their own camera before it; its sound is as
 * loud for them as for its user anywhere in that ground, fading out beyond its edge, never fainter than before 2.92.0 and
 * with no step as one walks out.
 */
class GAudienceTest {
    static final float FRAME=1/60f;
    static StandClient.Cast cast(){return GGesture.REFERENCE_CAST;}

    @Test void othersInItsGroundWatchItFromItsSkysShotToItsCut(){
        assertEquals(FallingSword.S_PULLED,GArenaClient.JOIN,0,"they join as the sky's shot begins");
        for(float t=0;t<GArenaClient.JOIN;t+=.005f)assertFalse(GArenaClient.joins(t),"their own camera before it, at "+t);
        for(float t=GArenaClient.JOIN;t<IsosangRules.G_BACK;t+=.005f)assertTrue(GArenaClient.joins(t),"its cutscene from then to its cut, at "+t);
        for(float t=IsosangRules.G_BACK;t<8;t+=.005f)assertFalse(GArenaClient.joins(t),"and their own camera again after it, at "+t);
        assertTrue(GArenaClient.joins(1.83f),"the user's frame of it (1.83 s into G in the 2.88.0 film) is in it");
        // the first shot they see is the sky's own, from its first frame: not a moment of her face, nor of the swing up
        var sky=GScene.skyShot(cast(),GArenaClient.JOIN);var first=FallingSword.shot(cast(),GArenaClient.JOIN);
        assertEquals(0,first.eye().distanceTo(sky.eye()),1e-9,"the sky's camera from the first frame");
        assertEquals(0,first.look().distanceTo(sky.look()),1e-9);
        assertEquals(sky.fov(),first.fov(),1e-9);
        var before=FallingSword.shot(cast(),GArenaClient.JOIN-FRAME);
        assertTrue(before.look().subtract(before.eye()).normalize().dotProduct(sky.look().subtract(sky.eye()).normalize())<.999,
            "a frame (60 fps) before it the camera is still swinging up from her face");
        assertEquals(GStage.STORM,FallingSword.space(cast(),GArenaClient.JOIN),"in the sky's space");
        assertEquals(3,FallingSword.space(cast(),IsosangRules.G_FALL),"then the fall's");
        assertEquals(3,FallingSword.space(cast(),Math.nextDown(IsosangRules.G_BACK)),"up to the cut");
        assertEquals(-1,FallingSword.space(cast(),IsosangRules.G_BACK),"the cutscene is over as they leave it");
    }

    @Test void itsSoundIsAsLoudInItsGroundAndFadesBeyondItsEdge(){
        Vec3d strike=FallingSword.strike(cast());
        GArenaBounds ground=GArenaClient.ground(cast().id(),strike);
        assertEquals(GExplosionRules.area(strike),ground,"out of the arena: 62 × 47 blocks round where its sword strikes");
        double e=1e-3;
        for(Vec3d at:new Vec3d[]{strike,cast().origin(),new Vec3d(ground.minX()+e,strike.y,ground.minZ()+e),new Vec3d(ground.maxX()-e,strike.y,ground.maxZ()-e),
            new Vec3d(ground.minX()+e,strike.y,ground.maxZ()-e),new Vec3d(ground.maxX()-e,strike.y,ground.minZ()+e)}){
            assertEquals(0,ground.outside(at),1e-9,"in its ground at "+at);
            assertEquals(1,IsosangClient.carried(ground.outside(at)),1e-9,"as loud as for its user at "+at);
        }
        assertEquals(10,ground.outside(new Vec3d(ground.maxX()+10,strike.y,strike.z)),1e-9,"10 blocks past its east edge");
        assertEquals(.75,IsosangClient.carried(10),1e-9);
        assertEquals(5,ground.outside(new Vec3d(ground.minX()-3,strike.y,ground.minZ()-4)),1e-9,"past a corner, the straight way to it");
        assertEquals(0,IsosangClient.carried(IsosangClient.CARRY),1e-9,"none CARRY blocks out of it");
        assertEquals(0,IsosangClient.carried(IsosangClient.CARRY+30),1e-9);
        // never fainter than before 2.92.0 (from the nearer of its user and where its sword strikes), and no step as one
        // walks across its edge or anywhere else
        for(double x=-90;x<=90;x+=1.5)for(double z=-90;z<=90;z+=1.5){
            Vec3d at=strike.add(x,0,z);
            double now=IsosangClient.carried(ground.outside(at));
            double before=IsosangClient.carried(Math.min(at.distanceTo(cast().origin()),at.distanceTo(strike)));
            assertTrue(now>=before-1e-9,"fainter than before at "+at);
            double step=Math.abs(IsosangClient.carried(ground.outside(at.add(.25,0,.25)))-now);
            assertTrue(step<=.25*Math.sqrt(2)/IsosangClient.CARRY+1e-9,"a step in its loudness at "+at);
        }
    }
}
