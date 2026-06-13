package com.bnxit.tsports.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class ExoPlayerManager(private val context: Context) {

    var player: ExoPlayer? = null
        private set

    private lateinit var httpDataSourceFactory: DefaultHttpDataSource.Factory
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var entryUrl: String? = null

    // Standard Chrome browser User-Agent
    private val browserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

    fun initializePlayer(): ExoPlayer {
        httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent"      to browserUserAgent,
                    "Accept"          to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Origin"          to "http://10.11.12.13",
                    "Referer"         to "http://10.11.12.13/"
                )
            )

        // DefaultDataSource wraps HTTP + local — required by DefaultMediaSourceFactory
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // ── KEY FIX ─────────────────────────────────────────────────────────────
        // We were using HlsMediaSource.Factory which ONLY understands HLS (.m3u8).
        // DefaultMediaSourceFactory auto-detects the format (HLS, DASH, MP4, TS…)
        // exactly like a browser — if the stream isn't pure HLS it won't stall.
        // ────────────────────────────────────────────────────────────────────────
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // No custom LoadControl — use ExoPlayer's default buffering strategy.
        // This is the most stable and battle-tested configuration.
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoPlayer", "Error [${error.errorCode}] ${error.errorCodeName}: ${error.message}")
                val url = entryUrl
                if (url != null) {
                    Log.d("ExoPlayer", "Retrying with fresh URL resolution...")
                    playStream(url)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE     -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY    -> "READY"
                    Player.STATE_ENDED    -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d("ExoPlayer", "State → $stateName")
            }
        })

        return player!!
    }

    fun playStream(urlStr: String) {
        entryUrl = urlStr

        if (urlStr.contains("player.php")) {
            coroutineScope.launch {
                val resolvedData = withContext(Dispatchers.IO) {
                    try {
                        Log.d("ExoPlayer", "Fetching player page: $urlStr")
                        val connection = URL(urlStr).openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("User-Agent", browserUserAgent)
                        connection.setRequestProperty("Referer",    "http://10.11.12.13/")
                        connection.connectTimeout = 8000
                        connection.readTimeout    = 8000
                        connection.instanceFollowRedirects = true

                        var fetchedCookie: String? = null
                        val cookieHeader = connection.getHeaderField("Set-Cookie")
                        if (cookieHeader != null) {
                            fetchedCookie = cookieHeader.split(";")[0]
                            Log.d("ExoPlayer", "Got cookie: $fetchedCookie")
                        }

                        val html = connection.inputStream.bufferedReader().use { it.readText() }
                        Log.d("ExoPlayer", "Player page HTML length: ${html.length}")

                        // Try multiple patterns the server might use
                        val patterns = listOf(
                            "var\\s+primarySource\\s*=\\s*['\"]([^'\"]+)['\"]",
                            "file\\s*:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]",
                            "source\\s*:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]",
                            "src\\s*=\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]",
                            "['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]"
                        )

                        var resolvedUrl = urlStr
                        for (pat in patterns) {
                            val matcher = Pattern.compile(pat).matcher(html)
                            if (matcher.find()) {
                                resolvedUrl = matcher.group(1) ?: continue
                                Log.d("ExoPlayer", "Pattern matched [$pat] → $resolvedUrl")
                                break
                            }
                        }

                        if (resolvedUrl == urlStr) {
                            Log.w("ExoPlayer", "No stream URL found in page HTML! Trying direct URL.")
                            Log.d("ExoPlayer", "HTML snippet: ${html.take(500)}")
                        }

                        Pair(resolvedUrl, fetchedCookie)
                    } catch (e: Exception) {
                        Log.e("ExoPlayer", "Failed to resolve stream URL", e)
                        Pair(urlStr, null)
                    }
                }
                startPlayback(resolvedData.first, resolvedData.second)
            }
        } else {
            startPlayback(urlStr, null)
        }
    }

    private fun startPlayback(url: String, cookie: String?) {
        Log.d("ExoPlayer", "Starting playback: $url")

        val properties = mutableMapOf(
            "User-Agent"      to browserUserAgent,
            "Accept"          to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Origin"          to "http://10.11.12.13",
            "Referer"         to "http://10.11.12.13/"
        )
        if (cookie != null) {
            properties["Cookie"] = cookie
        }
        httpDataSourceFactory.setDefaultRequestProperties(properties)

        // ── HOW BROWSERS PLAY LIVE HLS ──────────────────────────────────────────
        // Browser (hls.js) targets ~3 segment durations behind the live edge,
        // typically 15–20 seconds. This means it always has buffered content
        // ahead of playback, so network jitter never causes a freeze.
        //
        // seekToDefaultPosition() put us at 0s behind live = nothing buffered
        // ahead = every tiny hiccup caused a freeze.
        //
        // LiveConfiguration targets us 15s behind live, always with content
        // buffered ahead, matching browser behaviour exactly.
        // ────────────────────────────────────────────────────────────────────────
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(15_000)   // 15s behind live = browser default
                    .setMinOffsetMs(5_000)        // never closer than 5s to edge
                    .setMaxOffsetMs(30_000)       // never more than 30s behind
                    .build()
            )
            .build()
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    fun releasePlayer() {
        player?.release()
        player = null
    }
}
