/*
 * No More 63 AE2
 *
 * Configuration container for the addon.
 *
 * Defines a single knob: how many unique item types an ME item storage cell
 * may hold. Replaces the hardcoded AE2 default of 63 with a configurable
 * value, clamped to a safe range to avoid pathological or crashing values.
 */
package nm63ae2;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Holds the {@link ModConfigSpec} for the addon and exposes typed accessors.
 *
 * <p>The configured value is defensively clamped on every read, so the mixin
 * (and any other consumer) can never observe a value outside the safe range
 * &mdash; even if a malicious or broken config file bypasses the spec bounds.
 */
public final class NoMore63AE2Config {

    /** Absolute minimum that BasicCellInventory will tolerate (AE2's hardcoded floor). */
    public static final int MIN_SAFE_VALUE = 63;

    /**
     * Absolute maximum. 65535 fits comfortably in an unsigned 16-bit field and is
     * well below {@link Integer#MAX_VALUE} so the equal-distribution card math
     * (which divides total bytes by maxTypes) cannot overflow.
     */
    public static final int MAX_SAFE_VALUE = 65535;

    /** Default if the config entry is missing/invalid. */
    public static final int DEFAULT_VALUE = 4096;

    private final ModConfigSpec spec;
    private final ModConfigSpec.IntValue maxItemTypesPerCell;

    public NoMore63AE2Config() {
        var b = new ModConfigSpec.Builder();

        maxItemTypesPerCell = b.comment(
                "No More 63 AE2 config",
                "Controls how many unique item types an AE2 item storage cell can hold.",
                "AE2 default is 63. This addon raises the cap to the value below.",
                "Values are clamped to the safe range " + MIN_SAFE_VALUE + ".." + MAX_SAFE_VALUE + ".")
                .defineInRange("maxItemTypesPerCell", DEFAULT_VALUE, MIN_SAFE_VALUE, MAX_SAFE_VALUE);

        spec = b.build();
    }

    public ModConfigSpec spec() {
        return spec;
    }

    /** Raw configured value (already range-checked by ModConfigSpec). */
    public int maxItemTypesPerCell() {
        return maxItemTypesPerCell.get();
    }

    /**
     * Defensively-clamped accessor for the mixin and any other consumer.
     * Returns a value in {@code [MIN_SAFE_VALUE, MAX_SAFE_VALUE]} even if the
     * spec is not yet loaded.
     */
    public static int getMaxItemTypesPerCell() {
        if (NoMore63AE2.config() == null) {
            return DEFAULT_VALUE;
        }
        int raw;
        try {
            raw = NoMore63AE2.config().maxItemTypesPerCell();
        } catch (IllegalStateException ex) {
            return DEFAULT_VALUE;
        }
        if (raw < MIN_SAFE_VALUE) return MIN_SAFE_VALUE;
        if (raw > MAX_SAFE_VALUE) return MAX_SAFE_VALUE;
        return raw;
    }
}
