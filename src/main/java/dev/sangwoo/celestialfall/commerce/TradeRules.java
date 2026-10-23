package dev.sangwoo.celestialfall.commerce;
import dev.sangwoo.celestialfall.meteor.MeteorRules;
import dev.sangwoo.celestialfall.progression.ProgressProfile;
import java.util.Locale;
/** Prices and rewards are entirely server-owned; one request trades or crafts exactly one item. */
public final class TradeRules {
    public static final int BUY_PRICE=75,SELL_PRICE=50,BUY_XP=10,SELL_XP=5,HOLD_TICKS=20;
    /** The seller's products: the Rokakaka and (2.49.0, user request) the meteor pickaxe at 5,000. */
    public static final int FRUIT=0,PICKAXE=1,PICKAXE_PRICE=5000,PICKAXE_XP=10;
    /** A stand arrow made at the crafter (2.49.0). */
    public static final int CRAFT_XP=20;
    public static final double REACH=3.5;
    public record Result(ProgressProfile profile,String error) {public boolean ok(){return error.isEmpty();}}
    public static int price(int product){return product==PICKAXE?PICKAXE_PRICE:BUY_PRICE;}
    public static String won(long amount){return String.format(Locale.ROOT,"%,d",amount)+"원";}
    /** One Rokakaka bought from the seller ({@code buying}) or sold to the buyer. */
    public static Result trade(ProgressProfile p,boolean buying,boolean hasItem,boolean room){
        if(buying)return buy(p,FRUIT,room);
        if(!hasItem)return new Result(p,"판매할 로카카카가 없습니다.");
        if(p.money()>ProgressProfile.MAX_MONEY-SELL_PRICE)return new Result(p,"보유 금액 한도에 도달했습니다.");
        return new Result(p.withMoney(p.money()+SELL_PRICE).grantXp(SELL_XP),"");
    }
    /** One of the seller's products. */
    public static Result buy(ProgressProfile p,int product,boolean room){
        int price=price(product);
        if(p.money()<price)return new Result(p,"돈이 부족해요. "+won(price)+"이 필요합니다.");
        if(!room)return new Result(p,"인벤토리에 빈 공간을 만들어 주세요.");
        return new Result(p.withMoney(p.money()-price).grantXp(product==PICKAXE?PICKAXE_XP:BUY_XP),"");
    }
    /** A stand arrow at the crafter from what the player carries. */
    public static Result craft(ProgressProfile p,int fragments,int sticks,boolean room){
        if(fragments<MeteorRules.ARROW_FRAGMENTS)return new Result(p,"운석 조각이 부족해요. ("+fragments+" / "+MeteorRules.ARROW_FRAGMENTS+")");
        if(sticks<MeteorRules.ARROW_STICKS)return new Result(p,"막대기가 "+MeteorRules.ARROW_STICKS+"개 필요해요.");
        if(!room)return new Result(p,"인벤토리에 빈 공간을 만들어 주세요.");
        return new Result(p.grantXp(CRAFT_XP),"");
    }
    private TradeRules(){}
}
