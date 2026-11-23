package dev.nightbeam.corerpg.quests.reward;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.Icon;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/** A registered kind of reward. Addons register their own through {@link RewardTypes#register}. */
public final class RewardType<T extends Reward> {
    private final ResourceLocation id;
    private final Supplier<T> factory;
    private final Supplier<Icon> icon;
    private Icon cachedIcon;

    public RewardType(ResourceLocation id, Supplier<T> factory, Supplier<Icon> icon) {
        this.id = id;
        this.factory = factory;
        this.icon = icon;
    }

    public ResourceLocation id() {
        return id;
    }

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
        return "corerpg_quests.reward_type." + id.getNamespace() + "." + id.getPath();
    }
}
