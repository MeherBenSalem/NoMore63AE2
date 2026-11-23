package dev.nightbeam.corerpg.quests.reward;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.AutoclaimMode;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.book.ObjectKind;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.book.RewardTable;
import dev.nightbeam.corerpg.quests.book.Tristate;
import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Something a quest gives. Rewards are claimed per player, unless they are team rewards, which the first member to
 * claim takes for the whole team. Rewards also appear as entries of reward tables.
 */
public abstract class Reward extends QuestObject {
    public @Nullable Quest quest;
    public @Nullable RewardTable table;
    public Tristate teamReward = Tristate.DEFAULT;
    public AutoclaimMode autoclaim = AutoclaimMode.DEFAULT;
    public boolean excludeFromClaimAll = false;
    public boolean ignoreRewardBlocking = false;

    @Override
    public final ObjectKind kind() {
        return ObjectKind.REWARD;
    }

    @Override
    public QuestBook book() {
        if (quest != null) {
            return quest.book();
        }
        return table == null ? null : table.book();
    }

    public abstract RewardType<?> type();

    @Override
    protected void writeType(JsonObject json) {
        json.addProperty("type", type().shortId());
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("claiming");
        p.enumValue("team_reward", Tristate.class, Tristate.DEFAULT, () -> teamReward, v -> teamReward = v);
        p.enumValue("autoclaim", AutoclaimMode.class, AutoclaimMode.DEFAULT, () -> autoclaim, v -> autoclaim = v);
        p.bool("exclude_from_claim_all", false, () -> excludeFromClaimAll, v -> excludeFromClaimAll = v);
        p.bool("ignore_reward_blocking", false, () -> ignoreRewardBlocking, v -> ignoreRewardBlocking = v);
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable(type().translationKey());
    }

    @Override
    protected Icon defaultIcon() {
        return type().icon();
    }

    public abstract void claim(RewardContext ctx);

    public boolean isTeamReward() {
        QuestBook book = book();
        return teamReward.get(book != null && book.defaultTeamRewards);
    }

    public AutoclaimMode effectiveAutoclaim() {
        QuestBook book = book();
        if (book != null && book.suppressAllAutoclaim) {
            return AutoclaimMode.DISABLED;
        }
        if (autoclaim != AutoclaimMode.DEFAULT) {
            return autoclaim;
        }
        return book == null ? AutoclaimMode.DISABLED : book.defaultAutoclaim;
    }

    /** Choice rewards need the player to pick, so they can never be auto-claimed. */
    public boolean needsChoice() {
        return false;
    }

    public void addTooltip(List<Component> lines) {
    }
}
