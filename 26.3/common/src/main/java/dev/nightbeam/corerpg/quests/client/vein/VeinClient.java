package dev.nightbeam.corerpg.quests.client.vein;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightbeam.corerpg.quests.client.KeyBinds;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.vein.VeinMining;
import dev.nightbeam.corerpg.quests.vein.VeinShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Client side of vein mining: the key, shape cycling, the outline preview and the crosshair overlay. */
public final class VeinClient {
    private static boolean active;
    private static int shape;
    private static boolean sentActive;
    private static int sentShape = -1;
    private static List<BlockPos> preview = List.of();
    private static BlockPos previewOrigin;
    private static BlockState previewState;
    private static int previewShape = -1;
    private static int previewAge;
    /** QA automation can hold the key without a keyboard. */
    public static boolean qaHeld;

    private VeinClient() {
    }

    public static void reset() {
        active = false;
        sentActive = false;
        sentShape = -1;
        preview = List.of();
        previewOrigin = null;
    }

    public static boolean isActive() {
        return active;
    }

    public static void setShapeForQa(int index) {
        shape = Math.floorMod(index, VeinShape.values().length);
    }

    public static VeinShape shape() {
        return VeinShape.byIndex(shape);
    }

    public static void tick(Minecraft mc) {
        active = CoreConfig.SERVER.veinEnabled.get() && (KeyBinds.VEIN_MINE.isDown() || qaHeld) && mc.gui.screen() == null;
        if (active != sentActive || shape != sentShape) {
            sentActive = active;
            sentShape = shape;
            if (mc.getConnection() != null) {
                Services.PLATFORM.sendToServer(new Payloads.VeinState(active, shape));
            }
        }
        if (!active) {
            preview = List.of();
            previewOrigin = null;
            return;
        }
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK || mc.level == null || mc.player == null) {
            preview = List.of();
            previewOrigin = null;
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (pos.equals(previewOrigin) && state.equals(previewState) && shape == previewShape && ++previewAge < 10) {
            return;
        }
        previewOrigin = pos;
        previewState = state;
        previewShape = shape;
        previewAge = 0;
        preview = state.isAir() ? List.of() : VeinMining.plan(mc.level, mc.player, pos, state, blockHit.getDirection(), shape(), CoreConfig.SERVER.veinMaxBlocks.get());
    }

    /** Scroll while holding the key to change shape. Returns true to swallow the scroll. */
    public static boolean onScroll(double delta) {
        if (!active || delta == 0) {
            return false;
        }
        shape = Math.floorMod(shape + (delta > 0 ? -1 : 1), VeinShape.values().length);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("corerpg_quests.vein.shape", shape().displayName()), true);
        }
        previewAge = 100;
        return true;
    }

    private static final VoxelShape BOX = Shapes.create(new AABB(0, 0, 0, 1, 1, 1).inflate(0.004));

    public static void submitWorld(SubmitNodeCollector collector, Vec3 camera) {
        if (!active || preview.isEmpty() || !CoreConfig.CLIENT.veinPreview.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float alpha = CoreConfig.CLIENT.veinPreviewAlpha.get().floatValue();
        float width = mc.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        int i = 0;
        for (BlockPos pos : preview) {
            float t = preview.size() <= 1 ? 0 : i / (float) (preview.size() - 1);
            // Warm ember at the origin fading to gold at the edge of the vein.
            float r = 1.0F;
            float g = 0.55F + 0.35F * t;
            float b = 0.15F + 0.25F * t;
            PoseStack pose = new PoseStack();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            collector.submitShapeOutline(pose, BOX, RenderTypes.lines(), ARGB.colorFromFloat(alpha, r, g, b), width, false);
            i++;
        }
    }

    public static void renderOverlay(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!active || !CoreConfig.CLIENT.veinOverlay.get()) {
            return;
        }
        int x = graphics.guiWidth() / 2 + 12;
        int y = graphics.guiHeight() / 2 + 8;
        Component title = Component.translatable("corerpg_quests.vein.overlay", shape().displayName());
        Component count = Component.translatable("corerpg_quests.vein.blocks", preview.size());
        int w = Math.max(mc.font.width(title), mc.font.width(count)) + 8;
        graphics.fill(x - 3, y - 3, x + w - 3, y + 21, 0xA0100A08);
        graphics.fill(x - 3, y - 3, x - 2, y + 21, 0xFFE8A33D);
        graphics.text(mc.font, title, x + 1, y, 0xFFF2D49B, false);
        graphics.text(mc.font, count, x + 1, y + 10, 0xFFB8A58C, false);
    }
}
