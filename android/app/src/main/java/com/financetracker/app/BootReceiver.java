package com.financetracker.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Fires on device boot.
 * 1. Reconnects NotificationListenerService
 * 2. Checks if export reminder is due — fires a notification if so
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG        = "FinanceTracker.Boot";
    private static final String PREFS_NAME = "FinanceTrackerPrefs";
    private static final String CHANNEL_ID = "finance_tracker_export";
    private static final int    EXPORT_NOTIF_ID = 9001;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) &&
            !"android.intent.action.QUICKBOOT_POWERON".equals(action)) return;

        Log.d(TAG, "Boot completed — checking export reminder");
        createChannel(context);
        checkExportReminder(context);
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Export Reminders", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Reminds you to back up your Finance Tracker data");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void checkExportReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastExport  = prefs.getString("ft_last_export_date", "");
        int reminderDays   = prefs.getInt("ft_export_reminder_days", 7);

        boolean overdue  = false;
        long daysSince   = -1;

        if (lastExport == null || lastExport.isEmpty()) {
            overdue = true;
        } else {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                Date last = sdf.parse(lastExport);
                Date now  = new Date();
                if (last != null) {
                    daysSince = TimeUnit.MILLISECONDS.toDays(now.getTime() - last.getTime());
                    overdue   = daysSince >= reminderDays;
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not parse export date: " + e.getMessage());
                overdue = true;
            }
        }

        if (!overdue) { Log.d(TAG, "Export up to date"); return; }

        String body = (lastExport == null || lastExport.isEmpty())
            ? "You have never backed up Finance Tracker data. Open the app to export."
            : "Last backup was " + daysSince + " day" + (daysSince == 1 ? "" : "s") + " ago. Open Finance Tracker to export.";

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra("openTab", "settings");
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent pi = PendingIntent.getActivity(context, EXPORT_NOTIF_ID, openIntent, piFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Finance Tracker — Back up your data")
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try { nm.notify(EXPORT_NOTIF_ID, builder.build()); }
            catch (SecurityException e) { Log.w(TAG, "No notification permission: " + e.getMessage()); }
        }
    }
}
