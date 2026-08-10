package nm63ae2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

/**
 * Loader-agnostic TOML config for the item-type limit.
 * Loaded once at mod startup from {@code config/no_more_63_ae2.toml}.
 */
public final class NoMore63AE2Config {

    public static final int MIN_SAFE_VALUE = 63;
    public static final int MAX_SAFE_VALUE = 65535;
    public static final int DEFAULT_VALUE = 4096;

    private static final String KEY = "maxItemTypesPerCell";

    private static volatile int cachedValue = DEFAULT_VALUE;
    private static volatile boolean loaded;

    private NoMore63AE2Config() {
    }

    /**
     * Loads or creates the config file at the given path.
     *
     * @param configFile absolute path to {@code no_more_63_ae2.toml}
     */
    public static void load(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            if (!Files.exists(configFile)) {
                writeDefault(configFile);
            }

            try (CommentedFileConfig config = CommentedFileConfig.builder(configFile)
                    .autosave()
                    .sync()
                    .build()) {
                config.load();
                ensureDefaults(config);
                cachedValue = clamp(config.getInt(KEY));
                config.save();
                loaded = true;
            }
        } catch (IOException ex) {
            Constants.LOG.error("[{}] Failed to load config from {}", Constants.MOD_ID, configFile, ex);
            cachedValue = DEFAULT_VALUE;
            loaded = true;
        }
    }

    public static int getMaxItemTypesPerCell() {
        return clamp(cachedValue);
    }

    public static boolean isLoaded() {
        return loaded;
    }

    private static void writeDefault(Path configFile) throws IOException {
        String content = """
                # No More 63 AE2 config
                # Controls how many unique item types an AE2 item storage cell can hold.
                # AE2 default is 63.
                maxItemTypesPerCell = %d
                """.formatted(DEFAULT_VALUE);
        Files.writeString(configFile, content);
    }

    private static void ensureDefaults(CommentedFileConfig config) {
        if (!config.contains(KEY)) {
            config.set(KEY, DEFAULT_VALUE);
        }
        config.setComment(KEY,
                "No More 63 AE2 config\n"
                        + "Controls how many unique item types an AE2 item storage cell can hold.\n"
                        + "AE2 default is 63.");
        config.set(KEY, clamp(config.getInt(KEY)));
    }

    private static int clamp(int value) {
        if (value < MIN_SAFE_VALUE) {
            return MIN_SAFE_VALUE;
        }
        if (value > MAX_SAFE_VALUE) {
            return MAX_SAFE_VALUE;
        }
        return value;
    }
}
