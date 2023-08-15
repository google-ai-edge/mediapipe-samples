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


// A reducer function that handles state updates based on dispatched actions

// import action types
import { GEST_ASSIGN, FINLOCK_ASSIGN, INIT_ASSIGN } from "./gesture.forms";

// define `initialState` object for Redux store, containing these three props
const initialState = {
  gesture: null, finger_locx: null, loaded: false,
};

const gestureHandler = (state = initialState, action) => { // `state` is current state of Redux store, `action` is dispatched action
  switch (action.type) {
    case GEST_ASSIGN:
      return {
        ...state,
        gesture: action.payload,
      };
    case FINLOCK_ASSIGN:
      return {
        ...state,
        finger_locx: action.payload,
      };
    case INIT_ASSIGN:
      return {
        ...state,
        loaded: true,
      };
    default:
      return state;
  }
};

// export the reducer function
export default gestureHandler;
