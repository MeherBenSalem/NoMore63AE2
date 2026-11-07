package dev.nightbeam.corerpg.quests.reward;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** What a reward can do while it is being claimed. */
public interface RewardContext {
    ServerPlayer player();

    /** False when the reward is auto-claimed silently: no toasts or chat messages. */
    boolean shouldNotify();

    /** Gives an item (dropping what does not fit) and records it for the "rewards received" toast. */
    void giveItem(ItemStack stack);

    /** Records a line for the "rewards received" toast (XP, stages...). */
    void announce(Component text);

    /** Shows a custom toast to the player. */
    void toast(Component title, Component description, dev.nightbeam.corerpg.quests.book.Icon icon);

    void addStage(String stage);

    void removeStage(String stage);

    /** The choice made in the quest book for a choice reward (-1 when none was made). */
    int choice();
}
