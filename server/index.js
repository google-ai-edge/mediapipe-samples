const express = require('express');
const http = require('http');
const { Server } = require("socket.io");
const path = require('path');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*", // Allow all origins for now
    methods: ["GET", "POST"]
  }
});

// Serve static files from the "client" directory
app.use(express.static(path.join(__dirname, '..', 'client')));

// Middleware for password verification
io.use((socket, next) => {
    const password = socket.handshake.auth.password;
    if (password === "password123") { // Hardcoded password for now
        return next();
    }
    return next(new Error("invalid password"));
});

io.on('connection', (socket) => {
    console.log('a user connected');

    // Handle chat messages
    socket.on('chat message', (msg) => {
        io.emit('chat message', msg); // Broadcast the message to all clients
    });

    socket.on('disconnect', () => {
        console.log('user disconnected');
    });
});

const PORT = process.env.PORT || 3000;

server.listen(PORT, () => {
  console.log(`listening on *:${PORT}`);
});