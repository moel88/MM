package dev.sangwoo.celestialfall.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Lee So-sang (Jade Knight · Moon Scar, 2.76.0): a character who fights in person, as Kageno does, given by the admin
 * command for now (/botand progress stand <targets> isosang). Their sword is all of them so far; their skills come as the
 * user gives them.
 */
public final class Isosang {
    public static final IsosangSwordItem SWORD=new IsosangSwordItem();
    private Isosang(){}
    public static void initialize(){Registry.register(Registries.ITEM,CelestialFall.id("isosang_sword"),SWORD);}
}
