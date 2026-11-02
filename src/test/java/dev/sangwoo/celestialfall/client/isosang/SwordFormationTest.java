package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's H as drawn (2.82.0): the body's motion (from the stance and back to it over the formation's whole length,
 * facing the front, no joint jumping or flipping between frames, never a foot in the ground, her feet together up there as
 * she stands, the leap and the hop drawn over where she is); the formation's swords (the same for every client, round its
 * empty middle out to its radius, none in the middle (2.82.3), as close together as in 2.82.2, each showing in the sky
 * before it falls and striking as the server's rain does); the plain sword's shape; the ink mountains and the clouds round
 * it (2.82.1); and her camera drawn back for it and in again.
 */
class SwordFormationTest {
    static final Anim CLIP=SwordFormation.CLIP;

    @Test void itRunsAsLongAsTheServerSaysFromTheStanceBackToIt(){
        var stance=IsosangSlash.stance();
        var first=CLIP.frame(0);var last=CLIP.frame(CLIP.length());
        for(int j=0;j<6;j++){assertTrue(turn(first,stance,j)<.5,"H starts in the stance");assertTrue(turn(last,stance,j)<.5,"H ends in the stance");}
        assertEquals(0,last.fw(),1e-6);assertEquals(0,last.up(),1e-6);assertEquals(0,MathHelper.wrapDegrees(last.yaw()),1e-3);
        assertEquals(IsosangRules.LENGTH[IsosangRules.FORMATION]/20f,CLIP.length(),1e-6);
        assertSame(CLIP,IsosangSlash.hit(IsosangRules.FORMATION),"its swing is drawn with its own clip");
        assertTrue(IsosangClient.life(IsosangRules.FORMATION)>CLIP.length(),"drawn for its whole length");
    }
    @Test void noJointJumpsOrFlips(){
        double h=1/960.0;
        // (the leap, the rain, the hop up; and the drop: the moves; between them she stands)
        for(double[] span:new double[][]{{0,2.4},{IsosangRules.H_END-.2,CLIP.length()}})
            for(double t=Math.max(h,span[0]);t+2*h<=span[1];t+=h){
                var p0=CLIP.frame((float)(t-h));var p1=CLIP.frame((float)t);var p2=CLIP.frame((float)(t+h));var p3=CLIP.frame((float)(t+2*h));
                for(int j=0;j<6;j++){
                    double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                    assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
                }
            }
        for(double t=0;t+1/60.0<=CLIP.length();t+=1/60.0)
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<125,"joint "+j+" flips at "+t);
        // standing up there she moves only a little
        for(double t=2;t+1/60.0<IsosangRules.H_END;t+=1/60.0)
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<1,"joint "+j+" moves fast as she stands, at "+t);
    }
    @Test void itFacesTheFront(){
        for(double t=0;t<=CLIP.length();t+=1/120.0){
            float yaw=MathHelper.wrapDegrees(CLIP.frame((float)t).yaw());
            assertTrue(Math.abs(yaw)<=32,"H turns "+yaw+" deg away from the front at "+t);
        }
    }
    @Test void herFeetNeverGoIntoWhatSheStandsOnAndAreOnItAsSheStands(){
        for(float t=0;t<=CLIP.length();t+=t<2.5f||t>IsosangRules.H_END-.5f?1/120f:1/4f){
            double[] f=IsosangSlash.feet(CLIP.frame(t));
            assertTrue(f[0]>-.02&&f[1]>-.02,"a foot in the ground at "+t+": "+f[0]+" "+f[1]);
            // on the ground before the leap and after she lands, on what she stands on up there, down again at the end
            boolean standing=t<.16f||t>.9f&&t<IsosangRules.H_DROP||t>IsosangRules.H_DOWN;
            if(standing)assertTrue(Math.min(f[0],f[1])<.05,"no foot down at "+t+": "+f[0]+" "+f[1]);
        }
        // her feet together up there
        var on=CLIP.frame(10);var p=dev.sangwoo.celestialfall.client.kageno.R6.poseOf(on);
        assertTrue(Math.abs(p.rLeg.rt()-p.lLeg.rt())<.12,"her feet together up there");
        // and the leap is drawn: up as the plan has her over where she is (IsosangClient.lift)
        assertEquals(IsosangRules.H_LEAP_HEIGHT,IsosangClient.lift(IsosangRules.planned(IsosangRules.H_PEAK),0),1e-9);
        assertEquals(0,IsosangClient.lift(IsosangRules.planned(10),IsosangRules.raised(10)),1e-9,"up there she is where she is drawn");
        assertEquals(.3,IsosangClient.lift(IsosangRules.planned(1.3),IsosangRules.planned(1.3)-.3),1e-9,"what the position lags behind is made up");
    }
    @Test void theSwordsLieRoundTheMiddleTheSameForEveryone(){
        UUID id=UUID.fromString("6b0e5a1c-1d2e-4f00-9a3b-0c1d2e3f4a5b");
        var a=SwordFormation.layout(id);var b=SwordFormation.layout(new UUID(id.getMostSignificantBits(),id.getLeastSignificantBits()));
        assertArrayEquals(a,b,"the same swords from the same id");
        // (2.82.3: three rings and none in the middle, the formation 10 blocks again)
        assertEquals(10,IsosangRules.FORMATION_RADIUS,1e-9);
        assertEquals(48,a.length,"three rings, none in the middle");
        Vec3d centre=new Vec3d(5,64,-3);
        // where the server reckons they lie (IsosangRules.formationSwords), and the one she stands on (2.82.4: where she
        // leapt, on the outer ring right behind the middle) upright, struck first, its pommel where the server holds her
        // and her own client takes her (IsosangClient.spot, IsosangRules.raised)
        int s0=IsosangRules.formationSeed(id);var places=IsosangRules.formationSwords(s0);int stand=IsosangRules.standSword();
        for(int i=0;i<a.length;i++){assertEquals(places[i][0],a[i].fw(),1e-12);assertEquals(places[i][1],a[i].rt(),1e-12);}
        for(int i=0;i<a.length;i++)if(i!=stand)assertTrue(a[i].tilt()>1,"the others lean a little");
        var on=a[stand];assertEquals(0,on.tilt(),1e-12,"the one she stands on upright");assertEquals(IsosangRules.FORMATION_STAND,on.height(),1e-12);
        assertEquals(IsosangRules.H_PLANT,on.plant(),1e-6,"it strikes first, under her as she hangs in the air");
        for(var b2:a)if(b2!=on)assertTrue(b2.plant()>on.plant(),"the rest after it");
        assertTrue(on.plant()<IsosangRules.H_DIVE,"struck before she comes down onto it");
        for(float yaw:new float[]{0,30,-135}){
            Vec3d pommel=SwordFormation.planted(centre,yaw,on),spot=IsosangClient.spot(new IsosangClient.Formation(centre,yaw));
            assertEquals(0,pommel.distanceTo(spot.add(0,IsosangRules.raised(10),0)),1e-9,"she stands on its pommel");
            // behind the formation's middle, the way she faced: the formation lies ahead of her, she on its outer ring
            Vec3d ahead=Vec3d.fromPolar(0,yaw);
            assertEquals(0,centre.subtract(spot).subtract(ahead.multiply(IsosangRules.FORMATION_STAND_BACK)).length(),1e-9,"its middle ahead of her");
        }
        var seen=new HashSet<String>();
        double near=0;
        for(int i=0;i<a.length;i++){
            var s=a[i];
            double r=Math.hypot(s.fw(),s.rt());
            // (2.82.3: no sword within some 5 blocks of the middle, under her or round her; the rest out to the formation's edge)
            assertTrue(r>5&&r<IsosangRules.FORMATION_RADIUS-.5,"out from the empty middle to the formation's edge: "+r);
            double nearest=Double.MAX_VALUE;
            for(int j=0;j<a.length;j++)if(j!=i)nearest=Math.min(nearest,Math.hypot(s.fw()-a[j].fw(),s.rt()-a[j].rt()));
            assertTrue(nearest>1,"two swords too close: "+nearest);
            near+=nearest/a.length;
            if(i!=stand)assertTrue(s.plant()>=IsosangRules.H_RAIN0-1e-6&&s.plant()<=IsosangRules.H_RAIN1+1e-6,"it strikes as the swords rain: "+s.plant());
            assertTrue(s.shows()+.08f<=s.plant()-SwordFormation.FALL+1e-4,"it shows in the sky before it falls");
            assertTrue(s.shows()>=IsosangRules.H_SKY-1e-6);
            assertTrue(s.sky()>=6&&s.sky()<=13,"high in the sky: "+s.sky());
            assertTrue(s.tilt()<15,"near upright: "+s.tilt());
            Vec3d pommel=SwordFormation.planted(centre,0,s),point=pommel.add(SwordFormation.down(s).multiply(SwordFormation.S_LEN));
            assertTrue(pommel.y-centre.y>1.1&&pommel.y-centre.y<1.9,"its pommel over the ground: "+(pommel.y-centre.y));
            assertTrue(point.y<centre.y,"its point in the ground");
            seen.add(Math.round(s.fw()*10)+","+Math.round(s.rt()*10));
            // falling, faster and faster, from where it hangs to where it strikes
            assertEquals(s.sky(),SwordFormation.above(s,s.plant()-SwordFormation.FALL),.07);
            assertEquals(0,SwordFormation.above(s,s.plant()),1e-9);
            double prev=Double.MAX_VALUE,prevStep=0;
            for(float t=s.plant()-SwordFormation.FALL;t<=s.plant();t+=.01f){double y=SwordFormation.above(s,t);assertTrue(y<=prev+1e-9);if(prev!=Double.MAX_VALUE){assertTrue(prev-y>=prevStep-1e-3);prevStep=prev-y;}prev=y;}
        }
        assertEquals(a.length,seen.size(),"no two in one place");
        // as close together as in 2.82.2 (its rings 4.3, 5.85, 7.3 of 9, 12, 15: 1.68 blocks to the nearest on the mean of
        // 200 formations; these rings 1.74)
        assertTrue(near>1.55&&near<1.95,"as close together as before: "+near);
        assertNotEquals(a[5].fw(),SwordFormation.layout(UUID.randomUUID())[5].fw(),"another formation, other places");
    }
    @Test void thePlainSwordIsWhole(){
        // every face turned out from the sword's middle; its pommel at its top, its point at its far end
        double top=1e9,bottom=-1e9;
        for(var f:SwordFormation.FACES){
            Vec3d c=Vec3d.ZERO;for(var v:f.v())c=c.add(v);c=c.multiply(1.0/f.v().length);
            for(var v:f.v()){top=Math.min(top,v.y);bottom=Math.max(bottom,v.y);}
            // (out from its axis; the guard's faces out from the guard's own middle, a knob's or a gem's from its own)
            final Vec3d cc=c;
            Vec3d axis=switch(f.part()){
                case GUARD->new Vec3d(0,(SwordFormation.S_GRIP+SwordFormation.S_GUARD)/2,0);
                case KNOB->SwordFormation.KNOBS.stream().min(java.util.Comparator.comparingDouble(k->k.squaredDistanceTo(cc))).orElseThrow();
                case GEM->SwordFormation.GEMS.stream().min(java.util.Comparator.comparingDouble(k->k.squaredDistanceTo(cc))).orElseThrow();
                default->new Vec3d(0,MathHelper.clamp(c.y,.02,SwordFormation.S_LEN-.02),0);
            },out=c.subtract(axis);
            if(out.lengthSquared()>1e-6)assertTrue(f.n().dotProduct(out)>-1e-6,"a face turned inward at "+c);
            assertEquals(1,f.n().length(),1e-9);
        }
        assertEquals(0,top,1e-9);assertEquals(SwordFormation.S_LEN,bottom,1e-9);
        assertTrue(SwordFormation.EDGES.stream().anyMatch(SwordFormation.Edge::outline));
        // (2.82.1) its finer parts: a fuller down the blade on both sides, a knob at each end of the guard, a gem on each face of its guard and pommel, a wound grip
        for(var part:SwordFormation.Part.values())assertTrue(SwordFormation.FACES.stream().anyMatch(f->f.part()==part),"no "+part);
        assertEquals(2,SwordFormation.KNOBS.size());assertEquals(4,SwordFormation.GEMS.size());
        for(var k:SwordFormation.KNOBS)assertTrue(Math.abs(k.x)>SwordFormation.S_GUARD_W*SwordFormation.THIN,"a knob past the guard's end");
        for(var f:SwordFormation.FACES)if(f.part()==SwordFormation.Part.FULLER)for(var v:f.v()){
            assertTrue(v.y>SwordFormation.S_GUARD&&v.y<SwordFormation.S_LEN-SwordFormation.S_POINT,"the fuller on the blade");
            // (as thinned, 2.82.4: across and through alike, so the fuller still lies over the blade's face)
            double th=SwordFormation.THIN;
            assertTrue(Math.abs(v.z)>=th*SwordFormation.bladeRidge(v.y)*(1-Math.abs(v.x)/(th*SwordFormation.bladeWidth(v.y)))-1e-9,"the fuller over the blade's face");
        }
        // its edge runs from the guard to the point (2.82.4: thinner, THIN across)
        assertEquals(SwordFormation.S_W*SwordFormation.THIN,SwordFormation.edge(SwordFormation.S_GUARD).x,1e-9);
        assertEquals(0,SwordFormation.edge(SwordFormation.S_LEN).x,1e-9);
        // (2.82.4, "검 두께를 지금보다 더 작아지게") thinner: no wider than THIN of the shape built, as long as it was
        double across=0,through=0;
        for(var f:SwordFormation.FACES)for(var v:f.v()){across=Math.max(across,Math.abs(v.x));through=Math.max(through,Math.abs(v.z));}
        assertTrue(SwordFormation.THIN<.7,"thinner");
        assertTrue(across<=(SwordFormation.S_GUARD_W+SwordFormation.S_KNOB*1.7)*SwordFormation.THIN+1e-9,"its guard no wider than THIN of it: "+across);
        assertTrue(through<=.07*SwordFormation.THIN,"no thicker than THIN of it: "+through);
    }
    @Test void theMountainsAreBroadMassesOneBeforeAnotherRimmedFromTheirSummits(){
        UUID id=UUID.fromString("6b0e5a1c-1d2e-4f00-9a3b-0c1d2e3f4a5b");
        float yaw=40;var all=SwordFormation.mountains(id,yaw);
        assertEquals(6,all.length);
        assertSame(all,SwordFormation.mountains(new UUID(id.getMostSignificantBits(),id.getLeastSignificantBits()),yaw),"the same mountains for the same formation");
        double behind=SwordFormation.behind(yaw);
        for(var mt:all){
            assertTrue(mt.radius()>IsosangRules.FORMATION_RADIUS+1,"outside the formation");
            // (2.82.4) none behind her, where her camera looks at her from: no mass reaches within 25 degrees of it
            for(var p:mt.peaks())for(double x:new double[]{p.xs()[0],p.xs()[p.xs().length-1]}){
                double a=mt.az()+Math.atan2(x,mt.radius()-p.front());
                assertTrue(Math.abs(MathHelper.wrapDegrees(Math.toDegrees(a-behind)))>25,"a mountain behind her: "+Math.toDegrees(a-behind));
            }
            assertTrue(mt.peaks().length>=3&&mt.peaks().length<=4);
            double front=-1;
            for(var p:mt.peaks()){
                assertTrue(p.front()>front,"each mass before the last");front=p.front();
                assertTrue(p.height()<=mt.peaks()[0].height()+1e-9,"the back one the tallest");
                // its outline: from the ground up to its summit, the highest point, and down again; x only growing along it
                assertEquals(0,p.ys()[0],1e-9);assertEquals(0,p.ys()[p.ys().length-1],1e-9);
                for(int i=0;i+1<p.xs().length;i++)assertTrue(p.xs()[i+1]>p.xs()[i],"x grows along the outline");
                for(int i=0;i<p.ys().length;i++)if(i!=p.top())assertTrue(p.ys()[i]<p.height(),"the summit the highest");
                // round at its summit: the outline falls slowly just under it, steeper further down
                double near=p.height()-p.at(p.xs()[p.top()]+.05*(p.xs()[p.xs().length-1]-p.xs()[p.top()]));
                assertTrue(near<.1*p.height(),"a round summit: "+near);
                // a broad mass, not a needle
                double wide=p.xs()[p.xs().length-1]-p.xs()[0];
                if(p==mt.peaks()[0])assertTrue(wide>1.15*p.height()&&wide<2.4*p.height(),"the tall one broad: "+wide+" for "+p.height());
                assertTrue(p.rimL()>.3&&p.rimL()<=1&&p.rimR()>.3&&p.rimR()<=1);
                // its ridges run down inside it from the top
                assertTrue(p.ridges().length>=2,"a ridge from the summit down each flank");
                for(int r=0;r<p.ridges().length;r++){
                    var ridge=p.ridges()[r];
                    assertTrue(ridge[0][1]>ridge[ridge.length-1][1],"a ridge runs down");
                    for(var q:ridge)assertTrue(q[1]>=0&&q[1]<=p.at(q[0])+1e-9,"a ridge inside the mass");
                }
            }
            assertTrue(mt.peaks()[0].height()>4&&mt.peaks()[0].height()<7,"as tall as the video's (the clouds among their summits)");
        }
    }
    @Test void cloudsCurlOverTheMountainsAndWispsOfMistDriftLow(){
        UUID id=UUID.fromString("6b0e5a1c-1d2e-4f00-9a3b-0c1d2e3f4a5b");
        float yaw=-70;var all=SwordFormation.clouds(id,yaw);
        int high=0,low=0;double behind=SwordFormation.behind(yaw);
        for(var cl:all){
            // (2.82.4) none of the clouds behind her, where her camera looks at her from (the mist low under it is), even as they drift
            if(!cl.mist())for(float t:new float[]{0,20,41})
                assertTrue(Math.abs(MathHelper.wrapDegrees(Math.toDegrees(cl.az()+cl.drift()*t-behind)))>40,"a cloud behind her at "+t);
            if(!cl.mist()){
                assertTrue(cl.turns()>.6,"its curl winds round");
                if(cl.radius()>IsosangRules.FORMATION_RADIUS+4){high++;assertTrue(cl.up()>4.5,"among and over the summits");assertTrue(cl.thick()>1.1,"a full band");}
            }else{low++;assertTrue(cl.up()<1.1&&cl.radius()<IsosangRules.FORMATION_RADIUS+1,"a wisp low over the formation");}
            // its band runs on without a break, from the inner end of its curl out to its tail, thinning to nothing there
            var path=SwordFormation.cloudPath(cl,10,1);
            assertEquals(SwordFormation.curlPoints(cl)+(int)Math.max(8,(int)(cl.length()/.22))+1,path.size());
            for(int i=0;i+1<path.size();i++){
                double d=Math.hypot(path.get(i+1)[0]-path.get(i)[0],path.get(i+1)[1]-path.get(i)[1]);
                assertTrue(d<.35,"a gap in a cloud's band: "+d);
            }
            assertEquals(0,path.get(path.size()-1)[2],1e-9,"its tail thins to nothing");
            assertTrue(path.stream().mapToDouble(q->q[2]).max().orElse(0)>.99,"full in its body");
            // it drifts and its wave runs along it as a function of the clock: no jump from one frame to the next
            var a=SwordFormation.cloudPath(cl,10,1);var b=SwordFormation.cloudPath(cl,10+1/60f,1);
            for(int i=0;i<a.size();i++)assertEquals(a.get(i)[1],b.get(i)[1],.02,"a cloud jumps");
        }
        assertEquals(6,high,"six over and behind the mountains");assertEquals(7,low,"seven wisps of mist");
        // coming in, it flows out from where its curl meets its band
        var cl=all[0];var in=SwordFormation.cloudPath(cl,cl.in()+.1f,.2f);
        assertTrue(in.get(in.size()-1)[2]==0&&in.stream().anyMatch(q->q[2]>.3),"flowing out as it comes in");
    }
    @Test void theInkCoversTheGroundGathersIntoTheMiddleThenSwirlsOutAsAWhirlpoolAndSpreadsAway(){
        // (2.82.5, "처음에 잉크가 중앙에 모였다가 점점 확장하고 검이 박히게": 2.82.4's pool opened under her and grew; the
        // video's ink darkens the ground all round, gathers into the middle, a spike bursts there, and it swirls out again.
        // 2.82.6, "잉크 모였다가 퍼지는게 너무 부자연스러운데 이거만 예쁘게 다듬어줘": it eases into motion, sweeps in, lingers,
        // and is drawn in all the way as the flash breaks; it soaks in by patches and dries by patches)
        float frame=1/240f;
        // it soaks in all round the formation as she leaps: wide, round its middle, she well inside it, nothing under her first
        assertEquals(0,SwordFormation.gathered(SwordFormation.POOL0),1e-6);
        assertEquals(0,SwordFormation.soak(SwordFormation.POOL0),1e-6);
        assertTrue(SwordFormation.gathered(SwordFormation.POOL0+.03f)>.99f&&SwordFormation.soak(SwordFormation.POOL1)>.99f,"all soaked in");
        for(float t=0;t<=SwordFormation.GATHER0;t+=frame)assertEquals(SwordFormation.POOL_WIDE,SwordFormation.gather(t),1e-9,"as wide as it soaks in at "+t);
        assertTrue(SwordFormation.POOL_WIDE>IsosangRules.FORMATION_STAND_BACK+3,"over where she stands and well past her");
        assertTrue(SwordFormation.POOL_WIDE>IsosangRules.FORMATION_RADIUS+2,"over the whole formation");
        // then it only closes in, never jumping; easing into motion (no sudden start), sweeping in (the video: some five
        // blocks a tenth of a second in), lingering, then drawn in all the way as the flash breaks there
        double last=SwordFormation.gather(0),fastest=0;
        for(float t=frame;t<=SwordFormation.COLLAPSE+.05f;t+=frame){
            double r=SwordFormation.gather(t);
            assertTrue(r<=last+1e-9,"it never spreads again as it gathers, at "+t);
            assertTrue(last-r<.7,"no jump at "+t+": "+(last-r));
            fastest=Math.max(fastest,last-r);last=r;
        }
        double first=SwordFormation.POOL_WIDE-SwordFormation.gather(SwordFormation.GATHER0+frame);
        assertTrue(first<.1*fastest,"easing into motion: "+first+" in its first frame, "+fastest+" at its fastest");
        assertTrue(fastest*240>90,"sweeping in fast: "+fastest*240+" blocks a second");
        double tenth=SwordFormation.gather(SwordFormation.GATHER0+.1f);
        assertTrue(tenth>4&&tenth<6.5,"some five blocks a tenth of a second in: "+tenth);
        double before=SwordFormation.gather(.5f),after=SwordFormation.gather(.56f);
        assertTrue(before<3.3&&after>2.4&&(before-after)/.06<10,"lingering, closing slowly: "+before+" to "+after);
        assertEquals(0,SwordFormation.gather(SwordFormation.COLLAPSE),1e-9,"drawn into the middle");
        // gone into the middle as the flash before the spike breaks there; swirling out only after it has burst
        assertEquals(IsosangRules.H_PLANT-.055f,SwordFormation.COLLAPSE,1e-6,"as the flash breaks");
        assertEquals(0,SwordFormation.gathered(SwordFormation.COLLAPSE),1e-6,"gone with the flash");
        assertTrue(SwordFormation.SWIRL0>=IsosangRules.H_PLANT,"swirling out once it has burst");
        // (2.82.7, "잉크가 모였다가 사라지고 검정색 물줄기가 회오리 처럼 흐르는거임", "이거처럼 이펙트가 크게 나옴": then a
        // whirlpool of black streams swirls out of the middle, as wide as the ink first soaked in, round a black eye)
        for(float t=0;t<SwordFormation.SWIRL0;t+=frame){
            assertEquals(0,SwordFormation.whirl(t),1e-9,"no whirlpool before the burst, at "+t);
            assertEquals(0,SwordFormation.eyeByte(t),1e-9,"no eye before the burst, at "+t);
        }
        // spreading out of the middle, never jumping or drawing back, slow to start
        last=0;fastest=0;
        for(float t=SwordFormation.SWIRL0;t<1.4f;t+=frame){
            double r=SwordFormation.whirl(t);
            assertTrue(r>=last-1e-9&&r-last<.7,"swirling out smoothly at "+t+": "+(r-last));
            fastest=Math.max(fastest,r-last);last=r;
        }
        assertTrue(SwordFormation.whirl(SwordFormation.SWIRL0+frame)<.1*fastest,"slow to start");
        assertTrue(SwordFormation.whirl(SwordFormation.SWIRL0+.03f)<1.5,"a small whirlpool first: "+SwordFormation.whirl(SwordFormation.SWIRL0+.03f));
        // big (the user's still: past the formation's edge, under her camera), mostly out as the rain of swords strikes
        assertEquals(SwordFormation.POOL_WIDE,SwordFormation.WHIRL_R,1e-9,"as wide as the ink first soaked in");
        assertTrue(SwordFormation.WHIRL_R>IsosangRules.FORMATION_RADIUS+4,"well past the formation's edge");
        assertTrue(SwordFormation.WHIRL_R>IsosangRules.FORMATION_STAND_BACK+4,"well past where she stands");
        assertTrue(SwordFormation.whirl(IsosangRules.H_RAIN0)>.75*SwordFormation.WHIRL_R,"mostly out as the swords strike: "+SwordFormation.whirl(IsosangRules.H_RAIN0));
        assertEquals(SwordFormation.WHIRL_R,SwordFormation.whirl(IsosangRules.H_RAIN1),1e-9);
        // its eye: the formation's empty middle (every sword strikes outside it, round its edge), opening after the
        // whirlpool has started to spread and always well inside it, never jumping
        double inner=IsosangRules.FORMATION_RING_R[0]-IsosangRules.FORMATION_RING_JITTER;
        assertTrue(SwordFormation.EYE_R<inner&&SwordFormation.EYE_R>inner-.8,"the swords strike round the eye's edge: "+SwordFormation.EYE_R+" inside "+inner);
        assertTrue(SwordFormation.EYE0>SwordFormation.SWIRL0,"the eye opens once it has started to spread");
        double lastEye=0;
        for(float t=SwordFormation.SWIRL0;t<1.4f;t+=frame){
            double e=SwordFormation.eye(t),w=SwordFormation.whirl(t);
            assertTrue(e<=.45*w+1e-9,"the eye well inside the whirlpool at "+t+": "+e+" of "+w);
            assertTrue(e>=lastEye-1e-9&&e-lastEye<.35,"the eye opens smoothly at "+t+": "+(e-lastEye));
            lastEye=e;
            // what the shader is told of it: its share of the whirlpool over .6, to a byte
            assertEquals(w<=1e-6?0:Math.min(1,e/w/.6),SwordFormation.eyeByte(t),.5/255+1e-6,"the eye's byte at "+t);
        }
        assertEquals(SwordFormation.EYE_R,SwordFormation.eye(SwordFormation.EYE1),1e-9);
        // its streams curl counterclockwise seen from above as they run out, some ten degrees off the round (the video
        // put back on the ground plane), and it turns slowly the same way (the shader's constants)
        assertTrue(SwordFormation.WHIRL_TWIST>0,"a stream keeps theta + TWIST ln r, theta growing clockwise seen from above");
        double pitch=Math.toDegrees(Math.atan(1/SwordFormation.WHIRL_TWIST));
        assertTrue(pitch>7&&pitch<14,"some ten degrees off the round: "+pitch);
        assertTrue(SwordFormation.WHIRL_SPIN>0&&SwordFormation.WHIRL_SPIN<1,"turning slowly: "+SwordFormation.WHIRL_SPIN);
        // (2.82.10, "퍼진다는 느낌이 안 나잖아 ... 소용돌이가 퍼져서 연기가 되는거처럼 만들어": 2.82.9 grew it three tenths
        // as it faded, too little and too quick) as the swords strike a clean front opens in its middle and runs out to the
        // rim, the whirlpool squeezed out ahead of it, turning into the smoke there
        assertTrue(SwordFormation.SPREAD0>IsosangRules.H_RAIN0&&Math.abs(SwordFormation.SPREAD0-1)<.05f,"at some 1 s, once the swords have begun to strike");
        assertTrue(SwordFormation.SPREAD1-SwordFormation.SPREAD0>=.25f&&SwordFormation.SPREAD1-SwordFormation.SPREAD0<=.4f,"long enough to see it run out, quick as a burst");
        for(float t=0;t<=SwordFormation.SPREAD0;t+=frame)assertEquals(0,SwordFormation.front(t),1e-9,"no front before it spreads, at "+t);
        assertTrue(SwordFormation.front(SwordFormation.SPREAD0+.02f)<.5,"from the very middle, easing in: "+SwordFormation.front(SwordFormation.SPREAD0+.02f));
        assertEquals(SwordFormation.RIM_R,SwordFormation.front(SwordFormation.SPREAD1),1e-6,"out to the rim");
        assertEquals(SwordFormation.RIM_R,SwordFormation.front(3),1e-6,"and there it stays");
        double lastFront=0,fastestFront=0,lateFront=0;
        for(float t=SwordFormation.SPREAD0;t<1.5f;t+=frame){
            double f=SwordFormation.front(t);
            assertTrue(f>=lastFront-1e-9,"running out only, at "+t);
            assertTrue(f-lastFront<.5,"never jumping at "+t+": "+(f-lastFront));
            fastestFront=Math.max(fastestFront,f-lastFront);
            if(t>SwordFormation.SPREAD1-.02f&&t<=SwordFormation.SPREAD1)lateFront=Math.max(lateFront,f-lastFront);
            lastFront=f;
        }
        assertTrue(fastestFront*240>40,"fast enough to read as spreading: "+fastestFront*240+" blocks a second");
        assertTrue(lateFront<.25*fastestFront,"slowing as it reaches the rim: "+lateFront/fastestFront);
        assertTrue(SwordFormation.front((SwordFormation.SPREAD0+SwordFormation.SPREAD1)/2)>.55*SwordFormation.RIM_R,"over half way out half way through");
        assertTrue(fastestFront*240<80,"never so fast it only pops: "+fastestFront*240+" blocks a second");
        // its edge pushed out a little, never past its square (1.4 of its radius), the streams' tails 1.04 of it
        assertEquals(0,SwordFormation.pushed(SwordFormation.SPREAD0),1e-9);
        assertEquals(SwordFormation.SPREAD_PUSH,SwordFormation.pushed(SwordFormation.SPREAD1),1e-9);
        assertTrue((1+SwordFormation.SPREAD_PUSH)*1.04<1.4,"inside its square");
        // what reached the rim turns into the smoke there, then nothing of it is left
        assertTrue(SwordFormation.WATER_OUT0>=SwordFormation.SPREAD1-.1f&&SwordFormation.WATER_OUT1>SwordFormation.SPREAD1,"once it has reached the rim");
        for(float t=0;t<SwordFormation.WATER_OUT0;t+=frame)assertEquals(1,SwordFormation.whirlShown(t),1e-6);
        for(float t=SwordFormation.WATER_OUT1;t<45;t+=.05f)assertEquals(0,SwordFormation.whirlShown(t),1e-6,"nothing left at "+t);
        for(float t=1;t<1.6f;t+=frame)assertEquals(SwordFormation.whirlShown(t),SwordFormation.whirlShown(t+frame),.05,"it vanishes in a jump at "+t);
        // (2.82.9, "바닥 잉크도 퍼질때 동시에 사라지게 하면 안되나?") the ink on the ground goes with it, from the middle out
        // as the front passes: the formation's blue ground and the ink at every sword's foot
        assertTrue(SwordFormation.tint(1.0f)>.5f,"the blue ground under the whirlpool");
        for(float t=SwordFormation.SPREAD1+.04f;t<45;t+=.05f)assertEquals(0,SwordFormation.tint(t),1e-6,"no blue ground left at "+t);
        for(float t=1;t<1.5f;t+=frame)assertEquals(SwordFormation.tint(t),SwordFormation.tint(t+frame),.05,"the blue ground jumps at "+t);
        double square=IsosangRules.FORMATION_RADIUS+.6;
        assertTrue(SwordFormation.RIM_R>square,"the front clears all of the blue ground");
        for(float t=0;t<2;t+=frame)assertEquals(Math.min(1,SwordFormation.front(t)/square/1.25),SwordFormation.holeByte(t,square),.5/255+1e-6,"the hole's byte at "+t);
        double farthest=0;
        for(var b:SwordFormation.layout(new UUID(9,3))){
            double d=Math.hypot(b.fw(),b.rt());farthest=Math.max(farthest,d);
            assertEquals(1,SwordFormation.groundInk(SwordFormation.SPREAD0,d),1e-6,"the ink at its foot there as it starts");
            assertEquals(0,SwordFormation.groundInk(SwordFormation.SPREAD1,d),1e-6,"cleared by the time the front reaches the rim");
            for(float t=1;t<1.4f;t+=frame)assertEquals(SwordFormation.groundInk(t,d),SwordFormation.groundInk(t+frame,d),.12,"a sword's ink jumps at "+t);
        }
        assertTrue(farthest+2<SwordFormation.RIM_R,"every sword inside the rim, its ink dried as the front gets there");
        // the inner ones go first: the ground clears from the middle out
        assertTrue(SwordFormation.groundInk(1.15f,5.5)<SwordFormation.groundInk(1.15f,8.7),"from the middle out");
        // the smoke at the rim: as the whirlpool reaches it, drifting a little out, thinning away by patches
        assertTrue(SwordFormation.front(SwordFormation.SMOKE_IN)>.8*SwordFormation.RIM_R,"as the whirlpool reaches the rim: "+SwordFormation.front(SwordFormation.SMOKE_IN));
        assertTrue(SwordFormation.WHIRL_R>SwordFormation.RIM_OUT,"the whirlpool reaches past the rim");
        assertEquals(SwordFormation.RIM_R,SwordFormation.rim(SwordFormation.SMOKE_IN),1e-9,"where it lies from the first");
        double lastRim=SwordFormation.RIM_R;
        for(float t=SwordFormation.SMOKE_IN;t<2.2f;t+=frame){
            double r=SwordFormation.rim(t);
            assertTrue(r>=lastRim-1e-9&&r-lastRim<.05,"drifting out smoothly at "+t+": "+(r-lastRim));
            lastRim=r;
        }
        double outer=IsosangRules.FORMATION_RING_R[IsosangRules.FORMATION_RING_R.length-1]+IsosangRules.FORMATION_RING_JITTER;
        assertTrue(SwordFormation.RIM_R-1.2>outer,"outside every sword: "+SwordFormation.RIM_R+" and "+outer);
        assertTrue(SwordFormation.RIM_OUT<IsosangRules.FORMATION_RADIUS+2.6,"before the mountains' feet");
        assertEquals(0,SwordFormation.smoke(SwordFormation.SMOKE_IN),1e-6);
        assertEquals(1,SwordFormation.smoke(1.5f),1e-6,"thick");
        assertTrue(SwordFormation.smoke(1.76f)>.1f&&SwordFormation.smoke(1.76f)<.9f,"thinning away: "+SwordFormation.smoke(1.76f));
        assertEquals(0,SwordFormation.smoke(SwordFormation.SMOKE_OUT1),1e-6);
        assertTrue(SwordFormation.SMOKE_OUT1<2,"gone before two seconds");
        for(float t=0;t<2.2f;t+=frame)assertEquals(SwordFormation.smoke(t),SwordFormation.smoke(t+frame),.07,"the smoke jumps at "+t);
        // its glow rises off the rim with it and goes with it; low behind her, where her camera is
        assertEquals(0,SwordFormation.veilLife(SwordFormation.SMOKE_IN),1e-6);
        assertEquals(1,SwordFormation.veilLife(1.5f),1e-6,"risen as the smoke is thickest");
        assertEquals(0,SwordFormation.veilLife(SwordFormation.SMOKE_OUT1),1e-6,"gone with the smoke");
        assertTrue(SwordFormation.VEIL_HIGH>=2.2&&SwordFormation.VEIL_HIGH<=3.2,"some half the mountains' height (4.4 to 6.4 blocks): "+SwordFormation.VEIL_HIGH);
        assertEquals(SwordFormation.VEIL_HIGH,SwordFormation.veilHeight(SwordFormation.VEIL_UP),1e-6);
        double lastHigh=SwordFormation.veilHeight(1.0f);
        for(float t=1.0f;t<2.2f;t+=frame){
            double h=SwordFormation.veilHeight(t);
            assertTrue(h>=lastHigh-1e-9&&h-lastHigh<.08,"rising smoothly at "+t);lastHigh=h;
            assertEquals(SwordFormation.veilLife(t),SwordFormation.veilLife(t+frame),.07,"the rising glow jumps at "+t);
        }
        assertEquals(0,SwordFormation.veilOpen(1.0,1.0),1e-9,"low straight behind her");
        assertEquals(1,SwordFormation.veilOpen(1.0+Math.PI,1.0),1e-9,"full across from her");
        for(double az=0;az<6.3;az+=.37){
            assertEquals(SwordFormation.veilAlpha(az,1.2,.4,1.4f,1,2,3),SwordFormation.veilAlpha(az+Math.PI*2,1.2,.4,1.4f,1,2,3),1e-9,"no seam round the rim");
            assertEquals(0,SwordFormation.veilAlpha(az,3,1,1.4f,1,2,3),1e-9,"nothing at its top");
        }
    }
    @Test void theWhirlpoolSpreadsOutIntoThickBlueSmokeBillowingUpOffTheRim(){
        // (2.82.10, "연기 파란색이여함 연기를 더 진하게 만들고 더 많이 피어오르게 하고 소용돌이가 퍼져서 연기가 되는거처럼
        // 만들어") puffs of blue smoke: off the spreading whirlpool as it runs out, then thick off the rim, billowing up
        float frame=1/240f;int seed=0x5eed;double back=1.0;
        assertTrue(SwordFormation.puffs(seed,back,SwordFormation.FRONT0-.01f).isEmpty(),"none before it spreads");
        assertTrue(SwordFormation.puffs(seed,back,SwordFormation.PUFF_END).isEmpty(),"all gone after");
        assertTrue(SwordFormation.FRONT0>=SwordFormation.SPREAD0&&SwordFormation.FRONT1<=SwordFormation.SPREAD1,"off the whirlpool while it spreads");
        assertTrue(SwordFormation.PUFF0<=SwordFormation.SMOKE_IN&&SwordFormation.PUFF0>SwordFormation.SPREAD0,"off the rim as the whirlpool reaches it");
        assertTrue(SwordFormation.PUFF_END<2.4f,"gone soon after the smoke");
        int most=0;double highest=0;
        for(float t=SwordFormation.FRONT0;t<SwordFormation.PUFF_END;t+=.01f){
            var all=SwordFormation.puffs(seed,back,t);most=Math.max(most,all.size());
            for(var p:all){
                double r=Math.hypot(p.x(),p.z());
                assertTrue(r>=1.4&&r<SwordFormation.RIM_OUT+1.3,"over the formation and its rim, before the mountains: "+r+" at "+t);
                assertTrue(p.y()>=.1&&p.y()<6.2,"rising off the ground, no higher than the mountains: "+p.y());
                assertTrue(p.size()>0&&p.size()<2.6,"a puff's size: "+p.size());
                assertTrue(p.alpha()>=0&&p.alpha()<=1&&p.light()>=0&&p.light()<=1&&p.thin()>=0&&p.thin()<=1);
                highest=Math.max(highest,p.y());
            }
        }
        assertTrue(most>120,"plenty of smoke: "+most);
        assertTrue(most<700,"not more than it can draw: "+most);
        assertTrue(highest>4,"billowing up past most of the mountains' height: "+highest);
        // thick: at its thickest the rim round the far side is covered
        float thick=1.4f;double cover=0;
        for(var p:SwordFormation.puffs(seed,back,thick))if(Math.hypot(p.x(),p.z())>SwordFormation.RIM_R-1)cover+=p.alpha()*p.size()*2;
        assertTrue(cover>SwordFormation.RIM_R*2*Math.PI*.9,"the rim covered over: "+cover);
        // each puff moves and changes smoothly (puffs are told apart by their seeds)
        for(float t=SwordFormation.FRONT0;t<SwordFormation.PUFF_END;t+=.0137f){
            var a=SwordFormation.puffs(seed,back,t);var b=SwordFormation.puffs(seed,back,t+frame);
            java.util.Map<Integer,SwordFormation.Puff> next=new java.util.HashMap<>();for(var p:b)next.put(p.seed(),p);
            for(var p:a){
                var q=next.get(p.seed());if(q==null){assertTrue(p.alpha()<.03f,"a puff vanishes in a jump at "+t+": "+p.alpha());continue;}
                assertTrue(Math.abs(q.y()-p.y())<.12&&Math.hypot(q.x()-p.x(),q.z()-p.z())<.3,"a puff jumps at "+t);
                assertEquals(p.size(),q.size(),.06,"a puff's size jumps at "+t);
                assertEquals(p.alpha(),q.alpha(),.1,"a puff's strength jumps at "+t);
            }
            java.util.Set<Integer> before=new java.util.HashSet<>();for(var p:a)before.add(p.seed());
            for(var q:b)if(!before.contains(q.seed()))assertTrue(q.alpha()<.03f,"a puff appears in a jump at "+t+": "+q.alpha());
        }
        // low and thin behind her (her camera looks in from there), full across from her
        double behindTop=0,acrossTop=0;
        for(float t=SwordFormation.PUFF0;t<SwordFormation.PUFF_END;t+=.02f)for(var p:SwordFormation.puffs(seed,back,t)){
            double az=Math.atan2(p.z(),p.x()),off=Math.abs(Math.atan2(Math.sin(az-back),Math.cos(az-back)));
            if(off<Math.toRadians(20))behindTop=Math.max(behindTop,p.y());
            if(off>Math.toRadians(150))acrossTop=Math.max(acrossTop,p.y());
        }
        assertTrue(behindTop<.6*acrossTop,"low behind her: "+behindTop+" against "+acrossTop);
        // the same for everyone: seeded
        assertEquals(SwordFormation.puffs(seed,back,1.4f),SwordFormation.puffs(seed,back,1.4f));
    }
    @Test void theWhirlpoolsClockAndBytes(){
        // what the ink shader is told: its clock over two seconds; the gathering sheet's share in a byte (exactly)
        assertEquals(.25f,SwordFormation.poolClock(.5f),1e-6);assertEquals(1,SwordFormation.poolClock(9),1e-6);
        for(int k=0;k<=127;k++)assertEquals(k,Math.round(SwordFormation.soakByte(k/127f)*255));
    }
    @Test void herCameraIsDrawnBackForItAndInAgain(){
        assertEquals(0,SwordFormation.pull(0),1e-9);
        assertTrue(SwordFormation.pull(1.4f)>4,"well back as the swords rain");
        assertEquals(SwordFormation.PULL_STAND,SwordFormation.pull(20),1e-6,"a little back as it stands");
        assertEquals(0,SwordFormation.pull(SwordFormation.LENGTH),1e-6,"in again at its end");
        for(float t=0;t<SwordFormation.LENGTH;t+=.002f)assertEquals(SwordFormation.pull(t),SwordFormation.pull(t+.002f),.03,"the camera jumps at "+t);
        assertEquals(SwordFormation.pull(3),IsosangSlash.pull(IsosangRules.FORMATION,3),1e-9);
    }
}
