package com.jcapps.adbremote.compat

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import com.jcapps.adbremote.provider.AppProvider

// Parcel helpers used by the provider Parcelables (MaterialFiles compat/ParcelCompat.kt). The
// artifact proves the file name via the checkNotNull message
// "...com.jcapps.adbremote.compat.ParcelCompatKt.readParcelableListCompat".

// obf: AbstractC1982.f8102 — the app class loader used for nested Parcelables
@JvmField
val appClassLoader: ClassLoader = AppProvider::class.java.classLoader!!

fun Parcel.readBooleanCompat(): Boolean = ParcelCompat.readBoolean(this)

fun Parcel.writeBooleanCompat(value: Boolean) {
    ParcelCompat.writeBoolean(this, value)
}

@Suppress("DEPRECATION")
fun <T : Parcelable> Parcel.readParcelable(): T? = readParcelable(appClassLoader)

fun <L : MutableList<T>, T : Parcelable> Parcel.readParcelableListCompat(list: L): L =
    readParcelableListCompat(list, appClassLoader)

@Suppress("DEPRECATION", "UNCHECKED_CAST")
fun <L : MutableList<T>, T : Parcelable> Parcel.readParcelableListCompat(
    list: L,
    classLoader: ClassLoader?,
): L {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return readParcelableList(list, classLoader) as L
    }
    val size = readInt()
    if (size == -1) {
        list.clear()
        return list
    }
    val listSize = list.size
    for (index in 0 until size) {
        val parcelable = readParcelable<Parcelable>(classLoader) as T
        if (index < listSize) {
            list[index] = parcelable
        } else {
            list += parcelable
        }
    }
    if (size < listSize) {
        list.subList(size, listSize).clear()
    }
    return list
}

fun <T : Parcelable> Parcel.writeParcelableListCompat(list: List<T>?, flags: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeParcelableList(list, flags)
        return
    }
    if (list == null) {
        writeInt(-1)
        return
    }
    writeInt(list.size)
    for (parcelable in list) {
        writeParcelable(parcelable, flags)
    }
}
