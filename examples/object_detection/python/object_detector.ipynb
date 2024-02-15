{
  "cells": [
    {
      "cell_type": "markdown",
      "metadata": {
        "id": "h2q27gKz1H20"
      },
      "source": [
        "##### Copyright 2023 The MediaPipe Authors. All Rights Reserved."
      ]
    },
    {
      "cell_type": "code",
      "execution_count": null,
      "metadata": {
        "cellView": "form",
        "id": "TUfAcER1oUS6"
      },
      "outputs": [],
      "source": [
        "#@title Licensed under the Apache License, Version 2.0 (the \"License\");\n",
        "# you may not use this file except in compliance with the License.\n",
        "# You may obtain a copy of the License at\n",
        "#\n",
        "# https://www.apache.org/licenses/LICENSE-2.0\n",
        "#\n",
        "# Unless required by applicable law or agreed to in writing, software\n",
        "# distributed under the License is distributed on an \"AS IS\" BASIS,\n",
        "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n",
        "# See the License for the specific language governing permissions and\n",
        "# limitations under the License."
      ]
    },
    {
      "cell_type": "markdown",
      "metadata": {
        "id": "L_cQX8dWu4Dv"
      },
      "source": [
        "# Object Detection with MediaPipe Tasks\n",
        "\n",
        "This notebook shows you how to use MediaPipe Tasks Python API to detect objects in images."
      ]
    },
    {
      "cell_type": "markdown",
      "metadata": {
        "id": "O6PN9FvIx614"
      },
      "source": [
        "## Preparation\n",
        "\n",
        "Let's start with installing MediaPipe."
      ]
    },
    {
      "cell_type": "code",
      "execution_count": null,
      "metadata": {
        "id": "gxbHBsF-8Y_l"
      },
      "outputs": [],
      "source": [
        "!pip install -q mediapipe"
      ]
    },
    {
      "cell_type": "markdown",
      "metadata": {
        "id": "a49D7h4TVmru"
      },
      "source": [
        "Then download an off-the-shelf model. Check out the [MediaPipe documentation](https://developers.google.com/mediapipe/solutions/vision/object_detector#models) for more image classification models that you can use."
      ]
    },
    {
      "cell_type": "code",
      "execution_count": null,
      "metadata": {
        "id": "OMjuVQiDYJKF"
      },
      "outputs": [],
      "source": [
        "!wget -q -O efficientdet.tflite -q https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite"
      ]
    },
    {
      "cell_type": "markdown",
      "metadata": {
        "id": "89BlskiiyGDC"
      },
      "source": [
        "## Visualization utilities"
      ]
    },
    {
      "cell_type": "code",
      "execution_count": null,
      "metadata": {
        "cellView": "form",
        "id": "H4aPO-hvbw3r"
      },
      "outputs": [],
      "source": [
        "#@markdown We implemented some functions to visualize the object detection results. <br/> Run the following cell to activate the functions.\n",
        "import cv2\n",
        "import numpy as np\n",
        "\n",
        "MARGIN = 10  # pixels\n",
        "ROW_SIZE = 10  # pixels\n",
        "FONT_SIZE = 1\n",
        "FONT_THICKNESS = 1\n",
        "TEXT_COLOR = (255, 0, 0)  # red\n",
        "\n",
        "\n",
        "def visualize(\n",
        "    image,\n",
        "    detection_result\n",
        ") -> np.ndarray:\n",
        "  \"\"\"Draws bounding boxes on the input image and return it.\n",
        "  Args:\n",
        "    image: The input RGB image.\n",
        "    detection_result: The list of all \"Detection\" entities to be visualize.\n",
        "  Returns:\n",
        "    Image with bounding boxes.\n",
        "  \"\"\"\n",
        "  for detection in detection_result.detections:\n",
        "    # Draw bounding_box\n",
        "    bbox = detection.bounding_box\n",
        "    start_point = bbox.origin_x, bbox.origin_y\n",
        "    end_point = bbox.origin_x + bbox.width, bbox.origin_y + bbox.height\n",
        "    cv2.rectangle(image, start_point, end_point, TEXT_COLOR, 3)\n",
        "\n",
        "    # Draw label and score\n",
        "    category = detection.categories[0]\n",
        "    category_name = category.category_name\n",
        "    probability = round(category.score, 2)\n",
        "    result_text = category_name + ' (' + str(probability) + ')'\n",
        "    text_location = (MARGIN + bbox.origin_x,\n",
        "                     MARGIN + ROW_SIZE + bbox.origin_y)\n",
        "    cv2.putText(image, result_text, text_location, cv2.FONT_HERSHEY_PLAIN,\n",
        "                FONT_SIZE, TEXT_COLOR, FONT_THICKNESS)\n",
        "\n",
        "  return image"
      ]
    },
    {
      "cell_type": "markdown",
      "metadata": {
        "id": "83PEJNp9yPBU"
      },
      "source": [
        "## Download test image\n",
        "\n",
        "Let's grab a test image that we'll use later. This image comes from [Pixabay](https://pixabay.com/photos/pet-cute-animal-domestic-mammal-3157961/)."
      ]
    },
    {
      "cell_type": "code",
      "execution_count": null,
      "metadata": {
        "id": "tzXuqyIBlXer"
      },
      "outputs": [],
      "source": [
        "!wget -q -O image.jpg https://storage.googleapis.com/mediapipe-tasks/object_detector/cat_and_dog.jpg\n",
        "\n",
        "IMAGE_FILE = 'image.jpg'\n",
        "\n",
        "import cv2\n",
        "from google.colab.patches import cv2_imshow\n",
        "\n",
        "img = cv2.imread(IMAGE_FILE)\n",
        "cv2_imshow(img)"
      ]
    },
    {
      "cell_type": "markdown",
      "metadata": {
        "id": "NAFQm3HHi5OG"
      },
      "source": [
        "Optionally, you can upload your own image. If you want to do so, uncomment and run the cell below."
      ]
    },
    {
      "cell_type": "code",
      "execution_count": null,
      "metadata": {
        "id": "7gwip05yi6lV"
      },
      "outputs": [],
      "source": [
        "# from google.colab import files\n",
        "# uploaded = files.upload()\n",
        "\n",
        "# for filename in uploaded:\n",
        "#   content = uploaded[filename]\n",
        "#   with open(filename, 'wb') as f:\n",
        "#     f.write(content)\n",
        "\n",
        "# if len(uploaded.keys()):\n",
        "#   IMAGE_FILE = next(iter(uploaded))\n",
        "#   print('Uploaded file:', IMAGE_FILE)"
      ]
    },
    {
      "cell_type": "markdown",
      "metadata": {
        "id": "Iy4r2_ePylIa"
      },
      "source": [
        "## Running inference and visualizing the results\n",
        "\n",
        "Here are the steps to run object detection using MediaPipe.\n",
        "\n",
        "Check out the [MediaPipe documentation](https://developers.google.com/mediapipe/solutions/vision/object_detector/python) to learn more about configuration options that this solution supports."
      ]
    },
    {
      "cell_type": "code",
      "execution_count": null,
      "metadata": {
        "id": "Yl_Oiye4mUuo"
      },
      "outputs": [],
      "source": [
        "# STEP 1: Import the necessary modules.\n",
        "import numpy as np\n",
        "import mediapipe as mp\n",
        "from mediapipe.tasks import python\n",
        "from mediapipe.tasks.python import vision\n",
        "\n",
        "# STEP 2: Create an ObjectDetector object.\n",
        "base_options = python.BaseOptions(model_asset_path='efficientdet.tflite')\n",
        "options = vision.ObjectDetectorOptions(base_options=base_options,\n",
        "                                       score_threshold=0.5)\n",
        "detector = vision.ObjectDetector.create_from_options(options)\n",
        "\n",
        "# STEP 3: Load the input image.\n",
        "image = mp.Image.create_from_file(IMAGE_FILE)\n",
        "\n",
        "# STEP 4: Detect objects in the input image.\n",
        "detection_result = detector.detect(image)\n",
        "\n",
        "# STEP 5: Process the detection result. In this case, visualize it.\n",
        "image_copy = np.copy(image.numpy_view())\n",
        "annotated_image = visualize(image_copy, detection_result)\n",
        "rgb_annotated_image = cv2.cvtColor(annotated_image, cv2.COLOR_BGR2RGB)\n",
        "cv2_imshow(rgb_annotated_image)"
      ]
    },
    {
      "cell_type": "code",
      "execution_count": null,
      "metadata": {
        "id": "huDfvvkvkqzC"
      },
      "outputs": [],
      "source": []
    }
  ],
  "metadata": {
    "colab": {
      "provenance": []
    },
    "kernelspec": {
      "display_name": "Python 3 (ipykernel)",
      "language": "python",
      "name": "python3"
    },
    "language_info": {
      "codemirror_mode": {
        "name": "ipython",
        "version": 3
      },
      "file_extension": ".py",
      "mimetype": "text/x-python",
      "name": "python",
      "nbconvert_exporter": "python",
      "pygments_lexer": "ipython3",
      "version": "3.8.10"
    }
  },
  "nbformat": 4,
  "nbformat_minor": 0
}
