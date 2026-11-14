package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.RainRules;

/**
 * X Chara's rain of knives as it rains, drawn (2.132.5): the skill of RainClient's casts (RainRules.SKILL), one for each
 * rain, its clock its call's, its origin its middle. Everything it draws is RainFx's, on the effects' layer.
 */
public final class RainZone implements Fx.Skill {
    @Override public boolean layered(){return true;}
    /** As long as it rains, and what its last knives leave. */
    @Override public float lifetime(){return (RainRules.LAST+20)/20f;}
    @Override public void alpha(Fx c,EffectMesh m){RainFx.zone(c,m,false);}
    @Override public void additive(Fx c,EffectMesh m){RainFx.zone(c,m,true);}
    @Override public void frame(Fx c,FxFrame f){RainFx.zoneFrame(c,f);}
}
