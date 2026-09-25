package com.financetracker.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;

public class MainActivity extends BridgeActivity {

    private static final String TAG        = "Paypathz.Main";
    private static final String PREFS_NAME = "FinanceTrackerPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWebView();
        setupJsBridge();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void setupWebView() {
        WebView webView = getBridge().getWebView();

        // Enable popups — required for Google GSI sign-in popup
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Handle popup windows (GSI opens one for sign-in)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                WebView popupView = new WebView(MainActivity.this);
                popupView.getSettings().setJavaScriptEnabled(true);
                popupView.getSettings().setSupportMultipleWindows(true);

                popupView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        String url = req.getUrl().toString();
                        Log.d(TAG, "Popup URL: " + url);
                        // If Google sends the callback to our app, handle it
                        if (url.startsWith("com.financetracker.app:")) {
                            handleOAuthCallback(url);
                            popupView.destroy();
                            return true;
                        }
                        // For storagerelay and other Google URLs, load normally
                        return false;
                    }

                    @Override
                    public void onPageFinished(WebView v, String url) {
                        // Close popup once Google auth is done and we're back
                        if (url != null && url.startsWith("https://localhost")) {
                            v.destroy();
                        }
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupView);
                resultMsg.sendToTarget();
                return true;
            }
        });
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // OAuth callback via custom scheme
        Uri data = intent.getData();
        if (data != null && "com.financetracker.app".equals(data.getScheme())) {
            handleOAuthCallback(data.toString());
            return;
        }

        // Notification tap → navigate to SMS tab
        String openTab = intent.getStringExtra("openTab");
        String smsId   = intent.getStringExtra("smsId");
        if ("sms".equals(openTab)) {
            getBridge().getWebView().postDelayed(() -> {
                String js = "window.__openSmsTab && window.__openSmsTab('" + (smsId != null ? smsId : "") + "');";
                getBridge().getWebView().evaluateJavascript(js, null);
            }, 800);
        }
    }

    private void handleOAuthCallback(String url) {
        try {
            Uri uri = Uri.parse(url);
            String code  = uri.getQueryParameter("code");
            String error = uri.getQueryParameter("error");
            Log.d(TAG, "OAuth callback — code=" + (code != null ? "present" : "null") + " error=" + error);

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (code != null) {
                prefs.edit().putString("ft_oauth_code", code).remove("ft_oauth_error").apply();
            } else {
                prefs.edit().putString("ft_oauth_error", error != null ? error : "cancelled")
                     .remove("ft_oauth_code").apply();
            }
            getBridge().getWebView().postDelayed(() ->
                getBridge().getWebView().evaluateJavascript(
                    "document.dispatchEvent(new Event('resume'));", null), 500);
        } catch (Exception e) {
            Log.e(TAG, "OAuth callback error", e);
        }
    }

    private void setupJsBridge() {
        getBridge().getWebView().addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        Log.d(TAG, "JS bridge registered");
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
        public boolean isAndroidApp() { return true; }
    }
}
