/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.validation.runner

import android.app.Activity
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Serves the input files of the cases to the samples. See the manifest. */
class ValidationFileProvider : FileProvider()

/**
 * The entry point the host starts, once per case:
 *
 *     adb shell am instrument -w -e case <id> <package>/<this class>
 *
 * It runs in the process of the app, so it can start the activity of a sample and reach its
 * ViewModel. The cases and their input files are assets of the app (see build.gradle.kts of the
 * app). The argument `case` names the ids to run, separated by commas; without it, every case
 * runs in this one process. The results go to `validation/out` in the external files directory
 * of the app, which `adb pull` can read; the first line of the output names that directory.
 * With `-e export true`, it runs nothing, and writes the cases the app holds to `cases.json`
 * there, for the host to check that they are the cases it is about to judge.
 */
class ValidationInstrumentation : Instrumentation() {
    private var arguments = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        val results = Bundle()
        try {
            results.putString(REPORT_KEY_STREAMRESULT, CaseRunner(this, arguments).run() + "\n")
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            Log.e(CaseRunner.TAG, "The validation could not run", error)
            results.putString(REPORT_KEY_STREAMRESULT, "The validation could not run: $error\n")
            finish(Activity.RESULT_CANCELED, results)
        }
    }
}

/** A sample of this app: what build.gradle.kts of the app wrote to validation/samples.json. */
class SampleInfo(json: JSONObject) {
    val task: String = json.getString("task")
    val domain: String = json.getString("domain")
    val heading: String = json.getString("heading")
    val path: String = json.getString("path")
    val activity: String = json.getString("activity")

    companion object {
        /** The samples this app was built with, and the versions it was built against. */
        fun read(context: Context): Pair<List<SampleInfo>, JSONObject> {
            val document = JSONObject(context.assets.open("validation/samples.json").bufferedReader().use { it.readText() })
            return document.getJSONArray("samples").objects().map(::SampleInfo) to document.getJSONObject("versions")
        }
    }
}

/** Runs cases one by one, each in the sample of its task, and writes what the sample showed. */
internal class CaseRunner(private val instrumentation: Instrumentation, private val arguments: Bundle) {
    private val context: Context = instrumentation.targetContext
    private val outDir = File(context.getExternalFilesDir(null), "validation/out")

    fun run(): String {
        val casesText = context.assets.open(CASES_ASSET).bufferedReader().use { it.readText() }
        val document = JSONObject(casesText)
        val cases = document.getJSONArray("cases").objects().map(::ValidationCase)
        val wanted = arguments.getString("case")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        val selected =
            wanted?.map { id -> cases.firstOrNull { it.id == id } ?: throw IllegalArgumentException("This app has no case $id.") }
                ?: cases
        val (samples, versions) = SampleInfo.read(context)
        outDir.mkdirs()
        if (arguments.getString("export") == "true") {
            File(outDir, "cases.json").writeText(casesText)
            return "Results in $outDir\n${cases.size} cases exported"
        }
        val casesSha256 = MessageDigest.getInstance("SHA-256").digest(casesText.toByteArray()).joinToString("") { "%02x".format(it) }
        File(outDir, "environment.json").writeText(environment(versions).put("casesSha256", casesSha256).toString(2))
        wakeDevice()

        return "Results in $outDir\n" + selected.joinToString("\n") { case ->
            val result = runCase(case, samples.firstOrNull { it.task == case.task }, document.getJSONObject("assets"))
            File(outDir, "${case.id}.json").writeText(result.toString(2))
            val outcome =
                when {
                    !result.isNull("runnerError") -> "could not run: ${result.getString("runnerError")}"
                    !result.isNull("error") -> "error: ${result.getString("error")}"
                    else -> "ran"
                }
            "${case.id}: $outcome"
        }
    }

    private fun runCase(case: ValidationCase, sample: SampleInfo?, assets: JSONObject): JSONObject {
        val startedAt = Date()
        val started = SystemClock.elapsedRealtime()
        val result = JSONObject().put("id", case.id).put("runs", JSONArray()).put("error", JSONObject.NULL)
        var session: SampleSession? = null
        try {
            if (sample == null) throw RunnerError("This app was built without the sample of ${case.task}.")
            val adapter = adapterOf(case.task)
            val intent = Intent().setClassName(context, sample.activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val activity = instrumentation.startActivitySync(intent) as ComponentActivity
            session = SampleSession(instrumentation, activity, case, assets)

            val runs = adapter.run(case, session)

            if (runs.map { it.key } != case.steps.map { it.key }) {
                throw RunnerError("The adapter returned the runs ${runs.map { it.key }} for the steps ${case.steps.map { it.key }}.")
            }
            if (case.options.unread().isNotEmpty()) {
                throw RunnerError("The adapter did not apply the options ${case.options.unread()} of the case.")
            }
            if (session.model == null) throw RunnerError("The adapter did not say which model the sample loads.")
            result.put("runs", jsonArray(runs.map { json("key" to it.key, "data" to it.data) }))
        } catch (error: SampleError) {
            result.put("error", error.message)
        } catch (error: Throwable) {
            Log.e(TAG, "Could not run ${case.id}", error)
            result.put("runnerError", "${error.javaClass.simpleName}: ${error.message}")
        }
        session?.let {
            it.model?.let { (asset, sha256) -> result.put("model", json("asset" to asset, "sha256" to sha256)) }
            screenshot(File(outDir, "${case.id}.png"))
            runCatching { it.finish() }
        }
        result.put("durationSec", (SystemClock.elapsedRealtime() - started) / 1000.0)
        // The log of this process since the case began: the errors MediaPipe logged, and the
        // lines that show which delegate ran.
        val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(startedAt)
        runCatching {
            val logcat = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=${Process.myPid()}", "-T", since)
            logcat.redirectErrorStream(true).redirectOutput(File(outDir, "${case.id}.log")).start().waitFor()
        }
        return result
    }

    private fun adapterOf(task: String): TaskAdapter {
        val name = ADAPTER_PACKAGE + "." + task.split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) } + "Adapter"
        val type =
            try {
                Class.forName(name)
            } catch (error: ClassNotFoundException) {
                throw RunnerError("The validation app has no adapter for the sample of $task ($name).")
            }
        return type.getDeclaredConstructor().newInstance() as TaskAdapter
    }

    /** What the user of the sample saw at the end of the case. */
    private fun screenshot(file: File) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(SETTLE_MS)
        runCatching {
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** An activity composes its screen, and so creates its ViewModel, only on a screen that is on. */
    private fun wakeDevice() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 3_000
        while (keyguard.isKeyguardLocked && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        check(!keyguard.isKeyguardLocked) { "The device is locked. Unlock it, or remove its screen lock." }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {
            it.readBytes().decodeToString()
        }

    private fun environment(versions: JSONObject): JSONObject =
        json(
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "system" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "build" to Build.FINGERPRINT,
            "abi" to Build.SUPPORTED_ABIS.firstOrNull(),
            "hardware" to Build.HARDWARE,
            "soc" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else null,
            "package" to context.packageName,
            "versions" to versions,
        )

    companion object {
        const val TAG = "MediaPipeValidation"
        private const val CASES_ASSET = "validation/cases.json"
        private const val ADAPTER_PACKAGE = "com.google.mediapipe.examples.validation.adapters"
        private const val SETTLE_MS = 500L
    }
}
