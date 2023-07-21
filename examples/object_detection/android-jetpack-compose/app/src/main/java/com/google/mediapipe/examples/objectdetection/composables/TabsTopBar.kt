package com.google.mediapipe.examples.objectdetection.composables

import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.google.mediapipe.examples.objectdetection.R

// This composable is used in Home screen to navigate between camera view and gallery view

// It takes an index to display an indication of which tab we're currently in, and a function
// to set that index when a tab is tapped

@Composable
fun TabsTopBar(
    selectedTabIndex: Int,
    setSelectedTabIndex: (Int) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTabIndex) {
        Tab(
            unselectedContentColor = Color.Gray,
            selected = selectedTabIndex == 0,
            onClick = {
                setSelectedTabIndex(0)
            },
            text = { Text("Camera") },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_photo_camera_24),
                    contentDescription = ""
                )
            },
        )
        Tab(
            unselectedContentColor = Color.Gray,
            selected = selectedTabIndex == 1,
            onClick = {
                setSelectedTabIndex(1)
            },
            text = { Text("Gallery") },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_photo_library_24),
                    contentDescription = ""
                )
            },
        )
    }
}