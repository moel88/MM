package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Casts aimed while they play (user request, 2.38.2): T held's slashes each fly the way the caster faces as they leave
 * ({@link RushAim}), and E's thrusts follow his aim until its finisher is planted ({@link KagenoRules#E_PLANT}). The
 * server keeps each way it took ({@link StandClient.Cast#aims}); until that reaches this client, the caster's facing as
 * this client saw that moment stands in for it, then turns to the server's over {@link #SETTLE} ticks. The caster's own
 * client keeps what he aimed.
 */
public final class KagenoAim {
    private KagenoAim(){}
    /** Per cast: each kept way as this client saw it (degrees, NaN before), then when the server's way was first shown (world ticks, NaN before). */
    private static final Map<UUID,float[]> SEEN=new LinkedHashMap<>();
    /** Ticks over which a way this client saw turns to the server's once that arrives. */
    static final float SETTLE=2;

    /** The caster's facing now (degrees): his own aim at this client, his body as tracked anywhere else. */
    static float live(StandClient.Cast c,float delta){
        var mc=MinecraftClient.getInstance();var e=mc.world==null?null:mc.world.getPlayerByUuid(c.caster());
        return e==null?c.yaw():e.getYaw(delta);
    }
    static boolean own(StandClient.Cast c){var p=MinecraftClient.getInstance().player;return p!=null&&p.getUuid().equals(c.caster());}
    private static float[] seen(StandClient.Cast c){
        var s=SEEN.get(c.id());
        if(s==null){s=new float[2*c.aims().length];java.util.Arrays.fill(s,Float.NaN);SEEN.put(c.id(),s);while(SEEN.size()>32)SEEN.remove(SEEN.keySet().iterator().next());}
        return s;
    }
    /** The way kept in slot {@code i} from {@code at} ticks into the cast (degrees); before then, the caster's facing now. */
    static float kept(StandClient.Cast c,int i,float at,float delta){
        if(c.age(delta)<at)return live(c,delta);
        float[] s=seen(c);if(Float.isNaN(s[i]))s[i]=live(c,delta);
        float server=c.aims()[i];
        if(own(c)||Float.isNaN(server))return s[i];
        var w=MinecraftClient.getInstance().world;float now=w==null?0:w.getTime()+delta;
        int k=c.aims().length+i;if(Float.isNaN(s[k]))s[k]=now;
        return s[i]+MathHelper.wrapDegrees(server-s[i])*Ease.smooth(now-s[k],0,SETTLE);
    }
    /** E: the way the body and the thrusts face (degrees), with the aim through the thrusts, then the planted way. */
    public static float thrust(StandClient.Cast c,float delta){return kept(c,0,KagenoRules.E_PLANT,delta);}
    /** Whether E's caster still aims and walks (the thrusts): his facing follows his camera until the finisher is planted. */
    public static boolean aimingThrust(StandClient.Cast c){return c.skill()==KagenoRules.E&&KagenoRules.eWalking(c.age(0));}
}
