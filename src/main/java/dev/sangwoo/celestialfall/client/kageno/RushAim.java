package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * T held is aimed while its slashes are thrown (user request, 2.38.2): each slash flies the way the caster faces as it
 * leaves, and his body turns with his aim until the dash, then faces the way he dashes. The server keeps the way it
 * threw each slash ({@link StandClient.Cast#aims}); until that reaches this client, the caster's facing as this client
 * saw the slash leave stands in for it, and the caster's own client keeps what he aimed.
 */
public final class RushAim {
    private RushAim(){}
    /** Each slash's way as this client saw it leave, per cast (degrees, NaN before). */
    private static final Map<UUID,float[]> SEEN=new LinkedHashMap<>();

    /** The caster's facing now (degrees): his own aim at this client, his body as tracked anywhere else. */
    static float live(StandClient.Cast c,float delta){
        var mc=MinecraftClient.getInstance();var e=mc.world==null?null:mc.world.getPlayerByUuid(c.caster());
        return e==null?c.yaw():e.getYaw(delta);
    }
    private static boolean own(StandClient.Cast c){var p=MinecraftClient.getInstance().player;return p!=null&&p.getUuid().equals(c.caster());}
    private static float[] seen(StandClient.Cast c){
        var s=SEEN.get(c.id());
        if(s==null){s=new float[]{Float.NaN,Float.NaN,Float.NaN};SEEN.put(c.id(),s);while(SEEN.size()>32)SEEN.remove(SEEN.keySet().iterator().next());}
        return s;
    }
    /** The way slash {@code i} was thrown (degrees); before it leaves, the way it would go now. */
    public static float slash(StandClient.Cast c,int i,float delta){
        if(c.age(delta)<KagenoRules.RUSH_SLASHES[i])return live(c,delta);
        float[] seen=seen(c);if(Float.isNaN(seen[i]))seen[i]=live(c,delta);
        float server=c.aims()[i];
        return own(c)||Float.isNaN(server)?seen[i]:server;
    }
    /** Whether slash {@code i} has left by {@code delta}. */
    static boolean thrown(StandClient.Cast c,int i,float delta){return c.age(delta)>=KagenoRules.RUSH_SLASHES[i];}
    /** The way the dash runs (degrees): to the first one a slash struck (where the server held them), else after the last slash thrown. */
    public static float dash(StandClient.Cast c,float delta){
        if(!c.targets().isEmpty()){
            Vec3d d=c.targets().get(0).position().subtract(c.origin());
            if(d.horizontalLengthSquared()>1)return (float)Math.toDegrees(Math.atan2(-d.x,d.z));
        }
        for(int i=KagenoRules.RUSH_SLASHES.length-1;i>=0;i--)if(thrown(c,i,delta))return slash(c,i,delta);
        return live(c,delta);
    }
    /** The way the body faces (degrees): with the aim until the dash, turning to the dash's way as it leaves. */
    public static float facing(StandClient.Cast c,float delta){
        float t=c.age(delta)/20f,start=KagenoRules.RUSH_START/20f,aim=live(c,delta);
        if(t<start-.05f)return aim;
        return aim+MathHelper.wrapDegrees(dash(c,delta)-aim)*Ease.smooth(t,start-.05f,start+.03f);
    }
    /** Whether the caster still aims (the slashes are being thrown): his facing follows his camera until the dash. */
    public static boolean aiming(StandClient.Cast c){return c.skill()==KagenoRules.RUSH&&c.age(0)<KagenoRules.RUSH_START;}
}
