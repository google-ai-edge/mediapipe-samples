// Copyright 2023 The MediaPipe Authors.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

//      http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// ---------------------------------------------------------------------------------------- //


// This is the brain of the app.

// First we import the necessary dependencies
// including `useRef`, `useEffect` from React
// `FilesetResolver` and `HandLandmarker` from `@mediapipe/tasks-vision`,
// `connect` from `react-redux`, and some custom actions and utilities from
// `../redux/gesture/gesture.ops` and `../utils/allGesture`

import { useRef, useEffect } from 'react';
import { FilesetResolver, HandLandmarker } from '@mediapipe/tasks-vision';
import { connect } from 'react-redux';
import { putGesture, putFingLock, putInitialze } from '../redux/gesture/gesture.ops';
import { rightHandGestures, leftHandGestures } from '../utils/allGesture';

// here three props are taken as input → putGesture, putFingLock, and putInitialze. 
// We also create a canvasRef using the useRef hook to reference the canvas element.
function Kernel({ putGesture, putFingLock, putInitialze }) {
  const canvasRef = useRef(null);

  // `useEffect` is used  to detect hand landmarks and draw them on the canvas using 
  // the `drawLandmarksAndConnectors` function.
  useEffect(() => {
    const drawLandmarksAndConnectors = (landmarks, ctx) => {

      // Draw connectors (Reference: https://developers.google.com/mediapipe/solutions/vision/hand_landmarker#models)
      // define keypoints
      const connections = [
        [0, 1], [1, 2], [2, 3], [3, 4], // Thumb
        [0, 5], [5, 6], [6, 7], [7, 8], // Index finger
        [5, 9], [9, 10], [10, 11], [11, 12], // Middle finger
        [9, 13], [13, 14], [14, 15], [15, 16], // Ring finger
        [13, 17], [17, 0], [17, 18], [18, 19], [19, 20], // Little finger
      ];

      ctx.strokeStyle = 'white'; // define stroke color
      ctx.lineWidth = 4; // define stroke width

      // draw connectors
      for (const connection of connections) {
        const [index1, index2] = connection;

        // calculate the pixel positions of the landmarks using the normalized x and y 
        // values and draw the lines using ctx.stroke()
        ctx.beginPath();
        ctx.moveTo(landmarks[index1].x * canvasRef.current.width, landmarks[index1].y * canvasRef.current.height);
        ctx.lineTo(landmarks[index2].x * canvasRef.current.width, landmarks[index2].y * canvasRef.current.height);
        ctx.stroke();
      }

      // Draw landmarks
      ctx.fillStyle = 'teal';
      for (const landmark of landmarks) {
        ctx.beginPath();
        ctx.arc(landmark.x * canvasRef.current.width, landmark.y * canvasRef.current.height, 7, 0, 2 * Math.PI);
        ctx.fill();
      }
    };

    const loadModelAndStartDetection = async () => {
      // load from CDN
      const vision = await FilesetResolver.forVisionTasks('https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.2/wasm');
      // create an instance of the HandLandmarker class with the specified options
      const handLandmarker = await HandLandmarker.createFromOptions(vision, { 
        // baseOptions → an object containing configuration options for the hand landmarker
        baseOptions: {
          modelAssetPath: 'https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task',
          delegate: 'GPU', // specify the execution backend
        },
        runningMode: 'IMAGE' || 'VIDEO',   // Image first then video (always!)
        numHands: 1,
        minHandDetectionConfidence: 0.6,
        minHandPresenceConfidence: 0.6,
        // minHandTrackingConfidence: 0.5,      // this is set by default
      });

      const cnvs = canvasRef.current;  // cnvs variable is used to reference the canvas element
      const ctx = cnvs.getContext('2d');  // ctx → 2D rendering context for the canvas
      const vidElm = document.createElement('video'); // newly created video element

      // start the camera and detect hand landmarks & continuously update the canvas with the video stream
      const startCamera = async () => {
        try {
          const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false }); // request access to get the video stream
          vidElm.srcObject = stream; // set the video stream as the source of the video element
          vidElm.setAttribute('autoplay', '');     // iOS fix (maybe?)
          vidElm.setAttribute('playsinline', '');  // iOS fix (maybe?)
          await vidElm.play(); // awaits the video to start playing

          const detectLandmarks = async () => {
            try {
              const results = handLandmarker.detect(vidElm); // detect hand landmarks from video stream, & store in results
              const landmarks = results?.landmarks; // extract the landmarks from the results
              const handType = (results?.handednesses[0]?.[0]?.categoryName) === "Left" ? "Right" : "Left"; // check if the detected hand is right/left

              // Clear canvas before drawing (if landmarks are detected)
              ctx.clearRect(0, 0, cnvs.width, cnvs.height);

              if (landmarks && landmarks.length > 0) {
                ctx.drawImage(vidElm, 0, 0, cnvs.width, cnvs.height); // Draw video frame

                console.log(handType);

                if (handType === 'Right') {
                  drawLandmarksAndConnectors(landmarks[0], ctx);
                  putGesture(rightHandGestures(landmarks[0]));
                } else if (handType === 'Left') {
                  drawLandmarksAndConnectors(landmarks[0], ctx);
                  putGesture(leftHandGestures(landmarks[0]));
                }

                putFingLock(landmarks[0]);
              } else {
                // If hand landmarks are not detected, still draw the video frame (IMPORTANT!)
                ctx.drawImage(vidElm, 0, 0, cnvs.width, cnvs.height);
              }
            } catch (error) {
              console.error('Error detecting landmarks: ', error);
            }

            requestAnimationFrame(detectLandmarks);
          };

          detectLandmarks(); 
        } catch (error) {
          console.error('Error accessing the camera: ', error);
        }
      };

      startCamera(); // start the camera
    };

    loadModelAndStartDetection();
  }, [putGesture, putFingLock, putInitialze]);  // ensure that detection and canvas rendering are updated when the gesture & finger state updates

  // 2nd `useEffect` is used to set the canvas size to the window size and update it when the window is resized.
  useEffect(() => {
    const setCanvasSize = () => {
      canvasRef.current.height = window.innerHeight;
      canvasRef.current.width = window.innerWidth;
    };

    setCanvasSize();
    window.addEventListener('resize', setCanvasSize);

    return () => {
      window.removeEventListener('resize', setCanvasSize); // remove the event listener when the component unmounts to prevent memory leaks
    };
  }, []);

  // return a div containing the canvas element
  // canvas element reference is set to the canvasRef using the ref attribute
  return (
    <div className="absolute top-0 filter filter-grayscale-80 opacity-10">
      <canvas className="transform scale-x-minus-1" ref={canvasRef} />
    </div>
  );
}

// `mapDispatchToProps` is used to dispatch the actions to the store
const mapDispatchToProps = {
  putGesture,
  putFingLock,
  putInitialze,
};

export default connect(null, mapDispatchToProps)(Kernel);
