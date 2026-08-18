package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.tasks.vision.core.RunningMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [OverlayView] pure logic that does not require Android instrumentation.
 *
 * These tests cover the scale-factor computation extracted to
 * [OverlayView.computeScaleFactor] and the [OverlayView.RenderMode] enum contract.
 */
class OverlayViewTest {

    @Test
    fun renderMode_hasTwoOptions() {
        assertEquals(2, OverlayView.RenderMode.values().size)
        assertEquals(OverlayView.RenderMode.RGB_OVERLAY, OverlayView.RenderMode.valueOf("RGB_OVERLAY"))
        assertEquals(OverlayView.RenderMode.DESENSITIZED, OverlayView.RenderMode.valueOf("DESENSITIZED"))
    }

    @Test
    fun computeScaleFactor_imageMode_usesMinRatioToFit() {
        // view 100x100, image 200x100 (2:1) -> min(0.5, 1.0) = 0.5 (fit by width)
        val factor = OverlayView.computeScaleFactor(
            RunningMode.IMAGE, viewWidth = 100, viewHeight = 100,
            imageWidth = 200, imageHeight = 100
        )
        assertEquals(0.5f, factor, 0.0001f)
    }

    @Test
    fun computeScaleFactor_videoMode_usesMinRatioToFit() {
        // view 200x200, image 100x200 (1:2) -> min(2.0, 1.0) = 1.0 (fit by height)
        val factor = OverlayView.computeScaleFactor(
            RunningMode.VIDEO, viewWidth = 200, viewHeight = 200,
            imageWidth = 100, imageHeight = 200
        )
        assertEquals(1.0f, factor, 0.0001f)
    }

    @Test
    fun computeScaleFactor_liveStreamMode_usesMaxRatioToFill() {
        // view 100x100, image 200x100 (2:1) -> max(0.5, 1.0) = 1.0 (fill by height, overflow width cropped)
        val factor = OverlayView.computeScaleFactor(
            RunningMode.LIVE_STREAM, viewWidth = 100, viewHeight = 100,
            imageWidth = 200, imageHeight = 100
        )
        assertEquals(1.0f, factor, 0.0001f)
    }

    @Test
    fun computeScaleFactor_liveStreamMode_squareImage_returnsOne() {
        val factor = OverlayView.computeScaleFactor(
            RunningMode.LIVE_STREAM, viewWidth = 480, viewHeight = 640,
            imageWidth = 480, imageHeight = 640
        )
        assertEquals(1.0f, factor, 0.0001f)
    }

    @Test
    fun computeScaleFactor_imageVsLiveStream_differWhenAspectRatiosMismatch() {
        // Same dimensions, different modes should produce different scale factors
        // view 100x200, image 200x100
        val imageFactor = OverlayView.computeScaleFactor(
            RunningMode.IMAGE, 100, 200, 200, 100
        )
        val streamFactor = OverlayView.computeScaleFactor(
            RunningMode.LIVE_STREAM, 100, 200, 200, 100
        )
        // min(0.5, 2.0)=0.5 vs max(0.5, 2.0)=2.0
        assertEquals(0.5f, imageFactor, 0.0001f)
        assertEquals(2.0f, streamFactor, 0.0001f)
    }
}
