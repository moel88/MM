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
    static void burst(EffectMesh m,Vec3d center,Vec3d right,Vec3d up,float life,float duration,double radius,int count){
        if(life<0||life>=duration)return;float t=life/duration,alpha=(1-t)*(1-t);
        for(int j=0;j<count;j++){
            double angle=j*2.39996,spread=radius*(.5+.5*Math.sin(j*9.31));
            Vec3d axis=right.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle))),normal=right.multiply(-Math.sin(angle)).add(up.multiply(Math.cos(angle)));
            Vec3d root=center.add(axis.multiply(t*spread*.65)),tip=center.add(axis.multiply((.3+t)*spread));
            m.triangle(tip,root.add(normal.multiply(.075*alpha)),root.subtract(normal.multiply(.075*alpha)),0xffefff,alpha);
            m.glowLine(root,tip,.022,0xd472ff,alpha);
        }
        m.star(center,radius*.27*alpha,0xffd7ff,alpha);
    }
    static void shock(EffectMesh m,Vec3d origin,float life,float duration,double radius){
        if(life<0||life>=duration)return;float t=life/duration,alpha=(1-t)*(1-t);double r=.3+radius*Math.sqrt(t);
        for(int j=0;j<64;j++){
            double a=j*Math.PI/32,b=(j+1)*Math.PI/32;
            Vec3d p=origin.add(Math.cos(a)*r,0,Math.sin(a)*r),q=origin.add(Math.cos(b)*r,0,Math.sin(b)*r);
            m.vertex(p,0xe7b3ff,0);m.vertex(q,0xe7b3ff,0);m.vertex(origin.add(Math.cos(b)*r*.81,0,Math.sin(b)*r*.81),0xfdf3ff,alpha*.75f);m.vertex(origin.add(Math.cos(a)*r*.81,0,Math.sin(a)*r*.81),0xfdf3ff,alpha*.75f);
        }
        m.ring(origin,new Vec3d(1,0,0),new Vec3d(0,0,1),r,64,1,.045,0xffffff,alpha,0);
    }
}
