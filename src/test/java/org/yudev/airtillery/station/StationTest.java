package org.yudev.airtillery.station;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the station that are plain logic: input validation, the slot
 * map, and pricing. None of this needs a running server.
 */
class StationTest {

    // ------------------------------------------------------------------
    // Slot map
    // ------------------------------------------------------------------

    @Test
    @DisplayName("every input has its label in the slot directly above")
    void labelsSitAboveInputs() {
        assertEquals(StationLayout.INPUT_COORDS - 9, StationLayout.LABEL_COORDS);
        assertEquals(StationLayout.INPUT_ANGLE - 9, StationLayout.LABEL_ANGLE);
        assertEquals(StationLayout.INPUT_PACKS - 9, StationLayout.LABEL_PACKS);
        assertEquals(StationLayout.INPUT_PAYMENT - 9, StationLayout.LABEL_PAYMENT);

        for (int label : new int[]{StationLayout.LABEL_COORDS, StationLayout.LABEL_ANGLE,
                StationLayout.LABEL_PACKS, StationLayout.LABEL_PAYMENT}) {
            assertTrue(label >= 0, "label slot fell off the top of the window");
            assertEquals(StationLayout.Role.LABEL, StationLayout.roleOf(label));
        }
    }

    @Test
    @DisplayName("no two interactive slots are adjacent")
    void interactiveSlotsAreSeparated() {
        Set<Integer> interactive = new HashSet<>();
        for (int slot = 0; slot < StationLayout.SIZE; slot++) {
            StationLayout.Role role = StationLayout.roleOf(slot);
            if (role != StationLayout.Role.FILLER && role != StationLayout.Role.LABEL) {
                interactive.add(slot);
            }
        }
        assertFalse(interactive.isEmpty());
        for (int slot : interactive) {
            // Horizontal neighbours, without wrapping across a row boundary.
            if (slot % 9 != 8) {
                assertFalse(interactive.contains(slot + 1),
                        "slots " + slot + " and " + (slot + 1) + " touch");
            }
            if (slot % 9 != 0) {
                assertFalse(interactive.contains(slot - 1),
                        "slots " + (slot - 1) + " and " + slot + " touch");
            }
        }
    }

    @Test
    @DisplayName("all slots are inside a double-chest window and roles are unique")
    void slotsAreInRangeAndDistinct() {
        int[] slots = {StationLayout.SELECTOR, StationLayout.INPUT_COORDS,
                StationLayout.INPUT_ANGLE, StationLayout.INPUT_PACKS,
                StationLayout.INPUT_PAYMENT, StationLayout.BUTTON_FIRE,
                StationLayout.BUTTON_RESET, StationLayout.LABEL_COORDS,
                StationLayout.LABEL_ANGLE, StationLayout.LABEL_PACKS,
                StationLayout.LABEL_PAYMENT};
        Set<Integer> seen = new HashSet<>();
        for (int slot : slots) {
            assertTrue(slot >= 0 && slot < StationLayout.SIZE, "slot " + slot + " out of range");
            assertTrue(seen.add(slot), "slot " + slot + " is used twice");
        }
        assertEquals(54, StationLayout.SIZE);
    }

    // ------------------------------------------------------------------
    // Coordinates
    // ------------------------------------------------------------------

    @Test
    @DisplayName("coordinate papers accept X Y Z and reject the rest")
    void coordinateParsing() {
        StationInputs.Parsed<StationInputs.Coordinates> ok =
                StationInputs.parseCoordinates("120 64 -350");
        assertTrue(ok.isValid());
        assertEquals(120.0, ok.get().getX());
        assertEquals(64.0, ok.get().getY());
        assertEquals(-350.0, ok.get().getZ());

        // Extra whitespace and comma decimals are tolerated: an anvil is an
        // awkward place to be exact.
        StationInputs.Parsed<StationInputs.Coordinates> messy =
                StationInputs.parseCoordinates("  10,5   70    -8  ");
        assertTrue(messy.isValid());
        assertEquals(10.5, messy.get().getX());

        for (String bad : new String[]{"", "   ", "1 2", "1 2 3 4", "a b c",
                "1 2 three", "NaN 0 0", "40000000 0 0"}) {
            StationInputs.Parsed<StationInputs.Coordinates> parsed =
                    StationInputs.parseCoordinates(bad);
            assertFalse(parsed.isValid(), "should have rejected '" + bad + "'");
            assertNotNull(parsed.getError());
            assertNull(parsed.get());
        }
        assertFalse(StationInputs.parseCoordinates(null).isValid());
    }

    // ------------------------------------------------------------------
    // Impact angle
    // ------------------------------------------------------------------

    @Test
    @DisplayName("impact angle must be strictly inside 0 to 90 degrees")
    void angleParsing() {
        assertEquals(75.0, StationInputs.parseAngle("75").get());
        assertEquals(0.5, StationInputs.parseAngle("0.5").get());
        assertEquals(89.9, StationInputs.parseAngle("89,9").get());

        // 90 needs infinite elevation and 0 never happens to a falling
        // projectile, so both ends are excluded rather than clamped.
        for (String bad : new String[]{"90", "90.1", "0", "-10", "180", "", "steep", null}) {
            assertFalse(StationInputs.parseAngle(bad).isValid(),
                    "should have rejected '" + bad + "'");
        }
    }

    // ------------------------------------------------------------------
    // Packet count
    // ------------------------------------------------------------------

    @Test
    @DisplayName("packet count is a positive integer under the configured ceiling")
    void packsParsing() {
        assertEquals(10, StationInputs.parsePacks("10", 64).get());
        assertEquals(1, StationInputs.parsePacks("1", 64).get());
        assertEquals(64, StationInputs.parsePacks("64", 64).get());

        for (String bad : new String[]{"0", "-3", "1.5", "", "many", null}) {
            assertFalse(StationInputs.parsePacks(bad, 64).isValid(),
                    "should have rejected '" + bad + "'");
        }
        assertFalse(StationInputs.parsePacks("65", 64).isValid(), "over the configured ceiling");

        // The absolute ceiling wins even if the config asks for more, so a
        // mis-edited config cannot order a hundred thousand entities.
        assertFalse(StationInputs.parsePacks(
                        String.valueOf(StationInputs.ABSOLUTE_MAX_PACKS + 1), 1_000_000)
                .isValid());
        assertTrue(StationInputs.parsePacks(
                        String.valueOf(StationInputs.ABSOLUTE_MAX_PACKS), 1_000_000)
                .isValid());
    }

    // ------------------------------------------------------------------
    // Ammunition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pack arithmetic matches the advertised price")
    void pricing() {
        assertEquals(100, ProjectileKind.ARROW.totalRounds(10));
        assertEquals(10, ProjectileKind.ARROW.totalPrice(10));

        assertEquals(25, ProjectileKind.TNT.totalRounds(5));
        assertEquals(5, ProjectileKind.TNT.totalPrice(5));

        assertEquals(3, ProjectileKind.TRIDENT.totalRounds(3));
        assertEquals(3, ProjectileKind.TRIDENT.totalPrice(3));

        for (ProjectileKind kind : ProjectileKind.values()) {
            assertTrue(kind.getPackSize() >= 1, kind + " must fire at least one round per pack");
            assertTrue(kind.getPricePerPack() >= 1, kind + " must cost something");
            assertNotNull(kind.getCurrency());
            assertNotNull(kind.getBallisticsType());
            assertTrue(kind.describePrice().contains(kind.getCurrencyName()));
        }
    }

    @Test
    @DisplayName("the selector cycles through every kind and returns")
    void selectorCycles() {
        ProjectileKind start = ProjectileKind.ARROW;
        Set<ProjectileKind> seen = new HashSet<>();
        ProjectileKind current = start;
        for (int i = 0; i < ProjectileKind.values().length; i++) {
            assertTrue(seen.add(current), "cycle repeated early at " + current);
            current = current.next();
        }
        assertSame(start, current, "cycle should come back round");
        assertEquals(ProjectileKind.values().length, seen.size());
    }

    @Test
    @DisplayName("unknown ammunition names fall back rather than throwing")
    void kindLookupIsForgiving() {
        assertSame(ProjectileKind.TNT, ProjectileKind.byName("TNT"));
        assertSame(ProjectileKind.ARROW, ProjectileKind.byName("SOMETHING_ELSE"));
        assertSame(ProjectileKind.ARROW, ProjectileKind.byName(null));
    }
}
