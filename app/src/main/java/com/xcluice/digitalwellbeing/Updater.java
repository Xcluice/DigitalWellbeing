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

    /** Newest published build (highest build-N tag with an APK), or null. */
    static Info fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://api.github.com/repos/Xcluice/DigitalWellbeing/releases?per_page=20").openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "DigitalWellbeing");
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        JSONArray arr = new JSONArray(bo.toString("UTF-8"));
        Info best = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.getJSONObject(i);
            String tag = r.optString("tag_name");
            if (!tag.startsWith("build-")) continue;
            int num;
            try { num = Integer.parseInt(tag.substring(6)); } catch (Exception e) { continue; }
            if (best != null && num <= best.build) continue;
            JSONArray as = r.optJSONArray("assets");
            String u = null;
            if (as != null) {
                for (int j = 0; j < as.length(); j++) {
                    JSONObject a = as.getJSONObject(j);
                    if (a.optString("name").endsWith(".apk")) u = a.optString("browser_download_url");
                }
            }
            if (u != null) { best = new Info(); best.build = num; best.url = u; }
        }
        return best;
    }

    @SuppressWarnings("deprecation")
    static int installed(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; }
        catch (Exception e) { return 0; }
    }
}
