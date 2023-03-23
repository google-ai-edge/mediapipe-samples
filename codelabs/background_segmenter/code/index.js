// we start by importing the tasks-vision module from the CDN
import vision from "https://cdn.skypack.dev/@mediapipe/tasks-vision";

// the Camera class will help us to interact with the webcam
import Camera from "./camera.js";
import {downloadImage, scaleImageData} from "./utils.js";
const { ImageSegmenter, SegmentationMask, FilesetResolver } = vision;

// get a reference to the DOM elements that we wil use
const btnStart = document.getElementById('btnStart');
const btnStop = document.getElementById('btnStop');
const video = document.getElementById('video');
const selVideoSource = document.getElementById('selectVideoSource');
const selVideoResolution = document.getElementById('selectVideoResolution');
const txtBackgroundImageInput = document.getElementById('txtBackgroundImage');

// get a reference to the canvas element and its context
const videCanvas = document.getElementById('canvas');
const videoCanvasCtx = videCanvas.getContext('2d');

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

// this function will be called when the webpage is loaded
document.addEventListener('DOMContentLoaded', async () =>{
    // populate the video source select with the available video sources
    await populateVideoSourceSelect();
    // create a new image segmenter instance using the mediapipe wasm runtime
    imageSegmenter = await createImageSegmenter();
    // initialize the materialize components
    M.AutoInit();
});

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



// draw the segmentation mask on the canvas
function segmentationCallback(segmentationMask){
    /**
     * Callback function called when the segmentation is done
     * @param segmentationMask {SegmentationMask} the segmentation mask
     */
    if(camera.isRunning) {
        drawSegmentationMask(segmentationMask);
        requestAnimationFrameId = window.requestAnimationFrame(startSegmentationTask);
    }
}
function startSegmentationTask(){
    /**
     * Dispatches the segmentation task
     */
    let nowInMs = Date.now();
    imageSegmenter.segmentForVideo(video, nowInMs, segmentationCallback);
}

function stopSegmentationTask(){
    /**
     * Stops the segmentation task
     */
    if(requestAnimationFrameId) {
        window.cancelAnimationFrame(requestAnimationFrameId);
    }
}

async function populateVideoSourceSelect() {
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


async function startCamera(){
    /**
     * Starts the camera and starts the segmentation
     */
    // before starting the camera we make sure that the segmentation task is not running
    stopSegmentationTask();
    // we stop the camera first to make sure that the camera is not running
    await stopCamera();
    // start the camera and get the video stream
    const deviceId = selVideoSource.value;
    const resolution = selVideoResolution.value;
    const [width, height] = resolution.split('x');
    await camera.setResolution(width, height);
    await camera.start(deviceId);
}

async function stopCamera(){
    /**
     * Stops the camera and segmentation task
     */
    if(camera.isRunning) {
        // stop the segmentation task if it is running
        stopSegmentationTask();
        // stop the camera
        await camera.stop();
    }
}


async function setBackgroundImage(){
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
        backgroundImage = await downloadImage(image_uri, videCanvas.width, videCanvas.height);
    }
    catch (e) {
        backgroundImage = null;
        M.toast({html: e.toString(), displayLength: 5000})
    }

}

function clearCanvas(){
    /**
     * Clears the canvas
     */
    videoCanvasCtx.clearRect(0, 0, videCanvas.width, videCanvas.height);
}


// add event listeners
btnStart.addEventListener('click', async () => {
    try {
        // set image background
        await setBackgroundImage();
        // start the camera
        await startCamera();
        // dispatch segmentation task
        startSegmentationTask();
    }
    catch (e) {
        M.toast({html: e.toString(), displayLength: 5000})
    }
});


btnStop.addEventListener('click', async () => {
    // stop the camera
    await stopCamera();
    // stop segmentation task
    stopSegmentationTask();
    // clear the canvas
    clearCanvas();
});

function createSegmentationMaskFromLabels(SegmentationMaskLabels) {
    /**
     * Creates a segmentation mask from the segmentation mask labels
     * @type {Uint8ClampedArray}
     */

    // create Uint8ClampedArray to hold the segmentation mask data
    let segmentationMask = new Uint8ClampedArray(
        video.videoWidth * video.videoHeight * 4
    );

    // we loop through the segmentation mask labels and create the segmentation mask
    for (let i in SegmentationMaskLabels) {

        // for each pixel we get the label index from the segmentation mask labels and
        // we get the label from the labels array
        const labelIdx = SegmentationMaskLabels[i];
        const label = labels[labelIdx];

        // we check if the label is in the labelsToSegment array
        if (labelsToSegment.includes(label)) {
            // if the label is in the labelsToSegment array, we set the pixel to white.
            // It could be a person or a sofa or a tv
            segmentationMask[i * 4] = 255;
            segmentationMask[i * 4 + 1] = 255;
            segmentationMask[i * 4 + 2] = 255;
            segmentationMask[i * 4 + 3] = 255;
        } else {
            // we set the pixel to black if it is not in the labelsToSegment array
            segmentationMask[i * 4] = 0;
            segmentationMask[i * 4 + 1] = 0;
            segmentationMask[i * 4 + 2] = 0;
            segmentationMask[i * 4 + 3] = 255;
        }
    }
    return segmentationMask;
}

async function drawSegmentationMask({ 0 : SegmentationMaskLabels} = SegmentationResult){

    /**
     * Draws the segmentation mask on the canvas
     * @param SegmentationMaskLabels {Array} the segmentation mask labels
     */

    // get the canvas dimensions
    const canvasWidth = videCanvas.width;
    const canvasHeight = videCanvas.height;
    const videoWidth = video.videoWidth;
    const videoHeight = video.videoHeight;

    // calculate the scale
    const scaleX = canvasWidth / videoWidth;
    const scaleY = canvasHeight / videoHeight;
    const scale = Math.min(scaleX, scaleY);

    // Scale the video to fit the canvas.
    const scaledWidth = videoWidth * scale;
    const scaledHeight = videoHeight * scale;

    // calculate the offset to center the video on the canvas
    const offsetX = (canvasWidth - scaledWidth) / 2;
    const offsetY = (canvasHeight - scaledHeight) / 2;


    // create the segmentation mask from the segmentation mask labels
    let segmentationMask = createSegmentationMaskFromLabels(SegmentationMaskLabels);
    // create an ImageData object from the segmentation mask
    let segmentationMaskImageData = new ImageData(segmentationMask, video.videoWidth, video.videoHeight);
    // scale the segmentation mask to the video canvas size
    segmentationMaskImageData = scaleImageData(segmentationMaskImageData, scaledWidth, scaledHeight);
    // create an ImageBitmap from the scaled segmentation mask
    const segmentationMaskBitmap = await createImageBitmap(segmentationMaskImageData);
    // create a canvas to hold the scaled segmentation mask and mirror it
    const canvasMask = document.createElement('canvas');
    const canvasMaskCtx = canvasMask.getContext('2d');
    canvasMask.width = canvasWidth;
    canvasMask.height = canvasHeight;
    canvasMaskCtx.save();
    canvasMaskCtx.translate(canvasWidth, 0);
    canvasMaskCtx.scale(-1, 1);
    canvasMaskCtx.drawImage(segmentationMaskBitmap, offsetX, offsetY, scaledWidth, scaledHeight);
    canvasMaskCtx.restore();


    // draw the video frame  at the center of the canvas
    videoCanvasCtx.save();
    // mirror the canvas
    videoCanvasCtx.translate(canvasWidth, 0);
    videoCanvasCtx.scale(-1, 1);
    videoCanvasCtx.clearRect(0, 0, canvasWidth, canvasHeight);
    // draw the video
    videoCanvasCtx.drawImage(video, offsetX, offsetY, scaledWidth, scaledHeight);
    videoCanvasCtx.restore();


    // we get the canvas data and the segmentation mask data to apply the segmentation
    const canvasData = videoCanvasCtx.getImageData(offsetX, offsetY, scaledWidth, scaledHeight).data; // canvas data
    const binaryMaskData = canvasMaskCtx.getImageData(offsetX, offsetY, scaledWidth, scaledHeight).data; // segmentation mask data

    // we check if the background image is null or not
    if(backgroundImage === null) {
        for (let i = 0; i < canvasData.length; i += 4) {
            // we check if the pixel is a background pixel or not and we set the pixel to black if it is a background pixel
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
        // we get the background image data
        const backgroundData = scaleImageData(backgroundImage, scaledWidth, scaledHeight).data; // background image data
        for (let i = 0; i < canvasData.length; i += 4) {
            // we check if the pixel is a background pixel or not and we set the pixel to the background image pixel if it is a background pixel
            const isBackgroundPixel = binaryMaskData[i] === 0 && binaryMaskData[i + 1] === 0 && binaryMaskData[i + 2] === 0;
            if (isBackgroundPixel) {
                canvasData[i + 0] = backgroundData[i + 0];
                canvasData[i + 1] = backgroundData[i + 1];
                canvasData[i + 2] = backgroundData[i + 2];
                canvasData[i + 3] = backgroundData[i + 3];
            }
        }
    }
    // update the canvas with the new canvas data after applying the segmentation mask
    videoCanvasCtx.putImageData(new ImageData(canvasData, scaledWidth, scaledHeight), offsetX, offsetY);

}



