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
        int left=0,right=0,steeds=0;
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
                assertTrue(SwordFinale.mid(fr,p,p.launch()).y-fr.spot().y>IsosangRules.FINALE_HOVER,"from up there");
            }
            // in the band ahead, slanting, its hilt back toward her, grown three times as big, its point in the ground
            Vec3d d=p.foot().subtract(fr.spot());
            assertTrue(IsosangRules.finaleIn(d.dotProduct(fr.f()),d.dotProduct(fr.r()),d.y,0),"it comes down in the band: "+d.dotProduct(fr.f())+" ahead, "+d.dotProduct(fr.r())+" aside");
            double slant=Math.toDegrees(Math.acos(-p.stuckDir().y));
            assertTrue(slant>=90-SwordFinale.SLANT_MAX-1e-6&&slant<=90-SwordFinale.SLANT_MIN+1e-6,"slanting: "+slant);
            assertTrue(p.stuckDir().dotProduct(fr.f())>0,"its point on ahead, its hilt back toward her");
            var ends=SwordFinale.stuck(p);
            assertTrue(ends[1].y<p.foot().y,"its point in the ground");
            assertEquals(3*SwordFormation.S_LEN,ends[0].distanceTo(ends[1]),1e-6,"three times as big as it stands");
            assertTrue(ends[0].subtract(fr.spot()).dotProduct(fr.f())>1.2,"its hilt short of where she comes down");
            var b=SwordFinale.blade(fr,p,p.plant()+.3f);assertEquals(SwordFinale.GROW,b.scale(),1e-9);
            // (2.101.3) launched just before it strikes: fast, from behind her back past her body, never up, dipping in
            float flight=p.plant()-p.launch();
            if(p.steed())assertEquals(SwordFinale.STEED_FLIGHT,flight,1e-6f,"hers flung in as she steps off it");
            else{
                assertTrue(flight>=SwordFinale.FLIGHT-1e-6&&flight<=SwordFinale.FLIGHT+SwordFinale.FLIGHT_SPREAD+1e-6,"fast: "+flight);
                Vec3d wait=p.readyMid().subtract(fr.spot());
                assertTrue(wait.dotProduct(fr.f())<-1.8,"it waits behind her back: "+wait.dotProduct(fr.f()));
                assertTrue(wait.y>IsosangRules.FINALE_HOVER+.8&&wait.y<IsosangRules.FINALE_HOVER+3.6,"about her, from her waist to over her head: "+wait.y);
                assertTrue(p.aimDir().distanceTo(p.foot().subtract(p.readyMid()).normalize())<1e-9,"aimed at where it strikes");
                // past her body: by her, or through her for those over her
                double by=99;Vec3d feet=SwordFinale.body(fr,p.launch());
                for(int i=0;i<=400;i++){Vec3d q=SwordFinale.flight(fr,p,i/400.0);double y=Math.max(feet.y,Math.min(feet.y+1.8,q.y));by=Math.min(by,q.distanceTo(new Vec3d(feet.x,y,feet.z)));}
                assertTrue(by<3.6,"it shoots past her body: "+by);
            }
            assertTrue(SwordFinale.flight(fr,p,0).distanceTo(SwordFinale.drawn(fr,p))<1e-9&&SwordFinale.flight(fr,p,1).distanceTo(p.stuckMid().add(p.stuckDir().multiply(SwordFinale.DRIVE)))<1e-9,"from where it was drawn back to driven in");
            assertTrue(SwordFinale.flightDir(fr,p,0).distanceTo(p.aimDir())<1e-9&&SwordFinale.flightDir(fr,p,1).distanceTo(p.stuckDir())<1e-9,"shot off the way it was aimed, in the way it stands");
            double came=Math.toDegrees(Math.asin(-p.aimDir().y)),stood=Math.toDegrees(Math.asin(-p.stuckDir().y));
            assertTrue(came>8,"aimed down: "+came);assertTrue(stood>=came-1e-6,"dipping in steeper than it came: "+came+" "+stood);
            double lastY=1e9;
            for(float t=p.launch();t<=p.plant();t+=.002f){
                Vec3d q=SwordFinale.mid(fr,p,t);assertTrue(q.y<=lastY+1e-9,"never up as it flies: "+t);lastY=q.y;
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
        assertTrue(SwordFinale.FOLD0<=IsosangRules.FINALE_LAUNCH&&SwordFinale.FOLD1>IsosangRules.FINALE_LAUNCH,"folding as she sends them, her camera cut behind her");
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
            if(t>=IsosangRules.FINALE_LAUNCH)assertTrue(s.eye().subtract(fr.spot()).dotProduct(fr.f())<-4,"behind her as they fly and come down");
            if(prev!=null&&!(t-1/60f<IsosangRules.FINALE_LAUNCH&&t>=IsosangRules.FINALE_LAUNCH))
                assertTrue(s.eye().distanceTo(prev.eye())<.6,"no jump at "+t+": "+s.eye().distanceTo(prev.eye()));
            prev=s;
        }
    }
}
