package dev.nightbeam.corerpg.quests.server;

import com.mojang.brigadier.CommandDispatcher;
import dev.nightbeam.corerpg.quests.command.QuestCommands;
import dev.nightbeam.corerpg.quests.command.TeamCommands;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.vein.VeinMining;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Entry points the loader modules and mixins call. Each is a no-op when the quest engine is not running. */
public final class CommonEvents {
    private CommonEvents() {
    }

    public static void serverStarting(MinecraftServer server) {
        ServerQuests.onServerStarting(server);
    }

    public static void serverStopped() {
        ServerQuests.onServerStopped();
    }

    private static final int QA_STOP_TICKS = Integer.getInteger("corerpg_quests.qa.stop_after_ticks", -1);
    private static int qaTicks;

    public static void serverTickEnd(MinecraftServer server) {
        if (QA_STOP_TICKS > 0 && server.isDedicatedServer() && ++qaTicks == QA_STOP_TICKS) {
            dev.nightbeam.corerpg.quests.CoreQuests.LOG.info("QA: stopping the server after {} ticks", QA_STOP_TICKS);
            server.halt(false);
        }
        VeinMining.tick();
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq != null) {
            sq.tick();
        }
    }

    public static void playerJoin(ServerPlayer player) {
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq != null) {
            sq.onPlayerJoin(player);
        }
    }

    public static void playerLeave(ServerPlayer player) {
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq != null) {
            sq.onPlayerLeave(player);
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        QuestCommands.register(dispatcher, context);
        TeamCommands.register(dispatcher);
    }

    public static void trigger(ServerPlayer player, TriggerEvent event) {
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq != null) {
            sq.engine().trigger(player, event);
        }
    }

    /** Team chat: returns true when the message was sent to the team instead of public chat. */
    public static boolean redirectChat(ServerPlayer player, String message) {
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq == null || !sq.teams().isTeamChat(player.getUUID())) {
            return false;
        }
        Team party = sq.teams().partyOf(player.getUUID());
        if (party == null) {
            return false;
        }
        TeamActions.tell(sq, party, Component.empty().append(player.getDisplayName()).append(Component.literal(": " + message).withStyle(net.minecraft.ChatFormatting.WHITE)));
        return true;
    }
}
