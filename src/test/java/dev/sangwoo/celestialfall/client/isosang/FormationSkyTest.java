package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The night over the formation (2.99.0, user request: "H 배경이나 그런것도 예쁘게 너가 할 수 있는 만큼 대폭 꾸며봐"): it falls
 * after the crown, the zenith first, and lifts as the formation ends, the horizon first; the moon rises from under the
 * land behind the mountain right of the far side and stands over the mountains in her camera's sky; the stars, their
 * river and the shooting stars lie where her camera sees them; the light on the water lies on its water.
 */
class FormationSkyTest {
    static final UUID ID=UUID.fromString("00000000-0000-0000-0000-0000000099a0");
    static final Vec3d CENTRE=new Vec3d(10,64,-20);
    static final float YAW=37;
    /** Her camera as the formation stands: some 14 blocks behind its middle, 5.4 up, looking in (the default third-person camera with H's pull). */
    static Vec3d camera(){return CENTRE.subtract(Vec3d.fromPolar(0,YAW).multiply(14)).add(0,5.4,0);}
    static double elevation(Vec3d from,Vec3d to){Vec3d d=to.subtract(from);return Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));}
    /** How far right of where the camera looks (her facing) {@code to} is, in degrees. */
    static double right(Vec3d from,Vec3d to){
        Vec3d f=Vec3d.fromPolar(0,YAW),r=new Vec3d(-f.z,0,f.x),d=to.subtract(from);
        return Math.toDegrees(Math.atan2(d.dotProduct(r),d.dotProduct(f)));
    }

    @Test void nightFallsAfterTheCrownTheZenithFirstAndLiftsAsItEndsTheHorizonFirst(){
        float end=IsosangRules.H_END;
        for(double el:FormationSky.RINGS){
            assertEquals(0,FormationSky.night(IsosangRules.H_PLANT,end,el),1e-6,"still day as the crown breaks, at "+el);
            assertEquals(1,FormationSky.night(FormationSky.NIGHT0+FormationSky.NIGHT_SPREAD+FormationSky.NIGHT_FALL+.01f,end,el),1e-4,"night all over, at "+el);
            assertEquals(1,FormationSky.night(end,end,el),1e-4,"still night as it ends, at "+el);
            assertEquals(0,FormationSky.night(end+FormationSky.DAWN0+FormationSky.DAWN_SPREAD+FormationSky.DAWN_FALL+.01f,end,el),1e-4,"day again, at "+el);
        }
        float falling=FormationSky.NIGHT0+.6f,lifting=end+FormationSky.DAWN0+.45f;
        assertTrue(FormationSky.night(falling,end,90)>FormationSky.night(falling,end,0)+.2f,"the zenith darkens first");
        assertTrue(FormationSky.night(lifting,end,0)<FormationSky.night(lifting,end,90)-.2f,"the horizon clears first");
        // a formation ended early: its night lifts then
        assertEquals(0,FormationSky.night(16f,15f,30),1e-4);
        // up only while any of it is
        assertFalse(FormationSky.up(.5f,end));assertTrue(FormationSky.up(2f,end));assertFalse(FormationSky.up(end+1.2f,end));
        assertTrue(FormationSky.up(end+FormationSky.DAWN0+FormationSky.DAWN_SPREAD+FormationSky.DAWN_FALL-.01f,end));
    }
    @Test void onlyItsCasterAndWhoeverStandsInItSeeIt(){
        // by where their body is (2.99.1): in its reach across the ground, not far over or under its ground
        assertTrue(FormationSky.within(CENTRE,CENTRE.add(11.9,0,0)));
        assertTrue(FormationSky.within(CENTRE,CENTRE.add(-6,IsosangRules.FORMATION_STAND,6)),"on a sword");
        assertFalse(FormationSky.within(CENTRE,CENTRE.add(12.2,0,0)),"a passer-by just outside");
        assertFalse(FormationSky.within(CENTRE,CENTRE.add(3,13,0)),"far over it");
        assertFalse(FormationSky.within(CENTRE,CENTRE.add(3,-5,0)),"far under it");
        // stepping in and out: over half a second each way, smoothly, from nothing the first time it is drawn
        UUID id=UUID.fromString("00000000-0000-0000-0000-0000000099b1");
        assertEquals(0,FormationSky.presence(id,10f,false),1e-6);
        assertEquals(0,FormationSky.presence(id,10.5f,false),1e-6,"outside: none");
        float a=FormationSky.presence(id,10.6f,true);
        assertEquals(a,FormationSky.presence(id,10.6f,true),1e-6,"asked again in the same frame it does not move");
        assertTrue(a>0&&a<.2f,"coming in: "+a);
        assertEquals(.5f,FormationSky.presence(id,10.75f,true),1e-4,"half way at a quarter second");
        assertEquals(1,FormationSky.presence(id,11.1f,true),1e-6,"all of it at half a second");
        assertEquals(1,FormationSky.presence(id,20f,true),1e-6);
        assertEquals(.5f,FormationSky.presence(id,20.25f,false),1e-4,"going out as smoothly");
        assertEquals(0,FormationSky.presence(id,20.6f,false),1e-6);
        assertEquals(0,FormationSky.presenceNow(id),1e-6);
        // the camera of whoever is in it stays well inside its sky (at its edge, the camera drawn back as far as it goes),
        // so none of them sees its edge; a camera that far out sees none of it
        double farthest=IsosangRules.FORMATION_RADIUS+dev.sangwoo.celestialfall.combat.OrbitCameraState.MAX_DISTANCE;
        assertEquals(1,FormationSky.inside(CENTRE,CENTRE.add(farthest,3,0)),1e-6);
        assertEquals(1,FormationSky.inside(CENTRE,camera()),1e-6,"her camera");
        assertTrue(FormationSky.SKY_R-farthest>20,"its edge far beyond any of their cameras");
        assertEquals(0,FormationSky.inside(CENTRE,CENTRE.add(FormationSky.INSIDE1+.5,2,0)),1e-6);
        assertTrue(FormationSky.INSIDE1<=FormationSky.SKY_R-.5);
    }
    @Test void theSkyGlowsLowOverTheMountainsAndDeepensOverhead(){
        double glow=lum(FormationSky.skyRgb(FormationSky.GLOW_EL));
        assertTrue(glow>lum(FormationSky.skyRgb(0))&&glow>lum(FormationSky.skyRgb(15)),"brightest a little over the horizon");
        assertTrue(lum(FormationSky.skyRgb(90))<lum(FormationSky.skyRgb(30))&&lum(FormationSky.skyRgb(30))<lum(FormationSky.skyRgb(12)),"deeper overhead");
        assertTrue(lum(FormationSky.skyRgb(-16))<lum(FormationSky.skyRgb(0)),"the land beyond sinks into a dusky mist");
        // her camera sees its glow just over the mountains' tops: GLOW_EL on the dome a degree or three over its horizon
        Vec3d g=FormationSky.onSky(CENTRE,FormationSky.forward(YAW),FormationSky.GLOW_EL,FormationSky.SKY_R);
        double e=elevation(camera(),g);assertTrue(e>.5&&e<4,"the glow over the mountains' tops: "+e);
        // the moon's glow lightens the sky round it
        Vec3d toMoon=FormationSky.dir(0,20);
        assertTrue(lum(FormationSky.skyAt(FormationSky.dir(0,20),0,20,toMoon))>lum(FormationSky.skyAt(FormationSky.dir(Math.PI,20),Math.PI,20,toMoon))+20);
    }
    static double lum(int c){return .3*((c>>16)&255)+.59*((c>>8)&255)+.11*(c&255);}
    @Test void theMoonRisesFromBehindTheMountainAndStandsOverThem(){
        float end=IsosangRules.H_END;Vec3d cam=camera();
        Vec3d low=FormationSky.moon(CENTRE,YAW,FormationSky.MOON_RISE0,end);
        assertTrue(low.y+FormationSky.MOON_R<CENTRE.y,"under the land as it starts to rise");
        Vec3d moon=FormationSky.moon(CENTRE,YAW,FormationSky.MOON_RISE1+.1f,end);
        double e=elevation(cam,moon),r=right(cam,moon),half=Math.toDegrees(Math.atan(FormationSky.MOON_R/moon.distanceTo(cam)));
        assertTrue(e>6&&e<14,"some 10 degrees over her camera's horizon: "+e);
        assertTrue(r>12&&r<25,"right of the formation's middle: "+r);
        assertTrue(half>3&&half<5,"a big moon (half "+half+" degrees)");
        // over the mountains' tops (the highest of them that way, as her camera sees them)
        double top=-90;
        for(var mt:SwordFormation.mountains(ID,YAW))for(double x=-mt.half();x<=mt.half();x+=.25){
            Vec3d p=SwordFormation.onMountain(CENTRE,mt,x,mt.top(),0);
            if(Math.abs(right(cam,p)-r)<half)top=Math.max(top,elevation(cam,p));
        }
        assertTrue(top>-90,"a mountain stands under it");
        assertTrue(e-half>top+1,"over the mountain under it: its bottom "+(e-half)+", the mountain's top "+top);
        // risen it is inside the sky's dome, before it
        assertTrue(moon.distanceTo(FormationSky.eye(CENTRE))<FormationSky.SKY_R-FormationSky.MOON_R-.5);
        // and it sinks a little as it goes
        assertTrue(FormationSky.moon(CENTRE,YAW,end+1,end).y<moon.y-1);
        assertEquals(0,FormationSky.moonLight(end+.8f,end),1e-6);
    }
    @Test void theMoonIsACrescentWithTheLittleStarInItsArms(){
        double lit=FormationSky.MOON_LIT;
        assertTrue(FormationSky.moonLit(.85*Math.cos(lit),.85*Math.sin(lit))>.8,"lit low on one side");
        assertTrue(FormationSky.moonLit(.7*Math.cos(lit+Math.PI),.7*Math.sin(lit+Math.PI))<.02,"dark on the other, up");
        assertTrue(FormationSky.moonAsh(.7*Math.cos(lit+Math.PI),.7*Math.sin(lit+Math.PI))>.03,"ashen there");
        assertEquals(0,FormationSky.moonLit(1.1,0),1e-6,"nothing outside it");
        assertTrue(FormationSky.moonLit(0,0)<.05,"a crescent, not a half moon: its middle dark");
    }
    @Test void theStarsLieAllRoundOverTheHorizonMostFaintAFewBright(){
        var stars=FormationSky.stars(ID,YAW);
        assertTrue(stars.length>FormationSky.STAR_COUNT+FormationSky.RIVER_COUNT*.6&&stars.length<=FormationSky.STAR_COUNT+FormationSky.RIVER_COUNT);
        int bright=0,front=0;double f=FormationSky.forward(YAW);
        for(var s:stars){
            assertTrue(s.el()>=6&&s.el()<=90,"over the horizon: "+s.el());
            assertTrue(s.size()>.001&&s.size()<.007,"small: "+s.size());
            assertTrue(s.born()>=FormationSky.STARS0&&s.born()<=FormationSky.STARS1+.5f);
            if(s.kind()==1)bright++;
            if(Math.abs(Math.IEEEremainder(s.az()-f,Math.PI*2))<Math.toRadians(50)&&s.el()<25)front++;
        }
        assertTrue(bright>FormationSky.STAR_COUNT*.03&&bright<FormationSky.STAR_COUNT*.1,"a few bright ones: "+bright);
        assertTrue(front>40,"plenty in her camera's sky: "+front);
        // the river rises across her camera's sky, from low on the left to high on the right
        Vec3d a=FormationSky.river(f,0,0);
        assertEquals(FormationSky.RIVER_A_EL,Math.toDegrees(Math.asin(a.y)),1e-6);
        boolean across=false;
        for(double s=0;s<1.2;s+=.05){
            Vec3d d=FormationSky.river(f,s,0);double el=Math.toDegrees(Math.asin(d.y)),off=Math.toDegrees(Math.IEEEremainder(Math.atan2(d.z,d.x)-f,Math.PI*2));
            if(el>8&&el<22&&Math.abs(off)<30)across=true;
        }
        assertTrue(across,"through her camera's sky");
        // the stars come out after night falls and are gone soon after it ends
        var s=stars[0];
        assertEquals(0,FormationSky.starAlpha(s,s.born()-.01f,IsosangRules.H_END),1e-6);
        assertEquals(0,FormationSky.starAlpha(s,IsosangRules.H_END+.5f,IsosangRules.H_END),1e-6);
    }
    @Test void theSwordInTheStarsHangsOverTheFarSidePointDown(){
        Vec3d cam=camera();
        for(int k=0;k<FormationSky.SWORD_STARS.length;k++){
            Vec3d p=FormationSky.swordStar(CENTRE,YAW,k);double e=elevation(cam,p),r=right(cam,p);
            assertTrue(e>3&&e<19,"over the mountains, under the top of her camera's picture: "+e);
            assertTrue(Math.abs(r)<6,"over the formation's middle: "+r);
        }
        assertTrue(FormationSky.swordStar(CENTRE,YAW,6).y<FormationSky.swordStar(CENTRE,YAW,0).y-4,"its point down");
        // clear of the moon
        Vec3d moon=FormationSky.moon(CENTRE,YAW,FormationSky.MOON_RISE1,IsosangRules.H_END);
        assertTrue(right(cam,moon)-right(cam,FormationSky.swordStar(CENTRE,YAW,4))>8);
        // its lines drawn one after another once the stars are out, and they stay
        assertEquals(0,FormationSky.swordLine(0,FormationSky.SWORD0),1e-6);
        assertTrue(FormationSky.SWORD0>=FormationSky.STARS1);
        for(int k=1;k<FormationSky.SWORD_LINES.length;k++)assertTrue(FormationSky.swordLine(k,FormationSky.SWORD0+FormationSky.SWORD_STEP*k-.01f)<1e-3);
        for(int k=0;k<FormationSky.SWORD_LINES.length;k++)assertEquals(1,FormationSky.swordLine(k,10),1e-6);
    }
    @Test void shootingStarsFallNowAndThenOverTheFarSky(){
        double f=FormationSky.forward(YAW);float end=IsosangRules.H_END;int seen=0;
        for(int k=0;k<9;k++){
            var me=FormationSky.meteor(ID,YAW,k);
            assertTrue(me.at()>=FormationSky.METEOR0-.9f,"not before the stars are out");
            assertTrue(Math.abs(Math.IEEEremainder(me.az()-f,Math.PI*2))<=Math.toRadians(65.01),"over the far side");
            assertTrue(me.el()>=11&&me.el()<=24,"in her camera's sky: "+me.el());
            assertTrue(me.life()>=.6f&&me.life()<=.85f);
            // falling
            assertTrue(FormationSky.meteorAt(me,me.length()).y<FormationSky.meteorAt(me,0).y,"it falls");
        }
        for(float t=0;t<end+2;t+=.05f){
            var l=FormationSky.meteors(ID,YAW,t,end);seen+=l.isEmpty()?0:1;
            if(t<FormationSky.METEOR0-.9f||t>end)assertTrue(l.isEmpty(),"none at "+t);
            assertTrue(l.size()<=1,"one at a time at "+t);
        }
        assertTrue(seen>40,"some eight of them: "+seen);
    }
    @Test void theLightOnTheWaterLiesOnItsWater(){
        // mirrored and found on the water: on its surface, between the camera and the mirrored point
        Vec3d cam=camera();double w=FormationSky.waterLevel(CENTRE);
        Vec3d star=FormationSky.onSky(CENTRE,FormationSky.forward(YAW)+.2,32,FormationSky.STAR_R);
        Vec3d q=FormationSky.onWater(cam,FormationSky.mirror(star,w),w);
        assertNotNull(q);assertEquals(w,q.y,1e-9);
        Vec3d a=q.subtract(cam).normalize(),b=FormationSky.mirror(star,w).subtract(cam).normalize();
        assertEquals(1,a.dotProduct(b),1e-9,"along the way to its mirror image");
        assertNull(FormationSky.onWater(cam,cam.add(3,1,0),w),"nothing over the water is in it");
        // no water in the bare middle nor past the mountains' feet
        assertEquals(0,FormationSky.wet(3),1e-6);assertEquals(1,FormationSky.wet(9),1e-6);assertEquals(0,FormationSky.wet(SwordFormation.WATER_EDGE+.2),1e-6);
        // the moon's path of light runs from her camera toward the moon's foot, over the formation's far water
        Vec3d moon=FormationSky.moon(CENTRE,YAW,FormationSky.MOON_RISE1,IsosangRules.H_END);
        Vec3d way=new Vec3d(moon.x-cam.x,0,moon.z-cam.z).normalize();
        double far=0;
        for(double s=1.5;s<40;s+=.25){
            Vec3d p=cam.add(way.multiply(s));
            assertEquals(1,FormationSky.onPath(cam,moon,p.x,p.z),1e-9,"on its middle at "+s);
            if(FormationSky.wet(Math.hypot(p.x-CENTRE.x,p.z-CENTRE.z))>.5)far=Math.max(far,s);
        }
        assertTrue(far>20,"over the far water, beyond the formation's middle: "+far);
        Vec3d side=new Vec3d(-way.z,0,way.x),p=cam.add(way.multiply(20));
        assertTrue(FormationSky.onPath(cam,moon,p.x+side.x*3,p.z+side.z*3)<.05,"narrow: 3 blocks off it at 20");
        assertEquals(0,FormationSky.onPath(cam,moon,cam.x-way.x*3,cam.z-way.z*3),1e-9,"none behind the camera");
    }
}
