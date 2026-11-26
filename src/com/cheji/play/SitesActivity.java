package com.cheji.play;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 右上角選單的「網站與功能說明」頁面：兩款專案的定位、官方網站與下載位置。 */
public class SitesActivity extends android.app.Activity {

    static final String[][] LINKS_PLAY = {
            {"官方網站", "https://diplay.org/zh-hant/"},
            {"Github社群官網", "https://shihabal3amri.github.io/DiPlay/"},
            {"Github社群連結", "https://github.com/shihabal3amri/DiPlay"},
            {"下載連結", "https://github.com/shihabal3amri/DiPlay/releases"}
    };

    static final String[][] LINKS_AUTO = {
            {"Github社群官網", "https://shihabal3amri.github.io/DiAuto/"},
            {"Github社群連結", "https://github.com/shihabal3amri/DiAuto"},
            {"下載連結", "https://github.com/shihabal3amri/DiAuto/releases"}
    };

    /** 車機Play 自己也是第三個更新來源，所以同一頁要有一組自己的連結；第一列跟另兩款專案一樣是社群官網。 */
    static final String[][] LINKS_SELF = {
            {"Github社群官網", "https://jin850921.github.io/chejiplay/"},
            {"專案頁面", "https://github.com/jin850921/chejiplay"},
            {"最新版 APK", "https://github.com/jin850921/chejiplay/releases"}
    };

    private TextView statePlay, stateAuto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        L.sync(this);
        int mode = MainActivity.prefs(this).getInt(MainActivity.Prefs.THEME, MainActivity.Prefs.THEME_AUTO);
        boolean dark = MainActivity.isDark(mode, this);
        setTheme(dark ? R.style.Theme_ChejiPlay_Dark : R.style.Theme_ChejiPlay_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sites);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Core.Ui.color(this, R.attr.cpBg));
            if (!dark && android.os.Build.VERSION.SDK_INT >= 23) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        int text = Core.Ui.color(this, R.attr.cpText);
        int dim = Core.Ui.color(this, R.attr.cpTextDim);
        TextView title = (TextView) findViewById(R.id.siteTitleText);
        title.setText(L.t("網站與功能說明"));
        title.setTextColor(text);
        View back = findViewById(R.id.btnBack);
        Core.Ui.headerButton(this, back);
        ((ImageView) back).setImageDrawable(Core.Ui.tinted(this, R.drawable.ic_back, text));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        LinearLayout host = (LinearLayout) findViewById(R.id.siteHost);
        statePlay = addGroup(host, Core.DIPLAY.impl + "（" + Core.DIPLAY.surface + "）",
                Core.DIPLAY.feature + "　" + Core.DIPLAY.note, LINKS_PLAY, Core.DIPLAY.impl);
        syncState(statePlay, Core.DIPLAY);
        stateAuto = addGroup(host, Core.DIAUTO.impl + "（" + Core.DIAUTO.surface + "）",
                Core.DIAUTO.feature + "　" + Core.DIAUTO.note, LINKS_AUTO, Core.DIAUTO.impl);
        syncState(stateAuto, Core.DIAUTO);
        // 第三組是本 App 自己：批次下載不含它，這件事寫在說明頁而不是只靠選單列名暗示。
        addGroup(host, Core.SELF.impl + "（本 App）",
                "CarPlay／Android Auto 的下載與啟動入口。　每日檢查一次本版號，有新版只亮右上角 ⋮ 的紅點；「"
                        + MainActivity.DL_BOTH_LABEL + "」不含本 App，更新車機Play 是選單裡單獨那列。",
                LINKS_SELF, Core.SELF.impl).setText(L.t(selfState()));

        TextView foot = (TextView) findViewById(R.id.siteFooter);
        foot.setText(L.t("點一下列即可用車機瀏覽器開啟；長按可複製網址。\n"
                + "下載位置都是 GitHub Releases 的官方資產，本 App 不會改動 APK 內容。"));
        foot.setTextColor(dim);
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncState(statePlay, Core.DIPLAY);
        syncState(stateAuto, Core.DIAUTO);
    }

    /** 本 App 的狀態：這一頁是它自己畫的，跑著就一定已安裝，版號照 `version_name(version_code)` 寫。 */
    private String selfState() {
        String v = Core.dispVersion(this, Core.SELF_PKG);
        return v == null ? "執行中" : "已安裝 " + v;
    }

    private void syncState(TextView tv, Core.Mod m) {
        if (tv == null) {
            return;
        }
        String installed = Core.dispVersion(this, m.pkg);
        tv.setText(L.t(installed == null ? "尚未安裝" : "已安裝 " + installed));
    }

    /** 一組＝標題列＋狀態＋說明句＋連結列；回傳狀態 TextView 讓 onResume 只改文字、不重建畫面。 */
    private TextView addGroup(LinearLayout host, String title, String featureLine, String[][] links, String impl) {
        int text = Core.Ui.color(this, R.attr.cpText);
        int dim = Core.Ui.color(this, R.attr.cpTextDim);
        int accent = Core.Ui.color(this, R.attr.cpAccent);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(Core.Ui.dp(this, 4), Core.Ui.dp(this, 18), Core.Ui.dp(this, 4), Core.Ui.dp(this, 2));
        TextView name = new TextView(this);
        name.setText(L.t(title));
        name.setTextSize(17);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setTextColor(text);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(name);
        TextView state = new TextView(this);
        state.setTextSize(12);
        state.setTextColor(accent);
        head.addView(state);
        host.addView(head);

        TextView feature = new TextView(this);
        feature.setText(L.t(featureLine));
        feature.setTextSize(12);
        feature.setTextColor(dim);
        feature.setPadding(Core.Ui.dp(this, 4), 0, Core.Ui.dp(this, 4), Core.Ui.dp(this, 8));
        host.addView(feature);

        for (String[] link : links) {
            View v = row(link[0], link[1]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Core.Ui.dp(this, 6);
            v.setLayoutParams(lp);
            host.addView(v);
            TextView d = new TextView(this);
            d.setText(L.t(desc(link[0], impl)));
            d.setTextSize(12);
            d.setTextColor(dim);
            d.setPadding(Core.Ui.dp(this, 6), Core.Ui.dp(this, 4), Core.Ui.dp(this, 6), 0);
            host.addView(d);
        }
        return state;
    }

    private View row(String label, final String url) {
        View v = getLayoutInflater().inflate(R.layout.item_site, host(), false);
        int text = Core.Ui.color(this, R.attr.cpText);
        ((TextView) v.findViewById(R.id.siteTitle)).setText(L.t(label));
        ((TextView) v.findViewById(R.id.siteTitle)).setTextColor(text);
        TextView u = (TextView) v.findViewById(R.id.siteUrl);
        u.setText(L.t(url));
        u.setTextColor(Core.Ui.color(this, R.attr.cpAccent));
        Core.Ui.card(this, v, Core.Ui.color(this, R.attr.cpSurface), Core.Ui.color(this, R.attr.cpOutline));
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(url);
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("url", url));
                    Toast.makeText(SitesActivity.this,L.t("已複製網址"), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(SitesActivity.this,L.t("複製失敗"), Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        return v;
    }

    private LinearLayout hostCache;

    private LinearLayout host() {
        if (hostCache == null) {
            hostCache = (LinearLayout) findViewById(R.id.siteHost);
        }
        return hostCache;
    }

    static String desc(String label, String impl) {
        if (label.equals("官方網站")) {
            return "繁中文指南：相容性檢查、安裝教學、版本差異與疑難排解。";
        }
        if (label.equals("Github社群官網")) {
            return "下載頁面：最新公開發佈版、安裝步驟與畫面截圖。";
        }
        if (label.equals("Github社群連結")) {
            return "原始碼與議題清單（GPL-3.0），可查版本變動與回報問題。";
        }
        if (label.equals("專案頁面")) {
            return "原始碼、發行清單與畫面示意圖；簽金鑰與密碼檔不放在這裡。";
        }
        if (label.equals("最新版 APK")) {
            return "本 App 自己的更新來源，右上角紅點查的就是這裡。";
        }
        return impl + " 最新版 APK、原始碼封包與 SHA-256 檢查碼。";
    }

    private void open(String url) {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Exception e) {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Toast.makeText(this,L.t("這台車機沒有瀏覽器，網址已複製"), Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this,L.t("無法開啟：" + url), Toast.LENGTH_LONG).show();
            }
        }
    }

}
