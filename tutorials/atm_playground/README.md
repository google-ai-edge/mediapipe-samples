# MediaPipe ATM Playground Demo 🏧 

An Interactive (Proof of Concept) Web Demo showcasing Touchless Interactions using the [MediaPipe](https://developers.google.com/mediapipe/) Machine Learning Library. 


<p align="center">
<img src="https://user-images.githubusercontent.com/48355572/260814196-59795e6f-196d-4b97-b0cd-40cec4d365df.png" alt="cover-img">
</p>


This project aims to test and demonstrate the capabilities of the new MediaPipe [Hand Landmarker](https://developers.google.com/mediapipe/api/solutions/js/tasks-vision.handlandmarker) task from MediaPipe Solutions, which outputs 21 hand landmarks. The task provides precise and accurate hand landmark detection, generating [21](https://developers.google.com/mediapipe/solutions/vision/hand_landmarker#models) key points on the hand. These landmarks are utilized in this interactive web app which enables users to perform contactless interactions with the interface using simple human gestures. Best experienced in well-lit environments. Ideal on larger screens.

> ⓘ All data taken via input video feed is deleted after returning inference and is computed directly on the client side, making it GDPR compliant.


<br/>

<p align="center">
<img src="https://user-images.githubusercontent.com/48355572/260842692-34bcee72-228a-4b24-84be-146c4973bd18.gif" alt="demo-gif">
</p>

<br/>


## Prerequisites
This project requires [Node.js](https://nodejs.org/en/download) to be installed on your local machine.

> ⚠️ Webcam required for hand detection and gesture recognition. Please ensure your device has a functioning webcam.


<br/>


## Installation

1. Clone the repository on your local machine:
    ```sh
    git clone https://github.com/googlesamples/mediapipe.git
    ```

2. Navigate into the project directory:
    ```sh
    cd tutorials/atm_playground
    ```

3. Install the necessary dependencies:
    ```sh
    npm install
    ```

4. Start the development server:
    ```sh
    npm start
    ```

5. Open the project in your browser at [`http://localhost:3000`](http://localhost:3000) to view your project.


<br/>


> 🚀 View a _live demo_ in your browser [**here.**](https://atm-playground.netlify.app) 


<br/>


## Built With
This project was created using:

- [React](https://react.dev/)
- [Tailwind CSS](https://tailwindcss.com/)
- [MediaPipe Hand Landmarker](https://developers.google.com/mediapipe/api/solutions/js/tasks-vision.handlandmarker)
- [Redux](https://redux.js.org/)
- [React Redux](https://react-redux.js.org/)
- [PostCSS](https://postcss.org/)
- [React Toastify](https://github.com/fkhadra/react-toastify/)
- [React Confetti](https://github.com/alampros/react-confetti/)
- [Figma](https://www.figma.com/)


<br/>


## License
Distributed under the Apache License 2.0. See [`LICENSE`](./LICENSE) for more information.