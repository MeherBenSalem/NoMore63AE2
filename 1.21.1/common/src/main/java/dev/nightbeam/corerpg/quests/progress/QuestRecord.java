package dev.nightbeam.corerpg.quests.progress;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.util.JsonUtil;

/** A team's history with one quest. Times are epoch milliseconds; 0 means "not yet". */
public final class QuestRecord {
    public long started;
    public long completed;
    public int timesCompleted;
    public long cooldownUntil;

    public boolean isCompleted() {
        return completed > 0;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (started > 0) {
            json.addProperty("s", started);
        }
        if (completed > 0) {
            json.addProperty("c", completed);
        }
        if (timesCompleted > 0) {
            json.addProperty("n", timesCompleted);
        }
        if (cooldownUntil > 0) {
            json.addProperty("cd", cooldownUntil);
        }
        return json;
    }

    static QuestRecord fromJson(JsonObject json) {
        QuestRecord r = new QuestRecord();
        r.started = JsonUtil.getLong(json, "s", 0);
        r.completed = JsonUtil.getLong(json, "c", 0);
        r.timesCompleted = JsonUtil.getInt(json, "n", 0);
        r.cooldownUntil = JsonUtil.getLong(json, "cd", 0);
        return r;
    }
}
