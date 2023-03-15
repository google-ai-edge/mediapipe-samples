# Segmenting the background using the MediaPipe Image Segmentation Task

The MediaPipe Segmentation Task provides a powerful and easy-to-use API for image segmentation on Videos and Images using JavaScript.Thanks to its web assembly backend, the `segmentForVideo`(for videos) and `segment`(for images) functions can quickly(nearly in real-time) classify each pixel in the image into multiple classes, such as people, dogs, cats, cows, etc. In this demo, the API was used to separate the background and the foreground (persons) to create a similar effect to that used in video streaming communication tools like Google Meet and Zoom.

# How to run the web application?

To run this demo, open the `index.html` file. Since the MediaPipe Segmentation API runs at the frontend, not backend is required.

# Project Layout

All the code for this demo is located within the `code` folder.

- `index.html` - Entrypoint of the application, contains all the HTML web page's visual elements(DOM Elements).
- `index.js` in this file, all the code that uses the MediaPipe Image Segmentation API to perform the segmentation can be found
- `camera.js` - This class is used to interact and manipulate the webcam using the HTML5 API.
- `utils.js` - Some utils, such as `downloadImage` and `scaleImageData,` have been implemented here.

# Web interface

![picture](assets/ui.png)



