package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's R with their hack off, the jab, as drawn (2.124.0, the user's video and words: "검이 잠시 q활성화 상태의 빛나는
 * 검으로 변하고 적을 찔러 적의 얼굴에 플레이어 화면을 따라오는 보라색 X자가 생기고", "적을 찌르면 칼은 곧바로 검은 칼로"):
 * the knife is the hack's from just after the press until the blow and black at once on it; it turns once round on the
 * raised fist the way the video's does, clear of the ground; the thrust puts its point on a face; nothing of it steps
 * from one frame to the next; and the X is an X of pixels, white within purple, that blinks in, holds for as long as
 * the server holds whom it struck, and blinks out.
 */
class CorruptionJabTest {
    static final float NONE=Float.NaN;
    /** The fist and the knife's way as right, up, ahead of a user at the origin facing +z. */
    static Vec3d[] blade(float t){return CorruptionJab.blade(Vec3d.ZERO,0,t);}
    static Vec3d tip(float t){var hb=blade(t);return hb[0].add(hb[1].multiply(KnifeCombo.TIP));}
    static double rt(Vec3d v){return -v.x;}

    @Test void itsTimesAreTheServers(){
        assertEquals(JAB_LIT/20f,CorruptionJab.LIT,1e-6);assertEquals(JAB_TWIRL/20f,CorruptionJab.TWIRL0,1e-6);assertEquals(JAB_TWIRLED/20f,CorruptionJab.TWIRL1,1e-6);
        assertEquals(JAB_DRAWN/20f,CorruptionJab.DRAWN,1e-6);assertEquals(blowAt(JAB,0)/20f,CorruptionJab.BLOW,1e-6);
        assertEquals(.7f,CorruptionJab.BLOW,1e-6,"the video's: the thrust lands 0.7 s after the press");
        assertEquals(LENGTH[JAB]/20f,CorruptionJab.CLIP.length(),1e-4,"the clip is as long as the server holds its motion");
        assertTrue(CorruptionJab.LIT<CorruptionJab.TWIRL0&&CorruptionJab.TWIRL0<CorruptionJab.TWIRL1&&CorruptionJab.TWIRL1<CorruptionJab.DRAWN&&CorruptionJab.DRAWN<CorruptionJab.BLOW);
        assertSame(CorruptionJab.CLIP,KnifeCombo.clipOf(JAB));assertEquals(CorruptionJab.LIFE,KnifeCombo.life(JAB),1e-6);
        assertSame(GiantCleave.CLIP,KnifeCombo.clipOf(CLEAVE),"the cleave is still the cleave");
        assertTrue(CorruptionJab.LIFE>=CorruptionJab.BLOW+CorruptionJab.BURST_LIFE,"what the blow leaves on their face is drawn until it is gone");
    }
    @Test void theKnifeIsTheHacksUntilTheBlowAndBlackAtOnceOnIt(){
        assertNull(CorruptionJab.lit(0,NONE));assertNull(CorruptionJab.lit(CorruptionJab.LIT-.01f,NONE),"its own black as R is pressed");
        float[] on=CorruptionJab.lit(CorruptionJab.LIT,NONE);
        assertNotNull(on);assertEquals(1,on[0],0);assertEquals(0,on[1],1e-6,"it comes on as the hack does, from its beginning");
        assertTrue(CorruptionJab.LIT<=.1f,"lit a moment after the press");
        for(float t=CorruptionJab.LIT;t<CorruptionJab.BLOW-1e-4f;t+=1/240f){float[] l=CorruptionJab.lit(t,NONE);assertNotNull(l,"lit at "+t);assertEquals(1,l[0],0);assertEquals(t-CorruptionJab.LIT,l[1],1e-5);}
        // the hack's coming on is done well before the thrust: it is the steady glowing knife that is turned and thrust
        assertTrue(CorruptionJab.TWIRL0-CorruptionJab.LIT>=.08f&&CorruptionJab.BLOW-CorruptionJab.LIT>HackLook.ON);
        // ("적을 찌르면 칼은 곧바로 검은 칼로") black on the blow's own frame, no going out
        assertNull(CorruptionJab.lit(CorruptionJab.BLOW,NONE));assertNull(CorruptionJab.lit(CorruptionJab.BLOW+.01f,NONE));assertNull(CorruptionJab.lit(1.1f,NONE));
        // stopped short while lit: it goes out as the hack does, and is black after
        float[] off=CorruptionJab.lit(.42f,.4f);
        assertNotNull(off);assertEquals(0,off[0],0);assertEquals(.02f,off[1],1e-5);
        assertNull(CorruptionJab.lit(.4f+HackLook.OFF+.01f,.4f));
        assertNotNull(CorruptionJab.lit(.39f,.4f),"as it was until it stopped");assertEquals(1,CorruptionJab.lit(.39f,.4f)[0],0);
        assertNull(CorruptionJab.lit(.2f,.02f),"stopped before it lit: never lit");
    }
    @Test void itTurnsOnceRoundOnTheRaisedFistTheWayTheVideosDoes(){
        assertEquals(0,CorruptionJab.spinAt(0),0);assertEquals(0,CorruptionJab.spinAt(CorruptionJab.TWIRL0),1e-4);
        assertEquals(360,Math.abs(CorruptionJab.spinAt(CorruptionJab.TWIRL1)),1e-3,"once round");assertEquals(360,Math.abs(CorruptionJab.spinAt(1)),1e-3);
        float was=0;
        for(float t=0;t<=.6f;t+=1/240f){float s=Math.abs(CorruptionJab.spinAt(t));assertTrue(s>=was-1e-4f,"it turns back at "+t);assertTrue(s-was<14,"a step of "+(s-was)+" at "+t);was=s;}
        // the fist is up for it, higher than their head, and the arm keeps still while the knife turns on it
        Vec3d from=blade(CorruptionJab.TWIRL0)[0];
        assertTrue(from.y>1.75,"the fist "+from.y+" up");
        for(float t=CorruptionJab.TWIRL0;t<=CorruptionJab.TWIRL1;t+=1/120f)assertTrue(blade(t)[0].distanceTo(from)<.12,"the fist wanders at "+t);
        // its point: up and back over their head, round to their right, ahead of them, to their left, and up and back again
        float q=(CorruptionJab.TWIRL1-CorruptionJab.TWIRL0);
        java.util.function.DoubleFunction<Vec3d> at=turn->{
            // the moment it has turned so far
            float lo=CorruptionJab.TWIRL0,hi=CorruptionJab.TWIRL1;
            for(int i=0;i<40;i++){float mid=(lo+hi)/2;if(Math.abs(CorruptionJab.spinAt(mid))<turn)lo=mid;else hi=mid;}
            return blade(lo)[1];
        };
        Vec3d start=blade(CorruptionJab.TWIRL0)[1],right=at.apply(90),ahead=at.apply(180),left=at.apply(270),end=blade(CorruptionJab.TWIRL1)[1];
        assertTrue(start.y>.4&&start.z<-.5,"it starts up and back: "+start);
        assertTrue(rt(right)>.8,"then to their right (the video's, from behind: up, right, down, left): "+right);
        assertTrue(ahead.z>.8,"then ahead of them: "+ahead);
        assertTrue(rt(left)<-.8,"then to their left: "+left);
        assertTrue(end.dotProduct(start)>.98,"and round to where it began");
        assertTrue(q>=.25f&&q<=.35f,"in about the video's third of a second");
        // three blocks of knife: all the way round its point is clear of the ground, and of their own body
        double lowest=9;
        for(float t=0;t<=CorruptionJab.DRAWN;t+=1/240f){
            Vec3d tip=tip(t);lowest=Math.min(lowest,tip.y);
            Vec3d[] hb=blade(t);
            for(double u=.5;u<=KnifeCombo.TIP;u+=.1){
                Vec3d p=hb[0].add(hb[1].multiply(u));
                assertFalse(Math.abs(p.x)<.24&&Math.abs(p.z)<.12&&p.y<1.85,"the knife is in their body at "+t+": "+p);
            }
        }
        assertTrue(lowest>.5,"its point comes down to "+lowest);
        // the hand turns it: the same turn in whoever's hand, none once it is over or stopped
        assertEquals(0,CorruptionJab.spin(UUID.randomUUID(),0),0,"no jab, no turn");
    }
    @Test void theThrustPutsItsPointOnAFace(){
        // drawn back: the fist behind them and low, by the right hip, the point ahead on the foe
        Vec3d[] drawn=blade(CorruptionJab.DRAWN);
        assertTrue(drawn[0].z<-.3&&drawn[0].y<1&&rt(drawn[0])>.2,"the fist by the hip: "+drawn[0]);
        assertTrue(drawn[1].z>.9,"its point on the foe: "+drawn[1]);
        // the blow: a face's height (a player's eyes are 1.62 up), ahead on the middle line, within its lane
        Vec3d tip=tip(CorruptionJab.BLOW);
        assertTrue(tip.y>1.45&&tip.y<1.85,"its point "+tip.y+" up");
        assertTrue(Math.abs(tip.x)<.25,"on the middle line: "+tip.x);
        assertTrue(tip.z>3.3&&tip.z<=reach(JAB,0)+.1,"as far as it reaches: "+tip.z);
        assertTrue(blade(CorruptionJab.BLOW)[1].z>.95,"thrust straight ahead");
        // the body goes out behind it: the fist over a block further ahead than it was drawn back
        assertTrue(blade(CorruptionJab.BLOW)[0].z-drawn[0].z>1.5);
        // quickest as it lands: the last twentieth of a second before the blow covers more than the one before it began
        double thrust=tip(CorruptionJab.BLOW).distanceTo(tip(CorruptionJab.BLOW-.05f)),hold=tip(CorruptionJab.DRAWN+.035f).distanceTo(tip(CorruptionJab.DRAWN));
        assertTrue(thrust>1.2&&thrust>hold*4,"the thrust "+thrust+", the hold "+hold);
    }
    @Test void itBeginsAndEndsInTheStanceAndNothingSteps(){
        R6.Frame rest=KnifeCombo.AT_REST,a=CorruptionJab.CLIP.frame(0),z=CorruptionJab.CLIP.frame(CorruptionJab.LENGTH_S);
        for(int j=0;j<rest.joints().joint.length;j++)for(int k=0;k<3;k++){
            assertEquals(rest.joints().joint[j][k],a.joints().joint[j][k],.5,"joint "+j+" as it begins");
            assertEquals(rest.joints().joint[j][k],z.joints().joint[j][k],.5,"joint "+j+" as it ends");
        }
        assertEquals(0,z.fw(),1e-3);assertEquals(0,z.up(),1e-3);
        Vec3d fist=blade(0)[0],tip=tip(0);
        for(float t=1/480f;t<=CorruptionJab.LENGTH_S;t+=1/480f){
            Vec3d f=blade(t)[0],p=tip(t);
            assertTrue(f.distanceTo(fist)<.12,"the fist steps "+f.distanceTo(fist)+" at "+t);
            assertTrue(p.distanceTo(tip)<.45,"the knife's point steps "+p.distanceTo(tip)+" at "+t);
            assertTrue(p.y>.2,"its point in the ground at "+t);
            fist=f;tip=p;
        }
        // the body is back over its feet at the end, and never off the ground through it
        for(float t=0;t<=CorruptionJab.LENGTH_S;t+=1/60f)assertTrue(CorruptionJab.CLIP.frame(t).up()<=.05,"they leave the ground at "+t);
    }
    @Test void theXIsAnXOfPixelsWhiteWithinPurple(){
        byte[][] c=CorruptionJab.CELLS;int white=0,purple=0;
        assertEquals(CorruptionJab.DOWN,c.length);assertEquals(CorruptionJab.ACROSS,c[0].length);
        assertTrue(CorruptionJab.ACROSS>CorruptionJab.DOWN,"wider than it is high, as the video's");
        for(int y=0;y<CorruptionJab.DOWN;y++)for(int x=0;x<CorruptionJab.ACROSS;x++){
            assertEquals(c[y][x],c[y][CorruptionJab.ACROSS-1-x],"the same left and right");assertEquals(c[y][x],c[CorruptionJab.DOWN-1-y][x],"the same up and down");
            if(c[y][x]==2){
                white++;
                // its white is outlined all round: every pixel beside one, corners too, is white or purple, and none is at the edge
                assertTrue(x>0&&y>0&&x<CorruptionJab.ACROSS-1&&y<CorruptionJab.DOWN-1,"white at the edge");
                assertTrue(c[y-1][x]!=0&&c[y+1][x]!=0&&c[y][x-1]!=0&&c[y][x+1]!=0,"white beside nothing at "+x+","+y);
            }else if(c[y][x]==1)purple++;
        }
        assertTrue(white>=30&&purple>white*.6,"white "+white+", purple "+purple);
        // an X: its middle is white, the middles of its four sides are empty, and its strokes run out to its corners
        int mx=CorruptionJab.ACROSS/2,my=CorruptionJab.DOWN/2;
        assertEquals(2,c[my][mx]);assertEquals(0,c[0][mx]);assertEquals(0,c[CorruptionJab.DOWN-1][mx]);assertEquals(0,c[my][0]);assertEquals(0,c[my][CorruptionJab.ACROSS-1]);
        assertEquals(2,c[2][2]);assertEquals(2,c[2][CorruptionJab.ACROSS-3]);
        // wider than a head (half a block), not as wide as a body is tall
        assertTrue(CorruptionJab.WIDE>.6&&CorruptionJab.WIDE<1.1);assertEquals(CorruptionJab.WIDE,CorruptionJab.CELL*CorruptionJab.ACROSS,1e-9);
        // it lies before their face toward the camera, not in their head
        Vec3d face=CorruptionJab.face(Vec3d.ZERO,1.62),camera=new Vec3d(0,2.5,-6);
        Vec3d at=CorruptionJab.before(face,.3,camera);
        assertTrue(face.y>1.6&&face.y<1.75,"on their face: "+face.y);
        assertEquals(.3+CorruptionJab.FRONT,at.distanceTo(face),1e-6);assertTrue(at.distanceTo(camera)<face.distanceTo(camera));
        assertTrue(CorruptionJab.before(face,.3,face.add(0,0,.2)).distanceTo(face)<=.1+1e-9,"never past a camera right at them");
    }
    @Test void theXBlinksInHoldsAndBlinksOut(){
        float all=JAB_STUN/20f;
        assertEquals(8,all,1e-6,"the user's eight seconds");
        assertEquals(0,CorruptionJab.shows(-.01f,all,3),0);assertEquals(0,CorruptionJab.shows(all,0,3),0,"gone as they are free");
        boolean on=false,off=false;
        for(float s=0;s<CorruptionJab.X_IN;s+=1/240f){float a=CorruptionJab.shows(s,all-s,3);on|=a>0;off|=a==0;}
        assertTrue(on&&off,"it blinks in");
        for(float s=CorruptionJab.X_IN;s<all-CorruptionJab.X_OUT;s+=1/60f)assertEquals(1,CorruptionJab.shows(s,all-s,3),0,"steady at "+s);
        on=off=false;
        for(float left=CorruptionJab.X_OUT;left>0;left-=1/240f){float a=CorruptionJab.shows(all-left,left,3);on|=a>0;off|=a==0;}
        assertTrue(on&&off,"it blinks out");
        // popped in from bigger, its own size while it holds, shrinking as it goes; no step
        assertTrue(CorruptionJab.size(0,all)>1.6f);assertEquals(1,CorruptionJab.size(CorruptionJab.X_IN,all),1e-4);assertEquals(1,CorruptionJab.size(4,4),1e-6);
        assertTrue(CorruptionJab.size(all-.01f,.01f)<.7f);
        float was=CorruptionJab.size(0,all);
        for(float s=1/240f;s<all;s+=1/240f){float z=CorruptionJab.size(s,all-s);assertTrue(Math.abs(z-was)<.06f,"its size steps at "+s);was=z;}
        // it rocks a little, never far, and smoothly
        float tip=CorruptionJab.tip(0,5),most=0;
        for(float t=1/240f;t<all;t+=1/240f){float a=CorruptionJab.tip(t,5);assertTrue(Math.abs(a-tip)<.5f);most=Math.max(most,Math.abs(a));tip=a;}
        assertTrue(most>3&&most<8,"it tips "+most);
        // now and then its rows slip a pixel for a moment: seldom, and never more than one
        int slipped=0,frames=0;
        for(float t=0;t<all;t+=1/60f){frames++;boolean any=false;for(int row=0;row<CorruptionJab.DOWN;row++){int k=CorruptionJab.slip(t,row,9);assertTrue(Math.abs(k)<=1);any|=k!=0;}if(any)slipped++;}
        assertTrue(slipped>0&&slipped<frames*.12,"slipped "+slipped+" of "+frames+" frames");
    }
    @Test void whomItStruckIsMarkedForAsLongAsTheServerHoldsThem(){
        CorruptionJab.clear();
        assertFalse(CorruptionJab.marked(77,500));
        // a jab this client heard of at its tick 500, as it began: its blow is drawn at its tick 514
        var cast=new StandClient.Cast(UUID.randomUUID(),UUID.randomUUID(),null,SKILL,JAB,1000,1000,500,Vec3d.ZERO,0,List.of());
        // the server's word of the blow comes a tick before this client draws it: the X waits for the knife
        CorruptionJab.mark(cast,List.of(new StandClient.Target(UUID.randomUUID(),77,Vec3d.ZERO)));
        assertTrue(CorruptionJab.marked(77,513),"marked at once (no stars over them)");assertFalse(CorruptionJab.marked(78,513));
        assertEquals("77:-0.05",CorruptionJab.describe(513,0),"its X not yet on: the knife lands next tick");
        assertEquals("77:0.00",CorruptionJab.describe(514,0));assertEquals("77:0.05",CorruptionJab.describe(515,0));
        // as long as the server holds them, to the tick
        assertTrue(CorruptionJab.marked(77,514+JAB_STUN-1));assertFalse(CorruptionJab.marked(77,514+JAB_STUN));
        // a jab heard of late (2 ticks in) runs as far ahead
        var late=new StandClient.Cast(UUID.randomUUID(),UUID.randomUUID(),null,SKILL,JAB,1000,1002,500,Vec3d.ZERO,0,List.of());
        CorruptionJab.mark(late,List.of(new StandClient.Target(UUID.randomUUID(),79,Vec3d.ZERO)));
        assertEquals("77:0.00 79:0.10",CorruptionJab.describe(514,0));
        CorruptionJab.clear();assertFalse(CorruptionJab.marked(77,500));
    }
    @Test void itsSoundsAreThePresssAndTheThrustsOnTheBlow(){
        assertEquals(blowAt(JAB,0)-KnifeClient.JAB_SOUND_BEFORE,KnifeClient.soundAt(JAB),"the thrust's sound starts so that its onset is on the blow");
        assertEquals(0,KnifeClient.soundAt(CLEAVE));assertEquals(blowAt(SLASH,0)-KnifeClient.SOUND_BEFORE,KnifeClient.soundAt(SLASH));
        assertNotEquals(KnifeClient.JAB_PRESS_SOUND,KnifeClient.JAB_SOUND);
        // a small shake as it lands, none before
        var cast=new StandClient.Cast(UUID.randomUUID(),UUID.randomUUID(),null,SKILL,JAB,0,0,0,Vec3d.ZERO,0,List.of());
        assertEquals(0,CorruptionJab.shake(cast,CorruptionJab.BLOW-.05f),1e-6);
        assertTrue(CorruptionJab.shake(cast,CorruptionJab.BLOW+.02f)>0&&CorruptionJab.shake(cast,CorruptionJab.BLOW+.02f)<=.9f);
    }
    @Test void itsTwoSoundsAreTheModsMonoAndAsLongAsTheyWereMade()throws Exception{
        var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(KnifeSoundsTest.ASSETS.resolve("sounds.json"))).getAsJsonObject();
        String[] names={KnifeClient.JAB_PRESS_SOUND,KnifeClient.JAB_SOUND};float[] long_={.107f,.61f};
        for(int i=0;i<2;i++){
            String name=names[i];
            assertTrue(json.has(name),name+" in sounds.json");
            assertEquals("celestialfall:"+name,json.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
            assertTrue(java.nio.file.Files.exists(KnifeSoundsTest.ogg(name)),name+".ogg");
            byte[] b=java.nio.file.Files.readAllBytes(KnifeSoundsTest.ogg(name));int id=KnifeSoundsTest.header(b);
            assertTrue(id>=0,name+" is Ogg Vorbis");assertEquals(1,b[id+11],name+" mono");
            assertEquals(48000,(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,name+" at 48 kHz");
            assertEquals(long_[i],KnifeSoundsTest.seconds(b),.02f,name+" as long as extract_jab.py makes it");
        }
        // the thrust's file leads its onset by a twentieth of a second: started a tick before the blow, the onset is on it
        assertEquals(1,KnifeClient.JAB_SOUND_BEFORE);
        // both are over before the jab is drawn no more
        assertTrue(KnifeClient.soundAt(JAB)/20f+.61f<CorruptionJab.LIFE);
    }
}
