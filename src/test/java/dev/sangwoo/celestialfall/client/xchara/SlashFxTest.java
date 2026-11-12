package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What X Chara's knife draws on a swing begun with their hack on (2.118.0, user request: "Q활성화 평타 이펙트를 전체적으로
 * 싹 다 갈아엎고 평타 소리에 맞춰서 새로 만들어줘 보라색 느낌으로 이펙트는 원래꺼보다는 약하고 적당하게"): the cut keeps to
 * the attack's sound (it begins as the sound does, is whole on the blow, cools and is eaten as the sound's tail falls
 * and is gone as the sound ends), lies along the knife's own path (its outer part), with its edge turned to the camera
 * and never twisting; the lunge's is a lance along the knife's line, out a little past its point; what a blow leaves on
 * whom it strikes (a star and rays, a swirl closing in and slowing, spikes, sparks) is small, soon gone and never jumps;
 * and all of it is less than 2.114.0's.
 */
class SlashFxTest {
    static final int[] HACK={HACK_SLASH,HACK_RISE,HACK_CHOP,HACK_LUNGE};
    static final float NEVER=Float.MAX_VALUE;
    static float blow(int hit){return blowAt(hit,0)/20f;}
    static double[] local(Vec3d p){return new double[]{p.z,p.y,-p.x};}

    @Test void theCutKeepsToTheSound(){
        for(int hit:HACK){
            int place=place(hit);float b=blow(hit),start=b-SlashFx.SOUND_BEFORE,from=SlashFx.FROM[place],to=SlashFx.TO[place];
            assertEquals(KnifeClient.soundAt(hit)/20f,start,1e-6);
            // it begins as the sound does (the knife let go), and the blow lands inside it
            assertTrue(from>=start&&from<=start+.06f,"hit "+hit+": the cut begins with the sound");
            assertTrue(from<b&&b<to,"hit "+hit+": the blow lands while it is cut");
            assertFalse(SlashFx.cutting(hit,from-.01f,NEVER));assertTrue(SlashFx.cutting(hit,from+.001f,NEVER));
            // whole and fresh on the blow
            assertEquals(0,SlashFx.cool(hit,b,NEVER),1e-6);assertEquals(0,SlashFx.eaten(hit,b,NEVER),1e-6);
            // then it cools and is eaten as the sound's tail falls, each without a step, the cooling ahead of the eating
            float cool=0,eaten=0;
            for(float t=b;t<=b+.5f;t+=1/240f){
                float c=SlashFx.cool(hit,t,NEVER),e=SlashFx.eaten(hit,t,NEVER);
                assertTrue(c>=cool-1e-6&&e>=eaten-1e-6,"hit "+hit+" at "+t);
                assertTrue(c-cool<.04f&&e-eaten<.04f,"hit "+hit+": no step at "+t);
                assertTrue(c>=e-1e-6,"hit "+hit+": it has cooled before it is eaten");
                cool=c;eaten=e;
            }
            assertEquals(1,cool,1e-6);assertEquals(1,eaten,1e-6);
            // gone as the sound ends
            float gone=b+SlashFx.EAT1;
            assertFalse(SlashFx.cutting(hit,gone,NEVER));assertTrue(SlashFx.cutting(hit,gone-.01f,NEVER));
            assertTrue(gone<=start+SlashFx.SOUND+1e-4&&gone>=start+SlashFx.SOUND-.08f,"hit "+hit+": gone as the sound ends");
            assertTrue(gone<KnifeCombo.LIFE);
        }
        // a plain swing has no place among them only by its kind: the same times by its place
        assertEquals(SlashFx.cool(HACK_SLASH,.3f,NEVER),SlashFx.cool(SLASH,.3f,NEVER));
    }
    @Test void aSwingStoppedShortLeavesWhatWasCutAndLetsItGoFromThere(){
        for(int hit:HACK){
            int place=place(hit);float from=SlashFx.FROM[place],b=blow(hit);
            // stopped before the knife was let go: nothing
            assertFalse(SlashFx.cutting(hit,from+.05f,from-.01f));
            // stopped part of the way: it goes from where it stopped
            float until=(from+b)/2;
            assertEquals(until,SlashFx.mark(hit,until),1e-6);assertEquals(b,SlashFx.mark(hit,NEVER),1e-6);
            assertTrue(SlashFx.cutting(hit,until+.1f,until));
            assertFalse(SlashFx.cutting(hit,until+SlashFx.EAT1,until));
            assertEquals(1,SlashFx.eaten(hit,until+SlashFx.EAT1,until),1e-6);
        }
    }
    @Test void theCutLiesAlongTheKnifesOwnOuterPart(){
        assertTrue(SlashFx.INNER>KnifeCombo.HEEL+1&&SlashFx.OUTER<=KnifeCombo.TIP+.1&&SlashFx.INNER<SlashFx.OUTER,"the outer part of the blade");
        for(int hit:new int[]{HACK_SLASH,HACK_RISE,HACK_CHOP}){
            int place=place(hit);float from=SlashFx.FROM[place],to=SlashFx.TO[place];
            var sw=KnifeCombo.swept(Vec3d.ZERO,0,hit,from,to,SlashFx.INNER,SlashFx.OUTER);
            for(int i=0;i<sw.n();i++){
                float ts=from+(to-from)*i/(sw.n()-1);var hb=KnifeCombo.blade(Vec3d.ZERO,0,hit,ts);
                assertTrue(sw.outer()[i].distanceTo(hb[0].add(hb[1].multiply(SlashFx.OUTER)))<1e-6);
                assertTrue(sw.inner()[i].distanceTo(hb[0].add(hb[1].multiply(SlashFx.INNER)))<1e-6);
                if(i>0)assertTrue(sw.along()[i]>=sw.along()[i-1],"it runs on from its tail to its head");
            }
            assertEquals(0,sw.along()[0],1e-6);assertEquals(1,sw.along()[sw.n()-1],1e-5);
            // a real sweep: its point travels some blocks
            double len=0;for(int i=1;i<sw.n();i++)len+=sw.outer()[i].distanceTo(sw.outer()[i-1]);
            assertTrue(len>4,"hit "+hit+": the knife's point sweeps "+len+" blocks");
        }
    }
    @Test void aCutsEdgeIsTurnedToTheCameraAndNeverTwists(){
        // from right behind its user a cut's band is edge-on (a hairline): its edge is drawn once more as a strip turned to
        // the camera, as wide from anywhere, to one side of the knife's point's path all along it
        Vec3d[] cameras={new Vec3d(0,3.2,-5.5),new Vec3d(6.5,1.8,2.4),new Vec3d(-4,3.6,-5),new Vec3d(.3,2.2,9),new Vec3d(0,9,1.5)};
        for(int hit:new int[]{HACK_SLASH,HACK_RISE,HACK_CHOP}){
            int place=place(hit);
            var sw=KnifeCombo.swept(Vec3d.ZERO,0,hit,SlashFx.FROM[place],SlashFx.TO[place],SlashFx.INNER,SlashFx.OUTER);
            for(Vec3d camera:cameras){
                var e=KnifeCombo.edge(sw,camera,SlashFx.EDGE);
                assertEquals(sw.n(),e.n());
                Vec3d before=null;
                for(int i=0;i<e.n();i++){
                    assertEquals(0,e.outer()[i].distanceTo(sw.outer()[i]),1e-9,"along the knife's point's path");
                    Vec3d side=e.inner()[i].subtract(e.outer()[i]);
                    assertEquals(SlashFx.EDGE,side.length(),1e-6,"as wide all along it");
                    assertEquals(0,side.normalize().dotProduct(camera.subtract(e.outer()[i]).normalize()),1e-6,"turned to the camera");
                    if(before!=null)assertTrue(side.dotProduct(before)>0,"hit "+hit+": its edge twists at "+i+" seen from "+camera);
                    before=side;
                }
                assertArrayEquals(sw.along(),e.along());
            }
        }
        // (the level cut's band seen from right behind is edge-on about its blow: that is what the strip is for)
        var sw=KnifeCombo.swept(Vec3d.ZERO,0,HACK_SLASH,SlashFx.FROM[1],SlashFx.TO[1],SlashFx.INNER,SlashFx.OUTER);
        Vec3d camera=new Vec3d(0,1.4,-5.5);double thinnest=9;
        for(int i=1;i+1<sw.n();i++){
            Vec3d along=sw.outer()[i+1].subtract(sw.outer()[i-1]).normalize(),across=sw.inner()[i].subtract(sw.outer()[i]).normalize();
            Vec3d normal=along.crossProduct(across).normalize(),view=camera.subtract(sw.outer()[i]).normalize();
            thinnest=Math.min(thinnest,Math.abs(normal.dotProduct(view)));
        }
        assertTrue(thinnest<.25,"the band seen from right behind is nearly edge-on somewhere: "+thinnest);
    }
    @Test void theLungesIsALanceAlongTheKnifesLineOutALittlePastItsPoint(){
        int hit=HACK_LUNGE;float b=blow(hit);
        var tip=KnifeCombo.blade(Vec3d.ZERO,0,hit,b);Vec3d way=tip[1],point=tip[0].add(way.multiply(KnifeCombo.TIP));
        // before it lands it reaches to the knife's point; as it lands, on past it; no step between
        assertEquals(0,SlashFx.lancePast(b-.05f),1e-6);assertEquals(SlashFx.LANCE_PAST,SlashFx.lancePast(b+.04f),1e-6);
        double before=0;
        for(float t=b-.06f;t<b+.06f;t+=1/240f){double p=SlashFx.lancePast(t);assertTrue(p>=before-1e-9&&p-before<.2,"no step at "+t);before=p;}
        var ends=SlashFx.lance(Vec3d.ZERO,0,hit,SlashFx.TO[4]);
        Vec3d along=ends[1].subtract(ends[0]);double len=along.length();
        assertTrue(len>2&&len<5,"a lance "+len+" blocks long");
        assertTrue(along.normalize().dotProduct(way)>Math.cos(Math.toRadians(14)),"along the knife's line");
        assertTrue(along.normalize().dotProduct(new Vec3d(0,0,1))>Math.cos(Math.toRadians(16)),"ahead of them");
        // its head: past the point, and not beyond what the lunge can strike by more than a little
        double[] head=local(ends[1]),knife=local(point);
        assertTrue(head[0]>knife[0],"past the knife's point");
        assertTrue(head[0]<=reach(hit,0)+.9,"not far beyond the lunge's reach: "+head[0]);
        assertTrue(Math.abs(head[2])<halfWidth(hit,0)&&head[1]>.6&&head[1]<2,"on the middle line at a body's height: "+head[2]+", "+head[1]);
        // its tail stays where the knife was as it was let go
        assertEquals(0,ends[0].distanceTo(SlashFx.lance(Vec3d.ZERO,0,hit,b)[0]),1e-9);
        // drawn as a band turned to the camera, as wide all along
        Vec3d camera=new Vec3d(-3,2.6,-4.5);
        var sw=SlashFx.strip(ends[0],ends[1],SlashFx.LANCE_WIDE,camera);
        for(int i=0;i<sw.n();i++){
            Vec3d side=sw.outer()[i].subtract(sw.inner()[i]);
            assertEquals(SlashFx.LANCE_WIDE,side.length(),1e-9);
            assertEquals(0,side.normalize().dotProduct(along.normalize()),1e-9,"square to its line");
            if(i>0)assertTrue(sw.along()[i]>sw.along()[i-1]);
        }
        assertEquals(0,sw.along()[0],1e-9);assertEquals(1,sw.along()[sw.n()-1],1e-6);
        // and the one ring round its line: out and gone in a fifth of a second
        assertEquals(0,SlashFx.ringShown(-.01f));assertEquals(0,SlashFx.ringShown(SlashFx.RING_LIFE));
        double r=0;float shown=0,most=0;
        for(float s=0;s<SlashFx.RING_LIFE;s+=1/240f){
            double rr=SlashFx.ringRadius(s);float a=SlashFx.ringShown(s);
            assertTrue(rr>=r-1e-9&&rr<.8,"it runs out, under a block");assertTrue(Math.abs(a-shown)<.3f,"no step at "+s);
            r=rr;shown=a;most=Math.max(most,a);
        }
        assertTrue(most>.9f);
    }
    @Test void whatABlowLeavesIsSmallAndSoonGone(){
        Vec3d mid=new Vec3d(0,1,3),camera=new Vec3d(0,2.5,-3.5);
        Vec3d at=SlashFx.burstAt(mid,camera);
        assertEquals(SlashFx.FRONT,at.distanceTo(mid),1e-9,"in front of their body, toward the camera");
        assertTrue(at.distanceTo(camera)<mid.distanceTo(camera));
        // (a camera right on them: never past it)
        assertTrue(SlashFx.burstAt(mid,mid.add(0,0,.2)).distanceTo(mid)<=.1+1e-9);
        var f=GlitchFx.facing(camera,at);
        for(int hit:new int[]{HACK_SLASH,HACK_LUNGE}){
            double big=SlashFx.big(hit);int n=last(hit)?SlashFx.SPIKES_LAST:SlashFx.SPIKES;
            assertEquals(last(hit)?SlashFx.LAST:1,big);
            for(int seed=1;seed<40;seed+=7){
                for(int j=0;j<SlashFx.RAYS;j++)assertTrue(SlashFx.ray(j,seed,big)[1]<=1.05*big+1e-9,"a ray within a block or so");
                for(float s=0;s<.5f;s+=1/240f){
                    var w=SlashFx.swirl(s,seed,big);
                    assertTrue(w[0]<=.62*big+1e-9&&w[0]>=.4*big,"the swirl about their body, never a block out: "+w[0]);
                    for(int j=0;j<n;j++){
                        var sp=SlashFx.spike(at,f,j,n,seed,s,big);
                        if(s>=SlashFx.SPIKE_LIFE){assertNull(sp);continue;}
                        assertTrue(sp.b().distanceTo(at)<=1.7*big+1e-9,"a spike within two blocks: "+sp.b().distanceTo(at));
                        assertTrue(sp.width()<=.16*big&&sp.fade()>=0&&sp.fade()<=1);
                    }
                    for(int j=0;j<SlashFx.SPARKS;j++){
                        var b=SlashFx.spark(at,new Vec3d(0,0,1),j,seed,s,big);
                        if(s>=SlashFx.SPARK_LIFE){assertNull(b);continue;}
                        if(b!=null)assertTrue(b[0].distanceTo(at)<2.2*big&&b[2].x>=0&&b[2].x<=1);
                    }
                }
                // everything of it is gone with the swirl, the longest, itself gone before the sound's tail is
                assertEquals(0,SlashFx.swirl(SlashFx.SWIRL_LIFE,seed,big)[4],1e-9);
            }
        }
        assertTrue(SlashFx.STAR_LIFE<SlashFx.SWIRL_LIFE&&SlashFx.RAY_LIFE<SlashFx.SWIRL_LIFE&&SlashFx.SPIKE_LIFE<SlashFx.SWIRL_LIFE&&SlashFx.SPARK_LIFE<SlashFx.SWIRL_LIFE);
        assertTrue(SlashFx.SWIRL_LIFE<=SlashFx.EAT1,"gone with the cut, as the sound ends");
        // few of each
        assertTrue(SlashFx.RAYS<=8&&SlashFx.SPIKES<=4&&SlashFx.SPIKES_LAST<=6&&SlashFx.SPARKS<=6);
    }
    @Test void theSwirlClosesInAndSlowsAndNothingOfABurstJumps(){
        Vec3d at=new Vec3d(0,1,3),camera=new Vec3d(2,2.5,-3.5);var f=GlitchFx.facing(camera,at);
        for(int seed=3;seed<30;seed+=9){
            double[] w0=SlashFx.swirl(0,seed,1);double radius=w0[0],turned=w0[1],rate=9,shown=0;
            assertEquals(0,w0[4],1e-9,"it comes in, not at once");
            for(float s=1/240f;s<SlashFx.SWIRL_LIFE;s+=1/240f){
                var w=SlashFx.swirl(s,seed,1);
                assertTrue(w[0]<=radius+1e-9,"it closes in");
                double step=Math.abs(w[1]-turned);
                assertTrue(step<=rate+1e-9,"it slows as the tail falls at "+s);
                assertTrue(step<Math.toRadians(8),"no jump of its turning at "+s);
                assertTrue(Math.abs(w[4]-shown)<.25,"no step of its showing at "+s);
                assertTrue(w[2]>0&&w[3]>0);
                radius=w[0];turned=w[1];rate=step;shown=w[4];
            }
            // it turns about once round in all, and has nearly stopped at the end
            assertEquals(Math.PI*2*SlashFx.TURNS,Math.abs(turned-w0[1]),.2);
            assertTrue(rate<Math.toRadians(.3));
            // a spike: out at once (within three frames), then only fading
            double far=0;float fade=0;boolean fading=false;
            for(float s=0;s<SlashFx.SPIKE_LIFE;s+=1/240f){
                var sp=SlashFx.spike(at,f,1,SlashFx.SPIKES,seed,s,1);double len=sp.b().distanceTo(sp.a());
                assertTrue(len>=far-1e-9,"it only goes out");assertTrue(len-far<.2,"no jump at "+s);
                if(sp.fade()<fade-1e-6)fading=true;else assertFalse(fading&&sp.fade()>fade+1e-6,"once it fades it only fades");
                far=len;fade=sp.fade();
            }
            assertTrue(far>.8,"a spike about a block long: "+far);
            assertEquals(far,SlashFx.spike(at,f,1,SlashFx.SPIKES,seed,.06f,1).b().distanceTo(SlashFx.spike(at,f,1,SlashFx.SPIKES,seed,.06f,1).a()),1e-9,"all out by 0.05 s");
            // a spark flies out and falls
            var early=SlashFx.spark(at,new Vec3d(0,0,1),2,seed,.02f,1);
            float life=0;for(float s=0;s<.4f;s+=1/240f)if(SlashFx.spark(at,new Vec3d(0,0,1),2,seed,s,1)!=null)life=s;
            var late=SlashFx.spark(at,new Vec3d(0,0,1),2,seed,life,1);
            assertTrue(late[0].distanceTo(at)>early[0].distanceTo(at));assertTrue(late[1].y<early[1].y,"it falls");
            assertTrue(late[2].x<.1,"gone as it ends");
        }
        // the crescents' shape: from nothing, widest near the head, closed at the head
        assertEquals(0,SlashFx.shape(0),1e-9);assertEquals(0,SlashFx.shape(1),1e-6);
        double widest=0,where=0;for(double u=0;u<=1;u+=.01){double v=SlashFx.shape(u);if(v>widest){widest=v;where=u;}}
        assertTrue(where>.6&&where<.9&&widest>.7);
    }
    @Test void itIsLessThanWhatTheHackDrewBefore(){
        // 2.114.0's cuts were bands 2 blocks across with an edge strip 0.55 wide (the finisher's a quarter more), each
        // blow leaving a flashed box, six torn bars, 14 pixels and digits, the body trailing copies of itself
        assertTrue(SlashFx.OUTER-SlashFx.INNER<=1,"a band half as wide");
        assertTrue(SlashFx.EDGE<.4,"a thinner edge");
        assertEquals(0,new KnifeCombo().afterimages(.2f),"no copies of the body");
        assertTrue(SlashFx.RAYS+SlashFx.SPIKES+SlashFx.SPARKS+2<=20,"a few parts to a burst");
    }
}
