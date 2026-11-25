package dev.sangwoo.celestialfall.boss;

import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.CounterStance;
import dev.sangwoo.celestialfall.xchara.CloneRules;
import dev.sangwoo.celestialfall.xchara.KnivesRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import dev.sangwoo.celestialfall.xchara.SlamRules;
import dev.sangwoo.celestialfall.xchara.XCharaAttack;
import dev.sangwoo.celestialfall.xchara.XCharaClone;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import dev.sangwoo.celestialfall.xchara.XCharaHack;
import dev.sangwoo.celestialfall.xchara.XCharaHeal;
import dev.sangwoo.celestialfall.xchara.XCharaBlink;
import dev.sangwoo.celestialfall.xchara.XCharaPull;
import dev.sangwoo.celestialfall.xchara.XCharaRules;
import dev.sangwoo.celestialfall.xchara.XCharaUltimate;
import dev.sangwoo.celestialfall.xchara.XCharaWeaken;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * X Chara's way of fighting, the boss (2.137.0, user request: "이제 엑스차라 보스를 만들자"; their answers: fighting and
 * all at once, every skill with the hack on and off): the player's own knife and skills (XCharaAttack, XCharaHack,
 * XCharaUltimate, XCharaClone, XCharaWeaken, XCharaPull, XCharaHeal), played from the server. Mine to make up, how it
 * chooses among them:
 * <ul>
 * <li>Whom its blows hold (staggered, incapacitated: its jab's eight seconds, its double's wall, its slam's moment) and
 * who stands within its knife's reach, it cuts: the knife's four hits, the fourth through any guard, sending them
 * off.</li>
 * <li>Otherwise it picks, by chance weighted by how far they stand and what is ready, one of: with the hack off, the
 * jab (close; through a guard), the three cuts (a dash and three), the small knives and the knives shot (from afar),
 * the slam (it leaps and comes down on where they stand as it dives) and the great X slash (through a guard, sealing
 * it); with the hack on, the double, the weakening, the pull (and the knife as they land before it), the terrain
 * delete (under them) and the sword dance. It turns its hack on or off for the one it picked (Q, no oftener than a
 * player may), and rests a moment between skills, circling out of reach.</li>
 * <li>(2.138.0) On one who stands off it closes with its teleport (Z, five blocks the way it looks), the hack on or off.</li>
 * <li>Hurt, with nobody near enough to stop it (or the one it fights held), it turns the hack on and restores
 * itself (its E, 65 at a time).</li>
 * <li>What comes at it it answers as every boss does (BossBrain: out of the way, behind its guard); what is slow to
 * land right before it, it cuts first.</li>
 * </ul>
 * What a player's client does for them it does for itself: it stands still while a hit or a skill holds it (a
 * player's client locks their keys), and tells its slam and its knives where it aims (XCharaAttack.aim), the one it
 * fights.
 */
final class XCharaBrain extends BossBrain {
    /** How near whom it fights it starts each (blocks between their feet): a little inside each reach. */
    /** (WALK_IN: from how far it will walk in to use its knife.) */
    static final double WALK_IN=7.5,KNIFE=3.0,JAB_REACH=3.5,CUTTER_REACH=5.4,CAST_REACH=10.5,ERASE_NEAR=7.5,ERASE_REACH=17,XSLASH_NEAR=2.5,XSLASH_FAR=14.5,SLAM_NEAR=3,SLAM_FAR=9.2,KNIVES_NEAR=3.5,KNIVES_FAR=16,SHOT_NEAR=5,SHOT_FAR=20;
    /** (2.138.0) From how far off it teleports toward whom it fights (blocks: it comes down short of them). */
    static final double BLINK_NEAR=6.8,BLINK_FAR=13;
    /** Ticks between two of its Q (a player's are heard no oftener than XCharaRules.HACK_RATE; a little more: the switch shows), and its rest after a skill. */
    static final int HACK_EVERY=XCharaRules.HACK_RATE+2,REST_LEAST=8,REST_MORE=14;
    /** It restores itself below this share of its health. */
    static final float HEAL_BELOW=.8f;
    enum Plan{
        NONE(false,false),COMBO(false,false),JAB(true,false),CUTTER(true,false),SHOT(true,false),KNIVES(true,false),SLAM(true,false),XSLASH(true,false),
        ERASE(true,true),HEAL(true,true),WEAKEN(true,true),PULL(true,true),CLONE(true,true),DANCE(true,true);
        /** Whether it needs the hack one way, and which. */
        final boolean hackMatters,hack;
        Plan(boolean hackMatters,boolean hack){this.hackMatters=hackMatters;this.hack=hack;}
    }
    private Plan plan=Plan.NONE;private long planStart;private LivingEntity mark;
    private boolean pressed;private long pressedAt;
    /** When it may press Q next, and until when it rests after a skill. */
    private long hackAt,restUntil;
    XCharaBrain(BossPlayer boss){super(boss);}

    @Override protected boolean committed(long now){return plan!=Plan.NONE;}
    @Override protected void onAngry(long now){plan=Plan.NONE;restUntil=now+10;}
    /** A hit of its knife, a skill of it or its E holds it where it stands, as a player's client holds their keys. */
    @Override protected boolean rooted(){return XCharaAttack.busy(boss)||XCharaHeal.busy(boss);}
    @Override protected String bound(){
        if(XCharaUltimate.casting(boss))return "검무";
        if(XCharaClone.casting(boss))return "분신";
        if(XCharaWeaken.casting(boss))return "약화";
        if(XCharaPull.casting(boss))return "끌어오기";
        return "묶임";
    }

    @Override protected void attack(long now){
        if(plan!=Plan.NONE&&(mark==null||!mark.isAlive()||mark.isRemoved()))end(now,false);
        if(plan==Plan.NONE){choose(now);if(plan==Plan.NONE)return;}
        if(plan==Plan.COMBO)combo(now);else skill(now);
    }
    /** Since when it walks in for a combo, and whether it has come within its knife's reach. */
    private long walkSince;private boolean closed;
    private void start(Plan p,LivingEntity on,long now){plan=p;mark=on;planStart=now;pressed=false;walkSince=now;closed=false;}
    private void end(long now,boolean rest){
        if(rest&&plan!=Plan.COMBO&&plan!=Plan.NONE)restUntil=now+REST_LEAST+random.nextInt(REST_MORE);
        plan=Plan.NONE;pressed=false;mark=null;
    }

    // ---- what to do next

    private static boolean ready(long readyAt,long now){return now>=readyAt;}
    private static boolean guarding(LivingEntity e){return e instanceof ServerPlayerEntity p&&CombatManager.guarding(p);}
    /** How much longer {@code e} is held (staggered, incapacitated, its guard broken; seized by its double), ticks. */
    static long heldFor(LivingEntity e,long now){
        var s=e instanceof ServerPlayerEntity p?CombatManager.peek(p):null;
        return s==null?0:Math.max(0,Math.max(s.disabledUntil,Math.max(s.brokenUntil,s.staggerUntil))-now);
    }
    /** Whether a new combo may start now: no hit of its knife in flight, the knife not resting. */
    private boolean knifeFree(long now){
        if(now<XCharaAttack.ready(boss))return false;
        var h=XCharaAttack.hit(boss);
        return h==null||!XCharaCombo.valid(h.combo())||now>=h.start()+XCharaCombo.LENGTH[h.combo()];
    }
    /**
     * How much it would rather do {@code p} now to one {@code d} off (0: it cannot): what is ready, in reach, and suits
     * (through a guard what a guard does not take; from afar what flies).
     */
    private float weight(Plan p,LivingEntity t,double d,boolean guard,long now){
        boolean sees=boss.canSee(t);
        // (one its double has seized or marked takes no other "!!!" of its meanwhile: it would only wait on it)
        if(p.hackMatters&&p.hack&&p!=Plan.ERASE&&(XCharaClone.held(t)||XCharaClone.marked(t)))return 0;
        float hp=boss.getHealth()/Math.max(1,boss.getMaxHealth());
        return switch(p){
            case COMBO->d<=WALK_IN&&knifeFree(now)?(d<=KNIFE?(guard?2.5f:5):(guard?1:2.5f)):0;
            case JAB->ready(XCharaAttack.jabReady(boss),now)&&d<=WALK_IN?(d<=JAB_REACH?(guard?6:4):(guard?3:2)):0;
            case CUTTER->ready(XCharaAttack.cutterReady(boss),now)&&d<=WALK_IN+1&&!guard?(d<=CUTTER_REACH?2.5f:1.5f):0;
            case KNIVES->ready(XCharaAttack.knivesReady(boss),now)&&d>=KNIVES_NEAR&&d<=KNIVES_FAR&&sees?(d>7?3:2):0;
            case SHOT->ready(XCharaAttack.shotReady(boss),now)&&d>=SHOT_NEAR&&d<=SHOT_FAR&&sees&&!guard?2:0;
            case SLAM->ready(XCharaAttack.slamReady(boss),now)&&d>=SLAM_NEAR&&d<=SLAM_FAR&&boss.isOnGround()?2.5f:0;
            case XSLASH->ready(XCharaAttack.xslashReady(boss),now)&&d>=XSLASH_NEAR&&d<=XSLASH_FAR&&sees?(guard?6:3):0;
            case ERASE->ready(XCharaAttack.eraseReady(boss),now)&&d>=ERASE_NEAR&&d<=ERASE_REACH?1.5f:0;
            case DANCE->ready(XCharaUltimate.ready(boss),now)&&d<=CAST_REACH&&sees&&!guard?(hp<.5f?4:2.5f):0;
            case CLONE->ready(XCharaClone.ready(boss),now)&&!XCharaClone.going(boss)&&d<=CAST_REACH&&sees&&!guard?2.5f:0;
            case WEAKEN->ready(XCharaWeaken.ready(boss),now)&&d<=CAST_REACH&&sees&&!guard&&!XCharaWeaken.weakened(t)?2:0;
            case PULL->ready(XCharaPull.ready(boss),now)&&d>=4.5&&d<=CAST_REACH&&sees&&!guard?3:0;
            default->0;
        };
    }
    private void choose(long now){
        var t=target;double d=flat(t.getPos());long held=heldFor(t,now);boolean guard=guarding(t),hack=XCharaHack.on(boss);
        if(!XCharaAttack.able(boss)){face(t,30);if(d>WAIT)goTo(t.getPos(),now,true);doing="대기";return;}
        // whom its blows hold, within its knife's reach: the knife (the combo runs on inside each hit's stagger)
        if(held>=4&&d<=KNIFE+.3&&knifeFree(now)){start(Plan.COMBO,t,now);return;}
        // hurt, and nobody to stop it: it restores itself
        if(boss.getHealth()<boss.getMaxHealth()*HEAL_BELOW&&ready(XCharaHeal.ready(boss),now)&&(d>6||held>=XCharaRules.HEAL_AT+6)){start(Plan.HEAL,boss,now);return;}
        if(now<restUntil){rest(t,d,now);return;}
        float total=0;var plans=Plan.values();float[] w=new float[plans.length];
        for(var p:plans){
            if(p==Plan.NONE||p==Plan.HEAL)continue;
            float x=weight(p,t,d,guard,now);
            // (what needs its Q pressed first a little less: the switch takes a moment)
            if(x>0&&p.hackMatters&&p.hack!=hack)x*=.75f;
            w[p.ordinal()]=x;total+=x;
        }
        if(total<=0){rest(t,d,now);return;}
        float pick=random.nextFloat()*total;
        for(var p:plans){pick-=w[p.ordinal()];if(w[p.ordinal()]>0&&pick<=0){start(p,t,now);return;}}
    }
    /** Nothing to do yet: up to them if they are far (dashing in on one who runs), else out of their reach, round them. */
    private void rest(LivingEntity t,double d,long now){
        if(d>WAIT+2.5){
            face(t,35);goTo(t.getPos(),now,d>5);
            var toward=t.getPos().subtract(boss.getPos()).multiply(1,0,1);
            // (2.138.0) its teleport, to close on one who stands off: the way it looks, once it looks at them
            if(d>=BLINK_NEAR&&d<=BLINK_FAR&&ready(XCharaBlink.ready(boss),now)&&boss.canSee(t)&&random.nextFloat()<.25f
                &&Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-toward.x,toward.z))-boss.getYaw()))<12&&XCharaBlink.cast(boss)){doing="순간이동";return;}
            if(d>11&&dashReady(now)&&random.nextFloat()<.2f&&room(toward.normalize(),5.4))dash(toward.normalize(),now);
            doing="접근";
        }else{keepAway(t,WAIT,now);doing="견제";}
    }

    // ---- the plans

    /** The knife's four hits, each as soon as it may begin, up to them between hits. */
    private void combo(long now){
        var t=mark;double d=flat(t.getPos());var h=XCharaAttack.hit(boss);
        boolean mine=h!=null&&h.start()>=planStart&&XCharaCombo.valid(h.combo())&&!XCharaCombo.skill(h.combo());
        int place=mine?XCharaCombo.place(h.combo()):0;
        boolean flying=mine&&now<h.start()+XCharaCombo.LENGTH[h.combo()],holding=mine&&XCharaCombo.holding(h.combo(),h.start(),now);
        if(CounterStance.active(t)){end(now,false);return;}
        // not begun and out of reach: it walks in (two seconds at most), as a player would to cut
        if(!mine&&!closed&&d>KNIFE-.4){
            if(now-walkSince>40||now<XCharaAttack.ready(boss)){end(now,false);return;}
            face(t,60);goTo(t.getPos(),now,true);planStart=now;doing="접근 평타";return;
        }
        closed=true;
        if(!flying&&(d>KNIFE+.9||now<XCharaAttack.ready(boss)||now-planStart>12&&place==0)){end(now,false);return;}
        face(t,60);
        if(d>2.5)move=t.getPos().subtract(boss.getPos()).multiply(1,0,1).normalize();
        else if(d<1.3)move=boss.getPos().subtract(t.getPos()).multiply(1,0,1).normalize().multiply(.6);
        doing="평타 "+place;
        if(place==4){if(!flying)end(now,true);return;}
        // the next hit, kept for when it may start (as a press made a little early is)
        if(place==0||!flying||now>=XCharaCombo.nextAt(h.combo(),h.start())-XCharaCombo.BUFFER){if(!holding)aim(t);XCharaAttack.request(boss);}
    }
    /** How far off whom it does {@code p} to may stand as it begins (blocks). */
    private static double reach(Plan p){
        return switch(p){
            case JAB->JAB_REACH;case CUTTER->CUTTER_REACH;case KNIVES->KNIVES_FAR;case SHOT->SHOT_FAR;case SLAM->SLAM_FAR;case XSLASH->XSLASH_FAR;
            case ERASE->ERASE_REACH;case HEAL->Double.MAX_VALUE;default->CAST_REACH;
        };
    }
    private boolean press(Plan p,LivingEntity t){
        return switch(p){
            case JAB->XCharaAttack.jab(boss);case CUTTER->XCharaAttack.cutter(boss);case KNIVES->XCharaAttack.knives(boss);case SHOT->XCharaAttack.shot(boss);
            case SLAM->XCharaAttack.slam(boss);case XSLASH->XCharaAttack.xslash(boss);
            case ERASE->XCharaAttack.erase(boss,t.getPos().add(0,.1,0));
            case HEAL->XCharaHeal.request(boss);
            case WEAKEN->XCharaWeaken.cast(boss,t.getId());case PULL->XCharaPull.cast(boss,t.getId());case CLONE->XCharaClone.cast(boss,t.getId());case DANCE->XCharaUltimate.cast(boss,t.getId());
            default->false;
        };
    }
    private static String name(Plan p){
        return switch(p){
            case JAB->"오염 찌르기";case CUTTER->"칼날 베기";case KNIVES->"오염 나이프";case SHOT->"칼날 발사";case SLAM->"덮어쓰기 강타";case XSLASH->"X 대참격";
            case ERASE->"지형 삭제";case HEAL->"복구";case WEAKEN->"약화";case PULL->"끌어오기";case CLONE->"분신";case DANCE->"검무";default->"";
        };
    }
    /** Whether the skill it pressed still runs. */
    private boolean running(long now){
        return switch(plan){
            case HEAL->XCharaHeal.busy(boss)||now<pressedAt+XCharaRules.HEAL_LENGTH;
            case WEAKEN->XCharaWeaken.casting(boss);case PULL->XCharaPull.casting(boss);case DANCE->XCharaUltimate.casting(boss);
            case CLONE->XCharaClone.casting(boss)||now<pressedAt+CloneRules.RELEASE;
            default->XCharaAttack.cleaving(boss);
        };
    }
    /** Where it aims at {@code t}: their chest, a little ahead of where they go. */
    private static Vec3d chest(LivingEntity t,double lead){return t.getPos().add(t.getVelocity().multiply(lead,0,lead)).add(0,t.getHeight()*.6,0);}
    private void skill(long now){
        var t=mark;boolean self=plan==Plan.HEAL;
        if(!pressed){
            if(now-planStart>50||!self&&CounterStance.active(t)){end(now,false);return;}
            if(!self)face(t,45);
            // its hack as the skill needs it: Q, as a player would press it
            if(plan.hackMatters&&XCharaHack.on(boss)!=plan.hack){
                doing="Q "+name(plan);
                if(now>=hackAt&&!XCharaHack.held(boss)){XCharaHack.toggle(boss);hackAt=now+HACK_EVERY;}
                if(!self&&flat(t.getPos())>reach(plan))goTo(t.getPos(),now,true);
                return;
            }
            // (a moment after the switch, as its knife and panel change)
            if(plan.hackMatters&&now<hackAt-HACK_EVERY+4){doing="Q "+name(plan);return;}
            if(!self){
                double d=flat(t.getPos());
                if(d>reach(plan)){goTo(t.getPos(),now,true);doing="접근 "+name(plan);return;}
                aim(t);
            }
            if(!press(plan,t)){doing="준비 "+name(plan);if(now-planStart>16)end(now,false);return;}
            pressed=true;pressedAt=now;doing=name(plan);return;
        }
        doing=name(plan);
        if(!running(now)){
            boolean pulled=plan==Plan.PULL;
            end(now,true);
            if(self)return;
            // pulled before it, or held by what it did: the knife at once
            if((pulled||heldFor(t,now)>=4)&&t.isAlive()&&flat(t.getPos())<=KNIFE+.6&&knifeFree(now)&&!CounterStance.active(t)){restUntil=now;start(Plan.COMBO,t,now);combo(now);}
            return;
        }
        if(self)return;
        face(t,30);
        long age=now-pressedAt;
        // what a player's client tells of where they aim: its slam's dive at where they stand, each knife at their chest
        if(plan==Plan.SLAM&&age>=SlamRules.AIM_FROM-2&&age<=SlamRules.DIVE)XCharaAttack.aim(boss,t.getPos().add(t.getVelocity().multiply(4,0,4)));
        else if(plan==Plan.SHOT&&age<=ShotRules.FIRE[ShotRules.BLADES-1])XCharaAttack.aim(boss,chest(t,3));
        else if(plan==Plan.KNIVES&&age<=KnivesRules.FIRE[KnivesRules.KNIVES-1])XCharaAttack.aim(boss,chest(t,1));
    }

    // ---- answers

    /** The threat it last answered with a cut of its own first (whose, and when it was begun). */
    private ServerPlayerEntity answeredFrom;private long answeredSeen=Long.MIN_VALUE;
    /**
     * A blow comes that its knife's first hit lands before (and its stagger stops it): something slow to land, from
     * right before it. It cuts first, and the combo goes on from it.
     */
    @Override protected boolean strikeFirst(BossThreats.Threat t,int left,long now){
        var from=t.from();if(from==null||t.timeStop()||CounterStance.active(from))return false;
        int lands=XCharaCombo.blowAt(XCharaCombo.SLASH,0);
        if(from==answeredFrom&&t.seen()==answeredSeen){
            var mine=XCharaAttack.hit(boss);
            return mine!=null&&XCharaCombo.valid(mine.combo())&&!XCharaCombo.skill(mine.combo())&&now<=mine.start()+XCharaCombo.blowAt(mine.combo(),0);
        }
        if(left<lands+2||plan!=Plan.NONE&&plan!=Plan.COMBO)return false;
        if(flat(from.getPos())>KNIFE||!knifeFree(now)||!XCharaAttack.able(boss))return false;
        if(guarding())guard(false,now);
        aim(from);
        if(!XCharaAttack.request(boss))return false;
        start(Plan.COMBO,from,now);answeredFrom=from;answeredSeen=t.seen();
        return true;
    }
}
