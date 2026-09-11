package com.zeoril.videoviewertv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView

/**
 * Главный экран: тонкая WebView-обёртка вокруг веб-интерфейса Video Viewer.
 *
 * Вся логика (каталог, плеер, авторизация, «продолжить просмотр» и т.д.)
 * живёт на сервере в web/ — приложение лишь открывает его с параметром tv=1.
 * Благодаря этому обновления интерфейса «прилетают» на телевизор автоматически,
 * без пересборки APK.
 */
@SuppressLint("SetJavaScriptEnabled")
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var errorBox: View
    private lateinit var errorText: TextView
    private var currentBase: String? = null
    private var backHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.webview)
        errorBox = findViewById(R.id.errorOverlay)
        errorText = findViewById(R.id.errorText)
        findViewById<Button>(R.id.btnRetry).setOnClickListener { loadCurrent() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettings() }

        setupWebView()
    }

    // ------------------------------------------------------------------ WebView

    private fun setupWebView() {
        web.setBackgroundColor(0xFF0A0E13.toInt())

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        // Плеер стартует по клику в интерфейсе; отключаем требование жеста,
        // чтобы «Продолжить просмотр» и смена дорожек не блокировались.
        s.mediaPlaybackRequiresUserGesture = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = false
        s.builtInZoomControls = false
        s.setSupportZoom(false)
        // Смешанный контент: http-страница может грузить https-постеры TMDB и т.п.
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Метка в UA — запасной способ включить TV-режим (основной: ?tv=1).
        // Пока userAgentString не переопределён, геттер возвращает дефолтный UA —
        // берём его как базу, чтобы просто дописать маркер.
        val base = s.userAgentString
        if (!base.contains("VVTV")) {
            s.userAgentString = "$base ${Prefs.TV_UA_MARKER}"
        }

        // Куки (httpOnly-сессия входа) сохраняются между запусками.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val u = url ?: return false
                val scheme = Uri.parse(u).scheme?.lowercase() ?: ""
                // http(s) остаются в WebView (весь сайт — один SPA).
                if (scheme == "http" || scheme == "https") return false
                // Прочие схемы (magnet:, intent: …) — наружу, если есть обработчик.
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                    true
                } catch (e: Exception) {
                    true
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    val code = error?.errorCode ?: -1
                    val desc = error?.description?.toString().orEmpty()
                    showError(
                        "Не удалось загрузить сервер\n($code: $desc)\n\n" +
                            "Проверьте адрес в настройках и доступность сервера " +
                            "в этой же сети."
                    )
                }
                super.onReceivedError(view, request, error)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // Логи JS можно смотреть через chrome://inspect.
        }

        // Remote-отладка (chrome://inspect) — удобно при разработке TV-режима.
        WebView.setWebContentsDebuggingEnabled(true)
    }

    // ------------------------------------------------------------------ Навигация

    private fun prefs() = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

    private fun storedBase(): String? =
        prefs().getString(Prefs.KEY_URL, null)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Приводим адрес к рабочему виду: схема http://, если не указана, и порт
     * 8080 — если не указан (сервер по умолчанию слушает именно его).
     * Порт не дописываем для https (там 443) и когда в адресе есть путь.
     */
    private fun normalizedBase(raw: String): String {
        var b = raw.trim().trimEnd('/')
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "http://$b"
        val uri = Uri.parse(b)
        if (uri.port == -1 && uri.scheme == "http" && uri.path.isNullOrEmpty()) b = "$b:8080"
        return b
    }

    /** Открываем веб-интерфейс в TV-режиме (?tv=1). */
    private fun tvUrl(base: String): String {
        val uri = Uri.parse(normalizedBase(base))
        return uri.buildUpon().appendQueryParameter("tv", "1").build().toString()
    }

    private fun loadCurrent() {
        val b = storedBase()
        if (b == null) {
            openSettings()
            return
        }
        currentBase = normalizedBase(b)
        web.loadUrl(tvUrl(currentBase!!))
    }

    override fun onResume() {
        super.onResume()
        val u = storedBase()
        if (u == null) {
            // Первый запуск / адрес не задан — на экран настроек.
            if (!isFinishing) openSettings()
        } else if (u != currentBase) {
            // Вернулись из настроек с новым адресом — перезагружаем.
            loadCurrent()
        }
    }

    private fun showError(text: String) {
        web.visibility = View.INVISIBLE
        errorText.text = text
        errorBox.visibility = View.VISIBLE
        errorBox.requestFocus()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ------------------------------------------------------- Медиа-клавиши

    /** Выполнить JS на странице (ошибки внутри скрипта глушатся try/catch). */
    private fun runJs(script: String) {
        web.evaluateJavascript(script, null)
    }

    /** play/pause по «кнопке» плеера, чтобы UI и иконка обновлялись. */
    private fun mediaPlayPause() {
        runJs(
            "(function(){var b=document.getElementById('ctrl-play');" +
                "if(b&&b.click)b.click();})()"
        )
    }

    private fun mediaSetPlaying(play: Boolean) {
        val body = if (play)
            "if(p.paused)p.play().catch(function(){});"
        else
            "if(!p.paused)p.pause();"
        runJs(
            "(function(){var p=document.getElementById('player');if(!p)return;$body})()"
        )
    }

    /** Перемотка ±delta секунд; использует родную seekBy() страницы, если есть. */
    private fun mediaSeek(delta: Int) {
        runJs(
            "(function(){try{" +
                "if(typeof window.seekBy==='function'){window.seekBy($delta);return;}" +
                "var p=document.getElementById('player');if(p){" +
                "var t=(p.currentTime||0)+($delta);" +
                "if(p.duration)t=Math.max(0,Math.min(p.duration,t));" +
                "p.currentTime=t;}" +
                "}catch(e){}})()"
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // MENU на пультах, где он есть, открывает настройки сервера.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings()
            return true
        }
        // Медиа-клавиши пульта (play/pause/ff/rw) → управление плеером на странице.
        // WebView сам не переводит их в <video>, поэтому пробрасываем в tv.js/app.js.
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { mediaPlayPause(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { mediaSetPlaying(true); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> { mediaSetPlaying(false); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { mediaSeek(30); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { mediaSeek(-30); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Спрашиваем страницу: она «съест» Back (закрыть плеер/модалку) или
        // приложению пора выходить? web/tv.js отвечает 'consumed'/'exit'.
        backHandled = false
        web.evaluateJavascript(
            "(function(){try{return (window.__vvBack)?window.__vvBack():'exit';}catch(e){return 'exit';}})()"
        ) { res ->
            val v = res?.trim()?.trim('"')?.trim()
            if (v != "consumed" && !backHandled) {
                backHandled = true
                super@MainActivity.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
