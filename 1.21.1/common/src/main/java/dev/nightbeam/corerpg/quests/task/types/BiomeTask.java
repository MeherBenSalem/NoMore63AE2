package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Visit a biome (or any biome in a tag). */
public class BiomeTask extends Task {
    public Ref biome = new Ref(ResourceLocation.withDefaultNamespace("plains"), false);

    @Override
    public TaskType<?> type() {
        return TaskTypes.BIOME;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.ref("biome", ResourceKind.BIOME, () -> biome, v -> biome = v);
    }

    @Override
    public boolean polls() {
        return true;
    }

    @Override
    public void poll(TaskContext ctx) {
        if (biome != null && biome.matches(ctx.player().level().getBiome(ctx.player().blockPosition()), Registries.BIOME)) {
            ctx.setProgress(1);
        }
    }

    @Override
    protected Component defaultTitle() {
        if (biome == null) {
            return Component.translatable("corerpg_quests.task_type.corerpg_quests.biome");
        }
        Component name = biome.tag() ? Component.literal("#" + biome.id())
                : Component.translatable("biome." + biome.id().getNamespace() + "." + biome.id().getPath());
        return Component.translatable("corerpg_quests.task.biome.title", name);
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.OAK_SAPLING);
    }
}
