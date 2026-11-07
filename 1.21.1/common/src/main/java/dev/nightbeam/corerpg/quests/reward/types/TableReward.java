package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.RewardTable;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Rewards from a reward table. RANDOM rolls the table, CHOICE lets the player pick one entry in the quest book, ALL
 * gives every entry.
 */
public class TableReward extends Reward {
    public enum Mode {
        RANDOM,
        CHOICE,
        ALL
    }

    private final Mode mode;
    public String tableId = "";

    private TableReward(Mode mode) {
        this.mode = mode;
    }

    public static TableReward random() {
        return new TableReward(Mode.RANDOM);
    }

    public static TableReward choice() {
        return new TableReward(Mode.CHOICE);
    }

    public static TableReward all() {
        return new TableReward(Mode.ALL);
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public RewardType<?> type() {
        return switch (mode) {
            case RANDOM -> RewardTypes.RANDOM;
            case CHOICE -> RewardTypes.CHOICE;
            case ALL -> RewardTypes.ALL_TABLE;
        };
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.id("table", () -> tableId, v -> tableId = v);
    }

    public @Nullable RewardTable table() {
        QuestBook book = book();
        return book == null ? null : book.table(tableId);
    }

    @Override
    public boolean needsChoice() {
        return mode == Mode.CHOICE;
    }

    @Override
    public void claim(RewardContext ctx) {
        RewardTable table = table();
        if (table == null) {
            return;
        }
        switch (mode) {
            case RANDOM -> table.roll(ctx.player().getRandom()).forEach(r -> r.claim(ctx));
            case ALL -> table.entries.forEach(e -> e.reward.claim(ctx));
            case CHOICE -> {
                int choice = ctx.choice();
                if (choice >= 0 && choice < table.entries.size()) {
                    table.entries.get(choice).reward.claim(ctx);
                }
            }
        }
    }

    @Override
    protected Component defaultTitle() {
        RewardTable table = table();
        Component name = table == null ? Component.literal(tableId) : table.displayTitle();
        return Component.translatable("corerpg_quests.reward." + mode.name().toLowerCase(Locale.ROOT) + ".title", name);
    }

    @Override
    protected Icon defaultIcon() {
        RewardTable table = table();
        return table == null ? super.defaultIcon() : table.displayIcon();
    }

    @Override
    public void addTooltip(List<Component> lines) {
        RewardTable table = table();
        if (table == null) {
            lines.add(Component.translatable("corerpg_quests.reward.table.missing", tableId).withStyle(ChatFormatting.RED));
            return;
        }
        if (table.hideTooltip) {
            return;
        }
        int shown = 0;
        for (RewardTable.Entry entry : table.entries) {
            if (shown++ >= 12) {
                lines.add(Component.translatable("corerpg_quests.reward.table.more", table.entries.size() - 12).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            Component line = Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY).append(entry.reward.displayTitle().copy().withStyle(ChatFormatting.GRAY));
            if (mode == Mode.RANDOM) {
                line = line.copy().append(Component.literal(String.format(Locale.ROOT, " (%.1f%%)", table.chance(entry))).withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(line);
        }
    }
}
