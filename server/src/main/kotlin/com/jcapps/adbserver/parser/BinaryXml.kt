package com.jcapps.adbserver.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// XML structures
// ---------------------------------------------------------------------------------------------

// obf: C0126 (f262 = namespace, f261 = name, f264 = rawValue, f263 = typedValue, f265 = value)
class Attribute {
    var namespace: String? = null
    var name: String? = null
    var rawValue: String? = null
    var typedValue: ResourceValue? = null
    var value: String? = null
}

/** obf: C0283 mode 2 (f604 = C0126[]) */
// obf: C0283 (mode 2)
class Attributes(size: Int) {
    val attributes: Array<Attribute?> = arrayOfNulls(size)

    /** Inlined into ApkMetaTranslator by R8. */
    fun get(name: String): Attribute? = attributes.firstOrNull { it?.name == name }
}

/** obf: C0086 used as start tag (f179 = namespace, f178 = name, f180 = attributes); R8-merged. */
// obf: C0086 (as XmlNodeStartTag)
class XmlNodeStartTag {
    var namespace: String? = null
    var name: String? = null
    var attributes: Attributes? = null
}

/** obf: C0339 used as end tag (f753 = namespace, f752 = name); R8-merged with the namespace tags. */
// obf: C0339 (as XmlNodeEndTag)
class XmlNodeEndTag {
    var namespace: String? = null
    var name: String? = null
}

// obf: C0339 (as XmlNamespaceStartTag: f753 = prefix, f752 = uri)
class XmlNamespaceStartTag {
    var prefix: String? = null
    var uri: String? = null
}

// obf: C0339 (as XmlNamespaceEndTag: f753 = prefix, f752 = uri)
class XmlNamespaceEndTag {
    var prefix: String? = null
    var uri: String? = null
}

// obf: InterfaceC0253
interface XmlStreamer {
    fun onNamespaceStart(tag: XmlNamespaceStartTag) // obf: mo117
    fun onStartTag(xmlNodeStartTag: XmlNodeStartTag) // obf: mo118
    fun onNamespaceEnd(tag: XmlNamespaceEndTag)     // obf: mo119
    fun onEndTag(xmlNodeEndTag: XmlNodeEndTag)       // obf: mo120
}

/** obf: C0283 mode 3 (f604 = InterfaceC0253[]) */
// obf: C0283 (mode 3)
class CompositeXmlStreamer(private vararg val xmlStreamers: XmlStreamer) : XmlStreamer {
    override fun onNamespaceStart(tag: XmlNamespaceStartTag) {
        for (s in xmlStreamers) s.onNamespaceStart(tag)
    }

    override fun onStartTag(xmlNodeStartTag: XmlNodeStartTag) {
        for (s in xmlStreamers) s.onStartTag(xmlNodeStartTag)
    }

    override fun onNamespaceEnd(tag: XmlNamespaceEndTag) {
        for (s in xmlStreamers) s.onNamespaceEnd(tag)
    }

    override fun onEndTag(xmlNodeEndTag: XmlNodeEndTag) {
        for (s in xmlStreamers) s.onEndTag(xmlNodeEndTag)
    }
}

/** Path of a launcher icon inside the APK (only `path`; no density in this trimmed version). */
// obf: C0072 (f157 = path)
data class IconPath(val path: String)

/**
 * Collects the `<application android:icon>` paths, one per resource config, in resources.arsc
 * order. Nothing else of the manifest is kept.
 */
// obf: C0086 (as ApkMetaTranslator: f178 = resourceTable, f179 = iconPaths, f180 = locale)
class ApkMetaTranslator(private val resourceTable: ResourceTable, private val locale: Locale?) : XmlStreamer {
    var iconPaths: List<IconPath> = emptyList()
        private set

    override fun onStartTag(xmlNodeStartTag: XmlNodeStartTag) {
        val attributes = xmlNodeStartTag.attributes
        if (xmlNodeStartTag.name!! == "application") {
            val iconAttr = attributes!!.get("icon") ?: return
            val resourceValue = iconAttr.typedValue
            if (resourceValue !is ReferenceResourceValue) {
                val value = iconAttr.value
                if (value != null) {
                    iconPaths = listOf(IconPath(value))
                }
                return
            }
            val resourceId = resourceValue.value.toLong() and 0xffffffffL
            val resources = resourceTable.getResourcesById(resourceId)
            if (resources.isEmpty()) return
            val icons = ArrayList<IconPath>()
            for (resource in resources) {
                // NPE (caught by the icon handler) when the entry renders as null.
                icons.add(IconPath(resource.resourceEntry.toStringValue(resourceTable, locale)!!))
            }
            iconPaths = icons
        }
    }

    override fun onNamespaceStart(tag: XmlNamespaceStartTag) {}
    override fun onNamespaceEnd(tag: XmlNamespaceEndTag) {}
    override fun onEndTag(xmlNodeEndTag: XmlNodeEndTag) {}
}

/**
 * Finds the foreground/background drawables of an `<adaptive-icon>` XML.
 *
 * `<foreground>`/`<background>` with a drawable/src attribute set the value directly; without one,
 * the next `<bitmap>` tag supplies it. `<animated-vector>` is treated like `<foreground>`.
 */
// obf: C0246 (f512 = pendingForeground, f513 = pendingBackground, f514 = background, f515 = foreground)
class AdaptiveIconParser : XmlStreamer {
    private var pendingForeground = false
    private var pendingBackground = false
    private var backgroundValue: String? = null

    // obf: f515
    var foreground: String? = null
        private set

    // obf: m261
    val background: String
        get() = backgroundValue ?: ""

    // obf: mo118
    override fun onStartTag(xmlNodeStartTag: XmlNodeStartTag) {
        when (xmlNodeStartTag.name ?: return) {
            "bitmap" -> {
                if (pendingForeground) {
                    foreground = getDrawable(xmlNodeStartTag)
                } else if (pendingBackground) {
                    backgroundValue = getDrawable(xmlNodeStartTag)
                }
            }
            "background" -> {
                val drawable = getDrawable(xmlNodeStartTag)
                if (drawable != null) {
                    backgroundValue = drawable
                } else {
                    pendingBackground = true
                }
            }
            "foreground", "animated-vector" -> {
                val drawable = getDrawable(xmlNodeStartTag)
                if (drawable != null) {
                    foreground = drawable
                } else {
                    pendingForeground = true
                }
            }
        }
    }

    override fun onNamespaceStart(tag: XmlNamespaceStartTag) {}
    override fun onNamespaceEnd(tag: XmlNamespaceEndTag) {}
    override fun onEndTag(xmlNodeEndTag: XmlNodeEndTag) {}

    companion object {
        /** Value of the first `drawable` or `src` attribute. */
        // obf: m260
        fun getDrawable(xmlNodeStartTag: XmlNodeStartTag): String? {
            val attributes = xmlNodeStartTag.attributes!!.attributes
            for (attribute in attributes) {
                if (attribute == null) continue
                if (attribute.name == "drawable" || attribute.name == "src") {
                    return attribute.value
                }
            }
            return null
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Binary XML parser
// ---------------------------------------------------------------------------------------------

// obf: C0258 (f546 = buffer, f548 = resourceTable, f544 = stringPool, f543 = resourceMap,
//             f545 = xmlStreamer, f547 = locale)
class BinaryXmlParser(buffer: ByteBuffer, private val resourceTable: ResourceTable) {
    private val buffer: ByteBuffer = buffer.duplicate().also { it.order(ByteOrder.LITTLE_ENDIAN) }
    private var stringPool: StringPool? = null
    private var resourceMap: Array<String?>? = null
    var xmlStreamer: XmlStreamer? = null
    var locale: Locale = Locale("", "")

    /** R8 inlined this into ApkFile.parseBinaryXml (C0018.m42). */
    fun parse() {
        // RES_XML_TYPE header (ignored)
        if (readChunkHeader() == null) return
        val stringPoolHeader = readChunkHeader() ?: return
        if (stringPoolHeader.chunkType.toInt() != 1) {
            throw ParserException(
                "Expect chunk type:" + Integer.toHexString(1) + ", but got:" +
                    Integer.toHexString(stringPoolHeader.chunkType.toInt())
            )
        }
        stringPool = ParseUtils.readStringPool(buffer, stringPoolHeader as StringPoolHeader, false)
        var chunkHeader = readChunkHeader() ?: return
        var header: ChunkHeader? = chunkHeader
        if (chunkHeader.chunkType.toInt() == 0x0180) { // XML_RESOURCE_MAP
            val count = (chunkHeader as XmlResourceMapHeader).bodySize / 4
            val resourceIds = LongArray(count)
            for (i in 0 until count) {
                resourceIds[i] = Buffers.readUInt(buffer)
            }
            val map = arrayOfNulls<String>(count)
            resourceMap = map
            for (i in 0 until count) {
                map[i] = "AttrId:0x" + java.lang.Long.toHexString(resourceIds[i])
            }
            header = readChunkHeader()
        }
        while (header != null) {
            val beginPos = buffer.position().toLong()
            when (val type = header.chunkType.toInt()) {
                0x0100 -> { // XML_START_NAMESPACE
                    val prefixRef = buffer.int
                    val uriRef = buffer.int
                    val tag = XmlNamespaceStartTag()
                    if (prefixRef > 0) tag.prefix = stringPool!!.get(prefixRef)
                    if (uriRef > 0) tag.uri = stringPool!!.get(uriRef)
                    xmlStreamer!!.onNamespaceStart(tag)
                }
                0x0101 -> { // XML_END_NAMESPACE
                    val prefixRef = buffer.int
                    val uriRef = buffer.int
                    val tag = XmlNamespaceEndTag()
                    if (prefixRef > 0) tag.prefix = stringPool!!.get(prefixRef)
                    if (uriRef > 0) tag.uri = stringPool!!.get(uriRef)
                    xmlStreamer!!.onNamespaceEnd(tag)
                }
                0x0102 -> { // XML_START_ELEMENT
                    val nsRef = buffer.int
                    val nameRef = buffer.int
                    val startTag = XmlNodeStartTag()
                    if (nsRef > 0) startTag.namespace = stringPool!!.get(nsRef)
                    startTag.name = stringPool!!.get(nameRef)
                    Buffers.readUShort(buffer) // attributeStart
                    Buffers.readUShort(buffer) // attributeSize
                    val attributeCount = Buffers.readUShort(buffer)
                    Buffers.readUShort(buffer) // idIndex
                    Buffers.readUShort(buffer) // classIndex
                    Buffers.readUShort(buffer) // styleIndex
                    val attributes = Attributes(attributeCount)
                    for (count in 0 until attributeCount) {
                        val attribute = readAttribute()
                        if (xmlStreamer != null) {
                            var value = attribute.rawValue
                            if (value == null) {
                                value = attribute.typedValue?.toStringValue(resourceTable, locale) ?: ""
                            }
                            attribute.value = value
                            attributes.attributes[count] = attribute
                        }
                    }
                    startTag.attributes = attributes
                    xmlStreamer?.onStartTag(startTag)
                }
                0x0103 -> { // XML_END_ELEMENT
                    val endTag = XmlNodeEndTag()
                    val nsRef = buffer.int
                    val nameRef = buffer.int
                    if (nsRef > 0) endTag.namespace = stringPool!!.get(nsRef)
                    endTag.name = stringPool!!.get(nameRef)
                    xmlStreamer?.onEndTag(endTag)
                }
                0x0104 -> { // XML_CDATA: read and dropped
                    val dataRef = buffer.int
                    if (dataRef > 0) stringPool!!.get(dataRef)
                    ParseUtils.readResValue(buffer, stringPool)
                }
                else -> {
                    if (type < 0x0100 || type > 0x017f) {
                        throw ParserException("Unexpected chunk type:$type")
                    }
                    Buffers.position(buffer, buffer.position() + header.bodySize)
                }
            }
            Buffers.position(buffer, beginPos + header.bodySize)
            header = readChunkHeader()
        }
    }

    /** Inlined into the start-element case by R8. */
    private fun readAttribute(): Attribute {
        val nsRef = buffer.int
        val nameRef = buffer.int
        val attribute = Attribute()
        if (nsRef > 0) attribute.namespace = stringPool!!.get(nsRef)
        attribute.name = stringPool!!.get(nameRef)
        val map = resourceMap
        if (attribute.name!!.isEmpty() && map != null && nameRef < map.size) {
            attribute.name = map[nameRef]
        }
        val rawValueRef = buffer.int
        if (rawValueRef > 0) attribute.rawValue = stringPool!!.get(rawValueRef)
        attribute.typedValue = ParseUtils.readResValue(buffer, stringPool)
        return attribute
    }

    // obf: m263
    private fun readChunkHeader(): ChunkHeader? {
        if (!buffer.hasRemaining()) return null
        val begin = buffer.position().toLong()
        val chunkType = Buffers.readUShort(buffer)
        val headerSize = Buffers.readUShort(buffer)
        val chunkSize = Buffers.readUInt(buffer)
        when (chunkType) {
            0 -> return PlainChunkHeader(chunkType, headerSize, chunkSize) // NULL
            1 -> { // STRING_POOL
                val header = StringPoolHeader(1, headerSize, chunkSize)
                header.stringCount = Buffers.checkedUInt(Buffers.readUInt(buffer))
                Buffers.readUInt(buffer) // styleCount
                header.flags = Buffers.readUInt(buffer)
                header.stringsStart = Buffers.readUInt(buffer)
                Buffers.readUInt(buffer) // stylesStart
                Buffers.position(buffer, begin + headerSize)
                return header
            }
            3 -> return PlainChunkHeader(chunkType, headerSize, chunkSize) // XML
            0x0180 -> { // XML_RESOURCE_MAP
                Buffers.position(buffer, begin + headerSize)
                return XmlResourceMapHeader(chunkType, headerSize, chunkSize)
            }
            0x0100, 0x0101, 0x0102, 0x0103, 0x0104 -> {
                val header = PlainChunkHeader(chunkType, headerSize, chunkSize)
                Buffers.readUInt(buffer) // lineNum
                Buffers.readUInt(buffer) // commentRef
                Buffers.position(buffer, begin + headerSize)
                return header
            }
            else -> throw ParserException("Unexpected chunk type:$chunkType")
        }
    }
}
