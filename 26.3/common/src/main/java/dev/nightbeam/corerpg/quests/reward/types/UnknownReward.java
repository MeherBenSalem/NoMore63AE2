package dev.nightbeam.corerpg.quests.reward.types;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** A reward of a type that is not installed; its JSON is kept as-is and claiming it does nothing. */
public class UnknownReward extends Reward {
    private JsonObject raw = new JsonObject();

    @Override
    public RewardType<?> type() {
        return RewardTypes.UNKNOWN;
    }

    public String originalType() {
        return raw.has("type") ? raw.get("type").getAsString() : "?";
    }

    @Override
    public JsonObject toJson(JsonCtx ctx) {
        JsonObject copy = raw.deepCopy();
        copy.addProperty("id", id);
        return copy;
    }

    @Override
    public JsonObject propsToJson(JsonCtx ctx) {
        return toJson(ctx);
    }

    @Override
    public void fromJson(JsonObject json, JsonCtx ctx) {
        raw = json.deepCopy();
        super.fromJson(json, ctx);
    }

    @Override
    public void claim(RewardContext ctx) {
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.reward.unknown", originalType());
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.BARRIER);
    }
}
