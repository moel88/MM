package dev.sangwoo.celestialfall.client.kageno;

import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Skill id → rebuilt visual implementation. */
public final class KagenoSkills {
    private KagenoSkills(){}
    private static final Fx.Skill[] SKILLS=new Fx.Skill[16];
    static {
        SKILLS[M1]=new ComboSkill();
        SKILLS[E]=new NeonThrustSkill();
        SKILLS[R]=new TimeWalkerSkill();
        SKILLS[T]=new SparkSkill();
        SKILLS[RUSH]=new ThroughSkill();
        SKILLS[Y]=new FallenSkill();
        SKILLS[H]=new HighestSkill();
        SKILLS[P]=new PoseSkill();
        // Lee So-sang's sword's four hits (2.77.0): drawn with the same passes, from their own swings (IsosangClient)
        SKILLS[dev.sangwoo.celestialfall.isosang.IsosangRules.SKILL]=new dev.sangwoo.celestialfall.client.isosang.IsosangSlash();
        // X Chara's knife's four hits (2.114.0): drawn with the same passes, from their own swings (KnifeClient)
        SKILLS[dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL]=new dev.sangwoo.celestialfall.client.xchara.KnifeCombo();
        // X Chara's G (2.112.0): drawn with the same passes, from its own casts (XCharaClient)
        SKILLS[dev.sangwoo.celestialfall.xchara.XCharaRules.SKILL]=new dev.sangwoo.celestialfall.client.xchara.SwordDance();
    }
    public static Fx.Skill of(int skill){return skill>=0&&skill<SKILLS.length?SKILLS[skill]:null;}
}
