package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of everything in a quest book. Each subclass lists its fields once in {@link #defineProps(Props)}; that list
 * is what the JSON files, the network sync and the editor use.
 */
public abstract class QuestObject {
    protected String id = "";
    public String title = "";
    public Icon icon = Icon.EMPTY;
    public List<String> tags = new ArrayList<>();

    public final String id() {
        return id;
    }

    /** Only the book (when loading or editing) changes ids, so its index stays correct. */
    public final void setIdInternal(String id) {
        this.id = id;
    }

    public abstract ObjectKind kind();

    /** The book this object belongs to, or null while it is detached (e.g. being pasted). */
    public abstract QuestBook book();

    protected void defineProps(Props p) {
        p.category("general");
        p.string("title", "", () -> title, v -> title = v);
        p.icon("icon", () -> icon, v -> icon = v);
        p.stringList("tags", () -> tags, v -> tags = v);
    }

    public final Props props() {
        Props p = new Props();
        defineProps(p);
        return p;
    }

    /** The object with its children, as stored in files and sent to clients. */
    public JsonObject toJson(JsonCtx ctx) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        writeType(json);
        props().writeTo(json, ctx);
        writeChildren(json, ctx);
        return json;
    }

    /** Only this object's own fields (what the property editor changes). */
    public JsonObject propsToJson(JsonCtx ctx) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        writeType(json);
        props().writeTo(json, ctx);
        return json;
    }

    public void fromJson(JsonObject json, JsonCtx ctx) {
        readProps(json, ctx);
        readChildren(json, ctx);
    }

    public void readProps(JsonObject json, JsonCtx ctx) {
        props().readFrom(json, ctx, describe());
    }

    protected void writeType(JsonObject json) {
    }

    protected void writeChildren(JsonObject json, JsonCtx ctx) {
    }

    protected void readChildren(JsonObject json, JsonCtx ctx) {
    }

    public Component displayTitle() {
        return title.isBlank() ? defaultTitle() : TextUtil.format(title);
    }

    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.unnamed");
    }

    public Icon displayIcon() {
        return icon.isEmpty() ? defaultIcon() : icon;
    }

    protected Icon defaultIcon() {
        return Icon.EMPTY;
    }

    public String describe() {
        return kind().getSerializedName() + " '" + id + "'";
    }

    @Override
    public String toString() {
        return describe();
    }
}
