package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Look at a block or entity for a while ("study the altar", "watch the warden"). Checked on the server. */
public class ObservationTask extends Task {
    public enum Target implements StringRepresentable {
        BLOCK("block"),
        ENTITY("entity");

        private final String name;

        Target(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public Target target = Target.BLOCK;
    public Ref block = new Ref(ResourceLocation.withDefaultNamespace("enchanting_table"), false);
    public Ref entity = null;
    public int ticks = 60;
    public double range = 0;

    @Override
    public TaskType<?> type() {
        return TaskTypes.OBSERVATION;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.enumValue("target", Target.class, Target.BLOCK, () -> target, v -> target = v);
        p.ref("block", ResourceKind.BLOCK, () -> block, v -> block = v);
        p.ref("entity", ResourceKind.ENTITY, () -> entity, v -> entity = v);
        p.integer("ticks", 60, 1, 72000, () -> ticks, v -> ticks = v);
        p.decimal("range", 0, 0, 128, () -> range, v -> range = v);
    }

    @Override
    public long maxProgress() {
        return ticks;
    }

    @Override
    public boolean polls() {
        return true;
    }

    @Override
    public Component progressText(long progress) {
        return Component.literal(String.format(java.util.Locale.ROOT, "%.1fs / %.1fs", Math.min(progress, ticks) / 20.0, ticks / 20.0));
    }

    @Override
    public void poll(TaskContext ctx) {
        int delay = book() == null ? 20 : book().detectionDelay;
        if (isObserving(ctx.player())) {
            ctx.setProgress(Math.min(ticks, ctx.progress() + delay));
        } else if (ctx.progress() > 0) {
            ctx.setProgress(0);
        }
    }

    @Override
    public void recheck(TaskContext ctx) {
        // Observation needs time; nothing to check instantly.
    }

    public boolean isObserving(ServerPlayer player) {
        double reach = range > 0 ? range : target == Target.BLOCK ? player.blockInteractionRange() : player.entityInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        if (target == Target.BLOCK) {
            HitResult hit = player.pick(reach, 1.0F, false);
            return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                    && block != null && block.matches(player.level().getBlockState(blockHit.getBlockPos()));
        }
        AABB box = player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable(), reach * reach);
        if (hit == null) {
            return false;
        }
        Entity seen = hit.getEntity();
        HitResult blockHit = player.pick(reach, 1.0F, false);
        if (blockHit.getType() == HitResult.Type.BLOCK && blockHit.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) {
            return false;
        }
        return entity == null || entity.matches(seen);
    }

    @Override
    protected Component defaultTitle() {
        String what = target == Target.BLOCK ? (block == null ? "?" : block.toString()) : (entity == null ? "?" : entity.toString());
        return Component.translatable("corerpg_quests.task.observation.title", Component.literal(what));
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.SPYGLASS);
    }
}
