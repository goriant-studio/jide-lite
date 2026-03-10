package com.goriant.jidelite.ui.main

data class EditorDiagnosticHint(
    val fileReference: String?,
    val lineNumber: Int,
    val message: String? = null
)

data class EditorDiagnostic(
    val filePath: String?,
    val lineNumber: Int,
    val message: String? = null,
    val requestId: Long = System.nanoTime()
)

object EditorDiagnosticParser {

    private val javacStylePattern = Regex("""(?m)^(.+?\.java):(\d+)(?::\d+)?:""")
    private val janinoStylePattern = Regex("""(?m)^(?:File\s+)?(.+?\.java),\s*Line\s+(\d+),\s*Column\s+\d+""")
    private val stackTracePattern = Regex("""(?m)\(([^():\n]+\.java):(\d+)\)""")
    private val lineOnlyPattern = Regex("""(?m)^Line\s+(\d+),\s*Column\s+\d+""")

    fun parse(stderr: String, stdout: String = ""): EditorDiagnosticHint? {
        for (source in listOf(stderr, stdout)) {
            parse(source)?.let { return it }
        }
        return null
    }

    fun parse(text: String): EditorDiagnosticHint? {
        if (text.isBlank()) {
            return null
        }

        javacStylePattern.find(text)?.let { match ->
            return EditorDiagnosticHint(
                fileReference = match.groupValues[1].trim(),
                lineNumber = match.groupValues[2].toInt(),
                message = match.value.trim()
            )
        }

        janinoStylePattern.find(text)?.let { match ->
            return EditorDiagnosticHint(
                fileReference = match.groupValues[1].trim(),
                lineNumber = match.groupValues[2].toInt(),
                message = match.value.trim()
            )
        }

        stackTracePattern.find(text)?.let { match ->
            return EditorDiagnosticHint(
                fileReference = match.groupValues[1].trim(),
                lineNumber = match.groupValues[2].toInt(),
                message = match.value.trim()
            )
        }

        lineOnlyPattern.find(text)?.let { match ->
            return EditorDiagnosticHint(
                fileReference = null,
                lineNumber = match.groupValues[1].toInt(),
                message = match.value.trim()
            )
        }

        return null
    }
}
