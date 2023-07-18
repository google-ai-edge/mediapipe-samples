package com.example.objectdetection.home.gallery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.objectdetection.ui.theme.Turquoise

// Here we have the gallery view which is displayed in Home screen

// It's used to run object detection on images and videos from the phone's storage

// It takes as input the object detection options, and a function to update the inference time state
@Composable
fun GalleryView(
    threshold: Float,
    maxResults: Int,
    delegate: Int,
    mlModel: Int,
    setInferenceTime: (Int) -> Unit,
) {

    // We need a state to hold the Uri of the currently chosen media (image or video) if any
    var selectedMediaUri by rememberSaveable {
        mutableStateOf<Uri?>(null)
    }


    // We use this launcher later to launch a new activity to select a media file from
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        // On selecting an image or a video, we update the selected media uri
        onResult = { uri ->
            selectedMediaUri = uri
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // We first want to figure out the type of the selected media, which is one of
        // the following three cases: Image, Video, Unknown.

        val selectedMediaType = selectedMediaUri?.let {
            val mimeType = LocalContext.current.contentResolver.getType(it)
            if (mimeType == null) {
                "Unknown"
            } else if (mimeType.startsWith("image")) {
                "Image"
            } else if (mimeType.startsWith("video")){
                "Video"
            } else {
                "Unknown"
            }
        }

        // Now that we know the selected media type, we display the appropriate composable

        when (selectedMediaType) {
            "Image" -> ImageDetectionView(
                threshold = threshold,
                maxResults = maxResults,
                delegate = delegate,
                mlModel = mlModel,
                setInferenceTime = setInferenceTime,
                imageUri = selectedMediaUri!!,
            )
            "Video" -> VideoDetectionView(
                threshold = threshold,
                maxResults = maxResults,
                delegate = delegate,
                mlModel = mlModel,
                setInferenceTime = setInferenceTime,
                videoUri = selectedMediaUri!!,
            )
            "Unknown" -> Text(
                text = "Unknown media type",
                modifier = Modifier.align(
                    Alignment.Center
                ),
            )
            // In this case, the selected media uri is null, i.e, nothing is selected
            else -> Text(
                text = "Click + to add an image or a video to begin running the object detection.",
                modifier = Modifier
                    .align(
                        Alignment.Center
                    )
                    .padding(20.dp),
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        // The floating action button here is used to launch an activity to select a media file from
        FloatingActionButton(
            onClick = {
                selectedMediaUri = null
                galleryLauncher.launch(arrayOf("image/*", "video/*"))
            },
            containerColor = Turquoise,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .absoluteOffset((-20).dp, (-10).dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
            )
        }
    }
}




