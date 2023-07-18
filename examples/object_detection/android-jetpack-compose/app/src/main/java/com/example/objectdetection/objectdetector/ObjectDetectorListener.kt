package com.example.objectdetection.objectdetector

// An implementation for the DetectorListener interface
// Two custom callback functions are supplied to react to results and errors

class ObjectDetectorListener(
    val onErrorCallback: (error: String, errorCode: Int) -> Unit,
    val onResultsCallback: (resultBundle: ObjectDetectorHelper.ResultBundle) -> Unit
) : ObjectDetectorHelper.DetectorListener {

    override fun onError(error: String, errorCode: Int) {
        onErrorCallback(error, errorCode)
    }

    override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
        onResultsCallback(resultBundle)
    }
}