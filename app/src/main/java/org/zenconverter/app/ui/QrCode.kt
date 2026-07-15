package org.zenconverter.app.ui

internal class QrCode(
    val size: Int,
    private val modules: Array<BooleanArray>
) {
    fun isDark(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        fun encode(text: String): QrCode {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val version = versions.firstOrNull { version ->
                val requiredBits = 4 + 8 + bytes.size * 8
                requiredBits <= version.dataCodewords * 8
            } ?: error("QR payload is too long")

            val buffer = BitBuffer()
            buffer.append(0b0100, 4)
            buffer.append(bytes.size, 8)
            bytes.forEach { value -> buffer.append(value.toInt() and 0xFF, 8) }

            val capacityBits = version.dataCodewords * 8
            buffer.append(0, minOf(4, capacityBits - buffer.size))
            while (buffer.size % 8 != 0) {
                buffer.append(0, 1)
            }

            var padCodeword = 0xEC
            while (buffer.size < capacityBits) {
                buffer.append(padCodeword, 8)
                padCodeword = if (padCodeword == 0xEC) 0x11 else 0xEC
            }

            return QrBuilder(version).build(buffer.toCodewords())
        }
    }
}

private data class QrVersion(
    val number: Int,
    val dataCodewords: Int,
    val errorCorrectionCodewords: Int
)

private val versions = listOf(
    QrVersion(number = 1, dataCodewords = 19, errorCorrectionCodewords = 7),
    QrVersion(number = 2, dataCodewords = 34, errorCorrectionCodewords = 10),
    QrVersion(number = 3, dataCodewords = 55, errorCorrectionCodewords = 15),
    QrVersion(number = 4, dataCodewords = 80, errorCorrectionCodewords = 20)
)

private class BitBuffer {
    private val bits = mutableListOf<Int>()

    val size: Int
        get() = bits.size

    fun append(value: Int, bitCount: Int) {
        for (i in bitCount - 1 downTo 0) {
            bits += (value ushr i) and 1
        }
    }

    fun toCodewords(): IntArray {
        return IntArray(bits.size / 8) { index ->
            var value = 0
            repeat(8) { bit ->
                value = (value shl 1) or bits[index * 8 + bit]
            }
            value
        }
    }
}

private class QrBuilder(
    private val version: QrVersion
) {
    private val size = version.number * 4 + 17
    private val modules = Array(size) { BooleanArray(size) }
    private val reserved = Array(size) { BooleanArray(size) }

    fun build(dataCodewords: IntArray): QrCode {
        drawFunctionPatterns()
        drawFormatBits(0)
        drawCodewords(dataCodewords + reedSolomonRemainder(dataCodewords, version.errorCorrectionCodewords))

        val baseModules = copyModules(modules)
        var bestModules = baseModules
        var bestPenalty = Int.MAX_VALUE

        for (mask in 0..7) {
            val candidate = copyModules(baseModules)
            applyMask(candidate, mask)
            drawFormatBits(candidate, mask)
            val penalty = penalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestModules = candidate
            }
        }

        return QrCode(size, bestModules)
    }

    private fun drawFunctionPatterns() {
        drawFinderPattern(0, 0)
        drawFinderPattern(size - 7, 0)
        drawFinderPattern(0, size - 7)

        for (i in 0 until size) {
            if (!reserved[6][i]) {
                setFunctionModule(i, 6, i % 2 == 0)
            }
            if (!reserved[i][6]) {
                setFunctionModule(6, i, i % 2 == 0)
            }
        }

        if (version.number > 1) {
            val positions = intArrayOf(6, size - 7)
            for (x in positions) {
                for (y in positions) {
                    if (!reserved[y][x]) {
                        drawAlignmentPattern(x, y)
                    }
                }
            }
        }

        setFunctionModule(8, size - 8, true)
    }

    private fun drawFinderPattern(left: Int, top: Int) {
        for (dy in -1..7) {
            for (dx in -1..7) {
                val x = left + dx
                val y = top + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val isInside = dx in 0..6 && dy in 0..6
                val isDark = isInside &&
                    (dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx in 2..4 && dy in 2..4))
                setFunctionModule(x, y, isDark)
            }
        }
    }

    private fun drawAlignmentPattern(centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunctionModule(centerX + dx, centerY + dy, distance != 1)
            }
        }
    }

    private fun drawFormatBits(mask: Int) {
        drawFormatBits(modules, mask)
        markFormatBitsReserved()
    }

    private fun drawFormatBits(target: Array<BooleanArray>, mask: Int) {
        val bits = formatBits(mask)
        for (i in 0..5) setModule(target, 8, i, bit(bits, i))
        setModule(target, 8, 7, bit(bits, 6))
        setModule(target, 8, 8, bit(bits, 7))
        setModule(target, 7, 8, bit(bits, 8))
        for (i in 9 until 15) setModule(target, 14 - i, 8, bit(bits, i))

        for (i in 0 until 8) setModule(target, size - 1 - i, 8, bit(bits, i))
        for (i in 8 until 15) setModule(target, 8, size - 15 + i, bit(bits, i))
        setModule(target, 8, size - 8, true)
    }

    private fun markFormatBitsReserved() {
        for (i in 0..5) reserved[i][8] = true
        reserved[7][8] = true
        reserved[8][8] = true
        reserved[8][7] = true
        for (i in 9 until 15) reserved[8][14 - i] = true

        for (i in 0 until 8) reserved[8][size - 1 - i] = true
        for (i in 8 until 15) reserved[size - 15 + i][8] = true
        reserved[size - 8][8] = true
    }

    private fun drawCodewords(codewords: IntArray) {
        var bitIndex = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            val upward = ((right + 1) and 2) == 0
            for (vertical in 0 until size) {
                val y = if (upward) size - 1 - vertical else vertical
                for (columnOffset in 0..1) {
                    val x = right - columnOffset
                    if (!reserved[y][x]) {
                        val isDark = bitIndex < codewords.size * 8 &&
                            bit(codewords[bitIndex / 8], 7 - bitIndex % 8)
                        modules[y][x] = isDark
                        bitIndex++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(target: Array<BooleanArray>, mask: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!reserved[y][x] && maskApplies(mask, x, y)) {
                    target[y][x] = !target[y][x]
                }
            }
        }
    }

    private fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
        setModule(modules, x, y, isDark)
        reserved[y][x] = true
    }
}

private fun setModule(target: Array<BooleanArray>, x: Int, y: Int, isDark: Boolean) {
    target[y][x] = isDark
}

private fun bit(value: Int, index: Int): Boolean {
    return ((value ushr index) and 1) != 0
}

private fun formatBits(mask: Int): Int {
    val errorCorrectionLevel = 1
    val data = (errorCorrectionLevel shl 3) or mask
    var remainder = data
    repeat(10) {
        remainder = (remainder shl 1) xor (((remainder ushr 9) and 1) * 0x537)
    }
    return ((data shl 10) or remainder) xor 0x5412
}

private fun maskApplies(mask: Int, x: Int, y: Int): Boolean {
    return when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> (x / 3 + y / 2) % 2 == 0
        5 -> (x * y) % 2 + (x * y) % 3 == 0
        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
        else -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
    }
}

private fun reedSolomonRemainder(data: IntArray, degree: Int): IntArray {
    val generator = reedSolomonGenerator(degree)
    val result = IntArray(degree)
    for (value in data) {
        val factor = value xor result[0]
        for (i in 0 until degree - 1) {
            result[i] = result[i + 1]
        }
        result[degree - 1] = 0
        for (i in 0 until degree) {
            result[i] = result[i] xor gfMultiply(generator[i + 1], factor)
        }
    }
    return result
}

private fun reedSolomonGenerator(degree: Int): IntArray {
    var result = intArrayOf(1)
    for (i in 0 until degree) {
        val next = IntArray(result.size + 1)
        for (j in result.indices) {
            next[j] = next[j] xor result[j]
            next[j + 1] = next[j + 1] xor gfMultiply(result[j], gfPower(i))
        }
        result = next
    }
    return result
}

private fun gfPower(power: Int): Int {
    var value = 1
    repeat(power) {
        value = value shl 1
        if (value and 0x100 != 0) {
            value = value xor 0x11D
        }
    }
    return value
}

private fun gfMultiply(left: Int, right: Int): Int {
    var x = left
    var y = right
    var result = 0
    while (y != 0) {
        if (y and 1 != 0) {
            result = result xor x
        }
        x = x shl 1
        if (x and 0x100 != 0) {
            x = x xor 0x11D
        }
        y = y ushr 1
    }
    return result and 0xFF
}

private fun penalty(modules: Array<BooleanArray>): Int {
    val size = modules.size
    var result = 0

    for (y in 0 until size) {
        var runColor = modules[y][0]
        var runLength = 1
        for (x in 1 until size) {
            if (modules[y][x] == runColor) {
                runLength++
            } else {
                if (runLength >= 5) result += runLength - 2
                runColor = modules[y][x]
                runLength = 1
            }
        }
        if (runLength >= 5) result += runLength - 2
    }

    for (x in 0 until size) {
        var runColor = modules[0][x]
        var runLength = 1
        for (y in 1 until size) {
            if (modules[y][x] == runColor) {
                runLength++
            } else {
                if (runLength >= 5) result += runLength - 2
                runColor = modules[y][x]
                runLength = 1
            }
        }
        if (runLength >= 5) result += runLength - 2
    }

    for (y in 0 until size - 1) {
        for (x in 0 until size - 1) {
            val color = modules[y][x]
            if (color == modules[y][x + 1] &&
                color == modules[y + 1][x] &&
                color == modules[y + 1][x + 1]
            ) {
                result += 3
            }
        }
    }

    val darkCount = modules.sumOf { row -> row.count { it } }
    val total = size * size
    val variance = kotlin.math.abs(darkCount * 20 - total * 10) / total
    return result + variance * 10
}

private fun copyModules(source: Array<BooleanArray>): Array<BooleanArray> {
    return Array(source.size) { row -> source[row].copyOf() }
}
