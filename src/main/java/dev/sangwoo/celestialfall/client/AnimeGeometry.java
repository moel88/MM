package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.MountainScale;
import dev.sangwoo.celestialfall.SpellTimeline;
import net.minecraft.util.math.Vec3d;

/** Geometry is authored at one fifth world scale, matching the sword mesh. */
final class AnimeGeometry {
    private static final Vec3d X=new Vec3d(1,0,0),Y=new Vec3d(0,1,0),Z=new Vec3d(0,0,1);
    private AnimeGeometry() {}
    static void rift(EffectMesh m,ClientSpell s,float age) {
        if(age<130 || age>183)return;
        float open=SpellTimeline.smooth(SpellTimeline.progress(age,130,142))*(1-SpellTimeline.smooth(SpellTimeline.progress(age,164,183)));
        Vec3d c=s.target.add(0,MountainScale.SUMMON_HEIGHT/MountainScale.MODEL+.3,0);
        double radius=21*open;
        for(int i=0;i<80;i++) {
            double a=i*Math.PI/40,b=(i+1)*Math.PI/40;
            double r1=radius*(1+.08*Math.sin(i*2.3)),r2=radius*(1+.08*Math.sin((i+1)*2.3));
            m.triangle(c,c.add(Math.cos(a)*r1,0,Math.sin(a)*r1*.48),c.add(Math.cos(b)*r2,0,Math.sin(b)*r2*.48),0x030008,1);
        }
    }
    static void energy(EffectMesh m,ClientSpell s,float age) {
        Vec3d hub=s.target.add(0,MountainScale.SUMMON_HEIGHT/MountainScale.MODEL+.4,0);
        float pulse=SpellTimeline.clashPulse(age);
        if(age>=32 && age<174) {
            float show=SpellTimeline.visibility(age)*(1-SpellTimeline.progress(age,156,174));
            for(int band=0;band<3;band++) {
                double radius=42+band*5;
                m.ring(hub.add(0,band*1.2,0),X,Z,radius,96,1,.07+pulse*.17,band==1?0xA9E7FF:0xA35AFF,
                    show*(.35f+pulse*.5f),age*.003*(band%2==0?1:-1));
            }
            for(int branch=0;branch<18;branch++) {
                double angle=branch*2.399+s.yaw*.01;
                Vec3d prev=hub;
                for(int step=1;step<=7;step++) {
                    double radius=step*(5+branch%4),a=angle+.13*Math.sin(step*4.3+branch+Math.floor(age/3)*.6);
                    Vec3d next=hub.add(Math.cos(a)*radius,Math.sin(step+branch)*1.8,Math.sin(a)*radius);
                    m.glowLine(prev,next,.07+pulse*.2,branch%3==0?0xDAF6FF:0xA86FFF,show*(.16f+pulse*.7f));
                    prev=next;
                }
            }
        }
        if(age>=130 && age<184) {
            float open=SpellTimeline.smooth(SpellTimeline.progress(age,130,142))*(1-SpellTimeline.progress(age,165,184));
            for(int i=0;i<72;i++) {
                double a=i*Math.PI/36,b=(i+1)*Math.PI/36,r=21*open;
                m.glowLine(hub.add(Math.cos(a)*r,0,Math.sin(a)*r*.48),hub.add(Math.cos(b)*r,0,Math.sin(b)*r*.48),.28,0xBD72FF,open);
            }
        }
        if(age>=SpellTimeline.DESCENT_START && age<SpellTimeline.CONTACT) {
            float speed=SpellTimeline.descentPower(age);
            Vec3d tip=s.target.add(0,SpellTimeline.swordTipHeight(age)/MountainScale.MODEL,0);
            for(int band=0;band<8;band++) {
                double t=(age*.035+band/8.0)%1;
                m.ring(tip.add(0,5+t*78,0),X,Z,3+t*13,64,1,.11+speed*.2,band%2==0?0xDFB4FF:0xADF0FF,
                    (float)(1-t)*speed*.8f,age*.018);
            }
            for(int strand=0;strand<8;strand++) {
                Vec3d previous=null;
                for(int step=0;step<=18;step++) {
                    double t=step/18.0,a=strand*Math.PI/4+t*5-age*.085,r=2+t*8;
                    Vec3d point=tip.add(Math.cos(a)*r,t*85,Math.sin(a)*r);
                    if(previous!=null)m.glowLine(previous,point,.25*(1-t)+.05,strand%2==0?0xA659FF:0xE3BDFF,(float)(1-t)*speed);
                    previous=point;
                }
            }
        }
        float t=age-SpellTimeline.IMPACT;
        if(t>=0 && t<175) {
            float fade=1-SpellTimeline.progress(t,50,175);
            Vec3d base=s.target.add(0,.08,0);
            m.ring(base,X,Z,19,96,1,.15,0xCA8EFF,fade,age*.002);
            m.ring(base,X,Z,22,96,1,.07,0xA46DFF,fade*.6f,-age*.003);
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6+age*.002;
                m.glowLine(base.add(Math.cos(a)*4,0,Math.sin(a)*4),base.add(Math.cos(a+.12)*21,0,Math.sin(a+.12)*21),.09,0xBB7EFF,fade*.7f);
            }
        }
    }
}
