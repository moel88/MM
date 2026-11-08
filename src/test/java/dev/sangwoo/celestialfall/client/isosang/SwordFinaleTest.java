package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlashTest.turn;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's H again as drawn (2.101.0, user request: "이번에는 H를 사용한 상태에서 H를 한 번 더 누르면 되는 스킬 ... H 영역에
 * 있던 모든 검이 전부 플레이어 뒤로 가고 앞으로 꽂는 스킬이야 이걸 쓰면 H가 해제돼", the reading confirmed; 2.101.1: "영상은 칼을
 * 타고 한바퀴를 돈 다음에 공중에서 검을 전면에 모두 꽂는거야 그리고 내려옴", "그리고 박히는 검들은 더 커야해"): her motion (from the
 * formation's stance on the pommel to the stance on the ground, no joint jumping or flipping, never a foot in the ground,
 * both feet on her sword as she rides it), its swords (the formation's own pulled out from where and as they stood,
 * nearest her first, as many of light with them; the one she stood on under her feet as she rides; streaming after her,
 * spread behind her back like wings, launched and come down slanting into the band ahead of her, hilts toward her, grown
 * three times as big, gone while it is drawn; never jumping from one frame to the next), and her camera's shots (following her
 * round her lap from low before her, in front of her and close as she raises her fingers up there, behind her as they
 * come down, never inside her, never jumping but at its one cut).
 */
class SwordFinaleTest {
    static final Anim CLIP=SwordFinale.CLIP;
    static final UUID FORM=new UUID(0x1234L,0x5678L);
    static final Vec3d CENTRE=new Vec3d(40,64,-20);
    static final float FORM_YAW=30,YAW=50;
    static IsosangClient.FinaleRun run(){
        int on=IsosangRules.standSword();
        Vec3d f=Vec3d.fromPolar(0,FORM_YAW);double[] st=IsosangRules.standAt();
        Vec3d spot=CENTRE.add(f.multiply(st[0])).add(new Vec3d(-f.z,0,f.x).multiply(st[1]));
        return new IsosangClient.FinaleRun(FORM,0,CENTRE,FORM_YAW,on,1,spot);
    }
    static StandClient.Cast cast(){
        return new StandClient.Cast(UUID.randomUUID(),UUID.randomUUID(),null,IsosangRules.SKILL,IsosangRules.FINALE,200,200,0,run().spot(),YAW,List.of());
    }

    /** Where {@code q} is on shot {@code s}'s screen (-1 to 1 across and up it, 16:9) and how deep in front of it. */
    static double[] screen(SwordFinale.Shot s,Vec3d q){
        Vec3d fw=s.look().subtract(s.eye()).normalize(),right=fw.crossProduct(new Vec3d(0,1,0)).normalize(),up=right.crossProduct(fw),d=q.subtract(s.eye());
        double z=d.dotProduct(fw),v=Math.tan(Math.toRadians(s.fov()/2));
        return new double[]{d.dotProduct(right)/z/(v*16/9.0),d.dotProduct(up)/z/v,z};
    }
    /** Whether {@code q} is out of shot {@code s}'s frame: past its edge, or behind its lens. */
    static boolean outOf(SwordFinale.Shot s,Vec3d q){double[] a=screen(s,q);return a[2]<.05||Math.abs(a[0])>1||Math.abs(a[1])>1;}
    @Test void itRunsFromTheFormationsStanceToTheStanceOnTheGround(){
        var first=CLIP.frame(0);var last=CLIP.frame(CLIP.length());
        var on=Anim.joints(Anim.k(0,SwordFormation.H_ON),Anim.k(1,SwordFormation.H_ON)).frame(0);
        for(int j=0;j<6;j++){assertTrue(turn(first,on,j)<.5,"it starts as she stood on the pommel");assertTrue(turn(last,IsosangSlash.stance(),j)<.5,"it ends in the stance");}
        assertEquals(IsosangRules.LENGTH[IsosangRules.FINALE]/20f,CLIP.length(),1e-6);
        assertSame(CLIP,IsosangSlash.hit(IsosangRules.FINALE),"its swing is drawn with its own clip");
        assertTrue(SwordFinale.LIFE>=IsosangRules.FINALE_GONE1+.3f,"drawn until its swords' light is gone");
    }
    @Test void noJointJumpsOrFlipsAndNeverAFootInTheGround(){
        double h=1/960.0;
        for(double t=h;t+2*h<=CLIP.length();t+=h){
            var p0=CLIP.frame((float)(t-h));var p1=CLIP.frame((float)t);var p2=CLIP.frame((float)(t+h));var p3=CLIP.frame((float)(t+2*h));
            for(int j=0;j<6;j++){
                double before=turn(p0,p1,j),now=turn(p1,p2,j),after=turn(p2,p3,j);
                assertFalse(now>4&&now>4*Math.max(before,after)+1e-9,"joint "+j+" jumps "+now+" deg at "+t);
            }
        }
        for(double t=0;t+1/60.0<=CLIP.length();t+=1/60.0)
            for(int j=0;j<6;j++)assertTrue(turn(CLIP.frame((float)t),CLIP.frame((float)(t+1/60.0)),j)<60,"joint "+j+" flips at "+t);
        for(float t=0;t<=CLIP.length();t+=1/120f){
            double[] f=IsosangSlash.feet(CLIP.frame(t));
            assertTrue(f[0]>-.02&&f[1]>-.02,"a foot in the ground at "+t+": "+f[0]+" "+f[1]);
        }
        // a crouch as she lands, her hand up before her face as she raises her fingers
        assertTrue(CLIP.frame(IsosangRules.FINALE_LAND).up()<-.2,"crouched as she lands");
        // (2.101.1) both feet on her sword as she rides it round (the ground of the pose is its top), crouched
        for(float t=IsosangRules.FINALE_RIDE;t<=1.2f;t+=1/60f){
            double[] f=IsosangSlash.feet(CLIP.frame(t));
            assertTrue(f[0]<.12&&f[1]<.12,"both feet on her sword at "+t+": "+f[0]+" "+f[1]);
        }
        assertTrue(CLIP.frame(.66f).up()<-.1,"crouched as she rides");
        for(float t=1.8f;t<2.2f;t+=.1f){
            var p=dev.sangwoo.celestialfall.client.kageno.R6.poseOf(CLIP.frame(t));
            Vec3d hand=dev.sangwoo.celestialfall.client.kageno.KagenoClips.blade(Vec3d.ZERO,0,p)[0],head=dev.sangwoo.celestialfall.client.kageno.KagenoClips.head(Vec3d.ZERO,0,p);
            assertTrue(hand.y>head.y-.35&&hand.z>head.z,"her hand up before her face at "+t+": "+hand.subtract(head));
        }
    }
    @Test void everySwordOfTheFormationIsPulledOutAsItStoodNearestFirst(){
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        assertEquals(IsosangRules.FORMATION_SWORDS,plans.length,"the formation's own (2.101.1: 28 in the band, none of light)");
        var layout=SwordFormation.layout(FORM);float formT=(c.start()-r.formStart())/20f;
        double lastD=-1;float lastPull=-1;
        java.util.List<Integer> order=new java.util.ArrayList<>();for(int k=0;k<SwordFinale.OWN;k++)order.add(k);
        order.sort(java.util.Comparator.comparingDouble(k->plans[k].start()));
        for(int k:order){
            var p=plans[k];assertTrue(p.own());
            if(k==r.on())assertEquals(IsosangRules.FINALE_LEAP+.02f,p.start(),1e-6f,"the one she stood on as she leaps off it");
            else assertTrue(p.start()>=SwordFinale.PULL0-1e-6&&p.start()<=SwordFinale.PULL0+SwordFinale.PULL_SPREAD+.03f,"pulled out once she is up, as the swords swirl: "+p.start());
            // as it stood: its pommel, its way down and its guard's turn the formation's at the moment it is pulled out
            var b=layout[k];
            assertTrue(SwordFormation.planted(CENTRE,FORM_YAW,b).distanceTo(p.planted())<1e-9);
            assertEquals(SwordFormation.spinAt(b,formT+p.start()),p.spin(),1e-6);
            var first=SwordFinale.blade(fr,p,p.start());
            assertTrue(first.pommel().distanceTo(p.planted())<1e-6,"first drawn where it stood");
            assertTrue(first.dir().distanceTo(SwordFormation.down(b))<1e-6,"pointing down as it stood");
            assertNull(SwordFinale.blade(fr,p,p.start()-.01f),"the formation's until then");
            // nearest first, the one she stood on aside
            if(k!=r.on()){double d=SwordFormation.foot(CENTRE,FORM_YAW,b).distanceTo(fr.spot());assertTrue(d>=lastD-1e-6,"nearest first");lastD=d;lastPull=p.start();}
        }
        // the formation's own sword k is pulled out then (SwordFormation hides it from then)
        for(int k=0;k<SwordFinale.OWN;k++)assertEquals(plans[k].start(),SwordFinale.plans(c,r,null)[k].start(),1e-9f);
    }
    @Test void theyGatherBehindHerAsWingsAndComeDownSlantingIntoTheBandAheadGrown(){
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        int left=0,right=0,steeds=0,central=0;var rounds=new java.util.ArrayList<Double>();
        Vec3d hub=fr.at(-SwordFinale.HUB_BACK,IsosangRules.FINALE_HOVER+SwordFinale.HUB_UP,0);
        for(var p:plans){
            if(p.steed())steeds++;
            else{
                Vec3d back=p.fanMid().subtract(fr.spot());
                assertTrue(back.dotProduct(fr.f())<-.2,"its place in the fan behind her back");
                assertTrue(back.y>IsosangRules.FINALE_HOVER,"up behind her as she stands in the air");
                if(back.dotProduct(fr.r())<-1)left++;if(back.dotProduct(fr.r())>1)right++;
                assertTrue(p.fanDir().dotProduct(p.fanMid().subtract(hub))>0,"pointing out from her back, as a wing's feathers");
                // its times: gathered (after her lap) before it is launched, from the air
                assertTrue(p.gather()+SwordFinale.GATHER<p.launch());
                assertTrue(p.launch()>=IsosangRules.FINALE_LAUNCH&&p.launch()<IsosangRules.FINALE_PLANT1,"sent from behind her once she sends them, each just before it strikes");
                assertTrue(SwordFinale.mid(fr,p,p.launch()).y-fr.spot().y>1,"from up off the ground");
            }
            // in the band ahead, slanting, its hilt back toward her, grown three times as big, its point in the ground
            Vec3d d=p.foot().subtract(fr.spot());
            assertTrue(IsosangRules.finaleIn(d.dotProduct(fr.f()),d.dotProduct(fr.r()),d.y,0),"it comes down in the band: "+d.dotProduct(fr.f())+" ahead, "+d.dotProduct(fr.r())+" aside");
            double slant=Math.toDegrees(Math.acos(-p.stuckDir().y));
            assertTrue(slant>=90-SwordFinale.SLANT_MAX-1e-6&&slant<84,"slanting: "+slant);
            assertTrue(p.stuckDir().dotProduct(fr.f())>0,"its point on ahead, its hilt back toward her");
            var ends=SwordFinale.stuck(p);
            assertTrue(ends[1].y<p.foot().y,"its point in the ground");
            assertEquals(3*SwordFormation.S_LEN,ends[0].distanceTo(ends[1]),1e-6,"three times as big as it stands");
            assertTrue(ends[0].subtract(fr.spot()).dotProduct(fr.f())>1.2,"its hilt short of where she comes down");
            var b=SwordFinale.blade(fr,p,p.plant()+.3f);assertEquals(SwordFinale.GROW,b.scale(),1e-9);
            // (2.101.3) launched just before it strikes: fast, never up, dipping in; (2.101.4) out of her camera's screen
            float flight=p.plant()-p.launch();
            if(p.steed())assertEquals(SwordFinale.STEED_FLIGHT,flight,1e-6f,"hers flung in as she steps off it");
            else{
                assertTrue(flight>=SwordFinale.FLIGHT-1e-6&&flight<=SwordFinale.FLIGHT+SwordFinale.FLIGHT_SPREAD+1e-6,"fast: "+flight);
                // (2.101.7) aimed in at the band's middle, pulled from where it strikes (AIM_PULL of the way)
                Vec3d mid=SwordFinale.ground(null,fr.at((SwordFinale.BAND_NEAR+SwordFinale.BAND_FAR)/2,0,0),fr.spot().y);
                assertTrue(p.aimDir().distanceTo(p.foot().lerp(mid,SwordFinale.AIM_PULL).subtract(p.readyMid()).normalize())<1e-9,"aimed in at the band's middle from where it strikes");
                // (2.101.6) it waits gathered round the middle of her camera's screen, pointing in on the band;
                // (2.101.7) only its blade in its frame: its hilt out past the edge (or behind the lens) all the time it
                // waits and is drawn back (her camera coming on and turning down after her), its blade from the edge in
                // to its point, never through the lens
                var s=SwordFinale.shot(c,r,SwordFinale.FOLD1);
                double[] point=screen(s,p.readyMid().add(p.aimDir().multiply(SwordFormation.S_LEN/2))),strikes=screen(s,p.foot());
                assertTrue(Math.abs(point[0])<.8&&Math.abs(point[1])<.8,"its point in the middle of it: "+point[0]+" "+point[1]);
                double round=Math.toDegrees(Math.atan2(point[1]-strikes[1],(point[0]-strikes[0])*16/9));rounds.add(round<0?round+360:round);
                double edge=-1,shown=0;double[] before=null;
                for(int i=0;i<=200;i++){
                    Vec3d q=p.readyMid().add(p.aimDir().multiply(SwordFormation.S_LEN*(i/200.0-.5)));double[] a=screen(s,q);
                    boolean in=a[2]>.05&&Math.abs(a[0])<=1&&Math.abs(a[1])<=1;
                    if(in&&edge<0)edge=a[2];if(in&&before!=null)shown+=Math.hypot((a[0]-before[0])*16/9,a[1]-before[1]);before=in?a:null;
                }
                assertTrue(edge>SwordFinale.EDGE_NEAR-.05&&edge<SwordFinale.EDGE_FAR+.1,"its blade into the frame right by the lens: "+edge);
                assertTrue(shown>.45,"a long blade in the frame: "+shown+" of its height");
                // (2.101.7: "카메라에 검이 반정도만 들어고") half of it in the frame, its middle on the edge
                int in=0;for(int i=0;i<=100;i++){double[] a=screen(s,p.readyMid().add(p.aimDir().multiply(SwordFormation.S_LEN*(i/100.0-.5))));if(a[2]>.05&&Math.abs(a[0])<=1&&Math.abs(a[1])<=1)in++;}
                assertTrue(in>=35&&in<=62,"half of it in the frame: "+in+"%");
                if(Math.abs(point[0])<.45&&Math.abs(point[1])<.45)central++;
                for(float t=SwordFinale.FOLD1;t<p.launch();t+=1/120f){
                    var st=SwordFinale.shot(c,r,t);var w=SwordFinale.blade(fr,p,t);
                    double[] tip=screen(st,w.point());
                    assertTrue(tip[2]>.05&&Math.abs(tip[0])<1&&Math.abs(tip[1])<1,"its point in the frame at "+t);
                    Vec3d hilt=w.point().subtract(w.dir().multiply(SwordFinale.HILT)),ax=w.dir().crossProduct(new Vec3d(0,1,0)).normalize(),ay=ax.crossProduct(w.dir());
                    for(int i=0;i<=6;i++)assertTrue(outOf(st,hilt.subtract(w.dir().multiply((SwordFormation.S_LEN-SwordFinale.HILT)*i/6))),"its hilt out of the frame at "+t);
                    for(int i=0;i<8;i++){double th=i*Math.PI/4;assertTrue(outOf(st,hilt.add(ax.multiply(.22*Math.cos(th))).add(ay.multiply(.22*Math.sin(th)))),"its guard out of the frame at "+t);}
                    // (they may cover her: 2.101.7, user request: "가려도 된다고 카메라를")
                    assertTrue(SwordFinale.toSegment(st.eye(),w.pommel(),w.point())>.08,"never through the lens: "+SwordFinale.toSegment(st.eye(),w.pommel(),w.point())+" at "+t);
                }
                Vec3d wait=p.readyMid().subtract(fr.spot());
                assertTrue(wait.dotProduct(fr.f())<-2&&wait.y>1,"behind her, off the ground: "+wait.dotProduct(fr.f())+" "+wait.y);
                // and shoots out of it into the scene: its foot in her camera's frame, on away from it
                double[] foot=screen(s,p.foot());
                assertTrue(Math.abs(foot[0])<.9&&Math.abs(foot[1])<.9,"where it strikes in the frame: "+foot[0]+" "+foot[1]);
                Vec3d eye=s.eye();double last=-1;
                for(int i=0;i<=40;i++){double away=SwordFinale.flightTip(fr,p,i/40.0).distanceTo(eye);assertTrue(away>=last-1e-6,"on away from her camera");last=away;}
            }
            assertTrue(SwordFinale.flight(fr,p,0).distanceTo(SwordFinale.drawn(fr,p))<1e-9&&SwordFinale.flight(fr,p,1).distanceTo(p.stuckMid().add(p.stuckDir().multiply(SwordFinale.DRIVE)))<1e-9,"from where it was drawn back to driven in");
            assertTrue(SwordFinale.flightDir(fr,p,0).distanceTo(p.aimDir())<1e-9&&SwordFinale.flightDir(fr,p,1).distanceTo(p.stuckDir())<1e-9,"shot off the way it was aimed, in the way it stands");
            double came=Math.toDegrees(Math.asin(-p.aimDir().y)),stood=Math.toDegrees(Math.asin(-p.stuckDir().y));
            assertTrue(came>4,"aimed down: "+came);assertTrue(stood>=came-1e-6&&stood<=came+SwordFinale.STEEPER+SwordFinale.SLANT_JITTER+1e-6,"dipping in a little steeper than it came: "+came+" "+stood);
            double lastY=1e9;
            double lastTip=1e9;
            for(float t=p.launch();t<=p.plant();t+=.002f){
                Vec3d q=SwordFinale.mid(fr,p,t);assertTrue(q.y<=lastY+1e-9,"never up as it flies: "+t);lastY=q.y;
                Vec3d tip=SwordFinale.tip(fr,p,t);assertTrue(tip.y<=lastTip+1e-9,"its point never up: "+t);lastTip=tip.y;
                assertTrue(SwordFinale.flightDir(fr,p,SwordFinale.along(p,t)).y<0,"always going down: "+t);
                assertTrue(SwordFinale.blade(fr,p,t).dir().distanceTo(SwordFinale.flightDir(fr,p,SwordFinale.along(p,t)))<1e-9,"pointing the way it goes");
            }
            assertTrue(p.plant()>=IsosangRules.FINALE_PLANT0&&p.plant()<=IsosangRules.FINALE_PLANT1+1e-6);
            assertTrue(p.vanish()>=IsosangRules.FINALE_GONE0&&p.vanish()+SwordFinale.FADE<=IsosangRules.FINALE_GONE1+.05f);
            assertNull(SwordFinale.blade(fr,p,p.vanish()+SwordFinale.FADE+.01f),"gone");
            // growing as it flies: its size from the fan's to three times it, never a jump (2.101.3: in a few frames)
            assertEquals(1,SwordFinale.scale(p,p.launch()),1e-9,"as it was until it is launched");
            double prev=1;
            for(float t=p.launch()-.05f;t<p.plant()+.05f;t+=1/240f){double sc=SwordFinale.scale(p,t);assertTrue(sc>=prev-1e-9&&sc-prev<.4,"growing smoothly: "+prev+" to "+sc);prev=sc;}
        }
        assertEquals(1,steeds,"one sword under her feet: the one she stood on");
        // (2.101.6, user request: "그냥 검을 중앙 부분에 모아줘 중앙이 비잖아 원본 영상도 중앙에서 나갈걸?") gathered in the
        // middle of her camera's screen, pointing in on the band from its left, over it, its right and under it
        int[] sides=new int[4];for(double a:rounds)sides[(int)(((a+45)%360)/90)]++;
        assertTrue(sides[0]>=4&&sides[1]>=4&&sides[2]>=4&&sides[3]>=4,"in on the band from every side (right, over, left, under): "+sides[0]+" "+sides[1]+" "+sides[2]+" "+sides[3]);
        // (2.101.7: "그리고 중앙은 아직도 비어있네") many points in the middle of the frame, and she big in it, her camera near her
        assertTrue(central>=12,"points in the middle of her camera's frame: "+central);
        var s0=SwordFinale.shot(c,r,SwordFinale.FOLD1);Vec3d feet0=SwordFinale.body(fr,SwordFinale.FOLD1);
        double tall=screen(s0,feet0.add(0,1.9,0))[1]-screen(s0,feet0)[1];
        assertTrue(tall>.44&&tall<.62,"she a quarter of its frame high or so (the video's): "+tall/2);
        assertTrue(left>8&&right>8,"spread to both sides like wings: "+left+" "+right);
        // (2.101.3) as she sends them the fan folds: each turned from a feather to point the way it shoots, waiting
        for(var p:plans){
            if(p.steed())continue;
            assertTrue(SwordFinale.blade(fr,p,SwordFinale.FOLD0-.01f).dir().distanceTo(p.fanDir())<1e-9,"a feather until the fan folds");
            assertTrue(SwordFinale.aimFrom(p)>=SwordFinale.FOLD1-1e-6,"drawn back only once it waits");
            var w=SwordFinale.blade(fr,p,SwordFinale.FOLD1+.005f);
            assertTrue(w.dir().distanceTo(p.aimDir())<1e-9,"waiting, pointed the way it shoots");
            assertTrue(w.mid().distanceTo(p.readyMid())<.08,"where it waits: "+w.mid().distanceTo(p.readyMid()));
            assertEquals(1,w.scale(),1e-9,"as big as it was until it shoots");
        }
        // (2.101.7) folded as she sends them, waiting by her camera as it cuts behind her (only their blades in its frame from its first frame)
        assertTrue(SwordFinale.FOLD0>=IsosangRules.FINALE_SEAL1-.1f&&SwordFinale.FOLD1<=IsosangRules.FINALE_LAUNCH+1e-6,"folded as she sends them, by her camera's cut behind her");
    }
    @Test void herSwordComesUpUnderHerFeetAndCarriesHerRoundUntilSheStepsOff(){
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        SwordFinale.Plan steed=null;for(var p:plans)if(p.steed())steed=p;
        assertNotNull(steed);assertEquals(r.on(),steed.k(),"the one she stood on");
        assertEquals(IsosangRules.FINALE_DESCEND+.06f,steed.launch(),1e-6f,"flung down into the band as she steps off it");
        for(float t=IsosangRules.FINALE_RIDE;t<IsosangRules.FINALE_DESCEND;t+=1/60f){
            var b=SwordFinale.blade(fr,steed,t);Vec3d feet=SwordFinale.body(fr,t);
            assertTrue(b.mid().distanceTo(feet)<.3,"under her feet at "+t+": "+b.mid().distanceTo(feet));
            assertTrue(b.mid().y<feet.y,"its flat under her soles");
            assertTrue(Math.abs(b.dir().y)<.16,"lying along under her: "+b.dir().y);
            assertTrue(b.dir().dotProduct(SwordFinale.heading(fr,t))>.98,"pointing the way she goes");
            assertEquals(1,b.scale(),1e-9);
        }
        // up off the ground after her as she leaps: from where it stood to under her feet, never a jump
        Vec3d prev=null;
        for(float t=steed.start();t<=IsosangRules.FINALE_RIDE;t+=1/60f){
            var b=SwordFinale.blade(fr,steed,t);
            if(prev!=null)assertTrue(b.mid().distanceTo(prev)<1.2,"no jump at "+t);
            prev=b.mid();
        }
    }
    @Test void noSwordJumpsFromOneFrameToTheNext(){
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        for(var p:plans){
            SwordFinale.Blade prev=null;float prevT=0;
            for(float t=p.start();t<p.vanish()+SwordFinale.FADE;t+=1/60f){
                var b=SwordFinale.blade(fr,p,t);if(b==null){prev=null;continue;}
                if(prev!=null){
                    double moved=b.mid().distanceTo(prev.mid());
                    // (2.101.3) launched, it shoots in along its line, many blocks a frame: on along it, never back or off it
                    if(prevT>=p.launch()&&t<=p.plant()){
                        // (2.101.3) launched, it shoots in many blocks a frame: on the way it points, never back or sideways
                        Vec3d step=b.mid().subtract(prev.mid());
                        double off=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,step.normalize().dotProduct(prev.dir())))));
                        assertTrue(step.length()<1e-6||off<25,"sword "+p.k()+" on the way it points at "+t+": "+off);
                    }else if(prevT<p.plant()&&t>p.plant())assertTrue(moved<8,"sword "+p.k()+" strikes "+moved+" blocks in a frame at "+t);
                    else if(prevT<p.launch()&&t>p.launch())assertTrue(moved<3,"sword "+p.k()+" leaves "+moved+" blocks in a frame at "+t);
                    // (2.101.4) the fan folding: each sweeps from her back to wait round her camera behind her
                    else if(t>SwordFinale.FOLD0&&prevT<SwordFinale.FOLD1&&!p.steed())assertTrue(moved<2.6,"sword "+p.k()+" sweeps "+moved+" blocks in a frame at "+t);
                    else assertTrue(moved<1.6,"sword "+p.k()+" moves "+moved+" blocks in a frame at "+t);
                    double turned=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,b.dir().dotProduct(prev.dir())))));
                    assertTrue(turned<70,"sword "+p.k()+" turns "+turned+" deg in a frame at "+t);
                }
                prev=b;prevT=t;
            }
        }
    }
    @Test void herCameraIsInFrontOfHerThenBehindHerNeverInsideHerNorJumpingButAtItsCut(){
        var c=cast();var r=run();var fr=SwordFinale.frame(c,r);
        SwordFinale.Shot prev=null;
        for(float t=IsosangRules.FINALE_FLASH;t<IsosangRules.FINALE_BACK0;t+=1/60f){
            var s=SwordFinale.shot(c,r,t);Vec3d chest=SwordFinale.chest(fr,t);
            assertTrue(s.eye().distanceTo(chest)>1.3,"not inside her at "+t+": "+s.eye().distanceTo(chest));
            if(t>=SwordFinale.CLOSE0&&t<IsosangRules.FINALE_LAUNCH){
                assertTrue(s.eye().subtract(fr.spot()).dotProduct(fr.f())>1.5,"close in front of her as she raises her fingers");
                assertTrue(s.eye().distanceTo(chest)<3,"close");
            }
            if(t>=IsosangRules.FINALE_LAUNCH)assertTrue(s.eye().subtract(fr.spot()).dotProduct(fr.f())<-3.5,"behind her as they fly and come down");
            // (2.101.7) near her, she in its frame as she comes down and lands
            if(t>=IsosangRules.FINALE_LAUNCH){
                if(t<IsosangRules.FINALE_DESCEND)assertTrue(s.eye().distanceTo(chest)<5.5,"near her up there: "+s.eye().distanceTo(chest));
                double[] feet=screen(s,SwordFinale.body(fr,t)),head=screen(s,SwordFinale.body(fr,t).add(0,1.9,0));
                assertTrue(feet[1]>-.75&&head[1]<.5&&Math.abs(feet[0])<.5,"she in its frame at "+t+": "+feet[1]+" "+head[1]);
            }
            if(prev!=null&&!(t-1/60f<IsosangRules.FINALE_LAUNCH&&t>=IsosangRules.FINALE_LAUNCH))
                assertTrue(s.eye().distanceTo(prev.eye())<.6,"no jump at "+t+": "+s.eye().distanceTo(prev.eye()));
            prev=s;
        }
    }
}
