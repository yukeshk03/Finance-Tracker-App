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

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends BridgeActivity {

    private static final String TAG        = "FinanceTracker.Main";
    private static final String PREFS_NAME = "FinanceTrackerPrefs";
    private static final String PREFS_KEY  = "ft_pending_sms";
    private static final String CHANNEL_ID = "finance_tracker_export";
    private static final int    EXPORT_NOTIF_ID = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupJsBridge();
        handleNotificationTap(getIntent());
        createExportNotificationChannel();
        checkExportReminder();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotificationTap(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkExportReminder(); // re-check every time app comes to foreground
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
            String json = prefs.getString(PREFS_KEY, "[]");
            prefs.edit().putString(PREFS_KEY, "[]").apply();
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
            // Cancel any pending export reminder notification
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

    // ── Export reminder notification ──────────────────────────────────────
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

    private void checkExportReminder() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastExport = prefs.getString("ft_last_export_date", "");
        int reminderDays = prefs.getInt("ft_export_reminder_days", 7);

        boolean overdue = false;
        long daysSince = -1;

        if (lastExport == null || lastExport.isEmpty()) {
            // Never exported — remind after first day of use
            overdue = true;
            daysSince = 0;
        } else {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                Date last = sdf.parse(lastExport);
                Date now = new Date();
                if (last != null) {
                    daysSince = TimeUnit.MILLISECONDS.toDays(now.getTime() - last.getTime());
                    overdue = daysSince >= reminderDays;
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not parse last export date: " + lastExport);
                overdue = true;
            }
        }

        if (!overdue) {
            Log.d(TAG, "Export up to date — " + daysSince + " days since last export");
            return;
        }

        Log.d(TAG, "Export overdue — firing reminder notification (daysSince=" + daysSince + ")");

        String body = lastExport == null || lastExport.isEmpty()
            ? "You have never backed up your data. Tap to export now."
            : "Last backup was " + daysSince + " day" + (daysSince == 1 ? "" : "s") + " ago. Tap to export now.";

        // Intent opens app to Settings tab
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("openTab", "settings");
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent pi = PendingIntent.getActivity(this, EXPORT_NOTIF_ID, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Finance Tracker — Back up your data")
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            try { nm.notify(EXPORT_NOTIF_ID, builder.build()); }
            catch (SecurityException e) { Log.w(TAG, "No notification permission: " + e.getMessage()); }
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
