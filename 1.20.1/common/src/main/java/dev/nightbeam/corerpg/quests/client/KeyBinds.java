package dev.nightbeam.corerpg.quests.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Key mappings; each loader registers {@link #ALL}. */
public final class KeyBinds {
    public static final String CATEGORY = "key.categories.corerpg_quests";
    public static final KeyMapping OPEN_BOOK = new KeyMapping("key.corerpg_quests.open_book", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping TEAM = new KeyMapping("key.corerpg_quests.team", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping VEIN_MINE = new KeyMapping("key.corerpg_quests.vein_mine", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);
    public static final KeyMapping CYCLE_TRACKER = new KeyMapping("key.corerpg_quests.cycle_tracker", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    public static final List<KeyMapping> ALL = List.of(OPEN_BOOK, TEAM, VEIN_MINE, CYCLE_TRACKER);

    private KeyBinds() {
    }
}
