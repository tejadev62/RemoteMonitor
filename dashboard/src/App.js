import React, { useEffect, useRef, useState } from 'react';
import io from 'socket.io-client';

const SIGNALING_SERVER = process.env.REACT_APP_SIGNALING_SERVER || "http://localhost:3000";
const ROOM_ID = "personal-room";

function App() {
  const [currentApp, setCurrentApp] = useState("Unknown");
  const [status, setStatus] = useState("Disconnected");
  const remoteVideoRef = useRef(null);
  const peerConnection = useRef(null);
  const socket = useRef(null);

  useEffect(() => {
    socket.current = io(SIGNALING_SERVER);

    socket.current.on('connect', () => {
      setStatus("Connected to Signaling Server");
      socket.current.emit('join-room', ROOM_ID, 'viewer');
    });

    socket.current.on('streamer-joined', (streamerId) => {
      setStatus("Streamer Online");
    });

    socket.current.on('offer', async (data) => {
      console.log("Received offer from", data.sender);
      createPeerConnection(data.sender);
      await peerConnection.current.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: data.offer }));
      const answer = await peerConnection.current.createAnswer();
      await peerConnection.current.setLocalDescription(answer);
      socket.current.emit('answer', {
        answer: answer.sdp,
        target: data.sender,
        roomId: ROOM_ID
      });
    });

    socket.current.on('ice-candidate', (data) => {
      console.log("Received ICE candidate");
      if (peerConnection.current) {
        peerConnection.current.addIceCandidate(new RTCIceCandidate(data.candidate));
      }
    });

    socket.current.on('app-changed', (appName) => {
      setCurrentApp(appName);
    });

    socket.current.on('streamer-left', () => {
      setStatus("Streamer Offline");
      if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null;
    });

    return () => {
      socket.current.disconnect();
    };
  }, []);

  const createPeerConnection = (streamerId) => {
    peerConnection.current = new RTCPeerConnection({
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun2.l.google.com:19302' },
        {
          urls: "turn:relay.metered.ca:80",
          username: "da0987bdebdb3e25b741ccbc",
          credential: "KO31IJzS23HDgx2C"
        },
        {
          urls: "turn:relay.metered.ca:443",
          username: "da0987bdebdb3e25b741ccbc",
          credential: "KO31IJzS23HDgx2C"
        },
        {
          urls: "turn:relay.metered.ca:443?transport=tcp",
          username: "da0987bdebdb3e25b741ccbc",
          credential: "KO31IJzS23HDgx2C"
        }
      ]
    });

    peerConnection.current.onicecandidate = (event) => {
      if (event.candidate) {
        console.log("Sending ICE candidate to", streamerId);
        socket.current.emit('ice-candidate', {
          candidate: event.candidate,
          target: streamerId,
          roomId: ROOM_ID
        });
      }
    };

    peerConnection.current.onconnectionstatechange = (event) => {
      console.log("Connection state change:", peerConnection.current.connectionState);
      setStatus("Connection: " + peerConnection.current.connectionState);
    };

    peerConnection.current.oniceconnectionstatechange = (event) => {
      console.log("ICE connection state change:", peerConnection.current.iceConnectionState);
    };

    peerConnection.current.ontrack = (event) => {
      console.log("Received remote track");
      if (remoteVideoRef.current) {
        remoteVideoRef.current.srcObject = event.streams[0];
      }
    };
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h1>Remote Monitor Dashboard</h1>
      <div style={{ marginBottom: '10px' }}>
        <strong>Status:</strong> {status}
      </div>
      <div style={{ marginBottom: '20px' }}>
        <strong>Current App:</strong> {currentApp}
      </div>
      <div style={{ background: '#000', width: '100%', maxWidth: '800px', aspectRatio: '16/9', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <video
          ref={remoteVideoRef}
          autoPlay
          playsInline
          controls
          style={{ width: '100%', maxHeight: '100%' }}
        />
      </div>
    </div>
  );
}

export default App;
