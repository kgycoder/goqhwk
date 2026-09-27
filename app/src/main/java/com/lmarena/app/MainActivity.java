package com.lmarena.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String DIRECT_URL = "https://arena.ai/text/direct";
    private static final String GOOGLE_LOGIN_URL = "https://accounts.google.com";
    private static final String ARENA_DOMAIN = "arena.ai";
    private static final String PREFS_NAME = "lmarena_prefs";
    private static final String KEY_LOGGED_IN = "logged_in";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button loginBtn;
    private View loginCard;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        loginBtn = findViewById(R.id.loginBtn);
        loginCard = findViewById(R.id.loginCard);

        setupWebView();

        loginBtn.setOnClickListener(v -> startLogin());

        // Check if already logged in via saved cookies
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(ARENA_DOMAIN);
        if (cookies != null && cookies.contains("session")) {
            proceedToChat();
        }
        // else show login card
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Allow navigation within google auth and arena.ai
                if (url.contains("google.com") || url.contains("arena.ai") ||
                    url.contains("accounts.google") || url.contains("oauth2")) {
                    return false;
                }
                // Open other URLs in browser
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                // Check if we landed on arena.ai after login
                if (url.contains(ARENA_DOMAIN) && !url.contains("login") && !url.contains("signin")) {
                    checkLoginSuccess(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                if (url.contains(ARENA_DOMAIN) && !url.contains("login")) {
                    // Inject check for login state
                    view.evaluateJavascript(
                        "(function() { " +
                        "  var h = document.body.innerText || ''; " +
                        "  return h.toLowerCase().indexOf('experience') >= 0 || " +
                        "         h.toLowerCase().indexOf('sign in') < 0; " +
                        "})()",
                        value -> {
                            if ("true".equals(value)) {
                                saveLoginState();
                                runOnUiThread(() -> proceedToChat());
                            }
                        }
                    );
                }
            }
        });

        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });
    }

    private void startLogin() {
        loginCard.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        statusText.setText(R.string.logging_in);
        progressBar.setVisibility(View.VISIBLE);
        webView.loadUrl(DIRECT_URL);
    }

    private void checkLoginSuccess(String url) {
        if (url.startsWith("https://arena.ai/") || url.equals(DIRECT_URL)) {
            saveLoginState();
            runOnUiThread(this::proceedToChat);
        }
    }

    private void saveLoginState() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        ed.putBoolean(KEY_LOGGED_IN, true);
        ed.apply();
        CookieManager.getInstance().flush();
    }

    private void proceedToChat() {
        webView.setVisibility(View.GONE);
        loginCard.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        startActivity(new Intent(this, ChatActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
