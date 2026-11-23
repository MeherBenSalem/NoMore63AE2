package dev.nightbeam.corerpg.quests.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.progress.TeamProgress;
import dev.nightbeam.corerpg.quests.team.TeamManager;
import dev.nightbeam.corerpg.quests.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Per-world data as readable JSON in {@code <world>/corerpg_quests/}: teams.json, players.json and one progress
 * file per team. Only changed files are rewritten, on every world save.
 */
public final class WorldStorage {
    private final Path dir;

    public WorldStorage(Path dir) {
        this.dir = dir;
    }

    public void loadTeams(TeamManager teams) {
        Path file = dir.resolve("teams.json");
        if (Files.exists(file)) {
            try {
                teams.fromJson(JsonUtil.readObject(file));
            } catch (IOException | RuntimeException e) {
                CoreQuests.LOG.error("Could not read {}", file, e);
            }
        }
    }

    public void loadPlayers(Set<UUID> known) {
        Path file = dir.resolve("players.json");
        if (Files.exists(file)) {
            try {
                JsonObject json = JsonUtil.readObject(file);
                if (json.has("received_book")) {
                    json.getAsJsonArray("received_book").forEach(e -> known.add(UUID.fromString(e.getAsString())));
                }
            } catch (IOException | RuntimeException e) {
                CoreQuests.LOG.error("Could not read {}", file, e);
            }
        }
    }

    public void loadProgress(Map<UUID, TeamProgress> progress) {
        Path progressDir = dir.resolve("progress");
        if (!Files.isDirectory(progressDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(progressDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                try {
                    TeamProgress p = TeamProgress.fromJson(JsonUtil.readObject(file));
                    progress.put(p.teamId(), p);
                } catch (IOException | RuntimeException e) {
                    CoreQuests.LOG.error("Could not read progress file {}", file, e);
                }
            }
        } catch (IOException e) {
            CoreQuests.LOG.error("Could not list {}", progressDir, e);
        }
    }

    public void save(TeamManager teams, Map<UUID, TeamProgress> progress, Set<UUID> known, boolean playersDirty) {
        try {
            if (teams.isDirty()) {
                JsonUtil.write(dir.resolve("teams.json"), teams.toJson());
                teams.clearDirty();
            }
            if (playersDirty) {
                JsonObject json = new JsonObject();
                JsonArray array = new JsonArray();
                known.forEach(u -> array.add(u.toString()));
                json.add("received_book", array);
                JsonUtil.write(dir.resolve("players.json"), json);
            }
            for (TeamProgress p : progress.values()) {
                if (p.isDirty()) {
                    JsonUtil.write(dir.resolve("progress").resolve(p.teamId() + ".json"), p.toJson());
                    p.clearDirty();
                }
            }
        } catch (IOException e) {
            CoreQuests.LOG.error("Could not save quest data to {}", dir, e);
        }
    }

    public void deleteProgress(UUID teamId) {
        try {
            Files.deleteIfExists(dir.resolve("progress").resolve(teamId + ".json"));
        } catch (IOException e) {
            CoreQuests.LOG.warn("Could not delete progress of team {}", teamId, e);
        }
    }
}
