package dev.nightbeam.corerpg.quests.server;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.filter.FilterParser;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.progress.TeamProgress;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.team.TeamException;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import dev.nightbeam.corerpg.quests.vein.VeinMining;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Handles packets from clients. Every handler re-checks permissions; nothing from the client is trusted. */
public final class ServerPacketHandler {
    private ServerPacketHandler() {
    }

    private static ServerQuests sq() {
        return ServerQuests.getOrNull();
    }

    public static void claimReward(Payloads.ClaimReward p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq != null) {
            sq.engine().claim(player, p.rewardId(), p.choice());
        }
    }

    public static void claimAll(Payloads.ClaimAll p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq != null) {
            sq.engine().claimAll(player, p.scopeId());
        }
    }

    public static void submitTask(Payloads.SubmitTask p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq != null) {
            sq.engine().submit(player, p.taskId());
        }
    }

    public static void pinQuest(Payloads.PinQuest p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq == null || (sq.book().get(p.questId()) == null && p.pin())) {
            return;
        }
        Team team = sq.teamOf(player);
        TeamProgress progress = sq.progress(team);
        progress.setPinned(player.getUUID(), p.questId(), p.pin());
        sq.markSync(team);
    }

    public static void requestEdit(Payloads.RequestEdit p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq == null) {
            return;
        }
        if (!sq.canEdit(player) || !sq.isEditing(player)) {
            player.sendSystemMessage(Component.translatable("corerpg_quests.editor.not_editing").withStyle(ChatFormatting.RED));
            return;
        }
        JsonObject op;
        try {
            op = JsonUtil.parse(JsonUtil.gunzip(p.data())).getAsJsonObject();
        } catch (RuntimeException e) {
            CoreQuests.LOG.warn("Malformed edit from {}", player.getGameProfile().getName(), e);
            return;
        }
        String error = sq.applyEdit(op);
        if (error != null) {
            player.sendSystemMessage(Component.translatable("corerpg_quests.editor.refused", error).withStyle(ChatFormatting.RED));
            sq.sendBook(player);
        }
    }

    public static void changeProgress(Payloads.ChangeProgress p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq == null || !sq.canEdit(player) || !sq.isEditing(player)) {
            return;
        }
        QuestObject object = p.objectId().isEmpty() ? sq.book() : sq.book().get(p.objectId());
        if (object == null) {
            return;
        }
        Team team = sq.teamOf(player);
        if (p.complete()) {
            sq.engine().forceComplete(team, object, player);
        } else {
            sq.engine().reset(team, object);
        }
    }

    public static void setEditMode(Payloads.SetEditMode p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq != null) {
            sq.setEditing(player, p.editing());
        }
    }

    public static void teamAction(Payloads.TeamAction p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq == null) {
            return;
        }
        try {
            TeamActions.run(sq, player, p.action(), p.arg());
        } catch (TeamException e) {
            player.displayClientMessage(e.component().copy().withStyle(ChatFormatting.RED), false);
            sq.sendState(player);
        }
    }

    public static void setFilter(Payloads.SetFilter p, ServerPlayer player) {
        ItemStack stack = player.getItemInHand(p.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!stack.is(ModRegistry.SMART_FILTER.get())) {
            return;
        }
        String filter = p.filter().trim();
        if (filter.isEmpty()) {
            stack.remove(ModRegistry.FILTER_COMPONENT.get());
            return;
        }
        String error = FilterParser.validate(filter);
        if (error != null) {
            player.displayClientMessage(Component.translatable("corerpg_quests.filter.invalid", error).withStyle(ChatFormatting.RED), false);
            return;
        }
        stack.set(ModRegistry.FILTER_COMPONENT.get(), filter);
    }

    public static void veinState(Payloads.VeinState p, ServerPlayer player) {
        VeinMining.setState(player, p.active(), p.shape());
    }

    public static void emergencyItems(Payloads.EmergencyItems p, ServerPlayer player) {
        ServerQuests sq = sq();
        if (sq != null) {
            sq.engine().giveEmergencyItems(player);
        }
    }
}
