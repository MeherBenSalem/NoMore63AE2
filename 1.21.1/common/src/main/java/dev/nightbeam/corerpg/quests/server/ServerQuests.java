package dev.nightbeam.corerpg.quests.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.api.CoreQuestsEvents;
import dev.nightbeam.corerpg.quests.book.BookEdits;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import dev.nightbeam.corerpg.quests.filter.FilterCache;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.progress.QuestLogic;
import dev.nightbeam.corerpg.quests.progress.TeamProgress;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.team.TeamManager;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import dev.nightbeam.corerpg.quests.vein.VeinMining;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the quest engine holds while a server runs: the book, teams, progress and who is editing. Loader modules
 * forward server lifecycle, player and tick events here.
 */
public final class ServerQuests implements TeamManager.Listener {
    private static ServerQuests instance;

    private final MinecraftServer server;
    private final JsonCtx ctx;
    private final Path bookDir;
    private final WorldStorage storage;
    private final TeamManager teams = new TeamManager();
    private final Map<UUID, TeamProgress> progress = new HashMap<>();
    private final Set<UUID> receivedBook = new HashSet<>();
    private final Set<UUID> editors = new HashSet<>();
    private final Set<UUID> syncQueue = new LinkedHashSet<>();
    private final QuestEngine engine;
    private QuestBook book;
    private boolean bookDirty;
    private int bookSaveDelay;
    private boolean playersDirty;

    private ServerQuests(MinecraftServer server) {
        this.server = server;
        this.ctx = new JsonCtx(server.registryAccess());
        this.bookDir = BookStorage.bookDir(CoreConfig.dir());
        this.storage = new WorldStorage(server.getWorldPath(LevelResource.ROOT).resolve("corerpg_quests"));
        this.engine = new QuestEngine(this);
        teams.setListener(this);
    }

    public static @Nullable ServerQuests getOrNull() {
        return instance;
    }

    public static ServerQuests get() {
        if (instance == null) {
            throw new IllegalStateException("The quest engine is not running");
        }
        return instance;
    }

    // ---- Lifecycle ----

    public static void onServerStarting(MinecraftServer server) {
        CoreConfig.loadServer();
        FilterCache.clear();
        ServerQuests sq = new ServerQuests(server);
        sq.book = BookStorage.load(sq.bookDir, sq.ctx);
        sq.storage.loadTeams(sq.teams);
        sq.storage.loadProgress(sq.progress);
        sq.storage.loadPlayers(sq.receivedBook);
        instance = sq;
        CoreQuests.LOG.info("Quest engine started: {} teams, {} progress records", sq.teams.all().size(), sq.progress.size());
    }

    public static void onServerStopped() {
        if (instance != null) {
            instance.save();
            instance = null;
        }
        VeinMining.reset();
    }

    /** Called on every world save (autosave and shutdown). */
    public void save() {
        if (bookDirty) {
            BookStorage.save(book, bookDir, ctx);
            bookDirty = false;
        }
        storage.save(teams, progress, receivedBook, playersDirty);
        playersDirty = false;
    }

    public void tick() {
        engine.tick();
        if (!syncQueue.isEmpty()) {
            for (UUID teamId : syncQueue) {
                Team team = teams.get(teamId);
                if (team != null) {
                    for (ServerPlayer player : team.onlineMembers(server)) {
                        sendState(player);
                    }
                }
            }
            syncQueue.clear();
        }
        if (bookDirty && --bookSaveDelay <= 0) {
            BookStorage.save(book, bookDir, ctx);
            bookDirty = false;
        }
    }

    // ---- Players ----

    public void onPlayerJoin(ServerPlayer player) {
        Team team = teamOf(player);
        send(player, new Payloads.SyncConfig(JsonUtil.COMPACT.toJson(CoreConfig.SERVER.spec.toJson())));
        sendBook(player);
        sendState(player);
        if (book.giveBookOnFirstJoin && receivedBook.add(player.getUUID())) {
            playersDirty = true;
            ItemStack stack = new ItemStack(ModRegistry.QUEST_BOOK.get());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        engine.recheckAll(player);
        engine.autoclaimPending(player);
        CoreQuests.LOG.debug("{} joined with team {}", player.getGameProfile().getName(), team.name());
    }

    public void onPlayerLeave(ServerPlayer player) {
        editors.remove(player.getUUID());
        VeinMining.onLeave(player);
    }

    public Team teamOf(ServerPlayer player) {
        return teams.activeTeam(player.getUUID(), player.getGameProfile().getName());
    }

    public TeamProgress progress(Team team) {
        return progress.computeIfAbsent(team.id(), TeamProgress::new);
    }

    public TeamProgress progressOf(ServerPlayer player) {
        return progress(teamOf(player));
    }

    public QuestLogic logic(Team team) {
        return new QuestLogic(book, progress(team));
    }

    public QuestLogic logicOf(ServerPlayer player) {
        return logic(teamOf(player));
    }

    // ---- Accessors ----

    public MinecraftServer server() {
        return server;
    }

    public QuestBook book() {
        return book;
    }

    public JsonCtx ctx() {
        return ctx;
    }

    public TeamManager teams() {
        return teams;
    }

    public QuestEngine engine() {
        return engine;
    }

    public Map<UUID, TeamProgress> allProgress() {
        return progress;
    }

    // ---- Editing ----

    public boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(CoreConfig.SERVER.editorPermissionLevel.get());
    }

    public boolean isEditing(ServerPlayer player) {
        return editors.contains(player.getUUID());
    }

    public void setEditing(ServerPlayer player, boolean editing) {
        if (editing && canEdit(player)) {
            editors.add(player.getUUID());
            send(player, new Payloads.EditorData(JsonUtil.gzip(JsonUtil.COMPACT.toJson(EditorDataBuilder.build(server)))));
        } else {
            editors.remove(player.getUUID());
        }
        sendState(player);
    }

    /** Applies an edit (already validated for permission), saves soon, and replays it on every client. */
    public @Nullable String applyEdit(JsonObject op) {
        BookEdits.assignIds(book, op);
        String error = BookEdits.apply(book, op, ctx);
        if (error != null) {
            return error;
        }
        book.version++;
        markBookDirty();
        byte[] data = JsonUtil.gzip(JsonUtil.COMPACT.toJson(op));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, new Payloads.BookEdit(data));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            engine.recheckAll(player);
        }
        return null;
    }

    public void markBookDirty() {
        bookDirty = true;
        bookSaveDelay = 20;
    }

    /** Re-reads the book from disk and re-syncs everyone. */
    public void reloadBook() {
        CoreConfig.loadServer();
        FilterCache.clear();
        book = BookStorage.load(bookDir, ctx);
        bookDirty = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, new Payloads.SyncConfig(JsonUtil.COMPACT.toJson(CoreConfig.SERVER.spec.toJson())));
            sendBook(player);
            sendState(player);
            engine.recheckAll(player);
        }
    }

    public Path bookDir() {
        return bookDir;
    }

    /** Swaps in a book without reading or writing any files. Only for game tests. */
    public void replaceBookForTesting(QuestBook replacement) {
        replacement.rebuildIndex();
        this.book = replacement;
        this.bookDirty = false;
    }

    // ---- Sync ----

    public void send(ServerPlayer player, CustomPacketPayload payload) {
        if (Services.PLATFORM.canSendToPlayer(player, payload.type())) {
            Services.PLATFORM.sendToPlayer(player, payload);
        }
    }

    public void sendBook(ServerPlayer player) {
        send(player, new Payloads.SyncBook(JsonUtil.gzip(JsonUtil.COMPACT.toJson(book.toJson(ctx)))));
    }

    /** Queues a state sync for every online member of the team (sent once at the end of the tick). */
    public void markSync(Team team) {
        syncQueue.add(team.id());
    }

    public void sendState(ServerPlayer player) {
        Team team = teamOf(player);
        JsonObject json = new JsonObject();
        json.addProperty("self", player.getStringUUID());
        json.add("team", team.toClientJson());
        json.add("progress", progress(team).toJson());
        json.addProperty("editing", isEditing(player));
        json.addProperty("can_edit", canEdit(player));
        JsonArray invites = new JsonArray();
        for (Team invite : teams.invitesFor(player.getUUID())) {
            JsonObject o = new JsonObject();
            o.addProperty("id", invite.id().toString());
            o.addProperty("name", invite.name());
            o.addProperty("color", invite.color());
            invites.add(o);
        }
        json.add("invites", invites);
        JsonArray online = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", p.getStringUUID());
            o.addProperty("name", p.getGameProfile().getName());
            online.add(o);
        }
        json.add("online", online);
        JsonArray open = new JsonArray();
        for (Team t : teams.all()) {
            if (t.freeToJoin() && t.type() == dev.nightbeam.corerpg.quests.team.TeamType.PARTY) {
                JsonObject o = new JsonObject();
                o.addProperty("id", t.id().toString());
                o.addProperty("name", t.name());
                o.addProperty("color", t.color());
                o.addProperty("members", t.members().size());
                open.add(o);
            }
        }
        json.add("open_parties", open);
        json.addProperty("team_chat", teams.isTeamChat(player.getUUID()));
        send(player, new Payloads.SyncState(JsonUtil.gzip(JsonUtil.COMPACT.toJson(json))));
    }

    public void message(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }

    // ---- TeamManager.Listener ----

    @Override
    public void onPlayerTeamChanged(UUID playerId, @Nullable Team from, Team to) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (from != null) {
            markSync(from);
        }
        markSync(to);
        if (player != null) {
            CoreQuestsEvents.PLAYER_TEAM_CHANGED.invoker().onTeamChanged(player, from, to);
            sendState(player);
            engine.recheckAll(player);
        }
    }

    @Override
    public void onTeamUpdated(Team team) {
        markSync(team);
        for (UUID invited : team.withRank(dev.nightbeam.corerpg.quests.team.TeamRank.INVITED)) {
            ServerPlayer player = server.getPlayerList().getPlayer(invited);
            if (player != null) {
                sendState(player);
            }
        }
    }

    @Override
    public void onStageChanged(Team team, String stage, boolean added) {
        CoreQuestsEvents.STAGE_CHANGED.invoker().onStageChanged(team, stage, added);
        for (ServerPlayer player : team.onlineMembers(server)) {
            engine.trigger(player, new TriggerEvent.Stage(stage, added));
        }
    }

    @Override
    public void onTeamDeleted(Team team) {
        progress.remove(team.id());
        storage.deleteProgress(team.id());
    }
}
