package dev.sangwoo.celestialfall.client.xchara;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's E with their hack on as drawn (2.119.0, user request: "Q활성화 E스킬부터 만들자 간단하게 패널 누르는 모션 나오고
 * 몸에 연두 이펙트 둘러지고 65힐"): the press brings the panel's arm up in front of them and its hand down on the panel
 * once, as its sound's press sounds, on it as the server heals them (2.120.0: the user's sound has one press), then back, nothing of it stepping from one frame to the next; stopped short it
 * goes back at once; the lime light round the body rises from the feet to over the head, winds round them and is gone
 * in a second; its sound is the mod's (2.120.0: the user's, lifted out of their recording).
 */
class HealClientTest {
    static final float NONE=Float.NaN;

    @Test void thePressComesUpComesDownOnceAsItsSoundPressesAndGoesBack(){
        assertEquals(HEAL_AT/20f,HealClient.HEALS,1e-6,"the heal lands as the server heals them");
        // the sound: played SOUND_AT ticks in, its press 10 ms into the file and its heal's tone 55 ms in
        float sound=HealClient.SOUND_AT/20f;
        assertEquals(sound+.010f,HealClient.PRESS,.006f,"the hand is down as the sound's press starts");
        assertEquals(sound+.055f,HealClient.HEALS,.006f,"the heal's tone starts as the heal lands");
        assertTrue(HealClient.PRESS>.25f&&HealClient.PRESS<HealClient.HEALS,"the press once the arm is up, before the heal");
        assertEquals(0,HealClient.lift(0,NONE),1e-6);assertEquals(1,HealClient.lift(.2f,NONE),1e-6);
        assertEquals(1,HealClient.lift(HealClient.PRESS,NONE),1e-6,"up as it presses");assertEquals(1,HealClient.lift(HealClient.HEALS,NONE),1e-6);
        assertEquals(0,HealClient.lift(HEAL_LENGTH/20f,NONE),1e-6,"back as it is over");
        // one press: off the panel until the arm is up, down as the press sounds, on it through the heal, off it after
        assertEquals(0,HealClient.tap(.2f),1e-6);assertEquals(0,HealClient.tap(HealClient.PRESS-.07f),1e-6);
        assertEquals(1,HealClient.tap(HealClient.PRESS),1e-6);assertEquals(1,HealClient.tap(HealClient.HEALS),1e-6,"on it as the heal lands");
        assertEquals(0,HealClient.tap(.6f),1e-6,"off it before the arm goes back");
        int downs=0;float was=0;
        for(float t=0;t<1.2f;t+=1/240f){float p=HealClient.tap(t);if(was<.5f&&p>=.5f)downs++;was=p;}
        assertEquals(1,downs,"once");
        // lit lime: from the hand's touch, full as the heal lands, gone before the arm is back
        assertEquals(0,HealClient.lit(HealClient.PRESS),1e-6);assertEquals(1,HealClient.lit(HealClient.HEALS),1e-6);assertEquals(0,HealClient.lit(1.05f),1e-6);
        // nothing steps from one frame to the next
        float lift=0,tap=0,lit=0;
        for(float t=0;t<1.2f;t+=1/240f){
            float l=HealClient.lift(t,NONE),p=HealClient.tap(t),g=HealClient.lit(t);
            assertTrue(Math.abs(l-lift)<.05f&&Math.abs(p-tap)<.12f&&Math.abs(g-lit)<.22f,"a step at "+t);
            lift=l;tap=p;lit=g;
        }
    }
    @Test void stoppedShortTheArmGoesBackAtOnce(){
        float stopped=.3f;
        assertEquals(HealClient.lift(stopped,NONE),HealClient.lift(stopped,stopped),1e-6,"from where it was");
        assertEquals(0,HealClient.lift(stopped+.15f,stopped),1e-6,"down in a moment");
        float before=HealClient.lift(stopped,stopped);
        for(float t=stopped;t<stopped+.2f;t+=1/240f){float l=HealClient.lift(t,stopped);assertTrue(l<=before+1e-6&&before-l<.08f);before=l;}
    }
    @Test void thePanelsArmComesUpInFrontAndItsHandComesDownOnATap(){
        for(boolean right:new boolean[]{true,false}){
            int off=right?1:0;float out=right?1:-1;
            MotionPose rest=new MotionPose(),up=new MotionPose(),tapped=new MotionPose();
            XCharaIdle.arms(rest,right,1,1,0,0,0);XCharaIdle.arms(up,right,1,1,0,1,0);XCharaIdle.arms(tapped,right,1,1,0,1,1);
            float[] a=rest.joint[MotionPose.RIGHT_ARM+off],b=up.joint[MotionPose.RIGHT_ARM+off],c=tapped.joint[MotionPose.RIGHT_ARM+off];
            // at rest as ever: straight, out from the body
            assertEquals(0,rest.elbow[off],1e-4);assertEquals(-out*XCharaIdle.PANEL_OUT,a[2],1);
            // up: the upper arm forward, the elbow bent, hardly out
            assertEquals(-XCharaIdle.PRESS_FORWARD,b[0],1e-3);assertEquals(-XCharaIdle.PRESS_ELBOW,up.elbow[off],1e-3);
            assertTrue(Math.abs(b[2])<Math.abs(a[2]),"in from where it hung");
            // a tap: the hand a little down (the arm and the elbow let down)
            assertTrue(c[0]>b[0]&&tapped.elbow[off]>up.elbow[off]);
            assertTrue(c[0]-b[0]<=10&&tapped.elbow[off]-up.elbow[off]<=20,"a tap, not a swing");
            // the knife's arm is not moved by it
            int knife=1-off;
            assertArrayEquals(rest.joint[MotionPose.RIGHT_ARM+knife],tapped.joint[MotionPose.RIGHT_ARM+knife],1e-5f);
            // half way up it is half way between
            MotionPose half=new MotionPose();XCharaIdle.arms(half,right,1,1,0,.5f,0);
            assertEquals((a[0]+b[0])/2,half.joint[MotionPose.RIGHT_ARM+off][0],1e-3);
        }
        // the panel under the hand: it floats below the hand and ahead of it, nearly flat; the hand comes down on a tap and
        // the panel stays where it floats (it is that much nearer the hand), never through the hand
        float[] rest=XCharaIdle.pressHand(0),down=XCharaIdle.pressHand(1);
        float dip=down[1]-rest[1];
        assertTrue(dip>.1f&&dip<.22f,"the hand comes down a hand's height on a tap: "+dip);
        assertTrue(rest[0]>.5f,"the hand well ahead of the shoulder");
        float[] carried=HackPanel.pressed(HackPanel.OUT,HackPanel.DOWN,HackPanel.AHEAD,HackPanel.TILT,0,0);
        assertArrayEquals(new float[]{HackPanel.OUT,HackPanel.DOWN,HackPanel.AHEAD,HackPanel.TILT},carried,1e-6f,"carried as ever with no press");
        float[] hover=HackPanel.pressed(HackPanel.OUT,HackPanel.DOWN,HackPanel.AHEAD,HackPanel.TILT,1,0),tapped=HackPanel.pressed(HackPanel.OUT,HackPanel.DOWN,HackPanel.AHEAD,HackPanel.TILT,1,1);
        assertEquals(HackPanel.PRESS_DOWN,hover[1],1e-6);assertTrue(hover[3]<15,"its face up");
        assertEquals(hover[1]-dip,tapped[1],1e-5,"it stays where it floats as the hand comes down");
        assertTrue(tapped[1]>.12f&&tapped[1]<.2f,"the fist on it, not through it: "+tapped[1]);
        float before=HackPanel.DOWN;
        for(float l=0;l<=1;l+=.01f){float[] q=HackPanel.pressed(HackPanel.OUT,HackPanel.DOWN,HackPanel.AHEAD,HackPanel.TILT,l,0);assertTrue(Math.abs(q[1]-before)<.01f);before=q[1];}
        // the old call is the pose with no press
        MotionPose one=new MotionPose(),two=new MotionPose();
        XCharaIdle.arms(one,true,1,1,3);XCharaIdle.arms(two,true,1,1,3,0,0);
        for(int i=0;i<2;i++)assertArrayEquals(one.joint[MotionPose.RIGHT_ARM+i],two.joint[MotionPose.RIGHT_ARM+i],1e-6f);
        assertTrue(XCharaIdle.LOOK_DOWN>15&&XCharaIdle.LOOK_DOWN<50,"they look down at it");
    }
    @Test void theLimeLightRisesRoundTheBodyAndIsGoneInASecond(){
        assertTrue(HealClient.EFFECT<=1.2f);
        // rings: from the feet to over the head, one after another, each only rising
        for(int k=0;k<HealClient.RINGS;k++){
            double high=-1;float shown=0;boolean seen=false;
            for(float s=0;s<HealClient.EFFECT;s+=1/240f){
                var r=HealClient.ring(k,s);if(r==null)continue;seen=true;
                assertTrue(r[0]>=high-1e-9&&r[0]<=1,"ring "+k+" only rises");assertTrue(Math.abs(r[1]-shown)<.3,"ring "+k+" steps at "+s);
                high=r[0];shown=(float)r[1];
            }
            assertTrue(seen&&high>.95,"ring "+k+" comes up over their head");
            assertNull(HealClient.ring(k,HealClient.EFFECT),"gone with the light");
        }
        assertNull(HealClient.ring(1,.05f),"the second starts after the first");assertNotNull(HealClient.ring(0,.05f));
        // pixels wind up round them, half one way and half the other; plus signs rise beside them
        for(int seed=1;seed<30;seed+=7){
            for(int j=0;j<HealClient.PIXELS;j++){
                double[] first=null,last=null;
                for(float s=0;s<HealClient.EFFECT+.2f;s+=1/240f){
                    var p=HealClient.pixel(j,seed,s);
                    if(s>=HealClient.EFFECT){assertNull(p,"pixel "+j+" gone with the light");continue;}
                    if(p==null)continue;if(first==null)first=p;
                    if(last!=null){assertTrue(p[1]>=last[1]-1e-9,"it only rises");assertTrue(Math.abs(p[0]-last[0])<.2,"it winds, never jumps");}
                    assertTrue(p[2]>0&&p[2]<.06&&p[3]>=0&&p[3]<=1);
                    last=p;
                }
                assertNotNull(first);
                assertTrue(Math.abs(last[0]-first[0])>Math.PI,"pixel "+j+" winds well round them");
                assertEquals(j%2==0,last[0]>first[0],"one way or the other by turns");
            }
            for(int j=0;j<HealClient.PLUSES;j++){
                double low=-1;
                for(float s=0;s<HealClient.EFFECT+.2f;s+=1/240f){
                    var p=HealClient.plus(j,seed,s);
                    if(s>=HealClient.EFFECT){assertNull(p);continue;}
                    if(p==null)continue;
                    assertTrue(p[2]>=low-1e-9,"it rises");low=p[2];
                    assertTrue(p[1]>HealClient.RADIUS&&p[1]<1.1,"beside them, outside the rings");assertTrue(p[2]>.3&&p[2]<2.2);
                }
                assertTrue(low>0);
            }
        }
        // round the body: the rings stand off it, within arm's reach
        assertTrue(HealClient.RADIUS>.45&&HealClient.RADIUS<.9);
        assertTrue(HealClient.LIFE>=HEAL_AT+HealClient.EFFECT*20,"kept until the light is gone");
    }
    @Test void itsSoundIsTheModsMonoAndCoversThePressAndTheLight()throws Exception{
        JsonObject json=JsonParser.parseString(Files.readString(KnifeSoundsTest.ASSETS.resolve("sounds.json"))).getAsJsonObject();
        String name="xchara_e";
        assertTrue(json.has(name));
        assertEquals("celestialfall:"+name,json.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
        byte[] b=Files.readAllBytes(KnifeSoundsTest.ogg(name));int id=KnifeSoundsTest.header(b);
        assertTrue(id>=0,name+" is Ogg Vorbis");
        assertEquals(1,b[id+11],name+" mono");
        assertEquals(48000,(b[id+12]&255)|(b[id+13]&255)<<8|(b[id+14]&255)<<16|(b[id+15]&255)<<24,name+" at 48 kHz");
        float length=KnifeSoundsTest.seconds(b);
        assertEquals(.455f,length,.02f,name+" as long as extract_heal.py cuts it");
        // played SOUND_AT ticks in: over once the heal's light is well up, before the light is gone
        float end=HealClient.SOUND_AT/20f+length;
        assertTrue(end>HealClient.HEALS+.3f&&end<=HealClient.HEALS+HealClient.EFFECT,"through the press and the heal");
    }
}
