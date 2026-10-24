package dev.sangwoo.celestialfall.menu;
import dev.sangwoo.celestialfall.progression.ProgressProfile;
/** Earned titles are cosmetic and remain earned after rebirth/transcendence. */
public record MenuProgress(int unlocked,int equipped) {
    public static final String[] TITLES={"한계에 닿은 자","새로운 시작","윤회의 끝","초월자"};
    public static final String[] NAMES={"최고 레벨 달성","첫 번째 환생","15번의 환생","첫 번째 초월"};
    public static final String[] DETAILS={"레벨 50에 도달하세요.","환생을 1회 달성하세요.","환생을 15회 달성하세요.","초월을 1회 달성하세요."};
    public MenuProgress {unlocked&=15;if(equipped<0||equipped>3||(unlocked&(1<<equipped))==0)equipped=-1;}
    public boolean earned(int id){return id>=0&&id<4&&(unlocked&(1<<id))!=0;}
    public MenuProgress observe(ProgressProfile p){
        int mask=unlocked;
        if(p.level()==50||p.rebirths()>0||p.transcendence()>0)mask|=1;
        if(p.rebirths()>0||p.transcendence()>0)mask|=2;
        if(p.rebirths()==15||p.transcendence()>0)mask|=4;
        if(p.transcendence()>0)mask|=8;
        return new MenuProgress(mask,equipped);
    }
    public MenuProgress equip(int id){return id==-1||earned(id)?new MenuProgress(unlocked,id):this;}
    public String title(){return equipped<0?"칭호 없음":TITLES[equipped];}
}
