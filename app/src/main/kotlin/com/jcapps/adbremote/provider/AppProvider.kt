package com.jcapps.adbremote.provider

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * No-op [ContentProvider] used purely as an app startup hook: [onCreate] runs each registered
 * initializer function (obf list `AbstractC0880.f5033`). All data operations are unsupported.
 */
// obf: AppProvider (original FQN kept)
class AppProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        // (original: `context as Application` cast check; value unused)
        @Suppress("UNUSED_VARIABLE")
        val application = context as Application
        for (initializer in appInitializers) {
            initializer()
        }
        return true
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException()
    }

    override fun getType(uri: Uri): String {
        throw UnsupportedOperationException()
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        throw UnsupportedOperationException()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        throw UnsupportedOperationException()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        throw UnsupportedOperationException()
    }
}
