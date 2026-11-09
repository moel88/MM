package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.combat.HitCounter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The hit count (2.108.0, user request: "히트 수 표시가 카게노 H에만 있는데 모든 스탠드에 모든 스킬 평타에 히트 수가 뜨게
 * 바꿔줘"): every blow a player lands counts, whoever's skill or basic attack it is, and the count shows for every
 * character, over the cooldown list's boxes (it was under them, seen only in Kageno's H's cutscene).
 */
class HitCounterTest {
    /** A count runs on while each hit comes within 2.5 s of the one before, and starts again after (Kageno's rule, kept). */
    @Test void aCountRunsOnWhileTheHitsKeepComing(){
        assertEquals(50,HitCounter.COMBO,"2.5 s");
        long never=Long.MIN_VALUE/2;
        assertEquals(1,HitCounter.next(0,never,100,1),"the first");
        assertEquals(2,HitCounter.next(1,100,150,1),"2.5 s on: still the same combo");
        assertEquals(1,HitCounter.next(2,150,201,1),"a tick more: a new one");
        assertEquals(6,HitCounter.next(2,150,160,4),"a blow counting four (Kageno's R's stored cut)");
        assertEquals(HitCounter.MOST,HitCounter.next(HitCounter.MOST,100,101,1),"no more than its display holds");
        assertEquals(dev.sangwoo.celestialfall.stand.KagenoRules.R_CUTS,dev.sangwoo.celestialfall.stand.KagenoRules.hits(dev.sangwoo.celestialfall.stand.KagenoRules.R,dev.sangwoo.celestialfall.stand.KagenoRules.R_STORED),
            "R's stored cut is its four slashes");
    }
    /**
     * Every blow that lands counts, whoever dealt it: the count is kept where every skill's and basic attack's harm comes,
     * LivingEntity.damage (LivingCombatMixin) as it lets a blow through; one Star Platinum's stopped time keeps counts as it
     * is kept (the blow counts as landed then), and not again as time moves and it comes to them.
     */
    @Test void everyBlowThatLandsCountsOnce()throws Exception{
        var mixin=node("dev/sangwoo/celestialfall/mixin/LivingCombatMixin");
        var guard=injector(mixin,"botand$guard");var after=injector(mixin,"botand$interruptComboOnDamage");
        assertTrue(flat(annotation(after)).contains("RETURN"),"counted as it returns");
        var a=calls(after);
        int landed=index(a,"landed","dev/sangwoo/celestialfall/combat/HitCounter");
        assertTrue(landed>=0,"a blow let through counts");
        assertTrue(index(a,"settling","dev/sangwoo/celestialfall/stand/StarPlatinumTimeStop")>=0&&index(a,"stopped","dev/sangwoo/celestialfall/stand/StarPlatinumTimeStop")>=0,
            "not what stopped time gives, nor one stopped (counted as it was kept)");
        var g=calls(guard);
        int defer=index(g,"defer","dev/sangwoo/celestialfall/stand/StarPlatinumTimeStop"),kept=index(g,"landed","dev/sangwoo/celestialfall/combat/HitCounter");
        assertTrue(defer>=0&&kept>defer,"a blow stopped time keeps counts as it is kept");
        assertEquals(1,g.stream().filter(c->c.name.equals("landed")).count(),"there, and nowhere else in the guard");
        // what turns a blow aside comes first: it does not count
        for(String aside:new String[]{"invulnerable","shielded","dodges","block"}){
            int at=-1;for(int i=0;i<g.size();i++)if(g.get(i).name.equals(aside)){at=i;break;}
            assertTrue(at>=0&&(at<kept||aside.equals("block")),aside);
        }
    }
    /** Every stand's and character's harm comes through LivingEntity.damage: none counts its own (Kageno's own count is gone). */
    @Test void noSkillKeepsACountOfItsOwn()throws Exception{
        var kageno=node("dev/sangwoo/celestialfall/stand/KagenoManager");
        for(FieldNode f:kageno.fields)assertNotEquals("HITS",f.name,"Kageno's own channel field is gone");
        for(var m:kageno.methods)for(var i:m.instructions)if(i instanceof LdcInsnNode l)assertNotEquals("kageno_hits_v1",l.cst,"only HitCounter tells the count");
        var hit=kageno.methods.stream().filter(m->m.name.equals("hit")&&m.desc.contains("LivingEntity;FI")).findFirst().orElseThrow();
        assertTrue(index(calls(hit),"weighing","dev/sangwoo/celestialfall/combat/HitCounter")>=0,"Kageno's blows weighed (R's four slashes) through the shared count");
        // every skill's harm goes to LivingEntity.damage, through CombatManager's two ways or straight
        String[] dealers={"dev/sangwoo/celestialfall/stand/KagenoManager","dev/sangwoo/celestialfall/stand/StarPlatinumAttack","dev/sangwoo/celestialfall/stand/StarPlatinumRush",
            "dev/sangwoo/celestialfall/stand/StarPlatinumBeatdown","dev/sangwoo/celestialfall/isosang/IsosangAttack","dev/sangwoo/celestialfall/isosang/GExplosionDamage",
            "dev/sangwoo/celestialfall/combat/CombatManager","dev/sangwoo/celestialfall/AtomicManager"};
        for(String d:dealers){
            boolean deals=false;
            for(var m:node(d).methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&(c.name.equals("damageWithoutKnockback")||c.name.equals("damageQuietly")||c.name.equals("damage")&&c.desc.startsWith("(Lnet/minecraft/entity/damage/DamageSource;F)Z")))deals=true;
            assertTrue(deals,d);
        }
        var cm=node("dev/sangwoo/celestialfall/combat/CombatManager");
        var without=cm.methods.stream().filter(m->m.name.equals("damageWithoutKnockback")).findFirst().orElseThrow();
        assertTrue(calls(without).stream().anyMatch(c->c.name.equals("damage")&&c.desc.equals("(Lnet/minecraft/entity/damage/DamageSource;F)Z")),"CombatManager's way ends in LivingEntity.damage");
    }
    /** The beatdown's opening punch opens its cutscene without harm, and its blows held back at 1 health deal none: hits all the same. */
    @Test void theBeatdownsHarmlessBlowsCount()throws Exception{
        var blow=node("dev/sangwoo/celestialfall/stand/StarPlatinumBeatdown").methods.stream().filter(m->m.name.equals("blow")).findFirst().orElseThrow();
        var c=calls(blow);
        assertTrue(index(c,"sendEntityDamage")>=0&&index(c,"struck","dev/sangwoo/celestialfall/combat/HitCounter")>index(c,"sendEntityDamage"),"held back at 1 health");
        boolean opener=false;
        for(var m:node("dev/sangwoo/celestialfall/stand/StarPlatinumAttack").methods){
            var k=calls(m);int begin=index(k,"begin","dev/sangwoo/celestialfall/stand/StarPlatinumBeatdown");
            if(begin>=0&&index(k,"struck","dev/sangwoo/celestialfall/combat/HitCounter")>begin)opener=true;
        }
        assertTrue(opener,"the opening punch");
    }
    /** The server tells the count and how many of it came this tick; a 2.107 one tells only the count (one, then). */
    @Test void aComboBeginsWhenAllOfItCameNow(){
        dev.sangwoo.celestialfall.client.HitCounterHud.clear();
        heard(1,1,100);assertEquals(100,comboAt(),0,"the first hit");
        heard(2,1,105);assertEquals(100,comboAt(),0,"the next");
        heard(5,3,106);assertEquals(100,comboAt(),0,"three at once (three struck by one blow) in the same combo");
        heard(3,3,300);assertEquals(300,comboAt(),0,"a new combo that began three at once");
        heard(4,1,302);heard(1,1,400);assertEquals(400,comboAt(),0,"one again: a new one (as a 2.107 server tells a combo's first)");
        heard(5,4,402);assertEquals(400,comboAt(),0,"Kageno's R's stored cut (four) in the same combo");
        heard(2,1,403);assertEquals(403,comboAt(),0,"lower than it was: a new one");
        assertEquals(2,dev.sangwoo.celestialfall.client.HitCounterHud.count);
        dev.sangwoo.celestialfall.client.HitCounterHud.clear();
    }
    /** It shows 2.75 s after the last hit (fading the last 0.25), as Kageno's did: gone before the server's count starts again. */
    @Test void itShowsUntilTheComboIsOver()throws Exception{
        assertTrue(shows(1,0)&&shows(12,2.7f));
        assertFalse(shows(0,.5f),"no count");
        assertFalse(shows(3,2.75f),"over");
        assertEquals(HitCounter.COMBO,(dev.sangwoo.celestialfall.client.HitCounterHud.SHOWN-dev.sangwoo.celestialfall.client.HitCounterHud.FADE)*20,1e-4,"it fades as the server's count ends");
    }
    /**
     * It stands where Kageno's did (the reference's place: the left, 0.31 down), over the cooldown list's first box there
     * (user request: "히트 수 표시 쿨타임이랑 겹치게 해도 돼 지금 위치가 너무 안 좋아 쿨타임 표시보다 히트 수 표시가 위에
     * 나오게 해줘"): its HUD callback is registered after the list's, so it is drawn after it (Kageno's was before, under
     * the boxes); a cutscene's overlay draws it at the same place.
     */
    @Test void itIsDrawnOverTheCooldownList()throws Exception{
        for(int[] s:new int[][]{{640,360},{480,270},{960,540},{427,240},{854,480},{1280,720},{720,480}}){
            int w=s[0],h=s[1];double[] at=place(w,h);
            assertEquals(w*.042,at[0],1e-9);assertEquals(h*.31,at[1],1e-9);
            // the list's first box (CooldownHud: the left 3.6%, from 0.3 down, 0.29 by 0.066 of the height): it is over it
            assertTrue(at[0]>Math.max(8,w*.036)&&at[0]<Math.max(8,w*.036)+h*.29&&at[1]>h*.3&&at[1]<h*.3+h*.066,w+"x"+h);
        }
        var client=node("dev/sangwoo/celestialfall/client/CelestialFallClient");
        var init=client.methods.stream().filter(m->m.name.equals("onInitializeClient")).findFirst().orElseThrow();
        var c=calls(init);
        int list=index(c,"initialize","dev/sangwoo/celestialfall/client/CooldownHud"),count=index(c,"initialize","dev/sangwoo/celestialfall/client/HitCounterHud");
        assertTrue(list>=0&&count>list,"the hit count's HUD callback after the list's: drawn over its boxes");
        assertTrue(index(c,"initialize","dev/sangwoo/celestialfall/client/StandClient")<list,"Kageno's own HUD (its overdrive line) before, as it was");
        var hud=node("dev/sangwoo/celestialfall/client/HitCounterHud");
        var hi=hud.methods.stream().filter(m->m.name.equals("initialize")).findFirst().orElseThrow();
        assertTrue(calls(hi).stream().anyMatch(x->x.name.equals("register")),"it draws from the HUD");
        var cine=node("dev/sangwoo/celestialfall/client/CinematicHud").methods.stream().filter(m->m.name.equals("render")).findFirst().orElseThrow();
        assertTrue(index(calls(cine),"render","dev/sangwoo/celestialfall/client/HitCounterHud")>=0,"and in every cutscene");
        for(var m:node("dev/sangwoo/celestialfall/client/KagenoRenderer").methods)for(var i:m.instructions)
            if(i instanceof LdcInsnNode l)assertNotEquals(" Hits",l.cst,"Kageno's own counter (drawn under the list) is gone");
    }

    // ---- helpers

    private static void heard(int n,int added,double at)throws RuntimeException{
        try{var m=dev.sangwoo.celestialfall.client.HitCounterHud.class.getDeclaredMethod("heard",int.class,int.class,double.class);m.setAccessible(true);m.invoke(null,n,added,at);}
        catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static double comboAt(){
        try{var f=dev.sangwoo.celestialfall.client.HitCounterHud.class.getDeclaredField("comboAt");f.setAccessible(true);return f.getDouble(null);}
        catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static boolean shows(int count,float since)throws Exception{
        var m=dev.sangwoo.celestialfall.client.HitCounterHud.class.getDeclaredMethod("shows",int.class,float.class);m.setAccessible(true);return (boolean)m.invoke(null,count,since);
    }
    private static double[] place(int w,int h)throws Exception{
        var m=dev.sangwoo.celestialfall.client.HitCounterHud.class.getDeclaredMethod("place",int.class,int.class);m.setAccessible(true);
        return (double[])m.invoke(null,w,h);
    }
    private static ClassNode node(String name)throws Exception{
        try(var stream=HitCounterTest.class.getResourceAsStream("/"+name+".class")){
            assertNotNull(stream,name);var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
    private static MethodNode injector(ClassNode mixin,String name){return mixin.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow(()->new AssertionError(name));}
    private static Object annotation(MethodNode m){return m.visibleAnnotations==null?null:m.visibleAnnotations.get(0);}
    /** An annotation's values, nested ones too, as text. */
    private static String flat(Object o){
        if(o instanceof org.objectweb.asm.tree.AnnotationNode a)return a.desc+flat(a.values);
        if(o instanceof List<?> l){var b=new StringBuilder("[");for(var x:l)b.append(flat(x)).append(',');return b.append(']').toString();}
        if(o instanceof String[] e)return String.join(".",e);
        return String.valueOf(o);
    }
    private static List<MethodInsnNode> calls(MethodNode m){
        var out=new ArrayList<MethodInsnNode>();
        for(var i:m.instructions)if(i instanceof MethodInsnNode call)out.add(call);
        return out;
    }
    private static int index(List<MethodInsnNode> calls,String name){for(int i=0;i<calls.size();i++)if(calls.get(i).name.equals(name))return i;return -1;}
    private static int index(List<MethodInsnNode> calls,String name,String owner){for(int i=0;i<calls.size();i++)if(calls.get(i).name.equals(name)&&calls.get(i).owner.equals(owner))return i;return -1;}
}
