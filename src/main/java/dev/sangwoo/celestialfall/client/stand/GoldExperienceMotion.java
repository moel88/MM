package dev.sangwoo.celestialfall.client.stand;

/**
 * A bone of Gold Experience's model moving on top of its stance (2.145.0), the way Star Platinum's does
 * ({@link StarPlatinumMotion}): floating, the whole body rises and sinks slowly with its breath and drifts a little, the
 * chest breathes, the head turns now and then, the arms float out and in, the legs dangle each a little after the one
 * above, the wings stir, the rings hanging from its belt swing and the hide behind its hips sways, each strip out of
 * step with the next and its end after its top. Standing to be looked at ({@code calm} 0) only the breath and the
 * hanging things move. Every movement is a sine of the clock in seconds, so each frame between ticks has its own pose.
 * (2.155.0: the body the user sent has no wing, ring or hide bones, its pads, hooks and hide going with its shoulders,
 * hips and legs as its own skin moves them; those kinds stay for the box-built model, which a skeleton with them would
 * bring back.)
 */
record GoldExperienceMotion(int kind,float index,int segment) {
    static final int STILL=0,ROOT=1,HIPS=2,TORSO=3,CHEST=4,NECK=5,HEAD=6,ARM=7,FOREARM=8,HAND=9,LEG=10,SHIN=11,FOOT=12,WING=13,BLADE=14,RING=15,HIDE=16,SIDE=17;
    /** The breath and the float: one slow rise and sink (seconds), and how far it goes (units; 0.1 of a block at its size). */
    static final double BREATH=3.8;
    static final float BOB=2.2f;
    /** What part a bone of the model is, by its name in tools/stand/make_gold_experience.py. */
    static GoldExperienceMotion of(String name){
        int under=name.lastIndexOf('_');
        int segment=under>0&&Character.isDigit(name.charAt(name.length()-1))?Integer.parseInt(name.substring(under+1)):0;
        float side=name.startsWith("right")?1:-1;
        switch(name){
            case "root":return new GoldExperienceMotion(ROOT,0,0);
            case "hips":return new GoldExperienceMotion(HIPS,0,0);
            case "torso":return new GoldExperienceMotion(TORSO,0,0);
            case "chest":return new GoldExperienceMotion(CHEST,0,0);
            case "neck":return new GoldExperienceMotion(NECK,0,0);
            case "head":return new GoldExperienceMotion(HEAD,0,0);
            default:break;
        }
        if(name.endsWith("_arm"))return new GoldExperienceMotion(ARM,side,0);
        if(name.endsWith("_forearm"))return new GoldExperienceMotion(FOREARM,side,0);
        if(name.endsWith("_hand"))return new GoldExperienceMotion(HAND,side,0);
        if(name.endsWith("_leg"))return new GoldExperienceMotion(LEG,side,0);
        if(name.endsWith("_shin"))return new GoldExperienceMotion(SHIN,side,0);
        if(name.endsWith("_foot"))return new GoldExperienceMotion(FOOT,side,0);
        if(name.endsWith("_wing"))return new GoldExperienceMotion(WING,side,0);
        if(name.contains("_wing_blade"))return new GoldExperienceMotion(BLADE,side,segment);
        if(name.endsWith("_ring"))return new GoldExperienceMotion(RING,side,0);
        if(name.startsWith("hide_right")||name.startsWith("hide_left"))return new GoldExperienceMotion(SIDE,side,name.endsWith("_1")?1:0);
        if(name.startsWith("hide_"))return new GoldExperienceMotion(HIDE,name.charAt(5)-'0',name.length()>6?1:0);
        return new GoldExperienceMotion(STILL,0,0);
    }
    static float wave(double t,double period,double phase){return (float)Math.sin(2*Math.PI*t/period+phase);}
    /**
     * Adds this bone's movement at t seconds to its pose (turn x, y, z in degrees as Blockbench writes them, offset x, y,
     * z in units); {@code calm} is how far it floats at rest (1) rather than stands to be looked at (0).
     */
    void add(float[] p,double t,float calm){
        switch(kind){
            case ROOT->{
                p[4]+=calm*BOB*wave(t,BREATH,0);
                p[3]+=calm*.3f*wave(t,7.9,1);p[5]+=calm*.28f*wave(t,6.3,2);
                p[1]+=calm*2.2f*wave(t,10.3,0);p[2]+=calm*.9f*wave(t,6.7,.4);
            }
            case HIPS->p[0]+=.5f*calm*wave(t,BREATH,-.4);
            case TORSO->p[0]+=.5f*wave(t,BREATH,.2);
            case CHEST->p[0]-=1.2f*wave(t,BREATH,.5);
            case NECK->p[0]+=.4f*wave(t,BREATH,1);
            case HEAD->{p[0]+=.9f*wave(t,BREATH,1.4);p[1]+=calm*3.5f*wave(t,11.9,0);p[2]+=calm*1f*wave(t,8.3,.7);}
            case ARM->{p[2]+=index*(.4f+1.3f*calm)*wave(t,BREATH,.3);p[0]+=calm*2f*wave(t,5.1,index);}
            case FOREARM->p[0]-=calm*1.4f*(1+wave(t,BREATH,.9));
            case HAND->p[0]+=calm*2.6f*wave(t,BREATH,1.3);
            case LEG->{p[0]+=calm*2.2f*wave(t,BREATH,-.9+index*.5);p[2]+=index*calm*1.1f*wave(t,BREATH,-.6);}
            case SHIN->p[0]+=calm*3f*wave(t,BREATH,-1.5+index*.5);
            case FOOT->p[0]+=calm*4f*wave(t,BREATH,-2.1+index*.5);
            case WING->{p[2]-=index*(.6f+.9f*calm)*wave(t,BREATH,.6);p[0]+=.5f*wave(t,2.9,index);}
            case BLADE->{float a=(.5f+.7f*segment)*(.5f+.5f*calm);p[0]+=a*wave(t,2.3,-.8*segment+index);p[2]+=.6f*a*wave(t,3.1,-.8*segment+1);}
            case RING->{float a=1.2f+2.6f*calm;p[0]+=a*wave(t,1.9,index)+.3f*a*wave(t,.83,index*1.7);p[2]+=.5f*a*wave(t,2.6,index+.9);}
            case HIDE->{
                float a=(1.1f+1.5f*segment)*(.5f+.6f*calm);
                p[0]+=a*wave(t,2.1,-.8*segment+index*.9)+.35f*a*wave(t,.72,-segment+index*1.3);
                p[2]+=.5f*a*wave(t,2.8,-.8*segment+index*.9);
            }
            case SIDE->{
                float a=(1.2f+1.5f*segment)*(.5f+.6f*calm);
                p[2]+=a*wave(t,2.3,-.7*segment+index)+.35f*a*wave(t,.77,-segment+index);
                p[0]+=.5f*a*wave(t,3.0,-.7*segment+index);
            }
            default->{}
        }
    }
}
