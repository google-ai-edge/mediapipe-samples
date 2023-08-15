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


// This is a React functional component that displays different alerts based 
// on the `gesture`, `finger_locx`, and `loaded` props passed to it. It also 
// listens for a Backspace key press event and triggers the "BACKSPACE" gesture accordingly.


// -----------------------------------------------------------------------------------------


// import the necessery dependencies / modules 
import { useState, useEffect } from "react"; // React hooks
import { connect } from "react-redux"; // `connect` from `react-redux` to connect the component to the Redux store 


// This function takes a msg argument and returns a corresponding message based on the provided msg. 
// It maps different gestures to appropriate messages like "Hovering 🤚," "Grabbing ✊," "Undo Input ✋," or "Clicking 👌."
const messageFrom = (msg) => {
  if (msg === "HOVER") return "Hovering 🤚";
  else if (msg === "GRAB") return "Grabbing ✊"; // horizontal scroll operation TODO
  else if (msg === "BACKSPACE") return "Undo Input ✋";
  else return "Clicking 👌";
};

// The uses the `useState` hook to create two variables: `status` for the current alert status 
// (e.g., "GRAB," "BACKSPACE," "no") 
// and `lastTime` to track the (triggering of) last alert.
function UIalert({ gesture, finger_locx, loaded }) {
  const [status, setStatus] = useState("no");
  const [lastTime, setLastTime] = useState(null);

  // This function listens for a Backspace key press event and triggers the "BACKSPACE" gesture accordingly.
  const handleKeyDown = (event) => {
    if (event.keyCode === 8) {
      // Backspace key pressed, trigger "BACKSPACE" gesture
      setStatus("BACKSPACE");

      const timer = setTimeout(() => {
        setStatus("no");
      }, 500);
      setLastTime(timer);
    }
  };

  useEffect(() => {

    // TESTING PURPOSES ONLY!
    console.log("Gesture:", gesture);
    // console.log("Finger LocX:", finger_locx);
    console.log("Loaded:", loaded);

    if (!loaded) {
      return; // if the `loaded` prop is false, return null i.e. no alert
    }

    if (lastTime) {
      clearTimeout(lastTime); // clear the last alert's timer
    }

    setStatus(gesture); // set the current alert status to the current gesture

    const timer = setTimeout(() => {
      setStatus("no");
    }, 500);
    setLastTime(timer);
    

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown); // remove event listener when component unmounts to prevent memory leaks
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gesture, finger_locx, loaded, setStatus, setLastTime]);


  // Component conditionally render different alerts based on the `status`. 
  if (!loaded) {
    return null;
  } else if (status === "no") {  // status is "no" and loaded is "true" 
    return (
      <div className="absolute top-0 mx-auto w-screen flex items-center justify-center mt-2" role="alert">
        <div>
          <div className=" text-white bg-red-600 rounded px-3 py-4 font-bold animate-pulse">
            ⚠️ Hands out of sight! <span className="ml-2"></span>
          </div>
        </div>
      </div>
    );
  } else if (status === "GRAB") { // status is "GRAB" and loaded is "true"
    return (
      <div className="fixed top-0 left-0 w-full h-full flex items-center justify-center z-50 bg-opacity-40 bg-black">
        <img
          src="/grabModal.png"
          alt=""
          className="w-3/5 h-2.5/5 transition-opacity duration-500 transform -translate-y-2/10 delay-200 ease-in-out"
        />
      </div>
    );
  } else { // display all other status in blue background & corresponding messages
    return (
      <div className="absolute top-0 mx-auto w-screen flex items-center justify-center mt-2">
        <div className="flex items-center text-white text-sm font-bold bg-blue-600 px-4 py-3 rounded" role="alert">
          <svg className="fill-current w-4 h-4 mr-2" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
            <path d="M12 0c-6.627 0-12 5.373-12 12s5.373 12 12 12 12-5.373 12-12-5.373-12-12-12zm-.001 5.75c.69 0 1.251.56 1.251 1.25s-.561 1.25-1.251 1.25-1.249-.56-1.249-1.25.559-1.25 1.249-1.25zm2.001 12.25h-4v-1c.484-.179 1-.201 1-.735v-4.467c0-.534-.516-.618-1-.797v-1h3v6.265c0 .535.517.558 1 .735v.999z" />
          </svg>
          <p>{messageFrom(status)}</p>
        </div>
      </div>
    );
  }
}

// The component is connected to the Redux store using `connect`, 
// mapping `gesture`, `finger_locx`, and `loaded` states to props.
const PropMapFromState = (state) => ({
  gesture: state.hand.gesture,
  finger_locx: state.hand.finger_locx,
  loaded: state.hand.loaded,
});

export default connect(PropMapFromState)(UIalert);
