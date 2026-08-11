package com.financetracker.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.BridgeActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends BridgeActivity {

    private static final String TAG        = "FinanceTracker.Main";
    private static final String PREFS_NAME = "FinanceTrackerPrefs";
    private static final String CHANNEL_ID = "finance_tracker_export";
    private static final int    EXPORT_NOTIF_ID = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupJsBridge();
        handleNotificationTap(getIntent());
        createExportNotificationChannel();
        // Check export reminder on every cold start
        BootReceiver.checkAndNotify(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotificationTap(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check export reminder each time app comes to foreground
        // This gives a "daily notification" effect without needing AlarmManager
        BootReceiver.checkAndNotify(this);
    }

    // ── JavaScript bridge ─────────────────────────────────────────────────
    private void setupJsBridge() {
        WebView webView = getBridge().getWebView();
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        Log.d(TAG, "JS bridge registered");
    }

    public class AndroidBridge {

        @JavascriptInterface
        public String getPendingSms() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String json = prefs.getString("ft_pending_sms", "[]");
            prefs.edit().putString("ft_pending_sms", "[]").apply();
            Log.d(TAG, "getPendingSms: " + json.substring(0, Math.min(120, json.length())));
            return json;
        }

        @JavascriptInterface
        public void markSmsProcessed(String smsId) {
            Log.d(TAG, "markSmsProcessed: " + smsId);
        }

        @JavascriptInterface
        public boolean isAndroidApp() {
            return true;
        }

        // Called by React after every export so Java stays in sync
        @JavascriptInterface
        public void recordExportDate(String dateStr) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("ft_last_export_date", dateStr)
                .apply();
            Log.d(TAG, "Export date recorded: " + dateStr);
            // Cancel the pending export reminder notification
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(EXPORT_NOTIF_ID);
        }

        // Called by React when user changes the reminder day setting
        @JavascriptInterface
        public void setExportReminderDays(int days) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt("ft_export_reminder_days", days)
                .apply();
            Log.d(TAG, "Export reminder days set: " + days);
        }
    }

    // ── Export notification channel ───────────────────────────────────────
    private void createExportNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Export Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            ch.setDescription("Reminds you to back up your Finance Tracker data");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    // ── Handle notification tap ───────────────────────────────────────────
    private void handleNotificationTap(Intent intent) {
        if (intent == null) return;
        String openTab = intent.getStringExtra("openTab");
        String smsId   = intent.getStringExtra("smsId");

        if ("sms".equals(openTab)) {
            Log.d(TAG, "Opened via SMS notification — smsId=" + smsId);
            getBridge().getWebView().postDelayed(() -> {
                String js = "window.__openSmsTab && window.__openSmsTab('" + (smsId != null ? smsId : "") + "');";
                getBridge().getWebView().evaluateJavascript(js, null);
            }, 800);
        } else if ("settings".equals(openTab)) {
            Log.d(TAG, "Opened via export reminder notification");
            getBridge().getWebView().postDelayed(() -> {
                String js = "window.__openSettingsTab && window.__openSettingsTab();";
                getBridge().getWebView().evaluateJavascript(js, null);
            }, 800);
        }
    }
}
