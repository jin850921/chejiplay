package com.cheji.play;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared model, network, and styling helpers (no AndroidX on purpose: car head units). */
public final class Core {
    private Core() {
    }

    public static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ---------------------------------------------------------------- modules

    public static final class Mod {
        public final String id, surface, impl, pkg, prefix, api, site, pages, repo, releases, feature, note;
        public final int tileLight, tileDark, glyphLight, glyphDark, glyphRes;
        /** true＝圖檔本身就帶官方色，不能再用 SRC_IN 壓成單色；false＝隨主題反白的單色商標。 */
        public final boolean brandGlyph;

        Mod(String id, String surface, String impl, String pkg, String prefix, String api, String site,
            String pages, String repo, String releases, String feature, String note,
            int tileLight, int tileDark, int glyphLight, int glyphDark, int glyphRes, boolean brandGlyph) {
            this.id = id;
            this.surface = surface;
            this.impl = impl;
            this.pkg = pkg;
            this.prefix = prefix;
            this.api = api;
            this.site = site;
            this.pages = pages;
            this.repo = repo;
            this.releases = releases;
            this.feature = feature;
            this.note = note;
            this.tileLight = tileLight;
            this.tileDark = tileDark;
            this.glyphLight = glyphLight;
            this.glyphDark = glyphDark;
            this.glyphRes = glyphRes;
            this.brandGlyph = brandGlyph;
        }

        public int tile(boolean dark) {
            return dark ? tileDark : tileLight;
        }

        public int glyph(boolean dark) {
            return dark ? glyphDark : glyphLight;
        }
    }

    /** 品牌 navy：DiAuto 色塊與左上角 logo 色塊共用同一對值，換主題只翻深淺不換色系。 */
    public static final int BRAND_NAVY_LIGHT = 0xFF0F2B45, BRAND_NAVY_DARK = 0xFF1A4470;

    public static final Mod DIPLAY = new Mod("diplay", "CarPlay", "DiPlay", "com.shihab.diplay", "DiPlay-",
            "https://api.github.com/repos/shihabal3amri/DiPlay/releases",
            "https://diplay.org/zh-hant/",
            "https://shihabal3amri.github.io/DiPlay/",
            "https://github.com/shihabal3amri/DiPlay",
            "https://github.com/shihabal3amri/DiPlay/releases",
            "在 Android 車機上接收 iPhone 的 CarPlay，支援有線與無線連線。",
            "安裝在車機上，不是裝在 iPhone。公開預覽版，非 Apple 認證產品。",
            0xFF1D1D1F, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF1D1D1F, R.drawable.ic_carplay, false);

    public static final Mod DIAUTO = new Mod("diauto", "Android Auto", "DiAuto",
            "com.andrerinas.headunitrevived", "DiAuto-",
            "https://api.github.com/repos/shihabal3amri/DiAuto/releases",
            "https://shihabal3amri.github.io/DiAuto/",
            "https://shihabal3amri.github.io/DiAuto/",
            "https://github.com/shihabal3amri/DiAuto",
            "https://github.com/shihabal3amri/DiAuto/releases",
            "在 Android 車機上接收 Android Auto，把手機的地圖與音樂顯示在車螢幕上。",
            "需搭配 Android 手機使用（內建 Android Auto 支援），這不是 CarPlay。",
            // 商標固定官方藍＋白箭頭，所以隨主題換的是色塊而不是圖。
            // 底色不能再用品牌藍 #039BE5：實測它會讓三角整塊消失（對比 1.00）；
            // navy 在淺色（#0F2B45，對卡面 14.46／藍 4.70／白箭 12.81）與深色（#1A4470，對卡面 1.59、L* 差 14.7／藍 3.24／白箭 8.83）都站得住。
            BRAND_NAVY_LIGHT, BRAND_NAVY_DARK, 0xFF039BE5, 0xFF039BE5, R.drawable.ic_androidauto, true);

    public static final Mod[] MODS = {DIPLAY, DIAUTO};

    // ------------------------------------------------------------ self update

    /**
     * 一條「更新來源」＝ id＋說故事的名字＋清單位址＋檔名前綴＋備用網頁。
     * DiPlay／DiAuto 由 Mod 帶出（{@link #sourceOf}），本 App 自己用 {@link #SELF}，兩邊走同一條下載路。
     */
    public static final class Source {
        public final String id, impl, api, prefix, page;

        Source(String id, String impl, String api, String prefix, String page) {
            this.id = id;
            this.impl = impl;
            this.api = api;
            this.prefix = prefix;
            this.page = page;
        }
    }

    public static Source sourceOf(Mod m) {
        return new Source(m.id, m.impl, m.api, m.prefix, m.releases);
    }

    /**
     * 本 App 自己的發佈來源，由作者一個人的 repo 提供，所以寫死在這裡、不在設定頁開欄位。
     * id 是 Job／latest／errors 三張表的鍵；Release 資產檔名必須是 ASCII 前綴 chejiplay-，
     * 版號就是 tag（v1.0 → 1.0），跟 APK 自己的 versionName 用同一個 cmpVersion 比。
     */
    public static final String SELF_PKG = "com.cheji.play";
    public static final Source SELF = new Source("self", "車機Play",
            "https://api.github.com/repos/jin850921/chejiplay/releases",
            "chejiplay-",
            "https://github.com/jin850921/chejiplay/releases");

    // -------------------------------------------------------------- shortcuts

    /**
     * 主畫面快捷鈕：與 Mod 不同的是「只有開啟」——不下載、不查更新、不佔長按取消的語意。
     * Google Maps 由車機上的 Google Play 維護，DiAuto／DiPlay 的 GitHub 流程對它沒有意義。
     */
    public static final class Shortcut {
        public final String id, title, method, pkg, missing;
        public final int tileLight, tileDark, glyphLight, glyphDark, glyphRes;
        /** true＝圖檔自帶官方色（多色圖釘），不能 tint；false＝單色圖，隨主題反白。 */
        public final boolean colorGlyph;

        Shortcut(String id, String title, String method, String pkg, String missing,
                 int tileLight, int tileDark, int glyphLight, int glyphDark, int glyphRes, boolean colorGlyph) {
            this.id = id;
            this.title = title;
            this.method = method;
            this.pkg = pkg;
            this.missing = missing;
            this.tileLight = tileLight;
            this.tileDark = tileDark;
            this.glyphLight = glyphLight;
            this.glyphDark = glyphDark;
            this.glyphRes = glyphRes;
            this.colorGlyph = colorGlyph;
        }

        public int tile(boolean dark) {
            return dark ? tileDark : tileLight;
        }

        public int glyph(boolean dark) {
            return dark ? glyphDark : glyphLight;
        }

        /** pkg 是空字串 ＝這個位子還沒加入，點下去是開清單而不是開啟。 */
        public boolean isEmpty() {
            return pkg == null || pkg.length() == 0;
        }
    }

    public static final String MAPS_PKG = "com.google.android.apps.maps";

    // 這張卡的圖優先取車機上 Google Maps 自己的圖示（MainActivity.applyShortcutIcon），查不到才用下面這個內建圖釘。
    // 圖釘是官方多色（紅 3.48／藍 3.03／藍 3.83／綠 4.47／黃 8.00 對 #2A2E33），
    // 淺色用近黑才能五段都過 3:1；深色改 #E8EAED 是 Google 自己那張圖的底，
    // 黃段 1.42 是商標本身的特性（被紅綠包在中間），不是我們把它調淡。
    public static final Shortcut MAPS = new Shortcut("maps", "Google Maps", "直接開啟", MAPS_PKG,
            "這台車機還沒有 Google Maps。請用車機上的 Google Play 安裝，裝好後再點一次即可。",
            0xFF2A2E33, 0xFFE8EAED, 0, 0, R.drawable.ic_maps, true);

    /**
     * 自訂欄位的位子：還沒加入時是「＋ 添加○○」，加入後同一張卡換成那套 App 的名稱與圖示，
     * 所以加入前後是同一個位子，不會多出第二個入口。
     *
     * @param id 決定這張卡的身分（custom／custom2），長按選單與寫回設定都認它
     */
    public static Shortcut customSlot(String id, String pkg, String title, String emptyTitle, String emptyMethod) {
        if (pkg == null || pkg.length() == 0) {
            return new Shortcut(id, emptyTitle, emptyMethod, "",
                    "", 0xFFCBD6E2, 0xFF39434F, 0xFF2F6FB0, 0xFF6FA8E6, R.drawable.ic_add, false);
        }
        return new Shortcut(id, title, "直接開啟", pkg,
                "查不到這套 App（可能已從車機移除）。長按這張卡可以換一個或移除。",
                0xFFCBD6E2, 0xFF39434F, 0, 0, 0, false);
    }

    public static String appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static Drawable appIcon(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationIcon(pm.getApplicationInfo(pkg, 0));
        } catch (Exception e) {
            return null;
        }
    }

    // 排序偏好：導航／地圖／測速類排前面，因為這些最常被放進快捷鈕，跟第幾欄無關。
    private static final String[] NAV_HINTS = {"map", "maps", "navig", "waze", "here", "tomtom", "sygic",
            "garmin", "gps", "geo", "driving", "traffic", "radar", "speed", "alert",
            "導航", "地圖", "路線", "導路", "行車", "車機", "測速", "雷達", "安全訊號", "警示"};

    /** 車機上有主畫面圖示的 App（排除本 App），回 {名稱, 套件名}；導航／地圖類排前面方便找。 */
    public static String[][] launchableApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN);
        probe.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> hits;
        try {
            hits = pm.queryIntentActivities(probe, 0);
        } catch (Exception e) {
            return new String[0][];
        }
        if (hits == null) {
            return new String[0][];
        }
        List<String[]> out = new ArrayList<String[]>();
        Set<String> seen = new HashSet<String>();
        for (ResolveInfo ri : hits) {
            if (ri == null || ri.activityInfo == null || ri.activityInfo.packageName == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            CharSequence raw = ri.loadLabel(pm);
            out.add(new String[]{raw == null || raw.length() == 0 ? pkg : raw.toString(), pkg});
        }
        Collections.sort(out, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                int hint = navScore(b[1] + " " + b[0]) - navScore(a[1] + " " + a[0]);
                return hint != 0 ? hint : a[0].compareToIgnoreCase(b[0]);
            }
        });
        // 同名 App（車機上很常見）才補套件名，否則名稱後面不掛雜音。
        Map<String, Integer> dup = new HashMap<String, Integer>();
        for (String[] a : out) {
            Integer n = dup.get(a[0]);
            dup.put(a[0], n == null ? 1 : n + 1);
        }
        for (String[] a : out) {
            if (dup.get(a[0]) > 1) {
                a[0] = a[0] + "（" + a[1] + "）";
            }
        }
        return out.toArray(new String[out.size()][]);
    }

    private static int navScore(String s) {
        String low = s.toLowerCase(Locale.US);
        for (String hint : NAV_HINTS) {
            if (low.contains(hint)) {
                return 1;
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- release

    public static final class Rel {
        public final String version, url, fileName, page, publishedAt;
        public final long size;

        Rel(String version, String url, String fileName, String page, String publishedAt, long size) {
            this.version = version;
            this.url = url;
            this.fileName = fileName;
            this.page = page;
            this.publishedAt = publishedAt;
            this.size = size;
        }
    }

    /** Newest non-draft release carrying an APK (DiPlay ships its newest as a pre-release). */
    public static Rel latestRelease(Mod m) throws IOException {
        return latestRelease(m.api, m.prefix, m.releases);
    }

    /** 同一條解析路也給本 App 自己用：差別只有 api、檔名前綴與查不到 html_url 時的備用頁面。 */
    public static Rel latestRelease(String api, String prefix, String page) throws IOException {
        String body = Net.getText(api);
        JSONArray arr;
        try {
            arr = new JSONArray(body);
        } catch (Exception e) {
            throw new IOException("發佈清單格式無法解析");
        }
        Rel loose = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null || r.optBoolean("draft", false)) {
                continue;
            }
            JSONArray assets = r.optJSONArray("assets");
            if (assets == null) {
                continue;
            }
            String tag = trimV(r.optString("tag_name", ""));
            String when = r.optString("published_at", "");
            for (int j = 0; j < assets.length(); j++) {
                JSONObject a = assets.optJSONObject(j);
                if (a == null) {
                    continue;
                }
                String name = a.optString("name", "");
                String lower = name.toLowerCase();
                if (!lower.endsWith(".apk") || lower.contains("source")) {
                    continue;
                }
                String url = a.optString("browser_download_url", "");
                if (url.isEmpty()) {
                    continue;
                }
                Rel rel = new Rel(tag, url, name, r.optString("html_url", page), when, a.optLong("size", 0));
                if (name.startsWith(prefix)) {
                    return rel;
                }
                if (loose == null) {
                    loose = rel;
                }
            }
        }
        return loose;
    }

    public static String trimV(String tag) {
        return tag.regionMatches(true, 0, "v", 0, 1) ? tag.substring(1) : tag;
    }

    /** Numeric segment compare; returns >0 when b is newer than a. */
    public static int cmpVersion(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        String[] pa = a.split("[^0-9]+");
        String[] pb = b.split("[^0-9]+");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            long va = i < pa.length && pa[i].length() > 0 ? parse(pa[i]) : 0;
            long vb = i < pb.length && pb[i].length() > 0 ? parse(pb[i]) : 0;
            if (va != vb) {
                return va < vb ? -1 : 1;
            }
        }
        return 0;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 比對用：只取 versionName（拿不到才退回 versionCode 的文字），格式不准動，否則 `cmpVersion` 會誤判。 */
    public static String installedVersion(Context c, String pkg) {
        PackageInfo pi = pkgInfo(c, pkg);
        if (pi == null) {
            return null;
        }
        return pi.versionName == null ? String.valueOf(versionCodeOf(pi)) : pi.versionName;
    }

    /**
     * 顯示用：已安裝的 APK 兩個值都拿得到，所以一律写成 `version_name(version_code)`。
     * 只有 GitHub 那端（Release tag）沒有 code，那裡的版號維持原樣，不加括號。
     */
    public static String dispVersion(Context c, String pkg) {
        PackageInfo pi = pkgInfo(c, pkg);
        if (pi == null) {
            return null;
        }
        long code = versionCodeOf(pi);
        String name = pi.versionName == null ? String.valueOf(code) : pi.versionName;
        return code > 0 ? name + "(" + code + ")" : name;
    }

    private static PackageInfo pkgInfo(Context c, String pkg) {
        try {
            return c.getPackageManager().getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** 28 起 versionCode 是 long；車機可能是 24～27，所以兩條路都要留。 */
    private static long versionCodeOf(PackageInfo pi) {
        return android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
    }

    // ---------------------------------------------------------------- jobs

    public static final class Job {
        public final String modId;
        public final boolean[] cancelFlag = new boolean[1];
        public volatile boolean active;
        public volatile int percent;
        public volatile String text;

        Job(String modId) {
            this.modId = modId;
        }
    }

    public static final Map<String, Job> JOBS =
            Collections.synchronizedMap(new HashMap<String, Job>());

    private static Runnable hubListener;

    public static void setHubListener(Runnable r) {
        hubListener = r;
    }

    public static Job job(String modId) {
        synchronized (JOBS) {
            Job j = JOBS.get(modId);
            if (j == null) {
                j = new Job(modId);
                JOBS.put(modId, j);
            }
            return j;
        }
    }

    public static void ping() {
        Runnable r = hubListener;
        if (r != null) {
            MAIN.post(r);
        }
    }

    // ---------------------------------------------------------------- network

    public interface Progress {
        void onProgress(long done, long total);
    }

    public static final class Net {
        static final long MAX_TEXT = 4L * 1024 * 1024;
        static final long MAX_APK = 256L * 1024 * 1024;

        private static boolean allowed(String host) {
            String h = host.toLowerCase();
            return h.equals("github.com") || h.equals("api.github.com") || h.equals("codeload.github.com")
                    || h.equals("uploads.github.com") || h.equals("raw.githubusercontent.com")
                    || h.equals("objects.githubusercontent.com") || h.equals("release-assets.githubusercontent.com")
                    || h.endsWith(".githubusercontent.com") || h.endsWith(".github.io") || h.equals("diplay.org")
                    || h.endsWith(".diplay.org");
        }

        private static HttpURLConnection connect(String url) throws IOException {
            String cur = url;
            for (int hop = 0; hop < 6; hop++) {
                URL u = new URL(cur);
                if (!"https".equalsIgnoreCase(u.getProtocol())) {
                    throw new IOException("只接受 https 連線");
                }
                if (!allowed(u.getHost())) {
                    throw new IOException("不允許的網域：" + u.getHost());
                }
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "ChejiPlay/1.0");
                c.setRequestProperty("Accept", "*/*");
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null || loc.isEmpty()) {
                        throw new IOException("重新導向缺少位置");
                    }
                    cur = new URL(u, loc).toString();
                    continue;
                }
                if (code < 200 || code >= 300) {
                    c.disconnect();
                    if (code == 403 || code == 429) {
                        throw new IOException("GitHub 要求太頻繁（" + code + "），請稍後再試");
                    }
                    throw new IOException("伺服器回應 " + code);
                }
                return c;
            }
            throw new IOException("重新導向次數過多");
        }

        public static String getText(String url) throws IOException {
            HttpURLConnection c = connect(url);
            try {
                InputStream in = c.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_TEXT) {
                        throw new IOException("回應內容過大");
                    }
                    out.write(buf, 0, n);
                }
                in.close();
                return out.toString("UTF-8");
            } finally {
                c.disconnect();
            }
        }

        public static void download(String url, File dest, long expectSize, Progress cb, boolean[] cancelFlag)
                throws IOException {
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("無法建立下載目錄");
            }
            File tmp = new File(dest.getAbsolutePath() + ".part");
            HttpURLConnection c = connect(url);
            long total = c.getContentLengthLong();
            if (total <= 0) {
                total = expectSize;
            }
            if (total > MAX_APK) {
                c.disconnect();
                throw new IOException("檔案過大（" + humanSize(total) + "）");
            }
            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            long done = 0;
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelFlag != null && cancelFlag.length > 0 && cancelFlag[0]) {
                        throw new IOException("已取消");
                    }
                    done += n;
                    if (done > MAX_APK) {
                        throw new IOException("下載超過安全上限");
                    }
                    out.write(buf, 0, n);
                    if (cb != null) {
                        cb.onProgress(done, total);
                    }
                }
                out.flush();
            } catch (IOException e) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
                try {
                    out.close();
                } catch (Exception ignored) {
                }
                if (tmp.exists() && !tmp.delete()) {
                    tmp.deleteOnExit();
                }
                if (e.getMessage() == null || !e.getMessage().equals("已取消")) {
                    c.disconnect();
                    throw new IOException(readable(e));
                }
                c.disconnect();
                throw e;
            }
            in.close();
            out.close();
            c.disconnect();
            if (total > 0 && done < total) {
                if (tmp.exists()) {
                    tmp.delete();
                }
                throw new IOException("下載不完整");
            }
            if (dest.exists() && !dest.delete()) {
                throw new IOException("無法覆蓋舊檔案");
            }
            if (!tmp.renameTo(dest)) {
                throw new IOException("無法儲存檔案");
            }
        }

        static String readable(IOException e) {
            String m = e.getMessage();
            if (m == null || m.isEmpty()) {
                return e.getClass().getSimpleName();
            }
            if (m.contains("resolve host") || m.contains("Unable to resolve host")) {
                return "找不到伺服器，請確認車機已連網";
            }
            if (m.contains("failed to connect") || m.contains("ETIMEDOUT") || m.contains("ECONNREFUSED")) {
                return "連不上伺服器，請確認車機網路";
            }
            if (m.contains("SSLHandshake") || m.contains("certificate") || m.contains("CertPathValidator")) {
                return "憑證驗證失敗，請先更新車機系統時間";
            }
            return m;
        }
    }

    public static String humanSize(long b) {
        if (b <= 0) {
            return "未知大小";
        }
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024L * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", b / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
    }

    public static File apkDir(Context c) {
        File d = c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (d == null) {
            d = new File(c.getFilesDir(), "download");
        }
        if (!d.isDirectory()) {
            d.mkdirs();
        }
        return d;
    }

    // ---------------------------------------------------------------- styling

    public static final class Ui {
        private Ui() {
        }

        public static int dp(Context c, float v) {
            return (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                    c.getResources().getDisplayMetrics()) + 0.5f);
        }

        public static int color(Context c, int attrId) {
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(attrId, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                if (tv.resourceId != 0) {
                    return c.getResources().getColor(tv.resourceId);
                }
            }
            return Color.GRAY;
        }

        /** 進度條的共用定義：主畫面下載條與載入動畫條都套主題的 accent／track，不吃車機系統預設色。 */
        static void bar(Context c, android.widget.ProgressBar b) {
            android.content.res.ColorStateList tint =
                    android.content.res.ColorStateList.valueOf(color(c, R.attr.cpAccent));
            android.content.res.ColorStateList track =
                    android.content.res.ColorStateList.valueOf(color(c, R.attr.cpTrack));
            b.setProgressTintList(tint);
            b.setProgressBackgroundTintList(track);
        }

        static int mix(int a, int b, float t) {
            return Color.argb(Color.alpha(a),
                    Math.round(Color.red(a) * (1 - t) + Color.red(b) * t),
                    Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
                    Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
        }

        static GradientDrawable shape(Context c, int color, float radiusDp, int strokeColor, int strokeDp) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(color);
            g.setCornerRadius(dp(c, radiusDp));
            if (strokeDp > 0) {
                g.setStroke(strokeDp, strokeColor);
            }
            return g;
        }

        /** Tap feedback wrapper. */
        static StateListDrawable pressable(Context c, GradientDrawable normal, GradientDrawable pressed) {
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[]{android.R.attr.state_pressed}, pressed);
            s.addState(new int[]{android.R.attr.state_focused}, pressed);
            s.addState(new int[]{}, normal);
            return s;
        }

        public static void card(Context c, android.view.View v, int fillColor, int strokeColor) {
            int r = dp(c, 18);
            GradientDrawable n = new GradientDrawable();
            n.setColor(fillColor);
            n.setCornerRadius(r);
            n.setStroke(dp(c, 1), strokeColor);
            GradientDrawable p = new GradientDrawable();
            p.setColor(mix(fillColor, strokeColor, 0.35f));
            p.setCornerRadius(r);
            p.setStroke(dp(c, 1), strokeColor);
            v.setBackground(pressable(c, n, p));
        }

        public static void tile(Context c, android.view.View v, int fill) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(fill);
            g.setCornerRadius(dp(c, 26));
            g.setStroke(dp(c, 1), color(c, R.attr.cpOutline));
            v.setBackground(g);
        }

        public static void headerButton(Context c, android.view.View v) {
            int accent = color(c, R.attr.cpAccent);
            int surface = color(c, R.attr.cpSurface);
            int r = dp(c, 23);
            GradientDrawable n = new GradientDrawable();
            n.setColor(Color.TRANSPARENT);
            n.setCornerRadius(r);
            GradientDrawable p = new GradientDrawable();
            p.setColor(mix(surface, accent, 0.16f));
            p.setCornerRadius(r);
            v.setBackground(pressable(c, n, p));
        }

        public static android.graphics.drawable.Drawable tinted(Context c, int res, int color) {
            android.graphics.drawable.Drawable d = c.getResources().getDrawable(res);
            android.graphics.drawable.Drawable copy = d.mutate();
            copy.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            return copy;
        }
    }
}
