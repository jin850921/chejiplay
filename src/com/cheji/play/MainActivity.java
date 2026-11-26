package com.cheji.play;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends android.app.Activity {

    static final int MENU_CHECK = 201;
    static final int MENU_DL_PLAY = 202;
    static final int MENU_DL_AUTO = 203;
    static final int MENU_SITES = 204;
    static final int MENU_THEME = 205;
    static final int MENU_DL_BOTH = 206;
    static final int MENU_SHORTCUT = 207;
    /** 本 App 自己的更新列，與 DiPlay／DiAuto 同樣只在右上角選單裡。 */
    static final int MENU_DL_SELF = 208;
    /** 介面語言：六種模式（自動＋五種固定語言），選完立刻重畫，不必重啟 App。 */
    static final int MENU_LANG = 209;
    /** 批次下載列只處理兩個社群專案；選單與檢查結果對話框共用這一句，兩邊不會各說各話。 */
    static final String DL_BOTH_LABEL = "DiPlay＋DiAuto 兩個都下載";
    /** 長按「已加入的快捷鈕卡」才出現的兩項。 */
    static final int MENU_SC_SWAP = 301;
    static final int MENU_SC_DROP = 302;

    private final Map<String, Core.Rel> latest = new HashMap<String, Core.Rel>();
    private final Map<String, String> errors = new HashMap<String, String>();
    private Card[] cards;
    /** 上下兩排各自獨立的卡片列：下載卡在上、快捷鈕在下，等高與寬度分開算。 */
    private Card[] modRow, scRow;
    private LinearLayout cardHost, modHost, scHost;
    private TextView versionLine, footer, subtitle;
    private View menuDot;
    private boolean dark;
    private boolean horizontal;
    /** 卡片剛重建過：下一遍 layout 一定要重排，不能只靠橫／直切換當觸發。 */
    private boolean layoutDirty;
    private boolean autoChecked;
    private int[] syncedH = new int[2];
    private BroadcastReceiver pkgReceiver;

    /** 載入畫面只在程式冷開機跑一次；換主題、換語言都是 recreate()，不重播。 */
    private static boolean splashPlayed;
    private View splash, splashBadge, splashCar;
    private ProgressBar splashBar;
    private android.animation.ObjectAnimator splashFill;
    private long splashAt;
    private boolean firstLayoutDone;
    /** 載入動畫的總長度（含最後整層淡出）＝1.5 秒；改這一個數字，入場時間軸等比跟著走。 */
    private static final int SPLASH_TOTAL_MS = 1500;
    /** 整層淡出的長度，所有入場動作都排在淡出之前。 */
    private static final int SPLASH_FADE_MS = 260;
    /** 進度條會自己走到的上限；最後那一截要等首輪排版真的回來才解鎖，不假裝讀完了。 */
    private static final int SPLASH_PROGRESS_HOLD = 900;
    /** 上限是保險：排版事件沒如預期進來也一定收掉。 */
    private static final int SPLASH_MAX_MS = 3000;

    static final class Card {
        /** 下載型卡片（DiPlay／DiAuto）的資料；快捷鈕為 null。 */
        Core.Mod mod;
        /** 只開啟不下載的快捷鈕；null ＝下載型卡片。 */
        Core.Shortcut sc;
        View root;
        TextView title, method, status;
        ProgressBar progress;
        /** 快捷鈕的圖塊，以及 item_module.xml 的預設尺寸——換成 App 自己的圖是滿版，還原預設時要用它收回來。 */
        ImageView glyph;
        int glyphW, glyphH;
    }

    // ------------------------------------------------------------------- life

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        SharedPreferences sp = prefs(this);
        L.sync(this);
        dark = isDark(sp.getInt(Prefs.THEME, Prefs.THEME_AUTO), this);
        setTheme(dark ? R.style.Theme_ChejiPlay_Dark : R.style.Theme_ChejiPlay_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Core.Ui.color(this, R.attr.cpBg));
            if (!dark && Build.VERSION.SDK_INT >= 23) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        subtitle = (TextView) findViewById(R.id.subtitleText);
        versionLine = (TextView) findViewById(R.id.versionLine);
        footer = (TextView) findViewById(R.id.footerText);
        cardHost = (LinearLayout) findViewById(R.id.cardHost);
        modHost = (LinearLayout) findViewById(R.id.modHost);
        scHost = (LinearLayout) findViewById(R.id.scHost);

        int text = Core.Ui.color(this, R.attr.cpText);
        int dim = Core.Ui.color(this, R.attr.cpTextDim);
        ((TextView) findViewById(R.id.titleText)).setTextColor(text);
        subtitle.setText(L.t("CarPlay／Android Auto 的下載與啟動入口"));
        subtitle.setTextColor(dim);
        footer.setText(L.t("DiPlay、DiAuto 是 GitHub 上的社群獨立專案，與 Apple、Google、BYD 無隸屬關係。"
                + "本 App 只負責抓最新版 APK 與開啟，下載位置一律是 GitHub Releases 官方資產。\n"
                + "車機Play 自身也走同一條更新路，新版本在右上角選單裡下載。\n"
                + "兩個專案以 BYD 車機為主力，其他品牌不保證可用。\n"
                + "可以把「車機Play」加入車機的開機自啟動清單，之後每次開車機螢幕就自動打開這裡，點卡片立刻進入 CarPlay 或 Android Auto。"
                + "自啟動清單的名稱與位置依車機品牌不同，能不能加入、可以加幾個都由車機系統決定。"));
        footer.setTextColor(dim);

        for (int i = 0; i < Prefs.CACHED_KEYS.length; i++) {
            String[] k = Prefs.CACHED_KEYS[i];
            String url = sp.getString(k[1], null);
            String ver = sp.getString(k[2], null);
            String file = sp.getString(k[3], null);
            if (url != null && ver != null && file != null) {
                latest.put(Core.MODS[i].id, new Core.Rel(ver, url, file, sp.getString(k[4], null),
                        sp.getString(k[5], null), sp.getLong(k[6], 0)));
            }
        }

        rebuildCards();

        // 左上角 logo：色塊沿用 DiAuto 卡那對 navy，圓角比例也一致（26dp/104dp → 14dp/56dp）；商標是白圖，不随主題翻色。
        findViewById(R.id.logoBadge).setBackground(Core.Ui.shape(this,
                dark ? Core.BRAND_NAVY_DARK : Core.BRAND_NAVY_LIGHT, 14,
                Core.Ui.color(this, R.attr.cpOutline), Core.Ui.dp(this, 1)));

        View themeBtn = findViewById(R.id.btnTheme);
        View menuBtn = findViewById(R.id.btnMenu);
        Core.Ui.headerButton(this, themeBtn);
        Core.Ui.headerButton(this, menuBtn);
        ((ImageView) themeBtn).setImageDrawable(Core.Ui.tinted(this, R.drawable.ic_theme, text));
        ((ImageView) menuBtn).setImageDrawable(Core.Ui.tinted(this, R.drawable.ic_more, text));
        themeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThemePicker();
            }
        });
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v);
            }
        });

        menuDot = findViewById(R.id.menuDot);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(Core.Ui.color(this, R.attr.cpBadge));
        menuDot.setBackground(dot);

        ((ImageView) findViewById(R.id.artCar)).setColorFilter(
                Core.Ui.color(this, R.attr.cpArt), android.graphics.PorterDuff.Mode.SRC_IN);

        ((ScrollView) findViewById(R.id.mainScroll)).getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        applyLayout();
                        if (!firstLayoutDone) {
                            firstLayoutDone = true;
                            scheduleSplashOut();
                        }
                    }
                });
        refresh();
        playSplash();
    }

    // ---------------------------------------------------------------- splash

    /**
     * 載入動畫：商標色塊彈入 → 標題與說明淡入 → 進度條顯示啟動進度 → 側面車線稿從畫面左側開進定位，
     * 首輪排版完成後整層淡出。全部用 framework 的 ViewPropertyAnimator／ObjectAnimator，
     * 沒有 AndroidX 也沒有逐格圖。每個起點與長度都是入場基準時間的百分比，所以只改
     * {@link #SPLASH_TOTAL_MS} 就能整條時間軸一起加長或縮短。
     */
    private void playSplash() {
        if (splashPlayed) {
            return;
        }
        splashPlayed = true;
        final View badge = findViewById(R.id.splashBadge);
        final TextView title = (TextView) findViewById(R.id.splashTitle);
        final TextView hint = (TextView) findViewById(R.id.splashHint);
        splashBar = (ProgressBar) findViewById(R.id.splashBar);
        splashCar = findViewById(R.id.splashCar);
        splash = findViewById(R.id.splash);
        /** 入場時間軸的基準＝總長度扣掉最後的淡出；淡出之前所有元素都得到位。 */
        final int t = SPLASH_TOTAL_MS - SPLASH_FADE_MS;

        splash.setBackgroundColor(Core.Ui.color(this, R.attr.cpBg));
        badge.setBackground(Core.Ui.shape(this,
                dark ? Core.BRAND_NAVY_DARK : Core.BRAND_NAVY_LIGHT, 22,
                Core.Ui.color(this, R.attr.cpOutline), Core.Ui.dp(this, 1)));
        title.setTextColor(Core.Ui.color(this, R.attr.cpText));
        hint.setText(L.t("正在讀取車機上的安裝狀態"));
        hint.setTextColor(Core.Ui.color(this, R.attr.cpTextDim));
        Core.Ui.bar(this, splashBar);
        splashBar.setIndeterminate(false);
        splashBar.setProgress(0);
        ((ImageView) splashCar).setColorFilter(
                Core.Ui.color(this, R.attr.cpArt), android.graphics.PorterDuff.Mode.SRC_IN);

        badge.setAlpha(0f);
        badge.setScaleX(0.82f);
        badge.setScaleY(0.82f);
        title.setAlpha(0f);
        hint.setAlpha(0f);
        splashBar.setAlpha(0f);
        splashCar.setAlpha(0f);
        // 起步先往左推出車身自己的寬度，定位再由 post 裡依實際位置修正，窄螢幕也不會露出斷點。
        splashCar.setTranslationX(-Core.Ui.dp(this, 330));
        splash.setVisibility(View.VISIBLE);
        splashAt = android.os.SystemClock.uptimeMillis();
        splash.postDelayed(splashOut, SPLASH_MAX_MS);

        badge.post(new Runnable() {
            @Override
            public void run() {
                // 定位已知了，起點重新推到整輛車完全在畫面外，才不會出現「車已經在螢幕裡滑動」。
                float shift = splashCar.getX() + splashCar.getWidth() + Core.Ui.dp(MainActivity.this, 12);
                splashCar.setTranslationX(-shift);
                android.view.animation.Interpolator ease =
                        new android.view.animation.DecelerateInterpolator(1.35f);
                splashCar.animate().alpha(1f).translationX(0f)
                        .setDuration(t * 8 / 10).setInterpolator(ease).start();
                badge.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(t * 34 / 100).setStartDelay(t * 11 / 100)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
                title.animate().alpha(1f).setDuration(t * 29 / 100)
                        .setStartDelay(t * 24 / 100).setInterpolator(ease).start();
                hint.animate().alpha(1f).setDuration(t * 29 / 100)
                        .setStartDelay(t * 35 / 100).setInterpolator(ease).start();
                splashBar.animate().alpha(1f).setDuration(t * 23 / 100)
                        .setStartDelay(t * 42 / 100).start();
                // 啟動進度跟進度條一起出現，到該淡出的那一刻剛好走到上限；最後一截由 scheduleSplashOut 解鎖。
                splashFill = android.animation.ObjectAnimator
                        .ofInt(splashBar, "progress", 0, SPLASH_PROGRESS_HOLD);
                splashFill.setInterpolator(new android.view.animation.LinearInterpolator());
                splashFill.setStartDelay(t * 42 / 100);
                splashFill.setDuration(t - t * 42 / 100);
                splashFill.start();
            }
        });
    }

    /** 首輪排版跑完就收，但一定讓動畫走完入場時間軸。 */
    private void scheduleSplashOut() {
        if (splash == null) {
            return;
        }
        long left = (SPLASH_TOTAL_MS - SPLASH_FADE_MS) - (android.os.SystemClock.uptimeMillis() - splashAt);
        if (left < 0) {
            left = 0;
        }
        splash.removeCallbacks(splashOut);
        splash.postDelayed(splashOut, left);
    }

    private final Runnable splashOut = new Runnable() {
        @Override
        public void run() {
            if (splash == null) {
                return;
            }
            final View layer = splash;
            splash = null;
            layer.removeCallbacks(splashOut);
            if (splashFill != null) {
                splashFill.cancel();
                splashFill = null;
            }
            // 只有真的排完版才補到 100%；3 秒保險路徑是排版沒回來，進度就留在它實際走到的位置。
            if (firstLayoutDone && splashBar != null) {
                splashBar.setProgress(splashBar.getMax());
            }
            layer.animate().alpha(0f).setDuration(SPLASH_FADE_MS)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .setStartDelay(0).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            layer.setVisibility(View.GONE);
                        }
                    }).start();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Core.setHubListener(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    refresh();
                    syncVersionLine();
                }
            }
        });
        refresh();
        syncVersionLine();
        if (!autoChecked) {
            autoChecked = true;
            if (!checkedToday(prefs(this).getLong(Prefs.CHECKED_AT, 0))) {
                checkUpdates(false);
            }
        }
    }

    @Override
    protected void onStop() {
        Core.setHubListener(null);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 車機的安裝畫面常常只讓本頁暫停、不會走到 onStop，onStart 就不會再進來；
        // 回前台一律重掃一次安裝狀態。
        rescanSoon();
        watchPackages(true);
    }

    @Override
    protected void onPause() {
        watchPackages(false);
        Core.MAIN.removeCallbacks(rescanTask);
        super.onPause();
    }

    private final Runnable rescanTask = new Runnable() {
        @Override
        public void run() {
            refresh();
            syncVersionLine();
        }
    };

    private void rescanSoon() {
        rescanTask.run();
        Core.MAIN.removeCallbacks(rescanTask);
        Core.MAIN.postDelayed(rescanTask, 900);
    }

    // 安裝/移除在別處發生（檔案管理員、系統設定）時也要立刻反映，所以在前台時收系統的套件廣播。
    private void watchPackages(boolean on) {
        if (on) {
            if (pkgReceiver != null) {
                return;
            }
            pkgReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent it) {
                    String pkg = it.getData() == null ? null : it.getData().getSchemeSpecificPart();
                    for (Core.Mod m : Core.MODS) {
                        if (m.pkg.equals(pkg)) {
                            rescanSoon();
                            return;
                        }
                    }
                    // 快捷鈕那些 App 被裝上／移除時，卡片狀態列也要立刻跟著變（兩欄都算）。
                    String c1 = prefs(ctx).getString(Prefs.SC_PKG, null);
                    String c2 = prefs(ctx).getString(Prefs.SC_PKG2, null);
                    if (Core.MAPS_PKG.equals(pkg) || (c1 != null && c1.equals(pkg)) || (c2 != null && c2.equals(pkg))) {
                        rescanSoon();
                    }
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
            f.addDataScheme("package");
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(pkgReceiver, f, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(pkgReceiver, f);
                }
            } catch (Exception e) {
                pkgReceiver = null;
            }
            return;
        }
        if (pkgReceiver != null) {
            try {
                unregisterReceiver(pkgReceiver);
            } catch (Exception ignored) {
            }
            pkgReceiver = null;
        }
    }

    // ------------------------------------------------------------------ cards

    private Card buildCard(final Core.Mod m) {
        Card c = new Card();
        c.mod = m;
        c.root = getLayoutInflater().inflate(R.layout.item_module, modHost, false);
        c.title = (TextView) c.root.findViewById(R.id.moduleTitle);
        c.method = (TextView) c.root.findViewById(R.id.moduleMethod);
        c.status = (TextView) c.root.findViewById(R.id.moduleStatus);
        c.progress = (ProgressBar) c.root.findViewById(R.id.moduleProgress);
        Core.Ui.bar(this, c.progress);

        Core.Ui.tile(this, c.root.findViewById(R.id.moduleTile), m.tile(dark));
        ImageView glyph = (ImageView) c.root.findViewById(R.id.moduleGlyph);
        if (m.brandGlyph) {
            glyph.setImageResource(m.glyphRes);
        } else {
            glyph.setImageDrawable(Core.Ui.tinted(this, m.glyphRes, m.glyph(dark)));
        }
        c.title.setText(L.t(m.surface));
        c.title.setTextColor(Core.Ui.color(this, R.attr.cpText));
        c.method.setText(L.t("以 " + m.impl + " 實現"));
        c.method.setTextColor(Core.Ui.color(this, R.attr.cpAccent));
        c.root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                primaryAction(m);
            }
        });
        c.root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Core.Job j = Core.job(m.id);
                if (!j.active) {
                    return false;
                }
                cancelDownload(m);
                return true;
            }
        });
        modHost.addView(c.root);
        return c;
    }

    /** 主畫面分兩排：上面固定是 DiPlay／DiAuto 兩張下載卡，下面是 Maps（要手動開）＋ 0／1／2 個自訂欄位。 */
    private void rebuildCards() {
        SharedPreferences sp = prefs(this);
        modHost.removeAllViews();
        scHost.removeAllViews();
        List<Card> mods = new ArrayList<Card>();
        mods.add(buildCard(Core.DIPLAY));
        mods.add(buildCard(Core.DIAUTO));
        List<Card> scs = new ArrayList<Card>();
        if (sp.getBoolean(Prefs.SC_MAPS, false)) {
            scs.add(buildShortcut(Core.MAPS));
        }
        for (int slot = 1; slot <= slotCount(sp); slot++) {
            String pkg = sp.getString(pkgKey(slot), null);
            String label = customLabel(sp, slot);
            scs.add(buildShortcut(Core.customSlot(idOf(slot), pkg,
                    label != null ? label : pkg, EMPTY_TITLE, EMPTY_METHOD)));
        }
        modRow = mods.toArray(new Card[mods.size()]);
        scRow = scs.toArray(new Card[scs.size()]);
        List<Card> list = new ArrayList<Card>();
        list.addAll(mods);
        list.addAll(scs);
        cards = list.toArray(new Card[list.size()]);
        layoutDirty = true;
        syncedH[0] = 0;
        syncedH[1] = 0;
        applyLayout();
        refresh();
    }

    /** 自訂欄位數量：預設 0 個＝整排不出現，1 欄或 2 欄都在設定裡點出來。 */
    static int slotCount(SharedPreferences sp) {
        return Math.max(0, Math.min(2, sp.getInt(Prefs.SC_COUNT, 0)));
    }

    static String idOf(int slot) {
        return slot == 1 ? "custom" : "custom2";
    }

    static int slotOf(String id) {
        return "custom2".equals(id) ? 2 : 1;
    }

    static String pkgKey(int slot) {
        return slot == 1 ? Prefs.SC_PKG : Prefs.SC_PKG2;
    }

    static String labelKey(int slot) {
        return slot == 1 ? Prefs.SC_LABEL : Prefs.SC_LABEL2;
    }

    /** 兩欄位空著的時候是同一張卡、同一個名稱，不再分導航／測速。 */
    static final String EMPTY_TITLE = "添加快捷鈕";
    static final String EMPTY_METHOD = "點一下選一個 App";

    private Card buildShortcut(final Core.Shortcut s) {
        Card c = new Card();
        c.sc = s;
        c.root = getLayoutInflater().inflate(R.layout.item_module, scHost, false);
        c.title = (TextView) c.root.findViewById(R.id.moduleTitle);
        c.method = (TextView) c.root.findViewById(R.id.moduleMethod);
        c.status = (TextView) c.root.findViewById(R.id.moduleStatus);
        c.progress = (ProgressBar) c.root.findViewById(R.id.moduleProgress);
        Core.Ui.bar(this, c.progress);

        Core.Ui.tile(this, c.root.findViewById(R.id.moduleTile), s.tile(dark));
        ImageView glyph = (ImageView) c.root.findViewById(R.id.moduleGlyph);
        c.glyph = glyph;
        c.glyphW = glyph.getLayoutParams().width;
        c.glyphH = glyph.getLayoutParams().height;
        applyShortcutIcon(c);
        // 空位才是我們自己的「添加快捷鈕」；已經加入的卡片名稱是車機上那套 App 的名字，不進翻譯。
        c.title.setText(s.isEmpty() ? L.t(s.title) : s.title);
        c.title.setTextColor(Core.Ui.color(this, R.attr.cpText));
        c.method.setText(L.t(s.method));
        c.method.setTextColor(Core.Ui.color(this, R.attr.cpAccent));
        c.root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (s.isEmpty()) {
                    showAppPicker(prefs(MainActivity.this), slotOf(s.id), null);
                } else {
                    openShortcut(s);
                }
            }
        });
        c.root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (s.isEmpty() || !s.id.startsWith("custom")) {
                    return false;
                }
                showShortcutMenu(s, v, slotOf(s.id));
                return true;
            }
        });
        scHost.addView(c.root);
        return c;
    }

    /**
     * 快捷鈕的圖一律從這裡出：車機上那套 App 查得到圖示，就把它自己的圖滿版顯示（104dp 色塊多大就多大）；
     * 查不到才用本 App 內建的預設圖（Maps 是官方色圖釘、空位是「＋」）。
     * 組裝與 refresh() 都走這一個方法，所以 App 裝上、移除、或改版換了圖示，
     * 同一張卡的圖會跟著套件廣播即時重畫，不必重開程式，也不用重新進設定頁。
     */
    private void applyShortcutIcon(Card c) {
        Core.Shortcut s = c.sc;
        ImageView g = c.glyph;
        Drawable live = s.isEmpty() ? null : Core.appIcon(this, s.pkg);
        // 自訂欄位沒有內建備用圖：抓不到就維持畫面上那張，狀態列的「尚未安裝」已經說明原因。
        if (live == null && s.glyphRes == 0) {
            return;
        }
        ViewGroup.LayoutParams lp = g.getLayoutParams();
        g.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (live == null) {
            lp.width = c.glyphW;
            lp.height = c.glyphH;
            g.setLayoutParams(lp);
            if (s.colorGlyph) {
                g.setImageResource(s.glyphRes);
            } else {
                g.setImageDrawable(Core.Ui.tinted(this, s.glyphRes, s.glyph(dark)));
            }
            return;
        }
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        g.setLayoutParams(lp);
        // 抓到的是 App 自己的圖，品牌色不壓色。
        g.setImageDrawable(live);
    }

    private void bindShortcut(Card c) {
        Core.Shortcut s = c.sc;
        applyShortcutIcon(c);
        if (s.isEmpty()) {
            c.status.setText(L.t("尚未加入"));
            c.status.setTextColor(Core.Ui.color(this, R.attr.cpTextDim));
        } else {
            String installed = Core.installedVersion(this, s.pkg);
            String label = installed == null ? "尚未安裝" : "已安裝 " + Core.dispVersion(this, s.pkg);
            if (s.id.startsWith("custom")) {
                // 提示另起一列：四段式版號再帶七位數 code 這種長度會把「長按可換／移除」從中間折斷。
                label += "\n長按可換／移除";
            }
            c.status.setText(L.t(label));
            c.status.setTextColor(Core.Ui.color(this,
                    installed == null ? R.attr.cpWarn : R.attr.cpOk));
        }
        c.root.setAlpha(1f);
        c.progress.setVisibility(View.GONE);
        Core.Ui.card(this, c.root, Core.Ui.color(this, R.attr.cpSurface),
                Core.Ui.color(this, R.attr.cpOutline));
    }

    private void openShortcut(Core.Shortcut s) {
        if (Core.installedVersion(this, s.pkg) == null) {
            dialog("找不到 " + s.title, s.missing, null, null, "知道了", null);
            return;
        }
        Intent it = getPackageManager().getLaunchIntentForPackage(s.pkg);
        if (it == null) {
            dialog("找不到啟動項目", s.title + " 已安裝，但這台車機沒有取得它的主畫面圖示。"
                    + "請從車機的應用列表開啟。", null, null, "知道了", null);
            return;
        }
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(it);
        } catch (Exception e) {
            toast("無法開啟 " + s.title + "：" + message(e));
        }
    }

    /** 兩排各自鋪：橫排時每排是自己一條水平列，窄畫面才整排往下堆。 */
    private void applyLayout() {
        if (cards == null || cardHost.getWidth() <= 0) {
            return;
        }
        boolean want = cardHost.getWidth() >= Core.Ui.dp(this, 720);
        if (want != horizontal || layoutDirty) {
            horizontal = want;
            layoutDirty = false;
            layoutRow(modRow, want, 0);
            layoutRow(scRow, want, 1);
        }
        syncHeights(0, modRow);
        syncHeights(1, scRow);
    }

    /** 一排自己排版：卡的寬度由這一排的卡數決定，不跟另一排共用同一個除數。 */
    private void layoutRow(Card[] row, boolean want, int idx) {
        LinearLayout host = idx == 0 ? modHost : scHost;
        host.setOrientation(want ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        int gap = Core.Ui.dp(this, 8);
        int gapV = Core.Ui.dp(this, want ? 10 : 12);
        for (Card c : row) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    want ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = gap;
            lp.rightMargin = gap;
            lp.topMargin = gapV;
            lp.bottomMargin = gapV;
            c.root.setLayoutParams(lp);
            c.root.setMinimumWidth(minCardWidth(cardHost.getWidth(), row.length, want));
            // 兩排一起出現在同一螢幕時，卡的上下內距收緊，才不會把頁尾推出去。
            c.root.setPadding(Core.Ui.dp(this, 20), Core.Ui.dp(this, want ? 20 : 34),
                    Core.Ui.dp(this, 20), Core.Ui.dp(this, want ? 20 : 34));
        }
        syncedH[idx] = 0;
    }

    /** 橫排的寬度由畫面寬除以卡數決定：一排 3 張在最窄的車機上也排得下，寬螢幕仍撐到 240dp。 */
    private int minCardWidth(int hostPx, int count, boolean row) {
        int full = Core.Ui.dp(this, 240);
        if (!row || count <= 0) {
            return full;
        }
        int each = (hostPx - count * Core.Ui.dp(this, 16)) / count;
        return Math.max(Core.Ui.dp(this, 150), Math.min(full, each));
    }

    /** 同一排幾張卡必須讀起來是一樣高。 */
    private void syncHeights(int idx, Card[] row) {
        if (!horizontal || row.length < 2) {
            for (Card c : row) {
                c.root.setMinimumHeight(0);
            }
            syncedH[idx] = 0;
            return;
        }
        int max = 0;
        for (Card c : row) {
            max = Math.max(max, c.root.getMeasuredHeight());
        }
        if (max <= 0 || max == syncedH[idx]) {
            return;
        }
        syncedH[idx] = max;
        for (Card c : row) {
            c.root.setMinimumHeight(max);
        }
    }

    private void refresh() {
        if (cards == null) {
            return;
        }
        for (Card c : cards) {
            if (c.sc == null) {
                bind(c);
            } else {
                bindShortcut(c);
            }
        }
    }

    private void bind(Card c) {
        Core.Mod m = c.mod;
        String installed = Core.installedVersion(this, m.pkg);
        Core.Rel r = latest.get(m.id);
        Core.Job j = Core.JOBS.get(m.id);
        boolean busy = j != null && j.active;
        int dim = Core.Ui.color(this, R.attr.cpTextDim);
        int warn = Core.Ui.color(this, R.attr.cpWarn);
        int ok = Core.Ui.color(this, R.attr.cpOk);
        boolean outdated = r != null && installed != null && Core.cmpVersion(installed, r.version) > 0;

        String status;
        // 顯示一律 name(code)；比對還是用上面那個裸版號。
        String shown = installed == null ? null : Core.dispVersion(this, m.pkg);
        int statusColor = dim;
        String err = errors.get(m.id);
        if (busy) {
            status = j.text + (j.percent > 0 ? "　" + (j.percent / 10) + "%　長按可取消" : "");
        } else if (err != null) {
            status = "檢查失敗：" + err;
            statusColor = warn;
        } else if (installed == null) {
            status = "尚未安裝" + (r != null ? " · 最新版 " + r.version + " · " + Core.humanSize(r.size) : "");
        } else if (r == null) {
            status = "已安裝 " + shown + " · 尚未檢查更新";
        } else if (outdated) {
            status = "已安裝 " + shown + " · 右上角選單可更新到 " + r.version;
            statusColor = warn;
        } else {
            status = "已安裝 " + shown + " · 已是最新";
            statusColor = ok;
        }
        c.status.setText(L.t(status));
        c.status.setTextColor(statusColor);

        c.root.setAlpha(busy ? 0.7f : 1f);
        c.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            c.progress.setProgress(j.percent);
        }
        Core.Ui.card(this, c.root, Core.Ui.color(this, R.attr.cpSurface), Core.Ui.color(this, R.attr.cpOutline));
        if (modRow != null && scRow != null) {
            syncHeights(0, modRow);
            syncHeights(1, scRow);
        }
        updateBadge();
    }

    /** The only update reminder: a dot on the three-dot menu. */
    private void updateBadge() {
        if (menuDot == null) {
            return;
        }
        boolean remind = false;
        for (Core.Mod m : Core.MODS) {
            String installed = Core.installedVersion(this, m.pkg);
            Core.Rel r = latest.get(m.id);
            if (installed == null || (r != null && Core.cmpVersion(installed, r.version) > 0)) {
                remind = true;
                break;
            }
        }
        if (!remind) {
            Core.Rel r = latest.get(Core.SELF.id);
            String mine = Core.installedVersion(this, Core.SELF_PKG);
            remind = r != null && mine != null && Core.cmpVersion(mine, r.version) > 0;
        }
        menuDot.setVisibility(remind ? View.VISIBLE : View.GONE);
    }

    private void syncVersionLine() {
        long at = prefs(this).getLong(Prefs.CHECKED_AT, 0);
        versionLine.setText(L.t(at <= 0 ? "尚未檢查更新"
                : "上次檢查更新：" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(at))));
        versionLine.setTextColor(Core.Ui.color(this, R.attr.cpTextDim));
    }

    // ---------------------------------------------------------------- actions

    private void primaryAction(Core.Mod m) {
        Core.Job j = Core.job(m.id);
        if (j.active) {
            toast(m.impl + " 下載中，長按卡片可取消");
            return;
        }
        if (Core.installedVersion(this, m.pkg) == null) {
            download(Core.sourceOf(m));
        } else {
            openApp(m);
        }
    }

    private void cancelDownload(Core.Mod m) {
        Core.Job j = Core.job(m.id);
        if (!j.active) {
            return;
        }
        j.cancelFlag[0] = true;
        j.text = "取消中";
        refresh();
    }

    private void openApp(Core.Mod m) {
        Intent it = getPackageManager().getLaunchIntentForPackage(m.pkg);
        if (it == null) {
            dialog("找不到啟動項目",
                    m.impl + " 已安裝，但這台車機沒有取得它的主畫面圖示。請從車機的應用列表開啟 " + m.impl + "。",
                    null, null, "知道了", null);
            return;
        }
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(it);
        } catch (Exception e) {
            toast("無法開啟 " + m.impl + "：" + e.getMessage());
        }
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        // 排序：查詢 1 列 → 兩個社群專案 3 列 → 本 App 自己 1 列 → 設定類 4 列。
        pm.getMenu().add(0, MENU_CHECK, 0, L.t("檢查更新（只查版號，不下載）"));
        pm.getMenu().add(0, MENU_DL_PLAY, 1, L.t(downloadLabel(Core.DIPLAY)));
        pm.getMenu().add(0, MENU_DL_AUTO, 2, L.t(downloadLabel(Core.DIAUTO)));
        pm.getMenu().add(0, MENU_DL_BOTH, 3, L.t(DL_BOTH_LABEL));
        pm.getMenu().add(0, MENU_DL_SELF, 4, L.t(selfLabel()));
        pm.getMenu().add(0, MENU_SHORTCUT, 5, L.t("主畫面快捷鈕"));
        pm.getMenu().add(0, MENU_SITES, 6, L.t("網站與功能說明"));
        pm.getMenu().add(0, MENU_LANG, 7, L.t("介面語言"));
        pm.getMenu().add(0, MENU_THEME, 8, L.t("主題：明／暗"));
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case MENU_CHECK:
                        checkUpdates(true);
                        return true;
                    case MENU_DL_PLAY:
                        download(Core.sourceOf(Core.DIPLAY));
                        return true;
                    case MENU_DL_AUTO:
                        download(Core.sourceOf(Core.DIAUTO));
                        return true;
                    case MENU_DL_BOTH:
                        downloadAll();
                        return true;
                    case MENU_DL_SELF:
                        selfUpdate();
                        return true;
                    case MENU_SHORTCUT:
                        showShortcutSettings();
                        return true;
                    case MENU_SITES:
                        startActivity(new Intent(MainActivity.this, SitesActivity.class));
                        return true;
                    case MENU_LANG:
                        showLangPicker();
                        return true;
                    case MENU_THEME:
                        showThemePicker();
                        return true;
                    default:
                        return false;
                }
            }
        });
        pm.show();
    }

    private String downloadLabel(Core.Mod m) {
        Core.Rel r = latest.get(m.id);
        String ver = r == null ? "最新版" : r.version;
        String installed = Core.installedVersion(this, m.pkg);
        if (installed == null) {
            return "下載 " + m.impl + " " + ver;
        }
        if (r != null && Core.cmpVersion(installed, r.version) > 0) {
            return "更新 " + m.impl + " " + Core.dispVersion(this, m.pkg) + " → " + ver;
        }
        return "下載 " + m.impl + " " + ver + "（已是最新）";
    }

    /** 本 App 自己的更新列：說法跟 DiPlay／DiAuto 對齊，下載中就變成取消，不留按不下去的狀態。 */
    private String selfLabel() {
        Core.Job j = Core.job(Core.SELF.id);
        if (j.active) {
            return j.percent > 0 ? "取消下載 " + Core.SELF.impl + " " + (j.percent / 10) + "%"
                    : "取消下載 " + Core.SELF.impl;
        }
        Core.Rel r = latest.get(Core.SELF.id);
        String ver = r == null ? "最新版" : r.version;
        String mine = Core.installedVersion(this, Core.SELF_PKG);
        if (mine != null && r != null && Core.cmpVersion(mine, r.version) > 0) {
            return "更新 " + Core.SELF.impl + " " + Core.dispVersion(this, Core.SELF_PKG) + " → " + ver;
        }
        return "下載 " + Core.SELF.impl + " " + ver + (r == null || mine == null ? "" : "（已是最新）");
    }

    private void selfUpdate() {
        Core.Job j = Core.job(Core.SELF.id);
        if (j.active) {
            j.cancelFlag[0] = true;
            j.text = "取消中";
            toast(Core.SELF.impl + " 下載取消中");
            return;
        }
        download(Core.SELF);
    }

    private void showThemePicker() {
        final SharedPreferences sp = prefs(this);
        int cur = sp.getInt(Prefs.THEME, Prefs.THEME_AUTO);
        CharSequence[] items = new CharSequence[]{L.t("跟隨車機系統"), L.t("淺色"), L.t("深色")};
        new android.app.AlertDialog.Builder(this)
                .setTitle(L.t("介面主題"))
                .setSingleChoiceItems(items, cur, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        sp.edit().putInt(Prefs.THEME, which).apply();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(L.t("取消"), null)
                .show();
    }

    /**
     * 語言選單：每一列永遠用那種語言自己的寫法，翻成日本語後「English」那列仍看得出來選哪個。
     * 「自動」是照車機語系，其他五種是鎖死；選完存設定、同步語言層、重畫這一頁。
     */
    private void showLangPicker() {
        final SharedPreferences sp = prefs(this);
        new android.app.AlertDialog.Builder(this)
                .setTitle(L.t("介面語言"))
                .setSingleChoiceItems(L.NAMES, L.saved(this), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        sp.edit().putInt(Prefs.LANG, which).apply();
                        d.dismiss();
                        L.sync(MainActivity.this);
                        recreate();
                    }
                })
                .setNegativeButton(L.t("取消"), null)
                .show();
    }

    // --------------------------------------------------------------- shortcuts

    /** 快捷鈕設定：Google Maps 一顆開關，外加「手動添加欄位」要 0／1／2 個。 */
    private void showShortcutSettings() {
        final SharedPreferences sp = prefs(this);
        int text = Core.Ui.color(this, R.attr.cpText);
        int dim = Core.Ui.color(this, R.attr.cpTextDim);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Core.Ui.dp(this, 24), Core.Ui.dp(this, 2), Core.Ui.dp(this, 24), 0);

        String mapsVer = Core.installedVersion(this, Core.MAPS_PKG);
        LinearLayout mapsRow = settingRow(body, "Google Maps 快捷鈕",
                mapsVer == null ? "只負責開啟；這台車機目前查不到 Google Maps"
                        : "只負責開啟，更新請用車機上的 Google Play", text, dim);
        final Switch mapsSwitch = new Switch(this);
        mapsSwitch.setChecked(sp.getBoolean(Prefs.SC_MAPS, false));
        mapsRow.addView(mapsSwitch);
        mapsSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                sp.edit().putBoolean(Prefs.SC_MAPS, on).apply();
                rebuildCards();
            }
        });
        mapsRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mapsSwitch.toggle();
            }
        });

        final TextView slotValue = new TextView(this);
        slotValue.setTextSize(15);
        slotValue.setTextColor(Core.Ui.color(this, R.attr.cpAccent));
        slotValue.setText(L.t(slotLabel(sp)));
        LinearLayout slotRow = settingRow(body, "手動添加欄位",
                "兩欄都是你自己挑的 App；選「不顯示」就不出現卡片，改成較少也只是收起來，選過的 App 都還在。", text, dim);
        slotRow.addView(slotValue);
        slotRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSlotCount(slotValue);
            }
        });

        TextView foot = new TextView(this);
        foot.setText(L.t("「添加快捷鈕」這張卡最多兩張：還沒加入時點一下是選 App，加入後點一下就是開啟那個 App，"
                + "長按可以換一個或從主畫面移除。\n快捷鈕都只負責開啟，不會下載也不會檢查更新，"
                + "所以 Google Maps 一律交給車機上的 Google Play 維護。"));
        foot.setTextSize(12);
        foot.setTextColor(dim);
        foot.setLineSpacing(0, 1.05f);
        foot.setPadding(0, Core.Ui.dp(this, 16), 0, 0);
        body.addView(foot);

        new android.app.AlertDialog.Builder(this)
                .setTitle(L.t("主畫面快捷鈕"))
                .setView(body)
                .setPositiveButton(L.t("完成"), null)
                .show();
    }

    /** 欄位數量的單選：0 是整排不出現，寫回後把右側數值和主畫面一起更新。 */
    private void pickSlotCount(final TextView readout) {
        final SharedPreferences sp = prefs(this);
        new android.app.AlertDialog.Builder(this)
                .setTitle(L.t("主畫面幾個手動欄位"))
                .setSingleChoiceItems(new CharSequence[] { L.t("不顯示"), L.t("1 個"), L.t("2 個") },
                        slotCount(sp), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                sp.edit().putInt(Prefs.SC_COUNT, which).apply();
                                readout.setText(L.t(slotLabel(sp)));
                                rebuildCards();
                                d.dismiss();
                            }
                        })
                .show();
    }

    private static String slotLabel(SharedPreferences sp) {
        int n = slotCount(sp);
        return n == 0 ? "不顯示" : n + " 個";
    }

    private LinearLayout settingRow(LinearLayout host, String titleText, String hint, int text, int dim) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Core.Ui.dp(this, 14);
        row.setLayoutParams(lp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(this);
        t.setText(L.t(titleText));
        t.setTextSize(15);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(text);
        TextView h = new TextView(this);
        h.setText(L.t(hint));
        h.setTextSize(12);
        h.setTextColor(dim);
        h.setLineSpacing(0, 1.05f);
        col.addView(t);
        col.addView(h);
        row.addView(col);
        host.addView(row);
        return row;
    }

    /** 該欄目前選到的 App：裝著就讀即時名稱，被移除了才退回當下記下的名字。 */
    private String customLabel(SharedPreferences sp, int slot) {
        String pkg = sp.getString(pkgKey(slot), null);
        if (pkg == null || pkg.length() == 0) {
            return null;
        }
        String live = Core.appLabel(this, pkg);
        return live != null ? live : sp.getString(labelKey(slot), pkg);
    }

    /** 挑 App 的清單：每列左邊直接顯示那個 App 的圖示；只負責「放哪個進來」，移除是長按那張卡的事，不在這裡重複。 */
    private void showAppPicker(final SharedPreferences sp, final int slot, String currentLabel) {
        String[][] apps = Core.launchableApps(this);
        if (apps.length == 0) {
            dialog("查不到可啟動的 App", "這台車機沒有回報任何「有主畫面圖示」的 App，"
                    + "所以暫時無法加入自訂快捷鈕。", null, null, "知道了", null);
            return;
        }
        final List<CharSequence> names = new ArrayList<CharSequence>();
        final List<String> pkgs = new ArrayList<String>();
        for (String[] a : apps) {
            names.add(a[0]);
            pkgs.add(a[1]);
        }
        final int textColor = Core.Ui.color(this, R.attr.cpText);
        ListAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return names.size();
            }

            @Override
            public Object getItem(int position) {
                return names.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            /** 左邊是該 App 自己的圖示；查不到才退回系統預設圖示，名稱起點位置不會跟著跑。 */
            @Override
            public View getView(int position, View reuse, ViewGroup parent) {
                boolean fresh = !(reuse instanceof LinearLayout);
                LinearLayout row = fresh ? new LinearLayout(MainActivity.this) : (LinearLayout) reuse;
                if (fresh) {
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setMinimumHeight(Core.Ui.dp(MainActivity.this, 56));
                    row.setPadding(Core.Ui.dp(MainActivity.this, 24), 0, Core.Ui.dp(MainActivity.this, 24), 0);
                    ImageView iv = new ImageView(MainActivity.this);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                            Core.Ui.dp(MainActivity.this, 40), Core.Ui.dp(MainActivity.this, 40));
                    ilp.rightMargin = Core.Ui.dp(MainActivity.this, 16);
                    iv.setLayoutParams(ilp);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    TextView tv = new TextView(MainActivity.this);
                    tv.setTextSize(16);
                    tv.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    row.addView(iv);
                    row.addView(tv);
                }
                Drawable icon = Core.appIcon(MainActivity.this, pkgs.get(position));
                ((ImageView) row.getChildAt(0)).setImageDrawable(icon != null ? icon
                        : getResources().getDrawable(android.R.drawable.sym_def_app_icon));
                TextView label = (TextView) row.getChildAt(1);
                label.setText(names.get(position));
                label.setTextColor(textColor);
                return row;
            }
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle(L.t(currentLabel == null ? "第 " + slot + " 欄：選一個 App"
                        : "第 " + slot + " 欄換下一個（目前是 " + currentLabel + "）"))
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String pkg = pkgs.get(which);
                        String live = Core.appLabel(MainActivity.this, pkg);
                        sp.edit().putString(pkgKey(slot), pkg)
                                .putString(labelKey(slot), live != null ? live : names.get(which).toString()).apply();
                        rebuildCards();
                    }
                })
                .show();
    }

    /** 長按已加入的卡＝換一個／移除，兩個動作都在這裡，設定頁不再重複。 */
    private void showShortcutMenu(final Core.Shortcut s, View anchor, final int slot) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, MENU_SC_SWAP, 0, L.t("換下一個 App"));
        pm.getMenu().add(0, MENU_SC_DROP, 1, L.t("從主畫面移除"));
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                SharedPreferences sp = prefs(MainActivity.this);
                if (item.getItemId() == MENU_SC_DROP) {
                    sp.edit().remove(pkgKey(slot)).remove(labelKey(slot)).apply();
                    rebuildCards();
                    // Toast 放得下的寬度約 204px（14sp、中文字按一個字寬估），「下載完成，請點「安裝」」那條 308px 就會折成兩排；
                    // 「X 已從主畫面移除」最長約 190px，所以維持單排。位子換回「添加快捷鈕」這一排卡片當下就看不見了，不必在訊息裡再講一次。
                    toast(s.title + " 已從主畫面移除");
                } else {
                    showAppPicker(sp, slot, s.title);
                }
                return true;
            }
        });
        pm.show();
    }

    // ----------------------------------------------------------------- update

    // 車機可能一天被啟動很多次，GitHub API 只按 IP 限流，所以自動檢查限定在當天第一次。
    private static boolean checkedToday(long at) {
        if (at <= 0) {
            return false;
        }
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(at);
        Calendar now = Calendar.getInstance();
        return then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
    }

    private void checkUpdates(final boolean fromUser) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, Core.Rel> found = new HashMap<String, Core.Rel>();
                final Map<String, String> failed = new HashMap<String, String>();
                for (Core.Mod m : Core.MODS) {
                    try {
                        Core.Rel r = Core.latestRelease(m);
                        if (r == null) {
                            failed.put(m.id, "清單中找不到 APK 檔案");
                        } else {
                            found.put(m.id, r);
                        }
                    } catch (Exception e) {
                        failed.put(m.id, message(e));
                    }
                }
                // 本 App 自己排在同一輪檢查裡：同一條 Thread、同一個 CHECKED_AT，成功前不蓋時間戳。
                try {
                    Core.Rel r = Core.latestRelease(Core.SELF.api, Core.SELF.prefix, Core.SELF.page);
                    if (r == null) {
                        failed.put(Core.SELF.id, "清單中找不到 APK 檔案");
                    } else {
                        found.put(Core.SELF.id, r);
                    }
                } catch (Exception e) {
                    failed.put(Core.SELF.id, message(e));
                }
                Core.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        errors.clear();
                        errors.putAll(failed);
                        latest.putAll(found);
                        SharedPreferences.Editor ed = prefs(MainActivity.this).edit();
                        if (!found.isEmpty()) {
                            ed.putLong(Prefs.CHECKED_AT, System.currentTimeMillis());
                        }
                        for (int i = 0; i < Core.MODS.length; i++) {
                            Core.Rel r = latest.get(Core.MODS[i].id);
                            if (r == null) {
                                continue;
                            }
                            String[] k = Prefs.CACHED_KEYS[i];
                            ed.putString(k[1], r.url);
                            ed.putString(k[2], r.version);
                            ed.putString(k[3], r.fileName);
                            ed.putString(k[4], r.page);
                            ed.putString(k[5], r.publishedAt);
                            ed.putLong(k[6], r.size);
                        }
                        ed.apply();
                        refresh();
                        syncVersionLine();
                        if (fromUser) {
                            reportChecks(failed);
                        }
                    }
                });
            }
        }).start();
    }

    private void reportChecks(Map<String, String> failed) {
        StringBuilder b = new StringBuilder();
        for (Core.Mod m : Core.MODS) {
            String installed = Core.installedVersion(this, m.pkg);
            Core.Rel r = latest.get(m.id);
            b.append(L.t(m.impl)).append(L.t("　"));
            if (r == null) {
                b.append(L.t("檢查失敗：")).append(L.t(failed.get(m.id) != null ? failed.get(m.id) : "查不到版本"));
            } else if (installed == null) {
                b.append(L.t("未安裝，最新版 ")).append(L.t(r.version));
            } else if (Core.cmpVersion(installed, r.version) > 0) {
                b.append(L.t("已安裝 ")).append(L.t(Core.dispVersion(this, m.pkg))).append(L.t("，可更新到 ")).append(L.t(r.version));
            } else {
                b.append(L.t("已安裝 ")).append(L.t(Core.dispVersion(this, m.pkg))).append(L.t("，已是最新"));
            }
            b.append('\n');
        }
        Core.Rel self = latest.get(Core.SELF.id);
        String mine = Core.installedVersion(this, Core.SELF_PKG);
        b.append(L.t(Core.SELF.impl)).append(L.t("　"));
        if (self == null) {
            b.append(L.t("檢查失敗：")).append(L.t(failed.get(Core.SELF.id) != null ? failed.get(Core.SELF.id) : "查不到版本"));
        } else if (mine != null && Core.cmpVersion(mine, self.version) > 0) {
            b.append(L.t("已安裝 ")).append(L.t(Core.dispVersion(this, Core.SELF_PKG))).append(L.t("，可更新到 ")).append(L.t(self.version));
        } else {
            b.append(L.t("已安裝 ")).append(L.t(Core.dispVersion(this, Core.SELF_PKG))).append(L.t("，已是最新"));
        }
        dialog("更新檢查結果", b.toString().trim(), DL_BOTH_LABEL, new Runnable() {
            @Override
            public void run() {
                downloadAll();
            }
        }, "好", null);
    }

    private void downloadAll() {
        download(Core.sourceOf(Core.DIPLAY));
        cardHost.postDelayed(new Runnable() {
            @Override
            public void run() {
                download(Core.sourceOf(Core.DIAUTO));
            }
        }, 500);
    }

    // --------------------------------------------------------------- download

    /** 下載一條路共用，DiPlay／DiAuto 與本 App 自己都是同一套：查版號→下載→丟給安裝程式。 */
    private void download(final Core.Source s) {
        final Core.Job j = Core.job(s.id);
        if (j.active) {
            toast(s.impl + " 下載中");
            return;
        }
        if (!ensureInstallPermission()) {
            return;
        }
        if (latest.get(s.id) == null) {
            j.active = true;
            j.cancelFlag[0] = false;
            j.percent = 0;
            j.text = "正在取得最新版資訊";
            refresh();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final Core.Rel found = Core.latestRelease(s.api, s.prefix, s.page);
                        if (found == null) {
                            throw new java.io.IOException("清單中找不到 APK 檔案");
                        }
                        Core.MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                latest.put(s.id, found);
                                errors.remove(s.id);
                                j.active = false;
                                refresh();
                                download(s);
                            }
                        });
                    } catch (final Exception e) {
                        Core.MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                j.active = false;
                                j.text = null;
                                errors.put(s.id, message(e));
                                refresh();
                                toast("取得最新版失敗：" + message(e));
                            }
                        });
                    }
                }
            }).start();
            return;
        }
        startDownload(s);
    }

    private void startDownload(final Core.Source s) {
        final Core.Rel r = latest.get(s.id);
        final Core.Job j = Core.job(s.id);
        j.active = true;
        j.cancelFlag[0] = false;
        j.percent = 0;
        j.text = "準備下載";
        refresh();
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File out = new File(Core.apkDir(app), r.fileName);
                try {
                    Core.Net.download(r.url, out, r.size, new Core.Progress() {
                        long last;

                        @Override
                        public void onProgress(long done, long total) {
                            j.percent = total > 0 ? (int) Math.min(1000, done * 1000 / total) : 0;
                            j.text = "下載中 " + Core.humanSize(done)
                                    + (total > 0 ? " / " + Core.humanSize(total) : "");
                            long now = System.currentTimeMillis();
                            if (now - last > 200) {
                                last = now;
                                Core.ping();
                            }
                        }
                    }, j.cancelFlag);
                } catch (Exception e) {
                    j.active = false;
                    j.percent = 0;
                    j.text = null;
                    if (j.cancelFlag[0]) {
                        Core.ping();
                        toast(s.impl + " 下載已取消");
                        return;
                    }
                    errors.put(s.id, message(e));
                    Core.ping();
                    toast(s.impl + " 下載失敗：" + message(e));
                    return;
                }
                j.active = false;
                j.percent = 0;
                Core.ping();
                installApk(out, s);
            }
        }).start();
    }

    private void installApk(final File apk, final Core.Source s) {
        Core.MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                Uri uri = ApkProvider.uriFor(MainActivity.this, apk);
                Intent it = new Intent(Intent.ACTION_VIEW);
                it.setDataAndType(uri, "application/vnd.android.package-archive");
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try {
                    startActivity(it);
                    // 這句在車機上被切成兩排：連版號一起講是 308px 寬（14sp 實測），比 Toast 放得下的內容寬還大。
                    // 版號在卡片狀態列與右上角選單都看得到，這裡只留專案名，縮到 204px。
                    toast(s.impl + " 下載完成，請點「安裝」");
                } catch (ActivityNotFoundException e) {
                    final String path = apk.getAbsolutePath();
                    new android.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle(L.t("這台車機沒有可用的安裝程式"))
                            .setMessage(L.t("APK 已經下載完成：\n" + path
                                    + "\n\n請用車機的檔案管理員找到這個檔案再點安裝；"
                                    + "也可以從右上角選單改由瀏覽器開啟 GitHub 下載頁。"))
                            .setNeutralButton(L.t("複製檔案位置"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    copy(path);
                                }
                            })
                            .setPositiveButton(L.t("知道了"), null)
                            .show();
                } catch (Exception e) {
                    toast("開啟安裝畫面失敗：" + message(e));
                }
            }
        });
    }

    private boolean ensureInstallPermission() {
        if (Build.VERSION.SDK_INT < 26) {
            return true;
        }
        try {
            if (getPackageManager().canRequestPackageInstalls()) {
                return true;
            }
        } catch (Exception ignored) {
            return true;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(L.t("要先允許安裝應用程式"))
                .setMessage(L.t("Android 8 以上的車機，必須允許「車機Play」安裝應用程式，下載完成後才會跳出安裝畫面。"
                        + "允許後回到這裡再點一次即可。"))
                .setPositiveButton(L.t("前往設定"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (!goUnknownSources()) {
                            toast("找不到對應的設定頁，請在系統設定裡手動開啟「未知來源」");
                        }
                    }
                })
                .setNegativeButton(L.t("稍後"), null)
                .show();
        return false;
    }

    private boolean goUnknownSources() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return true;
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("chejiplay", text));
            toast("已複製檔案位置");
        } catch (Exception e) {
            toast("複製失敗");
        }
    }

    private void dialog(String title, String msg, String positive, final Runnable positiveAction,
                        String negative, Runnable negativeAction) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setTitle(L.t(title)).setMessage(L.t(msg));
        if (positive != null) {
            b.setPositiveButton(L.t(positive), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (positiveAction != null) {
                        positiveAction.run();
                    }
                }
            });
        }
        if (negative != null) {
            b.setNegativeButton(L.t(negative), null);
        }
        b.show();
    }

    static String message(Throwable e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    // ------------------------------------------------------------------- misc

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
    }

    static boolean isDark(int mode, Context c) {
        if (mode == Prefs.THEME_LIGHT) {
            return false;
        }
        if (mode == Prefs.THEME_DARK) {
            return true;
        }
        int night = getResources(c).uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static android.content.res.Configuration getResources(Context c) {
        return c.getResources().getConfiguration();
    }

    private void toast(String s) {
        Toast.makeText(this,L.t(s), Toast.LENGTH_LONG).show();
    }

    interface Prefs {
        String NAME = "chejiplay";
        String THEME = "theme";
        /** 語言模式，值就是 {@link L} 的常數；預設 0＝自動（照車機語系）。 */
        String LANG = "lang";
        String CHECKED_AT = "checked_at";
        /** Google Maps 要手動開啟；自訂欄位預設 0 個（不顯示），SC_PKG／SC_LABEL 是第 1 欄、SC_PKG2／SC_LABEL2 是第 2 欄。 */
        String SC_MAPS = "sc_maps";
        String SC_PKG = "sc_pkg";
        String SC_LABEL = "sc_label";
        String SC_PKG2 = "sc_pkg2";
        String SC_LABEL2 = "sc_label2";
        String SC_COUNT = "sc_count";
        int THEME_AUTO = 0;
        int THEME_LIGHT = 1;
        int THEME_DARK = 2;
        String[][] CACHED_KEYS = {
                {"diplay", "url_diplay", "ver_diplay", "file_diplay", "page_diplay", "pub_diplay", "size_diplay"},
                {"diauto", "url_diauto", "ver_diauto", "file_diauto", "page_diauto", "pub_diauto", "size_diauto"}
        };
    }
}
