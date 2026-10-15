package dev.sangwoo.celestialfall.client;

import net.minecraft.util.math.Vec3d;

/** Short-lived deterministic meshes: the same strike has the same shape on every viewer. */
final class KagenoVfx {
    private static final Vec3d UP=new Vec3d(0,1,0);
    private KagenoVfx(){}
    static float pulse(int skill,float age){
        float t=switch(skill){case 0->age-6;case 1->age>=50?age-52:age<8?99:(age-10)%6;case 2->age-36;case 3->age-dev.sangwoo.celestialfall.stand.KagenoRules.T_STRIKE;case 4->age>=dev.sangwoo.celestialfall.stand.KagenoRules.RUSH_FINISH?age-dev.sangwoo.celestialfall.stand.KagenoRules.RUSH_FINISH:age-dev.sangwoo.celestialfall.stand.KagenoRules.RUSH_IGNITE;case 5->age-20;case 7->age>=80&&age<105?(age-80)%2:99;default->99;};
        return t<0?0:(float)Math.exp(-t*.55)*.6f;
    }
}
