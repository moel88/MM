package dev.sangwoo.celestialfall.client.kageno;

import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Skill id → rebuilt visual implementation. */
public final class KagenoSkills {
    private KagenoSkills(){}
    private static final Fx.Skill[] SKILLS=new Fx.Skill[16];
    static {
        SKILLS[M1]=new ComboSkill();
        SKILLS[E]=new BarrageSkill();
        SKILLS[R]=new TimeWalkerSkill();
        SKILLS[T]=new SparkSkill();
        SKILLS[RUSH]=new ThroughSkill();
        SKILLS[Y]=new FallenSkill();
        SKILLS[H]=new HighestSkill();
        SKILLS[P]=new PoseSkill();
    }
    public static Fx.Skill of(int skill){return skill>=0&&skill<SKILLS.length?SKILLS[skill]:null;}
}
