package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The shops: the main street's shop-houses standing shoulder to shoulder (a shop below, the family above, the
 * frontage carried up flat in front of the roof the way old shopping streets build), each with its awning, its name
 * board, a sign standing out from the wall with the name read downward, noren or red lanterns where food is served,
 * and its own goods in front; the convenience store with its car park and lit pole sign; the public bath with its
 * brick chimney smoking; the post office, the clinic, the inn; and the arches over both ends of the main street.
 */
final class Shops {
    private final Townscape t;private final Canvas c;private final Random rnd;
    int shops;
    Shops(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    /** A kind of shop: its name read down the standing sign, the name board's two lines, colours and goods. */
    record Kind(String id,String vertical,String name,String sub,Block awning,Block awning2,DyeColor text,BlockState face){}
    static final List<Kind> MAIN=List.of(
        new Kind("ramen","ラーメン","らーめん 浜屋","醤油・味噌・塩",Blocks.RED_WOOL,Blocks.RED_WOOL,DyeColor.RED,s(Blocks.WHITE_TERRACOTTA)),
        new Kind("cafe","喫茶","喫茶 しおさい","珈琲・軽食",Blocks.BROWN_WOOL,Blocks.WHITE_WOOL,DyeColor.BROWN,s(Blocks.BRICKS)),
        new Kind("izakaya","居酒屋","居酒屋 灯","地魚・地酒",Blocks.BLUE_WOOL,Blocks.BLUE_WOOL,DyeColor.WHITE,s(Blocks.DARK_OAK_PLANKS)),
        new Kind("fish","鮮魚","鮮魚 丸浜","朝どれの魚",Blocks.BLUE_WOOL,Blocks.WHITE_WOOL,DyeColor.BLUE,s(Blocks.LIGHT_GRAY_CONCRETE)),
        new Kind("grocer","八百屋","八百屋 みどり","野菜・果物",Blocks.GREEN_WOOL,Blocks.WHITE_WOOL,DyeColor.GREEN,s(Blocks.WHITE_TERRACOTTA)),
        new Kind("pharmacy","くすり","海野薬局","処方せん受付",Blocks.LIME_WOOL,Blocks.WHITE_WOOL,DyeColor.GREEN,s(Blocks.SMOOTH_QUARTZ)),
        new Kind("barber","理容","理容 ナギサ","カット・顔そり",Blocks.RED_WOOL,Blocks.BLUE_WOOL,DyeColor.BLUE,s(Blocks.WHITE_CONCRETE)),
        new Kind("bakery","パン","パン工房 こむぎ","焼きたて",Blocks.ORANGE_WOOL,Blocks.WHITE_WOOL,DyeColor.ORANGE,s(Blocks.SMOOTH_SANDSTONE)),
        new Kind("books","本","灯台堂書店","本・文具",Blocks.CYAN_WOOL,Blocks.CYAN_WOOL,DyeColor.CYAN,s(Blocks.LIGHT_GRAY_TERRACOTTA)),
        new Kind("liquor","酒","浜田酒店","地酒・たばこ",Blocks.BLUE_WOOL,Blocks.BLUE_WOOL,DyeColor.BLUE,s(Blocks.SPRUCE_PLANKS)),
        new Kind("florist","花","花 はなみずき","鉢植え・切り花",Blocks.PINK_WOOL,Blocks.WHITE_WOOL,DyeColor.MAGENTA,s(Blocks.WHITE_TERRACOTTA)),
        new Kind("laundry","洗濯","コインランドリー","24時間",Blocks.LIGHT_BLUE_WOOL,Blocks.WHITE_WOOL,DyeColor.LIGHT_BLUE,s(Blocks.WHITE_CONCRETE)),
        new Kind("electric","電器","マリン電器","家電・修理",Blocks.YELLOW_WOOL,Blocks.YELLOW_WOOL,DyeColor.BLUE,s(Blocks.LIGHT_GRAY_CONCRETE)),
        new Kind("sushi","寿司","浜鮨","にぎり・ちらし",Blocks.BLUE_WOOL,Blocks.BLUE_WOOL,DyeColor.WHITE,s(Blocks.SPRUCE_PLANKS)),
        new Kind("soba","そば","岬庵","そば・うどん",Blocks.BROWN_WOOL,Blocks.BROWN_WOOL,DyeColor.WHITE,s(Blocks.DARK_OAK_PLANKS)),
        new Kind("snack","スナック","スナック 渚","カラオケ",Blocks.PURPLE_WOOL,Blocks.MAGENTA_WOOL,DyeColor.PINK,s(Blocks.GRAY_CONCRETE)),
        new Kind("sweets","和菓子","月見堂","だんご・最中",Blocks.LIME_WOOL,Blocks.WHITE_WOOL,DyeColor.GREEN,s(Blocks.WHITE_TERRACOTTA)),
        new Kind("realty","不動産","ボタンド不動産","賃貸・売買",Blocks.BLUE_WOOL,Blocks.WHITE_WOOL,DyeColor.BLUE,s(Blocks.LIGHT_GRAY_CONCRETE)),
        new Kind("cleaning","クリーニング","クリーニング白波","",Blocks.LIGHT_BLUE_WOOL,Blocks.WHITE_WOOL,DyeColor.BLUE,s(Blocks.WHITE_CONCRETE)),
        new Kind("bike","自転車","サイクル浜","修理・レンタル",Blocks.ORANGE_WOOL,Blocks.ORANGE_WOOL,DyeColor.ORANGE,s(Blocks.LIGHT_GRAY_CONCRETE)),
        new Kind("tobacco","たばこ","たばこ","",Blocks.RED_WOOL,Blocks.RED_WOOL,DyeColor.RED,s(Blocks.WHITE_TERRACOTTA)),
        new Kind("souvenir","みやげ","おみやげ 浜風","干物・海産物",Blocks.CYAN_WOOL,Blocks.WHITE_WOOL,DyeColor.CYAN,s(Blocks.SMOOTH_SANDSTONE)),
        new Kind("tea","お茶","お茶の 青葉園","日本茶・海苔",Blocks.GREEN_WOOL,Blocks.GREEN_WOOL,DyeColor.GREEN,s(Blocks.SPRUCE_PLANKS)),
        new Kind("toys","おもちゃ","おもちゃの まるや","駄菓子",Blocks.YELLOW_WOOL,Blocks.RED_WOOL,DyeColor.RED,s(Blocks.WHITE_TERRACOTTA)),
        new Kind("photo","写真","浜写真館","写真・現像",Blocks.BLACK_WOOL,Blocks.BLACK_WOOL,DyeColor.WHITE,s(Blocks.LIGHT_GRAY_CONCRETE)));
    static final Set<String> FOOD=Set.of("ramen","izakaya","sushi","soba","cafe","snack");

    /** Builds the shops on their plots: the main street's, some of the station street's, the special buildings. */
    void build(){
        List<Kind> pool=new ArrayList<>(MAIN);Collections.shuffle(pool,rnd);
        int k=0;
        List<Lot> main=new ArrayList<>();for(Lot l:t.layout.lots)if(l.zone.equals("main"))main.add(l);
        main.sort(Comparator.comparingInt(l->l.z0));
        for(int i=0;i<main.size();i++){
            Lot l=main.get(i);
            if(i==main.size()/2&&l.front==Direction.EAST){l.use="post";post(l);continue;}
            if(i==main.size()/3&&l.front==Direction.WEST){l.use="clinic";clinic(l);continue;}
            Kind kind=pool.get(k++%pool.size());
            l.use="shop:"+kind.id;shop(l,kind);
        }
        for(Lot l:t.layout.lots){
            if(l.zone.equals("station-street")&&rnd.nextInt(5)<3){
                Kind kind=pool.get(k++%pool.size());l.use="shop:"+kind.id;shop(l,kind);
            }
            if(l.zone.equals("konbini")){l.use="konbini";konbini(l);}
        }
        // the public bath and the inn on chosen plots
        Lot bath=pick(l->l.zone.equals("row")&&l.frontage()>=12&&l.cx()<-20&&l.cx()>-110&&l.cz()<-24&&l.cz()>-41);
        if(bath!=null){bath.use="sento";sento(bath);}
        Lot inn=pick(l->l.zone.equals("terrace")&&l.frontage()>=13&&l.y==Plan.TERRACE_Y[0]&&l.cx()>20);
        if(inn!=null){inn.use="ryokan";ryokan(inn);}
        arch(Plan.ST_N[0]-1,"ぼたんど銀座");arch(Plan.ST_S[3]+1,"ぼたんど銀座");
        Town.log("shops: "+shops);
    }
    interface Test{boolean ok(Lot l);}
    private Lot pick(Test test){
        List<Lot> l=new ArrayList<>();for(Lot x:t.layout.lots)if(test.ok(x))l.add(x);
        return l.isEmpty()?null:l.get(rnd.nextInt(l.size()));
    }

    // ---------------------------------------------------------------- shop-houses

    /** A shop-house: the shop on the ground floor, a flat front carried up past the roof, the family's rooms above. */
    void shop(Lot lot,Kind k){
        Frame f=Frame.of(c,lot);
        int W=f.W,D=f.D;
        int depth=Math.min(D-2,11),stories=rnd.nextInt(10)<3?3:2;
        t.houses.yard(f,false);
        for(int x=0;x<W;x++)for(int z=0;z<D;z++)f.set(x,0,z,z<depth?s(Blocks.SMOOTH_STONE):s(Blocks.GRASS_BLOCK));
        BlockState face=k.face(),side=s(Blocks.LIGHT_GRAY_CONCRETE),floor=s(Blocks.OAK_PLANKS);
        Kit.shell(f,0,0,W-1,depth-1,stories,side,side,side,floor,s(Blocks.SMOOTH_STONE));
        // the front wall in the shop's own facing, carried two blocks above the roof as a flat front
        int top=4*stories+1;
        for(int x=0;x<W;x++)for(int y=1;y<=top;y++)f.set(x,y,0,face);
        for(int x=0;x<W;x++)f.set(x,top+1,0,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
        // the shop front: glass across, the door, an open front for those that sell in the street
        boolean open=Set.of("fish","grocer","florist","souvenir","toys").contains(k.id());
        int door=W/2;
        for(int x=1;x<W-1;x++)for(int y=1;y<=3;y++)f.set(x,y,0,open&&y<=3?s(Blocks.AIR):x==door&&y<=2?s(Blocks.AIR):s(Blocks.GLASS_PANE));
        // the name board over the shop front and the awning under it
        for(int x=0;x<W;x++){f.set(x,4,0,s(Blocks.WHITE_CONCRETE));f.set(x,3,-1,s((x&1)==0?k.awning():k.awning2()));}
        f.wallSign(W/2,4,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,k.text(),true,"",k.name(),k.sub());
        if(W>=9){f.wallSign(1,4,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,k.text(),true,"",k.vertical());f.wallSign(W-2,4,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,k.text(),true,"",k.vertical());}
        // the standing sign out from the front, its name read downward on both faces
        int sy=5;
        for(int y=sy;y<=sy+3;y++)f.set(W-1,y,-1,s(Blocks.WHITE_CONCRETE));
        String[] v=verticalLines(k.vertical());
        f.wallSign(W-1,sy+2,-2,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,k.text(),true,v);
        f.wallSign(W-2,sy+2,-1,Blocks.BIRCH_WALL_SIGN,Direction.WEST,k.text(),true,v);
        f.set(W-1,sy+4,-1,s(Blocks.SEA_LANTERN));
        // upper floors: windows, a balcony rail now and then, plants
        for(int s=1;s<stories;s++){
            int fy=4*s;
            for(int x=1;x<W-1;x++){if(x==W/2&&rnd.nextBoolean())continue;f.set(x,fy+2,0,s(Blocks.GLASS_PANE));if(rnd.nextInt(3)>0)f.set(x,fy+3,0,s(Blocks.GLASS_PANE));}
            if(rnd.nextInt(3)==0)for(int x=1;x<W-1;x++)f.set(x,fy+1,-1,s(Blocks.IRON_BARS));
            if(rnd.nextBoolean())f.set(2,fy+1,-1,Kit.pot(rnd));
            Kit.roomLight(f,W/2,fy,depth/2,rnd.nextBoolean());
        }
        Kit.roof(f,0,1,W-1,depth-1,4*stories,Kit.SHED,false,Kit.SLATE,side,floor);
        // bright lights in the shop
        f.set(W/2,4,depth/2,s(Blocks.SEA_LANTERN));f.set(2,4,2,s(Blocks.SEA_LANTERN));
        goods(f,k,W,depth,door);
        shops++;
    }
    /** A name split down a standing sign: up to four lines of one or two characters. */
    static String[] verticalLines(String s){
        int n=s.length();String[] out=new String[4];Arrays.fill(out,"");
        if(n<=4){for(int i=0;i<n;i++)out[i]=String.valueOf(s.charAt(i));}
        else{int per=(n+3)/4;for(int i=0;i<4;i++)out[i]=s.substring(Math.min(n,i*per),Math.min(n,(i+1)*per));}
        return out;
    }
    /** What a shop shows: noren and red lanterns for food, wares out front for the open shops, counters inside. */
    private void goods(Frame f,Kind k,int W,int depth,int door){
        int c1=depth-3;
        switch(k.id()){
            case "ramen","izakaya","sushi","soba"->{
                // noren over the door, a red lantern beside it, a counter and stools inside
                DyeColor n=k.id().equals("ramen")?DyeColor.RED:DyeColor.BLUE;
                f.set(door,2,-1,bannerOf(n).with(Properties.HORIZONTAL_FACING,Direction.NORTH));
                if(!k.id().equals("sushi")){f.set(door-2,2,-1,s(Blocks.SHROOMLIGHT));f.set(door-2,3,-1,s(Blocks.CHAIN));}
                for(int x=1;x<W-1;x++){f.set(x,1,c1,Canvas.slab(Blocks.SPRUCE_SLAB,true));f.set(x,1,c1-2,s(Blocks.SPRUCE_FENCE));}
            }
            case "cafe","snack"->{
                for(int x=1;x<W-1;x+=3){f.set(x,1,3,s(Blocks.SPRUCE_FENCE));f.set(x,2,3,Canvas.slab(Blocks.SPRUCE_SLAB,false));}
                f.standingSign(door+1,1,-1,Blocks.SPRUCE_SIGN,Direction.NORTH,DyeColor.WHITE,true,k.id().equals("cafe")?"本日のおすすめ":"営業中",k.id().equals("cafe")?"ナポリタン":"カラオケ","");
            }
            case "fish"->{for(int x=1;x<W-1;x++){f.set(x,1,1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));f.set(x,2,1,rnd.nextBoolean()?s(Blocks.PACKED_ICE):s(Blocks.DRIED_KELP_BLOCK));}}
            case "grocer"->{for(int x=1;x<W-1;x++){f.set(x,1,1,s(Blocks.BARREL));f.set(x,2,1,Pal.pick(rnd,Blocks.MELON,Blocks.PUMPKIN,Blocks.HAY_BLOCK,Blocks.MOSS_BLOCK).getDefaultState());}}
            case "florist"->{for(int x=0;x<W;x++){if(f.air(x,1,-1))f.set(x,1,-1,Kit.pot(rnd));f.set(x,1,1,Canvas.leaves(rnd.nextBoolean()?Blocks.FLOWERING_AZALEA_LEAVES:Blocks.AZALEA_LEAVES));}}
            case "books","pharmacy","realty","photo"->{for(int x=1;x<W-1;x++){f.set(x,1,c1,s(Blocks.CHISELED_BOOKSHELF));f.set(x,2,c1,s(Blocks.BOOKSHELF));}}
            case "bakery","sweets"->{for(int x=1;x<W-1;x++){f.set(x,1,2,Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,true));if(rnd.nextBoolean())f.set(x,2,2,s(Blocks.CAKE));}}
            case "liquor","tea"->{for(int x=1;x<W-1;x++)f.set(x,1,c1,s(Blocks.BARREL));f.set(door+1,3,-1,Canvas.leaves(Blocks.SPRUCE_LEAVES));}
            case "laundry","cleaning"->{for(int x=1;x<W-1;x++){f.set(x,1,c1,s(Blocks.WHITE_CONCRETE));f.set(x,1,c1-1,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,true,false));}}
            case "barber"->{f.set(0,1,-1,s(Blocks.RED_STAINED_GLASS_PANE));f.set(0,2,-1,s(Blocks.WHITE_STAINED_GLASS_PANE));f.set(0,3,-1,s(Blocks.BLUE_STAINED_GLASS_PANE));
                for(int x=2;x<W-1;x+=3)f.set(x,1,3,Canvas.stairs(Blocks.QUARTZ_STAIRS,Direction.NORTH,false));}
            case "electric"->{for(int x=1;x<W-1;x++){f.set(x,1,1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));f.set(x,2,1,s(Blocks.BLACK_STAINED_GLASS));}}
            case "bike"->{for(int x=1;x<W-1;x+=2)if(f.air(x,1,-1))f.set(x,1,-1,s(Blocks.IRON_BARS));}
            case "tobacco"->{f.set(door,2,0,s(Blocks.GLASS_PANE));t.street.vending(f.wx(W-2,-1),f.wz(W-2,-1),f.dir(Direction.WEST),2);}
            default->{for(int x=1;x<W-1;x++)f.set(x,1,c1,s(Blocks.BARREL));if(f.air(1,1,-1))f.set(1,1,-1,Kit.pot(rnd));}
        }
        if(rnd.nextInt(4)==0&&!k.id().equals("tobacco"))t.street.vending(f.wx(0,-1),f.wz(0,-1),f.dir(Direction.EAST),1);
    }
    static BlockState bannerOf(DyeColor c){
        return switch(c){case RED->s(Blocks.RED_WALL_BANNER);case WHITE->s(Blocks.WHITE_WALL_BANNER);default->s(Blocks.BLUE_WALL_BANNER);};
    }

    // ---------------------------------------------------------------- special buildings

    /** ボタンドマート: a convenience store, its car park in front, a lit pole sign at the street corner. */
    void konbini(Lot lot){
        Frame f=Frame.of(c,lot);
        int W=f.W,D=f.D;
        t.houses.yard(f,false);
        for(int x=0;x<W;x++)for(int z=0;z<D;z++)f.set(x,0,z,Pal.asphalt(f.wx(x,z),f.wz(x,z)));
        int b0=Math.max(8,D-10),b1=D-1,x0=1,x1=W-2;
        Kit.shell(f,x0,b0,x1,b1,1,s(Blocks.WHITE_CONCRETE),s(Blocks.WHITE_CONCRETE),s(Blocks.WHITE_CONCRETE),s(Blocks.SMOOTH_STONE),s(Blocks.SMOOTH_STONE));
        // the front: glass all along, the door, Botand's blue band with the name
        for(int x=x0+1;x<x1;x++)for(int y=1;y<=3;y++)f.set(x,y,b0,s(Blocks.GLASS_PANE));
        int door=x1-3;f.set(door,1,b0,s(Blocks.AIR));f.set(door,2,b0,s(Blocks.AIR));f.set(door+1,1,b0,s(Blocks.AIR));f.set(door+1,2,b0,s(Blocks.AIR));
        for(int x=x0;x<=x1;x++){f.set(x,4,b0,s(Blocks.LIGHT_BLUE_CONCRETE));f.set(x,5,b0,s(Blocks.BLUE_CONCRETE));f.set(x,6,b0,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));}
        for(int z=b0;z<=b1;z++)for(int x:new int[]{x0,x1}){f.set(x,4,z,s(Blocks.LIGHT_BLUE_CONCRETE));f.set(x,5,z,s(Blocks.BLUE_CONCRETE));}
        f.wallSign((x0+x1)/2,4,b0-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLUE,true,"","ボタンドマート","BOTAND MART 24h");
        f.wallSign(x0+2,4,b0-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLUE,true,"","24時間営業");
        // inside: shelves, the counter, drink fridges glowing along the back, lights everywhere
        for(int x=x0+2;x<x1-3;x++)for(int z=b0+3;z<b1-2;z+=3)f.set(x,1,z,s(Blocks.CHISELED_BOOKSHELF));
        for(int z=b0+2;z<b1-1;z++)f.set(x1-2,1,z,s(Blocks.SMOOTH_QUARTZ));
        for(int x=x0+1;x<x1;x++){f.set(x,1,b1-1,s(Blocks.LIGHT_BLUE_STAINED_GLASS));f.set(x,2,b1-1,s(Blocks.LIGHT_BLUE_STAINED_GLASS));f.set(x,1,b1,s(Blocks.SEA_LANTERN));f.set(x,2,b1,s(Blocks.SEA_LANTERN));}
        for(int x=x0;x<=x1;x++)for(int z=b0;z<=b1;z++)f.set(x,4,z,x==x0||x==x1||z==b0||z==b1?f.get(x,4,z):(x+z)%3==0?s(Blocks.SEA_LANTERN):s(Blocks.SMOOTH_QUARTZ));
        for(int x=x0;x<=x1;x++)for(int z=b0;z<=b1;z++)if(!(x==x0||x==x1||z==b0||z==b1))f.set(x,5,z,s(Blocks.SMOOTH_STONE));
        // the car park: painted bays, a car, wheel stops; the pole sign at the corner; bins and machines by the door
        for(int x=1;x<W-1;x+=3){for(int z=1;z<b0-1;z++)f.set(x,0,z,s(Blocks.WHITE_CONCRETE));if(x+1<W-1)f.set(x+1,1,b0-1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));}
        Kit.car(f,4,b0-6,rnd);if(rnd.nextBoolean())Kit.car(f,10,b0-6,rnd);
        for(int y=1;y<=7;y++)f.set(0,y,0,s(Blocks.ANDESITE_WALL));
        for(int dx=0;dx<=1;dx++)for(int y=8;y<=9;y++){f.set(dx,y,0,s(Blocks.SEA_LANTERN));f.set(dx,y,-1,s(Blocks.LIGHT_BLUE_STAINED_GLASS));f.set(dx,y,1,s(Blocks.LIGHT_BLUE_STAINED_GLASS));}
        t.street.vending(f.wx(door+3,b0-1),f.wz(door+3,b0-1),f.dir(Direction.EAST),2);
        f.set(door-1,1,b0-1,s(Blocks.CAULDRON));f.set(door-2,1,b0-1,s(Blocks.CAULDRON));
        t.places.put("konbini",new int[]{f.wx(door,b0-2),Plan.COAST+1,f.wz(door,b0-2)});
        shops++;
    }

    /** 郵便局: two storeys, the red band and mark, a post box at the door. */
    void post(Lot lot){
        Frame f=Frame.of(c,lot);int W=f.W,D=f.D,depth=Math.min(D-2,11);
        t.houses.yard(f,false);
        Kit.shell(f,0,0,W-1,depth-1,2,s(Blocks.WHITE_CONCRETE),s(Blocks.WHITE_CONCRETE),s(Blocks.RED_CONCRETE),s(Blocks.SMOOTH_STONE),s(Blocks.SMOOTH_STONE));
        for(int x=1;x<W-1;x++)for(int y=1;y<=3;y++)f.set(x,y,0,s(Blocks.GLASS_PANE));
        f.set(W/2,1,0,s(Blocks.AIR));f.set(W/2,2,0,s(Blocks.AIR));
        for(int x=0;x<W;x++)f.set(x,4,0,s(Blocks.RED_CONCRETE));
        f.wallSign(W/2,4,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.RED,true,"〒","ぼたんど郵便局","POST OFFICE");
        Kit.windows(f,rnd,0,0,W-1,depth-1,'f',1,s(Blocks.GLASS_PANE),s(Blocks.GLASS_PANE),-99,true);
        Kit.roof(f,0,0,W-1,depth-1,8,Kit.FLAT,true,Kit.SLATE,s(Blocks.WHITE_CONCRETE),s(Blocks.SMOOTH_STONE));
        f.set(2,4,2,s(Blocks.SEA_LANTERN));f.set(W-3,4,2,s(Blocks.SEA_LANTERN));
        for(int x=1;x<W-1;x++)f.set(x,1,depth-3,s(Blocks.SMOOTH_QUARTZ));
        t.street.postBox(f.wx(1,-1),f.wz(1,-1));
        t.places.put("post",new int[]{f.wx(W/2,-1),f.y0+1,f.wz(W/2,-1)});
        shops++;
    }
    /** 医院: a small clinic, its name and hours by the door. */
    void clinic(Lot lot){
        Frame f=Frame.of(c,lot);int W=f.W,D=f.D,depth=Math.min(D-2,11);
        t.houses.yard(f,false);
        Kit.shell(f,0,0,W-1,depth-1,2,s(Blocks.SMOOTH_QUARTZ),s(Blocks.SMOOTH_QUARTZ),s(Blocks.LIGHT_BLUE_TERRACOTTA),s(Blocks.SMOOTH_STONE),s(Blocks.SMOOTH_STONE));
        for(int s=0;s<2;s++)Kit.windows(f,rnd,0,0,W-1,depth-1,'f',s,s(Blocks.WHITE_STAINED_GLASS_PANE),s(Blocks.GLASS_PANE),s==0?W/2:-99,true);
        f.door(W/2,1,0,Blocks.BIRCH_DOOR,Direction.NORTH,false);
        f.wallSign(W/2-1,2,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.GREEN,false,"浜田医院","内科・小児科","9:00-18:00","休診 日・祝");
        Kit.roof(f,0,0,W-1,depth-1,8,Kit.GABLE,true,Kit.BLUE,s(Blocks.SMOOTH_QUARTZ),s(Blocks.SMOOTH_STONE));
        Kit.roomLight(f,W/2,0,depth/2,false);
        shops++;
    }

    /** 銭湯: the public bath under a big tiled roof, two noren, a mural of Mt Fuji inside, the brick chimney smoking. */
    void sento(Lot lot){
        Frame f=Frame.of(c,lot);int W=f.W,D=f.D;
        t.houses.yard(f,true);
        int x0=1,x1=W-2,z0=2,z1=D-2;
        BlockState wood=s(Blocks.DARK_OAK_PLANKS),plaster=s(Blocks.CALCITE),post=Canvas.axis(Blocks.STRIPPED_DARK_OAK_LOG,Direction.Axis.Y);
        Kit.shell(f,x0,z0,x1,z1,1,wood,post,s(Blocks.STRIPPED_DARK_OAK_WOOD),s(Blocks.SMOOTH_STONE),s(Blocks.STONE_BRICKS));
        Houses.perimeter(f,x0,z0,x1,z1,(x,z,corner)->{for(int y=1;y<=5;y++)f.set(x,y,z,corner?post:y>=4?plaster:wood);});
        for(int x=x0+1;x<x1;x++)for(int z=z0+1;z<z1;z++){f.set(x,4,z,s(Blocks.AIR));f.set(x,5,z,s(Blocks.AIR));}
        // two entrances: blue noren for men, red for women, ゆ over both
        int m=(x0+x1)/2;
        for(int dx:new int[]{-2,2}){f.set(m+dx,1,z0,s(Blocks.AIR));f.set(m+dx,2,z0,s(Blocks.AIR));
            f.set(m+dx,2,z0-1,(dx<0?s(Blocks.BLUE_WALL_BANNER):s(Blocks.RED_WALL_BANNER)).with(Properties.HORIZONTAL_FACING,Direction.NORTH));}
        f.wallSign(m-2,4,z0-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","男湯","");
        f.wallSign(m+2,4,z0-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","女湯","");
        f.wallSign(m,4,z0-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"ゆ","なみの湯","");
        for(int x=m-3;x<=m+3;x++)f.set(x,3,z0-1,Canvas.slab(Blocks.DEEPSLATE_TILE_SLAB,false));
        // the baths inside, and Mt Fuji painted across the back wall
        for(int x=x0+2;x<=x1-2;x++)for(int z=z1-4;z<=z1-2;z++){boolean rim=x==x0+2||x==x1-2||z==z1-4||z==z1-2;f.set(x,0,z,s(Blocks.SMOOTH_STONE));f.set(x,1,z,rim?s(Blocks.SMOOTH_STONE):s(Blocks.WATER));}
        for(int x=x0+1;x<x1;x++)for(int y=1;y<=5;y++){
            int dx=Math.abs(x-m);
            BlockState p=y>=5-Math.max(0,3-dx/2)&&dx<=8?(y>=4&&dx<=2?s(Blocks.WHITE_CONCRETE):s(Blocks.BLUE_CONCRETE)):s(Blocks.LIGHT_BLUE_CONCRETE);
            f.set(x,y,z1-1,p);
        }
        f.hangingLantern(m,5,(z0+z1)/2,false);f.hangingLantern(x0+2,5,(z0+z1)/2,false);
        Kit.roof(f,x0,z0,x1,z1,6,Kit.GABLE,false,Kit.KAWARA,plaster,s(Blocks.SPRUCE_PLANKS));
        // the chimney behind, smoke from its top
        int cx=x1,cz=z1+1;
        for(int y=1;y<=26;y++)for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++)f.set(cx-dx,y,cz-dz,y>=24?s(Blocks.STONE_BRICKS):s(Blocks.BRICKS));
        f.set(cx,27,cz,s(Blocks.CAMPFIRE).with(Properties.LIT,true).with(Properties.SIGNAL_FIRE,true));
        f.set(cx,26,cz,s(Blocks.HAY_BLOCK));
        f.wallSign(cx-1,20,cz-2,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"な","み","の","湯");
        t.places.put("sento",new int[]{f.wx(m,z0-2),f.y0+1,f.wz(m,z0-2)});
        shops++;
    }

    /** 旅館: a two-storey wooden inn looking over the town to the sea, lanterns at its gate. */
    void ryokan(Lot lot){
        Frame f=Frame.of(c,lot);int W=f.W,D=f.D;
        t.houses.yard(f,true);
        int x0=1,x1=W-2,z0=3,z1=D-2;
        BlockState post=Canvas.axis(Blocks.STRIPPED_SPRUCE_LOG,Direction.Axis.Y),wood=s(Blocks.SPRUCE_PLANKS),plaster=s(Blocks.CALCITE);
        Kit.shell(f,x0,z0,x1,z1,2,wood,post,s(Blocks.STRIPPED_DARK_OAK_WOOD),s(Blocks.STRIPPED_BAMBOO_BLOCK),s(Blocks.STONE_BRICKS));
        for(int s=0;s<2;s++){
            final int fy=4*s;
            Houses.perimeter(f,x0,z0,x1,z1,(x,z,corner)->{if(corner)return;boolean p=(x-x0)%3==0&&(z==z0||z==z1)||(z-z0)%3==0&&(x==x0||x==x1);
                for(int y=fy+1;y<=fy+3;y++)f.set(x,y,z,p?post:y==fy+3?plaster:wood);});
            for(int x=x0+1;x<x1;x++)if((x-x0)%3!=0){f.set(x,fy+2,z1,s(Blocks.WHITE_STAINED_GLASS_PANE));f.set(x,fy+2,z0,s(Blocks.WHITE_STAINED_GLASS_PANE));}
            for(int x=x0+2;x<x1;x+=4)f.hangingLantern(x,fy+3,(z0+z1)/2,false);
        }
        int m=(x0+x1)/2;
        f.door(m,1,z0,Blocks.JUNGLE_DOOR,Direction.NORTH,false);f.door(m+1,1,z0,Blocks.JUNGLE_DOOR,Direction.NORTH,true);
        for(int x=m-2;x<=m+3;x++)f.set(x,4,z0-1,Canvas.slab(Blocks.DEEPSLATE_TILE_SLAB,false));
        f.wallSign(m,3,z0-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","旅館 汐見荘","");
        for(int x:new int[]{m-3,m+4}){f.set(x,1,1,s(Blocks.STONE_BRICK_WALL));f.set(x,2,1,s(Blocks.LANTERN));}
        for(int x=x0-1;x<=x1+1;x++)for(int z=z0-1;z<=z1+1;z++)if((x==x0-1||x==x1+1||z==z0-1||z==z1+1)&&f.air(x,4,z))f.set(x,4,z,Canvas.slab(Blocks.DEEPSLATE_TILE_SLAB,false));
        Kit.roof(f,x0,z0,x1,z1,8,Kit.HIP,true,Kit.KAWARA,plaster,wood);
        t.nature.pine(f.wx(1,1),f.y0+1,f.wz(1,1),4);
        t.places.put("ryokan",new int[]{f.wx(m,z0-2),f.y0+1,f.wz(m,z0-2)});
        shops++;
    }

    /** An arch over the main street with the shopping street's name, lamps along its beam. */
    void arch(int z,String name){
        int g=c.ground(Plan.MAIN_X0,z);
        for(int x:new int[]{Plan.MAIN_X0-1,Plan.MAIN_X1+1}){int gg=c.ground(x,z);for(int y=gg+1;y<=g+7;y++)c.set(x,y,z,s(Blocks.POLISHED_ANDESITE));}
        for(int x=Plan.MAIN_X0-1;x<=Plan.MAIN_X1+1;x++){c.set(x,g+8,z,s(Blocks.WHITE_CONCRETE));c.set(x,g+9,z,s(Blocks.LIGHT_BLUE_CONCRETE));if(Math.floorMod(x,3)==0)c.set(x,g+10,z,s(Blocks.SEA_LANTERN));}
        for(Direction d:new Direction[]{Direction.NORTH,Direction.SOUTH}){
            c.wallSign(0,g+8,z+d.getOffsetZ(),Blocks.BIRCH_WALL_SIGN,d,DyeColor.BLUE,true,"",name,"BOTAND GINZA");
            c.wallSign(-3,g+8,z+d.getOffsetZ(),Blocks.BIRCH_WALL_SIGN,d,DyeColor.BLUE,true,"","商店街","");
            c.wallSign(3,g+8,z+d.getOffsetZ(),Blocks.BIRCH_WALL_SIGN,d,DyeColor.BLUE,true,"","ようこそ","");
        }
    }
}
