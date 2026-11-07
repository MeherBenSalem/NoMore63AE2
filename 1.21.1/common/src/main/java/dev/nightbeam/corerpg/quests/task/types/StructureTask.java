package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;

/** Stand inside a structure (any of its pieces): a village, a stronghold, a modded dungeon. */
public class StructureTask extends Task {
    public Ref structure = new Ref(ResourceLocation.withDefaultNamespace("village_plains"), false);

    @Override
    public TaskType<?> type() {
        return TaskTypes.STRUCTURE;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.ref("structure", ResourceKind.STRUCTURE, () -> structure, v -> structure = v);
    }

    @Override
    public boolean polls() {
        return true;
    }

    @Override
    public void poll(TaskContext ctx) {
        if (structure == null) {
            return;
        }
        ServerLevel level = ctx.player().serverLevel();
        BlockPos pos = ctx.player().blockPosition();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        if (structure.tag()) {
            Optional<HolderSet.Named<Structure>> tag = registry.getTag(TagKey.create(Registries.STRUCTURE, structure.id()));
            if (tag.isEmpty()) {
                return;
            }
            for (Holder<Structure> holder : tag.get()) {
                if (level.structureManager().getStructureWithPieceAt(pos, holder.value()).isValid()) {
                    ctx.setProgress(1);
                    return;
                }
            }
        } else {
            Structure s = registry.get(structure.id());
            if (s != null && level.structureManager().getStructureWithPieceAt(pos, s).isValid()) {
                ctx.setProgress(1);
            }
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.structure.title", Component.literal(structure == null ? "?" : structure.toString()));
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.FILLED_MAP);
    }
}
