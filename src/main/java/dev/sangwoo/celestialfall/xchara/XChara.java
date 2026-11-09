package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * X Chara (2.111.0, user request: "이제 신규 스탠드 만들자 스탠드 이름은 엑스 차라 검 모델링부터 보내줄게 대충 이걸 참고해"):
 * a character who fights in person with their knife (the user's answer: their player holds it, as Kageno and Lee So-sang
 * hold their swords), given by the admin command for now (/botand progress stand <targets> x_chara). Their knife is all
 * of them so far; their skills come as the user gives them.
 */
public final class XChara {
    public static final XCharaKnifeItem KNIFE=new XCharaKnifeItem();
    private XChara(){}
    public static void initialize(){Registry.register(Registries.ITEM,CelestialFall.id("x_chara_knife"),KNIFE);}
}
