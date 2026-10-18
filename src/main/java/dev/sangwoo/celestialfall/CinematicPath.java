package dev.sangwoo.celestialfall;

/** Deliberate anime cuts: shoulder, constellation wide, blade close-up, tracking, low angle, blast wide. */
public final class CinematicPath {
    public static final int RETURN_START=330, END=370;
    public record Shot(double radius,double height,double focusHeight,double orbit,double fov,double roll,boolean playerAnchor) {}
    private CinematicPath() {}
    public static int shotIndex(float age) {
        if(age<60)return 0;if(age<136)return 1;if(age<156)return 2;
        if(age<174)return 3;if(age<206)return 4;return 5;
    }
    public static float blend(float age) {
        return SpellTimeline.smooth(SpellTimeline.progress(age,0,16))
            *(1-SpellTimeline.smooth(SpellTimeline.progress(age,RETURN_START,END)));
    }
    public static Shot sample(float age) {
        double tip=SpellTimeline.swordTipHeight(age);
        switch(shotIndex(age)) {
            case 0: {
                double t=ease(age,0,60);
                return new Shot(6+4*t,2.2+4*t,220+80*t,-.12,84,0,true);
            }
            case 1: {
                double t=ease(age,60,136),pulse=SpellTimeline.clashPulse(age);
                return new Shot(190-35*t,75+20*t,300,.25+t*.14,88-pulse*12,2*Math.sin(t*3),false);
            }
            case 2: return new Shot(30,tip+52,tip+108,.83,62,-7,false);
            case 3: {
                double t=ease(age,156,174);
                return new Shot(43-8*t,tip+28,tip+92,1.05+t*.2,76+12*t,9-5*t,false);
            }
            case 4: {
                double t=ease(Math.min(age,SpellTimeline.CONTACT),174,202);
                return new Shot(64,9,Math.max(95,tip+90),-.22,100+8*t,-4,false);
            }
            default: {
                double t=ease(age,206,230),pan=ease(age,230,370);
                return new Shot(64+226*t-15*pan,9+115*t,95+25*pan,-.22+.14*t+.13*pan,
                    108-20*t,(-4+7*t)*(1-pan),false);
            }
        }
    }
    private static double ease(float age,float start,float end) { return SpellTimeline.smooth(SpellTimeline.progress(age,start,end)); }
    public static float shake(float age) {
        if(SpellTimeline.contactHold(age))return 0;
        if(age<SpellTimeline.IMPACT)return SpellTimeline.clashPulse(age)*.65f;
        float t=age-SpellTimeline.IMPACT;
        return Math.max(SpellTimeline.wavePulse(age),(float)Math.exp(-t/28.0))
            *(1-SpellTimeline.progress(t,80,115));
    }
}
