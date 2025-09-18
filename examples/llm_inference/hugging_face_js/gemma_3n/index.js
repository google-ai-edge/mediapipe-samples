// Copyright 2025 The MediaPipe Authors.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

//      http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// -------------------------------------------------------------------------- //

import {oauthLoginUrl, oauthHandleRedirectIfPresent} from "@huggingface/hub";
import {FilesetResolver, LlmInference} from '@mediapipe/tasks-genai';

// --- DOM Element References ---
const webcamElement = document.getElementById('webcam');
const statusMessageElement = document.getElementById(
  'status-message',
);
const responseContainer = document.getElementById(
  'response-container',
);
const promptInputElement = document.getElementById(
  'prompt-input',
);
const recordButton = document.getElementById(
  'record-button',
);
const sendButton = document.getElementById('send-button');
const recordButtonIcon = recordButton.querySelector('i');
const loaderOverlay = document.getElementById('loader-overlay');
const progressBarFill = document.getElementById('progress-bar-fill');
const signInMessage = document.getElementById('sign-in-message');
const loaderMessage = document.getElementById('loader-message');

// --- State Management ---
let isRecording = false;
let isLoading = false;
let mediaRecorder = null;
let audioChunks = [];

// --- Model-specific constants ---
// If the user wants to try running on a more limited device, they can switch
// the demo from default E4B to E2B by appending '?e2b' to the URL.
const thisUrl = new URL(window.location.href);
const use_e4b = !thisUrl.searchParams.has('e2b');
const cacheFileName = use_e4b ? "3n_e4b" : "3n_e2b";
const remoteFileUrl = use_e4b ? 'https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4-Web.litertlm'
                              : 'https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4-Web.litertlm';
// Model size in bytes for reliable progress indication; just hard-coded for now
const modelSize = use_e4b ? 4274978816 : 3038117888;

// --- Core Functions ---

/**
 * Updates the progress bar's width.
 * @param {number} percentage The progress percentage (0-100).
 */
function updateProgressBar(percentage) {
  if (progressBarFill) {
    progressBarFill.style.width = `${percentage}%`;
  }
}

/**
 * Initializes our local LLM from a StreamReader.
 */
let llmInference;
async function initLlm(modelReader) {
  console.log('Initializing LLM');
  loaderMessage.textContent = "Initializing model...";

  // We have no actual progress updates for this last initialization step, but
  // it's relatively short (<10s on a decent laptop). So we just set to 90%.
  // TODO: It'd look nicer to have this go from 0 to 100% instead.
  updateProgressBar(90);
  const genaiFileset = await FilesetResolver.forGenAiTasks(
      'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-genai/wasm');
  try {
    llmInference = await LlmInference.createFromOptions(genaiFileset, {
          baseOptions: {modelAssetBuffer: modelReader},
          maxTokens: 2048,
          maxNumImages: 1,
          supportAudio: true,
        });
  
    // Enable demo now that loading has fully finished.
    loaderOverlay.style.opacity = '0';
    setTimeout(() => {
      loaderOverlay.style.display = 'none';
      promptInputElement.disabled = false;
      sendButton.disabled = false;
      recordButton.disabled = false;
    }, 300);
  } catch (error) {
    console.error('Failed to initialize the LLM', error);
    loaderOverlay.style.display = 'none'; // Hide loader on error
  }
}

/**
 * Replaces our demo with a sign-in button for HuggingFace.
 */
function requireSignIn() {
  document.getElementById('loader-overlay').style = "display:none";
  document.getElementById('main-container').style = "display:none";
  document.getElementById("signin").style.removeProperty("display");
  document.getElementById('sign-in-message').style.removeProperty("display");
  document.getElementById("signin").onclick = async function() {
    // prompt=consent to re-trigger the consent screen instead of silently redirecting
    window.location.href = (await oauthLoginUrl({scopes: window.huggingface.variables.OAUTH_SCOPES})) + "&prompt=consent";
  };
  // clear old oauth, if any
  localStorage.removeItem("oauth");
}

/**
 * Utility function to show progress while we load from remote file into local
 * cache.
 */
async function pipeStreamAndReportProgress(readableStream, writableStream) {
  // Effectively "await responseStream.pipeTo(writeStream)", but with progress
  // reporting.
  const reader = readableStream.getReader();
  const writer = writableStream.getWriter();
  let bytesCount = 0;
  let progressBarPercent = 0;
  let wasAborted = false;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) {
        break;
      }
      if (value) {
        bytesCount += value.length;
        const percentage = Math.round(bytesCount / modelSize * 90);
        if (percentage > progressBarPercent) {
          progressBarPercent = percentage;
          updateProgressBar(progressBarPercent);
        }
        await writer.write(value);
      }
    }
  } catch (error) {
    console.error('Error while piping stream:', error);
    // Abort the writer if there's an error
    wasAborted = true;
    await writer.abort(error);
    throw error;
  } finally {
    // Release the reader lock
    reader.releaseLock();
    // Close the writer only if the stream wasn't aborted
    if (!wasAborted) {
      console.log('Closing the writer, and hence the stream');
      await writer.close();
    }
  }
}

/**
 * Loads the LLM file from either cache or OAuth-guarded remote download.
 */
async function loadLlm() {
  let opfs = await navigator.storage.getDirectory();
  // If we can load the model from cache, then do so.
  try {
    const fileHandle = await opfs.getFileHandle(cacheFileName);
    // Check to make sure size is as expected, and not a partially-downloaded
    // or corrupt file.
    console.log('Model found in cache; checking size.');
    const file = await fileHandle.getFile();
    console.log('File size is: ', file.size);
    if (file.size !== modelSize) {
      console.error('Cached model had unexpected size. Redownloading.');
      throw new Error('Unexpected cached model size');
    }
    console.log('Model found in cache of expected size, reusing.');
    const fileReader = file.stream().getReader();
    await initLlm(fileReader);
  } catch {
    // Otherwise, we need to be download remotely, which requires oauth.
    console.log('Model not found in cache: oauth and download required.');
    // We first remove from cache, in case model file is corrupted/partial.
    try {
      await opfs.removeEntry(cacheFileName);
    } catch {}
    let oauthResult = localStorage.getItem("oauth");
    if (oauthResult) {
      try {
        oauthResult = JSON.parse(oauthResult);
      } catch {
        oauthResult = null;
      }
    }
    oauthResult ||= await oauthHandleRedirectIfPresent();
    // If we have successful oauth from one of the methods above, download from
    // remote.
    if (oauthResult?.accessToken) {
      localStorage.setItem("oauth", JSON.stringify(oauthResult));
      const modelUrl = remoteFileUrl;
      const oauthHeaders = {
        "Authorization": `Bearer ${oauthResult.accessToken}`
      };
      
      const response =  await fetch(modelUrl, {headers: oauthHeaders});
      if (response.ok) {
        const responseStream = await response.body;
        // Cache locally, so we can avoid this next time.
        const fileHandle =
            await opfs.getFileHandle(cacheFileName, {create: true});
        const writeStream = await fileHandle.createWritable();
        await pipeStreamAndReportProgress(responseStream, writeStream);
        console.log('Model written to cache!');
        const file = await fileHandle.getFile();
        const fileReader = file.stream().getReader();
        await initLlm(fileReader);
      } else {
        console.error('Model fetch encountered error. Likely requires sign-in or Gemma license acknowledgement.');
        requireSignIn();
      }
    } else {
      // No successful oauth, so replace our demo with a HuggingFace sign-in button.
      console.log('No oauth detected. Requiring sign-in.');
      requireSignIn();
    }
  }
}

/**
 * Initializes the webcam and microphone.
 */
let audioUrl = undefined;
async function initMedia() {
  // Disable controls on startup
  promptInputElement.disabled = true;
  sendButton.disabled = true;
  recordButton.disabled = true;

  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: true,
      audio: true,
    });
    webcamElement.srcObject = stream;
    statusMessageElement.style.display = 'none';
    webcamElement.style.display = 'block';

    await loadLlm();

    // Set up MediaRecorder for audio
    mediaRecorder = new MediaRecorder(stream);
    mediaRecorder.ondataavailable = (event) => {
      audioChunks.push(event.data);
    };
    mediaRecorder.onstop = () => {
      // We process the audio here, and free the previous one, if any
      const blob = new Blob(audioChunks, {type: 'audio/webm'});
      if (audioUrl) window.URL.revokeObjectURL(audioUrl);
      audioUrl = window.URL.createObjectURL(blob);
      audioChunks = [];

      sendQuery({audioSource: audioUrl});
    };
  } catch (error) {
    console.error('Error accessing media devices.', error);
    audioUrl = undefined;
    statusMessageElement.textContent =
      'Error: Could not access camera or microphone. Please check permissions.';
    loaderOverlay.style.display = 'none'; // Hide loader on error
  }
}

/**
 * Toggles the audio recording state.
 */
function toggleRecording() {
  isRecording = !isRecording;
  if (isRecording) {
    if (mediaRecorder && mediaRecorder.state === 'inactive') {
      mediaRecorder.start();
    }
    recordButton.classList.add('recording');
    if (recordButtonIcon) {
      recordButtonIcon.className = 'fa-solid fa-stop';
    }
    promptInputElement.placeholder = 'Recording... Press stop when done.';
  } else {
    if (mediaRecorder && mediaRecorder.state === 'recording') {
      mediaRecorder.stop();
    }
    recordButton.classList.remove('recording');
    if (recordButtonIcon) {
      recordButtonIcon.className = 'fa-solid fa-microphone';
    }
    promptInputElement.placeholder = 'Ask a question about what you see...';
  }
}

/**
 * Sends a text prompt with webcam frame to the Gemma 3n model.
 */
async function sendTextQuery() {
  const prompt = promptInputElement.value.trim();
  sendQuery(prompt);
}

/**
 * Sends the user's prompt (text or audio) with webcam frame to the Gemma 3n model.
 */
async function sendQuery(prompt) {
  if (!prompt || isLoading) {
    return;
  }

  setLoading(true);

  try {
    const query = [
      '<ctrl99>user\n',
      prompt,  // audio or text
      {imageSource: webcam},
      '<ctrl100>\n<ctrl99>model\n'
    ];
    let resultSoFar = '';
    await llmInference.generateResponse(query, (newText, isDone) => {
      resultSoFar += newText;
      updateResponse(resultSoFar);
    });
    promptInputElement.value = '';
  } catch (error) {
    console.error('Error running Gemma 3n on query.', error);
    updateResponse(
      `Error: Could not get a response. ${error instanceof Error ? error.message : String(error)}`,
    );
  } finally {
    setLoading(false);
  }
}

/**
 * Updates the response container with new content.
 * @param {string} text The text to display.
 */
function updateResponse(text) {
  responseContainer.classList.remove('thinking');
  responseContainer.innerHTML = '';
  const p = document.createElement('p');
  p.textContent = text;
  responseContainer.appendChild(p);
}

/**
 * Sets the loading state of the UI.
 * @param {boolean} loading - True if loading, false otherwise.
 */
function setLoading(loading) {
  isLoading = loading;
  promptInputElement.disabled = loading;
  sendButton.disabled = loading;
  recordButton.disabled = loading;

  if (loading) {
    responseContainer.classList.add('thinking');
    responseContainer.innerHTML = '<p>Processing...</p>';
  }
}

// --- Event Listeners ---
recordButton.addEventListener('click', toggleRecording);
sendButton.addEventListener('click', sendTextQuery);
promptInputElement.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    sendTextQuery();
  }
});

// --- Initialization ---
document.addEventListener('DOMContentLoaded', initMedia);
