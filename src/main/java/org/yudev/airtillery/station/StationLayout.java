package org.yudev.airtillery.station;

/**
 * Slot map of the station window.
 *
 * <p>Six rows of nine, the size of a double chest. Every slot a player can put
 * something into has its label in the slot directly above it — the label slot
 * is always {@code input - 9} — and no two interactive slots touch, so a
 * mis-aimed click lands on inert glass rather than on the wrong control.
 *
 * <pre>
 *   row 0    .  .  .  .  T  .  .  .  .      T  ammunition selector
 *   row 1    .  .  L  .  L  .  L  .  .      L  label
 *   row 2    .  .  C  .  A  .  N  .  .      C  coordinates   "X Y Z"
 *   row 3    .  .  .  .  L  .  .  .  .      A  impact angle  "α"
 *   row 4    .  .  .  .  $  .  .  .  .      N  packet count  "N"
 *   row 5    .  .  .  F  .  R  .  .  .      $  payment
 *                                           F  fire    R  reset
 * </pre>
 */
public final class StationLayout {

    public static final int SIZE = 54;

    public static final int SELECTOR = 4;

    public static final int INPUT_COORDS = 20;
    public static final int INPUT_ANGLE = 22;
    public static final int INPUT_PACKS = 24;
    public static final int INPUT_PAYMENT = 40;

    public static final int LABEL_COORDS = INPUT_COORDS - 9;
    public static final int LABEL_ANGLE = INPUT_ANGLE - 9;
    public static final int LABEL_PACKS = INPUT_PACKS - 9;
    public static final int LABEL_PAYMENT = INPUT_PAYMENT - 9;

    public static final int BUTTON_FIRE = 48;
    public static final int BUTTON_RESET = 50;

    /** What a slot does when clicked. */
    public enum Role {
        FILLER,
        LABEL,
        SELECTOR,
        INPUT_COORDS,
        INPUT_ANGLE,
        INPUT_PACKS,
        PAYMENT,
        FIRE,
        RESET
    }

    private StationLayout() {
    }

    public static Role roleOf(int slot) {
        switch (slot) {
            case SELECTOR:
                return Role.SELECTOR;
            case INPUT_COORDS:
                return Role.INPUT_COORDS;
            case INPUT_ANGLE:
                return Role.INPUT_ANGLE;
            case INPUT_PACKS:
                return Role.INPUT_PACKS;
            case INPUT_PAYMENT:
                return Role.PAYMENT;
            case BUTTON_FIRE:
                return Role.FIRE;
            case BUTTON_RESET:
                return Role.RESET;
            case LABEL_COORDS:
            case LABEL_ANGLE:
            case LABEL_PACKS:
            case LABEL_PAYMENT:
                return Role.LABEL;
            default:
                return Role.FILLER;
        }
    }

    /** True for the three slots that accept a renamed paper. */
    public static boolean isPaperInput(Role role) {
        return role == Role.INPUT_COORDS
                || role == Role.INPUT_ANGLE
                || role == Role.INPUT_PACKS;
    }
}
