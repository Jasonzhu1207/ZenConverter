package org.zenconverter.app.audio

/**
 * Iterative radix-2 Cooley-Tukey FFT. Kept as a small standalone utility so the
 * spectrum and key detectors share one implementation instead of each carrying a
 * copy. [real] and [imaginary] must have the same power-of-two length.
 */
object Fft {
    fun transform(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        require(n == imaginary.size) { "real and imaginary must be the same length" }
        require(n > 0 && n and (n - 1) == 0) { "length must be a power of two" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imaginary[i] = imaginary[j].also { imaginary[j] = imaginary[i] }
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * Math.PI / length
            val wReal = kotlin.math.cos(angle)
            val wImaginary = kotlin.math.sin(angle)
            var i = 0
            while (i < n) {
                var currentReal = 1.0
                var currentImaginary = 0.0
                for (k in 0 until length / 2) {
                    val evenIndex = i + k
                    val oddIndex = i + k + length / 2
                    val oddReal = real[oddIndex] * currentReal - imaginary[oddIndex] * currentImaginary
                    val oddImaginary = real[oddIndex] * currentImaginary + imaginary[oddIndex] * currentReal
                    real[oddIndex] = real[evenIndex] - oddReal
                    imaginary[oddIndex] = imaginary[evenIndex] - oddImaginary
                    real[evenIndex] += oddReal
                    imaginary[evenIndex] += oddImaginary
                    val nextReal = currentReal * wReal - currentImaginary * wImaginary
                    currentImaginary = currentReal * wImaginary + currentImaginary * wReal
                    currentReal = nextReal
                }
                i += length
            }
            length = length shl 1
        }
    }

    /** Magnitude spectrum of the first [n/2] bins for a Hann-windowed frame. */
    fun magnitudes(frame: DoubleArray, fftSize: Int): DoubleArray {
        val real = DoubleArray(fftSize)
        val imaginary = DoubleArray(fftSize)
        val usable = minOf(frame.size, fftSize)
        for (index in 0 until usable) {
            val window = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * index / (fftSize - 1))
            real[index] = frame[index] * window
        }
        transform(real, imaginary)
        val bins = fftSize / 2
        return DoubleArray(bins) { bin ->
            kotlin.math.sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
        }
    }
}
