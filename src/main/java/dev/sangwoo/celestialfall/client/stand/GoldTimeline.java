package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceStance;

/**
 * Gold Experience coming out of its user, floating beside them and going back (2.146.0, user request: "이제 이걸 플레이어
 * q스킬로 이식하고 q누르면 나오게 바꾸고"), as smooth functions of the seconds since Q; the times are Star Platinum's
 * ({@link StandTimeline}). Coming out it shoots from its user's body to its place beside them, curled up behind its
 * crossed forearms (the summon stance), fading in and growing to its size; opens into the pose of the picture the user
 * sent (a hand on its hip, its legs crossed), quick to gather speed and long to slow down; holds it; and settles into
 * floating at rest (GoldExperienceMotion). Going back it curls up again and is drawn into its user, faster and faster as
 * it fades. Its golden aura comes in with its body, flares as it opens, and goes with it.
 */
public final class GoldTimeline {
    /** Coming out (seconds): out of the body by EMERGE, solid by FADE, opening from BURST to POSED, the pose held to HOLD, at rest by SETTLED. */
    public static final float EMERGE=StandTimeline.EMERGE,FADE=StandTimeline.FADE,BURST=StandTimeline.BURST,POSED=StandTimeline.POSED,HOLD=StandTimeline.HOLD,SETTLED=StandTimeline.SETTLED;
    /** Going back takes this long (seconds). */
    public static final float GONE=StandTimeline.GONE;
    /** Q again while the last change is under way: the new one starts from the look it had got to, taking over in this long (seconds). */
    public static final float CATCH=StandTimeline.CATCH;
    private GoldTimeline(){}
    /** The look {@code t} seconds after Q brought it out (no lean: that comes from following its user). */
    public static GoldExperienceLook summon(float t,GoldExperienceLook into){
        into.only(GoldExperienceStance.SUMMON);
        float e=StandTimeline.clamp(t/EMERGE),r=1-e;
        into.out=1-r*r*r;
        into.alpha=StandTimeline.smooth(t/FADE);
        into.scale=.84f+.16f*StandTimeline.overshoot(t/.36f);
        float b=StandTimeline.burst((t-BURST)/(POSED-BURST)),settle=StandTimeline.smooth((t-HOLD)/(SETTLED-HOLD));
        into.weights[GoldExperienceStance.SUMMON]=1-b;
        into.weights[GoldExperienceStance.HERO]=b*(1-settle);
        into.weights[GoldExperienceStance.IDLE]=b*settle;
        float flare=StandTimeline.smooth((t-BURST)/.15f)*(1-StandTimeline.smooth((t-.42f)/.4f));
        into.glow=into.alpha*(1+.9f*flare);
        into.red=into.green=into.blue=1;into.shadow=1;into.lean=into.roll=0;
        return into;
    }
    /** The look {@code t} seconds after it was sent back, from {@code from}, the look it had then. */
    public static GoldExperienceLook dismiss(float t,GoldExperienceLook from,GoldExperienceLook into){
        float u=StandTimeline.clamp(t/GONE),curl=StandTimeline.smooth(t/.22f),fade=1-StandTimeline.smooth((t-.05f)/.25f);
        float out=from.out,alpha=from.alpha,glow=from.glow,scale=from.scale;
        into.set(from);
        for(int i=0;i<into.weights.length;i++)into.weights[i]=into.weights[i]*(1-curl)+(i==GoldExperienceStance.SUMMON?curl:0);
        into.out=out*(1-u*u*u);into.alpha=alpha*fade;into.glow=glow*fade;into.scale=scale*(1-.14f*u*u);
        into.lean=into.roll=0;
        return into;
    }
    /**
     * The look {@code t} seconds after the last change: brought out ({@code out}) or sent back. {@code from} is the look
     * it had when that change came (null if there was none before it): going back starts from it, and coming out takes
     * over from it within CATCH.
     */
    public static GoldExperienceLook at(boolean out,float t,GoldExperienceLook from,GoldExperienceLook into){
        if(!out)return dismiss(t,from==null?REST:from,into);
        summon(t,into);
        if(from!=null&&t<CATCH)into.mix(from,into,StandTimeline.smooth(t/CATCH));
        return into;
    }
    private static final GoldExperienceLook REST=new GoldExperienceLook().only(GoldExperienceStance.IDLE);
    static{REST.glow=1;}
    /** Whether a stand sent back {@code t} seconds ago is gone. */
    public static boolean gone(float t){return t>=GONE;}
}
