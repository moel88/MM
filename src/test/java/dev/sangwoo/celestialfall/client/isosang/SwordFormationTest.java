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
        // (2.82.13, "검들이 다 삐 뚫어져있냐 영상은 전부 제대로 박혀있는데": every one struck straight down)
        for(var s:a)assertEquals(0,s.tilt(),1e-12,"upright");
        var on=a[stand];assertEquals(IsosangRules.FORMATION_STAND,on.height(),1e-12);
        assertTrue(on.hers());for(int i=0;i<a.length;i++)if(i!=stand)assertFalse(a[i].hers(),"only the one she stands on is hers");
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
    @Test void sixOfTheUsersMountainGoRoundTheCircleFloating() throws java.io.IOException{
        // 2.82.16 ("이제 산 갈아엎고 사진이랑 똑같이 만들어봐 인게임에서는 저거랑 똑같은 산 6개가 한 바퀴로 둘러져있음", then
        // their painting: "이거 쓰면 되는데 투명하게 다듬으면 될듯")
        UUID id=UUID.fromString("6b0e5a1c-1d2e-4f00-9a3b-0c1d2e3f4a5b");
        float yaw=40;var all=SwordFormation.mountains(id,yaw);
        assertEquals(6,all.length);
        assertSame(all,SwordFormation.mountains(new UUID(id.getMostSignificantBits(),id.getLeastSignificantBits()),yaw),"the same mountains for the same formation");
        double behind=SwordFormation.behind(yaw);
        for(int i=0;i<all.length;i++){
            var mt=all[i];
            assertEquals(SwordFormation.MOUNTAIN_R,mt.radius(),1e-12);assertTrue(mt.radius()>IsosangRules.FORMATION_RADIUS+2,"outside the formation");
            // evenly round, a sixth apart, one of the gaps between them right behind her
            assertEquals(60,MathHelper.wrapDegrees(Math.toDegrees(all[(i+1)%6].az()-mt.az())),1e-6);
            assertEquals(30,Math.abs(MathHelper.wrapDegrees(Math.toDegrees(mt.az()-behind)))%60,1e-6,"their middles 30 degrees off behind her");
        }
        // the painting as the texture: as wide as it is for its height; most of a sixth of the round each, a little gap
        // between neighbours (as the still's); smaller than the traced one was ("산 크기가 너무 커 지금 더 줄이고")
        try(var in=SwordFormation.class.getResourceAsStream("/assets/celestialfall/textures/effect/isosang_mountain.png")){
            assertNotNull(in,"the painting is in the mod");
            var img=javax.imageio.ImageIO.read(in);
            assertEquals(SwordFormation.MOUNTAIN_ASPECT,img.getWidth()/(double)img.getHeight(),1e-6,"as wide as the painting");
            assertTrue(img.getColorModel().hasAlpha(),"see-through round it");
            assertEquals(0,img.getRGB(0,0)>>>24,"clear at its corner");
        }
        double sixth=SwordFormation.MOUNTAIN_R*Math.PI/6;
        assertTrue(SwordFormation.MOUNTAIN_HALF<sixth-1&&SwordFormation.MOUNTAIN_HALF>.75*sixth,"most of a sixth of the round, a gap between: "+SwordFormation.MOUNTAIN_HALF+" of "+sixth);
        assertTrue(SwordFormation.MOUNTAIN_TOP<5);
        // on the circle (not a hexagon of flat faces), floating over the ground ("영상에는 산이 중간에 떠있는데", "조금 더 낮추고")
        Vec3d mid=new Vec3d(0,0,0);
        for(var mt:all)for(double x=-SwordFormation.MOUNTAIN_HALF;x<=SwordFormation.MOUNTAIN_HALF;x+=.5)
            assertEquals(mt.radius(),SwordFormation.onMountain(mid,mt,x,0,0).horizontalLength(),1e-9,"round the circle");
        assertEquals(SwordFormation.MOUNTAIN_FLOAT,SwordFormation.onMountain(mid,all[0],0,0,0).y,1e-12);
        assertTrue(SwordFormation.MOUNTAIN_FLOAT>.5&&SwordFormation.MOUNTAIN_FLOAT<3,"floating, a little");
        // a mountain at each sixth, a gap right behind her (her camera looks in through it)
        for(var mt:all)assertEquals(1,SwordFormation.mountainCover(all,mt.az()),1e-9,"a mountain at each sixth");
        assertTrue(SwordFormation.mountainCover(all,behind)<.2,"a gap behind her");
        // rising into place without a jump, sinking away as the formation ends
        float end=IsosangRules.H_END,frame=1/60f;
        for(var mt:all){
            assertEquals(0,SwordFormation.mountainUp(end,mt,SwordFormation.RISE0-.01f),1e-9);
            assertEquals(1,SwordFormation.mountainUp(end,mt,10),1e-6);
            assertEquals(0,SwordFormation.mountainUp(end,mt,end+SwordFormation.GONE+.25f),1e-6);
            for(float t=SwordFormation.RISE0;t<end+3;t+=frame)
                assertEquals(SwordFormation.mountainUp(end,mt,t),SwordFormation.mountainUp(end,mt,t+frame),.06,"it jumps at "+t);
        }
        // where they stand before her camera, out behind her, they thin to let her be seen; not inside their ring
        var mt0=all[0];double toBack=Math.IEEEremainder(behind-mt0.az(),Math.PI*2)*mt0.radius();
        assertEquals(SwordFormation.OCCLUDED,SwordFormation.occlusion(mt0,toBack,behind),1e-6);
        assertEquals(1,SwordFormation.occlusion(mt0,toBack,Double.NaN),1e-9);
        assertEquals(1,SwordFormation.occlusion(mt0,toBack,behind+Math.PI),1e-6);
    }
    @Test void faintHazeTurnsSlowlyOverTheMountainsAndWispsOfMistDriftLow(){
        // 2.82.16 ("산 위에는 희미한 안개같은 구름이 천천히 돌아가고"): banks of faint haze over and behind the mountains, all
        // turning slowly the same way together; the low wisps of mist as before; no curled clouds any more
        UUID id=UUID.fromString("6b0e5a1c-1d2e-4f00-9a3b-0c1d2e3f4a5b");
        float yaw=-70;var all=SwordFormation.clouds(id,yaw);
        int high=0,low=0;
        for(var cl:all){
            assertTrue(cl.mist(),"no curled clouds");
            if(cl.haze()){
                high++;
                assertTrue(cl.up()>2.5&&cl.up()<6.3,"from among the mountains up over their summits: "+cl.up());
                assertTrue(cl.radius()>SwordFormation.MOUNTAIN_R-1,"round and behind the mountains");
                assertEquals(SwordFormation.HAZE_TURN,cl.drift(),1e-12,"turning together");
            }else{low++;assertTrue(cl.up()<1.1&&cl.radius()<IsosangRules.FORMATION_RADIUS+1,"a wisp low over the formation");}
            // its band runs on without a break, thinning to nothing at its end(s)
            var path=SwordFormation.cloudPath(cl,10,1);
            for(int k=0;k+1<path.size();k++){
                double d=Math.hypot(path.get(k+1)[0]-path.get(k)[0],path.get(k+1)[1]-path.get(k)[1]);
                assertTrue(d<.35,"a gap in a cloud's band: "+d);
            }
            assertEquals(0,path.get(path.size()-1)[2],1e-9,"its tail thins to nothing");
            if(cl.haze())assertEquals(0,path.get(0)[2],1e-9,"a bank of haze soft at both ends");
            assertTrue(path.stream().mapToDouble(q->q[2]).max().orElse(0)>.95,"full in its body");
            // it drifts and its wave runs along it as a function of the clock: no jump from one frame to the next
            var a=SwordFormation.cloudPath(cl,10,1);var b=SwordFormation.cloudPath(cl,10+1/60f,1);
            for(int k=0;k<a.size();k++)assertEquals(a.get(k)[1],b.get(k)[1],.02,"a cloud jumps");
        }
        assertEquals(SwordFormation.HAZE,high);assertEquals(7,low,"seven wisps of mist");
        assertTrue(Math.PI*2/SwordFormation.HAZE_TURN>90,"slowly: a turn in some two minutes");
        assertTrue(SwordFormation.HAZE_ALPHA>.15f&&SwordFormation.HAZE_ALPHA<.25f,"denser (\"안개 구름도 더 진하게\"), still see-through");
        assertTrue(SwordFormation.HAZE_NEAR0<SwordFormation.HAZE_NEAR1,"thinning away as the camera comes into it");
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
        // (the mist standing round the rim: theWhirlpoolSpreadsOutIntoASeeThroughBlueMistStandingRoundTheRim)
    }
    @Test void theWhirlpoolSpreadsOutIntoASeeThroughBlueMistStandingRoundTheRim(){
        // (2.82.11, "아니 내가 사진에 표시한 애들처럼 나오게 해달라고 연기를", their marked screenshot: a see-through deep
        // blue mist hugging the ground round the rim in streaks, rising a little; 2.82.10's billows were opaque lumps)
        float frame=1/240f;
        // round the rim: as the whirlpool reaches it, standing to some half the mountains' height and more, gone with the
        // smoke lying there, thinning away by holes at the last
        assertEquals(0,SwordFormation.veilLife(SwordFormation.SMOKE_IN),1e-6);
        assertEquals(1,SwordFormation.veilLife(1.5f),1e-6,"there as the smoke is thickest");
        assertEquals(0,SwordFormation.veilLife(SwordFormation.SMOKE_OUT1),1e-6,"gone with the smoke");
        assertTrue(SwordFormation.VEIL_HIGH>3&&SwordFormation.VEIL_HIGH<4.4,"over half the mountains' height (4.4 to 6.4 blocks), under the lowest: "+SwordFormation.VEIL_HIGH);
        assertEquals(SwordFormation.VEIL_HIGH,SwordFormation.veilHeight(SwordFormation.VEIL_UP),1e-6);
        assertTrue(SwordFormation.VEIL_UP>SwordFormation.SMOKE_IN&&SwordFormation.VEIL_UP<SwordFormation.SMOKE_OUT0);
        double lastHigh=SwordFormation.veilHeight(1.0f);
        for(float t=1.0f;t<2.2f;t+=frame){
            double h=SwordFormation.veilHeight(t);
            assertTrue(h>=lastHigh-1e-9&&h-lastHigh<.08,"rising smoothly at "+t);lastHigh=h;
            assertEquals(SwordFormation.veilLife(t),SwordFormation.veilLife(t+frame),.07,"the mist jumps at "+t);
            assertEquals(SwordFormation.veilThin(t),SwordFormation.veilThin(t+frame),.03,"its thinning jumps at "+t);
        }
        assertEquals(0,SwordFormation.veilThin(1.5f),1e-6,"whole while it stands");
        assertEquals(1,SwordFormation.veilThin(SwordFormation.SMOKE_OUT1),1e-6,"broken up as it goes");
        // off the spreading whirlpool: a low ring of it riding just outside the clean front, handing over to the rim's
        assertEquals(0,SwordFormation.frontMist(SwordFormation.SPREAD0),1e-6,"none before it spreads");
        assertEquals(0,SwordFormation.frontMist(SwordFormation.SPREAD1+.08f),1e-6,"handed over to the rim's");
        assertTrue(SwordFormation.frontMist(1.2f)>.9f,"riding it as it runs out");
        for(float t=1;t<1.5f;t+=frame){
            assertEquals(SwordFormation.frontMist(t),SwordFormation.frontMist(t+frame),.05,"the mist off the whirlpool jumps at "+t);
            double at=SwordFormation.frontMistAt(t);
            assertTrue(at>SwordFormation.front(t)&&at<=SwordFormation.RIM_R+SwordFormation.FRONT_MIST_OUT+1e-9,"just outside the front, never past the rim's mist: "+at);
            assertTrue(SwordFormation.frontMistHeight(t)<=SwordFormation.FRONT_MIST_HIGH+1e-9&&SwordFormation.frontMistHeight(t)>0,"low");
        }
        assertTrue(SwordFormation.FRONT_MIST_HIGH<SwordFormation.VEIL_HIGH/2,"lower than the rim's");
        assertTrue(SwordFormation.veilLife(SwordFormation.SPREAD1+.08f)>.3f,"the rim's there as it hands over");
        // low behind her (her camera looks in from there), full across from her; gone close to the camera
        assertEquals(0,SwordFormation.veilOpen(1.0,1.0),1e-9,"low straight behind her");
        assertEquals(1,SwordFormation.veilOpen(1.0+Math.PI,1.0),1e-9,"full across from her");
        assertEquals(0,SwordFormation.camera(new Vec3d(0,0,0),new Vec3d(1.5,0,0)),1e-9,"gone close to the camera");
        assertEquals(1,SwordFormation.camera(new Vec3d(0,0,0),new Vec3d(6,0,0)),1e-9);
        // a ring drawn finely enough that its quads read as one sheet round the rim (some 3/4 of a block each)
        assertTrue(Math.PI*2*SwordFormation.RIM_OUT/SwordFormation.MIST_SEGS<.85,"fine round the rim");
        // a dark navy, see-through, giving no light (2.82.12, "더 진하고 어두운 남색", "연기는 눈에 잘 안 띄이게 만들어야함
        // 살짝 투명하고": none of its light added again; the front running out lights what is round it only a little)
        assertEquals(0,SwordFormation.MIST_GLOW,1e-9,"no light added");
        assertTrue(SwordFormation.FRONT_LIGHT>0&&SwordFormation.FRONT_LIGHT<.2f,"the front's light faint: "+SwordFormation.FRONT_LIGHT);
    }
    @Test void asItStandsTheSwordsTurnSlowlyTheWaterFlowsAndInkComesOffThem(){
        // (2.82.13: the user's video read and confirmed, "검들이 천천히 회전하네 자연스럽게", "돌면서 바깥으로 퍼짐", "연한
        // 물도 있는데", "검 밑동에서 동그란 물결", "칼날 옆의 어두운 연기")
        var a=SwordFormation.layout(UUID.fromString("6b0e5a1c-1d2e-4f00-9a3b-0c1d2e3f4a5b"));
        float frame=1/240f;int one=0,other=0;
        for(var b:a){
            // still in the sky and as it strikes; then turning slowly about its length, at its own pace, never jumping
            assertEquals(b.spin(),SwordFormation.spinAt(b,b.plant()),1e-9);
            if(b.hers()){assertEquals(b.spin(),SwordFormation.spinAt(b,30),1e-9,"hers stays still under her");continue;}
            double rate=(SwordFormation.spinAt(b,20.5f)-SwordFormation.spinAt(b,20))/.5;
            assertTrue(Math.abs(rate)>=47&&Math.abs(rate)<=73,"some six seconds a turn: "+rate);
            if(rate>0)one++;else other++;
            for(float t=b.plant();t<b.plant()+3;t+=frame)
                assertTrue(Math.abs(SwordFormation.spinAt(b,t+frame)-SwordFormation.spinAt(b,t))<=73*frame+1e-6,"it jumps at "+t);
        }
        assertTrue(one>5&&other>5,"each its own way round: "+one+" "+other);
        assertEquals(0,SwordFormation.eased(0,1.2),1e-12);assertEquals(.6,SwordFormation.eased(1.2,1.2),1e-12);assertEquals(1.4,SwordFormation.eased(2,1.2),1e-9);
        // rings opening from their feet now and then, fading as they spread, never jumping
        double lastR=0;
        for(double u=0;u<1;u+=.01){double r=SwordFormation.rippleRadius(u);assertTrue(r>=lastR&&r<=SwordFormation.RIPPLE_R+1e-9);lastR=r;}
        assertEquals(0,SwordFormation.rippleAlpha(0),1e-9,"comes in from nothing");assertEquals(0,SwordFormation.rippleAlpha(1),1e-9,"gone as it ends");
        for(var b:a){
            int rings=0;double last=-1;
            for(float t=2;t<12;t+=frame){
                double u=SwordFormation.rippleAge(b,t);float now=u<0?0:SwordFormation.rippleAlpha(u),then;
                double v=SwordFormation.rippleAge(b,t+frame);then=v<0?0:SwordFormation.rippleAlpha(v);
                assertEquals(now,then,.03,"a ring jumps at "+t);
                if(u>=0&&last<0)rings++;last=u;
            }
            assertTrue(rings>=2&&rings<=5,"now and then: "+rings);
        }
        // the dark smoke and the flecks off the blades: one after another, each coming in and going without a jump
        for(int base:new int[]{611,4242,-977})for(int k=0;k<SwordFormation.WISPS;k++)
            for(float t=2;t<8;t+=frame){
                float now=SwordFormation.wispAlpha(SwordFormation.shed(base,k,SwordFormation.WISPS,SwordFormation.WISP_LIFE,t)[0]);
                float then=SwordFormation.wispAlpha(SwordFormation.shed(base,k,SwordFormation.WISPS,SwordFormation.WISP_LIFE,t+frame)[0]);
                assertEquals(now,then,.03,"smoke jumps at "+t);
            }
        assertEquals(0,SwordFormation.wispAlpha(0),1e-9);assertEquals(0,SwordFormation.wispAlpha(.9999),.01);
        // the water on the ground: coming in as the whirlpool's water goes, there until the formation ends, gone with it;
        // the formation's clock it runs by told in thousandths of a second, sixteen bits
        float end=IsosangRules.H_END;
        assertEquals(0,SwordFormation.flowLife(SwordFormation.FLOW0,end),1e-9);
        assertTrue(SwordFormation.FLOW0<SwordFormation.WATER_OUT1&&SwordFormation.FLOW1>SwordFormation.WATER_OUT1,"as the whirlpool's water goes");
        assertEquals(1,SwordFormation.flowLife(5,end),1e-9);assertEquals(1,SwordFormation.flowLife(end-.01f,end),1e-6);
        assertEquals(0,SwordFormation.flowLife(end+SwordFormation.GONE,end),1e-9);
        for(float t=1;t<end+1;t+=frame){
            assertEquals(SwordFormation.flowLife(t,end),SwordFormation.flowLife(t+frame,end),.03,"the water jumps at "+t);
            int step=SwordFormation.flowClock(t+frame)-SwordFormation.flowClock(t);
            assertTrue(step>=3&&step<=6,"its clock runs on smoothly at "+t+": "+step);
        }
        assertTrue(SwordFormation.flowClock(end+SwordFormation.GONE)<65535,"told all the while it lies");
    }
    @Test void itsDarkerWaterFlowsRoundSlowlyAndOut() throws java.io.IOException{
        // 2.82.15 ("너무 빨리 돌아가고 ... 가운데 검정색이 순환", the video's darker water marked red: "물의 흐름 느낌"): the
        // ink shader turns its bands round at FLOW_TURN radians a second, clockwise seen from above, and spreads them out
        // at FLOW_OUT blocks a second; far slower round than 2.82.14's whole turn every eleven seconds or so (0.55)
        String src;try(var in=SwordFormation.class.getResourceAsStream("/assets/celestialfall/shaders/core/kageno_flame.fsh")){src=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var m=java.util.regex.Pattern.compile("const float FLOW_W=([0-9.]+),FLOW_OUT=([0-9.]+);").matcher(src);
        assertTrue(m.find(),"the shader says how its darker water flows");
        assertEquals(SwordFormation.FLOW_TURN,Double.parseDouble(m.group(1)),1e-9);assertEquals(SwordFormation.FLOW_OUT,Double.parseDouble(m.group(2)),1e-9);
        assertTrue(SwordFormation.FLOW_TURN<=.25*1.0001&&SwordFormation.FLOW_TURN>=.1,"slow, a turn in half a minute or so: "+SwordFormation.FLOW_TURN);
        assertTrue(SwordFormation.FLOW_OUT>.2&&SwordFormation.FLOW_OUT<1,"spreading out gently");
        assertTrue(src.contains("float X=(th-FLOW_W*tsec)"),"clockwise seen from above (theta grows clockwise there)");
        assertTrue(src.contains("float pnoise(vec2 p,float n)"),"drawn round the circle, closing up exactly");
        assertTrue(src.contains("float bA=.42*band*along*grain*smoothstep(ri-.02,ri+.12,r)*tint.a;"),"in its water only, not over the bare middle");
        assertTrue(SwordFormation.WATER_IN>2,"its middle bare");
    }
    @Test void theWaterRunsUpToTheMountainsFeet(){
        // 2.82.14 ("산이랑 물이랑 X좌표 붙어있게 해"): each way round the water runs to the nearest mountain standing that
        // way and a little under it, never stopping short of it; its edge smooth all round; its pale strands along the
        // mountains' feet, inside the water (2.82.16: the mountains all the way round)
        int checked=0;
        for(long seed=1;seed<=24;seed++){
            UUID id=new UUID(seed*0x9E3779B97F4A7C15L,~seed*31);float yaw=(float)(seed*47%360);
            var ms=SwordFormation.mountains(id,yaw);var sh=SwordFormation.shore(id,yaw);
            double far=0;for(var mt:ms)far=Math.max(far,mt.radius());double cap=far;
            int n=SwordFormation.SHORE_STEPS;double last=Double.NaN;
            for(int k=0;k<=n*10;k++){
                double az=Math.PI*2*k/(n*10),e=SwordFormation.waterEdge(ms,az),nearest=far;
                for(var mt:ms){
                    double w=SwordFormation.along(mt,az);if(w<=0)continue;
                    double s=mt.radius();
                    if(w>=1)assertTrue(e<=s+1e-9,"it runs to every mountain standing that way: "+e+" "+s);
                    nearest=Math.min(nearest,s);
                }
                assertTrue(e>=nearest-1e-9,"never short of the nearest: "+e+" "+nearest);
                assertTrue(e<=cap+1e-9&&e>SwordFormation.WATER_IN+SwordFormation.STRAND_FROM,"between: "+e);
                if(!Double.isNaN(last))assertEquals(last,e,.25,"its edge jumps at "+az);
                last=e;checked++;
            }
            // (2.82.16) the water's edge the circle at the mountains' feet, right round (behind her, in the gap between
            // two, too); no strands in the gap
            double back=SwordFormation.behind(yaw);
            assertEquals(SwordFormation.MOUNTAIN_R,SwordFormation.waterEdge(ms,back),1e-9,"on the circle behind her too");
            assertTrue(SwordFormation.mountainCover(ms,back)<.2,"few strands in the gap behind her");
            for(var mt:ms){
                assertEquals(1,SwordFormation.mountainCover(ms,mt.az()),1e-9,"strands at its feet");
                assertTrue(SwordFormation.waterEdge(ms,mt.az())<=mt.radius()+1e-9,"up to its face");
            }
            // the rings as drawn: the water from inside the shader's soft inner edge to its edge and past it, the strands
            // a strip inside the edge
            for(int k=0;k<n;k++){
                double e=SwordFormation.waterEdge(ms,Math.PI*2*k/n);
                assertEquals(SwordFormation.WATER_IN,sh.water()[0][k],1e-12);assertEquals(e,sh.water()[1][k],1e-12);
                assertEquals(e+SwordFormation.EDGE_PAST,sh.water()[2][k],1e-12);
                for(int j=0;j+1<sh.strands().length;j++)assertTrue(sh.strands()[j][k]<sh.strands()[j+1][k]);
                assertTrue(sh.strands()[0][k]>SwordFormation.WATER_IN&&sh.strands()[3][k]<e);
            }
        }
        assertTrue(checked>40000);
        assertTrue(SwordFormation.WATER_IN<.37*SwordFormation.FLOW_SCALE,"inside the shader's soft inner edge");
        assertTrue(SwordFormation.EDGE_PAST>.3,"a little under the mountains, no ground between");
        assertArrayEquals(new float[]{1,1,0},SwordFormation.WATER_RINGS);assertArrayEquals(new float[]{0,1,1,0},SwordFormation.STRAND_RINGS);
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
