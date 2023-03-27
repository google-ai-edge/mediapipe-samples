// we start by importing mediapipe tasks vision module
import vision from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision";

// we then import the camera class and the utils functions
import Camera from "./camera.js";
import {fetchImage, resizeImageData} from "./utils.js";
import {createPortrait} from "./portrait.js";
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
                "https://storage.googleapis.com/mediapipe-tasks/image_segmenter/selfie_segmentation.tflite"

        },
        runningMode: "VIDEO",
    })
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

async function segmentationCallback(segmentationMask){
    /**
     * Callback function called when the segmentation task is completed for every frame
     * @param segmentationMask {SegmentationMask} the segmentation mask
     */
    if(camera.isRunning) {
        // draw the segmentation mask on the canvas
        await drawSegmentationResult(segmentationMask);
        // start the segmentation task loop using requestAnimationFrame
        requestAnimationFrameId = window.requestAnimationFrame(startSegmentationTask);
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
        backgroundImage = await fetchImage(image_uri);
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

                // we create a tem canvas to hold the user uploaded image
                const tempCanvas = document.createElement('canvas');
                const tempCanvasCtx = tempCanvas.getContext('2d');
                const img = new Image();
                img.src = base64Img;

                // we set the background image to the image selected by the user
                backgroundImage = await new Promise((resolve, reject) => {
                    img.onload = () => {
                        tempCanvas.width = img.width;
                        tempCanvas.height = img.height;
                        tempCanvasCtx.drawImage(img, 0, 0);
                        const imageData = tempCanvasCtx.getImageData(0, 0, tempCanvas.width, tempCanvas.height);
                        resolve(imageData);
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


async function drawSegmentationResult(segmentationResult){

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
    const segmentationMaskData = new ImageData(video.videoWidth, video.videoHeight);
    const pixelCount = segmentationMask.length;
    for (let i = 0; i < pixelCount; i++) {
        const maskValue = segmentationMask[i];
        const maskValueRGB = maskValue * 255;
        segmentationMaskData.data[i * 4] = maskValueRGB;
        segmentationMaskData.data[i * 4 + 1] = maskValueRGB;
        segmentationMaskData.data[i * 4 + 2] = maskValueRGB;
        segmentationMaskData.data[i * 4 + 3] = 255;
    }

    // scale and flip the segmentation mask to fit the canvas
    const segmentationMaskCanvas = document.createElement('canvas');
    const canvasMaskCtx = segmentationMaskCanvas.getContext('2d');
    segmentationMaskCanvas.width = canvasWidth;
    segmentationMaskCanvas.height = canvasHeight;
    canvasMaskCtx.save();
    canvasMaskCtx.translate(canvasWidth, 0);
    canvasMaskCtx.scale(-1, 1);
    const segmentationMaskBitmap = await createImageBitmap(segmentationMaskData);
    canvasMaskCtx.drawImage(segmentationMaskBitmap, offsetX, offsetY, scaledWidth, scaledHeight);
    canvasMaskCtx.restore();


    // scale, flip and draw the video to fit the canvas
    videoCanvasCtx.save();
    videoCanvasCtx.translate(canvasWidth, 0);
    videoCanvasCtx.scale(-1, 1);
    videoCanvasCtx.clearRect(0, 0, canvasWidth, canvasHeight);
    videoCanvasCtx.drawImage(video, offsetX, offsetY, scaledWidth, scaledHeight);
    videoCanvasCtx.restore();

    // we get the canvas data and the segmentation mask data to apply the segmentation
    const canvasVideoData = videoCanvasCtx.getImageData(offsetX, offsetY, scaledWidth, scaledHeight); // canvas data
    const canvasMaskData = canvasMaskCtx.getImageData(offsetX, offsetY, scaledWidth, scaledHeight); // segmentation mask data

    // apply background segmentation
    if(blurBackgroundOptIn.checked) {
        blurBackground(canvasVideoData, canvasMaskData);
    }
    else {
        changeBackground(canvasVideoData, canvasMaskData);
    }

    videoCanvasCtx.putImageData(canvasVideoData, offsetX, offsetY);

}

const blurBackground = (canvasVideoData, canvasMaskData, blurRadius = 8) => {
    /**
     * Applies the segmentation mask to the canvas data
     * @param canvasVideoData: canvas data
     * @param canvasMaskData: segmentation mask data
     */
    let width = canvasVideoData.width;
    let height = canvasVideoData.height;
    let videoPixels = canvasVideoData.data;
    let maskPixels = canvasMaskData.data;


    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            // get the pixel index
            const i = (y * width + x) * 4;

            // check if the pixel is a background pixel
            const isBackgroundPixel = (
                maskPixels[i] === 0 &&
                maskPixels[i + 1] === 0 &&
                maskPixels[i + 2] === 0
            );

            // if the pixel is a background pixel, we blur it
            if (isBackgroundPixel) {
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
    }

}


const changeBackground = (canvasVideoData, canvasMaskData) => {
    /**
     * Applies the segmentation mask to the canvas data
     * @param canvasVideoData: canvas data
     * @param canvasMaskData: segmentation mask data
     */

    // we get the canvas data and the segmentation mask data to apply the segmentation
    let width = canvasVideoData.width;
    let height = canvasVideoData.height;
    const videoPixels = canvasVideoData.data; // canvas data
    const maskPixels = canvasMaskData.data; // segmentation mask data

    if(backgroundImage === null) {
        for (let i = 0; i < videoPixels.length; i += 4) {
            // we check if the pixel is a background pixel
            const isBackgroundPixel = maskPixels[i] === 0 && maskPixels[i + 1] === 0 && maskPixels[i + 2] === 0;
            if (isBackgroundPixel) {
                // we set the pixel to black
                videoPixels[i + 0] = 0;
                videoPixels[i + 1] = 0;
                videoPixels[i + 2] = 0;
                videoPixels[i + 3] = 255;
            }
        }
    }
    else {
        // we get the background image data
        const backgroundImagePixels = resizeImageData(backgroundImage, width, height).data; // background image data
        for (let i = 0; i < videoPixels.length; i += 4) {
            // we check if the pixel is a background pixel
            const isBackgroundPixel = maskPixels[i] === 0 && maskPixels[i + 1] === 0 && maskPixels[i + 2] === 0;
            if (isBackgroundPixel) {
                // we set the pixel to the background image pixel
                videoPixels[i + 0] = backgroundImagePixels[i + 0];
                videoPixels[i + 1] = backgroundImagePixels[i + 1];
                videoPixels[i + 2] = backgroundImagePixels[i + 2];
                videoPixels[i + 3] = backgroundImagePixels[i + 3];
            }
        }
    }
}



