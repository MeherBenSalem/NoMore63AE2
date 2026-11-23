package dev.nightbeam.corerpg.quests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nightbeam.corerpg.quests.CoreQuests;
import net.minecraft.client.KeyMapping;

import java.util.List;

/** Key mappings; each loader registers {@link #ALL}. */
public final class KeyBinds {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CoreQuests.id("main"));
    public static final KeyMapping OPEN_BOOK = new KeyMapping("key.corerpg_quests.open_book", InputConstants.KEY_J, CATEGORY);
    public static final KeyMapping TEAM = new KeyMapping("key.corerpg_quests.team", -1, CATEGORY);
    public static final KeyMapping VEIN_MINE = new KeyMapping("key.corerpg_quests.vein_mine", InputConstants.KEY_GRAVE, CATEGORY);
    public static final KeyMapping CYCLE_TRACKER = new KeyMapping("key.corerpg_quests.cycle_tracker", -1, CATEGORY);

    public static final List<KeyMapping> ALL = List.of(OPEN_BOOK, TEAM, VEIN_MINE, CYCLE_TRACKER);

    private KeyBinds() {
    }
}
