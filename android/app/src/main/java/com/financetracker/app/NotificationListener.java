package com.financetracker.app;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * Intercepts notifications from banking apps (HDFC, GPay, PhonePe, Paytm, etc.)
 * Works even when the SMS is delivered as a push notification rather than a plain SMS.
 * Stays active in background and after device restart.
 */
public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "FinanceTracker.NL";

    // Banking and UPI app package names to watch
    private static final String[] WATCHED_PACKAGES = {
        "com.snapwork.hdfc",           // HDFC Bank Mobile Banking
        "com.mobikwik_new",            // MobiKwik
        "net.one97.paytm",             // Paytm
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",             // PhonePe
        "com.icici.iMobile",           // ICICI iMobile
        "com.csam.icici.bank.imobile", // ICICI iMobile alternate
        "com.sbi.lotusintouch",        // SBI YONO
        "com.infrasoft.obcbank",       // OBC Bank
        "com.axis.mobile",             // Axis Mobile
        "com.kotak.mahindra.kotak",    // Kotak Bank
        "com.indusind.bank",           // IndusInd Bank
        "com.idbibank.abhay",          // IDBI Bank
        "com.miui.securitycenter",     // MIUI (catches SMS notifications on MIUI)
        "com.android.mms",             // Stock SMS app
        "com.google.android.apps.messaging", // Google Messages
        "org.telegram.messenger",      // Telegram (some banks send here)
        "com.whatsapp",                // WhatsApp (bank bots)
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();

        // Check watched packages
        boolean isWatched = false;
        for (String watchedPkg : WATCHED_PACKAGES) {
            if (watchedPkg.equals(pkg)) { isWatched = true; break; }
        }

        // Also check if it's the default SMS app on this device
        if (!isWatched) {
            PackageManager pm = getPackageManager();
            String defaultSmsApp = android.provider.Telephony.Sms.getDefaultSmsPackage(this);
            if (pkg.equals(defaultSmsApp)) isWatched = true;
        }

        if (!isWatched) return;

        Notification notif = sbn.getNotification();
        if (notif == null) return;

        Bundle extras = notif.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
        String body = textSeq != null ? textSeq.toString() : "";

        // Also try big text (expanded notifications)
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (bigText != null && bigText.length() > body.length()) {
            body = bigText.toString();
        }

        String fullText = (title + " " + body).trim();
        Log.d(TAG, "Notification from " + pkg + ": " + fullText.substring(0, Math.min(80, fullText.length())));

        if (TransactionParser.isBankMessage(pkg, fullText)) {
            Log.d(TAG, "Bank notification detected — parsing...");
            TransactionParser.parseAndNotify(this, pkg, fullText);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed
    }

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "NotificationListenerService connected");
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "NotificationListenerService disconnected — will reconnect");
        // Request rebind so listener stays active
        requestRebind(getComponentName());
    }
}
