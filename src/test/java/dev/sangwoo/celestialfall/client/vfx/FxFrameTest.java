package dev.sangwoo.celestialfall.client.vfx;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** What the effects hand the effect layer each frame: lights, decals, shafts, impact. */
class FxFrameTest {
    @Test void keepsTheLightsThatMatterMost(){
        var f=new FxFrame();Vec3d camera=Vec3d.ZERO;
        for(int i=0;i<20;i++)f.light(new Vec3d(i*2,0,0),0xffffff,6,1f+i*.1f);
        f.light(new Vec3d(500,0,0),0xffffff,6,50);
        var lights=f.strongest(camera);
        assertEquals(FxFrame.MAX_LIGHTS,lights.size());
        for(int i=1;i<lights.size();i++)assertTrue(FxFrame.score(lights.get(i-1),camera)>=FxFrame.score(lights.get(i),camera));
        assertTrue(lights.stream().noneMatch(l->l.pos().x>400),"a light far beyond its reach is dropped");
    }
    @Test void aLightWithoutStrengthOrReachIsNothing(){
        var f=new FxFrame();
        f.light(Vec3d.ZERO,0xffffff,5,0);f.light(Vec3d.ZERO,0xffffff,0,1);f.light(Vec3d.ZERO,0xffffff,5,Float.NaN);f.light(null,0xffffff,5,1);
        assertEquals(0,f.lightCount());
        f.light(Vec3d.ZERO,0xff8000,5,2);var l=f.strongest(Vec3d.ZERO).get(0);
        assertEquals(2,l.r(),1e-6);assertEquals(2*128/255f,l.g(),1e-6);assertEquals(0,l.b(),1e-6);
    }
    @Test void decalsLieFlatOnTheGroundNearestFirst(){
        var f=new FxFrame();
        f.decal(new Vec3d(0,64,0),new Vec3d(0,1,0),3,1,1,1,0);
        assertEquals(0,f.decalCount(),"a straight-up direction has no direction on the ground");
        for(int i=8;i>=0;i--)f.decal(new Vec3d(i*3,64,0),new Vec3d(1,.5,1),3,1,i/8f,1,i);
        var decals=f.nearest(new Vec3d(0,64,0));
        assertEquals(FxFrame.MAX_DECALS,decals.size());
        assertEquals(0,decals.get(0).center().x,1e-9);
        for(var d:decals){assertEquals(0,d.along().y,1e-12);assertEquals(1,d.along().length(),1e-9);assertTrue(d.heat()>=0&&d.heat()<=1);}
    }
    @Test void theStrongestShaftImpactAndSplitWin(){
        var f=new FxFrame();assertTrue(f.empty());
        f.shaft(new Vec3d(1,0,0),0xff0000,.5f);f.shaft(new Vec3d(2,0,0),0x00ff00,.9f);f.shaft(new Vec3d(3,0,0),0x0000ff,.2f);
        assertEquals(2,f.shaft().x,1e-9);assertEquals(0x00ff00,f.shaftRgb());assertEquals(.9f,f.shaftStrength(),1e-6);
        f.impact(.4f,1);f.impact(.8f,0);f.impact(.6f,1);
        assertEquals(.8f,f.impact(),1e-6);assertEquals(0,f.impactMode(),1e-6);
        f.chroma(.01f);f.chroma(.005f);assertEquals(.01f,f.chroma(),1e-6);
        assertFalse(f.empty());
    }
}
