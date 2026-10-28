package com.dh4x.menu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String HOME = "file:///android_asset/www/index.html";
    private static final String[] FF = { "com.dts.freefireth", "com.dts.freefiremax" };

    private FrameLayout root;
    private WebView web;
    private boolean recriando = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0710);
        setContentView(root);

        web = criarWeb();
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        boolean restaurou = false;
        if (b != null) {
            try { restaurou = (web.restoreState(b) != null); } catch (Throwable t) { restaurou = false; }
        }
        if (!restaurou) web.loadUrl(HOME);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView criarWeb() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setBlockNetworkLoads(false);
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDefaultTextEncodingName("utf-8");
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        w.setBackgroundColor(0xFF0A0710);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.addJavascriptInterface(new Ponte(), "FFNative");
        w.setWebViewClient(new Cli());
        try { WebView.setWebContentsDebuggingEnabled(true); } catch (Throwable t) { }
        return w;
    }

    /* ---------- nunca mostra tela de erro ---------- */
    private class Cli extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            return tratar(r.getUrl() == null ? "" : r.getUrl().toString());
        }
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, String url) {
            return tratar(url == null ? "" : url);
        }
        @Override
        public void onPageFinished(WebView v, String url) {
            js("try{window.dh4xOnFocus&&window.dh4xOnFocus()}catch(e){}");
        }
        @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) { }
        @Override public void onReceivedError(WebView v, int code, String desc, String url) { }
        @Override
        public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
            recriar();
            return true;
        }
    }

    private boolean tratar(String url) {
        if (url == null || url.length() == 0) return true;
        String u = url.toLowerCase();
        if (u.startsWith("http://127.0.0.1") || u.startsWith("http://localhost")) return true;
        if (u.startsWith("intent:") || u.startsWith("market://") || u.startsWith("http://")
                || u.startsWith("https://") || u.startsWith("tg://") || u.startsWith("whatsapp://")) {
            abrir(url);
            return true;
        }
        return true;
    }

    private void recriar() {
        if (recriando) return;
        recriando = true;
        try {
            if (web != null) { root.removeView(web); web.destroy(); }
        } catch (Throwable t) { }
        web = criarWeb();
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        web.loadUrl(HOME);
        recriando = false;
    }

    private void js(final String code) {
        final WebView w = web;
        if (w == null) return;
        w.post(new Runnable() { public void run() {
            try { w.evaluateJavascript(code, null); } catch (Throwable t) { }
        }});
    }

    /* ---------- ciclo de vida: zero recarga ---------- */
    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        js("try{window.dh4xOnFocus&&window.dh4xOnFocus()}catch(e){}");
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) js("try{window.dh4xOnFocus&&window.dh4xOnFocus()}catch(e){}");
    }

    @Override
    protected void onResume() {
        super.onResume();
        js("try{window.dh4xOnFocus&&window.dh4xOnFocus()}catch(e){}");
    }

    @Override protected void onPause() { super.onPause(); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        try { if (web != null) web.saveState(out); } catch (Throwable t) { }
    }

    @Override
    protected void onDestroy() {
        try { if (web != null) { root.removeView(web); web.destroy(); } } catch (Throwable t) { }
        web = null;
        super.onDestroy();
    }

    /* ---------- helpers ---------- */
    private String pkgFF() {
        for (String p : FF) {
            try {
                if (getPackageManager().getLaunchIntentForPackage(p) != null) return p;
            } catch (Throwable t) { }
        }
        return null;
    }

    private void ligarServico() {
        try {
            Intent i = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Throwable t) {
            try { startService(new Intent(this, OverlayService.class)); } catch (Throwable t2) { }
        }
    }

    private void pedir() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            if (Settings.canDrawOverlays(this)) return;
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Toast.makeText(this, "Ative 'Sobrepor outros apps' para o DH4X MENU", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t2) { }
        }
    }

    private void abrir(String url) {
        try {
            Intent i;
            if (url.toLowerCase().startsWith("intent:")) {
                i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) {
            try {
                Intent i2 = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String p = i2.getPackage();
                if (p != null) {
                    Intent m = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + p));
                    m.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(m);
                }
            } catch (Throwable e2) { }
        }
    }

    /* ---------- ponte JS ---------- */
    public class Ponte {

        @JavascriptInterface public String versao() { return "4.0"; }
        @JavascriptInterface public String pkg() { return getPackageName(); }

        @JavascriptInterface public boolean temSobreposicao() {
            return OverlayService.podeSobrepor(MainActivity.this);
        }

        @JavascriptInterface public void pedirSobreposicao() {
            runOnUiThread(new Runnable() { public void run() { pedir(); } });
        }

        @JavascriptInterface public void abrirPareamento() {
            runOnUiThread(new Runnable() { public void run() {
                if (!OverlayService.podeSobrepor(MainActivity.this)) {
                    pedir();
                    Toast.makeText(MainActivity.this,
                            "Conceda 'Sobrepor outros apps' e toque em PAREAR de novo",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                ligarServico();
                OverlayService.mostrarPareamento(true);
            }});
        }

        @JavascriptInterface public void fecharPareamento() {
            runOnUiThread(new Runnable() { public void run() { OverlayService.mostrarPareamento(false); } });
        }

        @JavascriptInterface public void abrirPainel() {
            runOnUiThread(new Runnable() { public void run() {
                if (!OverlayService.podeSobrepor(MainActivity.this)) { pedir(); return; }
                ligarServico();
                OverlayService.mostrarPainel(true);
            }});
        }

        @JavascriptInterface public void fecharPainel() {
            runOnUiThread(new Runnable() { public void run() { OverlayService.mostrarPainel(false); } });
        }

        @JavascriptInterface public void mostrarCirculo(final boolean on) {
            runOnUiThread(new Runnable() { public void run() {
                ligarServico();
                OverlayService.mostrarCirculo(on);
            }});
        }

        /* ---- injecao sincronizada ---- */
        @JavascriptInterface public void injetar() {
            runOnUiThread(new Runnable() { public void run() {
                if (!OverlayService.podeSobrepor(MainActivity.this)) {
                    pedir();
                    Toast.makeText(MainActivity.this,
                            "Conceda 'Sobrepor outros apps' antes de injetar",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                String p = pkgFF();
                if (p == null) {
                    Toast.makeText(MainActivity.this, "Free Fire nao instalado", Toast.LENGTH_SHORT).show();
                    return;
                }
                ligarServico();
                OverlayService.iniciarInjecao(p);
            }});
        }

        @JavascriptInterface public void setFov(final boolean on, final int r) {
            runOnUiThread(new Runnable() { public void run() { OverlayService.defFov(on, r); } });
        }

        @JavascriptInterface public int syncEstado() {
            return OverlayService.estadoSync();
        }

        @JavascriptInterface public void abrirFreeFire() {
            runOnUiThread(new Runnable() { public void run() {
                String p = pkgFF();
                if (p == null) { Toast.makeText(MainActivity.this, "Free Fire nao encontrado",
                        Toast.LENGTH_SHORT).show(); return; }
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage(p);
                    if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
                } catch (Throwable t) { }
            }});
        }

        @JavascriptInterface public void abrirDevOptions() {
            runOnUiThread(new Runnable() { public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable e) { }
                }
            }});
        }

        @JavascriptInterface public void abrirIntent(final String url) {
            runOnUiThread(new Runnable() { public void run() { abrir(url); } });
        }

        @JavascriptInterface public String colar() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                        && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                    if (s != null) return s.toString();
                }
            } catch (Throwable t) { }
            return "";
        }

        @JavascriptInterface public void copiar(final String t) {
            runOnUiThread(new Runnable() { public void run() {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("dh4x", t));
                } catch (Throwable e) { }
            }});
        }

        @JavascriptInterface public void bypass() {
            runOnUiThread(new Runnable() { public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) { }
            }});
        }

        @JavascriptInterface public void toast(final String t) {
            runOnUiThread(new Runnable() { public void run() {
                Toast.makeText(MainActivity.this, t, Toast.LENGTH_SHORT).show();
            }});
        }

        @JavascriptInterface public void sair() {
            runOnUiThread(new Runnable() { public void run() { finish(); } });
        }
    }
}