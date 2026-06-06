package org.salabrowser.app;

import org.json.JSONException;
import org.json.JSONObject;

final class WatchEntry {
    final String url;
    final String title;
    final long updatedAt;

    WatchEntry(String url, String title, long updatedAt) {
        this.url = url;
        this.title = title;
        this.updatedAt = updatedAt;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("url", url);
        json.put("title", title);
        json.put("updatedAt", updatedAt);
        return json;
    }

    static WatchEntry fromJson(JSONObject json) {
        return new WatchEntry(
                json.optString("url"),
                json.optString("title", "Sin título"),
                json.optLong("updatedAt")
        );
    }
}
