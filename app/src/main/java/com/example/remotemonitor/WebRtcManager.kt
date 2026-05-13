package com.example.remotemonitor

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcManager(
    private val context: Context,
    private val listener: WebRtcListener
) {

    interface WebRtcListener {
        fun onIceCandidate(candidate: IceCandidate)
        fun onLocalDescription(sdp: SessionDescription)
    }

    private val eglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val audioDeviceModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startStreaming(mediaProjectionIntent: Intent) {
        videoCapturer = ScreenCapturerAndroid(mediaProjectionIntent, object : MediaProjection.Callback() {})
        
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer!!.initialize(SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext), context, videoSource.capturerObserver)
        videoCapturer!!.startCapture(1280, 720, 30)

        videoTrack = peerConnectionFactory.createVideoTrack("VIDEO_TRACK_ID", videoSource)
        
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack("AUDIO_TRACK_ID", audioSource)
    }

    fun createPeerConnection() {
        val iceServers = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:relay.metered.ca:80")
                .setUsername("da0987bdebdb3e25b741ccbc")
                .setPassword("KO31IJzS23HDgx2C")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:relay.metered.ca:443")
                .setUsername("da0987bdebdb3e25b741ccbc")
                .setPassword("KO31IJzS23HDgx2C")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:relay.metered.ca:443?transport=tcp")
                .setUsername("da0987bdebdb3e25b741ccbc")
                .setPassword("KO31IJzS23HDgx2C")
                .createIceServer(),
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        // Set SDP semantics to Unified Plan for better compatibility
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                android.util.Log.d("WebRtcManager", "Local ICE candidate: ${candidate.sdp}")
                listener.onIceCandidate(candidate)
            }

            override fun onDataChannel(p0: DataChannel?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        peerConnection?.addTrack(videoTrack)
        peerConnection?.addTrack(audioTrack)
    }

    fun handleAnswer(sdp: String) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun createOffer() {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        listener.onLocalDescription(sdp)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun stop() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        peerConnection?.dispose()
        peerConnectionFactory.dispose()
        eglBase.release()
    }
}
