package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.stand.StandBeatdown.*;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.*;

/**
 * The light of Star Platinum's beatdown's cutscene (2.64.0, user request: "찰지게 때리고 날려야해"), on the effect layer
 * with the punches' (KagenoRenderer draws it), in their colours, nothing over the whole screen (as the punches since
 * 2.56.0): as it bursts out of its user at "Star Platinum!" a ring of light runs out over the ground and light gathers
 * round it; a burst where it grips the collar; the uppercut's burst under the chin, rings racing up and light streaking
 * up after them; the rush's bursts all over the one struck, a blow every tenth of a second, the air streaming at them
 * and a pressure ring every few blows, all through the line (2.65.0: no big ORAs between); the last blow, on its "DA!",
 * the heavy punch's finish (the light gathered into the cocked fist, the dome, rings racing on, a lance of light,
 * sparks), a trail of light after the one struck as they fly, and where they come down a burst of dust, a ring along
 * the ground and the floor's chunks thrown up. Each of them lights the world round it. All of it follows the bodies as
 * the cutscene shows them.
 */
public final class StandBeatdownFx {
    /** A cutscene near the camera this frame: its clock, where the one struck's chest is shown, and the stand's body. */
    record Shown(Beat b,float t,Vec3d chest,StandPresence.Body stand){
        int seed(){return b.seed;}
    }
    private static final List<Shown> FRAME=new ArrayList<>();
    private StandBeatdownFx(){}

    /** Gathers this frame's cutscenes within 96 blocks of the camera; returns whether any show. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        FRAME.clear();
        if(mc.world==null)return false;
        for(var b:StandBeatdown.all()){
            float t=b.age(delta);
            if(t<CUT||t>b.over()+1.6f||b.from.squaredDistanceTo(camera)>96*96)continue;
            Body v=t<FINAL?victim(b,t):flight(b,Math.min(t,END));
            FRAME.add(new Shown(b,t,v.feet().add(0,1.15,0),StandPresence.body(b.user)));
        }
        return !FRAME.isEmpty();
    }
    // ───────────────────────────── where ─────────────────────────────

    /** Where the one struck is struck by the rush's blow {@code k}: a spot on their front, a different one each blow. */
    static Vec3d spot(Shown s,int k){
        var b=s.b;int seed=b.seed+k*7919;
        double rt=(Ease.hash(seed+5)-.5)*.7,up=(Ease.hash(seed+6)-.55)*.9;
        // where they are as that blow lands (they are driven back with every one)
        Body v=victim(b,blowAt(k));
        return v.feet().add(0,1.15+up,0).subtract(b.f.multiply(.32)).add(b.r.multiply(rt));
    }
    /** The one struck's chest as each big blow lands: {@code at} on the clock. */
    static Vec3d chestAt(Beat b,float at){return victim(b,Math.min(at,FINAL-1e-4f)).feet().add(0,1.15,0);}

    // ───────────────────────────── the passes ─────────────────────────────

    public static void alpha(EffectMesh m){
        for(var s:FRAME){
            float t=s.t;var b=s.b;
            // ink under each burst, a little bigger, for its dark edge
            for(int i=0;i<STRIKES.length;i++){
                float at=STRIKES[i],a=burstAlpha(t,at,i==STRIKES.length-1?.38f:.24f);if(a<=0)continue;
                Vec3d c=chestAt(b,at).subtract(b.f.multiply(.3)).add(0,i==0?.45:0,0);
                burst(m,c,size(i)*1.14,.36,12,b.seed+i*31,INK,INK,.78f*a,.78f*a);
            }
            float g=burstAlpha(t,GRAB,.2f);
            if(g>0)burst(m,chestAt(b,GRAB).add(0,.32,0).subtract(b.f.multiply(.3)),.9*1.14,.36,9,b.seed+7,INK,INK,.7f*g,.7f*g);
            int n=blows(t);
            for(int k=Math.max(0,n-3);k<n;k++){
                float a=burstAlpha(t,blowAt(k),.1f);if(a<=0)continue;
                burst(m,spot(s,k),.55*1.14,.36,8,b.seed+k*13,INK,INK,.6f*a,.6f*a);
            }
        }
    }
    public static void additive(EffectMesh m){
        for(var s:FRAME){
            float t=s.t;var b=s.b;Vec3d f=b.f,r=b.r;
            reveal(m,s,t);
            // the grab: a burst where its hand grips the collar
            float g=burstAlpha(t,GRAB,.2f);
            if(g>0){Vec3d c=chestAt(b,GRAB).add(0,.32,0).subtract(f.multiply(.3));burst(m,c,.9,.36,9,b.seed+7,LILAC,VIOLET,.85f*g,.8f*g);m.star(c,.45,WHITE,.45f*g);
                float u=Ease.unit(t,GRAB,GRAB+.18f);if(u<1)FxLib.ringPlane(m,c,r,UP,.2+.9*Ease.out(t,GRAB,GRAB+.16f),.12,VIOLET,.8f*(1-u));}
            uppercut(m,s,t);
            rush(m,s,t);
            finish(m,s,t);
        }
    }
    /** How big the burst of strike {@code i} is (the uppercut, the last). */
    static double size(int i){return i==0?1.9:2.6;}
    /** A burst's strength {@code t}: in over a frame as its blow lands at {@code at}, gone {@code life} seconds later. */
    static float burstAlpha(float t,float at,float life){
        if(t<at-.01f||t>at+life)return 0;
        return Ease.smooth(t,at-.006f,at+.012f)*(float)Math.pow(1-Ease.unit(t,at,at+life),.7);
    }
    /** "Star Platinum!": a ring of light runs out over the ground from its user and light gathers round them as the stand bursts out. */
    static void reveal(EffectMesh m,Shown s,float t){
        var b=s.b;float u=Ease.unit(t,REVEAL,REVEAL+.55f);if(t<REVEAL-.3f||u>=1)return;
        Vec3d o=b.from.add(0,.06,0);
        if(t>=REVEAL){
            FxLib.ring(m,o,.4+4.5*Ease.out(t,REVEAL,REVEAL+.5f),.45,VIOLET,.7f*(1-u));
            float u2=Ease.unit(t,REVEAL+.06f,REVEAL+.6f);if(t>=REVEAL+.06f&&u2<1)FxLib.ring(m,o,.3+3.2*Ease.out(t,REVEAL+.06f,REVEAL+.55f),.3,LILAC,.55f*(1-u2));
            m.softDisc(b.from.add(0,1.2,0),1.6*(1-u)+.2,VIOLET,.3f*(1-u));
        }
        // the air drawn in to them a moment before
        FxLib.converge(m,b.from.add(0,1.1,0),2.6,22,t,REVEAL-.3f,REVEAL,LILAC,b.seed+3);
    }
    /** The uppercut: its burst under the chin, rings racing up after the one struck and light streaking up. */
    static void uppercut(EffectMesh m,Shown s,float t){
        var b=s.b;float a=burstAlpha(t,UPPER,.3f);if(t<UPPER-.01f||t>UPPER+.6f)return;
        Vec3d c=chestAt(b,UPPER).add(0,.45,0).subtract(b.f.multiply(.3));
        if(a>0){burst(m,c,size(0),.36,12,b.seed,LILAC,VIOLET,.9f*a,.75f*a);m.star(c,.9,WHITE,.5f*a);m.softDisc(c,1.4,BLUE,.35f*a);}
        // rings racing up after them, flat (the uppercut drives straight up)
        for(int k=0;k<4;k++){
            float at=UPPER+.03f*k,u=Ease.unit(t,at,at+.3f);if(t<at||u>=1)continue;
            Vec3d o=c.add(0,.3+1.2*k*Ease.out(t,at,at+.3f)+.6*Ease.out(t,at,at+.3f),0);
            FxLib.ring(m,o,.35+(.8+.35*k)*Ease.out(t,at,at+.25f),.18,k%2==0?VIOLET:LILAC,.85f*(1-u)*Ease.smooth(t,at,at+.015f));
        }
        // light streaking straight up
        float sl=Ease.smooth(t,UPPER-.01f,UPPER+.01f)*(1-Ease.smooth(t,UPPER+.1f,UPPER+.4f));
        if(sl>0)for(int k=0;k<18;k++){
            double ang=Ease.hash(b.seed+k*5)*Math.PI*2,rad=.15+.55*Ease.hash(b.seed+k*7);
            Vec3d q=c.add(b.f.multiply(Math.cos(ang)*rad)).add(b.r.multiply(Math.sin(ang)*rad)).add(0,.2+1.6*Ease.hash(b.seed+k*11)*Ease.out(t,UPPER,UPPER+.2f),0);
            m.needle(q,q.add(0,.6+.9*Ease.hash(b.seed+k*13),0),.06,.35,k%4==0?WHITE:k%2==0?VIOLET:LILAC,sl);
        }
        sparks(m,c,UP,b.r,b.f.multiply(-1),t,UPPER,1.2,b.seed+70,24);
    }
    /** The rush: a burst for each blow on the one struck, the air streaming at them from the stand, a pressure ring every few blows, a glow on them. */
    static void rush(EffectMesh m,Shown s,float t){
        var b=s.b;if(t<RUSH_FROM-.05f||t>RUSH_TO+.4f)return;
        int n=blows(t);
        for(int k=Math.max(0,n-3);k<n;k++){
            float at=blowAt(k),a=burstAlpha(t,at,.1f);if(a<=0)continue;
            Vec3d c=spot(s,k);
            burst(m,c,.55,.36,8,b.seed+k*13,k%4==0?LILAC:VIOLET,VIOLET,.85f*a,.7f*a);
            if(k%3==0)m.star(c,.3,WHITE,.4f*a);
            float u=Ease.unit(t,at,at+.1f);
            FxLib.ringPlane(m,c.subtract(b.f.multiply(.1)),b.r,UP,.15+.6*Ease.out(t,at,at+.09f),.08,BLUE,.7f*(1-u));
        }
        // a pressure ring every fourth blow, racing on past them
        for(int k=Math.max(0,n-8);k<n;k++){
            if(k%4!=0)continue;
            float at=blowAt(k),u=Ease.unit(t,at,at+.3f);if(u>=1)continue;
            Vec3d o=spot(s,k).add(b.f.multiply(.3+1.8*Ease.out(t,at,at+.3f)));
            FxLib.ringPlane(m,o,b.r,UP,.5+1.2*Ease.out(t,at,at+.25f),.14,LILAC,.6f*(1-u)*Ease.smooth(t,at,at+.02f));
        }
        float on=Ease.smooth(t,RUSH_FROM-.03f,RUSH_FROM+.05f)*(1-Ease.smooth(t,RUSH_TO,RUSH_TO+.25f));
        if(on<=0)return;
        // the air streaming at them, past them
        Vec3d chest=s.chest;
        for(int k=0;k<14;k++){
            double ph=(t*6.5+Ease.hash(b.seed+k*3))%1;
            double rt=(Ease.hash(b.seed+k*5)-.5)*1.6,up=(Ease.hash(b.seed+k*7)-.5)*1.3;
            Vec3d q=chest.subtract(b.f.multiply(1.6-2.6*ph)).add(b.r.multiply(rt)).add(0,up,0);
            m.needle(q,q.add(b.f.multiply(.5+.4*Ease.hash(b.seed+k*11))),.04,.4,k%3==0?WHITE:LILAC,.55f*on*(float)Math.sin(Math.PI*ph));
        }
        m.softDisc(chest,1.2,VIOLET,.16f*on);
    }
    /** The last blow: the heavy punch's finish (its dome, rings racing on, the lance, sparks), the trail after the one struck as they fly, and where they come down. */
    static void finish(EffectMesh m,Shown s,float t){
        var b=s.b;if(t<FINAL-.45f||t>LAND+1.2f)return;
        Vec3d c=launch(b).feet().add(0,1.15,0).subtract(b.f.multiply(.3)),f=b.f,r=b.r;Vec3d up=UP;
        // light gathering in its cocked fist as it coils
        if(t<FINAL&&s.stand!=null){
            Vec3d fist=StandPunchFx.on(StandPunch.windFist(5),s.stand);
            float a=Ease.smooth(t,FINAL-.4f,FINAL-.3f)*(1-Ease.smooth(t,FINAL-.03f,FINAL+.01f));
            if(a>0){FxLib.converge(m,fist,2.6,34,t,FINAL-.4f,FINAL-.03f,LILAC,b.seed+5);m.softDisc(fist,.6*a+.1,VIOLET,.55f*a);m.softDisc(fist,.25*a,WHITE,.8f*a);}
        }
        if(t<FINAL-.01f)return;
        double g=1.3;float in=Ease.smooth(t,FINAL-.01f,FINAL+.012f);
        float a=burstAlpha(t,FINAL,.38f);
        if(a>0){burst(m,c,size(STRIKES.length-1),.36,13,b.seed+97,VIOLET,VIOLET,.9f*a,.6f*a);m.star(c,1.1,WHITE,.3f*a);}
        float ud=Ease.unit(t,FINAL,FINAL+.4f);
        if(ud<1){Vec3d o=c.add(f.multiply(.6));double rad=.5+3.4*g*Ease.out(t,FINAL,FINAL+.36f);float al=.8f*in*(1-ud);
            FxLib.ringPlane(m,o,r,up,rad,.32*g,LILAC,al);FxLib.ringPlane(m,o,f,r,rad*.92,.26*g,VIOLET,.85f*al);FxLib.ringPlane(m,o,f,up,rad*.88,.24*g,BLUE,.75f*al);
            m.softDisc(o,rad*.9,DEEP,.18f*in*(1-ud));}
        for(int k=0;k<6;k++){
            float at=FINAL+.035f*k,u=Ease.unit(t,at,at+.34f);if(t<at||u>=1)continue;
            Vec3d o=c.add(f.multiply((.4+1.3*k+2.0*Ease.out(t,at,at+.34f))*g));
            FxLib.ringPlane(m,o,r,up,.45+(1.4+.6*k)*g*Ease.out(t,at,at+.28f),(.24+.05*k)*g,k==0?LILAC:k%2==1?VIOLET:BLUE,.9f*(1-u)*Ease.smooth(t,at,at+.015f));
        }
        float lance=Ease.smooth(t,FINAL-.02f,FINAL+.005f)*(1-Ease.smooth(t,FINAL+.06f,FINAL+.22f));
        if(lance>0){Vec3d a0=c.subtract(f.multiply(.5)),a1=c.add(f.multiply(8.5*Ease.out(t,FINAL-.02f,FINAL+.05f)));
            m.softLine(a0,a1,1.9*g,DEEP,.4f*lance);m.needle(a0,a1,.56*g,.25,VIOLET,lance);m.needle(a0,a1.subtract(f.multiply(.5)),.2,.25,WHITE,.6f*lance);}
        sparks(m,c,f,r,up,t,FINAL,2.0,b.seed+131,60);
        // the trail of light after them as they fly (once the blow has held them)
        float fly=Ease.smooth(t,FINAL+FINAL_STOP-.02f,FINAL+FINAL_STOP+.04f)*(1-Ease.smooth(t,LAND-.05f,LAND+.25f));
        if(fly>0){
            Vec3d q=flight(b,Math.min(t,LAND)).feet().add(0,.9,0);
            Vec3d back=flight(b,Math.max(FINAL+FINAL_STOP,Math.min(t,LAND)-.22f)).feet().add(0,.9,0);
            Vec3d d=back.subtract(q);double l=d.length();
            if(l>.2){
                Vec3d end=q.add(d.multiply(Math.min(1,7/l)));
                m.softTaper(q,end,1.5,.08,VIOLET,.72f*fly);m.softTaper(q,end,.5,.02,WHITE,.8f*fly);m.softDisc(q,1.1,BLUE,.35f*fly);
                Vec3d dir=d.multiply(1/l);
                for(int k=0;k<10;k++){
                    double along=Ease.hash(k*7+b.seed)*Math.min(l,7),off=(Ease.hash(k*11+b.seed)-.5)*.8;
                    Vec3d qq=q.add(dir.multiply(along)).add(r.multiply(off)).add(0,(Ease.hash(k*13+b.seed)-.5)*.6,0);
                    m.needle(qq,qq.add(dir.multiply(.5+.4*Ease.hash(k*17))),.05,.3,k%2==0?WHITE:LILAC,.8f*fly);
                }
            }
        }
        // where they come down: a burst of light low on the ground and rings running out along it
        if(t>=LAND-.01f){
            Vec3d at=flight(b,LAND+.02f).feet();Vec3d floor=new Vec3d(at.x,b.land.y+.06,at.z);
            float u=Ease.unit(t,LAND,LAND+.55f),la=Ease.smooth(t,LAND-.01f,LAND+.015f);
            if(u<1){FxLib.ring(m,floor,.5+4.5*Ease.out(t,LAND,LAND+.5f),.5,LILAC,.45f*la*(1-u));
                float u2=Ease.unit(t,LAND+.06f,LAND+.6f);if(t>=LAND+.06f&&u2<1)FxLib.ring(m,floor,.4+3.2*Ease.out(t,LAND+.06f,LAND+.55f),.36,BLUE,.5f*(1-u2));}
            float bd=burstAlpha(t,LAND,.3f);
            if(bd>0)burst(m,floor.add(0,.4,0),1.6,.36,11,b.seed+211,LILAC,VIOLET,.6f*bd,.45f*bd);
        }
    }
    /** Sparks thrown out from {@code p} along {@code go}, falling as they slow. */
    static void sparks(EffectMesh m,Vec3d p,Vec3d go,Vec3d side,Vec3d up,float t,float at,double size,int seed,int n){
        float age=t-at;if(age<0||age>=.45f)return;
        for(int k=0;k<n;k++){
            float life=.18f+.22f*(float)Ease.hash(k*3+seed),u=age/life;if(u>=1)continue;
            double az=(Ease.hash(k*5+seed+3)-.5)*2.6,el=(Ease.hash(k*7+seed+7)-.35)*1.6,speed=(3+5*Ease.hash(k*11+seed+9))*size;
            Vec3d d=go.multiply(Math.cos(el)*Math.cos(az)).add(side.multiply(Math.cos(el)*Math.sin(az))).add(up.multiply(Math.sin(el))).normalize();
            Vec3d q=p.add(d.multiply(speed*life*(1-Math.pow(1-u,2))*.5)).add(0,-2.2*age*age,0);
            float al=(1-u)*Ease.smooth(age,0,.015f);int col=k%4==0?GOLD:k%6==1?WHITE:k%2==0?VIOLET:LILAC;
            m.needle(q.subtract(d.multiply(.16*(1-u)*size+.03)),q,.05*size,.3,col,al);
        }
    }
    /** Dust: thrown up where the last blow lands and where the one struck comes down (the effect layer's anime dust). */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(FRAME.isEmpty()||!FxLayer.available()||!KagenoFire.ready())return;
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        for(var s:FRAME){
            var b=s.b;float t=s.t;
            // under where the last blow lands (the rush drives them back all the way to it)
            Vec3d l=launch(b).feet(),under=new Vec3d(l.x,b.hold.y,l.z);
            if(t>FINAL-.1f&&t<FINAL+1.6f)FxLib.dustRing(under,t,FINAL,.4,3.4,16,1.1,dust,puffs,b.seed+61);
            if(t>LAND-.1f)FxLib.dustRing(new Vec3d(b.land.x,b.land.y,b.land.z).subtract(b.f.multiply(SLIDE)),t,LAND,.35,2.6,14,1.0,dust,puffs,b.seed+71);
            if(t>UPPER-.1f&&t<UPPER+1.4f)FxLib.dustRing(b.hold,t,UPPER,.3,1.8,10,.75,dust,puffs,b.seed+81);
        }
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,puffs,(float)(world.getTime()+MinecraftClient.getInstance().getTickDelta()));
    }
    /** Each blow lights the world round it; the one struck glows as they fly. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            var b=s.b;float t=s.t;
            for(int i=0;i<STRIKES.length;i++){
                float at=STRIKES[i];if(t<at-.01f||t>at+.6f)continue;
                boolean last=i==STRIKES.length-1;
                f.light(chestAt(b,at),0xc8b8ff,last?15:i==0?12:10,(last?4.6f:i==0?3.6f:3.0f)*FxLib.kick(t,at,last?.2f:.14f));
            }
            int n=blows(t);
            if(n>0&&t<RUSH_TO+.3f)f.light(s.chest,0xd0c4ff,8,1.8f*FxLib.kick(t,blowAt(n-1),.08f)+.4f*(1-Ease.smooth(t,RUSH_TO,RUSH_TO+.3f))*Ease.smooth(t,RUSH_FROM,RUSH_FROM+.1f));
            if(t>=REVEAL&&t<REVEAL+.8f)f.light(b.from.add(0,1.2,0),0xb8a4ff,10,2.8f*FxLib.kick(t,REVEAL,.3f));
            float fly=Ease.smooth(t,FINAL+FINAL_STOP-.02f,FINAL+FINAL_STOP+.04f)*(1-Ease.smooth(t,LAND,LAND+.3f));
            if(fly>0)f.light(flight(b,Math.min(t,LAND)).feet().add(0,.9,0),0x9c86ff,7,1.6f*fly);
            if(t>=LAND-.01f&&t<LAND+1.1f){
                // (kept small and quick: the first capture's 10 blocks at 2.6 turned the whole floor round it pink)
                f.light(b.land.add(0,1,0),0xb8b4ff,6,1.3f*FxLib.kick(t,LAND,.16f));
                float heat=.6f*(1-Ease.smooth(t,LAND+.05f,LAND+.55f)),al=Ease.smooth(t,LAND,LAND+.04f)*(1-Ease.smooth(t,LAND+.8f,LAND+1.1f));
                Vec3d floor=new Vec3d(b.land.x,b.land.y,b.land.z).subtract(b.f.multiply(SLIDE*.5));
                if(al>0)f.decal(floor,b.f,1.1+.8*Ease.out(t,LAND,LAND+.2f),.55,heat,al,31.3f);
            }
        }
    }
    /** Chunks of the floor thrown up where the one struck comes down. */
    public static void solids(FxSolids solids){
        for(var s:FRAME){
            var b=s.b;float t=s.t;
            if(t>=LAND&&t<LAND+1.0f)FxLib.chunks(solids,new Vec3d(b.land.x,b.land.y,b.land.z).subtract(b.f.multiply(SLIDE)),t-LAND,16,b.seed+977,1.6,5.5,.15,.45f);
        }
    }
}
