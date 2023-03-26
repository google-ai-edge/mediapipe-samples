import {fetchImage} from "./utils.js";

export async function createPortrait(photo, border = 20, footerOffset = 40, cornerRadius = 10, logoUrl = "mediapipe-logo.png") {
    /**
     * Creates a portrait from the given photo
     * @param photo {ImageData} The photo to use
     * @param canvas {HTMLCanvasElement} The canvas to draw the portrait on
     * @param border {number} The border width
     * @param footerOffset {number} The footer offset
     * @param cornerRadius {number} The corner radius
     * @param logoUrl {string} The logo url to use for the portrait
     * @returns {Promise<string>} The portrait as a data url
     */
    // create a canvas to draw the portrait on
    const portraitCanvas = document.createElement('canvas');
    const  portraitCtx = portraitCanvas.getContext('2d');
    const portraitWidth = photo.width;
    const portraitHeight = photo.height;
    portraitCanvas.width = portraitWidth;
    portraitCanvas.height = portraitHeight;


    // apply filter and border to the portrait
    photo = addGradientFilter(photo);
    photo = await addBorder(photo, border, footerOffset, cornerRadius);

    // draw the portrait on the canvas
    const photoBitmap = await createImageBitmap(photo)
    portraitCtx.drawImage(photoBitmap, 0, 0);

    // add media pipe logo to the portrait
    const logo = await fetchImage(logoUrl);
    const logoBitmap = await createImageBitmap(logo)
    portraitCtx.drawImage(logoBitmap, photoBitmap.width - logo.width / 2, photoBitmap.height - logo.height / 2 - 10, logo.width / 2, logo.height / 2);

    return portraitCanvas.toDataURL('image/png', 1.0);

}

function addGradientFilter (photo) {
    /**
     * Applies a filter to the given photo
     * @type {ImageData}
     */
    // create a gradient to use as a filter
    const gradient = createGradientFilter(photo.width, photo.height);
    // apply filter and border to the portrait
    return applyTransform(gradient, photo, (backgroundPixel, foregroundPixel) => {
        // https://en.wikipedia.org/wiki/Blend_modes#Screen
        return 255 - (255 - backgroundPixel) * (255 - foregroundPixel) / 255;
    });
}

function applyTransform (backgroundImage, foregroundImage, transformFunction, alpha = 255) {
    /**
     * Blends the foreground image with the background image using the given transform function
     * @param backgroundImage {ImageData} The background image
     * @param foregroundImage {ImageData} The foreground image
     * @param transformFunction {function} The transform function to use
     * @param alpha {number} The alpha value to use
     * @returns {ImageData} The blended image
     */
    const pixelsCount = backgroundImage.width * backgroundImage.height * 4;
    const backgroundPixels = backgroundImage.data;
    const foregroundPixels = foregroundImage.data;

    const resultPixels = new Uint8ClampedArray(pixelsCount);
    for (var i = 0; i < pixelsCount; i += 4) {
        resultPixels[i+0] = transformFunction(backgroundPixels[i+0], foregroundPixels[i+0]);
        resultPixels[i+1] = transformFunction(backgroundPixels[i+1], foregroundPixels[i+1]);
        resultPixels[i+2] = transformFunction(backgroundPixels[i+2], foregroundPixels[i+2]);
        resultPixels[i+3] = alpha;
    }
    return new ImageData(resultPixels, backgroundImage.width, backgroundImage.height);
}

async function addBorder(imageData, imageOffset = 20, footerOffset = 90, cornerRadius = 10){
    /**
     * Adds a border to the given image
     * @param imageData {ImageData} The image to add the border to
     * @param imageOffset {number} The image offset
     * @param footerOffset {number} The footer offset
     * @param cornerRadius {number} The corner radius
     * @returns {Promise<ImageData>} The image with the border
     */
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');

    canvas.width = imageData.width;
    canvas.height = imageData.height;

    // set corner radius and rectangle dimensions
    const rectWidth = canvas.width;
    const rectHeight = canvas.height;

    // top left corner
    const x1 = cornerRadius + imageOffset;
    const y1 = cornerRadius + imageOffset;

    // top right corner
    const x2 = rectWidth - cornerRadius - imageOffset;
    const y2 = cornerRadius + imageOffset;

    // bottom right corner
    const x3 = rectWidth - cornerRadius - imageOffset ;
    const y3 = rectHeight - cornerRadius - imageOffset - footerOffset;

    // bottom left corner
    const x4 = cornerRadius + imageOffset;
    const y4 = rectHeight - cornerRadius - imageOffset - footerOffset;

    // draw image with curved corners
    ctx.fillStyle = "white";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.arcTo(x2, y2, x3, y3, cornerRadius);
    ctx.arcTo(x3, y3, x4, y4, cornerRadius);
    ctx.arcTo(x4, y4, x1, y1, cornerRadius);
    ctx.arcTo(x1, y1, x2, y2, cornerRadius);
    ctx.closePath();
    ctx.clip();

    const imageBitmap = await createImageBitmap(imageData)
    ctx.drawImage(imageBitmap, 0, 0);

    return ctx.getImageData(0, 0, canvas.width, canvas.height)

}

function createGradientFilter (width, height) {
    /**
     * Creates a gradient filter
     * @param width : width of the gradient
     * @param height : height of the gradient
     * @returns {ImageData} The gradient
     */
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    canvas.width = width;
    canvas.height = height;
    // Fill a Radial Gradient
    // https://developer.mozilla.org/en-US/docs/Web/API/CanvasRenderingContext2D/createRadialGradient
    const gradient = ctx.createRadialGradient(width / 2, height / 2, 0, width / 2, height / 2, width * 0.6);
    gradient.addColorStop(0, "#804e0f");
    gradient.addColorStop(1, "#3b003b");
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, width, height);
    return ctx.getImageData(0, 0, width, height);
}




