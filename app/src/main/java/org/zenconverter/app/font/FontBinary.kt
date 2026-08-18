package org.zenconverter.app.font

import java.io.ByteArrayOutputStream

internal fun readUInt16BE(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}

internal fun readUInt32BE(bytes: ByteArray, offset: Int): Long {
    return ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)
}

internal fun ByteArrayOutputStream.writeUInt16BE(value: Int) {
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

internal fun ByteArrayOutputStream.writeUInt32BE(value: Long) {
    write(((value ushr 24) and 0xFF).toInt())
    write(((value ushr 16) and 0xFF).toInt())
    write(((value ushr 8) and 0xFF).toInt())
    write((value and 0xFF).toInt())
}
