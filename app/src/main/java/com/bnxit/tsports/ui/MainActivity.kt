package com.bnxit.tsports.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bnxit.tsports.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val streamUrl = "http://10.11.12.13/player.php?stream=1"

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()

        binding.btnDeveloperInfo.setOnClickListener {
            val panel = binding.developerInfoPanel
            panel.visibility =
                if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.webView.loadUrl(streamUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView

        // GPU rendering — critical for slow CPUs
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode             = true
            useWideViewPort                  = true
            cacheMode                        = WebSettings.LOAD_NO_CACHE
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            setSupportZoom(false)
            builtInZoomControls  = false
            displayZoomControls  = false
            allowContentAccess   = false
            allowFileAccess      = false
            setGeolocationEnabled(false)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                customViewCallback = callback
                binding.root.addView(view)
                webView.visibility = View.GONE
            }
            override fun onHideCustomView() {
                binding.root.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean = false

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(INJECT_SCRIPT, null)
            }
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        binding.webView.pauseTimers()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.stopLoading()
        binding.webView.destroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (binding.developerInfoPanel.visibility == View.VISIBLE) {
                binding.developerInfoPanel.visibility = View.GONE
                return true
            }
            return true // prevent exiting the app on back press
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        /**
         * Injected after every page load:
         *
         * 1. Black background, zero margins, no scroll — clean full-screen layout.
         * 2. Hide ALL native video controls and common player control bars
         *    (JW Player, Video.js, Plyr, etc.) so no UI chrome appears over the video.
         * 3. Seek to the live edge as soon as metadata is available —
         *    this fixes the "late stream" issue where the player starts from the
         *    beginning of the HLS playlist window instead of the live edge.
         */
        private val INJECT_SCRIPT = """
            (function() {
                /* ── 1. Page layout ───────────────────────── */
                var s = document.createElement('style');
                s.textContent = [
                    'html,body{margin:0;padding:0;background:#000;overflow:hidden;width:100%;height:100%}',

                    /* Native HTML5 controls (WebKit shadow DOM) */
                    'video::-webkit-media-controls{display:none!important}',
                    'video::-webkit-media-controls-enclosure{display:none!important}',
                    'video::-webkit-media-controls-panel{display:none!important}',
                    'video::-webkit-media-controls-play-button{display:none!important}',
                    'video::-webkit-media-controls-timeline{display:none!important}',
                    'video::-webkit-media-controls-current-time-display{display:none!important}',
                    'video::-webkit-media-controls-time-remaining-display{display:none!important}',
                    'video::-webkit-media-controls-mute-button{display:none!important}',
                    'video::-webkit-media-controls-volume-slider{display:none!important}',
                    'video::-webkit-media-controls-fullscreen-button{display:none!important}',

                    /* Common JS player control bars */
                    '.jw-controls,.jw-controlbar,.jw-overlays,',
                    '.vjs-control-bar,.vjs-big-play-button,.vjs-loading-spinner,',
                    '.plyr__controls,.plyr__progress,',
                    '.fp-controls,.fp-ui,',
                    '.video-controls,.player-controls,',
                    '[class*="control-bar"],[class*="controlbar"],',
                    '[class*="player-ui"],[class*="playerui"]{display:none!important}',

                    /* Video fills screen */
                    'video{position:fixed;top:0;left:0;width:100%!important;height:100%!important;',
                    'object-fit:contain;background:#000;z-index:0}'
                ].join('');
                document.head.appendChild(s);

                /* ── 2. Disable native controls attribute ─── */
                function removeControls(v) {
                    v.removeAttribute('controls');
                    v.controls = false;
                }

                /* ── 3. Seek to live edge ──────────────────── */
                function seekToLive(v) {
                    try {
                        var seekable = v.seekable;
                        if (seekable && seekable.length > 0) {
                            var liveEdge = seekable.end(seekable.length - 1);
                            if (isFinite(liveEdge) && liveEdge > 0) {
                                /* Stay 1 second behind the very tip to avoid stutter */
                                v.currentTime = Math.max(0, liveEdge - 1);
                            }
                        }
                    } catch(e) {}
                }

                /* ── 4. Watch for video element ───────────── */
                function initVideo(v) {
                    removeControls(v);
                    /* Unmute immediately — autoplay policy may have started it muted */
                    v.muted  = false;
                    v.volume = 1.0;
                    if (v.readyState >= 1) {
                        seekToLive(v);
                        v.play().catch(function(){});
                    } else {
                        v.addEventListener('loadedmetadata', function() {
                            v.muted  = false;
                            v.volume = 1.0;
                            seekToLive(v);
                            v.play().catch(function(){});
                        }, { once: true });
                    }
                }

                var existing = document.querySelector('video');
                if (existing) {
                    initVideo(existing);
                } else {
                    /* Poll until the JS player creates its video element */
                    var tries = 0;
                    var poll = setInterval(function() {
                        var v = document.querySelector('video');
                        if (v) { clearInterval(poll); initVideo(v); }
                        if (++tries > 20) clearInterval(poll);
                    }, 500);
                }
            })();
        """.trimIndent()
    }
}
