package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Gives items. Counts above a stack's size are split into several stacks. */
public class ItemReward extends Reward {
    public ItemStack item = new ItemStack(Items.APPLE);
    public int count = 1;
    public int randomBonus = 0;

    @Override
    public RewardType<?> type() {
        return RewardTypes.ITEM;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.item("item", () -> item, v -> item = v);
        p.integer("count", 1, 1, 1_000_000, () -> count, v -> count = v);
        p.integer("random_bonus", 0, 0, 1_000_000, () -> randomBonus, v -> randomBonus = v);
    }

    @Override
    public void claim(RewardContext ctx) {
        if (item.isEmpty()) {
            return;
        }
        int total = count + (randomBonus > 0 ? ctx.player().getRandom().nextInt(randomBonus + 1) : 0);
        int max = Math.max(1, item.getMaxStackSize());
        while (total > 0) {
            int n = Math.min(total, max);
            ctx.giveItem(item.copyWithCount(n));
            total -= n;
        }
    }

    @Override
    protected Component defaultTitle() {
        Component name = item.getHoverName();
        if (count > 1 || randomBonus > 0) {
            String amount = randomBonus > 0 ? count + "-" + (count + randomBonus) : String.valueOf(count);
            return Component.literal(amount + "x ").append(name);
        }
        return name;
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(item);
    }

    @Override
    public void addTooltip(List<Component> lines) {
        if (randomBonus > 0) {
            lines.add(Component.translatable("corerpg_quests.reward.item.bonus", randomBonus));
        }
    }
}
