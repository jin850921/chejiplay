package com.cheji.play;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 語言層：原始碼裡的所有顯示文字一律寫繁中，那是基準語言，{@link #t(String)} 依當時語言換掉。
 *
 * <p>三件事讓它可以安全地套在既有代碼上：</p>
 * <ul>
 * <li>繁中模式直接原樣回傳，日常那條路與改版前完全相同。</li>
 * <li>字典查不到的字串原樣回傳，所以漏翻最多是混語言，不會變成空白。</li>
 * <li>整句查不到時，會用「最長前綴／最長後綴」把串接好的句子拆開逐段翻，
 * 因此 {@code "已安裝 " + ver + "\n長按可換／移除"} 這種組合字串也能翻好，不必改組裝代碼。</li>
 * </ul>
 *
 * <p>品牌名（車機Play／DiPlay／DiAuto／Google Maps）與比對用的字（導航、地圖等 App 名稱提示）
 * 刻意不進字典，換語言時那些仍然照原樣出現。</p>
 */
public final class L {
    private L() {
    }

    public static final int AUTO = 0;
    public static final int ZH_TW = 1;
    public static final int ZH_CN = 2;
    public static final int EN = 3;
    public static final int JA = 4;
    public static final int KO = 5;

    /** 選單上每一種語言都用自己的母語寫法，不自翻，換語言時才不會找不到那一列。 */
    public static final String[] NAMES = {"自動", "繁體中文", "簡體中文", "English", "日本語", "한국어"};

    private static volatile int mode = ZH_TW;

    /** 目前實際生效的語言（「自動」已解析成其中一種）。 */
    public static int mode() {
        return mode;
    }

    /** 存進設定的選擇，可能是「自動」。 */
    public static int saved(Context c) {
        return MainActivity.prefs(c).getInt(MainActivity.Prefs.LANG, AUTO);
    }

    /** 「自動」看系統語系；中文一律繁中打底，只有明確是簡體環境才走簡中。 */
    public static int resolve(Context c) {
        int m = saved(c);
        if (m != AUTO) {
            return m;
        }
        Locale loc = c.getResources().getConfiguration().locale;
        if (loc == null) {
            return ZH_TW;
        }
        String lang = loc.getLanguage() == null ? "" : loc.getLanguage().toLowerCase(Locale.US);
        String country = loc.getCountry() == null ? "" : loc.getCountry().toUpperCase(Locale.US);
        if ("ja".equals(lang)) {
            return JA;
        }
        if ("ko".equals(lang)) {
            return KO;
        }
        if ("zh".equals(lang)) {
            return ("CN".equals(country) || "SG".equals(country) || "Hans".equals(loc.getScript()))
                    ? ZH_CN : ZH_TW;
        }
        if ("en".equals(lang)) {
            return EN;
        }
        return ZH_TW;
    }

    /** 每個畫面在 onCreate 開頭同步一次；換語言後是「存設定→sync→recreate」，不必重啟 App。 */
    public static void sync(Context c) {
        mode = resolve(c);
    }

    public static String t(String zhTw) {
        if (zhTw == null || mode == ZH_TW) {
            return zhTw;
        }
        String s = tr(zhTw, 0);
        return (mode == EN || mode == KO) ? latin(s) : s;
    }

    /**
     * 拉丁字元的語言不用全形標點。字典裡手寫的句子我已經改過，會漏的是「組合出來的字串」：
     * 品牌名（CarPlay／Android Auto）這類刻意不翻的欄位被括號夾住時，括號本身沒被字典咬到。
     * 只在英／韓文跑這一道，繁中要原樣，日文與簡中的（）：也要留全形。
     */
    private static String latin(String s) {
        if (s.length() == 0) {
            return s;
        }
        return s.replace('（', '(').replace('）', ')').replace('／', '/')
                .replace("　", "  ").replace("：", ": ").replace("；", "; ")
                .replace("，", ", ").replace("、", ", ").replace("。", ". ");
    }

    private static String tr(String s, int depth) {
        String[] hit = WORDS.get(s);
        if (hit != null) {
            return hit[mode - 2];
        }
        if (s.length() == 0 || depth >= 8) {
            return s;
        }
        for (String key : BY_LENGTH) {
            if (s.length() > key.length() && s.startsWith(key)) {
                return at(key) + tr(s.substring(key.length()), depth + 1);
            }
        }
        for (String key : BY_LENGTH) {
            if (s.length() > key.length() && s.endsWith(key)) {
                return tr(s.substring(0, s.length() - key.length()), depth + 1) + at(key);
            }
        }
        // 前後都貼不上字典時（動態內容夾在中間），再找一個出現在句中的片段拆開兩邊遞迴。
        // 只接受兩個字以上的片段，避免「。」這種單字標點在別人名稱裡亂咬。
        for (String key : BY_LENGTH) {
            int pos = key.length() > 1 ? s.indexOf(key) : -1;
            if (pos >= 0) {
                return tr(s.substring(0, pos), depth + 1) + at(key)
                        + tr(s.substring(pos + key.length()), depth + 1);
            }
        }
        return s;
    }

    private static String at(String key) {
        String[] row = WORDS.get(key);
        return row == null ? key : row[mode - 2];
    }

    private static final Map<String, String[]> WORDS = new HashMap<String, String[]>();
    private static final List<String> BY_LENGTH = new ArrayList<String>();

    private static void put(String zhTw, String cn, String en, String ja, String ko) {
        WORDS.put(zhTw, new String[]{cn, en, ja, ko});
    }

    static {
        // ---------------------------------------------------------- 主畫面骨架
        put("CarPlay／Android Auto 的下載與啟動入口", "CarPlay／Android Auto 的下载与启动入口",
                "Download and launch hub for CarPlay / Android Auto",
                "CarPlay／Android Auto のダウンロード＆起動の入口", "CarPlay／Android Auto 다운로드·실행 모음");
        put("正在讀取車機上的安裝狀態", "正在读取车机上的安装状态",
                "Reading install status on the head unit",
                "車のユニットのインストール状態を読み込んでいます", "차유닛의 설치 상태를 읽는 중");
        put("DiPlay、DiAuto 是 GitHub 上的社群獨立專案，與 Apple、Google、BYD 無隸屬關係。",
                "DiPlay、DiAuto 是 GitHub 上的社区独立项目，与 Apple、Google、BYD 无隶属关系。",
                "DiPlay and DiAuto are independent GitHub community projects, not affiliated with Apple, Google, or BYD.",
                "DiPlay・DiAuto は GitHub 上の独立したコミュニティプロジェクトで、Apple・Google・BYD とは関係ありません。",
                "DiPlay·DiAuto는 GitHub의 독립 커뮤니티 프로젝트로 Apple, Google, BYD와 무관합니다.");
        put("本 App 只負責抓最新版 APK 與開啟，下載位置一律是 GitHub Releases 官方資產。\n",
                "本 App 只负责获取最新版 APK 与打开，下载位置一律是 GitHub Releases 官方资产。\n",
                "This app only fetches the latest APK and opens it; downloads always come from official GitHub Releases assets.\n",
                "このアプリは最新版 APK の取得と起動のみ。ダウンロード元は常に GitHub Releases の公式アセットです。\n",
                "이 앱은 최신 APK를 받아 여는 역할만 하며, 다운로드는 항상 GitHub Releases 공식 자산입니다.\n");
        put("車機Play 自身也走同一條更新路，新版本在右上角選單裡下載。\n",
                "车机Play 自身也走同一条更新路径，新版本在右上角菜单里下载。\n",
                "車機Play updates itself the same way; a new version is downloaded from the three-dot menu.\n",
                "車機Play 本体も同じ更新経路で、新版は右上のメニューからダウンロードします。\n",
                "車機Play 자체도 같은 업데이트 경로를 사용하며, 새 버전은 우측 상단 메뉴에서 다운로드합니다.\n");
        put("兩個專案以 BYD 車機為主力，其他品牌不保證可用。\n", "两个项目以 BYD 车机为主，其他品牌不保证可用。\n",
                "Both projects target BYD head units; other brands are not guaranteed.\n",
                "両プロジェクトは BYD 車載機が主対象で、他ブランドは動作保証外です。\n",
                "두 프로젝트는 BYD 차량을 주로 겨냥하며 다른 브랜드는 보장하지 않습니다.\n");
        put("可以把「車機Play」加入車機的開機自啟動清單，之後每次開車機螢幕就自動打開這裡，點卡片立刻進入 CarPlay 或 Android Auto。",
                "可以把「车机Play」加入车机的开机自启动列表，之后每次开车机屏幕就自动打开这里，点卡片立刻进入 CarPlay 或 Android Auto。",
                "Add 車機Play to the head unit's boot-start list and this screen opens every time it powers on; tap a card to enter CarPlay or Android Auto right away.",
                "車載機の自動起動リストに「車機Play」を追加すると、電源を入れるたびにこの画面が開き、カードをタップするだけで CarPlay／Android Auto を起動できます。",
                "차량의 부팅 시 자동 실행 목록에 「車機Play」를 추가하면 화면이 켜질 때마다 여기가 열리고, 카드를 누르면 바로 CarPlay 또는 Android Auto로 들어갑니다.");
        put("自啟動清單的名稱與位置依車機品牌不同，能不能加入、可以加幾個都由車機系統決定。",
                "自启动列表的名称与位置因车机品牌不同，能不能加入、可以加几个都由车机系统决定。",
                "The boot-start list is named and placed differently by brand; whether apps can be added, and how many, is up to the head unit's system.",
                "自動起動リストの名前や場所はメーカーにより異なり、追加可否や件数は車載機のシステム次第です。",
                "자동 실행 목록의 이름과 위치는 브랜드마다 다르며, 추가 여부와 개수는 차량 시스템이 결정합니다.");
        // -------------------------------------------------------------- 卡片
        put("在 Android 車機上接收 iPhone 的 CarPlay，支援有線與無線連線。",
                "在 Android 车机上接收 iPhone 的 CarPlay，支持有线与无线连接。",
                "Receives iPhone CarPlay on an Android head unit, wired and wireless.",
                "Android 車載機で iPhone の CarPlay を受信。有線・無線の両方に対応。",
                "Android 차량에서 iPhone CarPlay를 수신. 유·무선 연결 지원.");
        put("安裝在車機上，不是裝在 iPhone。公開預覽版，非 Apple 認證產品。",
                "安装在车机上，不是装在 iPhone。公开预览版，非 Apple 认证产品。",
                "Installs on the head unit, not on the iPhone. Public preview; not an Apple-certified product.",
                "iPhone ではなく車載機にインストールします。公開プレビュー版で、Apple 認証製品ではありません。",
                "iPhone이 아닌 차량에 설치합니다. 공개 프리뷰 버전이며 Apple 인증 제품이 아닙니다.");
        put("在 Android 車機上接收 Android Auto，把手機的地圖與音樂顯示在車螢幕上。",
                "在 Android 车机上接收 Android Auto，把手机的地图与音乐显示在车屏幕上。",
                "Receives Android Auto on an Android head unit, showing your phone's maps and music on the car screen.",
                "Android 車載機で Android Auto を受信し、スマートフォンの地図と音楽을車の画面に表示합니다。",
                "Android 차량에서 Android Auto를 수신하여 스마트폰의 지도와 음악을 차량 화면에 표시합니다.");
        put("需搭配 Android 手機使用（內建 Android Auto 支援），這不是 CarPlay。",
                "需搭配 Android 手机使用（内置 Android Auto 支持），这不是 CarPlay。",
                "Requires an Android phone (built-in Android Auto support). This is not CarPlay.",
                "Android スマートフォンが必要（Android Auto 対応を内蔵）。CarPlay とは異なります。",
                "Android 스마트폰이 필요합니다(Android Auto 기본 지원). CarPlay가 아닙니다.");
        put("以 ", "由 ", "Powered by ", "", "");
        put(" 實現", " 实现", "", " で実現", " 기반");
        put("直接開啟", "直接打开", "Open directly", "直接起動", "바로 열기");
        put("添加快捷鈕", "添加快捷方式", "Add a shortcut", "ショートカットを追加", "단축 버튼 추가");
        put("點一下選一個 App", "点一下选一个应用", "Tap to choose an app", "タップしてアプリを選択", "눌러 앱 선택");
        put("尚未加入", "尚未添加", "Not added", "未追加", "미추가");
        put("尚未安裝", "尚未安装", "Not installed", "未インストール", "미설치");
        put("已安裝 ", "已安装 ", "Installed ", "インストール済み ", "설치됨 ");
        put("\n長按可換／移除", "\n长按可换／移除", "\nlong-press to swap or remove",
                "\n長押しで差し替え／削除", "\n길게 눌러 교체/제거");
        put("這台車機還沒有 Google Maps。請用車機上的 Google Play 安裝，裝好後再點一次即可。",
                "这台车机还没有 Google Maps。请用車机上的 Google Play 安装，装好后再点一次即可。",
                "This head unit doesn't have Google Maps yet. Install it from Google Play on the head unit, then tap again.",
                "この車載機には Google Maps がまだありません。車載機の Google Play でインストールしてからもう一度タップしてください。",
                "이 차량에 Google Maps가 아직 없습니다. 차량의 Google Play에서 설치한 뒤 다시 누르세요.");
        put("查不到這套 App（可能已從車機移除）。長按這張卡可以換一個或移除。",
                "找不到该应用（可能已从车机移除）。长按这张卡可以更换或移除。",
                "This app can't be found (it may have been removed from the head unit). Long-press the card to swap or remove it.",
                "このアプリが見つかりません（車載機から削除された可能性あり）。カードを長押しで差し替えまたは削除できます。",
                "앱을 찾을 수 없습니다(차량에서 제거되었을 수 있음). 카드를 길게 누르면 교체하거나 제거할 수 있습니다.");
        put("尚未檢查更新", "尚未检查更新", "Not checked yet", "未確認", "미확인");
        put("上次檢查更新：", "上次检查更新：", "Last checked: ", "最終確認：", "마지막 확인: ");
        put(" · 最新版 ", " · 最新版 ", " · latest ", " · 最新 ", " · 최신 ");
        put(" · 尚未檢查更新", " · 尚未检查更新", " · not checked yet", " · 未確認", " · 미확인");
        put(" · 右上角選單可更新到 ", " · 右上角菜单可更新到 ", " · update to ", " · 右上メニューで更新：", " · 우측 메뉴에서 업데이트：");
        put(" · 已是最新", " · 已是最新", " · up to date", " · 最新", " · 최신");
        put("檢查失敗：", "检查失败：", "Check failed: ", "確認に失敗：", "확인 실패: ");
        put("查不到版本", "查不到版本", "version unknown", "バージョン不明", "버전 확인 안 됨");
        // ------------------------------------------------------------ 右上角選單
        put("檢查更新（只查版號，不下載）", "检查更新（只查版本号，不下载）",
                "Check for updates (versions only, no download)", "アップデート確認（版番号のみ・ダウンロードなし）",
                "업데이트 확인(버전만, 다운로드 없음)");
        put("DiPlay＋DiAuto 兩個都下載", "DiPlay＋DiAuto 两个都下载", "Download DiPlay and DiAuto",
                "DiPlay＋DiAuto をまとめて入手", "DiPlay＋DiAuto 모두 다운로드");
        put("主畫面快捷鈕", "主屏幕快捷方式", "Home-screen shortcuts", "ホーム画面のショートカット", "홈 화면 단축 버튼");
        put("網站與功能說明", "网站与功能说明", "Sites and how it works", "サイトと機能の説明", "사이트 및 기능 안내");
        put("介面語言", "界面语言", "Language", "表示言語", "언어");
        put("主題：明／暗", "主题：浅色／深色", "Theme: light / dark", "テーマ：明／暗", "테마: 밝게／어둡게");
        put("下載 ", "下载 ", "Download ", "ダウンロード ", "다운로드 ");
        put("更新 ", "更新 ", "Update ", "更新 ", "업데이트 ");
        put("最新版", "最新版", "latest", "最新版", "최신");
        put("（已是最新）", "（已是最新）", " (up to date)", "（最新）", "(최신)");
        put("取消下載 ", "取消下载 ", "Cancel download ", "ダウンロード中止 ", "다운로드 취소 ");
        put(" 下載取消中", " 下载取消中", " cancelling download", " を中止しています", " 취소 중");
        // ------------------------------------------------------------ 對話框通用
        put("知道了", "知道了", "Got it", "了解", "확인");
        put("好", "好", "OK", "OK", "확인");
        put("取消", "取消", "Cancel", "キャンセル", "취소");
        put("完成", "完成", "Done", "完了", "완료");
        put("稍後", "稍后", "Later", "あとで", "나중에");
        put("前往設定", "前往设置", "Open settings", "設定へ", "설정으로");
        put("複製失敗", "复制失败", "Copy failed", "コピーできません", "복사 실패");
        put("。", "。", ".", "。", ".");
        put("）", "）", ")", "）", ")");
        put("（", "（", " (", "（", " (");
        put("：", "：", ": ", "：", ": ");
        put("　", "　", "  ", "　", "  ");
        put("介面主題", "界面主题", "Appearance", "画面テーマ", "화면 테마");
        put("跟隨車機系統", "跟随车机系统", "Follow the head unit", "車載機の設定に従う", "차량 시스템 따르기");
        put("淺色", "浅色", "Light", "ライト", "밝게");
        put("深色", "深色", "Dark", "ダーク", "어둡게");
        // -------------------------------------------------------------- 開啟 App
        put("找不到 ", "找不到 ", "Can't find ", "見つかりません：", "찾을 수 없음: ");
        put("無法開啟 ", "无法打开 ", "Can't open ", "開けません：", "열 수 없음: ");
        put("無法開啟：", "无法打开：", "Can't open: ", "開けません：", "열 수 없음: ");
        put("找不到啟動項目", "找不到启动项", "No launch entry", "起動項目が見つかりません", "실행 항목을 찾을 수 없음");
        put(" 已安裝，但這台車機沒有取得它的主畫面圖示。",
                " 已安装，但这台车机没有取得它的主屏幕图标。",
                " is installed, but this head unit can't get its home-screen icon.",
                " はインストール済みですが、この車載機でホーム画面のアイコンを取得できません。",
                " 설치되어 있지만 이 차량에서 홈 화면 아이콘을 가져오지 못했습니다.");
        put(" 已安裝，但這台車機沒有取得它的主畫面圖示。請從車機的應用列表開啟 ",
                " 已安装，但这台车机没有取得它的主屏幕图标。请从车机的应用列表打开 ",
                " is installed, but this head unit can't get its home-screen icon. Open ",
                " はインストール済みですが、この車載機でアイコンを取得できません。アプリ一覧から ",
                " 설치되어 있지만 아이콘을 가져오지 못했습니다. 앱 목록에서 ");
        put("請從車機的應用列表開啟。", "请从车机的应用列表打开。", "Open it from the head unit's app list.",
                "車載機のアプリ一覧から起動してください。", "차량의 앱 목록에서 여세요.");
        // ------------------------------------------------------------ 快捷鈕設定
        put("Google Maps 快捷鈕", "Google Maps 快捷方式", "Google Maps shortcut", "Google Maps ショートカット",
                "Google Maps 단축 버튼");
        put("只負責開啟；這台車機目前查不到 Google Maps", "只负责打开；这台车机目前找不到 Google Maps",
                "Opens only; Google Maps isn't on this head unit", "起動のみ。この車載機では Google Maps が見つかりません",
                "열기 전용; 이 차량에서 Google Maps를 찾을 수 없음");
        put("只負責開啟，更新請用車機上的 Google Play", "只负责打开，更新请用车机上的 Google Play",
                "Opens only; update it with Google Play on the head unit", "起動のみ。更新は車載機の Google Play で",
                "열기 전용, 업데이트는 차량의 Google Play에서");
        put("手動添加欄位", "手动添加栏位", "Manual slots", "手動追加欄", "수동 추가 칸");
        put("兩欄都是你自己挑的 App；選「不顯示」就不出現卡片，改成較少也只是收起來，選過的 App 都還在。",
                "两栏都是你自己挑的应用；选「不显示」就不出现卡片，改成较少也只是收起来，选过的应用都还在。",
                "Both slots hold apps you choose. \"Don't show\" hides the cards; picking fewer only tucks them away, and the apps you chose stay saved.",
                "2つの欄はどちらも自分で選ぶアプリです。「表示しない」にするとカードは出ず、減らしても隠れるだけで選んだアプリは残ります。",
                "두 칸 모두 직접 고른 앱입니다. '표시 안 함'을 고르면 카드가 사라지고, 줄이기만 하면 접히는 것과 같아 선택한 앱은 그대로 남습니다.");
        put("「添加快捷鈕」這張卡最多兩張：還沒加入時點一下是選 App，加入後點一下就是開啟那個 App，",
                "「添加快捷方式」这张卡最多两张：还没添加时点一下是选应用，添加后点一下就是打开那个应用，",
                "Up to two \"Add a shortcut\" cards: before adding, a tap picks an app; after adding, the same tap opens it. ",
                "「ショートカットを追加」のカードは最大2枚。追加前はタップでアプリを選び、追加後はタップでそのアプリを起動します。",
                "「단축 버튼 추가」 카드는 최대 두 장: 추가 전에는 눌러 앱을 고르고, 추가 후에는 눌러 그 앱을 엽니다.");
        put("長按可以換一個或從主畫面移除。\n快捷鈕都只負責開啟，不會下載也不會檢查更新，",
                "长按可以换一个或从主屏幕移除。\n快捷方式都只负责打开，不会下载也不会检查更新，",
                "Long-press to swap one or remove it from the home screen.\nShortcuts only open apps; they never download or check for updates, ",
                "長押しで差し替えまたはホーム画面から削除できます。ショートカットは起動のみで、ダウンロードもアップデート確認もしません。",
                "길게 누르면 교체하거나 홈 화면에서 제거할 수 있습니다. 단축 버튼은 열기만 하고 다운로드나 업데이트 확인은 하지 않습니다.");
        put("所以 Google Maps 一律交給車機上的 Google Play 維護。", "所以 Google Maps 一律交给车机上的 Google Play 维护。",
                "so Google Maps is maintained by Google Play on the head unit.", "Google Maps は車載機の Google Play が管理します。",
                "따라서 Google Maps는 차량의 Google Play가 관리합니다.");
        put("主畫面幾個手動欄位", "主屏幕几个手动栏位", "How many manual slots", "ホーム画面の手動欄の数", "홈 화면 수동 칸 수");
        put("不顯示", "不显示", "Don't show", "表示しない", "표시 안 함");
        put("1 個", "1 个", "1", "1 個", "1개");
        put("2 個", "2 个", "2", "2 個", "2개");
        put(" 個", " 个", "", " 個", "개");
        put("第 ", "第 ", "Slot ", "第", "칸 ");
        put(" 欄：選一個 App", " 栏：选一个应用", ": choose an app", " 欄：アプリを選ぶ", ": 앱 선택");
        put(" 欄換下一個（目前是 ", " 栏换下一个（目前是 ", " → next app (currently ", " 欄を差し替え（現在 ",
                " 다음 앱으로 교체 (현재 ");
        put("查不到可啟動的 App", "找不到可启动的应用", "No launchable apps", "起動できるアプリが見つかりません", "실행 가능한 앱 없음");
        put("這台車機沒有回報任何「有主畫面圖示」的 App，", "这台车机没有报告任何「有主屏幕图标」的应用，",
                "This head unit reported no apps with a home-screen icon, ",
                "この車載機はホーム画面のアイコンを持つアプリを返さなかったため、", "이 차량이 홈 화면 아이콘이 있는 앱을 반환하지 않아 ");
        put("所以暫時無法加入自訂快捷鈕。", "所以暂时无法添加自定义快捷方式。", "so custom shortcuts can't be added yet.",
                "カスタムショートカットは追加できません。", "당장은 사용자 지정 단축 버튼을 추가할 수 없습니다.");
        put("換下一個 App", "换下一个应用", "Next app", "別のアプリに差し替え", "다음 앱으로 교체");
        put("從主畫面移除", "从主屏幕移除", "Remove from home screen", "ホーム画面から削除", "홈 화면에서 제거");
        put(" 已從主畫面移除", " 已从主屏幕移除", " removed from home screen",
                " はホーム画面から削除しました", " 홈 화면에서 제거했습니다");
        // -------------------------------------------------------------- 下載與安裝
        put(" 下載中", " 下载中", " is downloading", " をダウンロード中", " 다운로드 중");
        put(" 下載中，長按卡片可取消", " 下载中，长按卡片可取消",
                " is downloading. Long-press the card to cancel.", " をダウンロード中です。カードを長押しで中止",
                " 다운로드 중. 카드를 길게 눌러 취소");
        put("正在取得最新版資訊", "正在获取最新版信息", "Fetching release info", "最新情報を取得中", "최신 정보 가져오는 중");
        put("準備下載", "准备下载", "Preparing download", "ダウンロード準備中", "다운로드 준비");
        put("下載中 ", "下载中 ", "Downloading ", "ダウンロード中 ", "다운로드 중 ");
        put("%　長按可取消", "%　长按可取消", "%  long-press to cancel", "%　長押しで中止", "% 길게 눌러 취소");
        put("取消中", "取消中", "Cancelling", "中止中", "취소 중");
        put(" 下載已取消", " 下载已取消", " download cancelled", " のダウンロードを中止しました", " 다운로드 취소됨");
        put(" 下載失敗：", " 下载失败：", " download failed: ", " のダウンロードに失敗：", " 다운로드 실패: ");
        put(" 下載完成，請點「安裝」", " 下载完成，请点「安装」", " downloaded. Tap Install.",
                " のダウンロード完了。「インストール」をタップしてください", " 다운로드 완료. 「설치」를 누르세요");
        put("取得最新版失敗：", "获取最新版失败：", "Could not get the latest version: ", "最新版の取得に失敗：",
                "최신 버전 가져오기 실패: ");
        put("清單中找不到 APK 檔案", "列表里找不到 APK 文件", "No APK found in the release list",
                "リリース一覧に APK がありません", "목록에 APK 파일이 없음");
        put("更新檢查結果", "更新检查结果", "Update check results", "アップデート確認の結果", "업데이트 확인 결과");
        put("未安裝，最新版 ", "未安装，最新版 ", "Not installed. Latest ", "未インストール（最新 ", "미설치, 최신 ");
        put("，可更新到 ", "，可更新到 ", ", can update to ", "、次の版に更新可能 ", ", 업데이트 가능 ");
        put("，已是最新", "，已是最新", ", already latest", "、最新です", ", 최신버전");
        put("這台車機沒有可用的安裝程式", "这台车机没有可用的安装程序", "No installer available on this head unit",
                "この車載機には利用可能なインストーラーがありません", "이 차량에 사용 가능한 설치 프로그램이 없음");
        put("APK 已經下載完成：\n", "APK 已下载完成：\n", "APK downloaded:\n", "APK のダウンロードが完了しました：\n",
                "APK 다운로드 완료:\n");
        put("\n\n請用車機的檔案管理員找到這個檔案再點安裝；", "\n\n请用車机的文件管理器找到这个文件再点安装；",
                "\n\nFind the file with the head unit's file manager and tap install, ",
                "\n\n車載機のファイルマネージャーでこのファイルを開いてインストールするか、",
                "\n\n차량의 파일 관리자에서 이 파일을 찾아 설치를 누르거나");
        put("也可以從右上角選單改由瀏覽器開啟 GitHub 下載頁。", "也可以从右上角菜单改用浏览器打开 GitHub 下载页。",
                "or open the GitHub download page from the browser via the three-dot menu.",
                "右上のメニューからブラウザで GitHub のダウンロードページを開くこともできます。",
                "우측 상단 메뉴의 브라우저로 GitHub 다운로드 페이지를 열 수도 있습니다.");
        put("複製檔案位置", "复制文件位置", "Copy file path", "パスをコピー", "경로 복사");
        put("已複製檔案位置", "已复制文件位置", "File path copied", "パスをコピー했습니다", "경로를 복사했습니다");
        put("開啟安裝畫面失敗：", "打开安装界面失败：", "Could not open the installer: ", "インストール画面を開けませんでした：",
                "설치 화면 열기 실패: ");
        put("要先允許安裝應用程式", "需要先允许安装应用", "Allow app installs first", "先にアプリのインストールを許可してください",
                "설치 권한을 먼저 허용하세요");
        put("Android 8 以上的車機，必須允許「車機Play」安裝應用程式，下載完成後才會跳出安裝畫面。",
                "Android 8 以上的车机，必须允许「车机Play」安装应用，下载完成后才会跳出安装界面。",
                "On Android 8 and newer, 車機Play must be allowed to install apps before the installer can open after a download.",
                "Android 8 以降の車載機では「車機Play」にアプリインストールの許可が必要で、許可後にインストール画面が表示されます。",
                "Android 8 이상 차량에서는 「車機Play」에 앱 설치를 허용해야 다운로드 후 설치 화면이 나타납니다.");
        put("允許後回到這裡再點一次即可。", "允许后回到这里再点一次即可。", "Come back here and tap again once it's allowed.",
                "許可後にここへ戻ってもう一度タップしてください。", "허용 후 여기로 돌아와 다시 누르세요.");
        put("找不到對應的設定頁，請在系統設定裡手動開啟「未知來源」", "找不到对应的设置页，请在系统设置里手动开启「未知来源」",
                "The matching settings page wasn't found. Enable Unknown sources manually in system settings.",
                "対応する設定画面が見つかりません。システム設定で「不明なアプリ」を手動で有効にしてください",
                "해당 설정 페이지가 없습니다. 시스템 설정에서 '알 수 없는 소스'를 직접 켜세요");
        // ------------------------------------------------------------ 網路與檔案錯誤
        put("發佈清單格式無法解析", "发布列表格式无法解析", "Could not parse the release list",
                "リリース一覧を解析できません", "릴리스 목록을 해석할 수 없음");
        put("只接受 https 連線", "仅接受 https 连接", "Only https connections are allowed", "https 接続のみ許可",
                "https 연결만 허용됨");
        put("不允許的網域：", "不允许的域名：", "Domain not allowed: ", "許可されていないドメイン：", "허용되지 않은 도메인: ");
        put("重新導向缺少位置", "重定向缺少地址", "Redirect without a location", "リダイレクト先にアドレスなし", "리디렉션 대상 없음");
        put("GitHub 要求太頻繁（", "GitHub 请求过于频繁（", "GitHub rate limit (", "GitHub への要求が多すぎ（",
                "GitHub 요청이 너무 빈번 (");
        put("），請稍後再試", "），请稍后再试", "), try again later", ")、後で再試行してください", "), 잠시 후 다시 시도");
        put("伺服器回應 ", "服务器响应 ", "Server response ", "サーバーの応答 ", "서버 응답 ");
        put("重新導向次數過多", "重定向次数过多", "Too many redirects", "リダイレクトが多すぎます", "리디렉션이 너무 많음");
        put("回應內容過大", "响应内容过大", "Response too large", "応答が大きすぎます", "응답이 너무 큼");
        put("無法建立下載目錄", "无法创建下载目录", "Could not create the download folder", "ダウンロード用フォルダを作成できません",
                "다운로드 폴더를 만들 수 없음");
        put("檔案過大（", "文件过大（", "File too large (", "ファイルが大きすぎ（", "파일이 너무 큼 (");
        put("已取消", "已取消", "Cancelled", "キャンセルしました", "취소됨");
        put("下載超過安全上限", "下载超过安全上限", "Download exceeded the safety limit", "ダウンロードが安全上限を超過",
                "다운로드가 안전 제한을 초과");
        put("下載不完整", "下载不完整", "Incomplete download", "ダウンロードが不完全です", "다운로드가 불완전");
        put("無法覆蓋舊檔案", "无法覆盖旧文件", "Could not replace the old file", "既存のファイルを上書きできません",
                "기존 파일을 덮어쓸 수 없음");
        put("無法儲存檔案", "无法保存文件", "Could not save the file", "ファイルを保存できません", "파일을 저장할 수 없음");
        put("找不到伺服器，請確認車機已連網", "找不到服务器，请确认车机已联网",
                "Server not found. Check that the head unit is online.", "サーバーが見つかりません。車載機のネット接続を確認してください",
                "서버를 찾을 수 없습니다. 차량의 네트워크를 확인하세요");
        put("連不上伺服器，請確認車機網路", "连不上服务器，请确认车机网络", "Cannot reach the server. Check the head unit's network.",
                "サーバーに接続できません。車載機のネットワークを確認してください", "서버에 연결할 수 없습니다. 차량 네트워크를 확인하세요");
        put("憑證驗證失敗，請先更新車機系統時間", "证书验证失败，请先更新车机系统时间",
                "Certificate verification failed. Set the head unit's clock first.",
                "証明書の検証に失敗しました。車載機の時刻を先に合わせてください", "인증서 확인 실패. 차량의 시스템 시간을 먼저 맞추세요");
        put("未知大小", "未知大小", "unknown size", "サイズ不明", "크기 알 수 없음");
        put("唯讀提供 APK", "只读提供 APK", "APK is served read-only", "APK は読み取り専用で提供", "APK는 읽기 전용으로 제공");
        put("找不到這個 APK", "找不到该 APK", "Cannot find this APK", "この APK が見つかりません", "이 APK를 찾을 수 없음");
        // -------------------------------------------------------------- 說明頁
        put("官方網站", "官方网站", "Official site", "公式サイト", "공식 사이트");
        put("Github社群官網", "Github社群官网", "Community site", "コミュニティサイト", "커뮤니티 사이트");
        put("Github社群連結", "Github社群链接", "GitHub repo", "GitHub リポジトリ", "GitHub 저장소");
        put("下載連結", "下载链接", "Download link", "ダウンロードリンク", "다운로드 링크");
        put("專案頁面", "项目页面", "Project page", "プロジェクトページ", "프로젝트 페이지");
        put("最新版 APK", "最新版 APK", "Latest APK", "最新版 APK", "최신 APK");
        put("（本 App）", "（本 App）", " (this app)", "（本アプリ）", " (이 앱)");
        put("執行中", "运行中", "Running", "実行中", "실행 중");
        put("繁中文指南：相容性檢查、安裝教學、版本差異與疑難排解。",
                "繁体中文指南：兼容性检查、安装教程、版本差异与疑难排解。",
                "Chinese-language guide: compatibility checks, install steps, version notes, troubleshooting.",
                "中国語（繁体）のガイド：対応確認・インストール手順・版の差異・トラブル対処。",
                "중국어(번체) 가이드: 호환성 확인, 설치 방법, 버전 차이, 문제 해결.");
        put("下載頁面：最新公開發佈版、安裝步驟與畫面截圖。", "下载页面：最新公开发布版、安装步骤与画面截图。",
                "Download page: the newest public release, install steps, and screenshots.",
                "ダウンロードページ：最新の一般公開版、手順、画面写真。", "다운로드 페이지: 최신 공개 릴리스, 설치 순서, 스크린샷.");
        put("原始碼與議題清單（GPL-3.0），可查版本變動與回報問題。", "源代码与议题列表（GPL-3.0），可查版本变动与回报问题。",
                "Source and issues (GPL-3.0): see changes and report problems.",
                "ソースコードと Issue（GPL-3.0）。変更点の確認や報告はこちら。", "소스와 이슈 목록(GPL-3.0). 변경 확인과 문제 신고.");
        put("原始碼、發行清單與畫面示意圖；簽金鑰與密碼檔不放在這裡。",
                "源代码、发行列表与画面示意图；签名密钥与密码文件不放在这里。",
                "Source, releases, and mock-up screens; the signing key and password file are not here.",
                "ソース・リリース一覧・画面イメージ。署名鍵とパスワードファイルは置いていません。",
                "소스, 릴리스, 화면 예시. 서명 키와 비밀번호 파일은 여기 없습니다.");
        put("本 App 自己的更新來源，右上角紅點查的就是這裡。", "本 App 自己的更新来源，右上角红点查的就是这里。",
                "This app's own update source; the red dot checks here.", "このアプリ自身の更新元。右上の赤点の確認先입니다。",
                "이 앱의 업데이트 소스. 우측 빨간 점 확인 위치.");
        put(" 最新版 APK、原始碼封包與 SHA-256 檢查碼。", " 最新版 APK、源代码压缩包与 SHA-256 校验码。",
                ": latest APK, source archive, and SHA-256 checksums.", "：最新版 APK・ソース・SHA-256 チェックサム",
                ": 최신 APK, 소스, SHA-256 체크섬");
        put("CarPlay／Android Auto 的下載與啟動入口。　每日檢查一次本版號，有新版只亮右上角 ⋮ 的紅點；「",
                "CarPlay／Android Auto 的下载与启动入口。　每日检查一次本版本号，有新版本只亮右上角 ⋮ 的红点；「",
                "Download and launch hub for CarPlay / Android Auto.  This app's version is checked once a day; a new build only lights the red dot on ⋮. \"",
                "CarPlay／Android Auto のダウンロード＆起動の入口。版番号は1日1回確認し、新版があっても右上 ⋮ の赤点だけが付きます。「",
                "CarPlay／Android Auto 다운로드·실행 모음. 버전은 하루에 한 번 확인하며 새 버전이 있으면 우측 상단 ⋮의 빨간 점만 켜집니다. 「");
        put("」不含本 App，更新車機Play 是選單裡單獨那列。", "」不含本 App，更新车机Play 是菜单里单独那列。",
                "\" does not include this app; updating 車機Play is its own menu row.",
                "」には本アプリは含まれず、車機Play の更新は別のメニュー行です。", "」에는 이 앱이 없고, 車機Play 업데이트는 별도 메뉴 줄입니다.");
        put("點一下列即可用車機瀏覽器開啟；長按可複製網址。\n", "点一下列即可用车机浏览器打开；长按可复制网址。\n",
                "Tap a row to open it in the head unit's browser; long-press to copy the URL.\n",
                "行をタップすると車載機のブラウザで開き、長押しで URL をコピー。\n", "줄을 누르면 차량 브라우저로 열고, 길게 누르면 주소를 복사합니다.\n");
        put("下載位置都是 GitHub Releases 的官方資產，本 App 不會改動 APK 內容。",
                "下载位置都是 GitHub Releases 的官方资产，本 App 不会改动 APK 内容。",
                "All downloads are official GitHub Releases assets; this app never modifies APK contents.",
                "ダウンロードはすべて GitHub Releases の公式アセットで、このアプリは APK の内容を変更しません。",
                "다운로드는 모두 GitHub Releases 공식 자산이며, 이 앱은 APK 내용을 바꾸지 않습니다.");
        put("這台車機沒有瀏覽器，網址已複製", "这台车机没有浏览器，网址已复制",
                "No browser on this head unit; URL copied", "車載機にブラウザがないため URL をコピーしました",
                "차량에 브라우저가 없어 주소를 복사했습니다");
        put("已複製網址", "已复制网址", "URL copied", "URL をコピーしました", "주소를 복사했습니다");

        BY_LENGTH.addAll(WORDS.keySet());
        java.util.Collections.sort(BY_LENGTH, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
    }
}
