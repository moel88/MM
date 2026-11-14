package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.xchara.EraseRules;

/**
 * X Chara's terrain delete once its grounds are marked, drawn (2.133.0): the skill of EraseClient's casts
 * (EraseRules.SKILL), one for each call, its clock ticks since the mark, its origin where its user stood. Everything it
 * draws is EraseFx's, on the effects' layer: the "!!!" and the marked grounds, each breaking free, the cubes' glitches,
 * their bursts, the voids and whom they struck in the blended and the light passes, the cubes' blocks and the pieces of
 * ground among the solids.
 */
public final class EraseZone implements Fx.Skill {
    /** How long one is drawn for (seconds after the mark): until the ground is back, and a little. */
    public static final float LIFE=EraseRules.BACK/20f+EraseFx.AFTER;
    @Override public boolean layered(){return true;}
    @Override public float lifetime(){return LIFE;}
    /** None: there is no body of its own to copy (its user's is the knife's cast's). */
    @Override public float afterimages(float sec){return 0;}
    @Override public void alpha(Fx c,EffectMesh m){EraseFx.zone(c,m,false);}
    @Override public void additive(Fx c,EffectMesh m){EraseFx.zone(c,m,true);}
    @Override public void frame(Fx c,FxFrame f){EraseFx.frame(c,f);}
    @Override public void solids(Fx c,FxSolids s){EraseFx.solids(c,s);}
    @Override public float shake(StandClient.Cast cast,float sec){var c=EraseClient.CALLS.get(cast.id());return c==null||c.cast!=cast?0:EraseFx.shake(c,sec);}
}
