package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.town.TownBlocks;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The rift's cracks of light (2.49.0): each side of a crack block joins the neighbour on that side, no other. */
class CrackTest {
    @Test void eachSideIsItsOwnProperty(){
        for(var d:Direction.Type.HORIZONTAL)assertEquals(d.asString(),TownBlocks.Crack.side(d).getName());
    }
}
