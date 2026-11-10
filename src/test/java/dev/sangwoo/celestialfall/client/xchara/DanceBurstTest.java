package dev.sangwoo.celestialfall.client.xchara;

import java.util.Arrays;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's G's blows in the world (2.112.4, user request: "그냥 꽃 모양은 포기하고 너가 창작해줄 수 없음? 수 많은 검이
 * 박혀서 몸에서 이펙트가 터지는거", then "검이 날아오면 안돼" and "꽂혀도 안돼 그냥 이펙트만 터지게"): no flower and no sword,
 * only light bursting out of the one marked's body where an unseen blade goes through it, the blades from all round them;
 * each eruption from their skin out along the blade's way and soon gone; at the end everything out of their whole body,
 * bigger. 2.112.5 ("이펙트 크게 만들어주고 보라색을 더 진하게 만들어봐"): half again as big as 2.112.4's, a deep purple with
 * white only in thin cores and flashes, and black.
 */
class DanceBurstTest {
    /** A player's body, and where they stand. */
    static final double W=.6,H=1.8;
    static final Vec3d FEET=new Vec3d(4.5,64,-7.5);
    static final int SEED=12345;

    static boolean inBox(Vec3d p,double slack){return Math.abs(p.x)<=W/2+slack&&Math.abs(p.z)<=W/2+slack&&p.y>=-slack&&p.y<=H+slack;}
    static boolean onSkin(Vec3d p){
        double e=1e-6;
        return inBox(p,e)&&(Math.abs(Math.abs(p.x)-W/2)<e||Math.abs(Math.abs(p.z)-W/2)<e||Math.abs(p.y)<e||Math.abs(p.y-H)<e);
    }

    @Test void eachBlowGoesThroughTheirBodyFromSkinToSkin(){
        for(int k=0;k<HITS;k++){
            var p=DanceBurst.pierce(k,SEED,W,H);
            assertEquals(1,p.way().length(),1e-9);
            assertTrue(inBox(p.inside(),-.05),"blow "+k+" through a point inside them: "+p.inside());
            assertTrue(onSkin(p.in())&&onSkin(p.out()),"blow "+k+" goes in and comes out on their skin: "+p.in()+" "+p.out());
            assertTrue(p.out().subtract(p.inside()).dotProduct(p.way())>0&&p.in().subtract(p.inside()).dotProduct(p.way())<0,"in on one side, out the other");
            assertEquals(1,p.out().subtract(p.in()).normalize().dotProduct(p.way()),1e-9,"along its way");
            assertTrue(p.inside().y>H*.25,"through their middle or their head, not their feet: "+p.inside().y);
        }
    }
    @Test void theUnseenBladesComeFromAllRoundThem(){
        double[] az=new double[HITS];
        for(int k=0;k<HITS;k++){
            Vec3d from=DanceBurst.pierce(k,SEED,W,H).way().negate();
            az[k]=Math.atan2(from.z,from.x);
            double el=Math.toDegrees(Math.asin(from.y));
            assertTrue(el>=DanceBurst.LOW-1e-9&&el<=DanceBurst.HIGH+1e-9,"blow "+k+" from "+el+" degrees up");
            assertTrue(DanceBurst.pierce(k,SEED,W,H).way().y>-.25,"few into the ground: "+k);
        }
        Arrays.sort(az);double gap=az[0]+2*Math.PI-az[HITS-1];
        for(int k=1;k<HITS;k++)gap=Math.max(gap,az[k]-az[k-1]);
        assertTrue(Math.toDegrees(gap)<30,"no side of them left out: a gap of "+Math.toDegrees(gap)+" degrees");
        // every blow its own, the same on every client
        for(int k=0;k+1<HITS;k++)assertTrue(DanceBurst.pierce(k,SEED,W,H).out().distanceTo(DanceBurst.pierce(k+1,SEED,W,H).out())>.05,"blows "+k+" and "+(k+1)+" alike");
        assertEquals(DanceBurst.pierce(7,SEED,W,H),DanceBurst.pierce(7,SEED,W,H));
    }
    @Test void itBurstsOutOfTheirBodyAlongTheBladesWaySmallAndIsSoonGone(){
        for(int k=0;k<HITS;k++){
            boolean last=k==HITS-1;double big=DanceBurst.big(last);
            var p=DanceBurst.pierce(k,SEED,W,H);Vec3d out=FEET.add(p.out());
            boolean seen=false,thorn=false;
            for(float s=0;s<DanceBurst.LIFE+1;s+=1/6f){
                for(int j=0;j<DanceBurst.SPIKES;j++){
                    var sp=DanceBurst.spike(out,p.way(),k,j,SEED,s,big);if(sp==null)continue;
                    seen=true;thorn|=sp.rgb()==DanceBurst.INK;
                    assertTrue(sp.a().distanceTo(out)<.06,"from their skin");
                    Vec3d d=sp.b().subtract(sp.a()).normalize();
                    assertTrue(d.dotProduct(p.way())>Math.cos(Math.toRadians(42)),"out of them along its way: "+Math.toDegrees(Math.acos(d.dotProduct(p.way())))+" degrees off");
                    assertTrue(sp.b().distanceTo(out)<2.7*big,"some one and a half to four blocks: "+sp.b().distanceTo(out));
                    assertTrue(s<DanceBurst.SPIKE_LIFE);
                }
                for(boolean drop:new boolean[]{false,true})for(int j=0;j<(drop?DanceBurst.DROPS:DanceBurst.SPARKS);j++){
                    var b=DanceBurst.bit(out,p.way(),k,j,SEED,s,big,drop);if(b==null)continue;
                    assertTrue(b.at().distanceTo(out)<3.6*big&&s<DanceBurst.LIFE,"blow "+k+"'s "+(drop?"drop":"spark")+" "+j+" at "+s);
                    assertTrue(b.at().subtract(out).dotProduct(p.way())>-.5*big,"flying out of them, not into them");
                }
            }
            assertTrue(seen&&thorn,"blow "+k+": its spikes, black thorns among them");
            // and a smaller burst back out where it went in
            Vec3d in=FEET.add(p.in());boolean back=false;
            for(int j=16;j<16+DanceBurst.BACK_SPIKES;j++){
                var sp=DanceBurst.spike(in,p.way().negate(),k,j,SEED,1.5f,big*DanceBurst.BACK);if(sp==null)continue;back=true;
                assertTrue(sp.b().subtract(sp.a()).normalize().dotProduct(p.way())<-Math.cos(Math.toRadians(42)),"back out the way it came");
            }
            assertTrue(back);
        }
        // it comes in over a frame, not at once, and does not jump
        var p=DanceBurst.pierce(3,SEED,W,H);Vec3d out=FEET.add(p.out());
        for(int j=0;j<DanceBurst.SPIKES;j++){
            float last=0;double reach=0;
            for(float s=0;s<DanceBurst.SPIKE_LIFE+.5f;s+=1/6f){
                var sp=DanceBurst.spike(out,p.way(),3,j,SEED,s,1);float f=sp==null?0:sp.fade();
                assertTrue(Math.abs(f-last)<.55f,"spike "+j+" jumps at "+s);last=f;
                if(sp!=null&&s<1.2f){double r=sp.b().distanceTo(sp.a());assertTrue(r>=reach-1e-9,"it shoots out, not back");reach=r;}
            }
        }
    }
    @Test void atTheEndEverythingBurstsOutOfTheirWholeBody(){
        Vec3d c=FEET.add(0,H*.55,0);double farthest=0;int thorns=0;double[] az=new double[DanceBurst.FINALE_SPIKES];
        for(int j=0;j<DanceBurst.FINALE_SPIKES;j++){
            var sp=DanceBurst.finaleSpike(c,j,SEED,2.4f);
            assertNotNull(sp,"spike "+j);
            farthest=Math.max(farthest,sp.b().distanceTo(c));
            if(sp.rgb()==DanceBurst.INK)thorns++;
            Vec3d d=sp.b().subtract(c);az[j]=Math.atan2(d.z,d.x);
            assertNull(DanceBurst.finaleSpike(c,j,SEED,DanceBurst.SPIKE_LIFE),"gone in its time");
        }
        assertTrue(farthest>3&&farthest<6.5,"bigger than a blow's: "+farthest);
        assertTrue(thorns>=4&&thorns<DanceBurst.FINALE_SPIKES/2,"black thorns among them: "+thorns);
        Arrays.sort(az);double gap=az[0]+2*Math.PI-az[az.length-1];
        for(int j=1;j<az.length;j++)gap=Math.max(gap,az[j]-az[j-1]);
        assertTrue(Math.toDegrees(gap)<100,"all round them: a gap of "+Math.toDegrees(gap));
        assertTrue(DanceBurst.FINALE_LIFE<(END+60-LAUNCH),"over before the cast stops drawing");
    }
    @Test void eachBlowCoversThemAndTheyStayCoveredWhileCutUp(){
        // 2.112.5: "이펙트가 커서 캐릭터가 덮일정도로"
        Vec3d mid=FEET.add(0,H*.55,0),cam=mid.add(-6,1.6,-8);
        Vec3d at=DanceBurst.coverAt(mid,cam);
        assertEquals(DanceBurst.COVER_FRONT,at.distanceTo(mid),1e-9);
        assertTrue(at.distanceTo(cam)<mid.distanceTo(cam)-.7,"over them, toward the camera (in front of their body)");
        assertTrue(DanceBurst.COVER>=Math.max(H*.55,H*.45)+.5,"its haze over their whole body, feet to head");
        // the blows ten a second, the haze of one still there as the next comes
        for(float t=blowAt(0)+.5f;t<blowAt(HITS-1);t+=.25f){
            float sum=0;for(int k=0;k<HITS;k++)sum+=DanceBurst.coverHaze(t-blowAt(k));
            assertTrue(sum>.6f,"covered at "+t+": "+sum);
        }
        assertEquals(0,DanceBurst.coverHaze(DanceBurst.COVER_LIFE),1e-9);
        // its spikes across the frame, reaching out some four blocks
        double far=0;
        for(int k=0;k<HITS-1;k++)for(int j=0;j<DanceBurst.COVER_SPIKES;j++){
            var sp=DanceBurst.coverSpike(at,cam,k,j,SEED,2,1);if(sp==null)continue;
            Vec3d d=sp.b().subtract(sp.a()).normalize();
            assertEquals(0,d.dotProduct(cam.subtract(at).normalize()),1e-9,"across the frame");
            far=Math.max(far,sp.b().distanceTo(at));
            assertTrue(sp.b().distanceTo(at)<DanceBurst.COVER_SPIKE+.4);
            assertEquals(j<DanceBurst.COVER_THORNS,sp.rgb()==DanceBurst.INK,"its first spikes black thorns");
        }
        assertTrue(far>3.5,"some four blocks: "+far);
        // and it thins to nothing in their own first person (the camera in their head)
        assertEquals(0,DanceBurst.near(mid,mid.add(0,.1,0)),1e-9);
    }
    @Test void itIsBiggerThan2_112_4sAndADeepPurple(){
        // half again as big ("이펙트 크게"), its spikes reaching some four blocks
        assertEquals(1.5,DanceBurst.SIZE,1e-9);
        double far=0;
        for(int k=0;k<HITS-1;k++){
            var p=DanceBurst.pierce(k,SEED,W,H);Vec3d out=FEET.add(p.out());
            for(int j=DanceBurst.THORNS;j<DanceBurst.SPIKES;j++){var sp=DanceBurst.spike(out,p.way(),k,j,SEED,2,DanceBurst.big(false));if(sp!=null)far=Math.max(far,sp.b().distanceTo(out));}
        }
        assertTrue(far>3.5,"its longest spikes some four blocks: "+far);
        // a deep purple ("보라색을 더 진하게"): purple (more blue than red, little green, violet to magenta), dark, still vivid
        for(int rgb:new int[]{DanceBurst.DEEP,DanceBurst.PURPLE,DanceBurst.GLOW,DanceBurst.SPARK}){
            int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
            assertTrue(b>r&&r>g&&g<r/2.0,"purple: "+Integer.toHexString(rgb));
            double hue=240+60.0*(r-g)/(b-g);
            assertTrue(hue>265&&hue<290,"violet to magenta: "+Integer.toHexString(rgb)+" "+hue);
            assertTrue(b>=0xb0,"vivid: "+Integer.toHexString(rgb));
        }
        for(int rgb:new int[]{DanceBurst.DEEP,DanceBurst.PURPLE,DanceBurst.GLOW})assertTrue(luma(rgb)<70,"deep: "+Integer.toHexString(rgb));
        assertTrue(luma(DanceBurst.DEEP)<luma(DanceBurst.PURPLE),"a deeper and a brighter purple");
        // the white only in thin cores (some spikes, none of the thorns), and the black
        int cores=0,spikes=0;
        var p=DanceBurst.pierce(5,SEED,W,H);
        for(int j=0;j<DanceBurst.SPIKES;j++){
            var sp=DanceBurst.spike(FEET.add(p.out()),p.way(),5,j,SEED,2,1);if(sp==null)continue;
            if(sp.rgb()==DanceBurst.INK){assertFalse(sp.core(),"no white down a thorn");continue;}
            spikes++;if(sp.core())cores++;
            assertTrue(sp.rgb()==DanceBurst.DEEP||sp.rgb()==DanceBurst.PURPLE,"its spikes purple, not white: "+Integer.toHexString(sp.rgb()));
        }
        assertTrue(cores>0&&cores<=spikes/2+1,"white cores down some: "+cores+" of "+spikes);
        assertEquals(0xffffff,DanceBurst.WHITE);
        assertTrue(luma(DanceBurst.INK)<12,"black: "+Integer.toHexString(DanceBurst.INK));
    }
    static double luma(int rgb){return .2126*(rgb>>16&255)+.7152*(rgb>>8&255)+.0722*(rgb&255);}
}
