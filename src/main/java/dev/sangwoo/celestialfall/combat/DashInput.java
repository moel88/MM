package dev.sangwoo.celestialfall.combat;

/** Coalesces GLFW taps and mobile held-key polling into one request per press. */
public final class DashInput {
    private boolean rawDown,heldDown,pending;
    public void key(boolean down,boolean allowed) {
        if(down&&!rawDown&&allowed)pending=true;
        rawDown=down;
    }
    public boolean consume(boolean held,boolean queued,boolean allowed) {
        boolean tap=pending||(held&&!heldDown)||(queued&&!held&&!rawDown);
        pending=false;heldDown=held;
        return allowed&&tap;
    }
    public void clear(){rawDown=heldDown=pending=false;}
}
