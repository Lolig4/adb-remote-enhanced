package com.jcapps.adbserver.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Resource values
// ---------------------------------------------------------------------------------------------

// obf: AbstractC0002 (f2 = value)
abstract class ResourceValue(val value: Int) {
    // obf: mo1
    abstract fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String?
}

// obf: C0054
class ReferenceResourceValue(value: Int) : ResourceValue(value) {
    // obf: mo1
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String? {
        val resourceId = value.toLong() and 0xffffffffL
        if (resourceId > 16973824 && resourceId < 16977920) {
            return "@android:style/$resourceId"
        }
        val raw = "resourceId:0x" + java.lang.Long.toHexString(resourceId)
        if (resourceTable == null) return raw
        var selected: ResourceEntry? = null
        var typeSpec: TypeSpec? = null
        // Keeps the LAST matching config.
        for (resource in resourceTable.getResourcesById(resourceId)) {
            typeSpec = resource.typeSpec
            selected = resource.resourceEntry
        }
        if (selected == null) return raw
        if (locale != null) {
            // BUG(original, inherited from apk-parser): a reference cycle recurses until
            // StackOverflowError (an Error, not caught by the handlers' catch (Exception)).
            return selected.toStringValue(resourceTable, locale)
        }
        return "@" + typeSpec!!.name + '/' + selected.key
    }
}

// obf: C0087 mode 0 (static instance f181 = new C0087(-1, 0))
class EmptyResourceValue private constructor() : ResourceValue(-1) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String = ""

    companion object {
        // obf: C0087.f181
        val INSTANCE = EmptyResourceValue()
    }
}

// obf: C0087 mode 1
class BooleanResourceValue(value: Int) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String =
        (value != 0).toString()
}

// obf: C0087 mode 2
class DecimalResourceValue(value: Int) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String = value.toString()
}

// obf: C0087 mode 3
class DimensionResourceValue(value: Int) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String {
        val unit = (value and 0xff).toShort().toInt()
        val unitStr = when (unit) {
            5 -> "mm"
            0 -> "px"
            1 -> "dp"
            2 -> "sp"
            3 -> "pt"
            4 -> "in"
            else -> "unknown unit:0x" + Integer.toHexString(unit)
        }
        return (value shr 8).toString() + unitStr
    }
}

// obf: C0087 mode 4
class FloatResourceValue(value: Int) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String =
        java.lang.Float.intBitsToFloat(value).toString()
}

// obf: C0087 mode 5
class FractionResourceValue(value: Int) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String {
        val type = (value and 15).toShort().toInt()
        val pstr = when (type) {
            0 -> "%"
            1 -> "%p"
            else -> "unknown type:0x" + Integer.toHexString(type)
        }
        // BUG(original, inherited from apk-parser): intBitsToFloat of the shifted bits is not how
        // complex fractions are decoded. Unused for icons.
        return java.lang.Float.intBitsToFloat(value shr 4).toString() + pstr
    }
}

// obf: C0087 mode 6
class HexResourceValue(value: Int) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String =
        "0x" + Integer.toHexString(value)
}

// obf: C0203 (f431 = dataType)
class RawResourceValue(value: Int, val dataType: Short) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String =
        "{" + dataType.toInt() + ':' + (value.toLong() and 0xffffffffL) + '}'
}

// obf: C0331 (f731 = stringPool)
class StringResourceValue(value: Int, val stringPool: StringPool) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String? {
        if (value >= 0) return stringPool.get(value)
        return null
    }
}

// obf: C0189 (f415 = len)
class RGBResourceValue(value: Int, val len: Int) : ResourceValue(value) {
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String {
        val sb = StringBuilder("#")
        // BUG(original, inherited from apk-parser): bytes are not zero-padded (0x0a -> "a").
        for (i in len / 2 - 1 downTo 0) {
            sb.append(Integer.toHexString((value shr (i * 8)) and 0xff))
        }
        return sb.toString()
    }
}

// ---------------------------------------------------------------------------------------------
// Resource table model
// ---------------------------------------------------------------------------------------------

// obf: C0209 (f436 = size, f435 = flags, f438 = key, f437 = value)
open class ResourceEntry {
    var size: Int = 0
    var flags: Int = 0
    var key: String? = null
    var value: ResourceValue? = null

    // obf: mo201
    open fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String? {
        val v = value ?: return "null"
        return v.toStringValue(resourceTable, locale)
    }
}

/**
 * Complex (bag) entry. R8 dropped every field of the map items, see [ResourceTableMap].
 */
// obf: C0155 (f322 = count, f321 = resourceTableMaps)
class ResourceMapEntry(resourceEntry: ResourceEntry) : ResourceEntry() {
    var count: Long = 0
    var resourceTableMaps: Array<ResourceTableMap> = emptyArray()

    init {
        size = resourceEntry.size
        flags = resourceEntry.flags
        key = resourceEntry.key
    }

    // obf: mo201
    override fun toStringValue(resourceTable: ResourceTable?, locale: Locale?): String? {
        if (resourceTableMaps.isEmpty()) return null
        return resourceTableMaps[0].toString()
    }
}

/**
 * Item of a [ResourceMapEntry]. In the dex it is an empty instance of the R8-merged class C0335
 * (mode 20) whose toString() is Object.toString(); nothing ever reads its data.
 * RECON-UNSURE: kept field-less to match the dex; its toString therefore yields the JVM default
 * "ClassName@hash" just like the original (never reached for icons: map entries have no value and
 * are filtered out by [ResourceTable.getResourcesById]).
 */
// obf: C0335 (mode 20)
class ResourceTableMap

// obf: C0176 (f368 = name, f369 = entryFlags)
class TypeSpec {
    var name: String? = null
    lateinit var entryFlags: LongArray
}

// obf: C0028 (f62 = buffer, f63 = keyStringPool, f64 = stringPool, f65 = offsets)
class Type {
    var buffer: ByteBuffer? = null
    var keyStringPool: StringPool? = null
    var stringPool: StringPool? = null
    lateinit var offsets: LongArray

    /** Inlined into ResourceTable.m287 by R8. */
    fun getResourceEntry(id: Int): ResourceEntry? {
        if (id >= offsets.size) return null
        val offset = offsets[id]
        if (offset == 0xffffffffL) return null
        Buffers.position(buffer, offset)
        val buf = buffer!!
        val beginPos = buf.position().toLong()
        val resourceEntry = ResourceEntry()
        resourceEntry.size = Buffers.readUShort(buffer)
        resourceEntry.flags = Buffers.readUShort(buffer)
        val keyRef = buffer!!.int.toLong()
        resourceEntry.key = keyStringPool!!.get(keyRef.toInt())
        if ((resourceEntry.flags and 1) != 0) {
            // FLAG_COMPLEX
            val mapEntry = ResourceMapEntry(resourceEntry)
            Buffers.readUInt(buffer) // parent
            mapEntry.count = Buffers.readUInt(buffer)
            Buffers.position(buffer, beginPos + resourceEntry.size)
            val count = mapEntry.count
            val maps = arrayOfNulls<ResourceTableMap>(count.toInt())
            var i = 0L
            while (i < count) {
                val map = ResourceTableMap()
                Buffers.readUInt(buffer) // name ref
                ParseUtils.readResValue(buffer, stringPool)
                maps[i.toInt()] = map
                i++
            }
            @Suppress("UNCHECKED_CAST")
            mapEntry.resourceTableMaps = maps as Array<ResourceTableMap>
            return mapEntry
        }
        Buffers.position(buffer, beginPos + resourceEntry.size)
        resourceEntry.value = ParseUtils.readResValue(buffer, stringPool)
        return resourceEntry
    }
}

// obf: C0004 (f5 = typeSpec, f4 = resourceEntry; the type argument is not kept)
class Resource(val typeSpec: TypeSpec, @Suppress("UNUSED_PARAMETER") type: Type, val resourceEntry: ResourceEntry)

// obf: C0300 (f645 = id, f644 = typeStringPool, f647 = keyStringPool, f646 = typeSpecsMap, f648 = typesMap)
class ResourcePackage(header: PackageHeader) {
    val id: Short = header.id.toShort()
    var typeStringPool: StringPool? = null
    var keyStringPool: StringPool? = null
    val typeSpecsMap: HashMap<Short, TypeSpec> = HashMap()
    val typesMap: HashMap<Short, MutableList<Type>> = HashMap()
}

/** obf: C0283 mode 10 (f604 = HashMap<Short, ResourcePackage>) */
// obf: C0283 (mode 10)
class ResourceTable {
    val packageMap: HashMap<Short, ResourcePackage> = HashMap()

    // obf: m287
    fun getResourcesById(resourceId: Long): List<Resource> {
        val packageId = ((resourceId shr 24) and 0xff).toShort()
        val typeId = ((resourceId shr 16) and 0xff).toShort()
        val entryIndex = (resourceId and 0xffff).toInt()
        val resourcePackage = packageMap[packageId] ?: return emptyList()
        val typeSpec = resourcePackage.typeSpecsMap[typeId]
        val types = resourcePackage.typesMap[typeId]
        if (typeSpec == null || types == null) return emptyList()
        if (entryIndex >= typeSpec.entryFlags.size) return emptyList()
        val result = ArrayList<Resource>()
        for (type in types) {
            val resourceEntry = type.getResourceEntry(entryIndex)
            val value = resourceEntry?.value ?: continue
            if (value is ReferenceResourceValue && resourceId == (value.value.toLong() and 0xffffffffL)) {
                continue
            }
            result.add(Resource(typeSpec, type, resourceEntry))
        }
        return result
    }
}

// ---------------------------------------------------------------------------------------------
// resources.arsc parser
// ---------------------------------------------------------------------------------------------

// obf: C0026 (f57 = buffer, f55 = byteOrder, f54 = stringPool, f56 = resourceTable)
class ResourceTableParser(buffer: ByteBuffer) {
    private val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    private val buffer: ByteBuffer = buffer.duplicate().also { it.order(byteOrder) }
    private var stringPool: StringPool? = null
    var resourceTable: ResourceTable? = null
        private set

    /** R8 inlined this (and readPackage) into ApkFile.parseResourceTable (C0018.m43). */
    fun parse() {
        val resourceTableHeader = readChunkHeader() as ResourceTableHeader
        val stringPoolHeader = readChunkHeader() as StringPoolHeader
        stringPool = ParseUtils.readStringPool(buffer, stringPoolHeader, true)
        resourceTable = ResourceTable()
        if (resourceTableHeader.packageCount != 0) {
            var packageHeader = readChunkHeader() as PackageHeader
            for (i in 0 until resourceTableHeader.packageCount) {
                val next = readPackage(packageHeader)
                resourceTable!!.packageMap[next.first.id] = next.first
                if (next.second != null) {
                    packageHeader = next.second!!
                }
            }
        }
    }

    private fun readPackage(packageHeader: PackageHeader): Pair<ResourcePackage, PackageHeader?> {
        val resourcePackage = ResourcePackage(packageHeader)
        val beginPos = buffer.position().toLong()
        // The type and key string pools are skipped and replaced by EMPTY pools (as in the dex):
        // type names resolve to "" and entry keys to "".
        if (packageHeader.typeStrings > 0) {
            Buffers.position(buffer, packageHeader.typeStrings + beginPos - packageHeader.headerSize)
            val header = readChunkHeader() as StringPoolHeader
            Buffers.position(buffer, buffer.position() + header.bodySize)
            resourcePackage.typeStringPool = StringPool(0)
        }
        if (packageHeader.keyStrings > 0) {
            Buffers.position(buffer, beginPos + packageHeader.keyStrings - packageHeader.headerSize)
            val header = readChunkHeader() as StringPoolHeader
            Buffers.position(buffer, buffer.position() + header.bodySize)
            resourcePackage.keyStringPool = StringPool(0)
        }
        while (buffer.hasRemaining()) {
            val chunkHeader = readChunkHeader()
            val chunkBegin = buffer.position().toLong()
            when (chunkHeader.chunkType.toInt()) {
                0 -> Buffers.position(buffer, buffer.position() + buffer.remaining()) // NULL
                0x0202 -> { // TABLE_TYPE_SPEC
                    val typeSpecHeader = chunkHeader as TypeSpecHeader
                    val entryFlags = LongArray(typeSpecHeader.entryCount)
                    for (i in 0 until typeSpecHeader.entryCount) {
                        entryFlags[i] = Buffers.readUInt(buffer)
                    }
                    val typeSpec = TypeSpec()
                    val typeId = (typeSpecHeader.id.toInt() and 0xff).toShort()
                    typeSpec.entryFlags = entryFlags
                    typeSpec.name = resourcePackage.typeStringPool?.get(typeSpecHeader.id - 1)
                    resourcePackage.typeSpecsMap[typeId] = typeSpec
                    Buffers.position(buffer, chunkBegin + typeSpecHeader.bodySize)
                }
                0x0201 -> { // TABLE_TYPE
                    val typeHeader = chunkHeader as TypeHeader
                    val offsets = LongArray(typeHeader.entryCount)
                    for (i in 0 until typeHeader.entryCount) {
                        offsets[i] = Buffers.readUInt(buffer)
                    }
                    val type = Type()
                    val typeId = (typeHeader.id.toInt() and 0xff).toShort()
                    // Type name lookup kept for its side effects only (result unused in the dex).
                    resourcePackage.typeStringPool!!.get((typeHeader.id.toInt() and 0xff).toShort() - 1)
                    Buffers.position(buffer, typeHeader.entriesStart + chunkBegin - typeHeader.headerSize)
                    val slice = buffer.slice()
                    slice.order(byteOrder)
                    type.buffer = slice
                    type.keyStringPool = resourcePackage.keyStringPool
                    type.offsets = offsets
                    type.stringPool = stringPool
                    resourcePackage.typesMap.getOrPut(typeId) { ArrayList() }.add(type)
                    Buffers.position(buffer, chunkBegin + typeHeader.bodySize)
                }
                0x0200 -> return Pair(resourcePackage, chunkHeader as PackageHeader) // next package
                0x0203 -> Buffers.position(buffer, chunkBegin + chunkHeader.bodySize) // TABLE_LIBRARY
                // BUG(original, inherited from apk-parser): says 0x but prints decimal.
                else -> throw ParserException("unexpected chunk type: 0x" + chunkHeader.chunkType.toInt())
            }
        }
        return Pair(resourcePackage, null)
    }

    // obf: m54
    private fun readChunkHeader(): ChunkHeader {
        val begin = buffer.position().toLong()
        val chunkType = Buffers.readUShort(buffer)
        val headerSize = Buffers.readUShort(buffer)
        val chunkSize = Buffers.readUInt(buffer).toInt().toLong() // dex: (int) m359
        when (chunkType) {
            0 -> { /* falls through to the PlainChunkHeader below */ }
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
            2 -> { // TABLE
                val header = ResourceTableHeader(2, headerSize, chunkSize)
                header.packageCount = Buffers.readUInt(buffer).toInt()
                Buffers.position(buffer, begin + headerSize)
                return header
            }
            0x0200 -> { // TABLE_PACKAGE
                val header = PackageHeader(0x0200, headerSize, chunkSize)
                header.id = Buffers.readUInt(buffer).toInt()
                val name = Buffers.readString(buffer, 128)
                val nul = name.indexOf('\u0000')
                if (nul >= 0) name.substring(0, nul) // package name is not kept
                header.typeStrings = Buffers.checkedUInt(Buffers.readUInt(buffer))
                Buffers.readUInt(buffer) // lastPublicType
                header.keyStrings = Buffers.checkedUInt(Buffers.readUInt(buffer))
                Buffers.readUInt(buffer) // lastPublicKey
                Buffers.position(buffer, begin + headerSize)
                return header
            }
            0x0201 -> { // TABLE_TYPE
                val header = TypeHeader(0x0201, headerSize, chunkSize)
                header.id = Buffers.readUByte(buffer).toByte()
                Buffers.readUByte(buffer)  // res0
                Buffers.readUShort(buffer) // res1
                header.entryCount = Buffers.checkedUInt(Buffers.readUInt(buffer))
                header.entriesStart = Buffers.checkedUInt(Buffers.readUInt(buffer))
                // ResTable_config: read and discarded (no config/density/locale selection at all).
                val configBegin = buffer.position().toLong()
                val configSize = Buffers.readUInt(buffer)
                buffer.short // mcc
                buffer.short // mnc
                val language = ByteArray(2)
                buffer.get(language)
                String(language, Charsets.UTF_8).replace(" ", "")
                val country = ByteArray(2)
                buffer.get(country)
                String(country, Charsets.UTF_8).replace(" ", "")
                Buffers.readUByte(buffer)  // orientation
                Buffers.readUByte(buffer)  // touchscreen
                Buffers.readUShort(buffer) // density
                val skip = (configSize - (buffer.position().toLong() - configBegin)).toInt()
                Buffers.position(buffer, buffer.position() + skip)
                Buffers.position(buffer, begin + headerSize)
                return header
            }
            0x0202 -> { // TABLE_TYPE_SPEC
                val header = TypeSpecHeader(0x0202, headerSize, chunkSize)
                header.id = Buffers.readUByte(buffer).toByte()
                Buffers.readUByte(buffer)  // res0
                Buffers.readUShort(buffer) // res1
                header.entryCount = Buffers.checkedUInt(Buffers.readUInt(buffer))
                Buffers.position(buffer, begin + headerSize)
                return header
            }
            0x0203 -> { // TABLE_LIBRARY
                val header = PlainChunkHeader(0x0203, headerSize, chunkSize)
                Buffers.readUInt(buffer) // count
                Buffers.position(buffer, begin + headerSize)
                return header
            }
            0x0204 -> { /* TABLE_OVERLAYABLE: treated like NULL (ends the package) */ }
            else -> throw ParserException("Unexpected chunk Type: 0x" + Integer.toHexString(chunkType))
        }
        Buffers.position(buffer, begin + headerSize)
        return PlainChunkHeader(0, headerSize, chunkSize)
    }
}
