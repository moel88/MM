package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;

/**
 * X Chara's delete slash as it goes, drawn (2.133.0): the skill of EraseClient's casts (EraseRules.SKILL), one for each
 * slash, its clock the slash's own (from when it went), its origin where its user stood. Everything it draws is
 * EraseFx's, on the effects' layer: the blade in the glitch pass, the ground, whom it deleted and what is round the blade
 * in the others, the ground's pieces among the solids.
 */
public final class EraseZone implements Fx.Skill {
    /** How long one is drawn for (seconds after it went): the last of the ground it came over drawn back by then. */
    public static final float LIFE=2.8f;
    @Override public boolean layered(){return true;}
    @Override public float lifetime(){return LIFE;}
    /** None: there is no body of its own to copy (its user's is the knife's cast's). */
    @Override public float afterimages(float sec){return 0;}
    @Override public void glitch(Fx c){EraseFx.wave(c);}
    @Override public void alpha(Fx c,EffectMesh m){EraseFx.zone(c,m,false);}
    @Override public void additive(Fx c,EffectMesh m){EraseFx.zone(c,m,true);}
    @Override public void frame(Fx c,FxFrame f){EraseFx.frame(c,f);}
    @Override public void solids(Fx c,FxSolids s){EraseFx.solids(c,s);}
    @Override public float shake(StandClient.Cast cast,float sec){var w=EraseClient.WAVES.get(cast.id());return w==null||w.cast!=cast?0:EraseFx.shake(w,sec);}
}
