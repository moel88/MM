package dev.sangwoo.botandverify;

import dev.sangwoo.celestialfall.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import static dev.sangwoo.botandverify.CombatSmoke.*;

/** Actual loaded-entity queries and armor-bypassing damage at the shrinking boundary. */
public final class AtomicSmoke {
    public static void run(MinecraftServer server,Map<String,Object> checks)throws Exception {
        var world=server.getOverworld();
        var props=(net.minecraft.world.level.ServerWorldProperties)world.getLevelProperties();
        long original=world.getTime(),start=original+1000;
        List<ZombieEntity> mobs=new ArrayList<>();
        boolean terrain=CelestialFall.CONFIG.terrainDamage;CelestialFall.CONFIG.terrainDamage=false;
        try {
            Vec3d origin=new Vec3d(64,101,16);
            double[][] offsets={{.01,0,0},{20,0,0},{27.9,0,0},{28.1,0,0},{0,69,0},{0,71,0}};
            for(double[] offset:offsets) {
                var mob=EntityType.ZOMBIE.create(world);mob.setAiDisabled(true);
                mob.setPosition(origin.add(offset[0],offset[1],offset[2]));
                mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);
                world.getChunk(mob.getBlockPos());require(world.spawnEntity(mob),"Atomic fixture spawn");mobs.add(mob);
            }
            var type=Class.forName("dev.sangwoo.celestialfall.AtomicManager$Cast");
            var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
            Object cast=ctor.newInstance(UUID.randomUUID(),UUID.randomUUID(),world,origin,0f,start,28.,50.,new HashSet<UUID>());
            var damage=AtomicManager.class.getDeclaredMethod("damage",type);damage.setAccessible(true);
            var activeField=AtomicManager.class.getDeclaredField("ACTIVE");activeField.setAccessible(true);
            @SuppressWarnings("unchecked") var active=(List<Object>)activeField.get(null);active.add(cast);
            int[] contacts=new int[mobs.size()];
            for(int age=140;age<=740;age++) {
                props.setTime(start+age);
                for(var mob:mobs){mob.setHealth(1000);mob.timeUntilRegen=0;}
                AtomicManager.tick(server);
                double r=AtomicTimeline.blastRadius(age,28);
                for(int i=0;i<mobs.size();i++) {
                    double[] d=offsets[i];boolean inside=AtomicTimeline.damageDue(age)&&AtomicTimeline.insideColumn(d[0],d[1],d[2],r,70);
                    require(1000-mobs.get(i).getHealth()==(inside?50:0),"Atomic radius/height/damage mismatch at "+age+" entity "+i);
                    if(inside)contacts[i]++;
                }
            }
            require(contacts[0]==30&&contacts[4]==30,"Ground and high center must both receive 30 pulses");
            require(contacts[3]==0&&contacts[5]==0,"Horizontal/vertical outside targets must not be hit");
            require(contacts[1]<30&&contacts[1]>20,"Horizontal shrink must release outer target");
            require(active.isEmpty(),"Cast did not expire at 740");
            checks.put("atomic_white_frame_onset_30_live_ticks_50_damage_fixed_height",true);
            checks.put("atomic_28_radius_loaded_entity_damage_50_and_shrinking_boundary",true);
        } finally { CelestialFall.CONFIG.terrainDamage=terrain;for(var mob:mobs)mob.discard();props.setTime(original); }
    }
}
