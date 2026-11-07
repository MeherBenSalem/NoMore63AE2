package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;

/** Gives experience points or levels. */
public class XpReward extends Reward {
    private final boolean levels;
    public int amount = 100;

    private XpReward(boolean levels) {
        this.levels = levels;
        this.amount = levels ? 5 : 100;
    }

    public static XpReward points() {
        return new XpReward(false);
    }

    public static XpReward levels() {
        return new XpReward(true);
    }

    @Override
    public RewardType<?> type() {
        return levels ? RewardTypes.XP_LEVELS : RewardTypes.XP;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.integer(levels ? "levels" : "xp", levels ? 5 : 100, 1, 1_000_000_000, () -> amount, v -> amount = v);
    }

    @Override
    public void claim(RewardContext ctx) {
        if (levels) {
            ctx.player().giveExperienceLevels(amount);
        } else {
            ctx.player().giveExperiencePoints(amount);
        }
        ctx.announce(defaultTitle());
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable(levels ? "corerpg_quests.reward.xp_levels.title" : "corerpg_quests.reward.xp.title", amount);
    }
}
