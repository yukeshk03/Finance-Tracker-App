package com.financetracker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "FT_SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null) return;

        StringBuilder fullMessage = new StringBuilder();
        String sender = "";
        // The actual time the SMS arrived on this device (epoch millis).
        // We take the timestamp from the FIRST PDU since multi-part SMS share one timestamp.
        long smsReceivedMillis = 0;

        for (Object pdu : pdus) {
            SmsMessage sms;
            // Use non-deprecated method for Android 6+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && format != null) {
                sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            } else {
                sms = SmsMessage.createFromPdu((byte[]) pdu);
            }
            if (sms == null) continue;
            sender = sms.getDisplayOriginatingAddress();
            fullMessage.append(sms.getMessageBody());
            // Capture timestamp from first PDU only (rest of multi-part SMS share it)
            if (smsReceivedMillis == 0) {
                smsReceivedMillis = sms.getTimestampMillis();
            }
        }

        String body = fullMessage.toString().trim();
        if (body.isEmpty()) return;

        // Fallback if for any reason getTimestampMillis() returned 0
        if (smsReceivedMillis == 0) {
            smsReceivedMillis = System.currentTimeMillis();
        }

        Log.d(TAG, "SMS from: " + sender + " | " + body.substring(0, Math.min(60, body.length())) + " | ts=" + smsReceivedMillis);

        if (TransactionParser.isBankMessage(sender, body)) {
            Log.d(TAG, "Bank SMS detected — parsing...");
            TransactionParser.parseAndNotify(context, sender, body, smsReceivedMillis);
        }
    }
}
