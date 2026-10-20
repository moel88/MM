package dev.sangwoo.celestialfall.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The cooldown list (2.42.0): the skill used last on top in the big box, the rest small below it newest first, bars
 * running out with their cooldowns, finished entries folding away (the next one growing into the top when the top one
 * goes), and nothing in it jumping from one 60 fps frame to the next.
 */
class CooldownListTest {
    /** The list's measures on a 640×365 GUI screen (1280×730 at GUI scale 2). */
    private static final CooldownList.Metrics M=new CooldownList.Metrics(23,136.5,148,34.3,99,19.7,5);
    private static final double FRAME=20/60.;

    @Test void theSkillUsedLastIsBigOnTopAndTheRestSmallNewestFirst(){
        var list=new CooldownList();
        list.add(1,"E",0,140,0);list.add(3,"T",30,290,30);list.add(5,"Y",50,230,50);
        double now=60;var boxes=list.layout(now,M);
        assertEquals(List.of("Y","T","E"),boxes.stream().map(b->b.entry().name).toList());
        assertEquals(1,boxes.get(0).big(),1e-9);assertEquals(0,boxes.get(1).big(),1e-9);assertEquals(0,boxes.get(2).big(),1e-9);
        assertEquals(M.top(),boxes.get(0).y(),1e-9);
        assertEquals(M.top()+M.bigHeight()+M.gap(),boxes.get(1).y(),1e-9,"the first small box sits under the big one");
        assertEquals(boxes.get(1).y()+M.smallHeight()+M.gap(),boxes.get(2).y(),1e-9);
        assertEquals(M.bigWidth(),boxes.get(0).width(),1e-9);assertEquals(M.smallWidth(),boxes.get(1).width(),1e-9);
    }
    @Test void barsRunOutWithTheirCooldowns(){
        var list=new CooldownList();var e=list.add(1,"E",0,140,0);
        assertEquals(1,e.remaining(0),1e-9);assertEquals(.5,e.remaining(70),1e-9);assertEquals(0,e.remaining(140),1e-9);
        // seen late (joining with a cooldown already running): the bar starts part way down
        var late=list.add(6,"Q",-900,900,0);assertEquals(.5,late.remaining(0),1e-9);
    }
    @Test void aFinishedEntryFoldsAwayAndTheNextGrowsIntoTheTop(){
        var list=new CooldownList();
        list.add(3,"T",0,260,0);list.add(1,"E",20,160,20);
        list.update(161);
        var e=list.entries().get(0);assertTrue(e.leaving(),"E's time is up");assertEquals(160,e.leftAt,1e-9,"it leaves when its time ran out");
        list.update(160+CooldownList.LEAVE+1);
        assertEquals(List.of("T"),list.entries().stream().map(x->x.name).toList(),"gone once folded away");
        assertEquals(1,list.layout(160+CooldownList.RESIZE+1,M).get(0).big(),1e-9,"T is the big one again");
    }
    @Test void aResetCooldownReplacesItsEntryOnTop(){
        var list=new CooldownList();
        list.add(1,"E",0,140,0);list.add(3,"T",10,270,10);list.add(1,"E",30,170,30);
        var live=list.entries().stream().filter(x->!x.leaving()).map(x->x.name).toList();
        assertEquals(List.of("E","T"),live);
        list.finish(3,40);assertTrue(list.entries().stream().filter(x->x.slot==3).allMatch(CooldownList.Entry::leaving));
    }
    @Test void theListIsTheSameHoweverOftenItIsUpdated(){
        var a=new CooldownList();var b=new CooldownList();
        for(var l:List.of(a,b)){l.add(1,"E",0,40,0);l.add(5,"Y",10,90,10);l.add(3,"T",20,60,20);}
        for(double t=20;t<=100;t+=FRAME)a.update(t);
        b.update(100);
        var la=a.layout(100,M);var lb=b.layout(100,M);
        assertEquals(la.size(),lb.size());
        for(int i=0;i<la.size();i++){assertEquals(la.get(i).y(),lb.get(i).y(),1e-9);assertEquals(la.get(i).big(),lb.get(i).big(),1e-9);}
    }
    /** Busy fighting: casts close together, some ending while others arrive, the top one finishing, a reset. */
    @Test void nothingJumpsBetweenFrames(){
        var list=new CooldownList();
        double[][] script={ // slot, cast at, cooldown
            {1,10,140},{3,90,260},{5,120,180},{6,165,1800},{2,180,180},{0,299,24},{7,330,600},{1,331,140},{8,333,1200},{0,336,24},{0,338,24}};
        int next=0;Map<CooldownList.Entry,double[]> last=new HashMap<>();
        double maxStep=0;
        for(double t=0;t<1000;t+=FRAME){
            while(next<script.length&&script[next][1]<=t){var s=script[next++];list.add((int)s[0],"s"+(int)s[0],s[1],s[1]+s[2],s[1]);}
            list.update(t);
            Map<CooldownList.Entry,double[]> now=new HashMap<>();
            for(var b:list.layout(t,M)){
                double[] v={b.y(),b.y()+b.height()*b.presence(),b.width(),b.height(),b.big(),b.presence(),b.entry().remaining(t)};now.put(b.entry(),v);
                double[] was=last.get(b.entry());
                if(was==null){assertTrue(b.presence()<.5,"a box comes in unfolding, not all at once: "+b.entry().name+" at "+t);continue;}
                // the fastest a box goes: one above it unfolding at its quickest while another shrinks (a box, gap included, is 24.7)
                for(int i=0;i<4;i++){double step=Math.abs(v[i]-was[i]);maxStep=Math.max(maxStep,step);
                    assertTrue(step<16,"box "+b.entry().name+" moved "+step+" in a frame at "+t+" (value "+i+")");}
                assertTrue(Math.abs(v[4]-was[4])<.3&&Math.abs(v[5]-was[5])<.35,"size and unfolding change smoothly at "+t);
                assertTrue(Math.abs(v[6]-was[6])<.02,"the bar runs down smoothly");
            }
            for(var e:last.keySet())if(!now.containsKey(e))assertTrue(last.get(e)[5]<.1,"a box leaves after folding away, not while showing");
            last=now;
        }
        assertTrue(maxStep>1,"the list did move");
        assertTrue(list.entries().stream().noneMatch(e->e.end<1000-CooldownList.LEAVE),"every finished entry is gone");
    }
}
