package org.salabrowser.app;

import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class RequestBlocker {
    private static final Set<String> BLOCKED_HOST_SUFFIXES = new HashSet<>(Arrays.asList(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adnxs.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "exoclick.com",
            "trafficjunky.com",
            "onclickperformance.com",
            "onclickmax.com",
            "juicyads.com",
            "hilltopads.net"
    ));

    private static final Set<String> BLOCKED_PATH_MARKERS = new HashSet<>(Arrays.asList(
            "/popunder",
            "/popup",
            "/interstitial",
            "/vast.",
            "/vast/",
            "adserver",
            "advertisement"
    ));

    boolean shouldBlock(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return false;
        }

        Uri uri = Uri.parse(rawUrl);
        String scheme = lower(uri.getScheme());
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return scheme.equals("intent") || scheme.equals("market");
        }

        String host = lower(uri.getHost());
        for (String suffix : BLOCKED_HOST_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }

        String url = rawUrl.toLowerCase(Locale.US);
        for (String marker : BLOCKED_PATH_MARKERS) {
            if (url.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
