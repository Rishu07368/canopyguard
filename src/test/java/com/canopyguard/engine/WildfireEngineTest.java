package com.canopyguard.engine;

import com.canopyguard.domain.FireCell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WildfireEngineTest {
    private WildfireEngine engine(double windDegrees) {
        return new WildfireEngine(new WildfireEngine.Settings(30, 30, 412L, windDegrees, 36, 20, 1.8), 15, 15);
    }

    @Test void fireAdvancesAndBurnedCellsDoNotReignite() {
        WildfireEngine engine = engine(90);
        engine.advance();
        WildfireEngine.Snapshot next = engine.advance();
        assertTrue(next.burnedCount() > 0);
        assertTrue(next.burningCount() > 0);
        assertTrue(next.cells().stream().anyMatch(cell -> cell.state() == FireCell.FireState.BURNING));
        long burned = next.burnedCount();
        WildfireEngine.Snapshot later = engine.advance();
        assertTrue(later.burnedCount() >= burned);
    }

    @Test void zeroFuelCellsCannotIgnite() {
        WildfireEngine a = engine(0), b = engine(180);
        WildfireEngine.Snapshot one = a.advance(), two = b.advance();
        for (int i = 0; i < one.cells().size(); i++) {
            if (one.cells().get(i).fuel() == 0) assertEquals(FireCell.FireState.UNBURNED, one.cells().get(i).state());
            assertEquals(one.cells().get(i).fuel(), two.cells().get(i).fuel());
        }
    }

    @Test void fixedSeedAndSettingsProduceRepeatableCells() {
        WildfireEngine first = engine(45), second = engine(45);
        for (int i = 0; i < 6; i++) {
            WildfireEngine.Snapshot a = first.advance(), b = second.advance();
            assertEquals(a.cells(), b.cells());
        }
    }

    @Test void windDirectionChangesSmokeAndPropagation() {
        WildfireEngine east = engine(90), west = engine(270);
        WildfireEngine.Snapshot eastSmoke = east.advance(), westSmoke = west.advance();
        assertNotEquals(eastSmoke.cells().stream().map(FireCell::smoke).toList(),
                westSmoke.cells().stream().map(FireCell::smoke).toList());
        for (int i = 0; i < 6; i++) { eastSmoke = east.advance(); westSmoke = west.advance(); }
        assertNotEquals(eastSmoke.cells().stream().map(FireCell::state).toList(),
                westSmoke.cells().stream().map(FireCell::state).toList());
    }

    @Test void windCanChangeDuringScenario() {
        WildfireEngine engine = engine(90);
        engine.changeWind(270, 20);
        assertEquals(270, engine.advance().windDegrees());
    }
}
