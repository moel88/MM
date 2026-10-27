package dev.sangwoo.botandmapmp;

import dev.sangwoo.celestialfall.commerce.MerchantEntity;
import dev.sangwoo.celestialfall.loot.FieldLootEntity;
import dev.sangwoo.celestialfall.loot.LootPositions;
import dev.sangwoo.celestialfall.map.MapGate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import dev.sangwoo.celestialfall.meteor.*;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;

/**
 * The map's multiplayer check, server half (only with -Dbotand.mapmp.server=true): on a dedicated server running the
 * built seaside world with the release jar, logs whether the mod takes the world for a Botand map from its definition,
 * where the merchants and the field supplies stand, and where each player is and whether the map is on for them.
 * Meteors (2.49.0): once both visitors say "meteor-ready" in the chat, drops one 14 blocks east of the spawn, puts the
 * first visitor beside it with a meteor pickaxe as it lands, and logs the meteor and what each player carries every
 * second until it is mined out (or half a minute has gone).
 */
public final class MapMpServer implements ModInitializer {
    static final Path OUT=Path.of(System.getProperty("botand.mapmp.out","mapmp"));
    private final StringBuilder log=new StringBuilder();
    private int ticks,launched=-1;private boolean finished;
    private final Set<String> ready=new HashSet<>();
    private MeteorState.Meteor meteor;
    private static final String[] ROLES={"seller","buyer","crafter"};

    @Override public void onInitialize(){
        if(!Boolean.getBoolean("botand.mapmp.server"))return;
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerMessageEvents.CHAT_MESSAGE.register((message,sender,params)->{
            if(message.getContent().getString().equals("meteor-ready")&&ready.add(sender.getName().getString()))note("ready for the meteor: "+sender.getName().getString()+" at tick "+ticks);
        });
    }
    private void note(String s){
        log.append(s).append('\n');System.out.println("[mapmp] "+s);
        try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("server.txt"),log);}catch(Exception ignored){}
    }
    /** Noted on the first tick, after the mod's own start (MapGate reads the definition when the server has started,
     *  and its listener can come after this one). */
    private void started(MinecraftServer server){
        var w=server.getOverworld();
        note("started: botand map="+MapGate.isBotandMap(server)+" city="+MapGate.isCity(server)+" enabled="+MapGate.enabled(w)
            +" definition="+MapGate.definition(server).map(d->d.name()+" ("+d.id()+"), "+d.loot().size()+" supply spots, places "+d.places().keySet()).orElse("none"));
        note("supply spots in use: "+LootPositions.current().size()+(LootPositions.current()==LootPositions.ALL?" (the city's)":" (the definition's)"));
        note("world spawn: "+w.getSpawnPos()+" time "+w.getTimeOfDay()+" daylight cycle "+server.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE));
    }
    private void tick(MinecraftServer server){
        ticks++;
        if(ticks==1)started(server);
        meteor(server);
        // checked every tick (700 ticks after the launch is seldom on one of the 200-tick reports below); without both
        // visitors ready it gives up after five minutes (a loaded machine can take over two to bring the second in)
        if(!finished&&(launched<0?ready.size()<2&&ticks>=6000:ticks>=2400&&ticks-launched>=700)){finished=true;note("done");}
        if(ticks%200!=0||ticks>2400&&!(launched>=0&&ticks-launched<=700))return;
        var w=server.getOverworld();
        StringBuilder s=new StringBuilder("tick "+ticks+":");
        for(var e:w.iterateEntities()){
            if(e instanceof MerchantEntity m)s.append(" merchant[").append(ROLES[m.role]).append(" ").append(fmt(m.getPos())).append(" yaw ").append(Math.round(m.getYaw())).append("]");
            if(e instanceof FieldLootEntity l)s.append(" supply[").append(l.slot()).append(" ").append(fmt(l.getPos())).append("]");
        }
        for(var p:server.getPlayerManager().getPlayerList())s.append(" player[").append(p.getName().getString()).append(" ").append(fmt(p.getPos()))
            .append(" map=").append(MapGate.enabled(p.getWorld())).append(" hp=").append(Math.round(p.getMaxHealth())).append(" mode=").append(p.interactionManager.getGameMode().getName()).append("]");
        note(s.toString());
    }
    /** The meteor check (see the class notes). */
    private void meteor(MinecraftServer server){
        var w=server.getOverworld();
        if(launched<0&&ready.size()>=2){
            var sp=w.getSpawnPos();int slot=MeteorState.get(w).firstEmpty();
            meteor=MeteorManager.launch(w,slot,new Vec3d(sp.getX()+14.5,w.getTopY(),sp.getZ()+.5),Double.NaN);
            launched=ticks;
            note("meteor launched: "+(meteor==null?"refused":"#"+meteor.slot()+" to "+fmt(meteor.pos())+" from "+fmt(meteor.from())+" lands at tick "+(ticks+MeteorRules.FALL_TICKS)));
        }
        if(launched<0||meteor==null)return;
        int t=ticks-launched;
        if(t==MeteorRules.FALL_TICKS+18){
            var p=server.getPlayerManager().getPlayer("Tabibito");
            if(p!=null){
                p.teleport(w,meteor.x()-3.6,meteor.y(),meteor.z(),270,20);
                p.getInventory().setStack(0,new net.minecraft.item.ItemStack(MeteorRegistry.PICKAXE));p.getInventory().selectedSlot=0;p.currentScreenHandler.sendContentUpdates();
                p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(0));
                note("Tabibito put beside the rock with a meteor pickaxe at tick "+ticks);
            }
        }
        if(t%20==0&&t<=700){
            var m=MeteorState.get(w).slot(meteor.slot());
            var rock=w.getEntity(meteor.uuid());
            StringBuilder s=new StringBuilder("meteor t="+t+": ");
            s.append(m==null||m.cycle()!=meteor.cycle()?"gone":"fragments "+m.fragments()+" blows "+m.hits()+(m.landed(w.getTime())?" landed":" falling")).append(" rock entity ").append(rock!=null);
            for(var p:server.getPlayerManager().getPlayerList()){
                int fr=0;for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(MeteorRegistry.FRAGMENT))fr+=p.getInventory().getStack(i).getCount();
                s.append(" | ").append(p.getName().getString()).append(" fragments ").append(fr);
            }
            note(s.toString());
        }
    }
    private static String fmt(net.minecraft.util.math.Vec3d v){return String.format("%.1f,%.1f,%.1f",v.x,v.y,v.z);}
}
