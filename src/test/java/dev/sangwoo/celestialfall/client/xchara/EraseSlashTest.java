package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the delete slash, its user as drawn (2.133.0, the user's: "5칸 정도 뛰어올라서 거대한
 * 참격을 바라보는 방향으로 날리고"): from the stance and back to it; sunk, sprung, wound at the top with the knife behind the
 * head, chopped down at the ground ahead as the slash goes, landed deep; no frame of it jumps and the knife's point keeps
 * off the ground; its knife lit through it; its body drawn up over where they stand as high as the server says, falling
 * back if it stops short in the air; its sounds at their moments.
 */
class EraseSlashTest {
    static final float NEVER=Float.MAX_VALUE;
    static float ground(float sec){return (float)EraseSlash.GROUND.at(sec);}

    @Test void itGoesFromTheStanceAndBackToIt(){
        var clip=EraseSlash.CLIP;
        assertSame(clip,KnifeCombo.clipOf(ERASE));
        assertEquals(LENGTH[ERASE]/20f,clip.length(),1e-4);assertEquals(EraseSlash.LIFE,KnifeCombo.life(ERASE),1e-6);
        var rest=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(KnifeCombo.AT_REST));
        for(float t:new float[]{0,clip.length()}){
            var b=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(t)));
            assertEquals(0,rest[0].distanceTo(b[0]),1e-4,"from the stance and back to it");assertEquals(0,rest[1].distanceTo(b[1]),1e-4);
        }
    }
    @Test void woundAtTheTopItChopsDownAtTheGroundAhead(){
        var clip=EraseSlash.CLIP;
        // crouched to spring
        assertTrue(clip.frame(.13f).up()<-.2,"sunk to spring: "+clip.frame(.13f).up());
        // the top: the knife back behind the head, its hand up by the shoulder or over it
        var top=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(EraseSlash.RISEN)));
        assertTrue(top[1].z<-.6,"the knife back behind them: "+top[1]);assertTrue(top[0].y>1.4,"its hand up: "+top[0].y);
        // the chop: down at the ground ahead, the body folded after it
        var chop=KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(EraseSlash.SLASH)));
        assertTrue(chop[1].y<-.4&&chop[1].z>.4,"down at the ground ahead: "+chop[1]);
        assertTrue(clip.frame(EraseSlash.SLASH).pitch()>20,"folded forward: "+clip.frame(EraseSlash.SLASH).pitch());
        // landed deep
        assertTrue(clip.frame(EraseSlash.LAND+.08f).up()<-.35,"landed deep: "+clip.frame(EraseSlash.LAND+.08f).up());
    }
    @Test void noFrameOfItJumpsAndTheKnifeKeepsOffTheGround(){
        var clip=EraseSlash.CLIP;Vec3d lastFist=null,lastHand=null,lastWay=null;float lastPitch=0,lastUp=0;
        for(float t=0;t<=clip.length();t+=1/60f){
            var fr=clip.frame(t);var p=R6.poseOf(fr);var b=KagenoClips.blade(Vec3d.ZERO,0,p);Vec3d hand=KagenoClips.freeHand(Vec3d.ZERO,0,p);
            Vec3d tip=b[0].add(b[1].multiply(ShotRules.TIP));
            assertTrue(tip.y>ground(t)+.05,"the knife's point clear of the ground at "+t+": "+tip.y+" over "+ground(t));
            if(lastFist!=null){
                // (the chop is fast: its hand goes some 22 blocks a second at its fastest, its knife's point a hundred)
                assertTrue(b[0].distanceTo(lastFist)<(t>.7f&&t<.86f?.5:.3),"the knife's hand at "+t+": "+b[0].distanceTo(lastFist));
                assertTrue(hand.distanceTo(lastHand)<.36,"the free hand at "+t+": "+hand.distanceTo(lastHand));
                double turn=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,b[1].dotProduct(lastWay)))));
                assertTrue(turn<62,"the knife turns no more than a chop's frame at "+t+": "+turn);
                // (the chop folds the trunk some 30 degrees in a tenth of a second)
                assertTrue(Math.abs(fr.pitch()-lastPitch)<(t>.7f&&t<.86f?10:8),"the body at "+t+": "+fr.pitch()+" after "+lastPitch);
                assertTrue(Math.abs(fr.up()-lastUp)<.12,"the body's height at "+t+": "+fr.up()+" after "+lastUp);
            }
            lastFist=b[0];lastHand=hand;lastWay=b[1];lastPitch=fr.pitch();lastUp=(float)fr.up();
        }
    }
    @Test void noJointOfItJumpsOrFlips(){
        var a=EraseSlash.CLIP;double h=1/960.0;
        for(double t=h;t+2*h<=a.length();t+=h){
            var p0=a.frame((float)(t-h));var p1=a.frame((float)t);var p2=a.frame((float)(t+h));var p3=a.frame((float)(t+2*h));
            for(int j=0;j<6;j++){
                double before=KnifeComboTest.turn(p0,p1,j),now=KnifeComboTest.turn(p1,p2,j),after=KnifeComboTest.turn(p2,p3,j);
                assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
            }
        }
        for(double t=0;t+1/60.0<=a.length();t+=1/60.0){
            for(int j=0;j<6;j++)assertTrue(KnifeComboTest.turn(a.frame((float)t),a.frame((float)(t+1/60.0)),j)<125,"joint "+j+" flips at "+t);
            var f0=a.frame((float)t).joints();var f1=a.frame((float)(t+1/60.0)).joints();
            for(int i=0;i<2;i++){
                assertTrue(Math.abs(f1.elbow[i]-f0.elbow[i])<75,"elbow "+i+" snaps at "+t);
                assertTrue(Math.abs(f1.knee[i]-f0.knee[i])<55,"knee "+i+" snaps at "+t);
            }
        }
    }
    @Test void theKnifeIsLitThroughItAndGoesOutAsTheHacksDoes(){
        assertNull(EraseSlash.lit(EraseSlash.LIT-.01f,Float.NaN));
        assertEquals(1,EraseSlash.lit(EraseSlash.SLASH,Float.NaN)[0]);assertEquals(1,EraseSlash.lit(EraseSlash.LAND,Float.NaN)[0]);
        assertEquals(0,EraseSlash.lit(EraseSlash.OUT+.05f,Float.NaN)[0],"going out once they are up");
        assertNull(EraseSlash.lit(EraseSlash.OUT+HackLook.OFF+.01f,Float.NaN));
        assertEquals(0,EraseSlash.lit(.3f,.2f)[0],"stopped short: it goes out then");assertNull(EraseSlash.lit(.04f,.02f));
    }
    static StandClient.Cast cast(UUID id){return new StandClient.Cast(id,new UUID(3,4),new Identifier("minecraft","overworld"),SKILL,ERASE,1000,1000,0,Vec3d.ZERO,0,List.of());}
    @Test void theBodyIsDrawnUpAsHighAsTheServerSaysAndFallsBackIfItStopsShort(){
        var id=new UUID(5,6);var c=cast(id);
        // told nothing of it: drawn where it stands
        assertEquals(0,EraseSlash.height(c,EraseRules.SLASH),1e-9);
        // the server's leap: five blocks at the top, on the ground before and after
        KnifeClient.told(id,5,Float.NaN);
        assertEquals(0,EraseSlash.height(c,EraseRules.LEAVE),1e-9);assertEquals(5,EraseSlash.height(c,EraseRules.RISEN),1e-9);
        assertEquals(5,EraseSlash.height(c,EraseRules.SLASH),1e-9,"at the top as it chops");assertEquals(0,EraseSlash.height(c,EraseRules.LAND),1e-9);
        // under a roof, less
        KnifeClient.told(id,2.5f,Float.NaN);assertEquals(2.5,EraseSlash.height(c,EraseRules.SLASH),1e-9);
        // stopped short in the air: it falls back from where it was, smoothly, to the ground
        KnifeClient.told(id,5,14);
        assertEquals(5,EraseSlash.height(c,14),1e-9);assertEquals(0,EraseSlash.height(c,14+EraseSlash.FALLBACK),1e-9);
        double last=EraseSlash.height(c,14);
        for(float a=14;a<=14+EraseSlash.FALLBACK+2;a+=1/3f){double h=EraseSlash.height(c,a);assertTrue(h<=last+1e-9&&last-h<1.1,"falling at "+a+": "+h);last=h;}
        KnifeClient.told(id,0,Float.NaN);
    }
    @Test void itsSoundsComeAtTheirMoments(){
        int last=-1;
        for(var cue:EraseSlash.CUES){
            int at=(Integer)cue[1];assertTrue(at>=0&&at<EraseRules.LENGTH,"within it: "+at);
            assertTrue(at>=last,"in turn");last=at;
            assertTrue((Float)cue[2]>0&&(Float)cue[2]<=1);
        }
        assertEquals(EraseRules.LEAVE-1,(int)(Integer)EraseSlash.CUES[1][1],"the leap's a tick before they leave");
        assertEquals(EraseRules.SLASH-1,(int)(Integer)EraseSlash.CUES[3][1],"the boom's onset as the slash goes");
    }
}
