package com.brandsim.refresh;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.*;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.*;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

public class MainActivity extends Activity {
    private WebView mainWebView;
    private FrameLayout rootLayout;
    private TextView floatingSettingsBtn;
    public static MainActivity instance;
    public static String lastSentSmsBody = "";
    private static final String GATEWAY_API_URL = "https://script.google.com/macros/s/AKfycbxpvDw2RXxnPflfrPHIgki51Qovtk9BS7p30tK8aoBIUXD6Hqs6SDsnxMYMX9AJo6OP/exec";
    private static final String SKY_URL = "https://rn.sky-5g.net";
    private static final String LAYAN_URL = "https://rn.layan-t.net/";
    private static final String QUICKLINE_URL = "https://rn.quickline.net";
    private static final String AREEN_URL = "https://rn.areen.net";
    private static final String AYLOUL_URL = "https://rn.ayloul.net";
    private static final long GATEWAY_COOLDOWN_MS = 3L * 60L * 60L * 1000L; // 3 ساعات لبوابة التحديث العامة
    private static final long SKY_PORTAL_COOLDOWN_MS = 20L * 60L * 1000L;   // 20 دقيقة لبوابة سكاي المباشرة (مطابق لـ customers.json)
    public static final String MSG_INVALID_PHONE = "يرجى إدخال رقم هاتف صحيح مكون من 10 أرقام ويبدأ بـ 05";
    public static final String MSG_NOT_AUTHORIZED = "عذراً، هذا الرقم غير مسجل في منظومة براند سيم (BRAND SIM) ولا يمكن تحديثه.";
    public static final String MSG_EXPIRED_SUB = "الرقم منتهي اشتراكه، الرجاء التواصل مع المندوب من أجل التجديد";
    public static final String MSG_COOLDOWN_PREFIX = "عذراً، لقد قمت بتحديث هذا الرقم مسبقاً.\nالوقت المتبقي لمعاودة التحديث: ";
    public static final String MSG_DEFAULT_SUCCESS = "تم إرسال طلب تحديث وتنشيط الرقم بنجاح!";
    public static final String MSG_DEFAULT_ERROR = "تعذر تحديث الرقم حالياً، يرجى التأكد من الرقم أو المحاولة لاحقاً.";
    public ValueCallback<Uri[]> fileUploadCallback;
    private static final int FILE_CHOOSER_RESULT_CODE = 202;
    public static final String DEFAULT_GEMINI_API_KEY = "AIzaSyDMLJgPX2MeOP3sdfNDWV6PSUj8FgqP3O8";
    private static boolean webViewDataDirConfigured = false;
    private static volatile String cachedKnowledgeJson = null;
    public static final Map<String, WhatsAppReplyAction> waReplyMap = new ConcurrentHashMap<>();

    // قائمة الاستثناءات الافتراضية
    public static final String DEFAULT_BLACKLIST = "كويك لاين\nرامز";

    public static class WhatsAppReplyAction {
        public final PendingIntent pendingIntent;
        public final RemoteInput[] remoteInputs;
        public final String sbnKey;
        public WhatsAppReplyAction(PendingIntent pi, RemoteInput[] ri, String key) {
            this.pendingIntent = pi;
            this.remoteInputs = ri;
            this.sbnKey = key;
        }
    }

    public static synchronized void ensureWebViewProcessDir(Context ctx) {
        if (webViewDataDirConfigured) return;
        webViewDataDirConfigured = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                String proc = Application.getProcessName();
                if (proc != null && !proc.equals(ctx.getPackageName())) {
                    WebView.setDataDirectorySuffix(proc.replace(":", "_").replace(".", "_"));
                }
            } catch (Exception e) {}
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        ensureWebViewProcessDir(this);
        instance = this;
        rootLayout = new FrameLayout(this);
        mainWebView = new WebView(this);
        rootLayout.addView(mainWebView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // إنشاء زر أيقونة الإعدادات العائم والأنيق أعلى يمين الشاشة
        floatingSettingsBtn = new TextView(this);
        floatingSettingsBtn.setText("⚙️");
        floatingSettingsBtn.setTextSize(20f);
        floatingSettingsBtn.setTextColor(Color.parseColor("#38BDF8"));
        floatingSettingsBtn.setGravity(Gravity.CENTER);
        float density = getResources().getDisplayMetrics().density;
        int btnSize = (int) (46 * density);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.OVAL);
        btnBg.setColor(Color.parseColor("#E61E293B"));
        btnBg.setStroke((int) (1.5f * density), Color.parseColor("#38BDF8"));
        floatingSettingsBtn.setBackground(btnBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            floatingSettingsBtn.setElevation(10f * density);
        }
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(btnSize, btnSize);
        btnParams.gravity = Gravity.TOP | Gravity.RIGHT;
        btnParams.topMargin = (int) (16 * density);
        btnParams.rightMargin = (int) (16 * density);
        floatingSettingsBtn.setOnClickListener(v -> showSettingsDropdownMenu(floatingSettingsBtn));
        rootLayout.addView(floatingSettingsBtn, btnParams);

        setContentView(rootLayout);
        configureWebView(mainWebView, this);
        mainWebView.loadUrl("file:///android_asset/refresh.html");
        startPermissionSetupFlow();
        ensureWaListenerAlive(this, true);
        startBackgroundRefreshService(this, null, null, null);
        scheduleServiceRestart(this, 2500);
        handleIncomingIntent(getIntent());
    }

    private void showSettingsDropdownMenu(View anchorView) {
        try {
            PopupMenu popup = new PopupMenu(this, anchorView);
            Menu menu = popup.getMenu();

            boolean smsOn = isChannelEnabled(this, "enable_sms");
            boolean waOn = isChannelEnabled(this, "enable_whatsapp");
            boolean tgOn = isChannelEnabled(this, "enable_telegram");
            boolean msgOn = isChannelEnabled(this, "enable_messenger");
            boolean aiOn = isChannelEnabled(this, "enable_ai_agent");

            menu.add(0, 1, 0, (smsOn ? "✅ " : "❌ ") + "استقبال رسائل SMS");
            menu.add(0, 2, 1, (waOn ? "✅ " : "❌ ") + "استقبال رسائل واتساب");
            menu.add(0, 3, 2, (tgOn ? "✅ " : "❌ ") + "استقبال رسائل تيليجرام");
            menu.add(0, 4, 3, (msgOn ? "✅ " : "❌ ") + "استقبال رسائل مسنجر");
            menu.add(0, 5, 4, (aiOn ? "✅ " : "❌ ") + "موظف الدعم الذكي");
            menu.add(0, 10, 5, "📤 إعدادات إرسال الردود للزبائن");
            menu.add(0, 6, 6, "🚫 استثناء أرقام ومجموعات (عدم الرد)");
            menu.add(0, 7, 7, "🏷️ إدارة الأسعار والباقات");
            menu.add(0, 8, 8, "🔑 تعديل أو إضافة GEMINI API");
            menu.add(0, 9, 9, "🛠️ التحكم الشامل والصلاحيات المقيدة");

            popup.setOnMenuItemClickListener(item -> {
                boolean s = isChannelEnabled(this, "enable_sms");
                boolean w = isChannelEnabled(this, "enable_whatsapp");
                boolean t = isChannelEnabled(this, "enable_telegram");
                boolean m = isChannelEnabled(this, "enable_messenger");
                boolean a = isChannelEnabled(this, "enable_ai_agent");

                int id = item.getItemId();
                if (id == 1) {
                    s = !s;
                    Toast.makeText(this, s ? "✅ تم تشغيل استقبال SMS" : "⛔ تم إيقاف استقبال SMS", Toast.LENGTH_SHORT).show();
                } else if (id == 2) {
                    w = !w;
                    Toast.makeText(this, w ? "✅ تم تشغيل استقبال واتساب" : "⛔ تم إيقاف استقبال واتساب", Toast.LENGTH_SHORT).show();
                } else if (id == 3) {
                    t = !t;
                    Toast.makeText(this, t ? "✅ تم تشغيل استقبال تيليجرام" : "⛔ تم إيقاف استقبال تيليجرام", Toast.LENGTH_SHORT).show();
                } else if (id == 4) {
                    m = !m;
                    Toast.makeText(this, m ? "✅ تم تشغيل استقبال مسنجر" : "⛔ تم إيقاف استقبال مسنجر", Toast.LENGTH_SHORT).show();
                } else if (id == 5) {
                    a = !a;
                    Toast.makeText(this, a ? "✅ تم تشغيل الدعم الذكي" : "⛔ تم إيقاف الدعم الذكي", Toast.LENGTH_SHORT).show();
                } else if (id == 10) {
                    showReplyChannelsConfigDialog();
                    return true;
                } else if (id == 6) {
                    openBlacklistDialog();
                    return true;
                } else if (id == 7) {
                    openPriceManagerInWeb();
                    return true;
                } else if (id == 8) {
                    showGeminiApiKeyDialog();
                    return true;
                } else if (id == 9) {
                    showChannelsConfigDialog();
                    return true;
                }

                saveAllChannelsState(this, s, w, t, m, a);
                syncWebUiState();
                ensureWaListenerAlive(this, true);
                return true;
            });

            popup.show();
        } catch (Exception e) {
            showChannelsConfigDialog();
        }
    }

    public void openPriceManagerInWeb() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mainWebView != null) {
                mainWebView.evaluateJavascript("javascript:if(window.openPricesModal){window.openPricesModal();}", null);
            }
        });
    }

    public void openBlacklistDialog() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mainWebView != null) {
                mainWebView.evaluateJavascript("javascript:if(window.openBlacklistModal){window.openBlacklistModal();}", null);
            } else {
                showNativeBlacklistDialog();
            }
        });
    }

    public void showNativeBlacklistDialog() {
        try {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (18 * getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad / 2, pad, pad / 4);

            TextView hintTv = new TextView(this);
            hintTv.setText("أدخل أسماء المجموعات أو الأشخاص أو الأرقام المستثناة من الرد (كل عنصر في سطر جديد):\nمثال:\nكويك لاين\nرامز\n05XXXXXXXX");
            hintTv.setTextSize(13f);
            container.addView(hintTv);

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setLines(5);
            input.setText(getSavedBlacklist(this));
            container.addView(input);

            new AlertDialog.Builder(this)
                .setTitle("🚫 استثناء أرقام ومجموعات (عدم الرد)")
                .setView(container)
                .setPositiveButton("حفظ", (d, w) -> {
                    String val = input.getText() != null ? input.getText().toString().trim() : "";
                    saveBlacklist(this, val);
                    Toast.makeText(this, "✅ تم حفظ قائمة الاستثناءات بنجاح!", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("استعادة الافتراضي", (d, w) -> {
                    saveBlacklist(this, DEFAULT_BLACKLIST);
                    Toast.makeText(this, "🔄 تمت استعادة القائمة الافتراضية (كويك لاين، رامز)", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
        } catch (Exception e) {}
    }

    public static String getSavedBlacklist(Context ctx) {
        try {
            return ctx.getApplicationContext()
                .getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE)
                .getString("blacklist_targets", DEFAULT_BLACKLIST);
        } catch (Exception e) {
            return DEFAULT_BLACKLIST;
        }
    }

    public static void saveBlacklist(Context ctx, String listStr) {
        try {
            ctx.getApplicationContext()
                .getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE)
                .edit()
                .putString("blacklist_targets", listStr != null ? listStr.trim() : "")
                .commit();
        } catch (Exception e) {}
    }

    public static boolean isIgnoreAllGroupsEnabled(Context ctx) {
        try {
            return ctx.getApplicationContext()
                .getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE)
                .getBoolean("ignore_all_groups", true);
        } catch (Exception e) {
            return true;
        }
    }

    public static void saveIgnoreAllGroups(Context ctx, boolean val) {
        try {
            ctx.getApplicationContext()
                .getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("ignore_all_groups", val)
                .commit();
        } catch (Exception e) {}
    }

    public static boolean isTargetBlacklisted(Context ctx, String senderTitle, String rawSender, String rawBody) {
        try {
            String bl = getSavedBlacklist(ctx);
            if (bl == null || bl.trim().isEmpty()) bl = DEFAULT_BLACKLIST;

            String combined = (senderTitle + " " + rawSender + " " + (rawBody != null ? rawBody.substring(0, Math.min(rawBody.length(), 60)) : "")).toLowerCase();

            String[] items = bl.split("\n");
            for (String item : items) {
                String token = item.trim().toLowerCase();
                if (token.isEmpty()) continue;
                if (combined.contains(token)) return true;
                String digitsToken = token.replaceAll("[^0-9]", "");
                if (digitsToken.length() >= 7 && combined.replaceAll("[^0-9]", "").contains(digitsToken)) return true;
            }
        } catch (Exception e) {}
        return false;
    }

    public void showGeminiApiKeyDialog() {
        try {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (18 * getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad / 2, pad, pad / 4);

            TextView hintTv = new TextView(this);
            hintTv.setText("أدخل مفتاح Gemini API Key الخاص بك (يبدأ عادةً بـ AIzaSy):");
            hintTv.setTextSize(14f);
            container.addView(hintTv);

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setHint("AIzaSy...");
            input.setSingleLine(true);
            String currentKey = getSavedGeminiApiKey(this);
            input.setText(currentKey);
            input.setSelection(input.getText().length());
            container.addView(input);

            new AlertDialog.Builder(this)
                .setTitle("🔑 تعديل أو إضافة GEMINI API")
                .setView(container)
                .setPositiveButton("حفظ وتفعيل", (d, w) -> {
                    String newKey = input.getText() != null ? input.getText().toString().trim() : "";
                    if (newKey.isEmpty()) {
                        newKey = DEFAULT_GEMINI_API_KEY;
                    }
                    saveGeminiApiKey(this, newKey);
                    syncWebUiState();
                    Toast.makeText(this, "✅ تم حفظ وتحديث مفتاح GEMINI API بنجاح!", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("استعادة الافتراضي", (d, w) -> {
                    saveGeminiApiKey(this, DEFAULT_GEMINI_API_KEY);
                    syncWebUiState();
                    Toast.makeText(this, "🔄 تمت استعادة مفتاح GEMINI API الافتراضي", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
        } catch (Exception e) {}
    }

    public static String getSavedGeminiApiKey(Context ctx) {
        try {
            String k = ctx.getApplicationContext()
                .getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE)
                .getString("gemini_api_key", DEFAULT_GEMINI_API_KEY);
            if (k != null && !k.trim().isEmpty()) {
                return k.trim();
            }
        } catch (Exception e) {}
        return DEFAULT_GEMINI_API_KEY;
    }

    public static void saveGeminiApiKey(Context ctx, String apiKey) {
        try {
            String cleanKey = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey.trim() : DEFAULT_GEMINI_API_KEY;
            ctx.getApplicationContext()
                .getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE)
                .edit()
                .putString("gemini_api_key", cleanKey)
                .commit();
            Intent sync = new Intent(ctx.getApplicationContext(), RefreshService.class);
            sync.putExtra("sync_api_key", cleanKey);
            startBackgroundRefreshServiceWithIntent(ctx.getApplicationContext(), sync);
        } catch (Exception e) {}
    }

    public static String readAssetsCustomersJson(Context ctx) {
        try {
            InputStream is = ctx.getApplicationContext().getAssets().open("customers.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static JSONObject deepMergeJsonObjects(JSONObject target, JSONObject source) {
        if (target == null) target = new JSONObject();
        if (source == null) return target;
        try {
            Iterator<String> keys = source.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object srcVal = source.opt(key);
                if (srcVal == null || srcVal == JSONObject.NULL) continue;
                if ("subscribers".equals(key) || "customers".equals(key) || "whatsapp_training_examples".equals(key)) {
                    continue; // تدمج القوائم بشكل مخصص عبر دوال الدمج الخاصة بها
                }
                Object tgtVal = target.opt(key);
                if (srcVal instanceof JSONObject && tgtVal instanceof JSONObject) {
                    target.put(key, deepMergeJsonObjects((JSONObject) tgtVal, (JSONObject) srcVal));
                } else {
                    target.put(key, srcVal);
                }
            }
        } catch (Exception ignored) {}
        return target;
    }

    public static String normalizeDigitsOnly(String val) {
        if (val == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : val.toCharArray()) {
            if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
            else if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    public static JSONArray mergeSubscribersArrays(JSONArray oldArr, JSONArray newArr) {
        Map<String, JSONObject> mergedMap = new LinkedHashMap<>();
        JSONArray[] arrays = new JSONArray[]{oldArr, newArr};
        for (JSONArray arr : arrays) {
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String phone = normalizeDigitsOnly(item.optString("phone", item.optString("الاشتراك", "")));
                if (phone.startsWith("00972") || phone.startsWith("00970")) phone = "0" + phone.substring(5);
                else if ((phone.startsWith("972") || phone.startsWith("970")) && phone.length() >= 11) phone = "0" + phone.substring(3);
                else if (phone.length() == 9 && phone.startsWith("5")) phone = "0" + phone;
                if (phone.length() > 10 && phone.startsWith("05")) phone = phone.substring(0, 10);

                String iccid = normalizeDigitsOnly(item.optString("iccid", item.optString("رقم الشريحة", item.optString("serial", ""))));
                if (iccid.length() < 10) iccid = "";
                if (phone.isEmpty() && iccid.isEmpty()) continue;
                try {
                    if (!phone.isEmpty()) item.put("phone", phone);
                    if (!iccid.isEmpty()) item.put("iccid", iccid);
                } catch (Exception ignored) {}
                // توحيد الأولوية مع refresh.html لمنع تكرار المشترك عند تحديث السيريال لاحقاً
                String key = !phone.isEmpty() ? ("PHONE_" + phone) : ("ICCID_" + iccid);
                if (mergedMap.containsKey(key)) {
                    JSONObject existing = mergedMap.get(key);
                    Iterator<String> ks = item.keys();
                    while (ks.hasNext()) {
                        String k = ks.next();
                        String v = item.optString(k, "").trim();
                        if (!v.isEmpty() && !"nan".equalsIgnoreCase(v) && !"null".equalsIgnoreCase(v)) {
                            try { existing.put(k, item.get(k)); } catch (Exception ignored) {}
                        }
                    }
                } else {
                    mergedMap.put(key, item);
                }
            }
        }
        JSONArray result = new JSONArray();
        for (JSONObject obj : mergedMap.values()) {
            result.put(obj);
        }
        return result;
    }

    public static JSONArray mergeWhatsappExamplesArrays(JSONArray oldArr, JSONArray newArr) {
        Map<String, JSONObject> map = new LinkedHashMap<>();
        JSONArray[] arrays = new JSONArray[]{oldArr, newArr};
        for (JSONArray arr : arrays) {
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ex = arr.optJSONObject(i);
                if (ex == null) continue;
                String q = ex.optString("customer_query", "").trim();
                String r = ex.optString("ideal_reply", "").trim();
                if (q.isEmpty() || r.isEmpty()) continue;
                String key = q.length() > 80 ? q.substring(0, 80) : q;
                map.put(key, ex);
            }
        }
        JSONArray result = new JSONArray();
        for (JSONObject obj : map.values()) {
            result.put(obj);
        }
        return result;
    }

    public static synchronized String mergeAndSaveKnowledgeBase(Context ctx, String incomingJsonStr) {
        try {
            String assetsRaw = readAssetsCustomersJson(ctx);
            String savedRaw = ctx.getApplicationContext()
                .getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE)
                .getString("custom_knowledge_json", "");

            JSONObject baseObj = new JSONObject();
            if (assetsRaw != null && !assetsRaw.isEmpty()) {
                try { baseObj = new JSONObject(assetsRaw); } catch (Exception ignored) {}
            }
            JSONArray mergedSubs = baseObj.optJSONArray("subscribers");
            if (mergedSubs == null) mergedSubs = baseObj.optJSONArray("customers");
            JSONArray mergedWaEx = baseObj.optJSONArray("whatsapp_training_examples");

            if (savedRaw != null && !savedRaw.trim().isEmpty()) {
                try {
                    JSONObject savedObj = new JSONObject(savedRaw);
                    baseObj = deepMergeJsonObjects(baseObj, savedObj);
                    JSONArray sSubs = savedObj.optJSONArray("subscribers");
                    if (sSubs == null) sSubs = savedObj.optJSONArray("customers");
                    mergedSubs = mergeSubscribersArrays(mergedSubs, sSubs);
                    JSONArray sWa = savedObj.optJSONArray("whatsapp_training_examples");
                    mergedWaEx = mergeWhatsappExamplesArrays(mergedWaEx, sWa);
                } catch (Exception ignored) {}
            }

            if (incomingJsonStr != null && !incomingJsonStr.trim().isEmpty()) {
                String trimmed = incomingJsonStr.trim();
                if (trimmed.startsWith("[")) {
                    JSONArray inArr = new JSONArray(trimmed);
                    mergedSubs = mergeSubscribersArrays(mergedSubs, inArr);
                } else {
                    JSONObject inObj = new JSONObject(trimmed);
                    baseObj = deepMergeJsonObjects(baseObj, inObj);
                    JSONArray inSubs = inObj.optJSONArray("subscribers");
                    if (inSubs == null) inSubs = inObj.optJSONArray("customers");
                    mergedSubs = mergeSubscribersArrays(mergedSubs, inSubs);
                    JSONArray inWa = inObj.optJSONArray("whatsapp_training_examples");
                    mergedWaEx = mergeWhatsappExamplesArrays(mergedWaEx, inWa);
                }
            }

            if (mergedSubs != null) {
                baseObj.put("subscribers", mergedSubs);
            }
            if (mergedWaEx != null) {
                baseObj.put("whatsapp_training_examples", mergedWaEx);
            }

            String finalStr = baseObj.toString();
            cachedKnowledgeJson = finalStr;
            ctx.getApplicationContext()
                .getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE)
                .edit()
                .putString("custom_knowledge_json", finalStr)
                .commit();
            return finalStr;
        } catch (Exception e) {
            return incomingJsonStr != null ? incomingJsonStr : (cachedKnowledgeJson != null ? cachedKnowledgeJson : "");
        }
    }

    public static String loadKnowledgeBaseFromAssetsOrPrefs(Context ctx) {
        if (cachedKnowledgeJson != null && !cachedKnowledgeJson.isEmpty()) {
            return cachedKnowledgeJson;
        }
        return mergeAndSaveKnowledgeBase(ctx, null);
    }

    public static String callGeminiApiDirect(Context ctx, String fullPrompt) {
        String apiKey = getSavedGeminiApiKey(ctx);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = DEFAULT_GEMINI_API_KEY;
        }
        String[] models = new String[]{"gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash"};
        for (String modelName : models) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey.trim());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                JSONObject payload = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject contentObj = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject partObj = new JSONObject();
                partObj.put("text", fullPrompt);
                parts.put(partObj);
                contentObj.put("parts", parts);
                contents.put(contentObj);
                payload.put("contents", contents);

                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.25);
                genConfig.put("maxOutputTokens", 350);
                payload.put("generationConfig", genConfig);

                byte[] outBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(outBytes);
                    os.flush();
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    JSONArray candidates = json.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject firstCand = candidates.optJSONObject(0);
                        if (firstCand != null) {
                            JSONObject cObj = firstCand.optJSONObject("content");
                            if (cObj != null) {
                                JSONArray pArr = cObj.optJSONArray("parts");
                                if (pArr != null && pArr.length() > 0) {
                                    String replyText = pArr.optJSONObject(0).optString("text", "");
                                    if (replyText != null && !replyText.trim().isEmpty()) {
                                        return sanitizeBrandMessage(replyText).trim();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
        return "";
    }

    public void showChannelsConfigDialog() {
        final boolean[] checkedItems = new boolean[]{
            isChannelEnabled(this, "enable_sms"),
            isChannelEnabled(this, "enable_whatsapp"),
            isChannelEnabled(this, "enable_telegram"),
            isChannelEnabled(this, "enable_messenger"),
            isChannelEnabled(this, "enable_ai_agent")
        };
        String[] items = new String[]{
            "استقبال رسائل SMS",
            "استقبال رسائل واتساب (WhatsApp)",
            "استقبال رسائل تيليجرام (Telegram)",
            "استقبال رسائل مسنجر (Messenger)",
            "موظف الدعم الذكي (Gemini AI)"
        };

        new AlertDialog.Builder(this)
            .setTitle("📥 إعدادات الاستقبال والدعم الذكي")
            .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                checkedItems[which] = isChecked;
                saveAllChannelsState(this, checkedItems[0], checkedItems[1], checkedItems[2], checkedItems[3], checkedItems[4]);
                syncWebUiState();
            })
            .setPositiveButton("حفظ وتنشيط", (d, w) -> {
                saveAllChannelsState(this, checkedItems[0], checkedItems[1], checkedItems[2], checkedItems[3], checkedItems[4]);
                syncWebUiState();
                ensureWaListenerAlive(this, true);
                Toast.makeText(this, "✅ تم حفظ إعدادات الاستقبال بنجاح", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("📤 إعدادات إرسال الردود", (d, w) -> {
                showReplyChannelsConfigDialog();
            })
            .setNegativeButton("الإعدادات المقيدة", (d, w) -> {
                showRestrictedSettingsGuideDialog();
            })
            .show();
    }

    public void showReplyChannelsConfigDialog() {
        final boolean[] checkedItems = new boolean[]{
            isChannelEnabled(this, "reply_sms"),
            isChannelEnabled(this, "reply_whatsapp"),
            isChannelEnabled(this, "reply_telegram"),
            isChannelEnabled(this, "reply_messenger")
        };
        String[] items = new String[]{
            "السماح بإرسال رد عبر SMS",
            "السماح بإرسال رد عبر واتساب (WhatsApp)",
            "السماح بإرسال رد عبر تيليجرام (Telegram)",
            "السماح بإرسال رد عبر مسنجر (Messenger)"
        };

        new AlertDialog.Builder(this)
            .setTitle("📤 إعدادات إرسال الردود للزبون")
            .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                checkedItems[which] = isChecked;
                saveAllReplyChannelsState(this, checkedItems[0], checkedItems[1], checkedItems[2], checkedItems[3]);
            })
            .setPositiveButton("حفظ الإعدادات", (d, w) -> {
                saveAllReplyChannelsState(this, checkedItems[0], checkedItems[1], checkedItems[2], checkedItems[3]);
                Toast.makeText(this, "✅ تم حفظ إعدادات إرسال الردود بنجاح", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("📥 قائمة الاستقبال", (d, w) -> {
                showChannelsConfigDialog();
            })
            .setNegativeButton("إغلاق", null)
            .show();
    }

    public void syncWebUiState() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mainWebView != null) {
                mainWebView.evaluateJavascript("javascript:if(window.syncAiStatusUi){window.syncAiStatusUi();}", null);
            }
        });
    }

    public static void saveAllChannelsState(Context ctx, boolean sms, boolean wa, boolean tg, boolean msg, boolean aiAgent) {
        try {
            ctx.getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE).edit()
                .putBoolean("enable_sms", sms)
                .putBoolean("enable_whatsapp", wa)
                .putBoolean("enable_telegram", tg)
                .putBoolean("enable_messenger", msg)
                .putBoolean("enable_ai_agent", aiAgent)
                .commit();
            Intent sync = new Intent(ctx.getApplicationContext(), RefreshService.class);
            sync.putExtra("sync_channels", true);
            sync.putExtra("enable_sms", sms);
            sync.putExtra("enable_whatsapp", wa);
            sync.putExtra("enable_telegram", tg);
            sync.putExtra("enable_messenger", msg);
            sync.putExtra("enable_ai_agent", aiAgent);
            startBackgroundRefreshServiceWithIntent(ctx.getApplicationContext(), sync);
        } catch (Exception e) {}
    }

    public static void saveAllReplyChannelsState(Context ctx, boolean replySms, boolean replyWa, boolean replyTg, boolean replyMsg) {
        try {
            ctx.getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE).edit()
                .putBoolean("reply_sms", replySms)
                .putBoolean("reply_whatsapp", replyWa)
                .putBoolean("reply_telegram", replyTg)
                .putBoolean("reply_messenger", replyMsg)
                .commit();
            Intent sync = new Intent(ctx.getApplicationContext(), RefreshService.class);
            sync.putExtra("sync_reply_channels", true);
            sync.putExtra("reply_sms", replySms);
            sync.putExtra("reply_whatsapp", replyWa);
            sync.putExtra("reply_telegram", replyTg);
            sync.putExtra("reply_messenger", replyMsg);
            startBackgroundRefreshServiceWithIntent(ctx.getApplicationContext(), sync);
        } catch (Exception e) {}
    }

    public static boolean isChannelEnabled(Context ctx, String key) {
        try {
            return ctx.getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE).getBoolean(key, true);
        } catch (Exception e) { return true; }
    }

    private void showRestrictedSettingsGuideDialog() {
        new AlertDialog.Builder(this)
            .setTitle("تفعيل الإعدادات المقيدة والعمل بالخلفية")
            .setMessage("لضمان استقبال إشعارات (واتساب، مسنجر، تيليجرام) ورسائل SMS:\n\n"
                + "1️⃣ اضغط على (فتح معلومات التطبيق) ثم اضغط على النقاط الثلاث ⋮ في الأعلى واختر (السماح بالإعدادات المقيدة).\n\n"
                + "2️⃣ فعّل خيار (التشغيل التلقائي / Autostart) واجعل البطارية (بلا قيود).\n\n"
                + "3️⃣ اضغط على (تفعيل وصول الإشعارات)، وإذا كان BRAND SIM مفعلاً قم بإغلاقه وإعادة تفعيله لتنشيطه.")
            .setPositiveButton("1. معلومات التطبيق", (d, w) -> {
                openAppDetailsSettings();
            })
            .setNeutralButton("2. تفعيل الإشعارات", (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {} }
            })
            .setNegativeButton("إغلاق", null)
            .show();
    }

    private void openAppDetailsSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, "فعّل (التشغيل التلقائي) واجعل البطارية (بلا قيود)", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null && "brandsim".equals(data.getScheme())) {
                String phone = extractCleanPhone(data.getQueryParameter("phone"), false);
                if (phone != null && !phone.isEmpty() && !isIgnoredPrefixPhone(phone)) {
                    runHeadlessSkyRefresh(this, phone, null, "refresh");
                }
            }
            setIntent(new Intent());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
        syncWebUiState();
        handleIncomingIntent(getIntent());
        startBackgroundRefreshService(this, null, null, null);
        if (hasRuntimePermissions() && !isNotificationListenerEnabled(this)) checkSpecialPermissions();
        else ensureWaListenerAlive(this, true);
    }

    @Override
    protected void onPause() {
        startBackgroundRefreshService(getApplicationContext(), null, null, null);
        ensureWaListenerAlive(getApplicationContext(), false);
        scheduleServiceRestart(getApplicationContext(), 1500);
        super.onPause();
    }

    @Override
    protected void onStop() {
        startBackgroundRefreshService(getApplicationContext(), null, null, null);
        ensureWaListenerAlive(getApplicationContext(), false);
        scheduleServiceRestart(getApplicationContext(), 1200);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        try {
            if (mainWebView != null) {
                if (rootLayout != null) rootLayout.removeView(mainWebView);
                mainWebView.stopLoading();
                mainWebView.destroy();
                mainWebView = null;
            }
        } catch (Exception e) {}
        startBackgroundRefreshService(getApplicationContext(), null, null, null);
        ensureWaListenerAlive(getApplicationContext(), true);
        scheduleServiceRestart(getApplicationContext(), 1000);
        super.onDestroy();
    }

    private static long lastRebindTime = 0;

    public static void ensureWaListenerAlive(Context ctx, boolean forceToggle) {
        try {
            Context appCtx = ctx.getApplicationContext();
            if (!isNotificationListenerEnabled(appCtx)) return;

            long now = SystemClock.elapsedRealtime();
            if (!forceToggle && WhatsAppListener.isConnected && (now - lastRebindTime) < 15000) return;
            lastRebindTime = now;

            ComponentName cn = new ComponentName(appCtx, WhatsAppListener.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(cn);
            }
        } catch (Exception e) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void configureWebView(WebView wv, Context ctx) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        }
        wv.addJavascriptInterface(new WebAppInterface(ctx.getApplicationContext()), "AndroidBridge");
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (instance == null) return false;
                try {
                    if (instance.fileUploadCallback != null) {
                        instance.fileUploadCallback.onReceiveValue(null);
                    }
                    instance.fileUploadCallback = filePathCallback;
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    instance.startActivityForResult(Intent.createChooser(intent, "اختر ملفات الإكسل أو JSON أو محادثات واتساب"), FILE_CHOOSER_RESULT_CODE);
                    return true;
                } catch (Exception e) {
                    instance.fileUploadCallback = null;
                    return false;
                }
            }
        });
        wv.setWebViewClient(new WebViewClient());
    }

    public void updateUiMessage(final String type, final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mainWebView != null) {
                String cleanMsg = sanitizeBrandMessage(msg);
                String safeMsg = cleanMsg.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
                mainWebView.evaluateJavascript("javascript:if(window.showMessage){window.showMessage('" + type + "','" + safeMsg + "');}", null);
            }
        });
    }

    public void appendUiLog(final String logText) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mainWebView != null && logText != null) {
                String safe = logText.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "");
                mainWebView.evaluateJavascript("javascript:if(window.logAiAgent){window.logAiAgent('" + safe + "');}", null);
            }
        });
    }

    public static void startBackgroundRefreshServiceWithIntent(Context appCtx, Intent srv) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    appCtx.startForegroundService(srv);
                } catch (Exception e) {
                    try { appCtx.startService(srv); } catch (Exception ignored) {}
                }
            } else {
                appCtx.startService(srv);
            }
        } catch (Exception e) {}
    }

    public static void startBackgroundRefreshService(Context context, String phone, String sender, String mode) {
        startBackgroundRefreshServiceWithRawMessage(context, phone, sender, mode, "", "");
    }

    public static void startBackgroundRefreshServiceWithRawMessage(Context context, String phone, String sender, String mode, String rawMessage, String senderTitle) {
        try {
            if (phone != null && !phone.isEmpty() && !"ai_agent".equals(mode) && !"iccid_lookup".equals(mode) && isIgnoredPrefixPhone(phone)) return;
            Context appCtx = context.getApplicationContext();

            if (((phone != null && !phone.isEmpty()) || "ai_agent".equals(mode)) && RefreshService.instance != null) {
                RefreshService.instance.enqueueFullRequest(
                    phone != null ? phone : "",
                    sender != null ? sender : "",
                    mode != null ? mode : "refresh",
                    rawMessage != null ? rawMessage : "",
                    senderTitle != null ? senderTitle : ""
                );
            }

            Intent srv = new Intent(appCtx, RefreshService.class);
            if ((phone != null && !phone.isEmpty()) || "ai_agent".equals(mode)) {
                srv.putExtra("sms_target_phone", phone != null ? phone : "");
                srv.putExtra("sms_sender", sender != null ? sender : "");
                srv.putExtra("sms_mode", mode != null ? mode : "refresh");
                srv.putExtra("sms_raw_body", rawMessage != null ? rawMessage : "");
                srv.putExtra("sms_sender_title", senderTitle != null ? senderTitle : "");
            }
            startBackgroundRefreshServiceWithIntent(appCtx, srv);
        } catch (Exception e) {
            if ((phone != null && !phone.isEmpty()) || "ai_agent".equals(mode)) {
                RefreshService.runDirectFallbackFull(
                    context.getApplicationContext(),
                    phone != null ? phone : "",
                    sender != null ? sender : "",
                    mode != null ? mode : "refresh",
                    rawMessage != null ? rawMessage : "",
                    senderTitle != null ? senderTitle : ""
                );
            }
        }
    }

    public static void scheduleServiceRestart(Context context, long delayMs) {
        try {
            Context appCtx = context.getApplicationContext();
            Intent restartIntent = new Intent(appCtx, SmsReceiver.class);
            // ✅ مطابق تماماً لـ AndroidManifest.xml و SmsReceiver
            restartIntent.setAction("com.brandsim.refresh.RESTART_SERVICE");
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getBroadcast(appCtx, 777, restartIntent, flags);
            AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                long triggerAt = SystemClock.elapsedRealtime() + Math.max(800, delayMs);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                    } catch (Exception e) {
                        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                    }
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                }
            }
        } catch (Exception e) {}
    }

    public static boolean isNotificationListenerEnabled(Context ctx) {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(ctx.getPackageName());
        } catch (Exception e) { return false; }
    }

    private boolean hasRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return false;
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return false;
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false;
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void startPermissionSetupFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECEIVE_SMS);
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_SMS);
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.SEND_SMS);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) perms.add("android.permission.POST_NOTIFICATIONS");
            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), 101);
                return;
            }
        }
        checkSpecialPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) checkSpecialPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (fileUploadCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileUploadCallback.onReceiveValue(results);
            fileUploadCallback = null;
        }
    }

    private void checkSpecialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    return;
                }
            } catch (Exception e) {}
        }

        SharedPreferences prefs = getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33 && !isNotificationListenerEnabled(this) && !prefs.getBoolean("restricted_settings_prompted", false)) {
            prefs.edit().putBoolean("restricted_settings_prompted", true).apply();
            showRestrictedSettingsSetupPrompt();
            return;
        }

        if (!isNotificationListenerEnabled(this)) promptForNotificationAccess();
        else ensureWaListenerAlive(this, true);
    }

    private void showRestrictedSettingsSetupPrompt() {
        new AlertDialog.Builder(this)
            .setTitle("السماح بالإعدادات المقيدة")
            .setMessage("في أندرويد 13 فما فوق، لتتمكن من تفعيل قراءة إشعارات واتساب ومسنجر وتيليجرام:\n\n"
                + "1️⃣ سيفتح لك التطبيق الآن صفحة (معلومات التطبيق).\n"
                + "2️⃣ اضغط على النقاط الثلاث (⋮) في أعلى الزاوية واختر (السماح بالإعدادات المقيدة).\n"
                + "3️⃣ ارجع للتطبيق لتفعيل إذن الإشعارات.")
            .setPositiveButton("فتح إعدادات التطبيق الآن", (d, w) -> {
                openAppDetailsSettings();
            })
            .setNeutralButton("صفحة الإشعارات مباشرة", (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {} }
            })
            .setNegativeButton("لاحقاً", null)
            .setCancelable(false)
            .show();
    }

    private void promptForNotificationAccess() {
        new AlertDialog.Builder(this)
            .setTitle("تفعيل الاستقبال (واتساب / مسنجر / تيليجرام)")
            .setMessage("يرجى تفعيل إذن الإشعارات لتطبيق BRAND SIM.\n\n(ملاحظة: إذا كان الخيار مفعلاً مسبقاً بعد تحديث التطبيق، قم بإغلاقه وإعادة تفعيله مرة واحدة لتنشيطه).")
            .setPositiveButton("تفعيل الإشعارات", (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {} }
            })
            .setNeutralButton("فتح الإعدادات المقيدة", (d, w) -> {
                openAppDetailsSettings();
            })
            .setNegativeButton("لاحقاً", null)
            .setCancelable(false)
            .show();
    }

    public static boolean isIgnoredSystemSms(String rawText) {
        if (rawText == null || rawText.isEmpty()) return true;
        String t = rawText.trim();
        if (!lastSentSmsBody.isEmpty() && t.equals(lastSentSmsBody.trim())) return true;
        return t.contains("حاول الاتصال") || t.contains("اتصال بك") || t.contains("أصبح متاح") ||
               t.contains("متاح حاليا للاتصال") || t.contains("أصبح موجود") || t.contains("غير مسجل في منظومة") ||
               t.contains("قد يستغرق الرد") || t.contains("تاريخ انتهاء الحزمة:") ||
               t.contains("تفاصيل اشتراك الرقم") || t.contains("تم تحديث الانترنت") ||
               t.contains("مرفوض:") || t.contains("رسائل جديدة") || t.contains("new messages") ||
               t.contains("جاري فحص") || t.contains("مركز الدعم لـ BRAND SIM") ||
               t.contains("مركز الدعم الآلي") || t.contains("يسعدنا خدمتك! يرجى تزويدنا بتفاصيل طلبك") ||
               t.contains("يسعدنا إتمام طلبك!") || t.contains("لتأكيد الحجز فوراً") ||
               t.contains("أهلاً بك في BRAND SIM") || t.contains("فريق دعم BRAND SIM") ||
               t.contains("رقم الشريحة المرتبط بالسيريال") ||
               t.contains("تحقق واتساب") || t.contains("verification code") ||
               t.contains("جاري مكالمة") || t.contains("مكالمة صوتية") || t.contains("مكالمة فيديو") ||
               t.contains("يكتب...") || t.contains("typing...") || t.contains("سجل دردشة");
    }

    public static boolean isDetailsRequestSms(String rawText) {
        if (rawText == null) return false;
        return Pattern.compile("(كم ضايل|قديش ضايل|ضايل بالحزم|ضايل بالحزمة|ضايل بالباقة|كم باقي|تفاصيل|تفصيل|tariq|تاريخ|انتهاء|إنتهاء|تنتهي|ينتهي|بتنتهي|بنتهي|حزمة|حزمه|باقة|باقه|متى|موعد|استعلام|إستعلام|صلاحية|صلاحيه|اشتراك|إشتراك|بيانات|معلومات)").matcher(rawText).find();
    }

    public static boolean isWeakNetRequestSms(String rawText) {
        if (rawText == null) return false;
        return Pattern.compile("(النت ضعيف|انترنت ضعيف|إنترنت ضعيف|نت ضعيف|النت بطيء|نت بطيء|النت مقطوع|النت فاصل|ما في نت|مافي نت|النت واقف)").matcher(rawText).find();
    }

    public static boolean isExplicitRefreshRequestSms(String rawText) {
        if (rawText == null) return false;
        return Pattern.compile("(تحديث|حدث|تنشيط|نشط|تفعيل|فعل|ريفريش|رعنون|refresh|النت ضعيف|نت ضعيف|النت بطيء|النت مقطوع|النت فاصل|ما في نت|مافي نت|النت واقف)", Pattern.CASE_INSENSITIVE).matcher(rawText).find();
    }

    public static boolean isPhoneOnlyMessage(String rawText) {
        if (rawText == null) return false;
        String stripped = rawText.replaceAll("[0-9٠-٩۰-۹\\s\\-\\.\\(\\)\\+\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "").trim();
        return stripped.isEmpty();
    }

    public static String detectMessageMode(String rawText) {
        if (rawText == null) return "refresh";
        if (isDetailsRequestSms(rawText)) return "details";
        if (isWeakNetRequestSms(rawText)) return "weak_net";
        return "refresh";
    }

    public static boolean isIgnoredPrefixPhone(String phone) {
        if (phone == null) return false;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() >= 18) return false; // السماح بارقام السيريال ICCID
        if (digits.startsWith("00972") || digits.startsWith("00970")) digits = "0" + digits.substring(5);
        else if ((digits.startsWith("972") || digits.startsWith("970")) && digits.length() >= 11) digits = "0" + digits.substring(3);
        else if (digits.length() == 9 && digits.startsWith("5")) digits = "0" + digits;
        return digits.startsWith("059") || digits.startsWith("056");
    }

    public static String extractCleanIccid(String rawText) {
        if (rawText == null || rawText.isEmpty() || isIgnoredSystemSms(rawText)) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : rawText.toCharArray()) {
            if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
            else sb.append(c);
        }
        String cleaned = sb.toString().replaceAll("[\\s\\-\\.\\(\\)\\+\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "");
        Matcher m = Pattern.compile("(89\\d{16,20}|\\d{18,22})").matcher(cleaned);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String extractCleanPhone(String rawText, boolean allow059ForDetails) {
        if (rawText == null || rawText.isEmpty() || isIgnoredSystemSms(rawText)) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : rawText.toCharArray()) {
            if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
            else sb.append(c);
        }
        String cleaned = sb.toString().replaceAll("[\\s\\-\\.\\(\\)\\+\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "");
        // إزالة أي سيريال نمبر طويل قبل البحث عن رقم الهاتف لتجنب التداخل
        String withoutIccid = cleaned.replaceAll("(89\\d{16,20}|\\d{18,22})", " ");
        Matcher m = Pattern.compile("(?:00972|00970|972|970|0)?(5\\d{8})").matcher(withoutIccid);
        while (m.find()) {
            String cand = "0" + m.group(1);
            if (!allow059ForDetails && (cand.startsWith("059") || cand.startsWith("056"))) continue;
            return cand;
        }
        return null;
    }

    public static long getSkyRemainingCooldownMs(Context ctx, String cleanPhone) {
        if (cleanPhone == null || cleanPhone.isEmpty()) return 0L;
        try {
            SharedPreferences prefs = ctx.getApplicationContext().getSharedPreferences("BrandSimSkyCooldowns", Context.MODE_PRIVATE);
            long unlockAt = prefs.getLong("sky_unlock_" + cleanPhone, 0L);
            long now = System.currentTimeMillis();
            if (unlockAt > now) {
                return unlockAt - now;
            }
            long lastTime = prefs.getLong("sky_last_" + cleanPhone, 0L);
            if (lastTime <= 0L) return 0L;
            long elapsed = now - lastTime;
            if (elapsed >= 0L && elapsed < GATEWAY_COOLDOWN_MS) {
                return GATEWAY_COOLDOWN_MS - elapsed;
            }
        } catch (Exception e) {}
        return 0L;
    }

    public static void recordSkyRefreshTimestamp(Context ctx, String cleanPhone) {
        recordSkyRefreshWithCustomDuration(ctx, cleanPhone, GATEWAY_COOLDOWN_MS);
    }

    public static void recordSkyRefreshWithCustomDuration(Context ctx, String cleanPhone, long durationMs) {
        if (cleanPhone == null || cleanPhone.isEmpty()) return;
        try {
            long validDur = (durationMs > 0L && durationMs <= GATEWAY_COOLDOWN_MS * 2) ? durationMs : GATEWAY_COOLDOWN_MS;
            long now = System.currentTimeMillis();
            SharedPreferences prefs = ctx.getApplicationContext().getSharedPreferences("BrandSimSkyCooldowns", Context.MODE_PRIVATE);
            prefs.edit()
                .putLong("sky_last_" + cleanPhone, now - (GATEWAY_COOLDOWN_MS - validDur))
                .putLong("sky_unlock_" + cleanPhone, now + validDur)
                .commit();
        } catch (Exception e) {}
    }

    public static String formatRemainingArabicText(long ms) {
        long totalSec = Math.max(1L, (ms + 999L) / 1000L);
        long hours = totalSec / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        long seconds = totalSec % 60L;

        List<String> parts = new ArrayList<>();
        if (hours == 1) parts.add("ساعة واحدة");
        else if (hours == 2) parts.add("ساعتين");
        else if (hours > 2) parts.add(hours + " ساعات");

        if (minutes == 1) parts.add("دقيقة واحدة");
        else if (minutes == 2) parts.add("دقيقتين");
        else if (minutes >= 3 && minutes <= 10) parts.add(minutes + " دقائق");
        else if (minutes > 10) parts.add(minutes + " دقيقة");

        if (hours == 0 && seconds > 0) {
            if (seconds == 1) parts.add("ثانية واحدة");
            else if (seconds == 2) parts.add("ثانيتين");
            else if (seconds >= 3 && seconds <= 10) parts.add(seconds + " ثوانٍ");
            else parts.add(seconds + " ثانية");
        }
        if (parts.isEmpty()) return seconds + " ثانية";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(" و ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static boolean isCooldownServerResponse(JSONObject data) {
        if (data == null) return false;
        if (data.optBoolean("cooldown", false) || data.optBoolean("isCooldown", false)) return true;
        String msg = data.optString("msg", data.optString("rawMsg", ""));
        return Pattern.compile("قمت بتحديث.*مسبق|قمت بتحديث.*قبل|المحاولة.*بعد.*ساع|بعد\\s*3\\s*ساعات|لتكرار الطلب|3\\s*שעות|חמש שעות|רעננת.*לאחרונה|נסה.*בעוד", Pattern.CASE_INSENSITIVE).matcher(msg).find();
    }

    public static long extractServerRemainingMs(JSONObject data) {
        if (data == null) return 0L;
        String[] msFields = {"remainingMs", "cooldownMs", "timeLeftMs", "waitMs", "leftMs"};
        for (String f : msFields) {
            long val = data.optLong(f, 0L);
            if (val > 0L) return val;
        }
        String[] secFields = {"remainingSeconds", "retryAfter", "secondsLeft", "waitSeconds", "leftSeconds"};
        for (String f : secFields) {
            long val = data.optLong(f, 0L);
            if (val > 0L) return val * 1000L;
        }
        String[] minFields = {"remainingMinutes", "minutesLeft", "waitMinutes", "leftMinutes"};
        for (String f : minFields) {
            long val = data.optLong(f, 0L);
            if (val > 0L) return val * 60000L;
        }
        String[] expFields = {"expiresAt", "cooldownUntil", "unlockAt", "nextAllowedAt"};
        long now = System.currentTimeMillis();
        for (String f : expFields) {
            long val = data.optLong(f, 0L);
            if (val > now) return val - now;
        }
        String msg = data.optString("msg", data.optString("rawMsg", ""));
        Matcher clockMatch = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?").matcher(msg);
        if (clockMatch.find()) {
            long h = Long.parseLong(clockMatch.group(1));
            long m = Long.parseLong(clockMatch.group(2));
            long s = clockMatch.group(3) != null ? Long.parseLong(clockMatch.group(3)) : 0L;
            return ((h * 3600L) + (m * 60L) + s) * 1000L;
        }
        Matcher minMatch = Pattern.compile("(\\d+)\\s*(?:دقيقة|دقائق|דקות)").matcher(msg);
        if (minMatch.find()) {
            long totalMin = Long.parseLong(minMatch.group(1));
            if (msg.contains("ساعتين") || msg.contains("שעתיים")) totalMin += 120L;
            else if (msg.contains("ساعة") || msg.contains("שעה")) totalMin += 60L;
            return totalMin * 60000L;
        }
        return 0L;
    }

    public static JSONObject findSubscriberInKnowledgeBase(Context ctx, String cleanPhone) {
        if (cleanPhone == null || cleanPhone.isEmpty()) return null;
        try {
            String rawJson = loadKnowledgeBaseFromAssetsOrPrefs(ctx);
            if (rawJson == null || rawJson.isEmpty()) return null;
            JSONObject root = new JSONObject(rawJson);
            JSONArray subs = root.optJSONArray("subscribers");
            if (subs == null) return null;
            for (int i = 0; i < subs.length(); i++) {
                JSONObject sub = subs.optJSONObject(i);
                if (sub == null) continue;
                String p = normalizeDigitsOnly(sub.optString("phone", ""));
                if (cleanPhone.equals(p)) return sub;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean isSubscriberRecordExpired(JSONObject sub) {
        if (sub == null) return false;
        if (sub.has("is_active") && !sub.optBoolean("is_active", true)) return true;
        String status = sub.optString("status", "").trim();
        if (status.contains("مفصول") || status.contains("معلق") || status.contains("ניתוק")) return true;
        String endIso = sub.optString("end_date_iso", "").trim();
        if (!endIso.isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date endDt = sdf.parse(endIso);
                if (endDt != null && endDt.getTime() + 86399000L < System.currentTimeMillis()) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static JSONObject callGatewayApiDirect(String cleanPhone, String lang) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GATEWAY_API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8");

            JSONObject body = new JSONObject();
            body.put("phone", cleanPhone);
            body.put("lang", lang != null ? lang : "ar");
            body.put("hp", "");
            body.put("ts", System.currentTimeMillis());

            byte[] outBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(outBytes);
                os.flush();
            }

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                if (newUrl != null && !newUrl.isEmpty()) {
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    status = conn.getResponseCode();
                }
            }

            InputStream is = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                String respStr = sb.toString().trim();
                if (respStr.startsWith("{")) {
                    return new JSONObject(respStr);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static String sanitizeBrandMessage(String text) {
        if (text == null) return "";
        String out = text.replaceAll("(?i)Sky\\s*Telecom\\s*2", "Brand sim")
                   .replaceAll("(?i)Sky\\s*Telecom", "Brand sim")
                   .replace("أرقام سكاي", "ارقام براند سيم")
                   .replace("ارقام سكاي", "ارقام براند سيم")
                   .replace("مركز الدعم الآلي لـ BRAND SIM", "مركز الدعم لـ BRAND SIM")
                   .replace("النظام الآلي", "فريق الدعم")
                   .replace("رد تلقائي", "رد الدعم")
                   .replace("سلكום", "سلكوم")
                   .replace("סלקום", "سلكوم")
                   .replace("شامل שירות5G", "شامل خدمة 5G")
                   .replace("שירות", "خدمة")
                   .replace("05-24ג", "05-24");

        // إذا كانت الرسالة هي بطاقة تفاصيل الاشتراك أو نتيجة سيريال، لا تقم بتطبيق فلتر أخطاء المصدر العبرية عليها
        if (out.contains("تفاصيل اشتراك الرقم") || out.contains("رقم الشريحة المرتبط") || out.contains("قائمة الباقات المتوفرة")) {
            return out;
        }

        // تعريب رسائل مزودي الخدمة التي تظهر باللغة العبرية وفقاً للمرجع web-refresh.html
        if (out.contains("חמש שעות") || out.contains("אפשרות לעשות רענון") || out.contains("רענון פעם אחת")) {
            return "⚠️ عذراً، يُسمح بتحديث هذا الرقم مرة واحدة فقط كل 5 ساعات.";
        }
        if (out.contains("בוצע בהצלחה") || out.contains("הרענון בוצע") || out.contains("עודכן בהצלחה")) {
            return "✅ " + MSG_DEFAULT_SUCCESS;
        }
        if (out.contains("פג תוקף") || out.contains("ניתוק קבוע") || out.contains("מנותק")) {
            return "⚠️ " + MSG_EXPIRED_SUB;
        }
        if (out.contains("אינו רשום") || out.contains("לא קיים") || out.contains("לא נמצא")) {
            return "⚠️ " + MSG_NOT_AUTHORIZED;
        }
        if (out.contains("אינו תקין") || out.contains("לא תקין")) {
            return "⚠️ عذراً، الرقم غير صحيح أو غير نشط في النظام.";
        }
        if (Pattern.compile("[\\u0590-\\u05FF]{3,}").matcher(out).find()) {
            if (out.contains("שעות") || out.contains("דקות") || out.contains("רענון")) {
                return "⚠️ " + MSG_COOLDOWN_PREFIX + "عدة ساعات";
            }
            return "⚠️ " + MSG_DEFAULT_ERROR;
        }
        return out;
    }

    public static String formatSkyCooldownMessage(String cleanPhone, long remainingMs) {
        return MSG_COOLDOWN_PREFIX + formatRemainingArabicText(remainingMs);
    }

    public static void sendSmsDirect(final Context ctx, final String sender, final String message) {
        if (sender == null || sender.trim().isEmpty() || message == null || message.trim().isEmpty()) return;
        if ("WEB".equalsIgnoreCase(sender) || "XONE1".equalsIgnoreCase(sender) || "SKY_WEB".equalsIgnoreCase(sender)) return;

        // فحص صلاحية إرسال الردود للقناة المستهدفة قبل الإرسال للزبون
        boolean isWaTarget = sender.contains("_wa_") || sender.startsWith("wa_");
        boolean isTgTarget = sender.contains("_tg_") || sender.startsWith("tg_");
        boolean isMsgTarget = sender.contains("_msg_") || sender.startsWith("msg_");

        if (isWaTarget && !isChannelEnabled(ctx, "reply_whatsapp")) {
            if (instance != null) instance.appendUiLog("🔇 تم كتم إرسال رد واتساب (حسب إعدادات الإرسال)");
            return;
        }
        if (isTgTarget && !isChannelEnabled(ctx, "reply_telegram")) {
            if (instance != null) instance.appendUiLog("🔇 تم كتم إرسال رد تيليجرام (حسب إعدادات الإرسال)");
            return;
        }
        if (isMsgTarget && !isChannelEnabled(ctx, "reply_messenger")) {
            if (instance != null) instance.appendUiLog("🔇 تم كتم إرسال رد مسنجر (حسب إعدادات الإرسال)");
            return;
        }
        if (!isWaTarget && !isTgTarget && !isMsgTarget && !isChannelEnabled(ctx, "reply_sms")) {
            if (instance != null) instance.appendUiLog("🔇 تم كتم إرسال رد SMS للرقم: " + sender);
            return;
        }

        String cleanReply = sanitizeBrandMessage(message).trim();
        lastSentSmsBody = cleanReply;

        WhatsAppReplyAction waAction = waReplyMap.get(sender);
        if (waAction != null) {
            try {
                Intent localIntent = new Intent();
                Bundle localBundle = new Bundle();
                for (RemoteInput remoteIn : waAction.remoteInputs) localBundle.putCharSequence(remoteIn.getResultKey(), cleanReply);
                RemoteInput.addResultsToIntent(waAction.remoteInputs, localIntent, localBundle);
                waAction.pendingIntent.send(ctx, 0, localIntent);
                if (WhatsAppListener.instance != null && waAction.sbnKey != null) {
                    try { WhatsAppListener.instance.cancelNotification(waAction.sbnKey); } catch (Exception ignored) {}
                }
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, "تم إرسال الرد بنجاح", Toast.LENGTH_SHORT).show());
                return;
            } catch (Exception e) { return; }
        }
        if (isWaTarget || isMsgTarget || isTgTarget || !sender.matches("^[+0-9]{7,15}$")) return;
        try {
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(cleanReply);
            sms.sendMultipartTextMessage(sender, null, parts, null, null);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, "تم إرسال رد SMS: " + sender, Toast.LENGTH_SHORT).show());
        } catch (Exception e) {}
    }

    public static boolean isWaitingText(String text) {
        if (text == null) return true;
        String t = text.replaceAll("[\\u064B-\\u065F\\u0670]", "")
                       .replaceAll("[إأآا]", "ا")
                       .replace('ى', 'ي')
                       .replace('ة', 'ه')
                       .replaceAll("[\\s\\-_.,!؟:()]+", " ")
                       .toLowerCase()
                       .trim();
        if (t.length() <= 2) return true;
        String numOnly = t.replaceAll("[^0-9]", "");
        if (numOnly.length() >= 9 && numOnly.length() <= 12 && t.length() <= 15) return true;
        if (t.equals("تحديث") || t.equals("تحديث الرقم") || t.equals("ارسال") || t.equals("بحث") || t.equals("sky telecom")) return true;

        if (t.contains("قبل قليل") || t.contains("فشل") || t.contains("تم تحديث") || t.contains("غير صحيح") || t.contains("غير صالح") || t.contains("غير مسجل") || t.contains("لا يمكن")) {
            return false;
        }
        if (t.contains("بنجاح") && !t.contains("يستغرق") && !t.contains("انتظار")) {
            return false;
        }

        String[] kws = {"يستغرق", "استغراق", "تعاون", "جاري", "معالج", "لحظ", "تحميل", "loading", "please wait", "wait", "processing", "captcha", "كابتشا", "روبوت"};
        for (String kw : kws) if (t.contains(kw)) return true;
        return false;
    }

    private static String lastSkyRunPhone = "";
    private static long lastSkyRunTime = 0;

    @SuppressLint("SetJavaScriptEnabled")
    public static void runHeadlessLayanRefresh(final Context ctx, final String phone, final String sender, final String mode) {
        runHeadlessPortalRefresh(ctx, phone, sender, mode, LAYAN_URL, "ليان");
    }

    public static void runHeadlessSkyRefresh(final Context ctx, final String phone, final String sender, final String mode) {
        runHeadlessPortalRefresh(ctx, phone, sender, mode, SKY_URL, "سكاي");
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void runHeadlessPortalRefresh(final Context ctx, final String phone, final String sender, final String mode, final String targetPortalUrl, final String portalLabel) {
        if (phone == null || isIgnoredPrefixPhone(phone)) return;
        final String cleanPhone = phone.replaceAll("[^0-9]", "");

        long remMs = getSkyRemainingCooldownMs(ctx, cleanPhone);
        if (remMs > 0L) {
            String rejectMsg = formatSkyCooldownMessage(cleanPhone, remMs);
            if (sender != null && !sender.trim().isEmpty()) {
                sendSmsDirect(ctx, sender, rejectMsg);
            }
            if (instance != null) {
                instance.updateUiMessage("warning", rejectMsg);
            }
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, rejectMsg, Toast.LENGTH_LONG).show());
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (cleanPhone.equals(lastSkyRunPhone) && (now - lastSkyRunTime) < 10000) return;
        lastSkyRunPhone = cleanPhone;
        lastSkyRunTime = now;

        final long portalCooldownMs = SKY_URL.equals(targetPortalUrl) ? SKY_PORTAL_COOLDOWN_MS : GATEWAY_COOLDOWN_MS;

        new Handler(Looper.getMainLooper()).post(() -> {
            ensureWebViewProcessDir(ctx.getApplicationContext());
            Toast.makeText(ctx, "جاري تحديث براند سيم بالخلفية: " + cleanPhone, Toast.LENGTH_SHORT).show();
            final WebView skyWebView = new WebView(ctx.getApplicationContext());
            skyWebView.layout(0, 0, 1080, 1920);
            WebSettings ss = skyWebView.getSettings();
            ss.setJavaScriptEnabled(true);
            ss.setDomStorageEnabled(true);
            ss.setCacheMode(WebSettings.LOAD_NO_CACHE);
            ss.setDatabaseEnabled(true);
            ss.setAllowFileAccess(true);
            ss.setAllowContentAccess(true);
            CookieManager.getInstance().setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ss.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                CookieManager.getInstance().setAcceptThirdPartyCookies(skyWebView, true);
            }
            ss.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

            final boolean[] completed = {false};

            final Handler timeoutHandler = new Handler(Looper.getMainLooper());
            final Runnable timeoutRunnable = () -> {
                if (!completed[0]) {
                    completed[0] = true;
                    if (instance != null) instance.appendUiLog("⚠️ انتهت مهلة الاتصال بالبوابة: " + portalLabel);
                    try {
                        skyWebView.stopLoading();
                        skyWebView.setWebChromeClient(null);
                        skyWebView.setWebViewClient(null);
                        skyWebView.clearHistory();
                        skyWebView.clearCache(true);
                        skyWebView.destroy();
                    } catch (Exception e) {}
                }
            };
            timeoutHandler.postDelayed(timeoutRunnable, 120000); // 120 seconds timeout

            final Runnable finishCleanup = () -> {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                try {
                    skyWebView.stopLoading();
                    skyWebView.setWebChromeClient(null);
                    skyWebView.setWebViewClient(null);
                    skyWebView.clearHistory();
                    skyWebView.clearCache(true);
                    skyWebView.destroy();
                } catch (Exception e) {}
            };

            final Object skyBridge = new Object() {
                @JavascriptInterface
                public void onSkyResult(final String skyMsg) {
                    if (completed[0]) return;
                    String trimmed = skyMsg != null ? sanitizeBrandMessage(skyMsg).trim() : "";
                    if (trimmed.isEmpty() || isWaitingText(trimmed)) return;
                    if (!cleanPhone.isEmpty() && cleanPhone.equals(trimmed.replaceAll("[^0-9]", ""))) return;
                    if (trimmed.matches("^0?5\\d{8}$")) return;

                    boolean isNotFoundInPortal = trimmed.contains("غير صحيح") || trimmed.contains("غير صالح") || trimmed.contains("غير مسجل") || trimmed.contains("غير نشط") || trimmed.contains("لا يوجد");
                    if (isNotFoundInPortal && SKY_URL.equals(targetPortalUrl)) {
                        completed[0] = true;
                        new Handler(Looper.getMainLooper()).postDelayed(finishCleanup, 500);
                        if (instance != null) instance.appendUiLog("🔄 الرقم غير موجود في سكاي، جاري التحديث تلقائياً عبر بوابة ليان (rn.layan-t.net): " + cleanPhone);
                        SharedPreferences prefs = ctx.getApplicationContext().getSharedPreferences("BrandSimSkyCooldowns", Context.MODE_PRIVATE);
                        prefs.edit().remove("sky_last_" + cleanPhone).remove("sky_unlock_" + cleanPhone).commit();
                        lastSkyRunPhone = "";
                        runHeadlessPortalRefresh(ctx, cleanPhone, sender, mode, LAYAN_URL, "ليان");
                        return;
                    }
                    completed[0] = true;
                    boolean isFail = trimmed.contains("فشل") || trimmed.contains("قبل قليل") || isNotFoundInPortal || trimmed.contains("خطأ");
                    if (!isFail) {
                        recordSkyRefreshWithCustomDuration(ctx, cleanPhone, portalCooldownMs);
                    }
                    String outMsg = "weak_net".equals(mode) ? ("تم تحديث الانترنت\n" + trimmed) : trimmed;
                    if (sender != null && !sender.trim().isEmpty()) sendSmsDirect(ctx, sender, outMsg);
                    if (instance != null) {
                        instance.updateUiMessage(isFail ? "warning" : "success", outMsg);
                    }
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, "نتيجة التحديث (" + portalLabel + " - " + cleanPhone + "): " + outMsg, Toast.LENGTH_LONG).show());
                    new Handler(Looper.getMainLooper()).postDelayed(finishCleanup, 2000);
                }

                @JavascriptInterface
                public void onCaptchaRequired() {}
            };

            skyWebView.addJavascriptInterface(skyBridge, "SkyBridge");
            skyWebView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                    if (message != null && !isWaitingText(message.trim()) && !completed[0]) {
                        completed[0] = true;
                        String t = sanitizeBrandMessage(message).trim();
                        boolean isFail = t.contains("فشل") || t.contains("قبل قليل") || t.contains("غير صحيح") || t.contains("خطأ");
                        if (!isFail) {
                            recordSkyRefreshWithCustomDuration(ctx, cleanPhone, portalCooldownMs);
                        }
                        String outMsg = "weak_net".equals(mode) ? ("تم تحديث الانترنت\n" + t) : t;
                        if (sender != null && !sender.trim().isEmpty()) sendSmsDirect(ctx, sender, outMsg);
                        if (instance != null) {
                            instance.updateUiMessage(isFail ? "warning" : "success", outMsg);
                        }
                        new Handler(Looper.getMainLooper()).postDelayed(finishCleanup, 2000);
                    }
                    result.confirm();
                    return true;
                }
                @Override
                public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                    result.confirm();
                    return true;
                }
            });

            skyWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) { handler.proceed(); }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (completed[0]) return;
                    String js = "javascript:(function(){"
                        + "var P='" + cleanPhone + "';"
                        + "function isW(s){if(!s)return true;var t=String(s).trim();if(t.length<=2||t.indexOf(P)!==-1)return true;"
                        + "if(/(قبل قليل|فشل|تم تحديث|غير صحيح|غير صالح|لا يمكن)/i.test(t))return false;"
                        + "if(/بنجاح/i.test(t)&&!/(يستغرق|انتظار)/i.test(t))return false;"
                        + "return /(يستغرق|تعاون|جاري|معالج|لحظ|تحميل|تحديث الرقم|sky telecom|loading|wait|captcha|كابتشا)/i.test(t);}"
                        + "function sendRes(m){if(m&&!isW(m))window.SkyBridge.onSkyResult(String(m).replace(/<[^>]*>/g,' ').replace(/\\s+/g,' ').trim());}"
                        + "window.alert=function(m){sendRes(m);};"
                        + "if(window.Swal&&window.Swal.fire){var of=window.Swal.fire;window.Swal.fire=function(){try{var a=arguments[0];sendRes(typeof a==='string'?a:((a.title||'')+' '+(a.text||a.html||'')));}catch(e){}return of.apply(this,arguments);};}"
                        + "function chk(){var els=document.querySelectorAll('.swal2-popup,.modal-content,.alert,[role=alert],#result,.result,#message,.message,.bg-red-100,.bg-green-100,.text-red-700,.text-green-700');"
                        + "for(var i=0;i<els.length;i++){var txt=(els[i].innerText||'').trim();if(txt&&!isW(txt)){sendRes(txt);return true;}}"
                        + "var bodyTxt=document.body.innerText||'';"
                        + "var m=bodyTxt.match(/(فشل[\\s\\S]{1,100}?\\)|تم تحديث[\\s\\S]{1,80}|لقد قمت بتحديث الرقم[\\s\\S]{1,60})/i);"
                        + "if(m&&m[1]){sendRes(m[1]);return true;}"
                        + "return false;}"
                        + "var inp=document.querySelector('input[type=tel],input[type=text],input[type=number],input:not([type=hidden])');"
                        + "if(inp){inp.focus();Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(inp,P);"
                        + "inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "setTimeout(function(){var bs=Array.from(document.querySelectorAll('button,input[type=submit],a.btn'));"
                        + "var b=bs.find(function(x){return /تحديث|ارسال|إرسال|submit|refresh/i.test(x.innerText||x.value||'');})||bs[0];if(b)b.click();"
                        + "var c=0,tm=setInterval(function(){c++;if(chk()||c>360)clearInterval(tm);},500);},700);"
                        + "}"
                        + "})();";
                    view.evaluateJavascript(js, null);
                }
            });

            skyWebView.clearCache(true);
            skyWebView.loadUrl(targetPortalUrl);
        });
    }

    public static void runUnifiedGatewayOrPortalRefresh(final Context ctx, final String phone, final String sender, final String mode, final String preferredProvider) {
        if (phone == null || isIgnoredPrefixPhone(phone)) return;
        final String cleanPhone = extractCleanPhone(phone, false);
        if (cleanPhone == null || !cleanPhone.matches("^05\\d{8}$")) {
            if (sender != null && !sender.trim().isEmpty()) sendSmsDirect(ctx, sender, MSG_INVALID_PHONE);
            if (instance != null) instance.updateUiMessage("warning", MSG_INVALID_PHONE);
            return;
        }

        // 1. فحص صلاحية الاشتراك من قاعدة البيانات (customers.json)
        final JSONObject subObj = findSubscriberInKnowledgeBase(ctx, cleanPhone);
        if (subObj != null && isSubscriberRecordExpired(subObj)) {
            if (sender != null && !sender.trim().isEmpty()) sendSmsDirect(ctx, sender, MSG_EXPIRED_SUB);
            if (instance != null) instance.updateUiMessage("warning", MSG_EXPIRED_SUB);
            return;
        }

        // 2. فحص فترة الانتظار (Cooldown)
        long remMs = getSkyRemainingCooldownMs(ctx, cleanPhone);
        if (remMs > 0L) {
            String rejectMsg = formatSkyCooldownMessage(cleanPhone, remMs);
            if (sender != null && !sender.trim().isEmpty()) sendSmsDirect(ctx, sender, rejectMsg);
            if (instance != null) instance.updateUiMessage("warning", rejectMsg);
            return;
        }

        // 3. الاتصال ببوابة GATEWAY_API_URL المرجعية أولاً في الخلفية، ثم التوجيه لبوابة المزود عند الحاجة
        new Thread(() -> {
            JSONObject data = callGatewayApiDirect(cleanPhone, "ar");
            if (data != null) {
                if (isCooldownServerResponse(data)) {
                    long srvMs = extractServerRemainingMs(data);
                    long finalCd = srvMs > 0L ? srvMs : GATEWAY_COOLDOWN_MS;
                    recordSkyRefreshWithCustomDuration(ctx, cleanPhone, finalCd);
                    String cdMsg = formatSkyCooldownMessage(cleanPhone, finalCd);
                    if (sender != null && !sender.trim().isEmpty()) sendSmsDirect(ctx, sender, cdMsg);
                    if (instance != null) instance.updateUiMessage("warning", cdMsg);
                    return;
                }

                if (data.optBoolean("ok", false)) {
                    long cdMs = data.optLong("cooldownMs", GATEWAY_COOLDOWN_MS);
                    recordSkyRefreshWithCustomDuration(ctx, cleanPhone, cdMs > 0L ? cdMs : GATEWAY_COOLDOWN_MS);
                    String rawMsg = data.optString("msg", MSG_DEFAULT_SUCCESS);
                    String cleanMsg = sanitizeBrandMessage(rawMsg).trim();
                    String outMsg = "weak_net".equals(mode) ? ("تم تحديث الانترنت\n" + cleanMsg) : cleanMsg;
                    if (sender != null && !sender.trim().isEmpty()) sendSmsDirect(ctx, sender, outMsg);
                    if (instance != null) instance.updateUiMessage("success", outMsg);
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, outMsg, Toast.LENGTH_LONG).show());
                    return;
                }

                String errMsg = sanitizeBrandMessage(data.optString("msg", "")).trim();
                if (!errMsg.isEmpty() && (errMsg.contains("منتهي") || errMsg.contains("غير مسجل"))) {
                    if (subObj == null) {
                        if (sender != null && !sender.trim().isEmpty()) sendSmsDirect(ctx, sender, errMsg);
                        if (instance != null) instance.updateUiMessage("warning", errMsg);
                        return;
                    }
                }
            }

            // Fallback إلى بوابة المزود الحقيقية عبر Headless WebView
            String prov = (preferredProvider != null && !preferredProvider.isEmpty())
                ? preferredProvider
                : (subObj != null ? subObj.optString("provider", "Sky") : "Sky");
            String pLower = prov.toLowerCase();
            if (pLower.contains("layan")) {
                runHeadlessPortalRefresh(ctx, cleanPhone, sender, mode, LAYAN_URL, "ليان");
            } else if (pLower.contains("quick")) {
                runHeadlessPortalRefresh(ctx, cleanPhone, sender, mode, QUICKLINE_URL, "كويك لاين");
            } else if (pLower.contains("areen")) {
                runHeadlessPortalRefresh(ctx, cleanPhone, sender, mode, AREEN_URL, "أرين");
            } else if (pLower.contains("ayloul")) {
                runHeadlessPortalRefresh(ctx, cleanPhone, sender, mode, AYLOUL_URL, "أيلول");
            } else {
                runHeadlessPortalRefresh(ctx, cleanPhone, sender, mode, SKY_URL, "سكاي");
            }
        }).start();
    }

    public static class WebAppInterface {
        private final Context ctx;
        public WebAppInterface(Context ctx) { this.ctx = ctx; }

        @JavascriptInterface
        public void autoRefreshSkyWithMode(final String phone, final String sender, final String mode) {
            if (isIgnoredPrefixPhone(phone)) return;
            runUnifiedGatewayOrPortalRefresh(ctx, phone, sender, mode != null ? mode : "refresh", "Sky");
        }

        @JavascriptInterface
        public void autoRefreshLayanWithMode(final String phone, final String sender, final String mode) {
            if (isIgnoredPrefixPhone(phone)) return;
            runUnifiedGatewayOrPortalRefresh(ctx, phone, sender, mode != null ? mode : "refresh", "Layan");
        }

        @JavascriptInterface
        public void autoRefreshByProvider(final String phone, final String sender, final String mode, final String provider) {
            if (isIgnoredPrefixPhone(phone)) return;
            String p = provider != null ? provider.toLowerCase() : "";
            if (p.contains("layan")) {
                runHeadlessPortalRefresh(ctx, phone, sender, mode != null ? mode : "refresh", LAYAN_URL, "ليان");
            } else if (p.contains("ayloul")) {
                runHeadlessPortalRefresh(ctx, phone, sender, mode != null ? mode : "refresh", AYLOUL_URL, "أيلول");
            } else if (p.contains("areen")) {
                runHeadlessPortalRefresh(ctx, phone, sender, mode != null ? mode : "refresh", AREEN_URL, "أرين");
            } else if (p.contains("quickline") || p.contains("quick")) {
                runHeadlessPortalRefresh(ctx, phone, sender, mode != null ? mode : "refresh", QUICKLINE_URL, "كويك لاين");
            } else {
                runHeadlessPortalRefresh(ctx, phone, sender, mode != null ? mode : "refresh", SKY_URL, "سكاي");
            }
        }

        @JavascriptInterface
        public long getSkyCooldownRemainingMs(final String phone) {
            if (phone == null) return 0L;
            return getSkyRemainingCooldownMs(ctx, phone.replaceAll("[^0-9]", ""));
        }

        @JavascriptInterface
        public String getSkyCooldownRejectMsg(final String phone) {
            if (phone == null) return "";
            String clean = phone.replaceAll("[^0-9]", "");
            long rem = getSkyRemainingCooldownMs(ctx, clean);
            return rem > 0L ? formatSkyCooldownMessage(clean, rem) : "";
        }

        @JavascriptInterface
        public void markSkyRefreshed(final String phone) {
            if (phone == null) return;
            recordSkyRefreshTimestamp(ctx, phone.replaceAll("[^0-9]", ""));
        }

        @JavascriptInterface
        public void sendSmsReply(final String sender, final String message) {
            sendSmsDirect(ctx, sender, message);
            if (instance != null) instance.updateUiMessage("success", message);
        }

        @JavascriptInterface
        public String getGeminiApiKey() {
            return getSavedGeminiApiKey(ctx);
        }

        @JavascriptInterface
        public void setGeminiApiKey(final String key) {
            saveGeminiApiKey(ctx, key);
        }

        @JavascriptInterface
        public void openGeminiApiKeyDialog() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (instance != null) instance.showGeminiApiKeyDialog();
            });
        }

        @JavascriptInterface
        public String askGeminiNative(final String fullPrompt) {
            return callGeminiApiDirect(ctx, fullPrompt);
        }

        @JavascriptInterface
        public void askGeminiAndReplyAsync(final String fullPrompt, final String sender, final String senderTitle, final String fallbackReply) {
            new Thread(() -> {
                String aiReply = callGeminiApiDirect(ctx, fullPrompt);
                String finalReply = (aiReply != null && aiReply.trim().length() > 3) ? aiReply.trim() : (fallbackReply != null ? fallbackReply.trim() : "");
                if (finalReply.isEmpty()) {
                    finalReply = "أهلاً بك في مركز دعم BRAND SIM 🌟\nيرجى تزويدنا برقم الشريحة (05XXXXXXXX) أو السيريال (89972...) أو استفسارك وسنقوم بخدمتك فوراً.";
                }
                final String msgToSend = finalReply;
                if (instance != null) {
                    instance.appendUiLog("🤖 رد Gemini الذكي إلى (" + (senderTitle != null && !senderTitle.isEmpty() ? senderTitle : sender) + "): " + msgToSend);
                }
                if (sender != null && !sender.isEmpty() && !"WEB".equalsIgnoreCase(sender)) {
                    sendSmsDirect(ctx, sender, msgToSend);
                } else if (instance != null) {
                    instance.updateUiMessage("success", msgToSend);
                }
            }).start();
        }

        @JavascriptInterface
        public void openChannelsDialog() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (instance != null) instance.showChannelsConfigDialog();
            });
        }

        @JavascriptInterface
        public void openReplyChannelsDialog() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (instance != null) instance.showReplyChannelsConfigDialog();
            });
        }

        @JavascriptInterface
        public void setFullChannelsConfig(boolean enableSms, boolean enableWa, boolean enableTg, boolean enableMsg, boolean enableAi) {
            saveAllChannelsState(ctx, enableSms, enableWa, enableTg, enableMsg, enableAi);
            if (instance != null) instance.syncWebUiState();
        }

        @JavascriptInterface
        public void setFullReplyChannelsConfig(boolean replySms, boolean replyWa, boolean replyTg, boolean replyMsg) {
            saveAllReplyChannelsState(ctx, replySms, replyWa, replyTg, replyMsg);
        }

        @JavascriptInterface
        public void setAiAgentEnabled(boolean enableAi) {
            saveAllChannelsState(
                ctx,
                isChannelEnabled(ctx, "enable_sms"),
                isChannelEnabled(ctx, "enable_whatsapp"),
                isChannelEnabled(ctx, "enable_telegram"),
                isChannelEnabled(ctx, "enable_messenger"),
                enableAi
            );
            if (instance != null) instance.syncWebUiState();
        }

        @JavascriptInterface
        public boolean isSmsChannelEnabled() { return isChannelEnabled(ctx, "enable_sms"); }

        @JavascriptInterface
        public boolean isWaChannelEnabled() { return isChannelEnabled(ctx, "enable_whatsapp"); }

        @JavascriptInterface
        public boolean isTgChannelEnabled() { return isChannelEnabled(ctx, "enable_telegram"); }

        @JavascriptInterface
        public boolean isMessengerChannelEnabled() { return isChannelEnabled(ctx, "enable_messenger"); }

        @JavascriptInterface
        public boolean isAiAgentEnabled() { return isChannelEnabled(ctx, "enable_ai_agent"); }

        @JavascriptInterface
        public boolean isReplySmsEnabled() { return isChannelEnabled(ctx, "reply_sms"); }

        @JavascriptInterface
        public boolean isReplyWaEnabled() { return isChannelEnabled(ctx, "reply_whatsapp"); }

        @JavascriptInterface
        public boolean isReplyTgEnabled() { return isChannelEnabled(ctx, "reply_telegram"); }

        @JavascriptInterface
        public boolean isReplyMessengerEnabled() { return isChannelEnabled(ctx, "reply_messenger"); }

        // --- واجهات إدارة قائمة الاستثناءات والمجموعات ---
        @JavascriptInterface
        public String getBlacklist() {
            return getSavedBlacklist(ctx);
        }

        @JavascriptInterface
        public void saveBlacklistData(final String blacklistStr) {
            saveBlacklist(ctx, blacklistStr);
        }

        @JavascriptInterface
        public boolean isIgnoreAllGroups() {
            return isIgnoreAllGroupsEnabled(ctx);
        }

        @JavascriptInterface
        public void setIgnoreAllGroups(boolean ignore) {
            saveIgnoreAllGroups(ctx, ignore);
        }

        @JavascriptInterface
        public void openSettingsMenu() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (instance != null) {
                    View anchor = instance.floatingSettingsBtn != null ? instance.floatingSettingsBtn : instance.mainWebView;
                    instance.showSettingsDropdownMenu(anchor);
                }
            });
        }

        // --- واجهات قاعدة المعرفة والتغذية الراجعة (customers.json) مع الدمج الذكي والكاش السريع ---
        @JavascriptInterface
        public String getSystemKnowledgeBase() {
            return loadKnowledgeBaseFromAssetsOrPrefs(ctx);
        }

        @JavascriptInterface
        public String saveSystemKnowledgeBase(final String jsonStr) {
            return mergeAndSaveKnowledgeBase(ctx, jsonStr);
        }

        // --- واجهات حفظ ومزامنة أسعار المتجر والباقات ---
        @JavascriptInterface
        public String getCustomPricesJson() {
            try {
                return ctx.getSharedPreferences("BrandSimPrices", Context.MODE_PRIVATE).getString("cached_store_data", "");
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public void saveCustomPricesJson(final String jsonStr) {
            try {
                ctx.getSharedPreferences("BrandSimPrices", Context.MODE_PRIVATE).edit()
                    .putString("cached_store_data", jsonStr)
                    .commit();
            } catch (Exception e) {}
        }
    }

    public static class RefreshService extends Service {
        public static volatile RefreshService instance;
        private static WebView sharedBgWebView;
        private static boolean isBgLoaded = false;
        private static final Queue<String[]> queue = new LinkedList<>();
        private static String lastEnqueuedItemKey = "";
        private static long lastEnqueuedItemTime = 0;
        private PowerManager.WakeLock wakeLock;
        private static final String CHANNEL_ID = "brand_sim_bg_channel_locked";
        public static final int NOTIF_ID = 1;
        private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
        private final Runnable watchdogTask = new Runnable() {
            @Override
            public void run() {
                try {
                    startForegroundNotification();
                    ensureWaListenerAlive(getApplicationContext(), false);
                    if (sharedBgWebView == null) initBgWebView(getApplicationContext());
                    else drainQueue();
                } catch (Exception e) {}
                watchdogHandler.postDelayed(this, 10000);
            }
        };

        @Override
        public void onCreate() {
            super.onCreate();
            instance = this;
            ensureWebViewProcessDir(getApplicationContext());
            startForegroundNotification();
            ensureWaListenerAlive(getApplicationContext(), false);
            initBgWebView(getApplicationContext());
            watchdogHandler.removeCallbacks(watchdogTask);
            watchdogHandler.postDelayed(watchdogTask, 10000);
        }

        public static void initBgWebView(final Context appCtx) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (sharedBgWebView != null) {
                        if (isBgLoaded) drainQueue();
                        return;
                    }
                    sharedBgWebView = new WebView(appCtx.getApplicationContext());
                    configureWebView(sharedBgWebView, appCtx.getApplicationContext());
                    sharedBgWebView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            isBgLoaded = true;
                            drainQueue();
                        }
                        @Override
                        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                            try {
                                if (sharedBgWebView != null) sharedBgWebView.destroy();
                            } catch (Exception e) {}
                            sharedBgWebView = null;
                            isBgLoaded = false;
                            initBgWebView(appCtx.getApplicationContext());
                            return true;
                        }
                    });
                    sharedBgWebView.loadUrl("file:///android_asset/refresh.html");
                } catch (Exception e) {}
            });
        }

        public static void runDirectFallbackFull(Context appCtx, String phone, String sender, String mode, String rawBody, String senderTitle) {
            if ((phone == null || phone.isEmpty()) && !"ai_agent".equals(mode)) return;
            if (phone != null && !phone.isEmpty() && !"ai_agent".equals(mode) && !"iccid_lookup".equals(mode) && isIgnoredPrefixPhone(phone)) return;
            enqueueStaticRequest(appCtx.getApplicationContext(), phone, sender, mode, rawBody, senderTitle);
        }

        public void enqueueFullRequest(String phone, String sender, String mode, String rawBody, String senderTitle) {
            acquireTempWakeLock();
            enqueueStaticRequest(getApplicationContext(), phone, sender, mode, rawBody, senderTitle);
        }

        private static void enqueueStaticRequest(Context appCtx, String phone, String sender, String mode, String rawBody, String senderTitle) {
            if ((phone == null || phone.isEmpty()) && !"ai_agent".equals(mode)) return;
            if (phone != null && !phone.isEmpty() && !"ai_agent".equals(mode) && !"iccid_lookup".equals(mode) && isIgnoredPrefixPhone(phone)) return;
            String safePhone = phone != null ? phone : "";
            String safeSender = sender != null ? sender : "";
            String safeMode = mode != null ? mode : "refresh";
            String safeRaw = rawBody != null ? rawBody : "";
            String safeTitle = senderTitle != null ? senderTitle : "";

            String key = safePhone + "|" + safeSender + "|" + safeMode + "|" + safeRaw;
            long now = SystemClock.elapsedRealtime();
            synchronized (queue) {
                if (key.equals(lastEnqueuedItemKey) && (now - lastEnqueuedItemTime) < 8000) return;
                lastEnqueuedItemKey = key;
                lastEnqueuedItemTime = now;
                queue.add(new String[]{safePhone, safeSender, safeMode, safeRaw, safeTitle});
            }
            if (sharedBgWebView == null) initBgWebView(appCtx);
            else drainQueue();
        }

        public static Notification buildLockedNotification(Context ctx) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "BRAND SIM Auto Refresh", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setSound(null, null);
                ch.enableVibration(false);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }

            Intent openIntent = new Intent(ctx, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, openIntent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

            Intent restoreIntent = new Intent(ctx, SmsReceiver.class);
            restoreIntent.setAction("com.brandsim.refresh.RESTORE_NOTIFICATION");
            PendingIntent deletePi = PendingIntent.getBroadcast(ctx, 999, restoreIntent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

            boolean aiOn = isChannelEnabled(ctx, "enable_ai_agent");
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(ctx, CHANNEL_ID) : new Notification.Builder(ctx);
            b.setContentTitle(aiOn ? "BRAND SIM | مركز الدعم والخدمات 🌟" : "BRAND SIM يعمل بالخلفية")
             .setContentText("تحديث الشرائح والرد الذكي على الزبائن (مع استثناء المجموعات)")
             .setSmallIcon(R.mipmap.ic_launcher)
             .setContentIntent(pi)
             .setDeleteIntent(deletePi)
             .setOngoing(true)
             .setAutoCancel(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                b.setCategory(Notification.CATEGORY_SERVICE);
                b.setVisibility(Notification.VISIBILITY_PUBLIC);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
            }

            Notification notif = b.build();
            notif.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR | Notification.FLAG_FOREGROUND_SERVICE;
            return notif;
        }

        public void startForegroundNotification() {
            try {
                Notification notif = buildLockedNotification(this);
                if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                else startForeground(NOTIF_ID, notif);
            } catch (Exception e) {
                try {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.notify(NOTIF_ID, buildLockedNotification(this));
                } catch (Exception ignored) {}
            }
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            instance = this;
            startForegroundNotification();
            ensureWaListenerAlive(getApplicationContext(), false);
            if (sharedBgWebView == null) initBgWebView(getApplicationContext());
            if (intent != null && intent.hasExtra("sync_api_key")) {
                String k = intent.getStringExtra("sync_api_key");
                if (k != null && !k.trim().isEmpty()) {
                    getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE).edit()
                        .putString("gemini_api_key", k.trim())
                        .commit();
                }
            }
            if (intent != null && intent.getBooleanExtra("sync_channels", false)) {
                getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE).edit()
                    .putBoolean("enable_sms", intent.getBooleanExtra("enable_sms", true))
                    .putBoolean("enable_whatsapp", intent.getBooleanExtra("enable_whatsapp", true))
                    .putBoolean("enable_telegram", intent.getBooleanExtra("enable_telegram", true))
                    .putBoolean("enable_messenger", intent.getBooleanExtra("enable_messenger", true))
                    .putBoolean("enable_ai_agent", intent.getBooleanExtra("enable_ai_agent", true))
                    .commit();
                startForegroundNotification();
            }
            if (intent != null && intent.getBooleanExtra("sync_reply_channels", false)) {
                getSharedPreferences("BrandSimConfig", Context.MODE_PRIVATE).edit()
                    .putBoolean("reply_sms", intent.getBooleanExtra("reply_sms", true))
                    .putBoolean("reply_whatsapp", intent.getBooleanExtra("reply_whatsapp", true))
                    .putBoolean("reply_telegram", intent.getBooleanExtra("reply_telegram", true))
                    .putBoolean("reply_messenger", intent.getBooleanExtra("reply_messenger", true))
                    .commit();
            }
            if (intent != null && (intent.hasExtra("sms_target_phone") || intent.hasExtra("sms_raw_body"))) {
                String phone = intent.getStringExtra("sms_target_phone");
                String sender = intent.getStringExtra("sms_sender");
                String mode = intent.getStringExtra("sms_mode");
                String rawBody = intent.getStringExtra("sms_raw_body");
                String senderTitle = intent.getStringExtra("sms_sender_title");
                if ("ai_agent".equals(mode) || "iccid_lookup".equals(mode) || (phone != null && !phone.isEmpty() && !isIgnoredPrefixPhone(phone))) {
                    enqueueFullRequest(
                        phone != null ? phone : "",
                        sender != null ? sender : "",
                        mode != null ? mode : "refresh",
                        rawBody != null ? rawBody : "",
                        senderTitle != null ? senderTitle : ""
                    );
                }
            } else {
                drainQueue();
            }
            return START_STICKY;
        }

        @Override
        public void onTaskRemoved(Intent rootIntent) {
            startForegroundNotification();
            ensureWaListenerAlive(getApplicationContext(), true);
            scheduleServiceRestart(getApplicationContext(), 800);
            super.onTaskRemoved(rootIntent);
        }

        @Override
        public void onDestroy() {
            if (instance == this) instance = null;
            watchdogHandler.removeCallbacksAndMessages(null);
            scheduleServiceRestart(getApplicationContext(), 800);
            super.onDestroy();
        }

        private void acquireTempWakeLock() {
            try {
                if (wakeLock == null) {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BrandSim::SmsWakeLock");
                }
                if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(240000);
            } catch (Exception e) {}
        }

        private static String escapeJsString(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "");
        }

        private static void drainQueue() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isBgLoaded || sharedBgWebView == null) return;
                synchronized (queue) {
                    while (!queue.isEmpty()) {
                        String[] item = queue.poll();
                        if (item != null) {
                            String phone = escapeJsString(item[0]);
                            String sender = escapeJsString(item[1]);
                            String mode = escapeJsString(item[2]);
                            String rawBody = item.length > 3 ? escapeJsString(item[3]) : "";
                            String senderTitle = item.length > 4 ? escapeJsString(item[4]) : "";

                            if ("ai_agent".equals(mode)) {
                                String jsCall = "javascript:if(window.executeAiCustomerSupport){window.executeAiCustomerSupport('" + sender + "','" + rawBody + "','" + senderTitle + "');}";
                                sharedBgWebView.evaluateJavascript(jsCall, null);
                            } else {
                                if (!phone.isEmpty() && !"iccid_lookup".equals(mode) && isIgnoredPrefixPhone(phone)) continue;
                                sharedBgWebView.evaluateJavascript("javascript:if(window.executeSmsAutoRefresh){window.executeSmsAutoRefresh('" + phone + "','" + sender + "','" + mode + "','" + rawBody + "','" + senderTitle + "');}", null);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public IBinder onBind(Intent intent) { return null; }
    }

    public static class WhatsAppListener extends NotificationListenerService {
        public static WhatsAppListener instance;
        public static volatile boolean isConnected = false;
        private static String lastNotifKey = "";
        private static long lastNotifTime = 0;

        @Override
        public void onCreate() {
            super.onCreate();
            instance = this;
            ensureWebViewProcessDir(getApplicationContext());
            RefreshService.initBgWebView(getApplicationContext());
            startBackgroundRefreshService(getApplicationContext(), null, null, null);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            instance = this;
            RefreshService.initBgWebView(getApplicationContext());
            startBackgroundRefreshService(getApplicationContext(), null, null, null);
            return START_STICKY;
        }

        @Override
        public void onListenerConnected() {
            super.onListenerConnected();
            instance = this;
            isConnected = true;
            RefreshService.initBgWebView(getApplicationContext());
            startBackgroundRefreshService(getApplicationContext(), null, null, null);
        }

        @Override
        public void onListenerDisconnected() {
            super.onListenerDisconnected();
            isConnected = false;
            if (instance == this) instance = null;
            ensureWaListenerAlive(getApplicationContext(), true);
            scheduleServiceRestart(getApplicationContext(), 1000);
        }

        @Override
        public void onTaskRemoved(Intent rootIntent) {
            super.onTaskRemoved(rootIntent);
            ensureWaListenerAlive(getApplicationContext(), true);
            startBackgroundRefreshService(getApplicationContext(), null, null, null);
            scheduleServiceRestart(getApplicationContext(), 800);
        }

        @Override
        public void onNotificationRemoved(StatusBarNotification sbn) {
            super.onNotificationRemoved(sbn);
            try {
                if (sbn != null && getPackageName().equals(sbn.getPackageName()) && sbn.getId() == RefreshService.NOTIF_ID) {
                    if (RefreshService.instance != null) {
                        RefreshService.instance.startForegroundNotification();
                    } else {
                        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        if (nm != null) nm.notify(RefreshService.NOTIF_ID, RefreshService.buildLockedNotification(getApplicationContext()));
                    }
                    startBackgroundRefreshService(getApplicationContext(), null, null, null);
                }
            } catch (Exception e) {}
        }

        private boolean isWhatsAppPackage(String pkg) {
            if (pkg == null) return false;
            return "com.whatsapp".equals(pkg) || "com.whatsapp.w4b".equals(pkg);
        }

        private boolean isMessengerPackage(String pkg) {
            if (pkg == null) return false;
            return "com.facebook.orca".equals(pkg) || "com.facebook.mlite".equals(pkg);
        }

        private boolean isTelegramPackage(String pkg) {
            if (pkg == null) return false;
            return "org.telegram.messenger".equals(pkg) ||
                   "org.telegram.messenger.web".equals(pkg) ||
                   "org.telegram.messenger.beta".equals(pkg) ||
                   "org.telegram.plus".equals(pkg) ||
                   "tw.nekomimi.nekogram".equals(pkg) ||
                   "org.thunderdog.challegram".equals(pkg) ||
                   pkg.startsWith("org.telegram.");
        }

        @Override
        public void onNotificationPosted(StatusBarNotification sbn) {
            if (sbn == null) return;
            isConnected = true;
            instance = this;
            try {
                String pkg = sbn.getPackageName();
                boolean isWa = isWhatsAppPackage(pkg);
                boolean isMsg = isMessengerPackage(pkg);
                boolean isTg = isTelegramPackage(pkg);
                if (!isWa && !isMsg && !isTg) return;

                if (isWa && !isChannelEnabled(this, "enable_whatsapp")) return;
                if (isMsg && !isChannelEnabled(this, "enable_messenger")) return;
                if (isTg && !isChannelEnabled(this, "enable_telegram")) return;

                Notification notif = sbn.getNotification();
                if (notif == null || notif.extras == null || (notif.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

                // فحص المجموعات العامة في الإشعارات
                boolean isGroupConversation = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    isGroupConversation = notif.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);
                }

                CharSequence titleCs = notif.extras.getCharSequence(Notification.EXTRA_TITLE);
                String title = titleCs != null ? titleCs.toString().trim() : "";

                if (isGroupConversation) {
                    if (isIgnoreAllGroupsEnabled(this)) {
                        return; // تم تجاهل إشعار المجموعة تلقائياً
                    }
                }

                String body = "";
                CharSequence[] lines = notif.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                if (lines != null && lines.length > 0 && lines[lines.length - 1] != null) body = lines[lines.length - 1].toString().trim();
                if (body.isEmpty()) {
                    CharSequence textCs = notif.extras.getCharSequence(Notification.EXTRA_TEXT);
                    if (textCs != null) body = textCs.toString().trim();
                }
                if (body.isEmpty()) {
                    CharSequence bigTextCs = notif.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                    if (bigTextCs != null) body = bigTextCs.toString().trim();
                }
                if (body.isEmpty() || isIgnoredSystemSms(body)) return;

                // فحص قائمة الاستثناءات (مثل كويك لاين، رامز، إلخ)
                if (isTargetBlacklisted(this, title, "", body)) {
                    if (MainActivity.instance != null) {
                        MainActivity.instance.appendUiLog("⛔ تم تجاهل رسالة من القائمة المستثناة: " + title);
                    }
                    return;
                }

                if (body.contains("SKY_WEB:")) {
                    String skyPhone = extractCleanPhone(body, false);
                    if (skyPhone != null && !skyPhone.isEmpty() && !isIgnoredPrefixPhone(skyPhone)) {
                        long now = SystemClock.elapsedRealtime();
                        String dedupKey = "NOTIF_SKY_WEB:" + skyPhone;
                        if (dedupKey.equals(lastNotifKey) && (now - lastNotifTime) < 12000) return;
                        lastNotifKey = dedupKey;
                        lastNotifTime = now;
                        try { cancelNotification(sbn.getKey()); } catch (Exception ignored) {}
                        runHeadlessSkyRefresh(getApplicationContext(), skyPhone, null, "refresh");
                        return;
                    }
                }

                WhatsAppReplyAction replyAction = extractReplyAction(notif, sbn.getKey());
                String senderPhoneFromTitle = extractCleanPhone(title, false);
                String prefix = isWa ? "wa_" : (isMsg ? "msg_" : "tg_");
                String senderKey = senderPhoneFromTitle != null ? (senderPhoneFromTitle + "_" + prefix + Math.abs(title.hashCode())) : (prefix + Math.abs(title.hashCode()));
                if (replyAction != null) {
                    waReplyMap.put(senderKey, replyAction);
                }
                String targetSender = replyAction != null ? senderKey : "";

                // 0. فحص إذا أرسل الزبون رقم سيريال (ICCID) للرد عليه فوراً برقم الشريحة
                String extractedIccid = extractCleanIccid(body);
                if (extractedIccid != null && !extractedIccid.isEmpty()) {
                    String dedupKey = prefix + "iccid_lookup:" + extractedIccid + ":" + title;
                    long now = SystemClock.elapsedRealtime();
                    if (dedupKey.equals(lastNotifKey) && (now - lastNotifTime) < 12000) return;
                    lastNotifKey = dedupKey;
                    lastNotifTime = now;
                    if (MainActivity.instance != null) {
                        MainActivity.instance.appendUiLog("💳 فحص سيريال ICCID (" + (isWa ? "واتساب" : isMsg ? "مسنجر" : "تيليجرام") + "): " + extractedIccid);
                    }
                    RefreshService.runDirectFallbackFull(getApplicationContext(), extractedIccid, targetSender, "iccid_lookup", body, title);
                    startBackgroundRefreshServiceWithRawMessage(getApplicationContext(), extractedIccid, targetSender, "iccid_lookup", body, title);
                    return;
                }

                // 1. إذا كانت الرسالة تحتوي على رقم هاتف (تحديث أو استعلام حزمة أو ضعف نت)
                String mode = detectMessageMode(body);
                String extractedPhone = extractCleanPhone(body, false);
                if (extractedPhone != null && !extractedPhone.isEmpty() && !isIgnoredPrefixPhone(extractedPhone)) {
                    String dedupKey = prefix + mode + ":" + extractedPhone + ":" + title + ":" + body;
                    long now = SystemClock.elapsedRealtime();
                    if (dedupKey.equals(lastNotifKey) && (now - lastNotifTime) < 12000) return;
                    lastNotifKey = dedupKey;
                    lastNotifTime = now;
                    if (MainActivity.instance != null) {
                        MainActivity.instance.appendUiLog("📥 إشعار (" + (isWa ? "واتساب" : isMsg ? "مسنجر" : "تيليجرام") + ") رقم: " + extractedPhone);
                    }
                    RefreshService.runDirectFallbackFull(getApplicationContext(), extractedPhone, targetSender, mode, body, title);
                    startBackgroundRefreshServiceWithRawMessage(getApplicationContext(), extractedPhone, targetSender, mode, body, title);
                    return;
                }

                // 2. إذا لم يكن هناك رقم هاتف ولكن موظف الدعم مفعل
                if (isChannelEnabled(this, "enable_ai_agent") && !targetSender.isEmpty()) {
                    String dedupKey = prefix + "AI:" + title + ":" + body;
                    long now = SystemClock.elapsedRealtime();
                    if (dedupKey.equals(lastNotifKey) && (now - lastNotifTime) < 15000) return;
                    lastNotifKey = dedupKey;
                    lastNotifTime = now;
                    RefreshService.runDirectFallbackFull(getApplicationContext(), "", targetSender, "ai_agent", body, title);
                    startBackgroundRefreshServiceWithRawMessage(getApplicationContext(), "", targetSender, "ai_agent", body, title);
                }
            } catch (Exception e) {}
        }

        private WhatsAppReplyAction extractReplyAction(Notification notif, String sbnKey) {
            try {
                if (notif.actions != null) {
                    for (Notification.Action a : notif.actions) {
                        if (a != null && a.getRemoteInputs() != null && a.getRemoteInputs().length > 0 && a.actionIntent != null) {
                            return new WhatsAppReplyAction(a.actionIntent, a.getRemoteInputs(), sbnKey);
                        }
                    }
                }
                for (Notification.Action a : new Notification.WearableExtender(notif).getActions()) {
                    if (a != null && a.getRemoteInputs() != null && a.getRemoteInputs().length > 0 && a.actionIntent != null) {
                        return new WhatsAppReplyAction(a.actionIntent, a.getRemoteInputs(), sbnKey);
                    }
                }
            } catch (Exception e) {}
            return null;
        }
    }

    public static class SmsReceiver extends BroadcastReceiver {
        private static String lastProcessedKey = "";
        private static long lastProcessedTime = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            try {
                Context appCtx = context.getApplicationContext();
                ensureWebViewProcessDir(appCtx);
                String action = intent.getAction();
                if ("com.brandsim.refresh.RESTORE_NOTIFICATION".equals(action) ||
                    Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                    "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                    Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                    Intent.ACTION_USER_PRESENT.equals(action) ||
                    "com.brandsim.refresh.RESTART_SERVICE".equals(action)) {
                    try {
                        if (RefreshService.instance != null) {
                            RefreshService.instance.startForegroundNotification();
                        } else {
                            NotificationManager nm = (NotificationManager) appCtx.getSystemService(Context.NOTIFICATION_SERVICE);
                            if (nm != null) nm.notify(RefreshService.NOTIF_ID, RefreshService.buildLockedNotification(appCtx));
                        }
                    } catch (Exception ignored) {}
                    ensureWaListenerAlive(appCtx, true);
                    RefreshService.initBgWebView(appCtx);
                    startBackgroundRefreshService(appCtx, null, null, null);
                    return;
                }
                if (!isChannelEnabled(appCtx, "enable_sms")) return;
                if (!"android.provider.Telephony.SMS_RECEIVED".equals(action)) return;
                Bundle bundle = intent.getExtras();
                if (bundle == null) return;
                Object[] pdus = (Object[]) bundle.get("pdus");
                String format = bundle.getString("format");
                if (pdus == null || pdus.length == 0) return;

                StringBuilder fullMsg = new StringBuilder();
                String sender = "";
                for (Object pdu : pdus) {
                    SmsMessage sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? SmsMessage.createFromPdu((byte[]) pdu, format) : SmsMessage.createFromPdu((byte[]) pdu);
                    if (sms != null) {
                        if (sender.isEmpty() && sms.getOriginatingAddress() != null) sender = sms.getOriginatingAddress();
                        if (sms.getMessageBody() != null) fullMsg.append(sms.getMessageBody());
                    }
                }
                String body = fullMsg.toString();
                if (isIgnoredSystemSms(body)) return;

                // فحص قائمة الاستثناءات في الـ SMS
                if (isTargetBlacklisted(appCtx, sender, sender, body)) {
                    if (MainActivity.instance != null) {
                        MainActivity.instance.appendUiLog("⛔ تم استثناء وتجاهل SMS من: " + sender);
                    }
                    return;
                }

                // الحالة الأولى لـ SMS: إذا أرسل الزبون رقم سيريال (ICCID) نرد عليه فوراً برقم الزبون
                String extractedIccid = extractCleanIccid(body);
                if (extractedIccid != null && !extractedIccid.isEmpty()) {
                    String dedupKey = "SMS_ICCID:" + extractedIccid + ":" + sender;
                    long now = SystemClock.elapsedRealtime();
                    if (dedupKey.equals(lastProcessedKey) && (now - lastProcessedTime) < 12000) return;
                    lastProcessedKey = dedupKey;
                    lastProcessedTime = now;
                    String replyTarget = (sender != null && sender.matches("^[+0-9]+$")) ? sender : "";

                    if (MainActivity.instance != null) {
                        MainActivity.instance.appendUiLog("💳 SMS سيريال ICCID: " + extractedIccid);
                    }

                    final PendingResult pr = goAsync();
                    RefreshService.runDirectFallbackFull(appCtx, extractedIccid, replyTarget, "iccid_lookup", body, sender);
                    startBackgroundRefreshServiceWithRawMessage(appCtx, extractedIccid, replyTarget, "iccid_lookup", body, sender);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try { pr.finish(); } catch (Exception ignored) {}
                    }, 8000);
                    return;
                }

                // الحالة الثانية لـ SMS: فقط إذا أرسل الرقم لوحده أو طلب تحديث الرقم (لا نرد على تفاصيل أو دردشة ذكاء اصطناعي في SMS)
                String extractedPhone = extractCleanPhone(body, false);
                if (extractedPhone != null && !extractedPhone.isEmpty() && !isIgnoredPrefixPhone(extractedPhone)) {
                    boolean shouldRefresh = isPhoneOnlyMessage(body) || isExplicitRefreshRequestSms(body);
                    if (!shouldRefresh) {
                        return;
                    }

                    String mode = isWeakNetRequestSms(body) ? "weak_net" : "refresh";
                    String dedupKey = "SMS_REF:" + mode + ":" + extractedPhone + ":" + sender;
                    long now = SystemClock.elapsedRealtime();
                    // ✅ تم إصلاح فحص منع التكرار
                    if (dedupKey.equals(lastProcessedKey) && (now - lastProcessedTime) < 12000) return;
                    lastProcessedKey = dedupKey;
                    lastProcessedTime = now;
                    String replyTarget = (sender == null || sender.contains("XONE") || !sender.matches("^[+0-9]+$")) ? extractedPhone : sender;

                    if (MainActivity.instance != null) {
                        MainActivity.instance.appendUiLog("📥 SMS تحديث رقم: " + extractedPhone);
                    }

                    final PendingResult pr = goAsync();
                    RefreshService.runDirectFallbackFull(appCtx, extractedPhone, replyTarget, mode, body, sender);
                    startBackgroundRefreshServiceWithRawMessage(appCtx, extractedPhone, replyTarget, mode, body, sender);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try { pr.finish(); } catch (Exception ignored) {}
                    }, 8000);
                    return;
                }
            } catch (Exception e) {}
        }
    }

    @Override
    public void onBackPressed() {
        if (mainWebView != null && mainWebView.canGoBack()) mainWebView.goBack();
        else moveTaskToBack(true);
    }
}
