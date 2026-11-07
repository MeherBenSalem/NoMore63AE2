package dev.nightbeam.corerpg.quests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.nightbeam.corerpg.quests.server.ServerQuests;
import dev.nightbeam.corerpg.quests.server.TeamActions;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.team.TeamException;
import dev.nightbeam.corerpg.quests.team.TeamRank;
import dev.nightbeam.corerpg.quests.team.TeamType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /team: parties, invites, ranks, allies, settings, team chat; and admin server teams. */
public final class TeamCommands {
    private static final SuggestionProvider<CommandSourceStack> TEAMS = (ctx, builder) -> {
        ServerQuests sq = ServerQuests.getOrNull();
        List<String> names = new ArrayList<>();
        if (sq != null) {
            for (Team t : sq.teams().all()) {
                if (t.type() != TeamType.PLAYER) {
                    names.add(t.shortId());
                }
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (ctx, builder) -> {
        ServerQuests sq = ServerQuests.getOrNull();
        List<String> names = new ArrayList<>();
        if (sq != null && ctx.getSource().getPlayer() != null) {
            Team team = sq.teamOf(ctx.getSource().getPlayer());
            team.ranks().keySet().forEach(id -> names.add(team.playerName(id)));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private TeamCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("team")
                .executes(ctx -> info(ctx, null))
                .then(Commands.literal("info").executes(ctx -> info(ctx, null))
                        .then(Commands.argument("team", StringArgumentType.string()).suggests(TEAMS).executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("list").executes(TeamCommands::list))
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> action(ctx, "create", "name"))))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> action(ctx, "invite", EntityArgument.getPlayer(ctx, "player").getGameProfile().getName()))))
                .then(Commands.literal("accept").then(Commands.argument("team", StringArgumentType.string()).suggests(TEAMS).executes(ctx -> action(ctx, "accept", "team"))))
                .then(Commands.literal("decline").then(Commands.argument("team", StringArgumentType.string()).suggests(TEAMS).executes(ctx -> action(ctx, "decline", "team"))))
                .then(Commands.literal("join").then(Commands.argument("team", StringArgumentType.string()).suggests(TEAMS).executes(ctx -> action(ctx, "join", "team"))))
                .then(Commands.literal("leave").executes(ctx -> run(ctx, "leave", "")))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(ctx -> action(ctx, "kick", "player"))))
                .then(Commands.literal("promote").then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(ctx -> action(ctx, "promote", "player"))))
                .then(Commands.literal("demote").then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(ctx -> action(ctx, "demote", "player"))))
                .then(Commands.literal("transfer").then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(ctx -> action(ctx, "transfer", "player"))))
                .then(Commands.literal("ally")
                        .then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> action(ctx, "ally_add", "player"))))
                        .then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(ctx -> action(ctx, "ally_remove", "player")))))
                .then(Commands.literal("disband").executes(ctx -> run(ctx, "disband", "")))
                .then(Commands.literal("chat").executes(ctx -> run(ctx, "toggle_chat", "")))
                .then(Commands.literal("msg").then(Commands.argument("message", StringArgumentType.greedyString()).executes(TeamCommands::message)))
                .then(Commands.literal("settings")
                        .then(Commands.literal("name").then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> action(ctx, "set_name", "value"))))
                        .then(Commands.literal("description").then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> action(ctx, "set_description", "value"))))
                        .then(Commands.literal("color").then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> action(ctx, "set_color", "value"))))
                        .then(Commands.literal("free_to_join").then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> run(ctx, "set_free_to_join", String.valueOf(BoolArgumentType.getBool(ctx, "value")))))))
                .then(Commands.literal("server").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString()).executes(TeamCommands::serverCreate)))
                        .then(Commands.literal("delete").then(Commands.argument("team", StringArgumentType.string()).suggests(TEAMS).executes(TeamCommands::serverDelete)))
                        .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("team", StringArgumentType.string()).suggests(TEAMS).executes(TeamCommands::forceJoin))))));
    }

    private static ServerQuests sq(CommandContext<CommandSourceStack> ctx) {
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq == null) {
            throw new IllegalStateException("Quest engine not running");
        }
        return sq;
    }

    private static int action(CommandContext<CommandSourceStack> ctx, String action, String argName) throws CommandSyntaxException {
        String value;
        try {
            value = StringArgumentType.getString(ctx, argName);
        } catch (IllegalArgumentException e) {
            value = argName;
        }
        return run(ctx, action, value);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String action, String arg) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        try {
            TeamActions.run(sq(ctx), player, action, arg);
            return 1;
        } catch (TeamException e) {
            ctx.getSource().sendFailure(e.component());
            return 0;
        }
    }

    private static int message(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerQuests sq = sq(ctx);
        Team party = sq.teams().partyOf(player.getUUID());
        if (party == null) {
            ctx.getSource().sendFailure(Component.translatable("corerpg_quests.team.error.not_in_party"));
            return 0;
        }
        TeamActions.tell(sq, party, Component.empty().append(player.getDisplayName())
                .append(Component.literal(": " + StringArgumentType.getString(ctx, "message")).withStyle(ChatFormatting.WHITE)));
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String query) throws CommandSyntaxException {
        ServerQuests sq = sq(ctx);
        Team team = query == null ? sq.teamOf(ctx.getSource().getPlayerOrException()) : sq.teams().find(query);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("corerpg_quests.team.error.team_not_found", query));
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("— ").withStyle(ChatFormatting.DARK_GRAY).append(team.describe())
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("corerpg_quests.team.type." + team.type().getSerializedName()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)), false);
        if (!team.description().isEmpty()) {
            src.sendSuccess(() -> Component.literal("  " + team.description()).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY), false);
        }
        for (Map.Entry<UUID, TeamRank> e : team.ranks().entrySet()) {
            boolean online = sq.server().getPlayerList().getPlayer(e.getKey()) != null;
            MutableComponent line = Component.literal("  " + (online ? "● " : "○ ")).withStyle(online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(Component.literal(team.playerName(e.getKey())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(e.getValue().displayName());
            src.sendSuccess(() -> line, false);
        }
        if (!team.stages().isEmpty()) {
            src.sendSuccess(() -> Component.translatable("corerpg_quests.team.stages", String.join(", ", team.stages())).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerQuests sq = sq(ctx);
        int n = 0;
        for (Team team : sq.teams().all()) {
            if (team.type() == TeamType.PLAYER) {
                continue;
            }
            n++;
            ctx.getSource().sendSuccess(() -> Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY).append(team.describe())
                    .append(Component.literal(" · " + team.members().size()).withStyle(ChatFormatting.GRAY)), false);
        }
        if (n == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.team.none"), false);
        }
        return n;
    }

    private static int serverCreate(CommandContext<CommandSourceStack> ctx) {
        try {
            Team team = sq(ctx).teams().createServerTeam(StringArgumentType.getString(ctx, "name"));
            ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.team.server_created", team.describe()), true);
            return 1;
        } catch (TeamException e) {
            ctx.getSource().sendFailure(e.component());
            return 0;
        }
    }

    private static int serverDelete(CommandContext<CommandSourceStack> ctx) {
        ServerQuests sq = sq(ctx);
        Team team = sq.teams().find(StringArgumentType.getString(ctx, "team"));
        try {
            if (team == null) {
                throw new TeamException("corerpg_quests.team.error.team_not_found", StringArgumentType.getString(ctx, "team"));
            }
            sq.teams().deleteServerTeam(team);
            ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.team.server_deleted", team.name()), true);
            return 1;
        } catch (TeamException e) {
            ctx.getSource().sendFailure(e.component());
            return 0;
        }
    }

    private static int forceJoin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerQuests sq = sq(ctx);
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Team team = sq.teams().find(StringArgumentType.getString(ctx, "team"));
        try {
            if (team == null) {
                throw new TeamException("corerpg_quests.team.error.team_not_found", StringArgumentType.getString(ctx, "team"));
            }
            sq.teams().forceJoin(team, player.getUUID(), player.getGameProfile().getName());
            ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.team.force_joined", player.getDisplayName(), team.displayName()), true);
            return 1;
        } catch (TeamException e) {
            ctx.getSource().sendFailure(e.component());
            return 0;
        }
    }
}
