package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.LongCooldowns;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * This player's long cooldowns as the server keeps them (2.73.0, LongCooldowns): Atomic's, the time stop's and the
 * beatdown's ready ticks and lengths, told on joining and whenever one changes (begun again in full on coming back or a
 * reset). The cooldown list draws those three from here once it has heard ({@link #known}).
 */
public final class LongCooldownsClient {
    private static final int N=LongCooldowns.Skill.values().length;
    private static final long[] READY=new long[N];
    private static final int[] LENGTH=new int[N];
    private static boolean known;
    private LongCooldownsClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(LongCooldowns.STATE,(mc,handler,b,response)->{
            if(b.readableBytes()<N*9)return;
            long[] ready=new long[N];int[] length=new int[N];
            for(int i=0;i<N;i++){ready[i]=b.readLong();length[i]=b.readVarInt();}
            mc.execute(()->{System.arraycopy(ready,0,READY,0,N);System.arraycopy(length,0,LENGTH,0,N);known=true;});
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(LongCooldownsClient::clear));
    }
    static void clear(){java.util.Arrays.fill(READY,0);java.util.Arrays.fill(LENGTH,0);known=false;}
    /** Whether the server has told them since joining. */
    public static boolean known(){return known;}
    /** The tick this skill is ready again (world time; 0 for none). */
    public static long ready(LongCooldowns.Skill skill){return READY[skill.ordinal()];}
    /** Its cooldown's whole length (ticks). */
    public static int length(LongCooldowns.Skill skill){return LENGTH[skill.ordinal()];}
}
