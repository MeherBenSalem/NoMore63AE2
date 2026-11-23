package dev.nightbeam.corerpg.quests.config;

import dev.nightbeam.corerpg.quests.platform.Services;

import java.nio.file.Path;
import java.util.List;

/** Server and client settings, in config/corerpg_quests/. */
public final class CoreConfig {
    public static final Server SERVER = new Server();
    public static final Client CLIENT = new Client();

    private CoreConfig() {
    }

    public static Path dir() {
        return Services.PLATFORM.configDir().resolve("corerpg_quests");
    }

    public static final class Server {
        public final ConfigSpec spec = new ConfigSpec("server.toml", "Core RPG - Quests Engine: server settings.\nThe quest book itself lives in config/corerpg_quests/book/.");

        public final ConfigSpec.Value<Boolean> partiesEnabled = spec.bool("teams", "parties_enabled", true, "Allow players to create and join parties.");
        public final ConfigSpec.Value<Integer> maxPartySize = spec.integer("teams", "max_party_size", 0, 0, 1000, "Maximum members in a party; 0 means no limit.");
        public final ConfigSpec.Value<Integer> editorPermissionLevel = spec.integer("quests", "editor_permission_level", 2, 0, 4, "Permission level needed to enter quest editing mode.");
        public final ConfigSpec.Value<Boolean> logCompletions = spec.bool("quests", "log_completions", false, "Write every quest completion to the server log.");

        public final ConfigSpec.Value<Boolean> veinEnabled = spec.bool("vein_mining", "enabled", true, "Enable vein mining (hold the vein mining key while breaking a block).");
        public final ConfigSpec.Value<Integer> veinMaxBlocks = spec.integer("vein_mining", "max_blocks", 64, 1, 8192, "Most blocks broken by one vein mine.");
        public final ConfigSpec.Value<Double> veinExhaustionPerBlock = spec.decimal("vein_mining", "exhaustion_per_block", 0.025, 0, 10, "Hunger exhaustion added for each extra block (vanilla adds 0.005 per block).");
        public final ConfigSpec.Value<Integer> veinXpPerBlock = spec.integer("vein_mining", "xp_points_per_block", 0, 0, 1000, "Experience points spent for each extra block.");
        public final ConfigSpec.Value<Boolean> veinRequireTool = spec.bool("vein_mining", "require_tool", false, "Only vein mine while holding a tool (an item that takes damage).");
        public final ConfigSpec.Value<Boolean> veinRequireCorrectTool = spec.bool("vein_mining", "require_correct_tool", true, "Only vein mine blocks the held item can harvest (blocks that need a pickaxe need one).");
        public final ConfigSpec.Value<Integer> veinCooldownTicks = spec.integer("vein_mining", "cooldown_ticks", 0, 0, 72000, "Ticks to wait between vein mines.");
        public final ConfigSpec.Value<Boolean> veinPreventToolBreak = spec.bool("vein_mining", "prevent_tool_break", true, "Stop before the held tool would break.");
        public final ConfigSpec.Value<Boolean> veinGatherDrops = spec.bool("vein_mining", "gather_drops", true, "Drop everything at the first block instead of scattering it.");
        public final ConfigSpec.Value<List<String>> veinMergeTags = spec.stringList("vein_mining", "merge_tags",
                List.of("c:ores/*", "minecraft:logs", "minecraft:base_stone_overworld", "minecraft:base_stone_nether", "c:cobblestones/*"),
                "Blocks that share one of these block tags count as the same block when vein mining.\nA trailing /* matches every sub-tag (c:ores/* puts iron and deepslate iron ore together).");
        public final ConfigSpec.Value<Boolean> veinRightClickHarvest = spec.bool("vein_mining", "right_click_harvest", true, "With the key held, right-clicking a ripe crop harvests and replants the connected field.");
        public final ConfigSpec.Value<Boolean> veinSingleCropHarvest = spec.bool("vein_mining", "single_crop_harvest", false, "Right-clicking a ripe crop (without the key) harvests and replants it.");
        public final ConfigSpec.Value<Boolean> veinRightClickTools = spec.bool("vein_mining", "right_click_tools", true, "With the key held, axes strip, shovels flatten and hoes till the connected area.");
    }

    public static final class Client {
        public final ConfigSpec spec = new ConfigSpec("client.toml", "Core RPG - Quests Engine: client settings.");

        public final ConfigSpec.Value<Boolean> trackerEnabled = spec.bool("hud", "tracker_enabled", true, "Show pinned quests on screen.");
        public final ConfigSpec.Value<Corner> trackerCorner = spec.enumValue("hud", "tracker_corner", Corner.TOP_LEFT, "Screen corner for the quest tracker.");
        public final ConfigSpec.Value<Double> trackerScale = spec.decimal("hud", "tracker_scale", 1.0, 0.5, 2.0, "Size of the quest tracker.");
        public final ConfigSpec.Value<Integer> trackerMaxQuests = spec.integer("hud", "tracker_max_quests", 4, 1, 12, "Most pinned quests shown at once.");
        public final ConfigSpec.Value<Boolean> autoPin = spec.bool("hud", "auto_pin", false, "Pin quests automatically when they start.");
        public final ConfigSpec.Value<Boolean> toasts = spec.bool("notifications", "toasts", true, "Show toasts for completed quests, chapters and received rewards.");
        public final ConfigSpec.Value<Boolean> sounds = spec.bool("notifications", "sounds", true, "Play sounds on quest completion.");
        public final ConfigSpec.Value<Boolean> veinPreview = spec.bool("vein_mining", "preview", true, "Outline the blocks a vein mine would break.");
        public final ConfigSpec.Value<Double> veinPreviewAlpha = spec.decimal("vein_mining", "preview_alpha", 0.75, 0.05, 1.0, "Opacity of the outline.");
        public final ConfigSpec.Value<Boolean> veinOverlay = spec.bool("vein_mining", "overlay", true, "Show the vein mining shape and block count next to the crosshair.");
        public final ConfigSpec.Value<Boolean> fancyFonts = spec.bool("quest_book", "fancy_fonts", true, "Use the bundled Cinzel serif for titles; false keeps the Minecraft font everywhere.");
        public final ConfigSpec.Value<Boolean> rememberChapter = spec.bool("quest_book", "remember_chapter", true, "Reopen the quest book at the last chapter viewed.");
        public final ConfigSpec.Value<Boolean> showCompletedChapters = spec.bool("quest_book", "show_completed_chapters", true, "List completed chapters in the chapter list.");
    }

    public enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public static void loadServer() {
        SERVER.spec.load(dir());
    }

    public static void loadClient() {
        CLIENT.spec.load(dir());
    }
}
