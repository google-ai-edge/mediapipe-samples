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

import org.json.JSONArray
import org.json.JSONObject

/**
 * One case of the validation spec: a task, a model, a mode and a delegate, with the options to
 * run it with and the inputs to feed it. `spec.py export --platform android` writes them.
 */
class ValidationCase(val json: JSONObject) {
    val id: String = json.getString("id")
    val task: String = json.getString("task")
    val modelId: String = json.getString("modelId")

    /** The name of the model in the assets of the spec. The sample may bundle it under another. */
    val modelAsset: String = json.getString("modelAsset")
    val mode: String = json.getString("mode")

    /** "CPU" or "GPU". */
    val delegate: String = json.getString("delegate")
    val options = Options(json.optJSONObject("options") ?: JSONObject())
    val steps: List<Step> = json.getJSONArray("steps").objects().map(::Step)
}

/** One input of a case, and the key under which its result is reported. */
class Step(val json: JSONObject) {
    val key: String = json.getString("key")

    fun string(name: String): String = json.getString(name)

    fun has(name: String): Boolean = json.has(name)
}

/**
 * The options of a case. A case must run with exactly these, so the runner fails a case whose
 * adapter has not read every one of them: an option nobody read is an option nobody applied.
 */
class Options(private val json: JSONObject) {
    private val read = mutableSetOf<String>()

    fun has(name: String): Boolean = json.has(name)

    fun string(name: String): String = value(name) as String

    fun boolean(name: String): Boolean = value(name) as Boolean

    fun int(name: String): Int = (value(name) as Number).toInt()

    fun float(name: String): Float = (value(name) as Number).toFloat()

    /**
     * For an option the sample has no setting for: states the value the sample always uses. The
     * case cannot run if the spec asks for another.
     */
    fun fixed(name: String, valueOfSample: Any) {
        val wanted = value(name)
        val same =
            if (wanted is Number && valueOfSample is Number) wanted.toDouble() == valueOfSample.toDouble()
            else wanted == valueOfSample
        if (!same) {
            throw RunnerError("The spec asks for $name = $wanted, but the sample always uses $valueOfSample.")
        }
    }

    /** The options that no call above has read. */
    fun unread(): Set<String> = json.keys().asSequence().toSet() - read

    private fun value(name: String): Any {
        if (!json.has(name)) throw RunnerError("The case has no option \"$name\".")
        read += name
        return json.get(name)
    }
}

/** What the sample showed for one step, in the form spec/README.md describes ("Observations"). */
class Run(val key: String, val data: JSONObject)

/** Drives the sample of one task through a case. One per task, in adapters/<task>. */
interface TaskAdapter {
    /**
     * Sets the sample up for [case] through its ViewModel, feeds it each step, and returns what
     * its UI state held afterwards, one [Run] per step.
     *
     * @throws SampleError if the sample reports an error to its user.
     * @throws RunnerError if the sample cannot run the case as the spec defines it.
     */
    fun run(case: ValidationCase, sample: SampleSession): List<Run>
}

/** The sample showed this error to its user. It is the outcome of the case, not a failure to run it. */
class SampleError(message: String) : Exception(message)

/** The case could not be run as the spec defines it. */
class RunnerError(message: String) : Exception(message)

/**
 * Fails the case if the sample did not take a setting as the case gives it, for example because
 * it clamps the value. A case must not run with another value than the spec states.
 */
fun requireApplied(setting: String, wanted: Any?, actual: Any?) {
    if (wanted != actual) throw RunnerError("The sample did not take $setting = $wanted; it uses $actual.")
}

fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

/** A JSON object of [pairs]; lists and arrays become JSON arrays, null becomes JSON null. */
fun json(vararg pairs: Pair<String, Any?>): JSONObject =
    JSONObject().apply { for ((name, value) in pairs) put(name, jsonValue(value)) }

fun jsonArray(items: Iterable<Any?>): JSONArray = JSONArray().apply { for (item in items) put(jsonValue(item)) }

private fun jsonValue(value: Any?): Any =
    when (value) {
        null -> JSONObject.NULL
        is Iterable<*> -> jsonArray(value)
        is FloatArray -> jsonArray(value.asIterable())
        is IntArray -> jsonArray(value.asIterable())
        is Array<*> -> jsonArray(value.asIterable())
        else -> value
    }
