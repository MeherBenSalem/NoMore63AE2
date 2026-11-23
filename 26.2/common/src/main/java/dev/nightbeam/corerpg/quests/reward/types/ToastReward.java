package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.network.chat.Component;

/** Shows a toast with a title and a line of text: a story beat, a hint, a "the gate is open". */
public class ToastReward extends Reward {
    public String description = "";

    @Override
    public RewardType<?> type() {
        return RewardTypes.TOAST;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.string("description", "", () -> description, v -> description = v);
    }

    @Override
    public void claim(RewardContext ctx) {
        ctx.toast(displayTitle(), TextUtil.format(description), displayIcon());
    }
}
