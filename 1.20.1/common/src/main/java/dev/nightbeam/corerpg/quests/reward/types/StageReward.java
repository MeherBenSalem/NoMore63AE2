package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;

/** Adds (or removes) a team stage. Stage tasks, other mods and scripts can react to it. */
public class StageReward extends Reward {
    public String stage = "";
    public boolean remove = false;
    public boolean silent = false;

    @Override
    public RewardType<?> type() {
        return RewardTypes.STAGE;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.string("stage", "", () -> stage, v -> stage = v);
        p.bool("remove", false, () -> remove, v -> remove = v);
        p.bool("silent", false, () -> silent, v -> silent = v);
    }

    @Override
    public void claim(RewardContext ctx) {
        if (stage.isBlank()) {
            return;
        }
        if (remove) {
            ctx.removeStage(stage);
        } else {
            ctx.addStage(stage);
        }
        if (!silent) {
            ctx.announce(defaultTitle());
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable(remove ? "corerpg_quests.reward.stage.removed" : "corerpg_quests.reward.stage.added", stage);
    }
}
