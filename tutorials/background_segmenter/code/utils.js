export function resizeImageData(imageData, newWidth, newHeight) {
    /**
     * resize an image data object
     * @param imageData {ImageData} The image data to scale
     * @param newWidth {number} The new width
     * @param newHeight {number} The new height
     * @returns {ImageData} The scaled image data
     */
        // Create a temporary canvas to hold the original image data
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    canvas.width = imageData.width;
    canvas.height = imageData.height;
    ctx.putImageData(imageData, 0, 0);
    // Create a new canvas to hold the scaled image data
    const newCanvas = document.createElement('canvas');
    const newCtx = newCanvas.getContext('2d');
    newCanvas.width = newWidth;
    newCanvas.height = newHeight;
    // Scale the image by drawing it onto the new canvas
    newCtx.drawImage(canvas, 0, 0, imageData.width, imageData.height, 0, 0, newWidth, newHeight);
    // Get the scaled image data from the new canvas
    return newCtx.getImageData(0, 0, newWidth, newHeight);
}

export async function fetchImage(imageUrl){
    /**
     * fetch an image and return its ImageData
     * @param imageUrl
     * @param targetWidth
     * @param targetHeight
     * @returns {Promise<ImageData>}
     */
    const image = await fetch(imageUrl)
    const imageBlog = await image.blob()
    imageUrl = URL.createObjectURL(imageBlog)
    const imageElement = document.createElement('img')
    imageElement.src = imageUrl
    return new Promise((resolve, reject) => {
        imageElement.onload = () => {
            const canvas = document.createElement('canvas')
            const ctx = canvas.getContext('2d')
            canvas.width = imageElement.width;
            canvas.height = imageElement.height;
            ctx.drawImage(imageElement, 0, 0, imageElement.width, imageElement.height)
            const imageData = ctx.getImageData(0, 0, imageElement.width, imageElement.height)
            resolve(imageData)
        }
        imageElement.onerror = () => {
            reject(new Error('Error downloading the image, trying with another one'))
        }
    });
}
export async function fetchImageAsElement(imageUrl){
    /**
     * fetch an image and return its ImageData
     * @param imageUrl
     * @param targetWidth
     * @param targetHeight
     * @returns {Promise<ImageData>}
     */
    const image = await fetch(imageUrl)
    const imageBlog = await image.blob()
    imageUrl = URL.createObjectURL(imageBlog)
    const imageElement = document.createElement('img')
    imageElement.src = imageUrl
    return new Promise((resolve, reject) => {
        imageElement.onload = () => {
            resolve(imageElement)
        }
        imageElement.onerror = () => {
            reject(new Error('Error downloading the image, trying with another one'))
        }
    });
}