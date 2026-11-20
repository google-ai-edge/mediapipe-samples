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

import com.android.build.api.dsl.LibraryExtension
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.w3c.dom.Attr
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Every sample of the spec becomes a library module, :samples:<task>, that compiles the
// sources of the sample where they are (examples/<sample>/android/app/src/main); the app takes
// the models from the assets directory of the sample. Three things of a sample cannot go into one
// app as they are, so this file generates them into samples/<task>/generated:
//
// - The resources. The resources of all modules of an app share one namespace, and the samples
//   use the same names for different things ("app_name", "settings_title", ...). The copy
//   gives the strings and resource files of a sample the prefix "<task>_".
// - R. The sources of the sample refer to R.string.app_name of their own package. The
//   generated R of that package maps each name to the prefixed resource, so the sources
//   need no change.
// - The manifest. The activities of the sample keep their themes and labels, but are not
//   launcher activities any more, and are not exported.

@Suppress("UNCHECKED_CAST")
val samples = gradle.extra["validationSamples"] as List<Map<String, String>>
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val androidNs = "http://schemas.android.com/apk/res/android"

fun version(name: String): String = catalog.findVersion(name).get().requiredVersion

/** What the build needs to know about a sample after generating its module. */
class GeneratedSample(
    val task: String,
    val moduleNamespace: String,
    val launcherActivity: String,
    /** The `<style>` definitions of the sample, by name and resource directory. They are not prefixed. */
    val styles: Map<String, String>,
)

fun elements(parent: Element): List<Element> =
    (0 until parent.childNodes.length).map { parent.childNodes.item(it) }.filterIsInstance<Element>()

fun parseXml(file: File): Element =
    DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file).documentElement

/** Writes [bytes] to [file] unless it has them already, so that unchanged inputs stay unchanged. */
fun writeIfChanged(file: File, bytes: ByteArray, written: MutableSet<File>) {
    written += file
    if (file.isFile && file.readBytes().contentEquals(bytes)) return
    file.parentFile.mkdirs()
    file.writeBytes(bytes)
}

fun generateSample(sample: Map<String, String>, out: File): GeneratedSample {
    val task = sample.getValue("task")
    val namespace = sample.getValue("namespace")
    val moduleNamespace = "com.google.mediapipe.examples.validation.samples.$task"
    val prefix = "${task}_"
    val main = File(sample.getValue("dir"), "app/src/main")
    val resDir = File(main, "res")
    val written = mutableSetOf<File>()

    // The resources the sample declares: type -> names. All of them get the prefix, except
    // styles: the samples share one theme, and the build checks below that they do.
    val prefixed = sortedMapOf<String, MutableSet<String>>()
    val styles = sortedMapOf<String, String>()
    val resDirs = resDir.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
    for (dir in resDirs) {
        for (file in dir.listFiles().orEmpty().sortedBy { it.name }) {
            if (!dir.name.startsWith("values")) {
                prefixed.getOrPut(dir.name.substringBefore("-")) { sortedSetOf() } += file.name.substringBefore(".")
                continue
            }
            for (element in elements(parseXml(file))) {
                when (element.tagName) {
                    "string" -> prefixed.getOrPut("string") { sortedSetOf() } += element.getAttribute("name")
                    "style" -> Unit
                    else ->
                        throw GradleException(
                            "$file declares a <${element.tagName}>. The validation app handles <string> and " +
                                "<style> only; teach generateSample() in ${buildFile.name} how to prefix it."
                        )
                }
            }
        }
    }
    val reference = Regex("""@([a-z]+)/([A-Za-z0-9_.]+)""")
    fun String.withPrefixedReferences() =
        reference.replace(this) { match ->
            val (type, name) = match.destructured
            if (name in prefixed[type].orEmpty()) "@$type/$prefix$name" else match.value
        }

    // 1. The resources.
    val stringName = Regex("""(<string\s[^>]*?\bname=")([^"]+)(")""")
    for (dir in resDirs) {
        for (file in dir.listFiles().orEmpty()) {
            if (dir.name.startsWith("values")) {
                val text =
                    stringName.replace(file.readText()) { "${it.groupValues[1]}$prefix${it.groupValues[2]}${it.groupValues[3]}" }
                        .withPrefixedReferences()
                writeIfChanged(File(out, "res/${dir.name}/${file.name}"), text.toByteArray(), written)
            } else {
                val bytes =
                    if (file.extension == "xml") file.readText().withPrefixedReferences().toByteArray() else file.readBytes()
                writeIfChanged(File(out, "res/${dir.name}/$prefix${file.name}"), bytes, written)
            }
        }
    }
    for (dir in resDirs.filter { it.name.startsWith("values") }) {
        for (style in dir.listFiles().orEmpty().flatMap { elements(parseXml(it)) }.filter { it.tagName == "style" }) {
            val items = elements(style).joinToString(";") { "${it.getAttribute("name")}=${it.textContent.trim().withPrefixedReferences()}" }
            styles["${style.getAttribute("name")} (${dir.name})"] = "parent=${style.getAttribute("parent")};$items"
        }
    }

    // 2. The R of the package of the sample. It cannot be a class named R: the Android Gradle
    // plugin leaves every class of that name out of a library, because the app generates them.
    val resources = buildString {
        appendLine("// Generated by ${buildFile.name}. Do not edit.")
        appendLine("package $namespace")
        appendLine()
        appendLine("import $moduleNamespace.R as ModuleR")
        appendLine()
        appendLine("/** What `R` means in the sources of the sample: its resources in the validation app. */")
        appendLine("internal typealias R = SampleResources")
        appendLine()
        appendLine("/** Maps the resource names of the sample to its resources, which have the prefix \"$prefix\". */")
        appendLine("internal object SampleResources {")
        val unprefixed = styles.keys.map { it.substringBefore(" (").replace('.', '_') }.toSortedSet()
        for (type in prefixed.keys + "style") appendLine("    val `$type` = ${type.replaceFirstChar(Char::uppercase)}Resources")
        for ((type, names) in prefixed.mapValues { (_, names) -> names.map { it to "$prefix$it" } } + ("style" to unprefixed.map { it to it })) {
            appendLine()
            appendLine("    object ${type.replaceFirstChar(Char::uppercase)}Resources {")
            for ((name, resource) in names) appendLine("        val `$name`: Int get() = ModuleR.$type.$resource")
            appendLine("    }")
        }
        appendLine("}")
    }
    writeIfChanged(File(out, "kotlin/${namespace.replace('.', '/')}/SampleResources.kt"), resources.toByteArray(), written)

    // 3. The manifest.
    val manifest = parseXml(File(main, "AndroidManifest.xml"))
    manifest.removeAttribute("package")
    val application = elements(manifest).single { it.tagName == "application" }
    if (application.hasAttributeNS(androidNs, "name")) {
        throw GradleException("The sample of $task has its own Application class, which one app for all samples cannot honour.")
    }
    val appTheme = application.getAttributeNS(androidNs, "theme")
    val appLabel = application.getAttributeNS(androidNs, "label")
    val attributes = application.attributes
    for (attribute in (0 until attributes.length).map { attributes.item(it) as Attr }) {
        if (!attribute.name.startsWith("xmlns")) application.removeAttributeNode(attribute)
    }
    var launcher: String? = null
    for (component in elements(application)) {
        if (component.tagName !in setOf("activity", "service", "receiver", "provider")) continue
        val name = component.getAttributeNS(androidNs, "name")
        val className =
            when {
                name.startsWith(".") -> namespace + name
                "." !in name -> "$namespace.$name"
                else -> name
            }
        component.setAttributeNS(androidNs, "android:name", className)
        if (component.tagName != "activity") continue
        for (filter in elements(component).filter { it.tagName == "intent-filter" }) {
            val names = elements(filter).map { it.getAttributeNS(androidNs, "name") }
            if ("android.intent.action.MAIN" in names) {
                if ("android.intent.category.LAUNCHER" in names) launcher = className
                component.removeChild(filter)
            }
        }
        component.setAttributeNS(androidNs, "android:exported", "false")
        if (!component.hasAttributeNS(androidNs, "theme") && appTheme.isNotEmpty()) {
            component.setAttributeNS(androidNs, "android:theme", appTheme)
        }
        if (!component.hasAttributeNS(androidNs, "label") && appLabel.isNotEmpty()) {
            component.setAttributeNS(androidNs, "android:label", appLabel)
        }
    }
    fun tidy(node: org.w3c.dom.Node) {
        for (child in (0 until node.childNodes.length).map { node.childNodes.item(it) }) {
            val blank = child.nodeType == org.w3c.dom.Node.TEXT_NODE && child.textContent.isBlank()
            if (blank || child.nodeType == org.w3c.dom.Node.COMMENT_NODE) node.removeChild(child) else tidy(child)
        }
    }
    tidy(manifest.ownerDocument)
    val manifestText =
        StringWriter().also { writer ->
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
            transformer.transform(DOMSource(manifest.ownerDocument), StreamResult(writer))
        }.toString().withPrefixedReferences()
    writeIfChanged(File(out, "AndroidManifest.xml"), manifestText.toByteArray(), written)

    // Files of an earlier build that the sample no longer has.
    out.walkBottomUp().filter { it.isFile && it !in written }.forEach { it.delete() }

    return GeneratedSample(
        task = task,
        moduleNamespace = moduleNamespace,
        launcherActivity = launcher ?: throw GradleException("The sample of $task has no launcher activity."),
        styles = styles,
    )
}

/**
 * The dependencies a sample declares, as (configuration, notation, excludes). The module of
 * the sample gets exactly these, so a sample cannot use what it does not declare.
 */
fun sampleDependencies(sample: Map<String, String>): List<Triple<String, Any, List<Pair<String, String>>>> {
    val buildFile = File(sample.getValue("dir"), "app/build.gradle.kts")
    val block = buildFile.readText().substringAfter("\ndependencies {", "").substringBeforeLast("}")
    val library = Regex("""^(implementation|api|runtimeOnly)\((platform\()?libs\.([a-z0-9.]+)\)?\)\s*(\{)?$""")
    val projectDependency = Regex("""^(implementation|api)\(project\("(:android-common:[a-z]+)"\)\)$""")
    val exclude = Regex("""^exclude\(group = "([^"]+)", module = "([^"]+)"\)$""")
    val other = Regex("""^(testImplementation|androidTestImplementation|debugImplementation)\(.*\)$""")
    val result = mutableListOf<Triple<String, Any, MutableList<Pair<String, String>>>>()
    var open = false
    for (line in block.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }) {
        val match = library.matchEntire(line)
        val excluded = exclude.matchEntire(line)
        val project = projectDependency.matchEntire(line)
        when {
            open && excluded != null -> result.last().third += excluded.groupValues[1] to excluded.groupValues[2]
            open && line == "}" -> open = false
            match != null -> {
                val alias = match.groupValues[3].replace('.', '-')
                val found = catalog.findLibrary(alias).orElseThrow { GradleException("$buildFile: no library $alias") }
                val notation: Any = if (match.groupValues[2].isNotEmpty()) dependencies.platform(found) else found
                result += Triple(match.groupValues[1], notation, mutableListOf())
                open = match.groupValues[4].isNotEmpty()
            }
            project != null -> result += Triple(project.groupValues[1], project(project.groupValues[2]), mutableListOf())
            other.matches(line) -> Unit
            else ->
                throw GradleException(
                    "$buildFile declares a dependency in a form the validation app does not read: $line"
                )
        }
    }
    return result
}

val generated = mutableListOf<GeneratedSample>()

for (sample in samples) {
    val task = sample.getValue("task")
    val sampleDir = File(sample.getValue("dir"))
    val main = File(sampleDir, "app/src/main")
    project(":samples:$task") {
        val out = File(projectDir, "generated")
        val module = generateSample(sample, out)
        generated += module

        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.android")
        apply(plugin = "org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            namespace = module.moduleNamespace
            compileSdk = version("compileSdk").toInt()
            defaultConfig { minSdk = version("minSdk").toInt() }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
            buildFeatures { compose = true }
            sourceSets.getByName("main") {
                manifest.srcFile(File(out, "AndroidManifest.xml"))
                res.setSrcDirs(listOf(File(out, "res")))
                // The app takes the assets of the samples from where they are (see its build file):
                // through this module, each model would be copied once more on the way.
                assets.setSrcDirs(emptyList<File>())
                // The sources of the sample, its generated R, and the adapter that drives the
                // sample for the validation (adapters/<task>).
                java.setSrcDirs(listOf(File(main, "java")))
                kotlin.setSrcDirs(
                    listOf(File(main, "java"), File(main, "kotlin"), File(out, "kotlin"), rootProject.file("adapters/$task"))
                )
            }
        }

        // The download script of the sample puts the pinned models into the assets of the
        // sample, so this build and the build of the sample share them.
        extra["ASSET_DIR"] = File(main, "assets").path
        extra["TEST_ASSETS_DIR"] = File(sampleDir, "app/src/androidTest/assets").path
        val downloadScripts = File(sampleDir, "app").listFiles { file -> file.name.startsWith("download_") && file.name.endsWith(".gradle.kts") }
        for (script in downloadScripts.orEmpty()) apply(from = script)

        dependencies {
            add("api", project(":runner"))
            for ((configuration, notation, excludes) in sampleDependencies(sample)) {
                if (excludes.isEmpty() || notation !is Provider<*>) {
                    add(configuration, notation)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    addProvider<Any, ExternalModuleDependency>(configuration, notation as Provider<Any>) {
                        for ((group, name) in excludes) exclude(mapOf("group" to group, "module" to name))
                    }
                }
            }
        }
    }
}

// Styles keep their names, so samples that define one name differently would silently get the
// theme of another sample.
for ((name, definitions) in generated.flatMap { module -> module.styles.map { Triple(it.key, it.value, module.task) } }.groupBy { it.first }) {
    val variants = definitions.groupBy({ it.second }, { it.third })
    if (variants.size > 1) {
        throw GradleException(
            "The samples define the style \"$name\" differently: ${variants.values}. One app has one " +
                "style of a name; make them equal, or teach generateSample() in ${buildFile.name} to prefix styles."
        )
    }
}

extra["validationModules"] =
    generated.map { module ->
        val sample = samples.single { it.getValue("task") == module.task }
        mapOf(
            "task" to module.task,
            "domain" to sample.getValue("domain"),
            "heading" to sample.getValue("heading"),
            "path" to sample.getValue("path"),
            "activity" to module.launcherActivity,
        )
    }

subprojects {
    // The LiteRT-LM runtime of the retrieval samples is built with a newer Kotlin than the
    // samples. As in those samples: keep the compile classpaths on the Kotlin of the build.
    configurations.configureEach {
        if (name.endsWith("CompileClasspath")) {
            resolutionStrategy.force(
                "org.jetbrains.kotlin:kotlin-stdlib:${version("kotlin")}",
                "org.jetbrains.kotlin:kotlin-reflect:${version("kotlin")}",
            )
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }
}
