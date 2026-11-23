package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * A weighted list of rewards. Quests use it for random rolls, "pick one" choices or "everything in the table"; with
 * the loot crate options it also becomes a crate item that mobs can drop.
 */
public final class RewardTable extends QuestObject {
    QuestBook book;
    public int rolls = 1;
    public double emptyWeight = 0;
    public boolean hideTooltip = false;
    public boolean lootCrate = false;
    public String crateName = "";
    public int crateColor = 0xFFFFFF;
    public boolean crateGlow = false;
    public double passiveDropChance = 0;
    public double monsterDropChance = 0;
    public double bossDropChance = 0;

    public final List<Entry> entries = new ArrayList<>();

    public RewardTable(QuestBook book) {
        this.book = book;
    }

    /** One weighted reward in the table. */
    public static final class Entry {
        public double weight;
        public final Reward reward;

        public Entry(double weight, Reward reward) {
            this.weight = weight;
            this.reward = reward;
        }
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.TABLE;
    }

    @Override
    public QuestBook book() {
        return book;
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.reward_table");
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.CHEST);
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.integer("rolls", 1, 1, 1000, () -> rolls, v -> rolls = v);
        p.decimal("empty_weight", 0, 0, 1_000_000, () -> emptyWeight, v -> emptyWeight = v);
        p.bool("hide_tooltip", false, () -> hideTooltip, v -> hideTooltip = v);
        p.category("loot_crate");
        p.bool("loot_crate", false, () -> lootCrate, v -> lootCrate = v);
        p.string("crate_name", "", () -> crateName, v -> crateName = v);
        p.color("crate_color", 0xFFFFFF, () -> crateColor, v -> crateColor = v);
        p.bool("crate_glow", false, () -> crateGlow, v -> crateGlow = v);
        p.decimal("passive_drop_chance", 0, 0, 100, () -> passiveDropChance, v -> passiveDropChance = v);
        p.decimal("monster_drop_chance", 0, 0, 100, () -> monsterDropChance, v -> monsterDropChance = v);
        p.decimal("boss_drop_chance", 0, 0, 100, () -> bossDropChance, v -> bossDropChance = v);
    }

    @Override
    protected void writeChildren(JsonObject json, JsonCtx ctx) {
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject o = entry.reward.toJson(ctx);
            o.addProperty("weight", entry.weight);
            array.add(o);
        }
        json.add("entries", array);
    }

    @Override
    protected void readChildren(JsonObject json, JsonCtx ctx) {
        entries.clear();
        if (json.has("entries")) {
            for (var e : json.getAsJsonArray("entries")) {
                JsonObject o = e.getAsJsonObject();
                Reward reward = RewardTypes.read(o, ctx);
                if (reward == null) {
                    CoreQuests.LOG.warn("Reward table '{}': skipped an entry of unknown type {}", id, o.get("type"));
                    continue;
                }
                reward.table = this;
                double weight = o.has("weight") ? o.get("weight").getAsDouble() : 1.0;
                entries.add(new Entry(Math.max(0, weight), reward));
            }
        }
    }

    public static RewardTable read(QuestBook book, JsonObject json, JsonCtx ctx) {
        RewardTable table = new RewardTable(book);
        table.setIdInternal(json.has("id") ? json.get("id").getAsString() : "");
        table.fromJson(json, ctx);
        return table;
    }

    public double totalWeight() {
        double total = emptyWeight;
        for (Entry entry : entries) {
            total += entry.weight;
        }
        return total;
    }

    /** Rolls {@link #rolls} times; each roll picks one entry by weight, or nothing when the empty weight wins. */
    public List<Reward> roll(RandomSource random) {
        List<Reward> result = new ArrayList<>();
        double total = totalWeight();
        if (total <= 0 || entries.isEmpty()) {
            return result;
        }
        for (int i = 0; i < rolls; i++) {
            double pick = random.nextDouble() * total;
            for (Entry entry : entries) {
                pick -= entry.weight;
                if (pick < 0) {
                    result.add(entry.reward);
                    break;
                }
            }
        }
        return result;
    }

    /** The chance in percent that one roll picks this entry. */
    public double chance(Entry entry) {
        double total = totalWeight();
        return total <= 0 ? 0 : entry.weight * 100.0 / total;
    }

    public Component crateDisplayName() {
        return crateName.isBlank() ? displayTitle() : dev.nightbeam.corerpg.quests.util.TextUtil.format(crateName);
    }
}
