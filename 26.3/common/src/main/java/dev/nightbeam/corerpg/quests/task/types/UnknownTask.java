package dev.nightbeam.corerpg.quests.task.types;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * A task whose type is not installed (an addon was removed). It keeps its JSON untouched, so saving the book does not
 * lose it, and it can never be completed.
 */
public class UnknownTask extends Task {
    private JsonObject raw = new JsonObject();

    @Override
    public TaskType<?> type() {
        return TaskTypes.UNKNOWN;
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
    public long maxProgress() {
        return Long.MAX_VALUE;
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.unknown", originalType());
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.BARRIER);
    }
}
