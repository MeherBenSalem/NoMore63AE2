package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.api.CoreQuestsEvents;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;

/** Does nothing by itself: fires {@link CoreQuestsEvents#CUSTOM_REWARD} with its key for scripts and addons. */
public class CustomReward extends Reward {
    public String key = "";

    @Override
    public RewardType<?> type() {
        return RewardTypes.CUSTOM;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.string("key", "", () -> key, v -> key = v);
    }

    @Override
    public void claim(RewardContext ctx) {
        CoreQuestsEvents.CUSTOM_REWARD.invoker().onCustomReward(ctx.player(), this);
    }

    @Override
    protected Component defaultTitle() {
        return Component.literal(key.isBlank() ? "?" : key);
    }
}
