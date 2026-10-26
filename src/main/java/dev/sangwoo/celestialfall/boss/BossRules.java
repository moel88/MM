package dev.sangwoo.celestialfall.boss;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What every boss does (2.69.0, user request: "이제부터 말하는건 앞으로 만드는 모든 보스에 적용 되는 기본적인
 * 메커니즘이야 일단 처음에는 중립적이여서 가만히 서있다가 공격하면 어그로가 끌리고 범위는 30블록이야 공격 우선 순위는
 * HP가 낮은 플레이어 우선 ... 어그로가 한 번 끌리면 30블록을 벗어나지 않는이상 30블록 이내에 있는 모든 플레이어를 HP가
 * 낮은 순서대로 공격함"): it stands still where it came, neutral, until a player strikes it; then it goes after every
 * player within RANGE blocks of it, the one with the least health first, until none is left within RANGE, when it goes
 * back to where it stands and is whole again. Pure rules, shared by the server and the tests; ticks of 1/20 s.
 */
public final class BossRules {
    /** How far from a boss its anger reaches (blocks): whoever is within it is fought, and whoever leaves it is let go. */
    public static final double RANGE=30;
    /** Ticks with nobody left within RANGE before it lets its anger go and goes back. */
    public static final int CALM_AFTER=60;
    /** Ticks it takes, back where it stands, to be whole again (it heals evenly over them). */
    public static final int HEAL_TICKS=100;
    /** Ticks it may take to walk back before it is put back where it stands. */
    public static final int HOME_TIMEOUT=600;
    /** The ring of boxes it leaves where it fell: how far out (blocks). */
    public static final double BOX_RING=3.2;
    /** Ticks a box it left stays unopened before it goes (five minutes, as a meteor). */
    public static final int BOX_LIFE=6000;
    /**
     * Ticks it takes to see what a player begins and answer it (a quick player's: 0.15 s): what is over sooner than that
     * after it began cannot be got out of the way of.
     */
    public static final int REACTION=3;
    /**
     * A boss is not held down for ever by blows that stagger (2.68.0's basic attacks): held (staggered, incapacitated,
     * its guard broken) POISE_HELD ticks of the last POISE_WINDOW, it shrugs off staggers for POISE_TICKS (its health
     * still taken, user request: "보스 ai는 매우 좋게"; two players taking turns could keep it down otherwise).
     */
    public static final int POISE_WINDOW=60,POISE_HELD=46,POISE_TICKS=30;
    /** The skills whose cooldowns a boss may have its own of (BossKind.cooldown). */
    public static final String TIME_STOP="time_stop",BEATDOWN="beatdown";
    private BossRules(){}

    /** One the boss may fight: their health and how far they are from it. */
    public record Candidate<T>(T who,float health,double distance){}
    /**
     * The order a boss fights those within its range in: the least health first ("HP가 낮은 플레이어 우선"), the nearer
     * of two with the same health first.
     */
    public static <T> List<T> order(List<Candidate<T>> candidates){
        var sorted=new ArrayList<>(candidates);
        sorted.removeIf(c->!(c.distance<=RANGE)||!(c.health>0));
        sorted.sort(Comparator.<Candidate<T>>comparingDouble(c->c.health).thenComparingDouble(c->c.distance));
        var out=new ArrayList<T>();for(var c:sorted)out.add(c.who);return out;
    }
    /**
     * Whom a boss goes after now: the first in {@link #order}, unless it is in the middle of fighting {@code current}
     * (who is still within its range) and nobody has clearly less health ({@code margin}): it does not turn from one to
     * another on every blow.
     */
    public static <T> T choose(List<Candidate<T>> candidates,T current,boolean busy,float margin){
        var order=order(candidates);if(order.isEmpty())return null;
        T first=order.get(0);if(current==null||first==current)return first;
        Candidate<T> cur=null,best=null;
        for(var c:candidates){if(c.who==current)cur=c;if(c.who==first)best=c;}
        if(cur==null||!(cur.distance<=RANGE)||!(cur.health>0))return first;
        if(busy&&best.health>=cur.health-margin)return current;
        return first;
    }
    /** Where the {@code n} boxes stand round ({@code x}, {@code z}): evenly on a ring {@code radius} out, the first toward {@code yaw}. */
    public static double[][] ring(double x,double z,int n,double radius,float yaw){
        var out=new double[Math.max(0,n)][];
        for(int i=0;i<out.length;i++){
            double a=Math.toRadians(yaw)+i*Math.PI*2/n;
            out[i]=new double[]{x-Math.sin(a)*radius,z+Math.cos(a)*radius};
        }
        return out;
    }
    /** The first coming due after {@code now}, coming every {@code every} ticks from {@code last}. */
    public static long nextAfter(long last,long every,long now){
        if(every<=0)throw new IllegalArgumentException("every");
        if(now<last)return last;
        long k=(now-last)/every+1;
        return last+k*every;
    }
    /** How much health it gets back this tick going from {@code health} to whole ({@code max}) over HEAL_TICKS. */
    public static float heal(float health,float max){return Math.min(max-health,max/HEAL_TICKS);}

    /**
     * A boss's poise: whether it has been held down long enough lately to shrug off the next stagger. Fed every tick
     * with whether it was held then.
     */
    public static final class Poise {
        private final boolean[] held=new boolean[POISE_WINDOW];
        private long last=Long.MIN_VALUE,until=Long.MIN_VALUE;private int count;
        /** Tick {@code now}: whether it is held (staggered, incapacitated, its guard broken). */
        public void tick(long now,boolean isHeld){
            if(now==last)return;
            if(last!=Long.MIN_VALUE&&now-last>1)for(long t=last+1;t<now&&t<last+1+POISE_WINDOW;t++)set(t,false);
            set(now,isHeld);last=now;
            if(count>=POISE_HELD&&now>=until){until=now+POISE_TICKS;java.util.Arrays.fill(held,false);count=0;}
        }
        private void set(long t,boolean value){int i=(int)Math.floorMod(t,(long)POISE_WINDOW);if(held[i])count--;held[i]=value;if(value)count++;}
        /** Whether a stagger now slides off it. */
        public boolean shrugs(long now){return now<until;}
    }
}
