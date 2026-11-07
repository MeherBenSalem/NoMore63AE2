package dev.nightbeam.corerpg.quests.filter;

import dev.nightbeam.corerpg.quests.CoreQuests;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Parsed filters by expression. A broken expression matches nothing (and is logged once). */
public final class FilterCache {
    private static final Map<String, ItemFilter> CACHE = new ConcurrentHashMap<>();
    private static final int MAX = 4096;

    private FilterCache() {
    }

    public static ItemFilter get(String expression) {
        ItemFilter cached = CACHE.get(expression);
        if (cached != null) {
            return cached;
        }
        ItemFilter filter;
        try {
            filter = FilterParser.parse(expression);
        } catch (FilterException e) {
            CoreQuests.LOG.warn("Invalid item filter '{}': {}", expression, e.getMessage());
            filter = Filters.NONE;
        }
        if (CACHE.size() > MAX) {
            CACHE.clear();
        }
        CACHE.put(expression, filter);
        return filter;
    }

    /** Tags and custom filters can change on reload. */
    public static void clear() {
        CACHE.clear();
    }
}
