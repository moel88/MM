package dev.sangwoo.celestialfall.commerce;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.server.command.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.text.Text;
import dev.sangwoo.celestialfall.progression.*;
import static net.minecraft.server.command.CommandManager.*;
public final class CommerceCommands {
    public static void add(LiteralArgumentBuilder<ServerCommandSource> root){
        var money=literal("money").executes(c->{var p=c.getSource().getPlayerOrThrow();if(!dev.sangwoo.celestialfall.map.MapGate.require(p))return 0;var s=ProgressManager.profile(p);c.getSource().sendFeedback(()->Text.literal("보유 금액: "+s.money()+"원  |  경험치: "+s.xp()+" / "+ProgressProfile.requiredXp(s.level())),false);return 1;});
        for(String action:new String[]{"add","set"})money.then(literal(action).requires(s->s.hasPermissionLevel(2)).then(argument("targets",EntityArgumentType.players()).then(argument("amount",LongArgumentType.longArg(0,ProgressProfile.MAX_MONEY)).executes(c->{
            var players=EntityArgumentType.getPlayers(c,"targets");long n=LongArgumentType.getLong(c,"amount");
            for(var p:players)if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())){c.getSource().sendError(Text.literal("보탠드 맵의 플레이어만 변경할 수 있어요."));return 0;}
            for(var p:players){var s=ProgressManager.profile(p);ProgressManager.set(p,s.withMoney(action.equals("set")?n:s.money()+n));}
            c.getSource().sendFeedback(()->Text.literal(players.size()+"명의 보유 금액을 변경했습니다."),true);return players.size();
        }))));root.then(money);
    }
    private CommerceCommands(){}
}
