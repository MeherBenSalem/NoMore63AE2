package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Grants an advancement (or one of its criteria). Handy for unlocking recipe-book entries or datapack logic. */
public class AdvancementReward extends Reward {
    public ResourceLocation advancement = ResourceLocation.withDefaultNamespace("story/root");
    public String criterion = "";

    @Override
    public RewardType<?> type() {
        return RewardTypes.ADVANCEMENT;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.resource("advancement", ResourceKind.ADVANCEMENT, ResourceLocation.withDefaultNamespace("story/root"), () -> advancement, v -> advancement = v);
        p.string("criterion", "", () -> criterion, v -> criterion = v);
    }

    @Override
    public void claim(RewardContext ctx) {
        ServerPlayer player = ctx.player();
        AdvancementHolder holder = player.server.getAdvancements().get(advancement);
        if (holder == null) {
            return;
        }
        if (!criterion.isBlank()) {
            player.getAdvancements().award(holder, criterion);
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String c : remaining) {
            player.getAdvancements().award(holder, c);
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.reward.advancement.title", Component.literal(advancement.toString()));
    }
}
