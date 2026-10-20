package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import dev.sangwoo.celestialfall.town.RoofShape;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The houses of the town and what fills the plots between them: old wooden houses with tiled roofs, engawa and
 * gardens; two- and three-storey modern houses in pale siding with a car in front; small apartment blocks with an
 * open corridor and a stair; monthly car parks, vegetable plots, gardens and empty plots gone to seed.
 */
final class Houses {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final String[] NAMES={"佐藤","鈴木","高橋","田中","伊藤","渡辺","山本","中村","小林","加藤","吉田","山田","佐々木","山口","松本",
        "井上","木村","林","斎藤","清水","山崎","森","池田","橋本","阿部","石川","山下","中島","石井","小川","前田","岡田","長谷川","藤田",
        "後藤","近藤","村上","遠藤","青木","坂本","福田","太田","西村","藤井","岡本","中野","三浦","原田","中川","松田","竹内","小野",
        "田村","和田","石田","森田","上田","内田","柴田","酒井","宮崎","横山","安藤","宮本","大野","谷口","工藤","丸山","杉山","野口",
        "浜田","磯部","網元","汐見","海野","灘","浦田","岬","渚"};
    static final String[] APARTMENTS={"メゾン潮風","コーポ浜木綿","ハイツ松風","コーポ汐見","メゾン岬","ハイツ浜通り","コーポ凪","グリーンハイツ"};
    static final BlockState[] SIDING={s(Blocks.SMOOTH_QUARTZ),s(Blocks.WHITE_CONCRETE),s(Blocks.SMOOTH_SANDSTONE),s(Blocks.LIGHT_GRAY_CONCRETE),
        s(Blocks.LIGHT_GRAY_TERRACOTTA),s(Blocks.BIRCH_PLANKS),s(Blocks.MUSHROOM_STEM),s(Blocks.CALCITE),s(Blocks.WHITE_TERRACOTTA),s(Blocks.SMOOTH_QUARTZ),
        s(Blocks.CYAN_TERRACOTTA),s(Blocks.GRAY_CONCRETE),s(Blocks.BROWN_TERRACOTTA),s(Blocks.SMOOTH_SANDSTONE),s(Blocks.CALCITE)};
    static final Kit.Roof[] MODERN_ROOFS={Kit.BLACK,Kit.BLACK,Kit.KAWARA,Kit.SLATE,Kit.BLUE,Kit.BROWN,Kit.TEAL,Kit.KAWARA};
    static final Kit.Roof[] OLD_ROOFS={Kit.KAWARA,Kit.KAWARA,Kit.KAWARA,Kit.KAWARA,Kit.BLUE,Kit.BLACK,Kit.RED};

    Houses(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}
    String name(){return NAMES[rnd.nextInt(NAMES.length)];}

    void build(Lot lot){
        Frame f=Frame.of(c,lot);
        switch(lot.use){
            case "old"->old(f);
            case "modern"->modern(f,false);
            case "modern3"->modern(f,true);
            case "apartment"->apartment(f);
            case "parking"->parking(f);
            case "field"->field(f);
            case "garden"->garden(f);
            default->vacant(f);
        }
    }

    // ---------------------------------------------------------------- old wooden house

    /** 古い木造の家: posts and dark boards under white plaster, a tiled roof (two-tiered when two storeys), a garden. */
    void old(Frame f){
        int W=f.W,D=f.D;
        int hw=Math.min(W-2,9+rnd.nextInt(4)),hd=Math.min(D-5,7+rnd.nextInt(3));
        if(hw<5||hd<5){garden(f);return;}
        int bx0=(W-hw)/2,bx1=bx0+hw-1,bz0=2,bz1=bz0+hd-1;
        int stories=rnd.nextInt(5)<2?1:2;
        BlockState post=Canvas.axis(Blocks.STRIPPED_SPRUCE_LOG,Direction.Axis.Y),boards=s(rnd.nextBoolean()?Blocks.DARK_OAK_PLANKS:Blocks.SPRUCE_PLANKS);
        BlockState plaster=s(rnd.nextInt(3)==0?Blocks.WHITE_TERRACOTTA:Blocks.CALCITE);
        BlockState beam=s(Blocks.STRIPPED_DARK_OAK_WOOD),floor=s(Blocks.SPRUCE_PLANKS),tatami=s(Blocks.STRIPPED_BAMBOO_BLOCK);
        Kit.Roof roof=OLD_ROOFS[rnd.nextInt(OLD_ROOFS.length)];
        // the ground: a front yard of gravel and stepping stones, a garden behind
        yard(f,true);
        Kit.shell(f,bx0,bz0,bx1,bz1,stories,boards,post,beam,tatami,s(Blocks.STONE_BRICKS));
        // plaster above the boards, posts every few blocks
        for(int s=0;s<stories;s++){
            final int fy=4*s,story=s;
            perimeter(f,bx0,bz0,bx1,bz1,(x,z,cornerCol)->{
                if(cornerCol)return;
                boolean postCol=((x-bx0)%3==0&&(z==bz0||z==bz1))||((z-bz0)%3==0&&(x==bx0||x==bx1));
                for(int y=fy+1;y<=fy+3;y++)f.set(x,y,z,postCol?post:y==fy+3||story>0&&y==fy+2?plaster:boards);
            });
        }
        // windows: frosted panes (shoji behind), each ground-floor window under a little tiled eave
        BlockState pane=s(Blocks.WHITE_STAINED_GLASS_PANE),glass=s(Blocks.GLASS_PANE);
        int door=bx0+1+rnd.nextInt(Math.max(1,hw-3));
        for(int s=0;s<stories;s++){
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'f',s,pane,glass,s==0?door:-99,s>0);
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'l',s,pane,glass,-99,false);
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'r',s,pane,glass,-99,false);
        }
        // the garden side: glass doors onto an engawa (veranda)
        for(int x=bx0+1;x<=bx1-1;x++){
            if((x-bx0)%3==0)continue;
            f.set(x,1,bz1,pane);f.set(x,2,bz1,pane);
            f.set(x,1,bz1+1,Canvas.slab(Blocks.SPRUCE_SLAB,false));
        }
        if(stories==2)Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'b',1,glass,pane,-99,true);
        hisashi(f,bx0,bz0,bx1,bz1,roof);
        Kit.entrance(f,door,bz0,Blocks.JUNGLE_DOOR,roof,name(),true);
        if(stories==2){
            // the ground floor's own skirt of roof round the house, the main roof above
            skirt(f,bx0,bz0,bx1,bz1,4,roof);
            Kit.roof(f,bx0,bz0,bx1,bz1,8,rnd.nextBoolean()?Kit.HIP:Kit.GABLE,hw>=hd,roof,plaster,floor);
        }else Kit.roof(f,bx0,bz0,bx1,bz1,4,rnd.nextInt(3)==0?Kit.GABLE:Kit.HIP,hw>=hd,roof,plaster,floor);
        // rooms: warm lights, low tables
        for(int s=0;s<stories;s++){
            int fy=4*s;
            if(rnd.nextInt(5)>0)Kit.roomLight(f,(bx0+bx1)/2,fy,(bz0+bz1)/2,true);
            f.set(bx0+2,fy+1,bz1-2,Canvas.slab(Blocks.SPRUCE_SLAB,true));
            if(rnd.nextBoolean())f.set(bx1-1,fy+1,bz0+1,s(Blocks.CHISELED_BOOKSHELF));
        }
        // garden: a pine, a stone lantern, shrubs, potted plants at the door
        int gz=bz1+2;
        if(D-1-gz>=2){
            t.nature.pine(f.wx(bx0+1,Math.min(D-2,gz+1)),f.y0+1,f.wz(bx0+1,Math.min(D-2,gz+1)),3+rnd.nextInt(2));
            stoneLantern(f,bx1-1,1,Math.min(D-2,gz));
            for(int x=bx0+3;x<bx1-1;x+=2)if(rnd.nextBoolean())f.set(x,1,D-2,Canvas.leaves(rnd.nextBoolean()?Blocks.AZALEA_LEAVES:Blocks.FLOWERING_AZALEA_LEAVES));
        }
        for(int x=door+2;x<=Math.min(bx1,door+3);x++)if(rnd.nextBoolean())f.set(x,1,bz0-1,Kit.pot(rnd));
        lotFence(f,door,rnd.nextInt(3));
        if(rnd.nextBoolean())Kit.airCon(f,bx1+1,1,bz1-1,Direction.EAST);
    }

    /** A small tiled eave over every ground-floor window of the front and sides, falling away from the wall. */
    private void hisashi(Frame f,int bx0,int bz0,int bx1,int bz1,Kit.Roof roof){
        for(int x=bx0+1;x<bx1;x++)if(isPane(f.get(x,2,bz0)))Kit.cell(f,x,3,bz0-1,RoofShape.SLOPE,Direction.NORTH,0,roof);
        for(int z=bz0+1;z<bz1;z++){
            if(isPane(f.get(bx0,2,z)))Kit.cell(f,bx0-1,3,z,RoofShape.SLOPE,Direction.WEST,0,roof);
            if(isPane(f.get(bx1,2,z)))Kit.cell(f,bx1+1,3,z,RoofShape.SLOPE,Direction.EAST,0,roof);
        }
    }
    private static boolean isPane(BlockState s){return s.getBlock() instanceof PaneBlock;}
    /**
     * A one-block skirt of roof round the house at a floor line (the lower tier of a two-tier roof): each side falls
     * away from the wall, and the corners are hips.
     */
    private void skirt(Frame f,int bx0,int bz0,int bx1,int bz1,int by,Kit.Roof roof){
        for(int x=bx0-1;x<=bx1+1;x++)for(int z=bz0-1;z<=bz1+1;z++){
            boolean w=x==bx0-1,e=x==bx1+1,n=z==bz0-1,s=z==bz1+1;
            if(!(w||e||n||s))continue;
            // a corner falls both ways: a hip named by the first of its two falls going clockwise (north then east...)
            Direction falls=n&&e?Direction.NORTH:e&&s?Direction.EAST:s&&w?Direction.SOUTH:w&&n?Direction.WEST:n?Direction.NORTH:s?Direction.SOUTH:w?Direction.WEST:Direction.EAST;
            Kit.cell(f,x,by,z,(n||s)&&(w||e)?RoofShape.HIP:RoofShape.SLOPE,falls,0,roof);
        }
    }
    void stoneLantern(Frame f,int bx,int by,int bz){
        f.set(bx,by,bz,Blocks.STONE_BRICK_WALL);f.set(bx,by+1,bz,Blocks.LANTERN);f.set(bx,by+2,bz,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));
    }

    // ---------------------------------------------------------------- modern house

    /** 今の家: two storeys (three for some, with a flat roof) in pale siding, a balcony at the back, a car in front. */
    void modern(Frame f,boolean three){
        int W=f.W,D=f.D;
        int front=4+rnd.nextInt(2);
        int hw=Math.min(W-2,7+rnd.nextInt(4)),hd=Math.min(D-front-2,6+rnd.nextInt(4));
        if(hw<5||hd<5){old(f);return;}
        boolean carLeft=rnd.nextBoolean();
        int bx0=carLeft?W-1-hw:1,bx1=bx0+hw-1,bz0=front,bz1=bz0+hd-1;
        int stories=three&&hw>=7?3:2;
        BlockState wall=SIDING[rnd.nextInt(SIDING.length)];
        // most houses are one colour all round; some have a pale band at the floor line
        BlockState trim=rnd.nextInt(3)==0?s(Blocks.SMOOTH_QUARTZ):wall;
        if(trim.getBlock()==wall.getBlock()&&rnd.nextInt(4)==0)trim=s(Blocks.LIGHT_GRAY_CONCRETE);
        BlockState floor=s(Pal.pick(rnd,Blocks.OAK_PLANKS,Blocks.BIRCH_PLANKS,Blocks.SPRUCE_PLANKS,Blocks.BAMBOO_PLANKS));
        Kit.Roof roof=MODERN_ROOFS[rnd.nextInt(MODERN_ROOFS.length)];
        int type=stories==3||rnd.nextInt(10)==0?Kit.FLAT:Pal.pick(rnd,new Integer[]{Kit.GABLE,Kit.GABLE,Kit.SHED,Kit.SHED,Kit.HIP});
        yard(f,false);
        // the parking pad in front
        int px=carLeft?1:bx1+1;
        if(px+1<W-1||carLeft){
            int pa=carLeft?1:Math.min(W-3,bx1+1);
            f.fill(pa,0,0,pa+1,0,front+3,s(Blocks.SMOOTH_STONE));
            if(rnd.nextInt(5)>0)Kit.car(f,pa,1,rnd);
        }
        Kit.shell(f,bx0,bz0,bx1,bz1,stories,wall,wall,trim,floor,s(Blocks.SMOOTH_STONE));
        BlockState pane=s(Blocks.GLASS_PANE),frost=s(Blocks.WHITE_STAINED_GLASS_PANE);
        int door=carLeft?bx0+1:bx1-1;
        for(int s=0;s<stories;s++){
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'f',s,frost,pane,s==0?door:-99,false);
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'l',s,pane,frost,-99,s>0);
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'r',s,pane,frost,-99,s>0);
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'b',s,pane,frost,-99,true);
        }
        // big glass doors onto the garden, and a balcony with a solid parapet above them
        for(int x=bx0+1;x<=Math.min(bx1-1,bx0+3);x++){f.set(x,1,bz1,pane);f.set(x,2,bz1,pane);f.set(x,3,bz1,pane);}
        for(int x=bx0;x<=bx1;x++){
            f.set(x,4,bz1+1,s(Blocks.SMOOTH_STONE));
            f.set(x,5,bz1+1,x==bx0||x==bx1?wall:rnd.nextInt(4)==0?s(Blocks.IRON_BARS):wall);
            f.set(x,6,bz1+1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
        }
        for(int x=bx0+1;x<=Math.min(bx1-1,bx0+2);x++){f.set(x,5,bz1,pane);f.set(x,6,bz1,pane);}
        Kit.airCon(f,bx1-1,5,bz1+1,Direction.WEST);
        if(rnd.nextInt(3)==0)futons(f,bx0+1,Math.min(bx1-2,bx0+3),5,bz1+2);
        Block door2=Pal.pick(rnd,Blocks.DARK_OAK_DOOR,Blocks.SPRUCE_DOOR,Blocks.BIRCH_DOOR,Blocks.MANGROVE_DOOR,Blocks.IRON_DOOR);
        Kit.entrance(f,door,bz0,door2,Kit.SLATE,name(),true);
        Kit.roof(f,bx0,bz0,bx1,bz1,4*stories,type,rnd.nextBoolean()||hw>=hd,roof,wall,floor);
        // rooms: white lights in most, warm in some
        for(int s=0;s<stories;s++)if(rnd.nextInt(4)>0)Kit.roomLight(f,(bx0+bx1)/2,4*s,(bz0+bz1)/2,rnd.nextInt(3)==0);
        for(int s=0;s<stories;s++){int fy=4*s;f.set(bx0+1,fy+1,bz1-1,Canvas.slab(Blocks.OAK_SLAB,true));if(s==1)f.bed(bx1-1,fy+1,bz0+1,Pal.pick(rnd,Blocks.WHITE_BED,Blocks.LIGHT_BLUE_BED,Blocks.GRAY_BED),Direction.SOUTH);}
        // around the house: an air conditioner, a water heater, plants by the door
        Kit.airCon(f,carLeft?bx0-1:bx1+1,1,bz1-1,carLeft?Direction.WEST:Direction.EAST);
        if(rnd.nextInt(3)==0){int hx=carLeft?bx0-1:bx1+1;if(f.air(hx,1,bz1-3)){f.set(hx,1,bz1-3,Blocks.SMOOTH_QUARTZ);f.set(hx,2,bz1-3,Blocks.SMOOTH_QUARTZ);}}
        if(rnd.nextBoolean())f.set(door+(carLeft?1:-1),1,bz0-1,Kit.pot(rnd));
        lotFence(f,-99,0);
        // garden at the back
        for(int x=1;x<W-1;x++)if(rnd.nextInt(5)==0&&D-2>bz1+1)f.set(x,1,D-2,Canvas.leaves(Blocks.OAK_LEAVES));
    }

    // ---------------------------------------------------------------- apartments

    /** アパート: a two- or three-storey block of small flats, an open corridor and stair to the street, balconies behind. */
    void apartment(Frame f){
        int W=f.W,D=f.D;
        int hw=W-3,hd=Math.min(D-5,8);
        if(hw<10||hd<6){modern(f,false);return;}
        int bx0=1,bx1=bx0+hw-1,bz0=3,bz1=bz0+hd-1;
        int stories=rnd.nextInt(3)==0?3:2;
        BlockState wall=Pal.pick(rnd,new BlockState[]{s(Blocks.WHITE_CONCRETE),s(Blocks.LIGHT_GRAY_CONCRETE),s(Blocks.SMOOTH_SANDSTONE),s(Blocks.CALCITE),s(Blocks.LIGHT_GRAY_TERRACOTTA)});
        BlockState trim=s(Blocks.SMOOTH_STONE),floor=s(Blocks.BIRCH_PLANKS);
        yard(f,false);
        f.fill(0,0,0,W-1,0,bz0-1,s(Blocks.SMOOTH_STONE));
        Kit.shell(f,bx0,bz0,bx1,bz1,stories,wall,trim,trim,floor,s(Blocks.SMOOTH_STONE));
        // flats: a door and a small frosted window each along the front, a dividing wall inside
        int units=Math.max(2,hw/4),uw=hw/units;
        BlockState frost=s(Blocks.WHITE_STAINED_GLASS_PANE),pane=s(Blocks.GLASS_PANE);
        for(int s=0;s<stories;s++){
            int fy=4*s;
            for(int u=0;u<units;u++){
                int ux=bx0+u*uw,dx=ux+1;
                if(dx>=bx1)break;
                f.door(dx,fy+1,bz0,Blocks.IRON_DOOR,Direction.NORTH,false);
                if(ux+3<bx1)f.set(ux+3,fy+2,bz0,frost);
                if(u>0)for(int z=bz0+1;z<bz1;z++)for(int y=fy+1;y<=fy+3;y++)f.set(ux,y,z,wall);
                // balcony glass doors and the balcony
                for(int x=ux+1;x<=Math.min(bx1-1,ux+uw-1);x++){f.set(x,fy+1,bz1,pane);f.set(x,fy+2,bz1,pane);}
                if(rnd.nextInt(5)>0)Kit.roomLight(f,ux+uw/2,fy,(bz0+bz1)/2,rnd.nextInt(3)==0);
                if(s>0){
                    // a balcony two blocks deep, railed, a partition between flats, the air conditioner on it
                    for(int x=ux;x<=Math.min(bx1,ux+uw);x++){
                        f.set(x,fy,bz1+1,s(Blocks.SMOOTH_STONE));f.set(x,fy,bz1+2,s(Blocks.SMOOTH_STONE));
                        f.set(x,fy+1,bz1+2,s(Blocks.IRON_BARS));
                    }
                    if(u>0){f.set(ux,fy+1,bz1+1,wall);f.set(ux,fy+2,bz1+1,wall);}
                    Kit.airCon(f,Math.min(bx1-1,ux+uw-1),fy+1,bz1+1,Direction.WEST);
                    if(rnd.nextInt(4)==0)futons(f,ux+1,Math.min(bx1-2,ux+uw-2),fy+1,bz1+3);
                }
            }
            // the open corridor along the front, with a railing
            if(s>0)for(int x=bx0;x<=bx1+2;x++){f.set(x,fy,bz0-1,s(Blocks.SMOOTH_STONE));f.set(x,fy+1,bz0-2,s(Blocks.IRON_BARS));if(x>bx1)f.set(x,fy+1,bz0-1,s(Blocks.AIR));}
            if(s>0)for(int x=bx0;x<=bx1+2;x++)f.set(x,fy,bz0-2,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));
        }
        // the outside stair at the right end, up to each corridor
        int sx=bx1+1;
        for(int s=1;s<stories;s++){
            int fy=4*s;
            for(int k=0;k<4;k++){
                int z=bz0+k,y=fy-3+k;
                if(z>=bz1)break;
                f.set(sx,y,z,Canvas.stairs(Blocks.POLISHED_ANDESITE_STAIRS,Direction.NORTH,false));
                f.set(sx+1,y,z,s(Blocks.IRON_BARS));
            }
        }
        // the roof, the name, mailboxes at the foot of the stair
        boolean flat=rnd.nextBoolean();
        Kit.roof(f,bx0,bz0,bx1,bz1,4*stories,flat?Kit.FLAT:Kit.GABLE,true,rnd.nextBoolean()?Kit.SLATE:Kit.BLACK,wall,floor);
        String name=APARTMENTS[rnd.nextInt(APARTMENTS.length)];
        f.wallSign(bx0+2,stories*4-1,bz0-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"",name);
        for(int x=bx0;x<bx0+Math.min(units,4);x++)f.set(x,2,bz0-1,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,true,false));
        lotFence(f,-99,0);
    }

    // ---------------------------------------------------------------- open plots

    /** 月極駐車場: an asphalt car park with painted bays, wheel stops, a few cars and the sign. */
    void parking(Frame f){
        int W=f.W,D=f.D;
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){f.set(x,0,z,Pal.asphalt(f.wx(x,z),f.wz(x,z)));f.clear(x,1,z,x,4,z);}
        boolean bays=W>=7;
        for(int x=0;x<W;x+=3){
            if(!bays)break;
            for(int z=D-6;z<D-1;z++)if(z>=0)f.set(x,0,z,s(Blocks.WHITE_CONCRETE));
            if(x+1<W)f.set(x+1,1,D-2,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
            if(x+1<W-1&&D>=7&&rnd.nextInt(3)>0)Kit.car(f,x+1,D-6,rnd);
        }
        f.set(0,1,0,s(Blocks.STONE_BRICK_WALL));f.set(0,2,0,s(Blocks.STONE_BRICK_WALL));
        f.wallSign(0,2,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLUE,false,"月極駐車場","空きあり","ボタンド不動産");
        for(int z=1;z<D;z++){f.set(0,1,z,Canvas.chain(Direction.Axis.Z));}
    }

    /** 畑: a vegetable plot in rows, its paths, a few stakes and a little shed. */
    void field(Frame f){
        int W=f.W,D=f.D;
        Block[] crops={Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,Blocks.BEETROOTS};
        for(int x=1;x<W-1;x++)for(int z=1;z<D-1;z++){
            f.clear(x,1,z,x,4,z);
            if((x&1)==0){f.set(x,0,z,s(Blocks.COARSE_DIRT));continue;}
            f.set(x,0,z,Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7));
            Block crop=crops[(x/2)%crops.length];
            BlockState cs=crop==Blocks.BEETROOTS?crop.getDefaultState().with(BeetrootsBlock.AGE,2+rnd.nextInt(2)):crop.getDefaultState().with(CropBlock.AGE,3+rnd.nextInt(5));
            f.set(x,1,z,cs);
        }
        for(int z=2;z<D-2;z+=3)if(rnd.nextBoolean())f.set(W-2,1,z,s(Blocks.BAMBOO));
        if(W>=7&&D>=6){f.fill(W-3,1,D-3,W-2,2,D-2,s(Blocks.SPRUCE_PLANKS));f.fill(W-4,3,D-4,W-1,3,D-1,Canvas.slab(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB,false));}
        Kit.fence(f,0,0,W-1,0,Blocks.SPRUCE_FENCE);
        f.set(1+rnd.nextInt(Math.max(1,W-2)),1,0,s(Blocks.AIR));
    }

    /** 庭: a garden with a big tree, shrubs, stepping stones and a bench. */
    void garden(Frame f){
        int W=f.W,D=f.D;
        yard(f,true);
        int tx=W/2,tz=D/2;
        if(rnd.nextInt(3)==0)t.nature.cherry(f.wx(tx,tz),f.y0+1,f.wz(tx,tz),5+rnd.nextInt(2));
        else t.nature.broadleaf(f.wx(tx,tz),f.y0+1,f.wz(tx,tz),5+rnd.nextInt(3));
        for(int i=0;i<6;i++){int x=1+rnd.nextInt(Math.max(1,W-2)),z=1+rnd.nextInt(Math.max(1,D-2));if(f.air(x,1,z))f.set(x,1,z,Canvas.leaves(rnd.nextBoolean()?Blocks.AZALEA_LEAVES:Blocks.FLOWERING_AZALEA_LEAVES));}
        f.set(1,1,1,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.SOUTH,false));
        Kit.hedge(f,0,0,W-1,0,1,Blocks.OAK_LEAVES);
        f.set(W/2,1,0,s(Blocks.AIR));
    }

    /** 空き地: an empty plot gone to grass and weeds, a little gravel, the estate agent's sign. */
    void vacant(Frame f){
        int W=f.W,D=f.D;
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            f.clear(x,1,z,x,4,z);
            f.set(x,0,z,rnd.nextInt(6)==0?s(Blocks.GRAVEL):rnd.nextInt(8)==0?s(Blocks.COARSE_DIRT):s(Blocks.GRASS_BLOCK));
            int r=rnd.nextInt(10);
            if(r<4)f.set(x,1,z,Blocks.GRASS);else if(r==4)f.tallPlant(x,1,z,Blocks.TALL_GRASS);else if(r==5)f.set(x,1,z,Blocks.DANDELION);
        }
        f.standingSign(1,1,1,Blocks.SPRUCE_SIGN,Direction.NORTH,DyeColor.RED,false,"売地","ボタンド不動産","☎ 0120-55-1234");
        Kit.fence(f,0,0,W-1,0,Blocks.IRON_BARS);
    }

    // ---------------------------------------------------------------- shared

    /** Levels the plot and clears it; gravel and stepping stones in front for old houses, grass otherwise. */
    void yard(Frame f,boolean gravel){
        for(int x=0;x<f.W;x++)for(int z=0;z<f.D;z++){
            f.clear(x,1,z,x,10,z);
            f.set(x,0,z,gravel&&z<3?s(Blocks.GRAVEL):s(Blocks.GRASS_BLOCK));
        }
    }
    interface Edge{void at(int x,int z,boolean corner);}
    static void perimeter(Frame f,int bx0,int bz0,int bx1,int bz1,Edge e){
        for(int x=bx0;x<=bx1;x++)for(int z=bz0;z<=bz1;z++){
            if(x!=bx0&&x!=bx1&&z!=bz0&&z!=bz1)continue;
            e.at(x,z,(x==bx0||x==bx1)&&(z==bz0||z==bz1));
        }
    }
    /** The plot's boundary: a block wall, a hedge or a block wall under a slat fence on the sides and back, open at the gate. */
    void lotFence(Frame f,int gate,int kind){
        int W=f.W,D=f.D;
        for(int z=0;z<D;z++)for(int x:new int[]{0,W-1})edgePiece(f,x,z,kind);
        for(int x=0;x<W;x++)edgePiece(f,x,D-1,kind);
        // the front: a low wall either side of the gate, the drive left open
        if(gate>=0)for(int x=0;x<W;x++)if(Math.abs(x-gate)>1&&f.air(x,1,0)&&f.get(x,0,0).getBlock()!=Blocks.SMOOTH_STONE)edgePiece(f,x,0,kind);
    }
    private void edgePiece(Frame f,int x,int z,int kind){
        if(!f.air(x,1,z)&&!(f.get(x,1,z).getBlock() instanceof PlantBlock))return;
        switch(kind){
            case 1->f.set(x,1,z,Canvas.leaves(Blocks.OAK_LEAVES));
            case 2->{f.set(x,1,z,TB.BLOCK_WALL);if(f.air(x,2,z))f.set(x,2,z,TB.SLAT_FENCE);}
            default->{f.set(x,1,z,TB.BLOCK_WALL);if(f.air(x,2,z))f.set(x,2,z,TB.wallCap());}
        }
    }

    /** Bedding put out to air over a balcony's rail: one or two futons hanging down the outside from (x, y, z) on. */
    private void futons(Frame f,int x0,int x1,int y,int z){
        Block[] cloth={Blocks.WHITE_WALL_BANNER,Blocks.WHITE_WALL_BANNER,Blocks.LIGHT_BLUE_WALL_BANNER,Blocks.PINK_WALL_BANNER,Blocks.YELLOW_WALL_BANNER,Blocks.LIGHT_GRAY_WALL_BANNER};
        int n=1+rnd.nextInt(2);
        for(int k=0;k<n&&x0+k<=x1;k++){
            int x=x0+k;
            if(f.air(x,y,z)&&f.air(x,y-1,z))f.set(x,y,z,s(cloth[rnd.nextInt(cloth.length)]).with(Properties.HORIZONTAL_FACING,Direction.SOUTH));
        }
    }
}
