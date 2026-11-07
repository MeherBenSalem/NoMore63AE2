package dev.nightbeam.corerpg.quests.book;

import net.minecraft.util.StringRepresentable;

/** What happens when a player clicks a chapter image. */
public enum ClickAction implements StringRepresentable {
    NONE("none"),
    OPEN_URL("open_url"),
    OPEN_QUEST("open_quest"),
    OPEN_CHAPTER("open_chapter"),
    RUN_COMMAND("run_command");

    private final String name;

    ClickAction(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
