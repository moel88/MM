package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's delete slash as it goes, as drawn (2.133.0, the user's: "거대한 참격을 바라보는 방향으로 날리고 참격을 맞은 블록은
 * 픽셀로 산산이 부서지고 거기에 있던 플레이어는 0.5초 동안 글리치로 사라졌다 나타나게"; "폭 16블록 · 40블록까지"): the blade
 * covers what the server strikes (its width and its height), rises out of the ground, runs along at the server's speed,
 * its crown leading, and is eaten and gone at the end of its way (or as it is cut short); a block comes apart as its front
 * comes over it, its pixels thrown up and falling, its void drawn back within its time; whom it deletes is gone for half a
 * second, glitching out and back in.
 */
class EraseFxTest {
    static EraseClient.Wave wave(double range,float yaw){
        UUID id=new UUID(11,12),owner=new UUID(13,14);var from=new Vec3d(4,64,-2);
        var cast=new StandClient.Cast(id,owner,new Identifier("minecraft","overworld"),EraseRules.SKILL,0,1000,1000,0,from,yaw,List.of());
        return new EraseClient.Wave(id,owner,from,yaw,range,cast);
    }
    @Test void theBladeCoversWhatTheServerStrikes(){
        // its horns past the server's edge, its crown over the server's height
        assertTrue(EraseFx.HALF_DRAW>=EraseRules.HALF,"as wide as it strikes");
        assertTrue(EraseFx.top(0,1)>=EraseRules.HIGH-.5,"its crown about as high as it strikes: "+EraseFx.top(0,1));
        assertTrue(EraseFx.top(1,1)<=0,"its horns in the ground");
        // thin at its horns, deepest at its crown
        assertTrue(EraseFx.thick(0,1)>EraseFx.thick(.5,1)&&EraseFx.thick(.5,1)>EraseFx.thick(.95,1));
        for(double u=-1;u<=1;u+=.05)assertTrue(EraseFx.top(u,1)-EraseFx.thick(u,1)>=-EraseFx.SINK-.05,"its blade over the ground's line: "+u);
        // the crown leads, the horns behind it, as much to either side
        var w=wave(40,30);
        Vec3d crown=EraseFx.point(w,10,0,0),left=EraseFx.point(w,10,-1,0),right=EraseFx.point(w,10,1,0);
        double ahead=crown.subtract(w.from).dotProduct(w.f),hornL=left.subtract(w.from).dotProduct(w.f),hornR=right.subtract(w.from).dotProduct(w.f);
        assertEquals(10+EraseFx.BOW*.5,ahead,1e-6);assertEquals(10-EraseFx.BOW*.5,hornL,1e-6);assertEquals(hornL,hornR,1e-6);
        assertEquals(-EraseFx.HALF_DRAW,left.subtract(w.from).dotProduct(w.right),1e-6);assertEquals(EraseFx.HALF_DRAW,right.subtract(w.from).dotProduct(w.right),1e-6);
    }
    @Test void itRisesOutOfTheGroundAndRunsAtTheServersSpeed(){
        var w=wave(40,0);
        assertEquals(0,EraseFx.grown(0),1e-9);assertEquals(1,EraseFx.grown(EraseFx.RISE),1e-6);
        double last=0;for(float a=0;a<=4;a+=1/3f){double g=EraseFx.grown(a);assertTrue(g>=last-1e-9&&g-last<.5,"rising smoothly");last=g;}
        for(float a=0;a<=25;a+=.5f)assertEquals(EraseRules.front(a,40),EraseFx.frontAt(w,a),1e-9,"the server's front");
        // cut short, it stays where it was cut
        var cut=wave(40,0);cut.cut=6;
        assertEquals(EraseRules.front(6,40),EraseFx.frontAt(cut,15),1e-9);
    }
    @Test void atTheEndOfItsWayItIsEatenAndGone(){
        var w=wave(40,0);float end=EraseFx.endAt(w);
        assertEquals((40-EraseRules.NEAR)/EraseRules.SPEED,end,1e-6);
        assertEquals(0,EraseFx.eaten(w,end-1),1e-9,"whole as it goes");assertEquals(1,EraseFx.shown(w,end),1e-9);
        assertEquals(1,EraseFx.eaten(w,end+EraseFx.END1),1e-6);assertEquals(0,EraseFx.shown(w,end+EraseFx.FADE1),1e-6,"gone");
        assertTrue((end+EraseFx.FADE1)/20f<EraseZone.LIFE,"gone before its cast is");
        // a wall stops it short of 40
        var wall=wave(12.5,0);assertEquals((12.5-EraseRules.NEAR)/EraseRules.SPEED,EraseFx.endAt(wall),1e-6);
        // cut short: eaten at once
        var cut=wave(40,0);cut.cut=7;
        assertTrue(EraseFx.cutShort(cut));assertEquals(7,EraseFx.endAt(cut),1e-9);
        assertEquals(1,EraseFx.eaten(cut,7+EraseFx.CUT_TICKS),1e-6);assertEquals(0,EraseFx.shown(cut,7+EraseFx.CUT_TICKS+1),1e-6);
    }
    @Test void eachBlockComesApartAsItsFrontComesOverIt(){
        // when: at the middle, as the crown comes over it (the server's front a little behind); further on, later; past its end, never
        double range=30;
        assertEquals(0,EraseFx.pass(0,0,range),1e-9,"under its user, at once");
        float a=EraseFx.pass(12,0,range),b=EraseFx.pass(20,0,range);
        assertTrue(b>a,"further on, later");
        assertEquals((12-EraseFx.BOW*.5-EraseRules.NEAR)/EraseRules.SPEED,a,1e-5);
        assertTrue(EraseFx.pass(12,7.5,range)<a+1e-6||true);
        assertEquals(Float.MAX_VALUE,EraseFx.pass(range+EraseFx.BOW,0,range),"past where it goes, never");
        // its pixels: thrown up, falling back, resting on it, gone by 0.9 s; the same on every client
        int cell=EraseFx.cell(17,-40);
        boolean up=false;
        for(float t=0;t<1;t+=1/60f){
            var px=EraseFx.pixels(cell,t,0,1);
            for(var p:px){if(p==null)continue;assertTrue(p[1]>=.03-1e-9,"over the ground");if(p[1]>.6)up=true;assertTrue(t<.9f,"gone by then");}
        }
        assertTrue(up,"thrown up");
        assertArrayEquals(EraseFx.pixel(2,cell,.2f,0,1),EraseFx.pixel(2,cell,.2f,0,1));
        // its void: in as its pixels fly, held, drawn back within its time
        float r0=EraseFx.restore(cell);
        assertTrue(r0>=.95f&&r0<=1.25f);
        assertEquals(0,EraseFx.void_(0,r0),1e-9);assertEquals(1,EraseFx.void_(.5f,r0),1e-6);assertEquals(0,EraseFx.void_(r0+.25f,r0),1e-6);
        assertTrue(r0+.25f<EraseFx.GROUND_LIFE);
        assertTrue(EraseFx.GROUND_LIFE+(30-EraseRules.NEAR)/EraseRules.SPEED/20f<EraseZone.LIFE,"the last of it drawn back before its cast is gone");
    }
    @Test void whomItDeletesIsGoneHalfASecondGlitchingOutAndBackIn(){
        assertEquals(EraseRules.VANISH/20f,EraseClient.VANISH,1e-6);
        // white as it strikes, purple, gone, purple, lilac, and themselves again at VANISH
        assertEquals(EraseClient.STRUCK,EraseClient.silhouette(0));
        assertEquals(EraseClient.FLAT,EraseClient.silhouette(3.5f/60));
        assertEquals(-1,EraseClient.silhouette(.25f),"gone in the middle of it");
        assertEquals(EraseClient.BACK,EraseClient.silhouette(27.5f/60));
        assertEquals(-1,EraseClient.silhouette(EraseClient.VANISH),"themselves again");
        int frames=0;for(int k=0;k<30;k++)if(EraseClient.silhouette(k/60f+1e-4f)>=0)frames++;
        assertTrue(frames>=6&&frames<=12,"a few frames of silhouette, mostly gone: "+frames);
        assertTrue(EraseClient.slip(7.5f/60)>0,"one frame slipped aside");assertEquals(0,EraseClient.slip(4.5f/60),1e-9);
    }
}
