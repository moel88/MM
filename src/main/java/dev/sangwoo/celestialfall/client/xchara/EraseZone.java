package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.xchara.EraseRules;

/**
 * X Chara's delete slash once it is thrown, drawn (2.133.0): the skill of EraseClient's casts (EraseRules.SKILL), one for
 * each slash, its clock the slash's own (from when it was thrown), its origin the ground it lands on. Everything it draws
 * is EraseFx's, on the effects' layer: the slash in the glitch pass, where it lands, its cube's glitches, its burst, the
 * void and whom it struck in the others, its cube's blocks and the pieces of ground among the solids.
 */
public final class EraseZone implements Fx.Skill {
    /** How long one is drawn for (seconds after it was thrown): its flight at the longest, then until the ground is back, and a little. */
    public static final float LIFE=(EraseRules.FLY_MAX+EraseRules.BACK)/20f+EraseFx.AFTER;
    @Override public boolean layered(){return true;}
    @Override public float lifetime(){return LIFE;}
    /** None: there is no body of its own to copy (its user's is the knife's cast's). */
    @Override public float afterimages(float sec){return 0;}
    @Override public void glitch(Fx c){EraseFx.slash(c);}
    @Override public void alpha(Fx c,EffectMesh m){EraseFx.zone(c,m,false);}
    @Override public void additive(Fx c,EffectMesh m){EraseFx.zone(c,m,true);}
    @Override public void frame(Fx c,FxFrame f){EraseFx.frame(c,f);}
    @Override public void solids(Fx c,FxSolids s){EraseFx.solids(c,s);}
    @Override public float shake(StandClient.Cast cast,float sec){var s=EraseClient.STRIKES.get(cast.id());return s==null||s.cast!=cast?0:EraseFx.shake(s,sec);}
}
