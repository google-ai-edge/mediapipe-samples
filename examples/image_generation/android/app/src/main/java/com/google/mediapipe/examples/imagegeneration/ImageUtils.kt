package com.google.mediapipe.examples.imagegeneration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileDescriptor

object ImageUtils {

    fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val parcelFileDescriptor: ParcelFileDescriptor? =
                context.contentResolver.openFileDescriptor(uri, "r")
            val fileDescriptor: FileDescriptor? =
                parcelFileDescriptor?.fileDescriptor
            val image: Bitmap? =
                BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor?.close()
            image
        } catch (e: Exception) {
            Log.e("ImageUtils", "Image decoding failed: ${e.message}")
            null
        }
    }
}
