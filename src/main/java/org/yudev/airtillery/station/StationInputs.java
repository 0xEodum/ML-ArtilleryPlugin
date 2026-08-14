package org.yudev.airtillery.station;

import java.util.Locale;

/**
 * Parsing and validation of the renamed papers the station takes as input.
 *
 * <p>Every parser returns a {@link Parsed} carrying either a value or a message
 * meant to be shown to the player, because "this paper is wrong" is an ordinary
 * outcome of someone typing into an anvil, not an exceptional one.
 */
public final class StationInputs {

    /** Hard ceiling on packs per volley, independent of the configured one. */
    public static final int ABSOLUTE_MAX_PACKS = 512;

    private StationInputs() {
    }

    /** A validated value, or the reason it was rejected. */
    public static final class Parsed<T> {
        private final T value;
        private final String error;

        private Parsed(T value, String error) {
            this.value = value;
            this.error = error;
        }

        static <T> Parsed<T> of(T value) {
            return new Parsed<>(value, null);
        }

        static <T> Parsed<T> error(String error) {
            return new Parsed<>(null, error);
        }

        public boolean isValid() {
            return error == null;
        }

        public T get() {
            return value;
        }

        public String getError() {
            return error;
        }
    }

    /** Target coordinates, as written on a paper: {@code X Y Z}. */
    public static final class Coordinates {
        private final double x;
        private final double y;
        private final double z;

        Coordinates(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
        }
    }

    /**
     * {@code "X Y Z"} — three numbers separated by spaces. Commas are accepted
     * as decimal separators because an anvil is an awkward place to be fussy.
     */
    public static Parsed<Coordinates> parseCoordinates(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Parsed.error("пустое имя, нужен формат X Y Z");
        }
        String[] parts = text.trim().replace(',', '.').split("\\s+");
        if (parts.length != 3) {
            return Parsed.error("нужно три числа в формате X Y Z, найдено " + parts.length);
        }
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            try {
                values[i] = Double.parseDouble(parts[i]);
            } catch (NumberFormatException e) {
                return Parsed.error("'" + parts[i] + "' не число");
            }
            if (!Double.isFinite(values[i])) {
                return Parsed.error("координата " + parts[i] + " не конечна");
            }
            if (Math.abs(values[i]) > 30_000_000.0) {
                return Parsed.error("координата " + parts[i] + " за границей мира");
            }
        }
        return Parsed.of(new Coordinates(values[0], values[1], values[2]));
    }

    /**
     * {@code "α"} — the descent angle in degrees, strictly between 0 and 90.
     *
     * <p>90 is excluded because a perfectly vertical descent needs infinite
     * elevation, and 0 because a projectile is never travelling level when it
     * arrives. The achievable set is narrower still and depends on the target;
     * the solver reports the real bounds when it cannot meet a request.
     */
    public static Parsed<Double> parseAngle(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Parsed.error("пустое имя, нужно одно число - угол падения");
        }
        String value = text.trim().replace(',', '.');
        double degrees;
        try {
            degrees = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Parsed.error("'" + value + "' не число");
        }
        if (!Double.isFinite(degrees)) {
            return Parsed.error("угол не конечен");
        }
        if (degrees <= 0.0) {
            return Parsed.error("угол падения должен быть больше 0°");
        }
        if (degrees >= 90.0) {
            return Parsed.error("угол падения должен быть меньше 90°");
        }
        return Parsed.of(degrees);
    }

    /** {@code "N"} — how many packs to fire. */
    public static Parsed<Integer> parsePacks(String text, int maxPacks) {
        if (text == null || text.trim().isEmpty()) {
            return Parsed.error("пустое имя, нужно одно целое число - число пакетов");
        }
        String value = text.trim();
        int packs;
        try {
            packs = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Parsed.error("'" + value + "' не целое число");
        }
        if (packs < 1) {
            return Parsed.error("число пакетов должно быть не меньше 1");
        }
        int ceiling = Math.min(maxPacks, ABSOLUTE_MAX_PACKS);
        if (packs > ceiling) {
            return Parsed.error("не больше " + ceiling + " пакетов за залп");
        }
        return Parsed.of(packs);
    }
}
