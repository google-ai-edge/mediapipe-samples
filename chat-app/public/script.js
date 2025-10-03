document.addEventListener('DOMContentLoaded', () => {
    // --- App Login Elements ---
    const appLoginContainer = document.getElementById('app-login-container');
    const mainAppContainer = document.getElementById('main-app-container');
    const appPasswordInput = document.getElementById('app-password');
    const appLoginButton = document.getElementById('app-login-button');
    const appLoginError = document.getElementById('app-login-error');
    const appLogo = document.getElementById('app-logo');

    // --- Room Login Elements ---
    const loginContainer = document.getElementById('login-container');
    const chatContainer = document.getElementById('chat-container');
    const roomInput = document.getElementById('room');
    const passwordInput = document.getElementById('password');
    const joinButton = document.getElementById('join');
    const roomLoginError = document.getElementById('room-login-error');

    // --- Chat Elements ---
    const roomNameHeader = document.getElementById('room-name');
    const messages = document.getElementById('messages');
    const form = document.getElementById('form');
    const input = document.getElementById('input');

    // --- Background Changer ---
    const backgroundColorPicker = document.getElementById('background-color-picker');
    const chatBody = document.body; // Or a specific chat area

    // --- Mock Data & State ---
    const APP_PASSWORD = "123"; // Hardcoded app password
    const ROOM_PASSWORDS = {
        "general": "pass1",
        "dev-team": "pass2"
    };
    let currentRoom = "";

    // --- App Logo ---
    // In a real app, you'd check if logo.png exists. For now, we assume it does.
    // To make it visible, remove the `style="display: none;"` from the HTML or do it here.
    // For now, we will just keep it hidden as the file is missing.
    // appLogo.style.display = 'block';


    // --- App Login Logic ---
    appLoginButton.addEventListener('click', () => {
        if (appPasswordInput.value === APP_PASSWORD) {
            appLoginContainer.style.display = 'none';
            mainAppContainer.style.display = 'block'; // Show the main app
            loginContainer.style.display = 'block'; // Show the room login
        } else {
            appLoginError.style.display = 'block';
            appPasswordInput.classList.add('input-error');
        }
    });

    appPasswordInput.addEventListener('input', () => {
        appLoginError.style.display = 'none';
        appPasswordInput.classList.remove('input-error');
    });


    // --- Room Join Logic ---
    joinButton.addEventListener('click', () => {
        const room = roomInput.value.trim();
        const password = passwordInput.value;

        // In a real app, this would be an API call.
        // For this frontend-only demo, we use a mock object.
        if (room && ROOM_PASSWORDS[room] === password) {
            // Correct password
            currentRoom = room;
            loginContainer.style.display = 'none';
            chatContainer.style.display = 'flex';
            roomNameHeader.textContent = `Room: ${currentRoom}`;
        } else if (room && !ROOM_PASSWORDS[room]) {
            // New room, auto-admin (creator)
            ROOM_PASSWORDS[room] = password; // "Create" the room
            currentRoom = room;
            loginContainer.style.display = 'none';
            chatContainer.style.display = 'flex';
            roomNameHeader.textContent = `Room: ${currentRoom} (Admin)`;
        }
        else {
            // Incorrect password
            roomLoginError.style.display = 'block';
            passwordInput.classList.add('input-error');
        }
    });

    passwordInput.addEventListener('input', () => {
        roomLoginError.style.display = 'none';
        passwordInput.classList.remove('input-error');
    });


    // --- Chat Message Logic (Frontend Only) ---
    form.addEventListener('submit', (e) => {
        e.preventDefault();
        if (input.value) {
            const item = document.createElement('li');
            item.textContent = input.value;
            messages.appendChild(item);
            window.scrollTo(0, document.body.scrollHeight);
            input.value = '';
        }
    });

    // --- Background Color Changer ---
    backgroundColorPicker.addEventListener('input', (e) => {
        chatContainer.style.backgroundColor = e.target.value;
    });

});