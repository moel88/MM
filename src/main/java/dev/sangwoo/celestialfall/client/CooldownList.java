package dev.sangwoo.celestialfall.client;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The cooldown list's bookkeeping and layout (2.42.0), kept free of Minecraft so tests can step it frame by frame. Times
 * are ticks of the HUD's own clock ({@link CooldownHud}). After the user's Roblox recording: the skill used last sits on
 * top in a big box and the others still cooling sit below it in small boxes, newest first; each box has a bar along its
 * bottom that runs out to the left with its cooldown. A new entry unfolds on top while the old top shrinks into the list;
 * a finished one folds away and the boxes below close up; when the top one finishes, the next grows into its place.
 * Every size and position is a continuous function of the clock, so the list moves smoothly at 60 fps. A cooldown runs
 * once its skill is over (user request, 2.44.0), so one may be known before it begins: it waits and comes in on top as
 * it begins.
 */
public final class CooldownList {
    /** Ticks: a new box unfolding, a box growing or shrinking, a finished box folding away. */
    public static final double APPEAR=3.4,RESIZE=3.6,LEAVE=4.4;

    public static final class Entry {
        public final int slot;public final String name;
        /** When its cooldown began and when it runs out. */
        public final double start,end;
        final double shownAt;double leftAt=Double.NaN;
        double sizeFrom=1,sizeTo=1,sizeAt=-1e9;
        Entry(int slot,String name,double start,double end,double now){this.slot=slot;this.name=name;this.start=start;this.end=end;shownAt=now;}
        public boolean leaving(){return !Double.isNaN(leftAt);}
        /** The share of the cooldown still to run (the bar's length): 1 as it begins, 0 once it is over. */
        public double remaining(double now){return end<=start?0:clamp((end-now)/(end-start));}
        /** 0 a small box, 1 the big one. */
        public double big(double now){return sizeFrom+(sizeTo-sizeFrom)*smooth((now-sizeAt)/RESIZE);}
        /** How far the box has unfolded: 0 not there (yet, or any more), 1 all there. */
        public double presence(double now){
            double in=out((now-shownAt)/APPEAR);
            return leaving()?Math.min(in,1-smooth((now-leftAt)/LEAVE)):in;
        }
        void resize(double target,double now){if(target==sizeTo)return;sizeFrom=big(now);sizeTo=target;sizeAt=now;}
    }
    /** The list's measures in GUI units: its top left corner, the big and the small boxes, the space between boxes. */
    public record Metrics(double left,double top,double bigWidth,double bigHeight,double smallWidth,double smallHeight,double gap){}
    /** A box as drawn: where its slot in the list begins, its full size, how big (0..1) and how unfolded (0..1) it is. */
    public record Box(Entry entry,double x,double y,double width,double height,double big,double presence){}

    /** A cooldown still to begin: its skill is still being used. */
    private record Waiting(int slot,String name,double start,double end){}

    /** Newest first. */
    private final List<Entry> entries=new ArrayList<>();
    /** By slot. */
    private final TreeMap<Integer,Waiting> waiting=new TreeMap<>();
    public List<Entry> entries(){return entries;}
    public void clear(){entries.clear();waiting.clear();}

    /**
     * Skill slot {@code slot}'s cooldown, which began at {@code start} and runs out at {@code end}, seen at {@code now}:
     * it goes on top. One still shown for the same slot (its cooldown was reset and has begun again) leaves.
     */
    public Entry add(int slot,String name,double start,double end,double now){
        update(now);waiting.remove(slot);return put(slot,name,start,end,now);
    }
    /**
     * Skill slot {@code slot}'s cooldown, known at {@code now} to run from {@code start} to {@code end}: it goes on top as
     * it begins, at once if it already has (seen late, or its skill ended short: the bar starts part way down), and until
     * then the slot shows nothing. One already over takes the slot's entry away.
     */
    public void schedule(int slot,String name,double start,double end,double now){
        update(now);waiting.remove(slot);
        if(end<=now)finish(slot,now);
        else if(start<=now)put(slot,name,start,end,now);
        else{finish(slot,now);waiting.put(slot,new Waiting(slot,name,start,end));}
    }
    /** The slot's entry leaves at {@code now} although its time is not up (the server ended the cooldown early). */
    public void finish(int slot,double now){
        update(now);waiting.remove(slot);boolean changed=false;
        for(var e:entries)if(e.slot==slot&&!e.leaving()){e.leftAt=now;changed=true;}
        if(changed)arrange(now);
    }
    private Entry put(int slot,String name,double start,double end,double now){
        for(var e:entries)if(e.slot==slot&&!e.leaving())e.leftAt=now;
        var e=new Entry(slot,name,start,end,now);entries.add(0,e);arrange(now);return e;
    }
    /**
     * Waiting cooldowns that have begun by {@code now} come in and entries whose cooldown has run out leave, each at the
     * moment it happened (in that order, so the list is the same whenever this is called); ones that have folded away
     * are dropped.
     */
    public void update(double now){
        while(true){
            Entry first=null;
            for(var e:entries)if(!e.leaving()&&e.end<=now&&(first==null||e.end<first.end))first=e;
            Waiting due=null;
            for(var w:waiting.values())if(w.start<=now&&(due==null||w.start<due.start))due=w;
            if(due!=null&&(first==null||due.start<first.end)){waiting.remove(due.slot);put(due.slot,due.name,due.start,due.end,due.start);continue;}
            if(first==null)break;
            first.leftAt=first.end;arrange(first.end);
        }
        entries.removeIf(e->e.leaving()&&now-e.leftAt>=LEAVE);
    }
    /** The newest entry still running is the big one; the rest are small. */
    private void arrange(double at){
        boolean top=true;
        for(var e:entries){if(e.leaving())continue;e.resize(top?1:0,at);top=false;}
    }
    /** The boxes to draw at {@code now}, top to bottom: each takes its unfolded share of its height (and the gap after it). */
    public List<Box> layout(double now,Metrics m){
        var out=new ArrayList<Box>();double y=m.top();
        for(var e:entries){
            double p=e.presence(now),b=e.big(now);
            double w=m.smallWidth()+(m.bigWidth()-m.smallWidth())*b,h=m.smallHeight()+(m.bigHeight()-m.smallHeight())*b;
            if(p>0)out.add(new Box(e,m.left(),y,w,h,b,p));
            y+=(h+m.gap())*p;
        }
        return out;
    }

    static double clamp(double x){return x<0?0:x>1?1:x;}
    /** Eases out (fast, then settling). */
    static double out(double x){x=clamp(x);double r=1-x;return 1-r*r*r;}
    /** Eases in and out. */
    static double smooth(double x){x=clamp(x);return x*x*(3-2*x);}
}
