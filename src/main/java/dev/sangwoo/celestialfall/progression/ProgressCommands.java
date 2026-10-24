package dev.sangwoo.celestialfall.progression;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.*;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import java.util.function.UnaryOperator;
import static net.minecraft.server.command.CommandManager.*;

public final class ProgressCommands {
    private ProgressCommands() {}
    public static void add(LiteralArgumentBuilder<ServerCommandSource> root) {
        root.then(literal("status").executes(c->{
            if(!dev.sangwoo.celestialfall.map.MapGate.enabled(c.getSource().getWorld())){c.getSource().sendError(Text.translatable("message.botand.map_only"));return 0;}
            var p=c.getSource().getPlayerOrThrow();var s=ProgressManager.profile(p);
            c.getSource().sendFeedback(()->Text.translatable("message.botand.status",s.level(),s.rebirths(),s.transcendence(),Text.translatable(StandStats.get(s.stand()).translation()),Math.round(p.getHealth()),Math.round(p.getMaxHealth())),false);
            p.sendMessage(Text.translatable("message.botand.rebirth_button").formatted(Formatting.GOLD).styled(st->st.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/botand rebirth")))
                .append(Text.literal("   ")).append(Text.translatable("message.botand.transcend_button").formatted(Formatting.AQUA).styled(st->st.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/botand transcend")))),false);
            return 1;
        }));
        root.then(literal("rebirth").executes(c->transition(c.getSource(),false)));
        root.then(literal("transcend").executes(c->transition(c.getSource(),true)));
        var admin=literal("progress").requires(s->s.hasPermissionLevel(2));
        admin.then(edit("setlevel",1,50,(p,n)->p.withLevel(n)));
        admin.then(edit("addlevels",1,50,(p,n)->p.addLevels(n)));
        admin.then(edit("setrebirths",0,15,(p,n)->p.withRebirths(n)));
        admin.then(edit("settranscendence",0,ProgressProfile.MAX_TRANSCENDENCE,(p,n)->p.withTranscendence(n)));
        var stand=literal("stand");var targets=argument("targets",EntityArgumentType.players());
        for(String id:StandStats.ids())targets.then(literal(id).executes(c->editPlayers(c.getSource(),EntityArgumentType.getPlayers(c,"targets"),p->p.withStand(id))));
        root.then(admin.then(stand.then(targets)));
    }
    private interface Edit {ProgressProfile apply(ProgressProfile p,int value);}
    private static LiteralArgumentBuilder<ServerCommandSource> edit(String name,int min,int max,Edit edit) {
        return literal(name).then(argument("targets",EntityArgumentType.players()).then(argument("value",IntegerArgumentType.integer(min,max))
            .executes(c->editPlayers(c.getSource(),EntityArgumentType.getPlayers(c,"targets"),p->edit.apply(p,IntegerArgumentType.getInteger(c,"value"))))));
    }
    private static int editPlayers(ServerCommandSource source,java.util.Collection<net.minecraft.server.network.ServerPlayerEntity> players,UnaryOperator<ProgressProfile> edit) {
        for(var p:players)if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())){source.sendError(Text.translatable("message.botand.map_only"));return 0;}
        for(var p:players)ProgressManager.set(p,edit.apply(ProgressManager.profile(p)));
        source.sendFeedback(()->Text.translatable("message.botand.progress_updated",players.size()),false);return players.size();
    }
    public static int transition(ServerCommandSource source,boolean transcend)throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=source.getPlayerOrThrow();if(!dev.sangwoo.celestialfall.map.MapGate.require(p))return 0;var s=ProgressManager.profile(p);
        if(!ProgressManager.canChange(p)){source.sendError(Text.translatable("message.botand.progress_busy"));return 0;}
        if(!(transcend?s.canTranscend():s.canRebirth())){source.sendError(Text.translatable(transcend?"message.botand.transcend_locked":"message.botand.rebirth_locked"));return 0;}
        var next=transcend?s.transcend():s.rebirth();ProgressManager.set(p,next);
        source.sendFeedback(()->Text.translatable(transcend?"message.botand.transcended":"message.botand.reborn",transcend?next.transcendence():next.rebirths()),false);return 1;
    }
}
