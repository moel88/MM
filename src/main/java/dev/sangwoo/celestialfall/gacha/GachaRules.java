package dev.sangwoo.celestialfall.gacha;

import java.util.*;
import java.util.function.IntSupplier;

/**
 * The stand gacha's odds and guarantees (user request, 2.39.0). Each pull is SSR 1%, SR 5% or R 94%; within any ten
 * pulls one is SR or better (so every 10-pull has one), and the hundredth pull since the last SSR is an SSR. The server
 * rolls and keeps each player's count; the client only shows what was rolled.
 */
public final class GachaRules {
    private GachaRules(){}
    public enum Grade {R,SR,SSR}
    /** Chances out of {@link #ODDS}: SSR 1%, SR 5%, R the other 94%. */
    public static final int ODDS=10_000,SSR=100,SR=500;
    /** The tenth pull since the last SR or better is at least SR; the hundredth since the last SSR is SSR. */
    public static final int SR_PITY=10,SSR_PITY=100;
    /** Each grade's share of all pulls over the long run, guarantees included (R, SR, SSR; shown with the odds). */
    public static final double[] LONG_RUN={.8670,.1172,.0158};
    /** Pulls one request may make. */
    public static final int SINGLE=1,TEN=10;

    /** A player's count: pulls since the last SSR, pulls since the last SR or better, and every pull made. */
    public record Count(int sinceSsr,int sinceSr,int pulls){
        public static final Count NEW=new Count(0,0,0);
        public Count{sinceSsr=Math.max(0,Math.min(SSR_PITY-1,sinceSsr));sinceSr=Math.max(0,Math.min(SR_PITY-1,sinceSr));pulls=Math.max(0,pulls);}
        /** Pulls until an SSR is certain (1: the next pull is one). */
        public int toSsr(){return SSR_PITY-sinceSsr;}
        /** Pulls until an SR or better is certain (1: the next pull is one). */
        public int toSr(){return SR_PITY-sinceSr;}
        public Count after(Grade g){
            int total=pulls==Integer.MAX_VALUE?pulls:pulls+1;
            return switch(g){
                case SSR->new Count(0,0,total);
                case SR->new Count(sinceSsr+1,0,total);
                case R->new Count(sinceSsr+1,sinceSr+1,total);
            };
        }
    }

    /** One pull from {@code count}, with {@code roll} uniform in [0, {@link #ODDS}). */
    public static Grade pull(Count count,int roll){
        if(count.toSsr()<=1||roll<SSR)return Grade.SSR;
        if(count.toSr()<=1||roll<SSR+SR)return Grade.SR;
        return Grade.R;
    }
    public record Result(List<Grade> grades,Count before,Count after){
        public boolean any(Grade g){return grades.contains(g);}
    }
    /** {@code n} pulls in order from {@code count}, each drawing its roll from {@code rolls}. */
    public static Result pulls(Count count,int n,IntSupplier rolls){
        var out=new ArrayList<Grade>(n);var c=count;
        for(int i=0;i<n;i++){var g=pull(c,Math.floorMod(rolls.getAsInt(),ODDS));out.add(g);c=c.after(g);}
        return new Result(List.copyOf(out),count,c);
    }
    /** Least server ticks between two requests, and between two pulls, of one player (a public server's spam guard). */
    public static final int ASK_GAP=2,PULL_GAP=10;
    /** Whether a request of {@code n} pulls at server tick {@code now} comes too soon after this player's last ({@code last}: any request, a pull). */
    public static boolean tooSoon(long now,long[] last,int n){return now-last[0]<ASK_GAP||n>0&&now-last[1]<PULL_GAP;}
    /** A player's last request and pull, before any. */
    public static long[] never(){return new long[]{Long.MIN_VALUE/2,Long.MIN_VALUE/2};}
    /** The stand an SSR gives now (user request: only SSR has a reward, Kageno; R and SR have none yet). */
    public static final String SSR_STAND="kageno";
    /** Pulling needs no stand equipped (user request). */
    public static boolean canPull(String stand){return "none".equals(stand);}
}
