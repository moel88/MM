package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.SpellTimeline;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

public final class ClientSpell {
    public final UUID id;
    public final UUID caster;
    public final Identifier dimension;
    public final Vec3d target;
    public final long start;
    public final float yaw;
    public final int visualRadius = 96;
    public int lastAge;
    public ClientSpell(UUID id, Identifier dimension, Vec3d target, long start, float yaw, UUID caster, int currentAge) {
        this.id = id; this.caster = caster; this.dimension = dimension; this.target = target; this.start = start; this.yaw = yaw;
        this.lastAge = currentAge - 1;
    }
    public float age(long worldTime, float delta) { return Math.max(0, worldTime - start + delta); }
    public float visibility(long worldTime, float delta) { return SpellTimeline.visibility(age(worldTime, delta)); }
}
