package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * T held's dash, moved by the caster's own client (a player's movement is its own): at the start of each client tick
 * of the dash the local caster is given the velocity that takes him to the next point of the path the server plans
 * the same way ({@link KagenoRules#rushDestination}, {@link KagenoRules#rushStep}): just short of the first one a
 * slash struck (its held spot, as the server sends it) or to the end of the range after the last slash thrown (the way
 * the server threw it, {@link StandClient.Cast#aims}), aimed again every tick so a strike
 * while it runs turns it; at its end he stops. Server velocity packets, one a tick, were merged when several reached
 * a client within one frame, and the dash fell short in multiplayer.
 */
public final class RushDash {
    private RushDash(){}
    /** The planned point the caster was last sent to, and on which dash tick. */
    private record Plan(int tick,Vec3d at){}
    private static final Map<UUID,Plan> PLANS=new HashMap<>();

    /** Call at the start of every client tick, before the player moves. */
    public static void drive(MinecraftClient mc){
        var p=mc.player;PLANS.keySet().removeIf(id->!StandClient.CASTS.containsKey(id));
        if(p==null)return;
        StandClient.Cast cast=null;
        for(var c:StandClient.CASTS.values())if(c.skill()==KagenoRules.RUSH&&c.caster().equals(p.getUuid()))cast=c;
        if(cast==null)return;
        int k=(int)Math.floor(cast.age(0))-KagenoRules.RUSH_START;
        if(k<0||k>KagenoRules.RUSH_DASH)return;
        var plan=PLANS.get(cast.id());
        if(plan!=null&&plan.tick()>=k)return;
        if(k==KagenoRules.RUSH_DASH){
            if(plan!=null)p.setVelocity(0,Math.min(0,p.getVelocity().y),0);
            PLANS.put(cast.id(),new Plan(k,plan==null?p.getPos():plan.at()));
            return;
        }
        Vec3d from=plan==null?p.getPos():plan.at(),mark=cast.targets().isEmpty()?null:cast.targets().get(0).position();
        Vec3d dest=KagenoRules.rushDestination(cast.origin(),Vec3d.fromPolar(0,dashYaw(cast)),from,mark);
        // A cast first seen mid-dash catches up with the share already due.
        double share=plan==null?KagenoRules.rushDashed(k):KagenoRules.rushStep(k);
        Vec3d to=from.add(dest.subtract(from).multiply(1,0,1).multiply(share)),v=to.subtract(p.getPos()).multiply(1,0,1);
        p.setVelocity(v.x,Math.min(0,p.getVelocity().y),v.z);
        PLANS.put(cast.id(),new Plan(k,to));
    }
    /** The way the server dashes when no slash struck anyone: after the last slash thrown, the way it threw it. */
    private static float dashYaw(StandClient.Cast c){
        for(int i=c.aims().length-1;i>=0;i--)if(!Float.isNaN(c.aims()[i]))return c.aims()[i];
        return RushAim.dash(c,0);
    }
}
