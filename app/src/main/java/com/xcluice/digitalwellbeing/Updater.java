package com.xcluice.digitalwellbeing;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Updater {
    private Updater() {}

    static class Info { int build; String url; }

    /** Newest published build, or null. Uses GitHub's own "latest release" (the releases list can come back stale). */
    static Info fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://api.github.com/repos/Xcluice/DigitalWellbeing/releases/latest").openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "DigitalWellbeing");
        c.setRequestProperty("Cache-Control", "no-cache");
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        JSONObject r = new JSONObject(bo.toString("UTF-8"));
        String tag = r.optString("tag_name");
        if (!tag.startsWith("build-")) return null;
        Info i = new Info();
        i.build = Integer.parseInt(tag.substring(6));
        JSONArray as = r.optJSONArray("assets");
        if (as != null) {
            for (int j = 0; j < as.length(); j++) {
                JSONObject a = as.getJSONObject(j);
                if (a.optString("name").endsWith(".apk")) i.url = a.optString("browser_download_url");
            }
        }
        return i.url == null ? null : i;
    }

    @SuppressWarnings("deprecation")
    static int installed(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; }
        catch (Exception e) { return 0; }
    }
}
