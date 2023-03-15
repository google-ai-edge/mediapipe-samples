// we start by importing the tasks-vision module from the CDN
import vision from "https://cdn.skypack.dev/@mediapipe/tasks-vision";

// the Camera class will help us to interact with the webcam
import Camera from "./camera.js";
import {downloadImage, scaleImageData} from "./utils.js";
const { ImageSegmenter, SegmentationMask, FilesetResolver } = vision;

// get a reference to the the DOM elements that we will use
const btnStart = document.getElementById('btnStart');
const btnStop = document.getElementById('btnStop');
const video = document.getElementById('video');
const selVideoSource = document.getElementById('selectVideoSource');
const txtBackgroundImageInput = document.getElementById('txtBackgroundImage');

// get a reference to the canvas element and its context
const canvas = document.getElementById('canvas');
const canvasCtx = canvas.getContext('2d');

// create a new camera instance
const camera = new Camera(video, video.videoWidth, video.videoHeight);

// initialize variables that will be used later
let backgroundImage = null;
let requestAnimationFrameId = null;
let imageSegmenter = null;
let labelsToSegment = ["dog", "person"]
const labels = [
    'background',
    'aeroplane',
    'bicycle',
    'bird',
    'boat',
    'bottle',
    'bus',
    'car',
    'cat',
    'chair',
    'cow',
    'dining table',
    'dog',
    'horse',
    'motorbike',
    'person',
    'potted plant',
    'sheep',
    'sofa',
    'train',
    'tv'];

// segmentation stuff

async function createImageSegmenter() {
    /**
     * Creates a new image segmenter
     * @returns {Promise<ImageSegmenter>} imageSegmenter
     */
    const wasmFileset = await FilesetResolver.forVisionTasks(
        "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@latest/wasm"
    );
    // Initializes the Wasm runtime and creates a new image segmenter from the provided options.
    return await  ImageSegmenter.createFromOptions(wasmFileset, {
        baseOptions: {
            modelAssetPath:
                "https://storage.googleapis.com/mediapipe-assets/deeplabv3.tflite?generation=1661875711618421"

        },
        runningMode: "VIDEO",
    })
}

const segmentationCallback = (segmentationMask) => {
    /**
     * Callback function called when the segmentation is done
     * @param segmentationMask {SegmentationMask} the segmentation mask
     */
    if(camera.isRunning) {
        drawSegmentationMask(segmentationMask);
        requestAnimationFrameId = window.requestAnimationFrame(dispatchSegmentationTask);
    }
}

const dispatchSegmentationTask= ()=>{
    /**
     * Dispatches the segmentation task
     */
    let nowInMs = Date.now();
    imageSegmenter.segmentForVideo(video, nowInMs, segmentationCallback);
}

const populateVideoSourceSelect = async () => {
    /**
     * Populates the video source select with the available devices
     * @type {HTMLElement}
     */
    const videoDevices = await Camera.devicesList();
    videoDevices.forEach((device) => {
        const option = document.createElement('option');
        option.value = device.id;
        option.text = device.label;
        selVideoSource.appendChild(option);
    });
}


const startCamera = async () => {
    /**
     * Starts the camera and starts the segmentation
     */
    // we stop the camera first to make sure that the camera is not running
    await stopCamera();

    // start the camera and get the video stream
    const deviceId = selVideoSource.value;
    await camera.start(deviceId);

    // start segmentation task
    dispatchSegmentationTask();
}

const stopCamera = async () => {
    /**
     * Stops the camera and segmentation task
     */
    if(requestAnimationFrameId && camera.isRunning) {
        // stop the segmentation task
        window.cancelAnimationFrame(requestAnimationFrameId);
        // stop the camera
        await camera.stop();
        // clear the canvas
        canvasCtx.clearRect(0, 0, canvas.width, canvas.height);
    }
}


const setBackgroundImage = async () => {
    /**
     * Changes the background image
     */
    try {
        // set the background image to the image selected by the user
        const image_uri = txtBackgroundImageInput.value;
        if(image_uri === '') {
            backgroundImage = null;
            return;
        }
        backgroundImage = await downloadImage(image_uri, canvas.width, canvas.height);
    }
    catch (e) {
        backgroundImage = null;
        M.toast({html: e.toString(), displayLength: 5000})
    }

}

// add event listeners
btnStart.addEventListener('click', async () => {
    try {
        await setBackgroundImage();
        // start the camera
        await startCamera();
    }
    catch (e) {
        M.toast({html: e.toString(), displayLength: 5000})
    }
});

btnStop.addEventListener('click', async () => {

    // stop the camera
    await stopCamera();
});


const drawSegmentationMask = ({ 0 : SegmentationMaskLabels} = SegmentationResult) => {

    /**
     * Draws the segmentation mask on the canvas
     * @param SegmentationMaskLabels {Array} the segmentation mask labels
     */


    const canvasWidth = canvas.width;
    const canvasHeight = canvas.height;
    const videoWidth = video.videoWidth;
    const videoHeight = video.videoHeight;

    const scaleX = canvasWidth / videoWidth;
    const scaleY = canvasHeight / videoHeight;
    const scale = Math.min(scaleX, scaleY);

    // Scale the video to fit the canvas.
    const scaledWidth = videoWidth * scale;
    const scaledHeight = videoHeight * scale;


    // create segmentation mask image data
    let segmentationMask = new Uint8ClampedArray(
        video.videoWidth * video.videoHeight * 4
    );

    for (let i in SegmentationMaskLabels) {

        const labelIdx = SegmentationMaskLabels[i];
        const label = labels[labelIdx];

        if(labelsToSegment.includes(label)) {
            // we set the pixel to white if it is a person
            segmentationMask[i * 4] = 255;
            segmentationMask[i * 4 + 1] = 255;
            segmentationMask[i * 4 + 2] = 255;
            segmentationMask[i * 4 + 3] = 255;
        }
        else {

            // we set the pixel to black if it is not a person
            segmentationMask[i * 4] = 0;
            segmentationMask[i * 4 + 1] = 0;
            segmentationMask[i * 4 + 2] = 0;
            segmentationMask[i * 4 + 3] = 255;
        }
    }

    // we scale the segmentation mask to fit the canvas
    let segmentationMaskImageData = new ImageData(segmentationMask, video.videoWidth, video.videoHeight);
    segmentationMaskImageData = scaleImageData(segmentationMaskImageData, scaledWidth, scaledHeight);
    //canvasCtx.putImageData(segmentationMaskImageData, offsetX, offsetY);


    // draw the video frame  at the center of the canvas
    const offsetX = (canvasWidth - scaledWidth) / 2;
    const offsetY = (canvasHeight - scaledHeight) / 2;
    canvasCtx.save();
    canvasCtx.clearRect(0, 0, canvasWidth, canvasHeight);
    canvasCtx.drawImage(video, offsetX, offsetY, scaledWidth, scaledHeight);
    canvasCtx.restore();

    // change the canvas image data to the background image

    const canvasData = canvasCtx.getImageData(offsetX, offsetY, scaledWidth, scaledHeight).data; // canvas data
    const binaryMaskData = segmentationMaskImageData.data; // segmentation mask data

    if(backgroundImage === null) {
        for (let i = 0; i < canvasData.length; i += 4) {
            const isBackgroundPixel = binaryMaskData[i] === 0 && binaryMaskData[i + 1] === 0 && binaryMaskData[i + 2] === 0;
            if (isBackgroundPixel) {
                canvasData[i + 0] = 0;
                canvasData[i + 1] = 0;
                canvasData[i + 2] = 0;
                canvasData[i + 3] = 255;
            }
        }
    }
    else {
        const backgroundData = scaleImageData(backgroundImage, scaledWidth, scaledHeight).data; // background image data
        for (let i = 0; i < canvasData.length; i += 4) {
            const isBackgroundPixel = binaryMaskData[i] === 0 && binaryMaskData[i + 1] === 0 && binaryMaskData[i + 2] === 0;
            if (isBackgroundPixel) {
                canvasData[i + 0] = backgroundData[i + 0];
                canvasData[i + 1] = backgroundData[i + 1];
                canvasData[i + 2] = backgroundData[i + 2];
                canvasData[i + 3] = backgroundData[i + 3];
            }
        }
    }
    // draw the canvas image data
    canvasCtx.putImageData(new ImageData(canvasData, scaledWidth, scaledHeight), offsetX, offsetY);
}



document.addEventListener('DOMContentLoaded', async () =>{
    await populateVideoSourceSelect();
    imageSegmenter = await createImageSegmenter();
    M.AutoInit();
});