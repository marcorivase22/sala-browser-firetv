package org.salabrowser.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class HistoryStore {
    private static final String PREFS = "watch_history";
    private static final String KEY = "entries";
    private static final String WATCH_TIME_PREFIX = "watch_time:";
    private static final int MAX_ENTRIES = 30;

    private final SharedPreferences preferences;

    HistoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized List<WatchEntry> all() {
        List<WatchEntry> entries = new ArrayList<>();
        String raw = preferences.getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                WatchEntry entry = WatchEntry.fromJson(array.getJSONObject(i));
                if (!entry.url.isEmpty()) {
                    entries.add(entry);
                }
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY).apply();
        }
        Collections.sort(entries, Comparator.comparingLong((WatchEntry item) -> item.updatedAt).reversed());
        return entries;
    }

    synchronized void save(String url, String title) {
        if (url == null || url.isEmpty()) {
            return;
        }

        List<WatchEntry> entries = all();
        List<WatchEntry> updatedEntries = new ArrayList<>();
        updatedEntries.add(new WatchEntry(url, cleanTitle(title), System.currentTimeMillis()));
        for (WatchEntry entry : entries) {
            if (!entry.url.equals(url)) {
                updatedEntries.add(entry);
            }
        }

        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(updatedEntries.size(), MAX_ENTRIES); i++) {
            try {
                array.put(updatedEntries.get(i).toJson());
            } catch (JSONException ignored) {
                // This entry is skipped while preserving the rest of the history.
            }
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }

    synchronized void clear() {
        preferences.edit().clear().apply();
    }

    synchronized long addWatchTime(String url, long elapsedMs) {
        if (url == null || url.isEmpty() || elapsedMs <= 0) {
            return 0;
        }
        String key = WATCH_TIME_PREFIX + url;
        long total = preferences.getLong(key, 0) + elapsedMs;
        preferences.edit().putLong(key, total).apply();
        return total;
    }

    private String cleanTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Seguir viendo";
        }
        return title.replace("FMovies", "").replace("|", "").trim();
    }
}
