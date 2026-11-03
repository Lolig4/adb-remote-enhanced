package com.jcapps.adbserver

import com.google.gson.Gson

/**
 * Shared state handed to every endpoint: the Gson instance used for all JSON responses and the
 * token every request must carry in its `token` header.
 */
// obf: C0174 (fields: f364 = gson, f363 = token)
data class Param(val gson: Gson, val token: String)
