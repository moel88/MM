package dev.sangwoo.celestialfall.boss;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;

/**
 * A boss's body (2.69.0): a player on the map that nobody plays. It is a player so that it is one to every rule and
 * every skill the players have (its stand comes out beside it, its punches, its guard and its dash are the players'
 * own, every client draws it as it draws a player), and its brain plays it from the server: every tick, before its
 * body moves, the brain (BossBrain) chooses where it walks and looks and what it does. It is not in the server's list
 * of players (no one joined): Bosses keeps it, and PlayerManager finds it there (BossPlayerManagerMixin).
 */
public final class BossPlayer extends ServerPlayerEntity {
    public final BossKind kind;
    final BossBrain brain;
    /** Where it came, and which way it faced there: it stands there, neutral, and goes back there when its anger is gone. */
    Vec3d home;float homeYaw;
    public BossPlayer(ServerWorld world,BossKind kind,UUID id){
        super(world.getServer(),world,new GameProfile(id,kind.profile));
        this.kind=kind;
        new BossConnection(this);
        brain=kind==BossKind.JOTARO?new JotaroBrain(this):new BossBrain(this);
    }
    public BossBrain brain(){return brain;}
    public Vec3d home(){return home;}

    @Override public void tick(){
        // its body waits for the ground under it to be there, as a player's client waits for it
        boolean ground=getWorld().isChunkLoaded(getBlockPos());
        // the brain first (what it does this tick), then the player's own tick, then its body moves as a player's does on
        // its own client (a player's body moves where its client says it went; this one's moves here)
        if(ground&&!isRemoved()&&isAlive())brain.tick();
        super.tick();
        if(isRemoved())return;
        if(ground)playerTick();
        else{setVelocity(Vec3d.ZERO);forwardSpeed=sidewaysSpeed=0;}
    }
    @Override public boolean damage(DamageSource source,float amount){
        boolean hurt=super.damage(source,amount);
        // struck by a player (or held off by its guard): it is angry with them, and with everyone within its range
        if(source.getAttacker() instanceof ServerPlayerEntity p&&!(p instanceof BossPlayer))brain.provoked(p);
        return hurt;
    }
    /** It falls: no death message, no stats, nothing dropped; Bosses announces it and gives what it leaves. */
    @Override public void onDeath(DamageSource source){
        if(dead)return;
        dead=true;
        emitGameEvent(GameEvent.ENTITY_DIE);
        // every client hears it fall and sees it go down, as a player who dies
        getWorld().sendEntityStatus(this,(byte)3);
        extinguish();setFrozenTicks(0);setOnFire(false);
        Bosses.defeated(this,source);
        getDamageTracker().update();
    }
    /** Its name wherever the server writes it (a death message, a chat line): the boss's own, not its body's profile. */
    @Override public Text getDisplayName(){return Text.literal(kind.title);}
    @Override public Text getName(){return Text.literal(kind.title);}
    @Override public AbstractTeam getScoreboardTeam(){return null;}
    @Override public void increaseStat(Stat<?> stat,int amount){}
    @Override public void resetStat(Stat<?> stat){}
    @Override public boolean canFoodHeal(){return false;}
}
