navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;

/**
 * Camera class
 * @param videoElement {HTMLVideoElement} the video element to use
 * @param width {number} the width of the video
 * @param height {number} the height of the video
 * @constructor
 * @example
 * const camera = new Camera(document.getElementById('video'));
 * await camera.start();
 * const file = await camera.takePicture();
 * await camera.stop();
 */
export default class Camera {
    constructor(videoElement,
                width=250,
                height=250,
                facingMode='user',
                audio=false) {
        this.width = width;
        this.height = height;
        this.videoElement = videoElement;
        this.stream = null;
        this.facingMode = facingMode;
        this.audio = audio;
        this.isRunning = false;
    }
    async start(deviceId){
        /**
         * Starts the camera
         * @param deviceId {string} the id of the device to use
         * @returns {Promise<void>}
         */
        try {
            await Camera.checkCameraPermission();
            const constraints = {
                audio: this.audio,
                video: {
                    deviceId: deviceId,
                    facingMode: this.facingMode,
                    width: this.width,
                    height: this.height
                }
            };
            this.stream = await navigator.mediaDevices.getUserMedia(constraints);
            this.videoElement.srcObject = this.stream;
            await this.videoElement.play();
            return new Promise((resolve) => {
                this.videoElement.onloadedmetadata = () => {
                    this.isRunning = true;
                    resolve();
                };
            });
        }
        catch (e) {
            throw new Error("Error starting the camera: " + e.message);
        }
    }
    setResolution(width, height){
        /**
         * Sets the camera resolution
         * @param width {number}
         * @param height {number}
         * @returns {Promise<void>}
         */
        this.width = width;
        this.height = height;
    }

    async takePicture(fileName){
        /**
         * Takes a picture from the camera
         * @type {HTMLCanvasElement}
         * @returns {Promise<File>} file
         */
        if (!this.isRunning) {
            const canvas = document.createElement('canvas');
            canvas.width = this.width;
            canvas.height = this.height;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(this.videoElement, 0, 0, this.width, this.height);
            //canvas.toDataURL('image/png'); // base64
            return new Promise((resolve, reject) => {
                return canvas.toBlob((blob) => {
                    let file = new File([blob], fileName, {type: "image/jpeg"})
                    resolve(file);
                });
            })
        }
    }
    static stopStreamTracks(stream){
        /**
         * Stops the stream tracks
         * @returns {Promise<void>}
         */
        return new Promise((resolve, reject) => {
            stream.getTracks().forEach(track => {
                track.stop();
            });
            resolve();
        });
    }

    async stop(){
        /**
         * Stops the camera
         * @returns {Promise<void>}
         */
        await Camera.stopStreamTracks(this.stream);
        this.videoElement.srcObject = null;
        this.isRunning = false;
    }
    static async getCameraPermissionState(){
        /**
         * Checks if the user has given permission to use the camera
         * @returns {Promise<boolean>} hasPermission
         */
        try {
            const res = await navigator.permissions.query({name:'camera'})
            return res.state;
        }
        catch (e) {
            return false;
        }
    }
    static  async askForPermission(){
        /**
         * Asks for permission to use the camera
         * @returns {Promise<void>}
         */
        try {
            // ask for permission
            const stream = await navigator.mediaDevices.getUserMedia({video: true});
            // release the stream tracks after asking for permission to avoid the camera light to stay on
            await Camera.stopStreamTracks(stream);
        }
        catch (e) {
            throw new Error("error asking for permission");
        }
    }

    static async checkCameraPermission(){
        /**
         * Checks if the user has given permission to use the camera
         * @type {"denied"|"granted"|"prompt"|boolean|undefined}
         */
        const cameraPermissionState = await Camera.getCameraPermissionState();
        console.log(cameraPermissionState);
        if (cameraPermissionState === 'prompt'){
            await Camera.askForPermission();
        }
        else if (cameraPermissionState === 'denied'){
            throw new Error("the camera permission has been denied, make sure to allow it in the browser settings");
        }
    }

    static async devicesList() {
        /**
         * Returns a list of the available devices
         * @returns {Array} devices
         */
        try{
            let devices = [];
            await Camera.checkCameraPermission();
            const devicesList = await navigator.mediaDevices.enumerateDevices();
            for (let i = 0; i !== devicesList.length; ++i) {
                const deviceInfo = devicesList[i];
                if (deviceInfo.kind === 'videoinput'){
                    devices.push({
                        "id" : deviceInfo.deviceId,
                        "label": deviceInfo.label || `camera ${i}`
                    });
                }
            }
            return devices;
        }
        catch (e) {
            throw new Error("error listing the devices: " + e.message);
        }
    }
    static isSupported(){
        /**
         * Returns true if the browser supports getUserMedia
         * @returns {boolean} isSupported
         */
        return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
    }
}