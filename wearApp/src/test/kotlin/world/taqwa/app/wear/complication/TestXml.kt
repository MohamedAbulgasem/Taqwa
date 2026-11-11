package world.taqwa.app.wear.complication

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The files SourceManifestTest and FaceAlwaysOnTest read as XML: the watch app's manifest and the
 * Taqwa face's watch face XML. Neither is code, so the tests hold them as files.
 */
internal object TestXml {

    /** The file's root element, read namespace-aware (the manifest's `android:` attributes). */
    fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file).documentElement

    /** Gradle hands the path in (wearApp/build.gradle.kts); a bare JVM run looks for it from the module. */
    fun file(property: String, fallback: String): File {
        val file = File(System.getProperty(property) ?: fallback)
        check(file.isFile) { "no file at ${file.absolutePath}" }
        return file
    }
}
