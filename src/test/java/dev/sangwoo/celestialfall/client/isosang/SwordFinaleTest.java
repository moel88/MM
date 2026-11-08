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
        assertEquals(IsosangRules.FORMATION_SWORDS,SwordFinale.OWN,"the formation's own");
        assertEquals(SwordFinale.OWN+SwordFinale.LIGHT,plans.length,"and more of light (2.101.9: \"검들은 더 많게\")");
        assertTrue(SwordFinale.LIGHT>=SwordFinale.OWN,"more swords: "+plans.length);
        for(int k=SwordFinale.OWN;k<plans.length;k++){
            var p=plans[k];assertFalse(p.own());assertFalse(p.steed());
            assertTrue(p.appear()>IsosangRules.FINALE_LAUNCH&&p.start()==p.appear(),"one of light shows in its place once her camera has cut behind her and a sword there has been fired: "+p.appear());
            assertNull(SwordFinale.blade(fr,p,p.start()-.01f),"none before");
            assertTrue(p.vanish()>p.plant()&&p.vanish()<=p.plant()+.1f,"it bursts into light as it strikes");
            assertNull(SwordFinale.blade(fr,p,p.vanish()+SwordFinale.LIGHT_FADE+.01f),"gone: only her own stand in the band (\"28개 정도 박으면 될듯\")");
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
                    assertTrue(p.fanDir().dotProduct(p.fanMid().subtract(hub))>0,"pointing out from her back, as a wing's feathers");
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
                assertTrue(flight>=SwordFinale.FLIGHT-1e-6&&flight<=SwordFinale.FLIGHT+SwordFinale.FLIGHT_SPREAD+1e-6,"fast: "+flight);
                assertTrue(p.aimDir().y<-.05,"aimed down into the band: "+p.aimDir().y);
                // where it strikes in her camera's frame; on away from her camera, never by its lens
                double[] foot=screen(s,p.foot());
                assertTrue(Math.abs(foot[0])<1.05&&Math.abs(foot[1])<1,"where it strikes in the frame: "+foot[0]+" "+foot[1]);
                Vec3d eye=s.eye();
                assertTrue(SwordFinale.flightTip(fr,p,1).distanceTo(eye)>SwordFinale.flightTip(fr,p,0).distanceTo(eye)+2,"on away from her camera");
                for(int i=0;i<=40;i++)assertTrue(SwordFinale.flight(fr,p,i/40.0).distanceTo(eye)>1.5,"never by her camera's lens");
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
        assertTrue(left>8&&right>8,"spread to both sides like wings: "+left+" "+right);
        // (2.101.3) as she sends them the fan folds: each turned from a feather to point the way it shoots, waiting
        for(var p:plans){
            if(p.steed())continue;
            if(p.own())assertTrue(SwordFinale.blade(fr,p,SwordFinale.FOLD0-.01f).dir().distanceTo(p.fanDir())<1e-9,"a feather until the fan folds");
            assertTrue(SwordFinale.aimFrom(p)>=SwordFinale.FOLD1-1e-6,"drawn back only once it waits");
            float waits=Math.max(SwordFinale.FOLD1,p.appear()+SwordFinale.EMERGE)+.005f;
            if(p.launch()<waits+.005f)continue;
            var w=SwordFinale.blade(fr,p,waits);
            assertTrue(w.dir().distanceTo(p.aimDir())<1e-9,"waiting, pointed the way it shoots");
            assertTrue(w.mid().distanceTo(p.readyMid())<.08+(SwordFinale.aimFrom(p)<waits?SwordFinale.DRAW:0),"where it waits: "+w.mid().distanceTo(p.readyMid()));
            assertEquals(1,w.scale(),1e-9,"as big as it was until it shoots");
            assertTrue(Math.abs(MathHelper.wrapDegrees(w.spin()-p.face()))<1e-6,"its flat turned to her camera");
        }
        // folded as she sends them, waiting as her camera cuts behind her
        assertTrue(SwordFinale.FOLD0>=IsosangRules.FINALE_SEAL1-.1f&&SwordFinale.FOLD1<=IsosangRules.FINALE_LAUNCH+1e-6,"folded as she sends them, by her camera's cut behind her");
    }
    @Test void theyWaitWholeInPlacesSpreadEvenlyUnderTheUsersLineAllAimedAtHerFoe(){
        // (2.101.9, user request: "카메라는 검의 진행 방향을 정면에서 바라보지 말고, 진행 방향의 측후방에서 비스듬히 바라보는 3/4
        // 측면 시점으로", "검날의 전체 길이와 옆면이 드러나는 구도로", "가까운 검은 크고 길게, 멀리 있는 검은 작게", "여러 검이 화면의
        // 서로 다른 높이와 깊이에", then "검이 너무 모여있어 내가 보낸 사진처럼 조금만 모여있게", "그냥 저 각도에서 방금 내가 표시한
        // 범위에서 골고루 나오게 바꿔줘" and their answers: under the red line, all of it; every sword aimed at her foe; about 20 at
        // a time, each place filled again as its sword is fired)
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        var s=SwordFinale.shot(c,r,SwordFinale.FOLD1);
        Vec3d foe=SwordFinale.foe(c,fr,null);double[] her=SwordFinale.herBox(fr);
        var places=new java.util.ArrayList<double[]>();var lens=new java.util.ArrayList<Double>();
        for(var p:plans){
            if(p.steed())continue;
            // whole in front of her camera, its blade in its frame under the user's line, a blade and not a dot, clear of her
            assertTrue(SwordFinale.shows(fr,her,p.readyMid(),p.aimDir()),"where it waits it shows whole, clear of her: sword "+p.k());
            Vec3d pommel=p.readyMid().subtract(p.aimDir().multiply(SwordFormation.S_LEN/2)),point=p.readyMid().add(p.aimDir().multiply(SwordFormation.S_LEN/2));
            assertTrue(p.readyMid().y-fr.spot().y>SwordFinale.SLOT_UP-.4&&point.y-fr.spot().y>.4,"off the ground: "+(p.readyMid().y-fr.spot().y));
            // its flat to her camera ("옆면이 드러나는")
            Vec3d[] f=SwordFormation.frame(p.aimDir(),p.face());Vec3d toEye=s.eye().subtract(p.readyMid()).normalize();
            assertTrue(Math.abs(f[2].dotProduct(toEye))>.25,"its flat turned to her camera: "+f[2].dotProduct(toEye));
            // aimed straight at her foe ("B: 모두 적을 겨눔"), unless that would point it up
            double off=Math.toDegrees(Math.acos(MathHelper.clamp(p.aimDir().dotProduct(foe.subtract(p.readyMid()).normalize()),-1,1)));
            assertTrue(off<.3||Math.abs(p.aimDir().y+SwordFinale.AIM_DOWN)<1e-6,"aimed at her foe: "+off);
            if(p.appear()<=SwordFinale.FOLD1+SwordFinale.EMERGE_SPREAD+1e-4){
                // (on her camera's frame as rolled)
                double[] a=SwordFinale.onScreen(fr,p.readyMid()),q0=SwordFinale.onScreen(fr,pommel),q1=SwordFinale.onScreen(fr,point);places.add(a);
                lens.add(Math.hypot((q1[0]-q0[0])*16/9,q1[1]-q0[1])/2);
            }
        }
        // its places: WAVE_SLOTS of them, spread evenly over the frame under the user's line (none close together, none of it far from one)
        assertEquals(SwordFinale.WAVE_SLOTS,places.size(),"about 20 at a time");
        for(int i=0;i<places.size();i++)for(int j=i+1;j<places.size();j++){
            double[] a=places.get(i),b=places.get(j);
            assertTrue(Math.hypot((a[0]-b[0])*16/9,a[1]-b[1])/2>.1,"two places close together: "+a[0]+","+a[1]+" and "+b[0]+","+b[1]);
        }
        double[] f0=SwordFinale.onScreen(fr,foe);int far=0,all=0;
        for(double x=-.9;x<=.9;x+=.05)for(double y=-.85;y<SwordFinale.redLine(x)-.15;y+=.05){
            if(x>her[0]-.05&&x<her[1]+.05&&y>her[2]-.05&&y<her[3]+.05||Math.hypot((x-f0[0])*16/9,y-f0[1])<SwordFinale.FOE_CLEAR+.05)continue;
            double best=9;for(double[] a:places)best=Math.min(best,Math.hypot((a[0]-x)*16/9,a[1]-y)/2);
            all++;if(best>.3)far++;
        }
        assertTrue(far<=all/20,"evenly over all of it: "+far+" of "+all+" far from any");
        assertTrue(places.stream().filter(a->a[0]>0).count()>=4&&places.stream().filter(a->a[0]<-.4).count()>=4,"on both sides of the frame");
        // near ones big and long, far ones small; at different heights and depths
        assertTrue(java.util.Collections.max(lens)>1.6*java.util.Collections.min(lens),"near ones big and long, far ones small: "+lens);
        double up0=9,up1=-9,z0=99,z1=-99;for(double[] a:places){up0=Math.min(up0,a[1]);up1=Math.max(up1,a[1]);z0=Math.min(z0,a[2]);z1=Math.max(z1,a[2]);}
        assertTrue(up1-up0>1&&z1-z0>1.2,"at different heights and depths: "+(up1-up0)+" "+(z1-z0));
        // at no moment more than WAVE_SLOTS waiting, all of them as her camera cuts behind her (her own)
        for(float t=SwordFinale.FOLD1;t<SwordFinale.FOLD1+.6f;t+=1/240f){
            int waiting=0;for(var p:plans)if(!p.steed()&&p.appear()<=t&&t<p.launch())waiting++;
            assertTrue(waiting<=SwordFinale.WAVE_SLOTS,"no more than its places at "+t+": "+waiting);
        }
        for(var p:plans)if(!p.steed()&&p.appear()<=SwordFinale.FOLD1+SwordFinale.EMERGE_SPREAD+1e-4)assertTrue(p.own(),"her own coming out as her camera cuts behind her");
        // each place filled again only once its sword has been fired, a moment after; each waits a moment before it is fired
        for(int k=0;k<SwordFinale.WAVE_SLOTS;k++){
            var in=new java.util.ArrayList<SwordFinale.Plan>();for(var p:plans)if(p.slot()==k)in.add(p);
            in.sort(java.util.Comparator.comparingDouble(SwordFinale.Plan::appear));
            assertTrue(in.size()>=2,"each place filled again: "+k+" "+in.size());
            for(int i=1;i<in.size();i++){
                var a=in.get(i-1);var b=in.get(i);
                assertTrue(b.appear()>=a.launch()+SwordFinale.RELOAD-1e-5,"filled again once its sword is fired: place "+k);
                assertTrue(b.launch()-b.appear()>=SwordFinale.EMERGE+SwordFinale.WAIT_OUT-1e-5,"out through the screen and waiting a moment first: place "+k);
            }
        }
        // no two waiting at once in one another
        for(var a:plans)for(var b:plans){
            if(a.k()>=b.k()||a.steed()||b.steed()||a.launch()<=b.appear()||b.launch()<=a.appear())continue;
            Vec3d a0=a.readyMid().subtract(a.aimDir().multiply(1.1)),a1=a.readyMid().add(a.aimDir().multiply(1.1)),b0=b.readyMid().subtract(b.aimDir().multiply(1.1)),b1=b.readyMid().add(b.aimDir().multiply(1.1));
            double d=9;for(int u=0;u<=10;u++)for(int v=0;v<=10;v++)d=Math.min(d,a0.lerp(a1,u/10.0).distanceTo(b0.lerp(b1,v/10.0)));
            assertTrue(d>.08,"two swords in one another: "+a.k()+" "+b.k()+" "+d);
        }
    }
    @Test void eachComesOutThroughHerCamerasScreenIntoItsPlace(){
        // (2.101.9, user: "검이 화면을 뚫고 나오지 않음"; 2.101.8: "아니 카메라를 뚫고 나와야한다니까?")
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        Vec3d[] cam=SwordFinale.folded(fr);
        for(var p:plans){
            if(p.steed())continue;
            float a=p.appear();
            if(SwordFinale.refills(p)||!p.own())assertNotNull(SwordFinale.blade(fr,p,a+.001f),"drawn as it shows");
            var first=SwordFinale.blade(fr,p,a+.001f);
            assertTrue(first.from()>SwordFormation.S_LEN*.7,"all but its point still behind the screen as it shows: "+first.from());
            // where its blade comes through: on her camera's screen
            for(float t=a+.005f;t<a+SwordFinale.EMERGE;t+=.005f){
                var w=SwordFinale.blade(fr,p,t);if(w.from()<=1e-6)continue;
                Vec3d through=w.pommel().add(w.dir().multiply(w.from()*w.scale())),eye=SwordFinale.behind(fr,t)[0];
                assertEquals(SwordFinale.SCREEN,through.subtract(eye).dotProduct(cam[1]),.02,"cut where her camera's screen is at "+t);
                // along the line from the lens through its place: where it is on her screen, it stays
                double[] q=SwordFinale.onScreen(fr,w.mid()),q1=SwordFinale.onScreen(fr,p.readyMid());
                assertTrue(Math.hypot(q[0]-q1[0],q[1]-q1[1])<.02,"out along the line through its place at "+t);
            }
            var out=SwordFinale.blade(fr,p,a+SwordFinale.EMERGE+.001f);
            assertEquals(0,out.from(),1e-9,"whole once it is out");
            assertTrue(out.mid().distanceTo(p.readyMid())<.05+SwordFinale.BREATHE+SwordFinale.BOB,"in its place once it is out: "+out.mid().distanceTo(p.readyMid()));
        }
        // her camera's screen in its roll: the frame turned BEHIND_ROLL degrees
        assertEquals(-SwordFinale.BEHIND_ROLL,Math.toDegrees(Math.atan2(cam[2].y,cam[3].y)),.5,"its places laid out on the frame as rolled");
    }
    @Test void theyAreFiredAtRandomAsHerCameraCutsBehindHerFast(){
        // (2.101.9, user request: "그냥 검들 바로 쏘게 바꿔 지금 조금 기다리고 쏘는데 그냥 바로 쏴 카메라 뒤로 이동하자마자 그리고
        // 순서대로 나가는거 같은데 무작위로 빠르게 쏘게 검들은 더 많게")
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);var fr=SwordFinale.frame(c,r);
        var s=SwordFinale.shot(c,r,SwordFinale.FOLD1);
        var fired=new java.util.ArrayList<SwordFinale.Plan>();for(var p:plans)if(!p.steed())fired.add(p);
        fired.sort(java.util.Comparator.comparingDouble(SwordFinale.Plan::launch));
        assertEquals(plans.length-1,fired.size(),"every sword but hers fired from its place (more swords: \"검들은 더 많게\")");
        assertEquals(SwordFinale.FIRE0,fired.get(0).launch(),1e-6f,"the first as soon as one has come out through her camera's screen");
        assertTrue(SwordFinale.FIRE0-IsosangRules.FINALE_LAUNCH<.1f,"right as her camera cuts behind her (\"카메라 뒤로 이동하자마자\")");
        int n=fired.size();double[] rank=new double[n],fw=new double[n],deep=new double[n],up=new double[n],across=new double[n];
        for(int i=0;i<n;i++){
            var p=fired.get(i);
            if(i>0)assertTrue(p.launch()-fired.get(i-1).launch()<3*SwordFinale.FIRE_STEP,"one after another, fast: "+i);
            double[] q=screen(s,p.readyMid());
            rank[i]=i;fw[i]=p.foot().subtract(fr.spot()).dotProduct(fr.f());deep[i]=q[2];up[i]=q[1];across[i]=q[0];
        }
        float last=fired.get(n-1).launch();
        assertTrue(last-SwordFinale.FIRE0<.45f,"all fired in under half a second: "+(last-SwordFinale.FIRE0));
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
                    // (2.101.9) coming out through her camera's screen into its place, fast
                    else if(!p.steed()&&t>p.appear()&&prevT<p.appear()+SwordFinale.EMERGE)assertTrue(moved<4,"sword "+p.k()+" comes out "+moved+" blocks in a frame at "+t);
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
