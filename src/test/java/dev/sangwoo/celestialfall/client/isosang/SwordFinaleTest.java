package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.MathHelper;
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
        var on=IsosangLimbs.bend(Anim.joints(Anim.k(0,SwordFormation.H_ON),Anim.k(1,SwordFormation.H_ON)).frame(0),0);
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
            // (2.104.0: over what she stands on then, IsosangLimbs.FINALE: the pommel, her steed coming up under her, the ground)
            double[] f=IsosangSlash.feet(CLIP.frame(t));double g=IsosangLimbs.FINALE.at(t);
            assertTrue(f[0]-g>-.02&&f[1]-g>-.02,"a foot in the ground at "+t+": "+(f[0]-g)+" "+(f[1]-g));
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
        assertEquals(IsosangRules.FORMATION_SWORDS,SwordFinale.OWN,"the formation's own");
        // (2.101.10: "그냥 연사할때 골고루 화면 전체를 채우고 연사하자": those of light fired with her own, bursting into light as
        // they strike; her own stand in the band, as in the video's field)
        assertEquals(SwordFinale.OWN+SwordFinale.LIGHT,plans.length,"her own and those of light");
        for(int k=SwordFinale.OWN;k<plans.length;k++){
            var p=plans[k];assertFalse(p.own());assertFalse(p.steed());
            assertTrue(p.start()==p.appear()&&p.appear()>=SwordFinale.FOLD1-1e-5,"one of light shows as her camera cuts behind her or after: "+p.appear());
            assertNull(SwordFinale.blade(fr,p,p.start()-.01f),"none before");
            assertTrue(p.vanish()>p.plant()&&p.vanish()<=p.plant()+.1f,"it bursts into light as it strikes");
            assertNull(SwordFinale.blade(fr,p,p.vanish()+SwordFinale.LIGHT_FADE+.01f),"gone: only her own stand in the band");
        }
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
        int left=0,right=0,steeds=0;
        Vec3d hub=fr.at(-SwordFinale.HUB_BACK,IsosangRules.FINALE_HOVER+SwordFinale.HUB_UP,0);
        var s=SwordFinale.shot(c,r,SwordFinale.FOLD1);
        for(var p:plans){
            if(p.steed())steeds++;
            else{
                if(p.own()){
                    Vec3d back=p.fanMid().subtract(fr.spot());
                    assertTrue(back.dotProduct(fr.f())<-.2,"its place in the fan behind her back");
                    assertTrue(back.y>IsosangRules.FINALE_HOVER,"up behind her as she stands in the air");
                    if(back.dotProduct(fr.r())<-1)left++;if(back.dotProduct(fr.r())>1)right++;
                    // (2.101.10, user request: "여기서 검이 세워져있으면 안되고 눕히고 칼날 부분이 앞쪽") lying level, its blade
                    // straight ahead, its flat up, its point behind her front
                    assertTrue(p.fanDir().distanceTo(fr.f().normalize())<1e-9,"lying level, its blade straight ahead");
                    Vec3d[] lie=SwordFormation.frame(p.fanDir(),SwordFinale.lying(p));
                    assertTrue(Math.abs(lie[2].y)>.99,"lying flat: "+lie[2].y);
                    assertTrue(p.fanMid().add(p.fanDir().multiply(SwordFormation.S_LEN/2)).subtract(fr.spot()).dotProduct(fr.f())<.05,"its point behind her front");
                    // its times: gathered (after her lap) before it is launched, from the air
                    assertTrue(p.gather()+SwordFinale.GATHER<p.launch());
                }
                assertTrue(p.launch()>=IsosangRules.FINALE_LAUNCH-1e-6&&p.launch()<IsosangRules.FINALE_PLANT1,"fired from her camera's cut, each just before it strikes");
                assertTrue(SwordFinale.mid(fr,p,p.launch()).y-fr.spot().y>1,"from up off the ground");
            }
            // in the band ahead, slanting, its hilt back toward her, grown three times as big, its point in the ground
            Vec3d d=p.foot().subtract(fr.spot());
            assertTrue(IsosangRules.finaleIn(d.dotProduct(fr.f()),d.dotProduct(fr.r()),d.y,0),"it comes down in the band: "+d.dotProduct(fr.f())+" ahead, "+d.dotProduct(fr.r())+" aside");
            double slant=Math.toDegrees(Math.acos(-p.stuckDir().y));
            assertTrue(slant>=90-SwordFinale.SLANT_MAX-1e-6&&slant<86,"slanting: "+slant);
            assertTrue(p.stuckDir().dotProduct(fr.f())>0,"its point on ahead, its hilt back toward her");
            var ends=SwordFinale.stuck(p);
            assertTrue(ends[1].y<p.foot().y,"its point in the ground");
            assertEquals(3*SwordFormation.S_LEN,ends[0].distanceTo(ends[1]),1e-6,"three times as big as it stands");
            assertTrue(ends[0].subtract(fr.spot()).dotProduct(fr.f())>1.2,"its hilt short of where she comes down");
            var b=SwordFinale.blade(fr,p,p.plant()+.03f);assertEquals(SwordFinale.GROW,b.scale(),1e-9);
            // (2.101.3) launched just before it strikes: fast, never up, dipping in
            float flight=p.plant()-p.launch();
            if(p.steed())assertEquals(SwordFinale.STEED_FLIGHT,flight,1e-6f,"hers flung in as she steps off it");
            else{
                assertTrue(flight>=SwordFinale.FLIGHT-1e-6&&flight<=SwordFinale.FLIGHT_MOST+1e-6,"fast: "+flight);
                assertTrue(p.aimDir().y<-.05,"aimed down into the band: "+p.aimDir().y);
                // where it strikes in her camera's frame; on away from her camera, never by its lens
                double[] foot=screen(s,p.foot());
                assertTrue(Math.abs(foot[0])<1.05&&Math.abs(foot[1])<1,"where it strikes in the frame: "+foot[0]+" "+foot[1]);
                Vec3d eye=s.eye();
                assertTrue(SwordFinale.flightTip(fr,p,1).distanceTo(eye)>SwordFinale.flightTip(fr,p,0).distanceTo(eye)+8,"on away from her camera, from behind it");
                // (2.101.10) from behind her camera's lens, never right by it
                assertTrue(SwordFinale.depth(SwordFinale.folded(fr),SwordFinale.tipFrom(fr,p))<0,"shot off from behind her camera's lens");
                for(int i=0;i<=200;i++)assertTrue(SwordFinale.flightTip(fr,p,i/200.0).distanceTo(eye)>SwordFinale.LENS_CLEAR-.05,"never right by her camera's lens");
                Vec3d wait=p.readyMid().subtract(fr.spot());
                assertTrue(wait.y>1,"off the ground: "+wait.y);
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
            float fade=SwordFinale.fade(p);
            if(p.own())assertTrue(p.vanish()>=IsosangRules.FINALE_GONE0&&p.vanish()+fade<=IsosangRules.FINALE_GONE1+.05f);
            assertNull(SwordFinale.blade(fr,p,p.vanish()+fade+.01f),"gone");
            // growing as it flies: its size from the fan's to three times it, never a jump (2.101.3: in a few frames)
            assertEquals(1,SwordFinale.scale(p,p.launch()),1e-9,"as it was until it is launched");
            double prev=1;
            for(float t=p.launch()-.05f;t<p.plant()+.03f;t+=1/240f){double sc=SwordFinale.scale(p,t);assertTrue(sc>=prev-1e-9&&sc-prev<.4,"growing smoothly: "+prev+" to "+sc);prev=sc;}
        }
        assertEquals(1,steeds,"one sword under her feet: the one she stood on");
        var s0=SwordFinale.shot(c,r,SwordFinale.FOLD1);Vec3d feet0=SwordFinale.body(fr,SwordFinale.FOLD1);
        double tall=screen(s0,feet0.add(0,1.9,0))[1]-screen(s0,feet0)[1];
        assertTrue(tall>.44&&tall<.62,"she a quarter of its frame high or so (the video's): "+tall/2);
        assertTrue(left>=7&&right>=7,"spread to both sides like wings: "+left+" "+right);
        // (2.101.3) as she sends them the fan folds: each turned from lying in the fan to the way it shoots, waiting
        for(var p:plans){
            if(p.steed())continue;
            if(p.own()){
                var lying=SwordFinale.blade(fr,p,SwordFinale.FOLD0-.01f);
                assertTrue(lying.dir().distanceTo(p.fanDir())<1e-9,"lying in the fan until it folds");
                assertTrue(Math.abs(MathHelper.wrapDegrees(lying.spin()-SwordFinale.lying(p)))<1e-6,"its flat up in the fan");
            }
            assertTrue(SwordFinale.aimFrom(p)>=SwordFinale.FOLD1-1e-6,"drawn back only once it waits");
            float waits=Math.max(SwordFinale.FOLD1,p.appear())+.005f;
            if(p.launch()<waits+.005f)continue;
            var w=SwordFinale.blade(fr,p,waits);
            assertTrue(w.dir().distanceTo(p.aimDir())<1e-9,"waiting, pointed the way it shoots");
            assertTrue(w.mid().distanceTo(p.readyMid())<.08+(SwordFinale.aimFrom(p)<waits?SwordFinale.DRAW:0),"where it waits: "+w.mid().distanceTo(p.readyMid()));
            assertTrue(SwordFinale.depth(SwordFinale.folded(fr),w.point())<0,"behind her camera's lens");
            assertEquals(1,w.scale(),1e-9,"as big as it was until it shoots");
            assertTrue(Math.abs(MathHelper.wrapDegrees(w.spin()-p.face()))<1e-6,"its flat turned to her camera");
        }
        // folded as she sends them, waiting as her camera cuts behind her
        assertTrue(SwordFinale.FOLD0>=IsosangRules.FINALE_SEAL1-.1f&&SwordFinale.FOLD1<=IsosangRules.FINALE_LAUNCH+1e-6,"folded as she sends them, by her camera's cut behind her");
    }
    /** The blade of sword {@code p} {@code t} seconds in as her own cutscene's camera sees it (cut at its screen as it flies), or null. */
    static SwordFinale.Blade seen(SwordFinale.Frame fr,SwordFinale.Plan p,float t){
        var b=SwordFinale.blade(fr,p,t);if(b==null||p.steed()||t<p.launch()||t>=p.plant())return b;
        return b.cut(SwordFinale.cut(fr,p,b.pommel(),b.dir(),b.scale(),t));
    }
    /** Where the points of {@code b} that are drawn (past its cut) are on shot {@code s}'s screen. */
    static java.util.List<double[]> drawn(SwordFinale.Shot s,SwordFinale.Blade b){
        var out=new java.util.ArrayList<double[]>();double l=SwordFormation.S_LEN;
        for(int i=0;i<=24;i++){
            double along=b.from()+(l-b.from())*i/24.0;
            out.add(screen(s,b.pommel().add(b.dir().multiply(along*b.scale()))));
        }
        return out;
    }
    static boolean inFrame(double[] q){return q[2]>.05&&Math.abs(q[0])<1&&Math.abs(q[1])<1;}
    @Test void theyWaitOutOfHerFrameBehindHerCameraAllStraightAheadAndAreSeenOnlyAsTheyAreFired(){
        // (2.101.10, user request: "너무 징그러워 검이 카메라 뒤에 있어서 쏘는 순간에만 카메라에 나오게 해봐", "그리고 검들도
        // 적을 안 보게 해봐", "그냥 정면만 보게 바꿔"; before it: "그냥 연사할때 골고루 화면 전체를 채우고 연사하자")
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        // framed as in the user's video at 3.55 s: she a little left of and under the middle of the frame
        double[] at=SwordFinale.onScreen(fr,SwordFinale.body(fr,SwordFinale.FOLD1).add(0,.9,0));
        assertTrue(Math.abs(at[0]+.23)<.06&&Math.abs(at[1]+.08)<.06,"she where she is in the video's frame: "+at[0]+" "+at[1]);
        int[] cells=new int[24];int left=0,right=0,top=0,bottom=0,through=0;double lastDip=-1,lastFw=-1;
        var byDepth=new java.util.ArrayList<SwordFinale.Plan>();
        for(var p:plans){
            if(p.steed())continue;
            byDepth.add(p);
            // straight ahead, the way she faces (none turned toward anything), dipping
            Vec3d level=new Vec3d(p.aimDir().x,0,p.aimDir().z).normalize();
            assertTrue(Math.toDegrees(Math.acos(MathHelper.clamp(level.dotProduct(fr.f().normalize()),-1,1)))<.01,"straight ahead: sword "+p.k());
            double dip=Math.toDegrees(Math.asin(-p.aimDir().y));
            assertTrue(dip>=SwordFinale.DIP_NEAR-1e-6&&dip<=SwordFinale.DIP_FAR+1e-6,"dipping: "+dip);
            // waiting: behind her camera's lens, out of its frame, until it is fired
            for(float t=Math.max(SwordFinale.FOLD1,p.appear());t<p.launch();t+=1/240f){
                var b=SwordFinale.blade(fr,p,t);if(b==null)continue;var s=SwordFinale.shot(c,r,t);
                for(double[] q:drawn(s,b))assertTrue(!inFrame(q),"sword "+p.k()+" seen before it is fired at "+t);
            }
            // fired: it comes into her frame as it flies, from an edge or out through her camera's screen
            double[] entry=null;var path=new java.util.HashSet<Integer>();
            for(float t=p.launch();t<p.plant();t+=1/600f){
                var b=seen(fr,p,t);var s=SwordFinale.shot(c,r,t);
                for(double[] q:drawn(s,b)){
                    if(!inFrame(q))continue;
                    if(entry==null)entry=q;
                    path.add(Math.min(3,(int)((1-q[1])/2*4))*6+Math.min(5,(int)((q[0]+1)/2*6)));
                }
            }
            assertNotNull(entry,"sword "+p.k()+" seen as it flies");
            for(int k:path)cells[k]++;
            if(p.throughAt()!=null){through++;
                // out through her camera's screen where its point comes through it
                assertTrue(p.through()>p.launch()&&p.through()<p.plant());
            }else if(entry[0]<=-.9)left++;else if(entry[0]>=.9)right++;else if(entry[1]>=.9)top++;else if(entry[1]<=-.9)bottom++;
            else fail("sword "+p.k()+" shows inside the frame without coming through her camera's screen: "+entry[0]+" "+entry[1]);
            // none through her (as she stands in the air and comes down), none right by her camera's lens
            assertFalse(SwordFinale.shadowed(fr,SwordFinale.folded(fr)[0],p.foot(),p.aimDir()),"sword "+p.k()+"'s line through her or by the lens");
            for(float t=p.launch();t<=p.plant();t+=1/240f){
                Vec3d m=SwordFinale.mid(fr,p,t),d=SwordFinale.flightDir(fr,p,SwordFinale.along(p,t)),feet=SwordFinale.body(fr,t);double sc=SwordFinale.scale(p,t);
                for(int i=0;i<=12;i++){
                    Vec3d q=m.add(d.multiply(SwordFormation.S_LEN*sc*(i/12.0-.5))).subtract(feet);
                    assertFalse(Math.abs(q.dotProduct(fr.r()))<.35&&Math.abs(q.dotProduct(fr.f()))<.35&&q.y>0&&q.y<1.9,"sword "+p.k()+" through her at "+t);
                }
            }
        }
        // the steeper the farther it strikes
        byDepth.sort(java.util.Comparator.comparingDouble(p->p.foot().subtract(fr.spot()).dotProduct(fr.f())));
        for(var p:byDepth){double dip=Math.asin(-p.aimDir().y);assertTrue(dip>=lastDip-1e-9,"steeper the farther");lastDip=dip;}
        // seen all over her frame: in at every edge of it, a few out through its screen, every part of it crossed
        assertTrue(left>=8&&right>=8&&top>=8&&bottom>=6,"in at every edge: "+left+" "+right+" "+top+" "+bottom);
        assertTrue(through>=1,"a few out through her camera's screen: "+through);
        for(int k=0;k<24;k++)assertTrue(cells[k]>=3,"all over her frame: "+java.util.Arrays.toString(cells));
        // where they strike: all over the band (its nine parts), her own too (they stand there)
        int[] all=new int[9],own=new int[9];
        for(var p:plans){if(p.steed())continue;Vec3d d=p.foot().subtract(fr.spot());
            int a=(int)Math.min(2,Math.max(0,(d.dotProduct(fr.f())-SwordFinale.BAND_NEAR)/(SwordFinale.BAND_FAR-SwordFinale.BAND_NEAR)*3)),b=(int)Math.min(2,Math.max(0,(d.dotProduct(fr.r())+SwordFinale.BAND_HALF)/(2*SwordFinale.BAND_HALF)*3));
            all[a*3+b]++;if(p.own())own[a*3+b]++;}
        for(int k=0;k<9;k++){assertTrue(all[k]>=5,"all over the band: "+java.util.Arrays.toString(all));assertTrue(own[k]>=1,"her own all over the band: "+java.util.Arrays.toString(own));}
        // one of light shows where it waits a moment before it is fired
        for(var p:plans)if(!p.own())assertTrue(p.launch()-p.appear()<=SwordFinale.LIGHT_LEAD+1e-5&&p.appear()>=SwordFinale.FOLD1-1e-5,"one of light shows just before it is fired");
        // its flat to her camera as it flies ("옆면이 드러나는")
        var s=SwordFinale.shot(c,r,SwordFinale.FOLD1);
        for(var p:plans){if(p.steed())continue;Vec3d[] f=SwordFormation.frame(p.aimDir(),p.face());Vec3d w=s.eye().subtract(p.foot());Vec3d toEye=w.subtract(p.aimDir().multiply(w.dotProduct(p.aimDir()))).normalize();
            assertTrue(Math.abs(f[2].dotProduct(toEye))>.5,"its flat turned to her camera: "+f[2].dotProduct(toEye));}
    }
    @Test void thoseByHerLensComeOutThroughHerCamerasScreen(){
        // (2.101.9, user: "검이 화면을 뚫고 나오지 않음"; 2.101.8: "아니 카메라를 뚫고 나와야한다니까?")
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        Vec3d[] cam=SwordFinale.folded(fr);int n=0;
        for(var p:plans){
            if(p.steed()||p.throughAt()==null)continue;n++;
            double[] q=SwordFinale.onScreen(fr,p.throughAt());assertTrue(Math.abs(q[0])<1&&Math.abs(q[1])<1,"through her camera's screen in its frame");
            assertEquals(SwordFormation.S_LEN,seen(fr,p,p.through()-.004f).from(),1e-9,"all of it behind the screen until its point comes through");
            for(float t=p.through()+.002f;t<p.plant();t+=.002f){
                var w=seen(fr,p,t);if(w.from()<=1e-6)break;
                Vec3d cut=w.pommel().add(w.dir().multiply(w.from()*w.scale())),eye=SwordFinale.behind(fr,t)[0];
                assertEquals(SwordFinale.SCREEN,cut.subtract(eye).dotProduct(cam[1]),.02,"cut where her camera's screen is at "+t);
            }
        }
        assertTrue(n>=1,"some out through her camera's screen");
        // her camera's screen in its roll: the frame turned BEHIND_ROLL degrees
        assertEquals(-SwordFinale.BEHIND_ROLL,Math.toDegrees(Math.atan2(cam[2].y,cam[3].y)),.5,"laid out on the frame as rolled");
    }
    @Test void theyAreFiredAtRandomAsHerCameraCutsBehindHerFast(){
        // (2.101.9, user request: "그냥 검들 바로 쏘게 바꿔 지금 조금 기다리고 쏘는데 그냥 바로 쏴 카메라 뒤로 이동하자마자 그리고
        // 순서대로 나가는거 같은데 무작위로 빠르게 쏘게 검들은 더 많게")
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        var s=SwordFinale.shot(c,r,SwordFinale.FOLD1);
        var fired=new java.util.ArrayList<SwordFinale.Plan>();for(var p:plans)if(!p.steed())fired.add(p);
        fired.sort(java.util.Comparator.comparingDouble(SwordFinale.Plan::launch));
        assertEquals(plans.length-1,fired.size(),"every sword but hers fired");
        assertEquals(SwordFinale.FIRE0,fired.get(0).launch(),1e-6f,"the first as her camera cuts behind her");
        assertTrue(SwordFinale.FIRE0-IsosangRules.FINALE_LAUNCH<.1f,"right as her camera cuts behind her (\"카메라 뒤로 이동하자마자\")");
        int n=fired.size();double[] rank=new double[n],fw=new double[n],deep=new double[n],up=new double[n],across=new double[n];
        for(int i=0;i<n;i++){
            var p=fired.get(i);
            if(i>0)assertTrue(p.launch()-fired.get(i-1).launch()<3*SwordFinale.FIRE_STEP,"one after another, fast: "+i);
            double[] q=screen(s,p.foot());
            rank[i]=i;fw[i]=p.foot().subtract(fr.spot()).dotProduct(fr.f());deep[i]=q[2];up[i]=p.readyMid().y;across[i]=q[0];
        }
        float last=fired.get(n-1).launch();
        // (the user's video: one about every 0.03 s for some 0.7 to 0.9 s, hers last)
        assertTrue(last-SwordFinale.FIRE0>.55f&&last-SwordFinale.FIRE0<.75f,"all fired in some 0.7 s: "+(last-SwordFinale.FIRE0));
        for(double[] k:new double[][]{fw,deep,up,across})assertTrue(Math.abs(spearman(rank,k))<.35,"at random: "+spearman(rank,k));
        // the server's blows as they come down (three in their rain, one as hers comes down last; the last as the band flashes)
        float firstPlant=Float.MAX_VALUE,lastPlant=0,hers=0;
        for(var p:plans){if(p.steed())hers=p.plant();else{firstPlant=Math.min(firstPlant,p.plant());lastPlant=Math.max(lastPlant,p.plant());}}
        assertTrue(firstPlant>=IsosangRules.FINALE_PLANT0-1e-4f,"none down before FINALE_PLANT0");
        for(int i=0;i<3;i++){float b=IsosangRules.blowAt(IsosangRules.FINALE,i)/20f;assertTrue(b>=firstPlant&&b<=lastPlant,"blow "+i+" in their rain: "+b+" ("+firstPlant+" to "+lastPlant+")");}
        assertTrue(hers>lastPlant,"hers last");
        assertEquals(hers,IsosangRules.blowAt(IsosangRules.FINALE,3)/20f,.05f,"one as hers comes down");
    }
    /** Spearman's rank correlation of {@code a} and {@code b}. */
    static double spearman(double[] a,double[] b){
        double[] ra=ranks(a),rb=ranks(b);int n=a.length;double ma=0,mb=0;for(int i=0;i<n;i++){ma+=ra[i];mb+=rb[i];}ma/=n;mb/=n;
        double sab=0,saa=0,sbb=0;for(int i=0;i<n;i++){sab+=(ra[i]-ma)*(rb[i]-mb);saa+=(ra[i]-ma)*(ra[i]-ma);sbb+=(rb[i]-mb)*(rb[i]-mb);}
        return sab/Math.sqrt(saa*sbb);
    }
    static double[] ranks(double[] a){
        Integer[] o=new Integer[a.length];for(int i=0;i<o.length;i++)o[i]=i;java.util.Arrays.sort(o,java.util.Comparator.comparingDouble(i->a[i]));
        double[] r=new double[a.length];for(int i=0;i<o.length;i++)r[o[i]]=i;return r;
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
            for(float t=p.start();t<p.vanish()+SwordFinale.fade(p);t+=1/60f){
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
                    // (2.101.10) the fan folding: each sweeps back from her back to wait behind her camera
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
            if(t>=IsosangRules.FINALE_LAUNCH)assertTrue(s.eye().subtract(fr.spot()).dotProduct(fr.f())<-3,"behind her as they fly and come down (2.101.9: at a slant, round to her right)");
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
