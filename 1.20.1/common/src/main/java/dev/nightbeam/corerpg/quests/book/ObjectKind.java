package dev.nightbeam.corerpg.quests.book;

import net.minecraft.util.StringRepresentable;

public enum ObjectKind implements StringRepresentable {
    BOOK("book"),
    GROUP("group"),
    CHAPTER("chapter"),
    QUEST("quest"),
    LINK("link"),
    IMAGE("image"),
    TASK("task"),
    REWARD("reward"),
    TABLE("table");

    private final String name;

    ObjectKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static ObjectKind byName(String name) {
        for (ObjectKind kind : values()) {
            if (kind.name.equals(name)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown object kind " + name);
    }
}
