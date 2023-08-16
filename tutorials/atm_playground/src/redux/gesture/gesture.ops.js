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


// Action creator functions to create actions with specific types and payload data to dispatch to the reducer.
// Action creators are functions that return an action object with a type and payload property.

// import action types
import { GEST_ASSIGN, FINLOCK_ASSIGN, INIT_ASSIGN } from "./gesture.forms";

export const putGesture = (gesture) => ({
  type: GEST_ASSIGN,
  payload: gesture,
});

export const putFingLock = (locs) => ({
  type: FINLOCK_ASSIGN,
  payload: locs,
});

export const putInitialze = () => ({
  type: INIT_ASSIGN,
});
