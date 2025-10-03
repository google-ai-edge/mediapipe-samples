const socket = io({
    autoConnect: false // Do not connect automatically
});

// DOM Elements
const passwordContainer = document.getElementById('password-container');
const passwordInput = document.getElementById('password-input');
const passwordSubmit = document.getElementById('password-submit');
const errorMessage = document.getElementById('error-message');

const chatContainer = document.getElementById('chat-container');
const messages = document.getElementById('messages');
const form = document.getElementById('form');
const input = document.getElementById('input');

const bgColorPicker = document.getElementById('bg-color');

// --- Password Handling ---
passwordSubmit.addEventListener('click', () => {
    const password = passwordInput.value;
    socket.auth = { password };
    socket.connect();
});

socket.on("connect_error", (err) => {
    if (err.message === "invalid password") {
        errorMessage.classList.remove('hidden');
        passwordInput.value = '';
    }
});

socket.on('connect', () => {
    passwordContainer.classList.add('hidden');
    chatContainer.classList.remove('hidden');
    errorMessage.classList.add('hidden');
});


// --- Chat Functionality ---
form.addEventListener('submit', (e) => {
    e.preventDefault();
    if (input.value) {
        socket.emit('chat message', input.value);
        input.value = '';
    }
});

socket.on('chat message', (msg) => {
    const item = document.createElement('li');
    item.textContent = msg;
    messages.appendChild(item);
    window.scrollTo(0, document.body.scrollHeight);
});

// --- Background Change Functionality ---
bgColorPicker.addEventListener('input', (e) => {
    chatContainer.style.backgroundColor = e.target.value;
});