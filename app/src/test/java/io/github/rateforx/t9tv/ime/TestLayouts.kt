package io.github.rateforx.t9tv.ime

import java.io.File

/** Reads the real layout files from `src/main/assets/layouts` (unit tests run in the module directory). */
object TestLayouts {
    private val dir = File("src/main/assets/layouts")

    fun load(id: String): KeyLayout = LayoutParser.parse(id, File(dir, "$id.json").readText())

    fun ids(): List<String> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.sorted()
}
