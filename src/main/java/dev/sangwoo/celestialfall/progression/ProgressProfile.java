package dev.sangwoo.celestialfall.progression;

/** Immutable server-owned state. All transitions validate before changing any counter. */
public record ProgressProfile(int level,int rebirths,int transcendence,String stand,long money,int xp) {
    public static final int MAX_LEVEL=50,MAX_REBIRTHS=15,MAX_TRANSCENDENCE=1_000_000;
    public static final long MAX_MONEY=1_000_000_000L;
    public static final ProgressProfile NEW=new ProgressProfile(1,0,0,"none");
    public ProgressProfile(int level,int rebirths,int transcendence,String stand){this(level,rebirths,transcendence,stand,0,0);}
    public ProgressProfile {
        level=Math.max(1,Math.min(MAX_LEVEL,level));
        rebirths=Math.max(0,Math.min(MAX_REBIRTHS,rebirths));
        transcendence=Math.max(0,Math.min(MAX_TRANSCENDENCE,transcendence));
        if(!StandStats.known(stand))stand="none";
        money=Math.max(0,Math.min(MAX_MONEY,money));
        xp=level==MAX_LEVEL?0:Math.max(0,Math.min(requiredXp(level)-1,xp));
    }
    public boolean canRebirth(){return level==MAX_LEVEL&&rebirths<MAX_REBIRTHS;}
    public boolean canTranscend(){return rebirths==MAX_REBIRTHS&&transcendence<MAX_TRANSCENDENCE;}
    public ProgressProfile rebirth(){return canRebirth()?new ProgressProfile(1,rebirths+1,transcendence,stand,money,0):this;}
    public ProgressProfile transcend(){return canTranscend()?new ProgressProfile(level,0,transcendence+1,stand,money,xp):this;}
    public ProgressProfile withLevel(int value){return new ProgressProfile(value,rebirths,transcendence,stand,money,0);}
    public ProgressProfile addLevels(int value){return withLevel((int)Math.max(1,Math.min(MAX_LEVEL,(long)level+value)));}
    public ProgressProfile withRebirths(int value){return new ProgressProfile(level,value,transcendence,stand,money,xp);}
    public ProgressProfile withTranscendence(int value){return new ProgressProfile(level,rebirths,value,stand,money,xp);}
    public ProgressProfile withStand(String value){return new ProgressProfile(level,rebirths,transcendence,value,money,xp);}
    public ProgressProfile withMoney(long value){return new ProgressProfile(level,rebirths,transcendence,stand,value,xp);}
    public static int requiredXp(int level){return 100+25*(Math.max(1,Math.min(MAX_LEVEL,level))-1);}
    public ProgressProfile grantXp(int amount){
        if(amount<=0||level==MAX_LEVEL)return this;
        int next=level;long total=(long)xp+amount;
        while(next<MAX_LEVEL&&total>=requiredXp(next)){total-=requiredXp(next);next++;}
        return new ProgressProfile(next,rebirths,transcendence,stand,money,next==MAX_LEVEL?0:(int)total);
    }
}
