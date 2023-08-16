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


// This functional component renders the cursor element on the screen and 
// displays a certain background color and border based on the provided 
// gesture from the Redux store. The cursor's position is updated according 
// to the finger_locx data, and it disappears after a short delay.

// -----------------------------------------------------------------------------------------

// import the necessery dependencies / modules 
import { useRef, useEffect } from "react";
import { connect } from "react-redux";

// We define a `cursorRef` using `useRef` to reference the cursor element.
function CursorTip(props) {
  const cursorRef = useRef(null);

  // We use `useEffect` to update the cursor's position & visibility on the screen
  // based on the `props.finger_locx` provided from the Redux store.
  useEffect(() => {
    if (!props.finger_locx) return;
  
    // The cursor's position and visibility are controlled by the values in `props.finger_locx`. 
    // We use index finger's x and y coordinates to calculate its position relative to the window size. 
    // When `props.finger_locx` is available, the cursor is displayed and 
    // disappears after 500 milliseconds using `setTimeout`.
    const cursorStyle = cursorRef.current.style;
    cursorStyle.display = "flex";
    cursorStyle.left = `${window.innerWidth - props.finger_locx[8].x * window.innerWidth}px`;
    cursorStyle.top = `${props.finger_locx[8].y * window.innerHeight}px`;
  
    const interval = setTimeout(() => {
      cursorStyle.display = "none";
    }, 500);
  
    return () => clearTimeout(interval);
  }, [props.finger_locx]);


  // We set the background (bg) and showBorder based on the props.gesture provided from the Redux store. 
  let bg = "bg-white";
  let showBorder = false;

  if (props.gesture) {
    const firstCharacter = props.gesture[0];
    if (firstCharacter === "C") {
      bg = "bg-green-500";
      showBorder = true;
    } else if (firstCharacter === "G") {
      bg = "bg-blue-500";
      showBorder = true;
    } else if (firstCharacter === "B") {
      bg = "bg-red-500";
      showBorder = true;
    }
  }

  // We return the cursor element with the appropriate styling based on the `bg` and `showBorder` values.
  return (
    <div className={`absolute w-10 h-10 text-xl rounded-full z-50 hidden items-center justify-center font-bold ${bg}`} ref={cursorRef}>
      {props.gesture && props.gesture[0]}
      {showBorder && <div className="border-2 border-dashed border-gray-200 rounded-full absolute -inset-2 animate-spin" style={{ animationDuration: "4s" }}></div>}
    </div>
  );
}

// & finally we use `connect` to connect the component to the Redux store,
// by mapping the `gesture` and `finger_locx` states to props.
const PropMapFromState = (state) => ({
  gesture: state.hand.gesture,
  finger_locx: state.hand.finger_locx,
});

export default connect(PropMapFromState)(CursorTip);
