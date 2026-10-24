package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import dev.sangwoo.celestialfall.stand.StarPlatinumStance;

/**
 * Star Platinum coming out of its user, floating beside them and going back (2.51.0, user request: "q 누르면 플레이어
 * 옆에 자세 잡으면서 계속 나와있고"), as smooth functions of the seconds since Q. Coming out it shoots from the user's body
 * to its place beside them, curled up behind crossed forearms (the summon stance), fading in and growing to its size;
 * bursts open into the pose of the art, quick to gather speed and long to slow down, with a gust through its hair, scarf
 * and loincloth; holds the pose in the art's wind; and settles into floating at rest (StarPlatinumMotion). Going back it
 * curls up again and is drawn into its user, faster and faster as it fades. Every value starts and ends where its
 * neighbour does, and the joints start and stop moving gently except where the body is still too faint to see.
 */
public final class StandTimeline {
    /** Coming out (seconds): out of the body by EMERGE, solid by FADE, the burst from BURST to POSED, the pose held to HOLD, at rest by SETTLED. */
    public static final float EMERGE=.30f,FADE=.16f,BURST=.24f,POSED=.52f,HOLD=1.30f,SETTLED=2.0f;
    /** Going back takes this long (seconds). */
    public static final float GONE=.30f;
    /** Q again while the last change is under way: the new one starts from the look it had got to, taking over in this long (seconds). */
    public static final float CATCH=.2f;
    private StandTimeline(){}

    static float clamp(float v){return Math.max(0,Math.min(1,v));}
    static float smooth(float v){v=clamp(v);return v*v*(3-2*v);}
    /** The burst: still at both ends, at its fastest a third of the way in (1 - (1-u)^3 (1+3u)). */
    static float burst(float u){u=clamp(u);float r=1-u;return 1-r*r*r*(1+3*u);}
    /** Out past its size and back (ease-out-back), from fast to still. */
    static float overshoot(float u){u=clamp(u);float c1=1.70158f,c3=c1+1,r=u-1;return 1+c3*r*r*r+c1*r*r;}

    /** The look {@code t} seconds after Q brought it out (no lean or speed: those come from following its user). */
    public static StarPlatinumLook summon(float t,StarPlatinumLook into){
        into.only(StarPlatinumStance.SUMMON);
        float e=clamp(t/EMERGE),r=1-e;
        into.out=1-r*r*r;
        into.alpha=smooth(t/FADE);
        into.scale=.84f+.16f*overshoot(t/.36f);
        float b=burst((t-BURST)/(POSED-BURST)),settle=smooth((t-HOLD)/(SETTLED-HOLD));
        into.weights[StarPlatinumStance.SUMMON]=1-b;
        into.weights[StarPlatinumStance.HERO]=b*(1-settle);
        into.weights[StarPlatinumStance.IDLE]=b*settle;
        into.wind=b*(1-settle);
        into.gust=smooth((t-BURST)/.18f)*(1-smooth((t-.5f)/.5f));
        // its aura (2.52.0): comes in with the body and flares as it bursts into the pose
        float flare=smooth((t-BURST)/.15f)*(1-smooth((t-.42f)/.4f));
        into.glow=into.alpha*(1+.9f*flare);
        return into;
    }
    /** The look {@code t} seconds after it was sent back, from {@code from}, the look it had then. */
    public static StarPlatinumLook dismiss(float t,StarPlatinumLook from,StarPlatinumLook into){
        float u=clamp(t/GONE),curl=smooth(t/.22f);
        into.set(from);
        for(int i=0;i<into.weights.length;i++)into.weights[i]=from.weights[i]*(1-curl)+(i==StarPlatinumStance.SUMMON?curl:0);
        into.out=from.out*(1-u*u*u);
        into.alpha=from.alpha*(1-smooth((t-.05f)/.25f));
        into.glow=from.glow*(1-smooth((t-.05f)/.25f));
        into.scale=from.scale*(1-.14f*u*u);
        into.wind=from.wind*(1-smooth(u));
        into.gust=from.gust*(1-u)+.6f*(float)Math.sin(Math.PI*u);
        return into;
    }
    /**
     * The look {@code t} seconds after the last change: brought out ({@code out}) or sent back. {@code from} is the look
     * it had when that change came (null if there was none before it): going back starts from it, and coming out takes
     * over from it within CATCH.
     */
    public static StarPlatinumLook at(boolean out,float t,StarPlatinumLook from,StarPlatinumLook into){
        if(!out)return dismiss(t,from==null?NONE:from,into);
        summon(t,into);
        if(from!=null&&t<CATCH)into.mix(from,into,smooth(t/CATCH));
        return into;
    }
    private static final StarPlatinumLook NONE=new StarPlatinumLook().only(StarPlatinumStance.IDLE);
    /** Whether a stand sent back {@code t} seconds ago is gone. */
    public static boolean gone(float t){return t>=GONE;}
}
