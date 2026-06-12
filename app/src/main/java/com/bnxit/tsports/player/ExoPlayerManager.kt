package com.bnxit.tsports.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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

    private lateinit var dataSourceFactory: DefaultHttpDataSource.Factory
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var wasBuffering = false
    private var isSeekingToLive = false

    fun initializePlayer(): ExoPlayer {
        dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "ExoPlayer/Android TV IPTV",
                    "Accept" to "*/*"
                )
            )

        val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(hlsMediaSourceFactory)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val p = player ?: return
                if (playbackState == Player.STATE_BUFFERING) {
                    if (!isSeekingToLive) {
                        wasBuffering = true
                    }
                } else if (playbackState == Player.STATE_READY) {
                    if (wasBuffering && p.isCurrentMediaItemLive) {
                        wasBuffering = false
                        val offset = p.currentLiveOffset
                        if (offset != C.TIME_UNSET && offset > 25000) {
                            isSeekingToLive = true
                            p.seekToDefaultPosition()
                        }
                    } else {
                        isSeekingToLive = false
                    }
                }
            }
        })

        return player!!
    }

    fun playStream(urlStr: String) {
        if (urlStr.contains("player.php")) {
            coroutineScope.launch {
                val resolvedData = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL(urlStr).openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 8000
                        connection.readTimeout = 8000
                        
                        var resolvedUrl = urlStr
                        var fetchedCookie: String? = null
                        
                        // Extract cookie for session persistence if backend sends it
                        val cookieHeader = connection.getHeaderField("Set-Cookie")
                        if (cookieHeader != null) {
                            fetchedCookie = cookieHeader.split(";")[0]
                        }
                        
                        // Extract the actual m3u8 stream from the HTML source
                        val html = connection.inputStream.bufferedReader().use { it.readText() }
                        val pattern = Pattern.compile("var\\s+primarySource\\s*=\\s*['\"]([^'\"]+)['\"]")
                        val matcher = pattern.matcher(html)
                        if (matcher.find()) {
                            resolvedUrl = matcher.group(1) ?: urlStr
                        }
                        Pair(resolvedUrl, fetchedCookie)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Pair(urlStr, null)
                    }
                }
                
                startPlayback(resolvedData.first, resolvedData.second)
            }
        } else {
            startPlayback(urlStr, null)
        }
    }

    private fun startPlayback(url: String, newCookie: String?) {
        val properties = mutableMapOf(
            "User-Agent" to "ExoPlayer/Android TV IPTV",
            "Accept" to "*/*"
        )
        if (newCookie != null) {
            properties["Cookie"] = newCookie
        }
        dataSourceFactory.setDefaultRequestProperties(properties)

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(10000)   // Target offset from live edge: 10 seconds (extremely stable buffer)
                    .setMinOffsetMs(5000)       // Minimum offset: 5 seconds (helps prevent stutters)
                    .setMaxOffsetMs(25000)      // Maximum offset before forcing catchup: 25 seconds
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
