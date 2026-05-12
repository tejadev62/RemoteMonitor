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

    private var currentViewerId: String? = null

    private fun setupSocket() {
        val serverUrl = getString(R.string.signaling_server_url)
        socket = IO.socket(serverUrl)
        socket.connect()

        socket.on(Socket.EVENT_CONNECT) {
            socket.emit("join-room", roomId, "streamer")
        }

        socket.on("viewer-joined") { args ->
            currentViewerId = args[0] as String
            webRtcManager.createPeerConnection()
            webRtcManager.createOffer() // Initiate the offer from the phone
        }

        socket.on("answer") { args ->
            val data = args[0] as JSONObject
            val sdp = data.getString("answer")
            webRtcManager.handleAnswer(sdp)
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

    override fun onIceCandidate(candidate: IceCandidate) {
        val data = JSONObject()
        data.put("candidate", candidate.sdp)
        data.put("sdpMid", candidate.sdpMid)
        data.put("sdpMLineIndex", candidate.sdpMLineIndex)
        data.put("target", currentViewerId)
        data.put("roomId", roomId)
        socket.emit("ice-candidate", data)
    }

    override fun onLocalDescription(sdp: SessionDescription) {
        val data = JSONObject()
        data.put("offer", sdp.description)
        data.put("target", currentViewerId)
        data.put("roomId", roomId)
        socket.emit("offer", data)
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

    private fun createNotification(): android.app.Notification {
        val channelId = "screen_sharing_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Screen Sharing", android.app.NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Sharing")
            .setContentText("Your screen is being shared")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
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
