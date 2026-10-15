package dev.sangwoo.celestialfall.training;

/** Normal dummies match the default stand's 200 HP; combo dummies have ten times that. */
public enum DummyKind {
    IDLE("idle",200), ATTACK("attack",200), GUARD("guard",200), COMBO("combo",2000);
    public final String id;
    public final float health;
    DummyKind(String id,float health){this.id=id;this.health=health;}
}
