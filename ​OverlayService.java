package com.dh4x.menu;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

public class OverlayService extends Service {

    private static final String U_PANEL = "file:///android_asset/www/panel.html";
    private static final String U_PAIR  = "file:///android_asset/www/pair.html";

    private static final int SYNC_PARADO    = 0;
    private static final int SYNC_ABRINDO   = 1;
    private static final int SYNC_CARREGANDO = 2;
    private static final int SYNC_PRONTO    = 3;

    private static OverlayService INST;
    private static Boolean pendPainel = null;
    private static Boolean pendPair = null;

    private Handler ui;
    private WindowManager wm;
    private float den = 2f;
    private int sw = 720, sh = 1280;

    private TextView circle;
    private GradientDrawable cBg;
    private View fov;
    private WebView painel, pareamento;

    private WindowManager.LayoutParams cLp, fLp, pLp, aLp;
    private boolean montado = false;
    private boolean painelAberto = false;
    private boolean pairAberto = false;
    private int pDX, pDY, aDX, aDY;

    private boolean fovOn = false;
    private int fovRaio = 120;

    /* ---- sync da injecao ---- */
    private boolean syncAtivo = false;
    private int syncEstado = SYNC_PARADO;
    private long syncInicio = 0L;
    private int syncEstavel = 0;
    private boolean syncViuJogo = false;
    private String syncPkg = "com.dts.freefireth";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        INST = this;
        ui = new Handler(Looper.getMainLooper());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        try { den = getResources().getDisplayMetrics().density; } catch (Throwable t) { den = 2f; }
        if (den <= 0) den = 2f;

        medir();
        try { startForeground(77, notif()); } catch (Throwable t) { }
        montar();

        if (pendPainel != null) {
            final boolean b = pendPainel; pendPainel = null;
            ui.postDelayed(new Runnable() { public void run() { if (b) abrirPainel(); } }, 450);
        }
        if (pendPair != null) {
            final boolean b = pendPair; pendPair = null;
            ui.postDelayed(new Runnable() { public void run() { if (b) abrirPareamento(); } }, 650);
        }
    }

    @Override
    public int onStartCommand(Intent i, int f, int s) {
        montar();
        return START_STICKY;
    }

    /* ================== API estatica ================== */
    public static boolean podeSobrepor(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        try { return Settings.canDrawOverlays(c); } catch (Throwable t) { return false; }
    }

    public static void mostrarPareamento(boolean on) {
        if (INST == null) { pendPair = on; return; }
        INST.ui.post(new Runnable() { public void run() {
            if (on) INST.abrirPareamento(); else INST.fecharPareamento();
        }});
    }

    public static void mostrarPainel(boolean on) {
        if (INST == null) { pendPainel = on; return; }
        INST.ui.post(new Runnable() { public void run() {
            if (on) INST.abrirPainel(); else INST.fecharPainel();
        }});
    }

    public static void mostrarCirculo(boolean on) {
        if (INST == null) return;
        INST.ui.post(new Runnable() { public void run() {
            if (INST.circle != null) INST.circle.setVisibility(on ? View.VISIBLE : View.GONE);
        }});
    }

    public static void defFov(final boolean on, final int raio) {
        if (INST == null) return;
        INST.ui.post(new Runnable() { public void run() { INST.setFov(on, raio); } });
    }

    public static int estadoSync() {
        return INST == null ? SYNC_PARADO : INST.syncEstado;
    }

    public static void iniciarInjecao(final String pkg) {
        if (INST == null) return;
        INST.ui.post(new Runnable() { public void run() { INST.iniciarInjecaoInterno(pkg); } });
    }

    /* ================== metricas ================== */
    private void medir() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowMetrics m = wm.getCurrentWindowMetrics();
                Rect r = m.getBounds();
                if (r.width() > 0) sw = r.width();
                if (r.height() > 0) sh = r.height();
            } else {
                DisplayMetrics d = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(d);
                if (d.widthPixels > 0) sw = d.widthPixels;
                if (d.heightPixels > 0) sh = d.heightPixels;
            }
        } catch (Throwable t) {
            try {
                sw = getResources().getDisplayMetrics().widthPixels;
                sh = getResources().getDisplayMetrics().heightPixels;
            } catch (Throwable e) { }
        }
        if (sw <= 0) sw = 720;
        if (sh <= 0) sh = 1280;
    }

    private int dp(float v) { return (int) (v * den + 0.5f); }
    private int lim(int v, int a, int b) { return v < a ? a : (v > b ? b : v); }

    private WindowManager.LayoutParams base(int w, int h) {
        int tipo = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h, tipo,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    /* ================== montagem ================== */
    private void montar() {
        if (montado) return;
        if (!podeSobrepor(this)) return;
        montado = true;
        criarCirculo();
        criarFov();
        criarPainel();
        criarPareamento();
    }

    private void criarCirculo() {
        TextView tv = new TextView(this);
        tv.setText("DH");
        tv.setTextColor(0xFFFF69B4);
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        cBg = new GradientDrawable();
        cBg.setShape(GradientDrawable.OVAL);
        cBg.setColor(0xF01B1224);
        cBg.setStroke(dp(2), 0xFFA992C0);
        tv.setBackground(cBg);
        circle = tv;

        circle.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean mov;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY();
                        ox = cLp.x; oy = cLp.y; mov = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (e.getRawX() - sx), dy = (int) (e.getRawY() - sy);
                        if (Math.abs(dx) > 14 || Math.abs(dy) > 14) mov = true;
                        cLp.x = lim(ox + dx, 0, Math.max(0, sw - cLp.width));
                        cLp.y = lim(oy + dy, 0, Math.max(0, sh - cLp.height));
                        try { wm.updateViewLayout(circle, cLp); } catch (Throwable t) { }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!mov) alternarPainel();
                        return true;
                }
                return false;
            }
        });

        cLp = base(dp(64), dp(64));
        cLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        cLp.x = dp(14);
        cLp.y = dp(110);
        try { wm.addView(circle, cLp); } catch (Throwable t) { }
    }

    private void criarFov() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(2), 0xFFFF69B4);
        fov = new View(this);
        fov.setBackground(g);
        fov.setVisibility(View.GONE);
        fLp = base(dp(200), dp(200));
        fLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        try { wm.addView(fov, fLp); } catch (Throwable t) { }
    }

    private void setFov(boolean on, int raio) {
        fovOn = on;
        fovRaio = raio;
        if (fov == null || fLp == null) return;
        if (on && raio > 24) {
            int d = dp(raio * 2);
            if (d > sh) d = sh;
            fLp.width = d;
            fLp.height = d;
            fLp.x = Math.max(0, (sw - d) / 2);
            fLp.y = Math.max(0, (sh - d) / 2);
            fov.setVisibility(View.VISIBLE);
            try { wm.updateViewLayout(fov, fLp); } catch (Throwable t) { }
        } else {
            fov.setVisibility(View.GONE);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView novoWeb(String tag) {
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
        w.setBackgroundColor(Color.TRANSPARENT);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.setTag(tag);
        w.setWebViewClient(new Cli());
        return w;
    }

    private void criarPainel() {
        painel = novoWeb("painel");
        painel.addJavascriptInterface(new Ponte(), "FFOverlay");
        int w = Math.min(dp(370), (int) (sw * 0.97f));
        int h = Math.min(dp(430), (int) (sh * 0.82f));
        pLp = base(w, h);
        pLp.x = Math.max(4, (sw - w) / 2);
        pLp.y = Math.max(4, (int) (sh * 0.09f));
        pLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        painel.setVisibility(View.GONE);
        painel.loadUrl(U_PANEL);
        try { wm.addView(painel, pLp); } catch (Throwable t) { }
    }

    private void criarPareamento() {
        pareamento = novoWeb("pareamento");
        pareamento.addJavascriptInterface(new Ponte(), "FFOverlay");
        int w = Math.min(dp(340), (int) (sw * 0.95f));
        int h = Math.min(dp(400), (int) (sh * 0.72f));
        aLp = base(w, h);
        aLp.x = Math.max(4, (sw - w) / 2);
        aLp.y = Math.max(4, (int) (sh * 0.12f));
        aLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        pareamento.setVisibility(View.GONE);
        pareamento.loadUrl(U_PAIR);
        try { wm.addView(pareamento, aLp); } catch (Throwable t) { }
    }

    /* ================== abrir / fechar ================== */
    public void abrirPainel() { abrirPainel(0); }

    private void abrirPainel(final int tent) {
        if (!podeSobrepor(this)) {
            if (tent < 40) ui.postDelayed(new Runnable() { public void run() { abrirPainel(tent + 1); } }, 400);
            return;
        }
        montar();
        if (painel == null) return;
        painelAberto = true;
        painel.setVisibility(View.VISIBLE);
        try { wm.updateViewLayout(painel, pLp); } catch (Throwable t) { }
        try { painel.requestFocus(); } catch (Throwable t) { }
        js(painel, "try{window.dh4xOnOpen&&window.dh4xOnOpen()}catch(e){}");
    }

    public void fecharPainel() {
        if (painel == null) return;
        painelAberto = false;
        painel.setVisibility(View.GONE);
    }

    private void alternarPainel() { if (painelAberto) fecharPainel(); else abrirPainel(); }

    public void abrirPareamento() { abrirPareamento(0); }

    private void abrirPareamento(final int tent) {
        if (!podeSobrepor(this)) {
            if (tent < 40) ui.postDelayed(new Runnable() { public void run() { abrirPareamento(tent + 1); } }, 400);
            return;
        }
        montar();
        if (pareamento == null) return;
        pairAberto = true;
        pareamento.setVisibility(View.VISIBLE);
        try { wm.updateViewLayout(pareamento, aLp); } catch (Throwable t) { }
        try { pareamento.requestFocus(); } catch (Throwable t) { }
        js(pareamento, "try{window.dh4xOnOpen&&window.dh4xOnOpen()}catch(e){}");
    }

    public void fecharPareamento() {
        if (pareamento == null) return;
        pairAberto = false;
        pareamento.setVisibility(View.GONE);
    }

    public void esconderTudo() {
        fecharPainel();
        fecharPareamento();
        if (circle != null) circle.setVisibility(View.GONE);
        if (fov != null) fov.setVisibility(View.GONE);
    }

    /* ================== injecao sincronizada ================== */
    private void iniciarInjecaoInterno(String pkg) {
        montar();
        if (pkg != null && pkg.length() > 0) syncPkg = pkg;
        syncEstado = SYNC_ABRINDO;
        syncInicio = System.currentTimeMillis();
        syncEstavel = 0;
        syncViuJogo = false;
        syncAtivo = true;

        fecharPainel();
        fecharPareamento();
        if (circle != null) circle.setVisibility(View.GONE);
        if (fov != null) fov.setVisibility(View.GONE);

        abrirApp(syncPkg);
        avisarPainel(SYNC_ABRINDO);
        ui.postDelayed(passoSync, 1500);
    }

    private void abrirApp(String pkg) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage("com.dts.freefiremax");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Throwable t) { }
    }

    private final Runnable passoSync = new Runnable() {
        @Override public void run() {
            if (!syncAtivo) return;
            if (syncEstado == SYNC_PRONTO) { syncAtivo = false; return; }
            final long decorrido = System.currentTimeMillis() - syncInicio;
            new Thread(new Runnable() { public void run() {
                final String f = focoAtual();
                ui.post(new Runnable() { public void run() { avaliarSync(f, decorrido); } });
            }}).start();
        }
    };

    private void avaliarSync(String foco, long decorrido) {
        if (!syncAtivo) return;
        String f = foco == null ? "" : foco;

        boolean semFoco = (f.length() == 0);
        boolean noJogo = f.contains("com.dts.freefire");
        boolean splash = f.contains("splash") || f.contains("loading") || f.contains("unity");

        if (noJogo) syncViuJogo = true;

        if (noJogo && !splash && !semFoco) syncEstavel++;
        else syncEstavel = 0;

        if (syncEstado != SYNC_CARREGANDO) {
            syncEstado = SYNC_CARREGANDO;
            avisarPainel(SYNC_CARREGANDO);
        }

        boolean pronto = (syncViuJogo && syncEstavel >= 3 && decorrido > 4000)
                || (decorrido > 90000);

        if (pronto) { concluirSync(); return; }
        if (syncAtivo) ui.postDelayed(passoSync, 550);
    }

    private void concluirSync() {
        syncEstado = SYNC_PRONTO;
        syncAtivo = false;
        marcarReady();
        if (circle != null) circle.setVisibility(View.VISIBLE);
        if (fov != null && fovOn) setFov(true, fovRaio);
        toast("Injetado! Toque no circulo DH");
        avisarPainel(SYNC_PRONTO);
    }

    private void avisarPainel(int e) {
        js(painel, "try{window.dh4xOnSync&&window.dh4xOnSync(" + e + ")}catch(x){}");
    }

    private void marcarReady() {
        String[] dirs = { "/data/local/tmp/dh4x", "/sdcard/Android/data/com.dh4x.menu/files" };
        for (String d : dirs) {
            try {
                File f = new File(d);
                if (!f.exists()) f.mkdirs();
                FileWriter w = new FileWriter(new File(f, "ready"));
                w.write(String.valueOf(System.currentTimeMillis()));
                w.close();
            } catch (Throwable t) { }
        }
    }

    private static String focoAtual() {
        String out = exec("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'");
        if (out == null || out.length() == 0) {
            out = exec("dumpsys activity activities | grep -E 'ResumedActivity|topResumedActivity'");
        }
        if (out == null) return "";
        return out.toLowerCase();
    }

    private static String exec(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] { "sh", "-c", cmd });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            int n = 0;
            while ((l = r.readLine()) != null && n++ < 40) sb.append(l).append('\n');
            r.close();
            p.waitFor();
            return sb.toString();
        } catch (Throwable t) {
            return "";
        } finally {
            if (p != null) { try { p.destroy(); } catch (Throwable t) { } }
        }
    }

    /* ================== estabilidade do WebView ================== */
    private class Cli extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
        @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return true; }
        @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) { }
        @Override public void onReceivedError(WebView v, int c, String d, String u) { }
        @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail dt) {
            final String tag = String.valueOf(v.getTag());
            ui.post(new Runnable() { public void run() { recriar(tag); } });
            return true;
        }
    }

    private void recriar(String tag) {
        try {
            if ("painel".equals(tag)) {
                if (painel != null) {
                    try { wm.removeView(painel); } catch (Throwable t) { }
                    try { painel.destroy(); } catch (Throwable t) { }
                    painel = null;
                }
                criarPainel();
                if (painel != null) painel.setVisibility(painelAberto ? View.VISIBLE : View.GONE);
            } else {
                if (pareamento != null) {
                    try { wm.removeView(pareamento); } catch (Throwable t) { }
                    try { pareamento.destroy(); } catch (Throwable t) { }
                    pareamento = null;
                }
                criarPareamento();
                if (pareamento != null) pareamento.setVisibility(pairAberto ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable t) { }
    }

    private void js(final WebView w, final String code) {
        if (w == null) return;
        ui.post(new Runnable() { public void run() {
            try { w.evaluateJavascript(code, null); } catch (Throwable t) { }
        }});
    }

    /* ================== rotacao / split-screen ================== */
    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        ui.post(new Runnable() { public void run() {
            medir();
            try {
                if (circle != null && cLp != null) {
                    cLp.x = lim(cLp.x, 0, Math.max(0, sw - cLp.width));
                    cLp.y = lim(cLp.y, 0, Math.max(0, sh - cLp.height));
                    wm.updateViewLayout(circle, cLp);
                }
            } catch (Throwable t) { }
            try {
                if (painel != null && pLp != null) {
                    pLp.width = Math.min(dp(370), (int) (sw * 0.97f));
                    pLp.height = Math.min(dp(430), (int) (sh * 0.82f));
                    pLp.x = Math.max(4, (sw - pLp.width) / 2);
                    pLp.y = Math.max(4, (int) (sh * 0.09f));
                    wm.updateViewLayout(painel, pLp);
                }
            } catch (Throwable t) { }
            try {
                if (pareamento != null && aLp != null) {
                    aLp.width = Math.min(dp(340), (int) (sw * 0.95f));
                    aLp.height = Math.min(dp(400), (int) (sh * 0.72f));
                    aLp.x = Math.max(4, (sw - aLp.width) / 2);
                    aLp.y = Math.max(4, (int) (sh * 0.12f));
                    wm.updateViewLayout(pareamento, aLp);
                }
            } catch (Throwable t) { }
            js(painel, "try{window.dh4xOnResize&&window.dh4xOnResize()}catch(e){}");
            js(pareamento, "try{window.dh4xOnResize&&window.dh4xOnResize()}catch(e){}");
        }});
    }

    /* ================== notificacao ================== */
    private Notification notif() {
        String ch = "dh4x";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(ch) == null) {
                NotificationChannel c = new NotificationChannel(ch, "DH4X MENU",
                        NotificationManager.IMPORTANCE_MIN);
                c.setShowBadge(false);
                nm.createNotificationChannel(c);
            }
        }
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, fl);

        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, ch)
                : new Notification.Builder(this);
        b.setContentTitle("DH4X MENU ativo")
         .setContentText("Toque no circulo DH para abrir o painel")
         .setSmallIcon(android.R.drawable.ic_menu_compass)
         .setOngoing(true)
         .setContentIntent(pi);
        return b.build();
    }

    private void toast(final String t) {
        ui.post(new Runnable() { public void run() {
            try { Toast.makeText(OverlayService.this, t, Toast.LENGTH_SHORT).show(); } catch (Throwable e) { }
        }});
    }

    /* ================== ciclo de vida ================== */
    @Override
    public void onDestroy() {
        syncAtivo = false;
        try { if (circle != null) wm.removeView(circle); } catch (Throwable t) { }
        try { if (fov != null) wm.removeView(fov); } catch (Throwable t) { }
        try { if (painel != null) { wm.removeView(painel); painel.destroy(); } } catch (Throwable t) { }
        try { if (pareamento != null) { wm.removeView(pareamento); pareamento.destroy(); } } catch (Throwable t) { }
        circle = null; fov = null; painel = null; pareamento = null;
        montado = false;
        INST = null;
        super.onDestroy();
    }

    /* ================== ponte JS do overlay ================== */
    public class Ponte {

        @JavascriptInterface public void arrastarInicioPainel() {
            ui.post(new Runnable() { public void run() { if (pLp != null) { pDX = pLp.x; pDY = pLp.y; } } });
        }

        @JavascriptInterface public void arrastarPainel(final float dx, final float dy) {
            ui.post(new Runnable() { public void run() {
                if (painel == null || pLp == null) return;
                pLp.x = lim(pDX + (int) dx, 0, Math.max(0, sw - pLp.width));
                pLp.y = lim(pDY + (int) dy, 0, Math.max(0, sh - pLp.height));
                try { wm.updateViewLayout(painel, pLp); } catch (Throwable t) { }
            }});
        }

        @JavascriptInterface public void arrastarInicioPareamento() {
            ui.post(new Runnable() { public void run() { if (aLp != null) { aDX = aLp.x; aDY = aLp.y; } } });
        }

        @JavascriptInterface public void arrastarPareamento(final float dx, final float dy) {
            ui.post(new Runnable() { public void run() {
                if (pareamento == null || aLp == null) return;
                aLp.x = lim(aDX + (int) dx, 0, Math.max(0, sw - aLp.width));
                aLp.y = lim(aDY + (int) dy, 0, Math.max(0, sh - aLp.height));
                try { wm.updateViewLayout(pareamento, aLp); } catch (Throwable t) { }
            }});
        }

        @JavascriptInterface public void fecharPainel() {
            ui.post(new Runnable() { public void run() { OverlayService.this.fecharPainel(); } });
        }
        @JavascriptInterface public void minimizar() {
            ui.post(new Runnable() { public void run() { OverlayService.this.fecharPainel(); } });
        }
        @JavascriptInterface public void abrirPainel() {
            ui.post(new Runnable() { public void run() { OverlayService.this.abrirPainel(); } });
        }
        @JavascriptInterface public void fecharPareamento() {
            ui.post(new Runnable() { public void run() { OverlayService.this.fecharPareamento(); } });
        }
        @JavascriptInterface public void fecharTudo() {
            ui.post(new Runnable() { public void run() { esconderTudo(); } });
        }

        @JavascriptInterface public void sucesso() {
            ui.post(new Runnable() { public void run() {
                toast("Depuracao Wi-Fi conectada!");
                ui.postDelayed(new Runnable() { public void run() { fecharPareamento(); } }, 1300);
            }});
        }
        @JavascriptInterface public void falha() {
            ui.post(new Runnable() { public void run() { toast("Codigo invalido, tente de novo"); } });
        }

        @JavascriptInterface public String colar() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                        && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(OverlayService.this);
                    if (s != null) return s.toString();
                }
            } catch (Throwable t) { }
            return "";
        }

        @JavascriptInterface public void copiar(final String t) {
            ui.post(new Runnable() { public void run() {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("dh4x", t));
                } catch (Throwable e) { }
            }});
        }

        @JavascriptInterface public void abrirDevOptions() {
            ui.post(new Runnable() { public void run() {
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

        @JavascriptInterface public void abrirFreeFire() {
            ui.post(new Runnable() { public void run() { abrirApp(syncPkg); } });
        }

        @JavascriptInterface public void injetar() {
            ui.post(new Runnable() { public void run() { iniciarInjecaoInterno(syncPkg); } });
        }

        @JavascriptInterface public int syncEstado() {
            return OverlayService.this.syncEstado;
        }

        @JavascriptInterface public void abrirMain() {
            ui.post(new Runnable() { public void run() {
                try {
                    Intent i = new Intent(OverlayService.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) { }
            }});
        }

        @JavascriptInterface public void abrirIntent(final String u) {
            ui.post(new Runnable() { public void run() {
                try {
                    Intent i = Intent.parseUri(u, Intent.URI_INTENT_SCHEME);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable e) { }
                }
            }});
        }

        @JavascriptInterface public void bypass() {
            ui.post(new Runnable() { public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) { }
            }});
        }

        @JavascriptInterface public void setFov(final boolean on, final int r) {
            ui.post(new Runnable() { public void run() { OverlayService.this.setFov(on, r); } });
        }

        @JavascriptInterface public void toast(final String t) {
            ui.post(new Runnable() { public void run() { OverlayService.this.toast(t); } });
        }

        @JavascriptInterface public String estado() {
            return (painelAberto ? "painel:1" : "painel:0") + "|"
                 + (pairAberto ? "pareamento:1" : "pareamento:0") + "|sync:" + syncEstado;
        }
    }
}