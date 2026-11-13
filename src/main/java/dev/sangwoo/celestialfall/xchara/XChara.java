package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * X Chara (2.111.0, user request: "이제 신규 스탠드 만들자 스탠드 이름은 엑스 차라 검 모델링부터 보내줄게 대충 이걸 참고해"):
 * a character who fights in person with their knife (the user's answer: their player holds it, as Kageno and Lee So-sang
 * hold their swords), given by the admin command for now (/botand progress stand <targets> x_chara). Their knife
 * (2.111.0), their G, the sword dance (2.112.0, XCharaUltimate), and their basic attack, the knife's four hits (2.114.0,
 * XCharaAttack), and their Q, the hack (2.115.0, XCharaHack: their knife lit and a panel in their other hand, which
 * {@link #PANEL} is drawn as); their other skills come as the user gives them.
 */
public final class XChara {
    public static final XCharaKnifeItem KNIFE=new XCharaKnifeItem();
    /** The hack's panel (2.115.0): drawn in the other hand of X Chara's player while their hack is on (StandClient.panel); nobody has it in an inventory, and it does nothing of itself. */
    public static final net.minecraft.item.Item PANEL=new net.minecraft.item.Item(new net.minecraft.item.Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.EPIC).fireproof());
    private XChara(){}
    public static void initialize(){Registry.register(Registries.ITEM,CelestialFall.id("x_chara_knife"),KNIFE);Registry.register(Registries.ITEM,CelestialFall.id("x_chara_panel"),PANEL);XCharaUltimate.initialize();XCharaAttack.initialize();XCharaHack.initialize();XCharaHeal.initialize();XCharaClone.initialize();XCharaWeaken.initialize();}
}
