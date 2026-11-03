package com.jcapps.adbserver.parser

import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Read-only view of an APK: raw zip entries, the `<application android:icon>` paths from the
 * manifest, and a binary-XML streaming parser (resolving references through resources.arsc).
 */
// obf: C0018 (f45 = zipFile, f46 = preferredLocale, f41 = resourceTable, f42 = resourceTableParsed,
//             f43 = iconPaths, f44 = manifestParsed)
class ApkFile(filePath: String) : Closeable {
    private val preferredLocale: Locale = DEFAULT_LOCALE
    private val zipFile: ZipFile = ZipFile(File(filePath))
    private var resourceTable: ResourceTable? = null
    private var resourceTableParsed = false
    private var iconPaths: List<IconPath>? = null
    private var manifestParsed = false

    override fun close() {
        try {
            zipFile.close()
        } catch (e: Exception) {
        }
    }

    /** Bytes of the zip entry [path], or null when there is no such entry. */
    // obf: m40
    fun getFileData(path: String): ByteArray? {
        val entry = zipFile.getEntry(path) ?: return null
        val inputStream = zipFile.getInputStream(entry)!!
        return inputStream.use { it.readBytes() }
    }

    /** Icon paths of `<application android:icon>` (one per resource config; empty if none). */
    // obf: m41
    fun getIconPaths(): List<IconPath> {
        if (!manifestParsed) {
            parseResourceTable()
            // The original also streams the manifest into an XmlTranslator (obf C0249) that
            // rebuilds the textual XML; its result is never read, so it is omitted here.
            // RECON-UNSURE: omitted dead XmlTranslator (pure string building, no side effects).
            val apkMetaTranslator = ApkMetaTranslator(resourceTable!!, preferredLocale)
            val xmlStreamer = CompositeXmlStreamer(apkMetaTranslator)
            val data = getFileData("AndroidManifest.xml")
                ?: throw ParserException("Manifest file not found")
            parseBinaryXml(data, xmlStreamer)
            iconPaths = apkMetaTranslator.iconPaths
            manifestParsed = true
        }
        return iconPaths ?: emptyList()
    }

    /** Streams the binary XML [data] into [xmlStreamer]. */
    // obf: m42
    fun parseBinaryXml(data: ByteArray, xmlStreamer: XmlStreamer) {
        parseResourceTable()
        val buffer = ByteBuffer.wrap(data)!!
        val binaryXmlParser = BinaryXmlParser(buffer, resourceTable!!)
        binaryXmlParser.locale = preferredLocale
        binaryXmlParser.xmlStreamer = xmlStreamer
        binaryXmlParser.parse()
    }

    // obf: m43
    private fun parseResourceTable() {
        if (resourceTableParsed) return
        resourceTableParsed = true
        val data = getFileData("resources.arsc")
        if (data == null) {
            resourceTable = ResourceTable()
            return
        }
        val buffer = ByteBuffer.wrap(data)!!
        val resourceTableParser = ResourceTableParser(buffer)
        resourceTableParser.parse()
        resourceTable = resourceTableParser.resourceTable
    }

    companion object {
        // obf: f40
        private val DEFAULT_LOCALE: Locale = Locale.US
    }
}
