package com.queukat.train.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

object Dbg {
    fun isEnabled(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun d(
        context: Context,
        tag: String,
        msg: String,
    ) {
        if (isEnabled(context)) Log.d(tag, msg)
    }

    fun i(
        context: Context,
        tag: String,
        msg: String,
    ) {
        if (isEnabled(context)) Log.i(tag, msg)
    }

    fun w(
        context: Context,
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        if (!isEnabled(context)) return
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
    }
}
