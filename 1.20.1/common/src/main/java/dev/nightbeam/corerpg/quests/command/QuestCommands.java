package dev.nightbeam.corerpg.quests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.progress.TeamProgress;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import dev.nightbeam.corerpg.quests.server.BookStorage;
import dev.nightbeam.corerpg.quests.server.ServerQuests;
import dev.nightbeam.corerpg.quests.team.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** /corequests (alias /cq): opening the book, editing, admin progress tools, stages and custom task triggers. */
public final class QuestCommands {
    private static final SimpleCommandExceptionType NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("corerpg_quests.command.not_running"));
    private static final SimpleCommandExceptionType NO_OBJECT = new SimpleCommandExceptionType(Component.translatable("corerpg_quests.command.no_object"));

    private static final SuggestionProvider<CommandSourceStack> OBJECTS = (ctx, builder) -> {
        ServerQuests sq = ServerQuests.getOrNull();
        List<String> ids = new ArrayList<>();
        ids.add("book");
        if (sq != null) {
            for (Chapter c : sq.book().chapters) {
                ids.add(c.id());
                c.quests.forEach(q -> ids.add(q.id()));
            }
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> STAGES = (ctx, builder) -> {
        ServerQuests sq = ServerQuests.getOrNull();
        Set<String> stages = new LinkedHashSet<>();
        if (sq != null) {
            sq.teams().all().forEach(t -> stages.addAll(t.stages()));
        }
        return SharedSuggestionProvider.suggest(stages, builder);
    };

    private QuestCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        var root = dispatcher.register(build("corequests"));
        dispatcher.register(Commands.literal("cq").redirect(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .then(Commands.literal("open").executes(ctx -> open(ctx.getSource().getPlayerOrException(), ""))
                        .then(Commands.argument("object", StringArgumentType.greedyString()).suggests(OBJECTS)
                                .executes(ctx -> open(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "object")))))
                .then(Commands.literal("emergency_items").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return sq().engine().giveEmergencyItems(player) ? 1 : 0;
                }))
                .then(Commands.literal("editing_mode").requires(QuestCommands::isEditor)
                        .executes(ctx -> editing(ctx.getSource().getPlayerOrException(), !sq().isEditing(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> editing(ctx.getSource().getPlayerOrException(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("reload").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    ServerQuests sq = sq();
                    sq.reloadBook();
                    int quests = sq.book().allQuests().size();
                    ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.command.reloaded", quests), true);
                    return 1;
                }))
                .then(Commands.literal("export").requires(src -> src.hasPermission(2)).executes(QuestCommands::export))
                .then(Commands.literal("give_book").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                ItemStack stack = new ItemStack(ModRegistry.QUEST_BOOK.get());
                                if (!p.getInventory().add(stack)) {
                                    p.drop(stack, false);
                                }
                            }
                            return 1;
                        })))
                .then(Commands.literal("complete").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("object", StringArgumentType.greedyString()).suggests(OBJECTS)
                                        .executes(ctx -> changeProgress(ctx, true)))))
                .then(Commands.literal("reset").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("object", StringArgumentType.greedyString()).suggests(OBJECTS)
                                        .executes(ctx -> changeProgress(ctx, false)))))
                .then(Commands.literal("lock").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("locked", BoolArgumentType.bool()).executes(ctx -> flag(ctx, true)))))
                .then(Commands.literal("block_rewards").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("blocked", BoolArgumentType.bool()).executes(ctx -> flag(ctx, false)))))
                .then(Commands.literal("stage").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("add").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("stage", StringArgumentType.string()).suggests(STAGES).executes(ctx -> stage(ctx, true)))))
                        .then(Commands.literal("remove").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("stage", StringArgumentType.string()).suggests(STAGES).executes(ctx -> stage(ctx, false)))))
                        .then(Commands.literal("list").then(Commands.argument("target", EntityArgument.player()).executes(QuestCommands::listStages))))
                .then(Commands.literal("custom").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(ctx -> custom(ctx, 1))
                                        .then(Commands.argument("amount", LongArgumentType.longArg()).executes(ctx -> custom(ctx, LongArgumentType.getLong(ctx, "amount")))))))
                .then(Commands.literal("info").requires(src -> src.hasPermission(2)).executes(QuestCommands::info));
    }

    private static boolean isEditor(CommandSourceStack source) {
        return source.hasPermission(CoreConfig.SERVER.editorPermissionLevel.get());
    }

    private static ServerQuests sq() throws CommandSyntaxException {
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq == null) {
            throw NOT_RUNNING.create();
        }
        return sq;
    }

    private static int open(ServerPlayer player, String focus) throws CommandSyntaxException {
        sq().send(player, new Payloads.OpenBook(focus));
        return 1;
    }

    private static int editing(ServerPlayer player, boolean enabled) throws CommandSyntaxException {
        sq().setEditing(player, enabled);
        player.sendSystemMessage(Component.translatable(enabled ? "corerpg_quests.command.editing_on" : "corerpg_quests.command.editing_off"));
        return 1;
    }

    private static QuestObject object(String id) throws CommandSyntaxException {
        QuestObject object = id.equals("book") ? sq().book() : sq().book().get(id);
        if (object == null) {
            throw NO_OBJECT.create();
        }
        return object;
    }

    private static Collection<Team> teams(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        Set<Team> teams = new LinkedHashSet<>();
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, arg)) {
            teams.add(sq().teamOf(p));
        }
        return teams;
    }

    private static int changeProgress(CommandContext<CommandSourceStack> ctx, boolean complete) throws CommandSyntaxException {
        QuestObject object = object(StringArgumentType.getString(ctx, "object"));
        int n = 0;
        for (Team team : teams(ctx, "targets")) {
            if (complete) {
                sq().engine().forceComplete(team, object, null);
            } else {
                sq().engine().reset(team, object);
            }
            n++;
        }
        int teams = n;
        ctx.getSource().sendSuccess(() -> Component.translatable(complete ? "corerpg_quests.command.completed" : "corerpg_quests.command.reset",
                object.displayTitle(), teams), true);
        return n;
    }

    private static int flag(CommandContext<CommandSourceStack> ctx, boolean lock) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(ctx, lock ? "locked" : "blocked");
        for (Team team : teams(ctx, "targets")) {
            TeamProgress p = sq().progress(team);
            if (lock) {
                p.locked = value;
            } else {
                p.rewardsBlocked = value;
            }
            p.markDirty();
            sq().markSync(team);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(lock ? "corerpg_quests.command.locked" : "corerpg_quests.command.rewards_blocked", value), true);
        return 1;
    }

    private static int stage(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        String stage = StringArgumentType.getString(ctx, "stage");
        int n = 0;
        for (Team team : teams(ctx, "targets")) {
            if (add ? sq().teams().addStage(team, stage) : sq().teams().removeStage(team, stage)) {
                n++;
            }
        }
        int changed = n;
        ctx.getSource().sendSuccess(() -> Component.translatable(add ? "corerpg_quests.command.stage_added" : "corerpg_quests.command.stage_removed", stage, changed), true);
        return n;
    }

    private static int listStages(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Team team = sq().teamOf(EntityArgument.getPlayer(ctx, "target"));
        if (team.stages().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.command.no_stages", team.displayName()), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.command.stages", team.displayName(),
                    Component.literal(String.join(", ", team.stages())).withStyle(ChatFormatting.YELLOW)), false);
        }
        return team.stages().size();
    }

    private static int custom(CommandContext<CommandSourceStack> ctx, long amount) throws CommandSyntaxException {
        String key = StringArgumentType.getString(ctx, "key");
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
            sq().engine().customTrigger(p, key, amount);
        }
        return 1;
    }

    private static int export(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Path file = CoreConfig.dir().resolve("export").resolve("book_" + System.currentTimeMillis() + ".json");
        try {
            BookStorage.exportSingleFile(sq().book(), file, sq().ctx());
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.command.exported", file.toString()), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerQuests sq = sq();
        int quests = sq.book().allQuests().size();
        int tasks = 0;
        int rewards = 0;
        for (Quest q : sq.book().allQuests()) {
            tasks += q.tasks.size();
            rewards += q.rewards.size();
        }
        int t = tasks;
        int r = rewards;
        ctx.getSource().sendSuccess(() -> Component.translatable("corerpg_quests.command.info", sq.book().chapters.size(), quests, t, r,
                sq.book().tables.size(), sq.teams().all().size()), false);
        return quests;
    }
}
