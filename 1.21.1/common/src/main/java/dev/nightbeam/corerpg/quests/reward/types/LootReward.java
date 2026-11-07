package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Rolls a vanilla (datapack) loot table, e.g. a dungeon chest table, and gives the result. */
public class LootReward extends Reward {
    public ResourceLocation lootTable = ResourceLocation.withDefaultNamespace("chests/simple_dungeon");
    public int rolls = 1;

    @Override
    public RewardType<?> type() {
        return RewardTypes.LOOT;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.resource("loot_table", ResourceKind.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/simple_dungeon"), () -> lootTable, v -> lootTable = v);
        p.integer("rolls", 1, 1, 100, () -> rolls, v -> rolls = v);
    }

    @Override
    public void claim(RewardContext ctx) {
        ServerPlayer player = ctx.player();
        LootTable table = player.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTable));
        if (table == LootTable.EMPTY) {
            return;
        }
        for (int i = 0; i < rolls; i++) {
            LootParams params = new LootParams.Builder(player.serverLevel())
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.GIFT);
            for (ItemStack stack : table.getRandomItems(params)) {
                ctx.giveItem(stack);
            }
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.reward.loot.title", Component.literal(lootTable.toString()));
    }
}
