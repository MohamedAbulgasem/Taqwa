package world.taqwa.app.wear.link

import java.io.File
import java.io.IOException

/**
 * The epoch second [file] holds, as [writeStamp] wrote it, or null when there is none to read: no
 * file, an unreadable one, or anything but a number.
 */
internal fun readStamp(file: File): Long? =
    try {
        file.readText(Charsets.UTF_8).trim().toLongOrNull()
    } catch (_: IOException) {
        null
    }

/**
 * Writes the epoch second [at] into [file]; false when it could not. It never throws: a stamp that
 * is not written only means the next chance does its work again.
 */
internal fun writeStamp(file: File, at: Long): Boolean =
    try {
        file.writeText(at.toString(), Charsets.UTF_8)
        true
    } catch (_: IOException) {
        false
    }
