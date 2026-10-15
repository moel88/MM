package dev.sangwoo.celestialfall.commerce;
import dev.sangwoo.celestialfall.progression.ProgressProfile;
/** Prices and rewards are entirely server-owned; one request trades exactly one fruit. */
public final class TradeRules {
    public static final int BUY_PRICE=75,SELL_PRICE=50,BUY_XP=10,SELL_XP=5,HOLD_TICKS=20;
    public static final double REACH=3.5;
    public record Result(ProgressProfile profile,String error) {public boolean ok(){return error.isEmpty();}}
    public static Result trade(ProgressProfile p,boolean buying,boolean hasItem,boolean room){
        if(buying&&p.money()<BUY_PRICE)return new Result(p,"돈이 부족해요. "+BUY_PRICE+"원이 필요합니다.");
        if(buying&&!room)return new Result(p,"인벤토리에 빈 공간을 만들어 주세요.");
        if(!buying&&!hasItem)return new Result(p,"판매할 로카카카가 없습니다.");
        if(!buying&&p.money()>ProgressProfile.MAX_MONEY-SELL_PRICE)return new Result(p,"보유 금액 한도에 도달했습니다.");
        return new Result(p.withMoney(p.money()+(buying?-BUY_PRICE:SELL_PRICE)).grantXp(buying?BUY_XP:SELL_XP),"");
    }
    private TradeRules(){}
}
