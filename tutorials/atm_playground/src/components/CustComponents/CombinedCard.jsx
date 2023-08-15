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


// This is a React functional component that renders a customizable card 
// element with different styles based on the provided props. It also checks for hover 
// and click events to trigger specific actions.

// -----------------------------------------------------------------------------------------

// We import necessary dependencies, including `useRef` and `useState` from React, 
// as well as `connect` from `react-redux` for connecting to the Redux store.
import { useRef, useState } from "react";
import { connect } from "react-redux";

// `actionPose` is a utility function to calculate the position 
// of an element relative to the document's top-left corner.
function actionPose(el) {
  for (var lx = 0, ly = 0; el != null; lx += el.offsetLeft, ly += el.offsetTop, el = el.offsetParent);
  return { x: lx, y: ly };
}

// This main `CombinedCard` function component receives props as its argument.
function CombinedCard(props) {
  // We use `useRef` to create a reference to the card element.
  const cardRef = useRef(null);
  // We use `useState` to create a state variable `lastClicked` and its setter `setLastClicked`.
  const [lastClicked, setLastClicked] = useState(Date.now());

  // `isHovering` is a function that checks if the card is being hovered over by the user's hand.
  // It's obtained from the `props.finger_locx` array, which contains the coordinates of the user's hand.
  // It calculates the position of the card and compares it with the finger location to determine if it's hovering.
  const isHovering = () => {
    if (!props.finger_locx) return false;
    const pos = actionPose(cardRef.current);
    if (pos.x === 0) return false;

    const hpos = {
      x: window.innerWidth - props.finger_locx[8].x * window.innerWidth,
      y: props.finger_locx[8].y * window.innerHeight
    };

    if (
      pos.x <= hpos.x &&
      hpos.x <= pos.x + cardRef.current.offsetWidth - 12 &&
      pos.y <= hpos.y &&
      hpos.y <= pos.y + cardRef.current.offsetHeight - 12
    )
      return true;
    else return false;
  };

  // When the user hovers over the card and clicks, the component checks if the Redux store 
  // has the "CLICK" gesture and an onClick prop. If both are present, and the card was not clicked 
  // in the last second, it triggers the onClick function and updates the lastClicked state.
  if (isHovering() && props.gesture === "CLICK" && props.onClick && Date.now() - 1000 > lastClicked) {
    props.onClick();
    setLastClicked(Date.now());
  }

  // We set `classesName` and `bgClass` based on the type prop provided. 
  let classesName, bgClass;

  switch (props.type) {
    case 'default':
      classesName = 'w-64 rounded border-yellow-400';
      bgClass = 'bg-yellow-200 bg-opacity-30';
      break;
    case 'card2':
      classesName = 'w-128 rounded-xl border-green-400';
      bgClass = 'border-green-400 bg-green-300 bg-opacity-30 hover-gradient-green';
      break;
    case 'card2b':
      classesName = 'w-128 rounded-xl border-red-400';
      bgClass = 'border-red-400 bg-red-300 bg-opacity-30 hover-gradient-red';
      break;
    case 'card3':
      classesName = 'w-64 rounded border-yellow-400';
      bgClass = 'bg-violet-400 bg-opacity-30 transform transition duration-500 scale-105';
      break;
    case 'card4':
      classesName = 'w-128 rounded-xl border-emerald-400';
      bgClass = 'bg-green-200 bg-opacity-30 transform transition duration-500 scale-125';
      break;
    case 'card5':
      classesName = 'w-64 rounded border-blue-500';
      bgClass = 'bg-violet-400 bg-opacity-30 transform transition duration-500 scale-105';
      break;
    default:
      classesName = '';
      bgClass = '';
      break;
  }

  // Finally, we return the card element with the appropriate classes and styles.
  // cardRef is used to reference the element, and adds various classes based 
  // on the `type`, `isHovering`, and additional `className` props provided.
  return (
    <div
      ref={cardRef}
      className={`border-2 p-4 ${classesName} ${isHovering() ? bgClass : ""} ${
        props.className ? ` ${props.className}` : ""
      }`}
    >
      {/* Here the `children` prop is used to render child elements inside the card. */}
      {props.children}
    </div>
  );  
}

// The `mapStateToProps` function connects the component to the Redux store, 
// providing access to the `gesture` and `finger_locx` states as props.
const mapStateToProps = (state) => ({
  gesture: state.hand.gesture,
  finger_locx: state.hand.finger_locx,
});

// & finally we export the component by connecting it to the Redux store.
export default connect(mapStateToProps)(CombinedCard);
