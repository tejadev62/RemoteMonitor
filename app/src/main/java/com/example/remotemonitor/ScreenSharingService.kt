package com.example.remotemonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.*

class ScreenSharingService : LifecycleService(), WebRtcManager.WebRtcListener {

    private lateinit var socket: Socket
    private lateinit var webRtcManager: WebRtcManager
    private lateinit var appTracker: AppTracker
    private var roomId = "personal-room"
    private var timer: Timer? = null

    override fun onCreate() {
        super.onCreate()
        webRtcManager = WebRtcManager(this, this)
        appTracker = AppTracker(this)
        startForeground(1, createNotification())
        setupSocket()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val projectionIntent = intent?.getParcelableExtra<Intent>("PROJECTION_INTENT")
        if (projectionIntent != null) {
            webRtcManager.startStreaming(projectionIntent)
            startAppTracking()
        }
        return START_STICKY
    }

    private fun setupSocket() {
        val serverUrl = getString(R.string.signaling_server_url)
        socket = IO.socket(serverUrl)
        socket.connect()

        socket.on(Socket.EVENT_CONNECT) {
            socket.emit("join-room", roomId, "streamer")
        }

        socket.on("viewer-joined") { args ->
            val viewerId = args[0] as String
            webRtcManager.createPeerConnection()
        }

        socket.on("answer") { args ->
            val data = args[0] as JSONObject
            val sdp = data.getString("answer")
            webRtcManager.handleOffer(sdp) // Note: In streamer role, we handle answer or offer depending on flow. 
            // Simplified: here we assume the dashboard sends back an answer.
        }
        
        socket.on("offer") { args ->
            val data = args[0] as JSONObject
            val sdp = data.getString("offer")
            webRtcManager.handleOffer(sdp)
        }

        socket.on("ice-candidate") { args ->
            val data = args[0] as JSONObject
            val candidate = IceCandidate(
                data.getString("sdpMid"),
                data.getInt("sdpMLineIndex"),
                data.getString("candidate")
            )
            webRtcManager.addIceCandidate(candidate)
        }
    }

    private fun startAppTracking() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val currentApp = appTracker.getCurrentApp()
                val data = JSONObject()
                data.put("appName", currentApp)
                data.put("roomId", roomId)
                socket.emit("current-app", data)
            }
        }, 0, 5000)
    }

    private fun createNotification(): Notification {
        val channelId = "screen_sharing_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Screen Sharing", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Sharing")
            .setContentText("Your screen is being shared")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        val data = JSONObject()
        data.put("candidate", candidate.sdp)
        data.put("sdpMid", candidate.sdpMid)
        data.put("sdpMLineIndex", candidate.sdpMLineIndex)
        data.put("target", "VIEWER_ID") // Need to track viewer IDs properly in a real app
        data.put("roomId", roomId)
        socket.emit("ice-candidate", data)
    }

    override fun onLocalDescription(sdp: SessionDescription) {
        val data = JSONObject()
        data.put("offer", sdp.description)
        data.put("target", "VIEWER_ID")
        data.put("roomId", roomId)
        socket.emit("offer", data)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        webRtcManager.stop()
        socket.disconnect()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
