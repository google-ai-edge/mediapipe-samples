// we start by importing mediapipe tasks vision module
import vision from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision";

// we then import the camera class and the utils functions
import Camera from "./camera.js";
import {fetchImageAsElement} from "./utils.js";
import {createPortrait} from "./portrait.js";
import { createCopyTextureToCanvas } from "./convertMPMaskToImageBitmap.js"
const { ImageSegmenter, SegmentationMask, FilesetResolver } = vision;

// Here, we get a reference to the DOM elements
const btnStart = document.getElementById('btnStart');
const btnStop = document.getElementById('btnStop');
const btnTakePicture = document.getElementById('btnTakePicture');
const video = document.getElementById('video');
const selVideoSource = document.getElementById('selectVideoSource');
const selVideoResolution = document.getElementById('selectVideoResolution');

// background change options
const blurBackgroundOptIn = document.getElementById('blurBackgroundOptIn');
const chooseBackgroundImgOptIn = document.getElementById('chooseBackgroundImgOptIn');
const uploadBackgroundImgOptIn = document.getElementById('uploadBackgroundImgOptIn');

// select for users to choose a background image
const selBackgroundImg = document.getElementById('selBackgroundImg');
const selBackgroundImgDivContainer = document.getElementById('selBackgroundImgDivContainer');

// file input for users to upload a background image
const fileBackgroundImg = document.getElementById('fileBackgroundImg');
const fileBackgroundImgDivContainer = document.getElementById('fileBackgroundImgDivContainer');


// get a reference to the canvas element and its context
const videoCanvas = document.getElementById('canvas');
const videoCanvasCtx = videoCanvas.getContext('2d');

// create a new Camera class instance to interact with the camera
const camera = new Camera(video, video.videoWidth, video.videoHeight);
const tasksCanvas = document.createElement("canvas");
const toImageBitmap = createCopyTextureToCanvas(tasksCanvas);

// initialize variables that will be used later
let backgroundImage = null;
let requestAnimationFrameId = null;
let imageSegmenter = null;

const backgroundImageList = [
    "https://storage.googleapis.com/mediapipe-assets/bridge-image-seg.jpg",
    "https://storage.googleapis.com/mediapipe-assets/chairs-image-seg.jpg",
    "https://storage.googleapis.com/mediapipe-assets/stars-image-seg.jpg"
];



// this code will be executed when the DOM is loaded
document.addEventListener('DOMContentLoaded', async () =>{
    // populate the video source and background image select with the available devices and images respectively
    await populateVideoSourceSelect();
    await populateBackgroundImageSelect();

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
                "https://storage.googleapis.com/mediapipe-tasks/image_segmenter/selfie_segmentation.tflite",
            delegate: "GPU"

        },
        canvas: tasksCanvas,
        runningMode: "VIDEO",
    })
}

async function startSegmentationTask(){
    // In Safari, the timing of drawingImage for a VideoElement is severe and often results in an empty image.
    // Therefore, we use createImageBitmap to get the image from the video element at first.
    const input = await createImageBitmap(video);
    /**
     * Dispatches the segmentation task
     */
    let frameId = requestAnimationFrameId || 0;
    const segmentationMask = await imageSegmenter.segmentForVideo(input, frameId);


    if(camera.isRunning) {
        // draw the segmentation mask on the canvas
        await drawSegmentationResult(segmentationMask.confidenceMasks, input);

        segmentationMask.close();

        // start the segmentation task loop using requestAnimationFrame
        requestAnimationFrameId = window.requestAnimationFrame(startSegmentationTask);
    }
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

async function populateBackgroundImageSelect(){
    /**
     * Populates the background image select with the available images
     * @type {HTMLElement}
     */
    backgroundImageList.forEach((imageUrl) => {
        const filename = imageUrl.substring(imageUrl.lastIndexOf('/')+1);
        const option = document.createElement('option');
        option.value = imageUrl;
        option.text = filename;
        option.setAttribute('data-icon', imageUrl);
        selBackgroundImg.appendChild(option);
    });
}


async function startCamera(){
    /**
     * Starts the camera
     */
    // before starting the camera we make sure that the segmentation task is not running
    stopSegmentationTask();
    // we stop the camera first to make sure that the camera is not running
    await stopCamera();
    // start the camera and get the video stream
    const deviceId = selVideoSource.value;
    const resolution = selVideoResolution.value;
    const [width, height] = resolution.split('x');
    camera.setResolution(width, height);
    await camera.start(deviceId);
}

async function stopCamera(){
    /**
     * Stops the camera
     */
    if(camera.isRunning) {
        // stop the segmentation task if it is running
        stopSegmentationTask();
        // stop the camera
        await camera.stop();
    }
}

function clearCanvas(){
    /**
     * Clears the canvas
     */
    videoCanvasCtx.clearRect(0, 0, videoCanvas.width, videoCanvas.height);
}


// add event listeners
btnStart.addEventListener('click', async () => {
    try {
        // start the camera
        await startCamera();
        // dispatch segmentation task
        startSegmentationTask();
    }
    catch (e) {
        M.toast({html: e.toString(), displayLength: 5000})
    }
});

// set background image from the select
selBackgroundImg.addEventListener('change', async () => {
    // set image background
    try {
        backgroundImage = null;
        // set the background image to the image selected by the user
        const image_uri = selBackgroundImg.value;
        if(image_uri === '') {
            backgroundImage = null;
            return;
        }
        backgroundImage = await fetchImageAsElement(image_uri);
    }
    catch (e) {
        backgroundImage = null;
        M.toast({html: e.toString(), displayLength: 5000})
    }
});

// select background image from file
fileBackgroundImg.addEventListener('change', async (evt) => {
    try {
        backgroundImage = null;
        // set the background image to the image selected by the user
        const files = evt.target.files;
        const allowedExtReg = /(\.jpg|\.jpeg|\.png)$/i;
        // we only allow one file to be selected
        if (files && files.length) {
            let myFile = files[0];
            let myFileName = myFile.name;
            // check if the file extension is allowed
            if (allowedExtReg.test(myFileName)) {
                // we create a base64 image from the user uploaded image
                const fr = new FileReader()
                fr.readAsDataURL(myFile);
                const base64Img = await new Promise((resolve, reject) => {
                    fr.onload = () => {
                        resolve(fr.result);
                    }
                    fr.onerror = () => {
                        reject(fr.error);
                    }
                });

                const img = new Image();
                img.src = base64Img;

                // we set the background image to the image selected by the user
                backgroundImage = await new Promise((resolve, reject) => {
                    img.onload = () => {
                        resolve(img);
                    }
                    img.onerror = () => {
                        reject();
                    }
                });
            }
        }
    }
    catch (e) {
        backgroundImage = null;
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

btnTakePicture.addEventListener('click', async () => {
    // take a picture
    const photo = videoCanvasCtx.getImageData(0, 0, videoCanvas.width, videoCanvas.height);
    const portrait = await createPortrait(photo);
    const link = document.createElement('a');
    link.download = 'image.jpg';
    link.href = portrait;
    link.click();

});

blurBackgroundOptIn.addEventListener('change', () => {
    selBackgroundImgDivContainer.classList.add('hide');
    fileBackgroundImgDivContainer.classList.add('hide');
});

chooseBackgroundImgOptIn.addEventListener('change', () => {
    if(chooseBackgroundImgOptIn.checked) {
        selBackgroundImgDivContainer.classList.remove('hide');
        fileBackgroundImgDivContainer.classList.add('hide');
    }
    selBackgroundImg.dispatchEvent(new Event('change'));
});
uploadBackgroundImgOptIn.addEventListener('change', () => {
    if(uploadBackgroundImgOptIn.checked) {
        selBackgroundImgDivContainer.classList.add('hide');
        fileBackgroundImgDivContainer.classList.remove('hide');
    }
    fileBackgroundImg.dispatchEvent(new Event('change'));
});


async function drawSegmentationResult(segmentationResult, input){
    // get the canvas dimensions
    const canvasWidth = videoCanvas.width;
    const canvasHeight = videoCanvas.height;
    const videoWidth = video.videoWidth;
    const videoHeight = video.videoHeight;

    // calculate the scale of the video to fit the canvas
    const scaleX = canvasWidth / videoWidth;
    const scaleY = canvasHeight / videoHeight;
    const scale = Math.min(scaleX, scaleY);

    // The scale is defined for the video width and height
    const scaledWidth = videoWidth * scale;
    const scaledHeight = videoHeight * scale;

    // calculate the offset to center the video on the canvas
    const offsetX = (canvasWidth - scaledWidth) / 2;
    const offsetY = (canvasHeight - scaledHeight) / 2;

    // create segmentation mask
    const segmentationMask = segmentationResult[0];
    const segmentationMaskBitmap = await toImageBitmap(segmentationMask);

    videoCanvasCtx.save();
    videoCanvasCtx.fillStyle = 'white'
    videoCanvasCtx.clearRect(0, 0, scaledWidth, scaledHeight)

    // draw the mask image on the canvas
    videoCanvasCtx.translate(canvasWidth, 0);
    videoCanvasCtx.scale(-1, 1);
    videoCanvasCtx.drawImage(segmentationMaskBitmap, offsetX, offsetY, scaledWidth, scaledHeight);
    videoCanvasCtx.restore();

    videoCanvasCtx.save();
    if(blurBackgroundOptIn.checked) {
        // create blur background
        const blurBackgroundCanvas = document.createElement('canvas');
        blurBackgroundCanvas.width = scaledWidth;
        blurBackgroundCanvas.height = scaledHeight;
        const blurBackgroundCtx = blurBackgroundCanvas.getContext('2d');
        blurBackgroundCtx.translate(canvasWidth, 0);
        blurBackgroundCtx.scale(-1, 1);
        if (blurBackgroundCtx.filter) {
            blurBackgroundCtx.filter = 'blur(8px)'
            blurBackgroundCtx.drawImage(input, 0, 0, scaledWidth, scaledHeight)
        } else {
            // Safari does not supported for filter property.
            blurBackgroundCtx.drawImage(input, 0, 0, scaledWidth, scaledHeight)
            blurBackground(blurBackgroundCtx)
        }

        // draw the blur background on the canvas
        videoCanvasCtx.globalCompositeOperation = 'source-out'
        videoCanvasCtx.drawImage(blurBackgroundCanvas, offsetX, offsetY, scaledWidth, scaledHeight);
    }
    else {
        videoCanvasCtx.globalCompositeOperation = 'source-out'
        if(backgroundImage != null) {
            // draw the background image on the canvas
            videoCanvasCtx.drawImage(backgroundImage, offsetX, offsetY, scaledWidth, scaledHeight);
        } else {
            videoCanvasCtx.fillRect(0, 0,scaledWidth, scaledHeight)
        }
    }
    videoCanvasCtx.restore();

    videoCanvasCtx.save();
    // scale, flip and draw the video to fit the canvas
    videoCanvasCtx.globalCompositeOperation = 'destination-atop'
    videoCanvasCtx.translate(canvasWidth, 0);
    videoCanvasCtx.scale(-1, 1);
    videoCanvasCtx.drawImage(input, offsetX, offsetY, scaledWidth, scaledHeight);
    videoCanvasCtx.restore();
}

const blurBackground = (context, blurRadius = 8) => {
    /**
     * Applies blur to the canvas image
     * An implementation for fallback for safari that does not support the filter property,
     * which is not practical because it is very slow.
     * @param context: 2d canvas context
     */
    const { height, width } = context.canvas
    const imageData = context.getImageData(0, 0, width, height)
    const videoPixels = imageData.data
    
    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            // get the pixel index
            const i = (y * width + x) * 4;
            // we get the average color of the neighboring pixels and set it to the current pixel
            let r = 0, g = 0, b = 0, a = 0;
            let pixelCount = 0;
            // we loop through the neighboring pixels
            for (let dy = -blurRadius; dy <= blurRadius; dy++) {
                for (let dx = -blurRadius; dx <= blurRadius; dx++) {
                    let nx = x + dx;
                    let ny = y + dy;
                    // Check if the neighboring pixel is within the bounds of the image
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                        let offset = (ny * width + nx) * 4;
                        r += videoPixels[offset];
                        g += videoPixels[offset + 1];
                        b += videoPixels[offset + 2];
                        a += videoPixels[offset + 3];
                        pixelCount++;
                    }
                }
            }

            // Compute the average color of the neighboring pixels
            let avgR = r / pixelCount;
            let avgG = g / pixelCount;
            let avgB = b / pixelCount;
            let avgA = a / pixelCount;

            // Write the blurred pixel to the video canvas
            videoPixels[i] = avgR;
            videoPixels[i + 1] = avgG;
            videoPixels[i + 2] = avgB;
            videoPixels[i + 3] = avgA;
        }
    }

    context.putImageData(imageData, 0, 0)
}