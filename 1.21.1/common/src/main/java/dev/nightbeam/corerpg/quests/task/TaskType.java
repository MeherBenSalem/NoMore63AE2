package dev.nightbeam.corerpg.quests.task;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.Icon;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/** A registered kind of task. Addons register their own through {@link TaskTypes#register}. */
public final class TaskType<T extends Task> {
    private final ResourceLocation id;
    private final Supplier<T> factory;
    private final Supplier<Icon> icon;
    private Icon cachedIcon;

    public TaskType(ResourceLocation id, Supplier<T> factory, Supplier<Icon> icon) {
        this.id = id;
        this.factory = factory;
        this.icon = icon;
    }

    public ResourceLocation id() {
        return id;
    }

    /** "item" for this mod's own types, the full id for addon types. */
    public String shortId() {
        return id.getNamespace().equals(CoreQuests.MOD_ID) ? id.getPath() : id.toString();
    }

    public T create() {
        return factory.get();
    }

    public Icon icon() {
        if (cachedIcon == null) {
            cachedIcon = icon.get();
        }
        return cachedIcon;
    }

    public String translationKey() {
        return "corerpg_quests.task_type." + id.getNamespace() + "." + id.getPath();
    }
}
