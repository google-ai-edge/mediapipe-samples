# MediaPipe Decision Maker task for web

## Overview

This web sample evaluates a piece of text against a question, entirely in the
browser. It uses the MediaPipe Tasks Decision library
([`@mediapipe/tasks-decision`](https://www.npmjs.com/package/@mediapipe/tasks-decision))
with the Laya S256 model by default.

The demo supports the three question types of the Decision Maker task:

* **Boolean**: is a condition true for the input text (for example, "The user
  is asking for a refund")?
* **Choice**: which of several options best matches the input text (for
  example, which support team should handle a ticket)?
* **Score**: where does the input text fall on a rubric, from lowest to
  highest level (for example, customer satisfaction)?

It has two modes:

* **Text**: type an input text and a question, then see the answer and the
  probability of each option.
* **Game**: a runner game where each obstacle is described in text, and the
  model makes a Choice decision (jump, duck or wait) to play the game.

You can switch between the CPU and GPU delegates, or upload your own model
(`.task`, `.tflite` or `.litertlm`).

## Prerequisites

* A device that can access the web using Chrome, Firefox, or Safari
* For iOS devices, iOS 16 or later

## Running the demo

Web demos are hosted in the [MediaPipe Sample Web](https://github.com/google-ai-edge/mediapipe-samples-web/) repository.

[View the demo](https://google-ai-edge.github.io/mediapipe-samples-web/#/decision/decision_maker)
