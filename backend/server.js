const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());

const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

const PORT = process.env.PORT || 3000;

// Store rooms and their participants
// room: { streamer: socketId, viewers: [socketId] }
const rooms = {};

io.on('connection', (socket) => {
    console.log('User connected:', socket.id);

    socket.on('join-room', (roomId, role) => {
        socket.join(roomId);
        console.log(`User ${socket.id} joined room ${roomId} as ${role}`);

        if (!rooms[roomId]) {
            rooms[roomId] = { streamer: null, viewers: [] };
        }

        if (role === 'streamer') {
            rooms[roomId].streamer = socket.id;
            socket.to(roomId).emit('streamer-joined', socket.id);
        } else {
            rooms[roomId].viewers.push(socket.id);
            if (rooms[roomId].streamer) {
                // Notify the streamer that a new viewer joined
                socket.to(rooms[roomId].streamer).emit('viewer-joined', socket.id);
            }
        }
    });

    // WebRTC Signaling
    socket.on('offer', (data) => {
        // data: { offer, target, roomId }
        socket.to(data.target).emit('offer', {
            offer: data.offer,
            sender: socket.id
        });
    });

    socket.on('answer', (data) => {
        // data: { answer, target, roomId }
        socket.to(data.target).emit('answer', {
            answer: data.answer,
            sender: socket.id
        });
    });

    socket.on('ice-candidate', (data) => {
        // data: { candidate, target, roomId }
        socket.to(data.target).emit('ice-candidate', {
            candidate: data.candidate,
            sender: socket.id
        });
    });

    // Status Updates
    socket.on('current-app', (data) => {
        // data: { appName, roomId }
        socket.to(data.roomId).emit('app-changed', data.appName);
    });

    socket.on('disconnect', () => {
        console.log('User disconnected:', socket.id);
        // Clean up rooms
        for (const roomId in rooms) {
            if (rooms[roomId].streamer === socket.id) {
                rooms[roomId].streamer = null;
                socket.to(roomId).emit('streamer-left');
            } else {
                rooms[roomId].viewers = rooms[roomId].viewers.filter(id => id !== socket.id);
            }
        }
    });
});

server.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});
