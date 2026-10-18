package dev.sangwoo.celestialfall.loot;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** The eleven orange field markers in the delivered one-third city; excludes the arena supplies chest. */
public final class LootPositions {
    public static final List<BlockPos> ALL=List.of(
        new BlockPos(-50,81,-123),new BlockPos(-121,81,-90),new BlockPos(-20,81,-98),
        new BlockPos(56,81,-98),new BlockPos(-126,81,-15),new BlockPos(-90,81,-14),
        new BlockPos(-55,81,15),new BlockPos(17,81,-7),new BlockPos(81,81,-14),
        new BlockPos(98,81,95),new BlockPos(-42,81,106));
    public static boolean valid(int slot){return slot>=0&&slot<ALL.size();}
    public static UUID id(int slot){if(!valid(slot))throw new IllegalArgumentException("Unknown field slot");return UUID.nameUUIDFromBytes(("botand:field-loot:"+slot).getBytes(StandardCharsets.UTF_8));}
    private LootPositions(){}
}
