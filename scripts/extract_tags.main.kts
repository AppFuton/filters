#!/usr/bin/env kotlin

/**
 * This script aims to extract all tags from Kotatsu-redo-parsers and store them in
 * the `tags.json` file.
 */

// External dependencies
@file:DependsOn("org.json:json:20240303")

import org.json.JSONArray
import java.io.File
import java.util.Locale

val repoUrl = "https://github.com/Kotatsu-Redo/kotatsu-parsers-redo"
val scriptDir = File(".").canonicalFile
val rootDir = scriptDir.parentFile ?: File("..")

val dataDir = File(rootDir, "data")
val outputFile = File(dataDir, "tags.json")
val cloneDir = File(scriptDir, "kotatsu-parsers-tmp")

fun main() {
    println("--- Starting Tag Extraction ---")

    // Ensure the data directory exists before writing to it
    if (!dataDir.exists()) {
        dataDir.mkdirs()
    }

    // Initialize or read existing JSON structure
    val tagsSet = mutableSetOf<String>()
    if (outputFile.exists() && outputFile.length() > 0) {
        try {
            val existingArray = JSONArray(outputFile.readText())
            for (i in 0 until existingArray.length()) {
                tagsSet.add(existingArray.getString(i).trim().lowercase(Locale.ROOT))
            }
            println("Loaded ${tagsSet.size} existing tags from: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            println("Warning: Could not parse existing JSON file. Starting fresh.")
        }
    }

    // Clone the repository safely using local system git execution
    if (cloneDir.exists()) {
        cloneDir.deleteRecursively()
    }

    println("Cloning repository: $repoUrl...")
    val gitProcess = ProcessBuilder("git", "clone", "--depth", "1", repoUrl, cloneDir.absolutePath)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val exitCode = gitProcess.waitFor()
    if (exitCode != 0) {
        println("Error: Failed to clone the repository.")
        return
    }

    // Structural Parsing via Regex
    println("Scanning Kotlin source files...")

    val tagRegex = """(?:MangaTag|MangaGenre)\s*\(([^)]+)\)""".toRegex()
    val stringLiteralRegex = """"[^"]+"""".toRegex()

    val blocklist = setOf(
        "http", "href", "api", "github", "content", "layout", "widget", "config",
        "util", "json", "string", "text", "java", "kotlin", "void", "class", "fun"
    )

    var foundCount = 0

    cloneDir.walkTopDown().forEach { file ->
        if (file.isFile && file.extension == "kt") {
            try {
                val content = file.readText()
                tagRegex.findAll(content).forEach { match ->
                    val innerArguments = match.groupValues[1]
                    stringLiteralRegex.findAll(innerArguments).forEach { stringMatch ->
                        val cleanString = stringMatch.value.replace("\"", "").trim().lowercase(Locale.ROOT)

                        if (cleanString.matches(Regex("^[a-z][a-z ]{1,30}$")) && cleanString !in blocklist) {
                            if (tagsSet.add(cleanString)) {
                                foundCount++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Keep moving safely if an unreadable file structure appears
            }
        }
    }

    // Build, save and clean up workspace
    if (foundCount > 0 || !outputFile.exists()) {
        val sortedTags = tagsSet.sorted()
        val jsonArray = JSONArray(sortedTags)
        outputFile.writeText(jsonArray.toString(2))
        println("Success! Added $foundCount new unique tags.")
    } else {
        println("No new tags found to add.")
    }

    println("Cleaning up temporary clone folder...")
    cloneDir.deleteRecursively()

    println("Execution complete. Output target: ${outputFile.absolutePath}")
    println("Total unique elements: ${tagsSet.size}")
    println("--------------------------------")
}

main()
