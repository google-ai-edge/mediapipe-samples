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


// import necessary modules and components
import React, { useState, useEffect } from "react";
import DashboardUI from "./DashboardUI";
import { connect } from "react-redux";
import { putInitialze } from '../redux/gesture/gesture.ops';

const Landing = (props) => { // Landing component takes in props

  // Two states declared using `useState` hook: `loaded` is initialized with the value
  // of `props.loaded` and `cameraPermissionAllowed` is initialized with `false`
  const loaded=props.loaded;
  const [cameraPermissionAllowed, setCameraPermissionAllowed] = useState(false);


  // `useEffect` hook is used to check if the camera permission is granted or not
  // and set the state of `cameraPermissionAllowed` accordingly

  useEffect(() => {
    const checkCameraPermission = async () => {
      try {
        await navigator.mediaDevices.getUserMedia({ video: true }); // Request for camera permission
        setCameraPermissionAllowed(true);  // Set the state of `cameraPermissionAllowed` to `true`
      } catch (error) {
        console.error("Camera permission not allowed:", error);
        setCameraPermissionAllowed(false);
      }
    };

    checkCameraPermission(); // Check camera permission when the component is mounted

    // Set a timer of 5.5 seconds to call the `putInitialze`
    const timer = setTimeout(() => {
      props.putInitialze();
    }, 5500);

    return () => clearTimeout(timer);
    // eslint-disable-next-line
  }, []);


  // cleanup function to be called when the component is unmounted 
  if (!loaded) {
    return (
      <div className="absolute top-0 flex flex-col items-center justify-center w-screen h-screen text-white bg-cover" style={{ backgroundImage: "url('/bg_wave.gif')" }}>
      <div className="hidden lg:flex lg:flex-col lg:items-center lg:justify-center lg:w-screen lg:h-screen lg:text-white lg:bg-cover" style={{ backgroundImage: "url('/bg_wave.gif')" }}>
        <img className="w-1/3 pointer-events-none" src="/MainLogo_ATM.png" alt="Logo" />
        <div className="mt-4 text-sm w-1/3 text-center text-emerald-400">Revolutionizing Contactless Interactions</div>
        <div className="mt-4 text-sm w-2/5 text-center text-gray-400">A Proof of Concept Demo Showcasing Touchless Interactions Leveraging Mediapipe's Hand Project. Requires a newer computer. Best experienced in well-lit environments. Ideal on larger screens.</div>
      </div>
      {/* MOBILE PREVIEW */}
      <div className="absolute top-[50%] left-1/2 transform -translate-y-1/2 -translate-x-1/2">
        <div className="w-screen text-center mb-4 text-base lg:hidden">
          <div className="absolute flex justify-center items-center w-3/5 h-3/5 mx-auto -top-64 right-20 animate-pulse">
            <img className="pointer-events-none mb-4 md:hidden" src="/mobileDetect.png" alt="Logo" />
          </div>
          <div className="flex justify-center items-center w-3/5 h-3/5 mx-auto">
            <img className="pointer-events-none mb-4" src="/MainLogo_ATM.png" alt="Logo" />
          </div>
          <div className="text-green-400">
            Best experienced on a big screen or desktop! 😉
          </div>
        </div>
      </div>
        {cameraPermissionAllowed ? (
          <div className="flex items-center mt-2 text-sm w-1/4 hidden lg:flex">
            <div className="absolute top-6 right-28 p-4">Loading Model</div>
            <svg className="absolute top-6 right-24 animate-spin h-5 mt-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="green" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
          </div>
        ) : (
          <div className="flex items-center mt-2 text-sm w-1/4 hidden lg:flex">
            <div className="absolute top-6 right-28 p-4 text-red-400">Grant Camera Access</div>
            <div className="absolute top-10 right-24 pointer-events-none">
              <img src="/sumercamppulse.gif" alt="" style={{ width: 23, height: 23 }} />
            </div>
          </div>
        )}
        <div className="absolute top-2 left-2 p-4 pointer-events-none hidden lg:block">
          <img src="/MP_logo.png" alt="" style={{ width: 180, height: 50 }} />
        </div>
        <div className="absolute bottom-0 w-screen text-center mb-4 text-gray-400 text-sm">We neither collect, store, nor send any data. The video is processed in your browser itself and is GDPR compliant.</div>
      </div>
    );
  } else {
    return <DashboardUI />;
  }
};


// `PropMapFromState` function is used to map the `loaded` state from the redux store to the `loaded` props of the component.
const PropMapFromState = (state) => ({
  loaded: state.hand.loaded,
});

// The `mapDispatchToProps` object is defined to map the `putInitialze` action to the `props` object.
const mapDispatchToProps = {
  putInitialze,
};

// The `Landing` component is exported using the `connect` function from `react-redux` module.
export default connect(PropMapFromState, mapDispatchToProps)(Landing);
