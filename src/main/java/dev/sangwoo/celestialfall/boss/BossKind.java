package dev.sangwoo.celestialfall.boss;

import java.util.Locale;

/**
 * The bosses there are (2.69.0). Each is a body on the map like a player's (BossPlayer, a stand user like the players),
 * with the rules every boss shares (BossRules: neutral until struck, then after everyone within its range, the lowest
 * health first) and its own: its health, its stand, its skin, what it gives when it falls, how often it comes, and the
 * cooldowns of its own that differ from a player's.
 */
public enum BossKind {
    /**
     * Kujo Jotaro (user request: "이제 쿠죠 죠타로 보스 만들자 ... 보스 체력은 5000이고 ... 피가 2500이 되면 시간정지를
     * 사용 할 수 있어 죠타로의 시간정지 쿨타임은 2분 비트다운은 1분30초 나머지는 똑같아 죠타로를 잡으면 5000원을 주고
     * 죠타로가 죽은 자리 기준으로 원형으로 6개의 박스가 나오게", "죠타로는 1시간 30분마다 나중에 지정한 위치에 나오는데"):
     * Star Platinum's user, 5000 health, his time stop only at 2500 or less, it ready two minutes after it is over and
     * his beatdown a minute and a half after (a player's: a minute and half a minute), the rest as a player's; 5000 won to
     * whoever fells him and six boxes in a ring where he fell; back every hour and a half.
     */
    JOTARO("jotaro","쿠죠 죠타로","Kujo_Jotaro","star_platinum",5000,2500,5000,6,90*60*20);

    /** Its id (commands, packets, saved state), its name as the boss bar shows it, and its body's profile name (16 at most, ASCII). */
    public final String id,title,profile;
    /** The stand it fights with (a player's stand id, StandStats). */
    public final String stand;
    /** Its health, and the health at or under which its time stop may be called (0: never). */
    public final float health,timeStopBelow;
    /** The money whoever fells it gets, and how many boxes it leaves in a ring where it fell. */
    public final int reward,boxes;
    /** Ticks from one of its comings to the next (the world's time). */
    public final long every;
    BossKind(String id,String title,String profile,String stand,float health,float timeStopBelow,int reward,int boxes,long every){
        this.id=id;this.title=title;this.profile=profile;this.stand=stand;this.health=health;this.timeStopBelow=timeStopBelow;this.reward=reward;this.boxes=boxes;this.every=every;
    }
    /** Its cooldown for {@code skill} (BossRules.TIME_STOP, BEATDOWN, ...) where it differs from a player's {@code base}. */
    public int cooldown(String skill,int base){
        if(this==JOTARO){
            if(BossRules.TIME_STOP.equals(skill))return 2*60*20;
            if(BossRules.BEATDOWN.equals(skill))return 90*20;
        }
        return base;
    }
    /** Whether it may call its time stop with {@code health} left. */
    public boolean mayStopTime(float health){return timeStopBelow>0&&health<=timeStopBelow;}
    public static BossKind byId(String id){
        if(id==null)return null;
        for(var k:values())if(k.id.equals(id.toLowerCase(Locale.ROOT)))return k;
        return null;
    }
}
