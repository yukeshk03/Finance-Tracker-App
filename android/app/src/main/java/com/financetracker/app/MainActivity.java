package com.financetracker.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final String TAG        = "Paypathz.Main";
    private static final String PREFS_NAME = "FinanceTrackerPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupJsBridge();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        Uri data = intent.getData();
        if (data != null) {
            String scheme = data.getScheme();

            // ── OAuth deep link: com.financetracker.app://oauth?code=XXX ──
            if ("com.financetracker.app".equals(scheme) && "oauth".equals(data.getHost())) {
                String code  = data.getQueryParameter("code");
                String error = data.getQueryParameter("error");
                Log.d(TAG, "OAuth deep link: code=" + (code != null ? "present" : "null"));

                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                if (code != null) {
                    prefs.edit().putString("ft_oauth_code", code).remove("ft_oauth_error").apply();
                } else {
                    prefs.edit().putString("ft_oauth_error", error != null ? error : "cancelled")
                         .remove("ft_oauth_code").apply();
                }
                // Fire resume event to WebView so app picks up the code
                getBridge().getWebView().postDelayed(() ->
                    getBridge().getWebView().evaluateJavascript(
                        "document.dispatchEvent(new Event('resume'));", null), 300);
                return;
            }

            // ── SMS notification tap: financetracker://sms ──
            if ("financetracker".equals(scheme) && "sms".equals(data.getHost())) {
                String smsId = intent.getStringExtra("smsId");
                getBridge().getWebView().postDelayed(() -> {
                    String js = "window.__openSmsTab && window.__openSmsTab('" +
                                (smsId != null ? smsId : "") + "');";
                    getBridge().getWebView().evaluateJavascript(js, null);
                }, 800);
            }
        }
    }

    private void setupJsBridge() {
        getBridge().getWebView().addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        Log.d(TAG, "AndroidBridge registered");
    }

    public class AndroidBridge {

        @JavascriptInterface
        public String getPendingSms() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String json = prefs.getString("ft_pending_sms", "[]");
            prefs.edit().putString("ft_pending_sms", "[]").apply();
            return json;
        }

        @JavascriptInterface
        public String getOAuthCode() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String code = prefs.getString("ft_oauth_code", "");
            prefs.edit().remove("ft_oauth_code").apply();
            Log.d(TAG, "getOAuthCode: " + (code.isEmpty() ? "empty" : "present"));
            return code;
        }

        @JavascriptInterface
        public String getOAuthError() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String error = prefs.getString("ft_oauth_error", "");
            prefs.edit().remove("ft_oauth_error").apply();
            return error;
        }

        @JavascriptInterface
        public boolean isAndroidApp() {
            return true;
        }
    }
}
