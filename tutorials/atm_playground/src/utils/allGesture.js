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

// This is the logic for the gesture recognition.
// Takes in parameter input from MP hand_landmarker which is an array of 
// 21 landmarks representing the coordinates of hand keypoints

// Function for right hand gestures
function rightHandGestures(landmarks) {
  const [thumbIsOpen, indexIsOpen, middleIsOpen, ringIsOpen, littleIsOpen] = [   // Values assigned to corresponding fingers 
      landmarks[3].x < landmarks[2].x && landmarks[4].x < landmarks[2].x,
      landmarks[7].y < landmarks[6].y && landmarks[8].y < landmarks[6].y,
      landmarks[11].y < landmarks[10].y && landmarks[12].y < landmarks[10].y,
      landmarks[15].y < landmarks[14].y && landmarks[16].y < landmarks[14].y,
      landmarks[19].y < landmarks[17].y && landmarks[18].y < landmarks[17].y
    ];
  
    // Above is set to true by default if x-coordinate of finger tip is less than x-coordinate of finger base (else false)
    // Reference: https://github.com/google/mediapipe/blob/master/docs/solutions/hands.md#hand-landmark-model
  
    if (!thumbIsOpen && !indexIsOpen && !middleIsOpen && !ringIsOpen && !littleIsOpen) {
      return "GRAB";
    } else if (Math.sqrt(Math.pow(landmarks[4].x - landmarks[8].x, 2) + Math.sqrt(Math.pow(landmarks[4].y - landmarks[8].y, 2))) < 0.25) {  // Euclidean distance between the tip of the index finger and the tip of the thumb
      return "CLICK";
    } else if (thumbIsOpen && indexIsOpen && middleIsOpen && ringIsOpen && littleIsOpen && landmarks[0].y > landmarks[12].y) {
      return "BACKSPACE";
    } else {
      return "HOVER";
    }
}

// Function for left hand gestures
function leftHandGestures(landmarks) {
  const [thumbIsOpen, indexIsOpen, middleIsOpen, ringIsOpen, littleIsOpen] = [   // Values assigned to corresponding fingers 
      landmarks[3].x < landmarks[2].x && landmarks[4].x < landmarks[2].x,
      landmarks[7].y < landmarks[6].y && landmarks[8].y < landmarks[6].y,
      landmarks[11].y < landmarks[10].y && landmarks[12].y < landmarks[10].y,
      landmarks[15].y < landmarks[14].y && landmarks[16].y < landmarks[14].y,
      landmarks[19].y < landmarks[17].y && landmarks[18].y < landmarks[17].y
    ];
  
    // Above is set to true by default if x-coordinate of finger tip is less than x-coordinate of finger base (else false)
    // Reference: https://github.com/google/mediapipe/blob/master/docs/solutions/hands.md#hand-landmark-model
  
    if (!thumbIsOpen && !indexIsOpen && !middleIsOpen && !ringIsOpen && !littleIsOpen) {
      return "GRAB";
    } else if (Math.sqrt(Math.pow(landmarks[4].x - landmarks[8].x, 2) + Math.sqrt(Math.pow(landmarks[4].y - landmarks[8].y, 2))) < 0.25) {  // Euclidean distance between the tip of the index finger and the tip of the thumb
      return "CLICK";
    } else if (thumbIsOpen && indexIsOpen && middleIsOpen && ringIsOpen && littleIsOpen && landmarks[0].y > landmarks[12].y) {
      return "HOVER";
    } else {
      return "BACKSPACE";
    }
}

// Export the functions (for both left and right hand gestures)
export { rightHandGestures, leftHandGestures };
