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


// Reusable functional React Component
// Renders a modal with different content based on the showModal and showModal2 props.
// Utilizes the `handleModalInteraction` prop to handle click events when the modal is displayed.

import React from "react";

const ModalC1 = ({ showModal, showModal2, handleModalInteraction }) => { // take three props as inputs
  if (showModal) {
    return (
      <div
        className="fixed top-0 left-0 w-full h-full flex items-center justify-center z-50 bg-opacity-50 bg-black"
        onClick={handleModalInteraction}
      >
        <img
          src="./initialModal.gif"
          alt=""
          className="w-2.5/5 h-2/5 transition-opacity duration-500 pointer-events-none"
        />
      </div>
    );
  } else if (showModal2) {
    return (
      <div
        className="fixed top-0 left-0 w-full h-full flex items-center justify-center z-50 bg-opacity-50 bg-black"
        onClick={handleModalInteraction}
      >
        <img
          src="./nextModal.png"
          alt=""
          className="w-3/5 h-2.5/5 transition-opacity duration-500 pointer-events-none"
        />
      </div>
    );
  } else {
    return null;
  }
};

export default ModalC1;
