package org.zenconverter.app.conversion

enum class PdfSplitValidationError {
    SinglePagePdf,
    InvalidStartPage,
    InvalidEndPage,
    EndBeforeStart,
    StartExceedsPageCount,
    EndExceedsPageCount,
    SplitPointsOrder,
    SplitPointsOutOfRange,
    NoSplitOrTrimConfigured
}

object PdfSplitValidator {
    fun validatePdfSplit(
        options: PdfSplitOptions,
        totalPages: Int?,
        strictPageCountRequired: Boolean = false
    ): PdfSplitValidationError? {
        if (totalPages != null && totalPages <= 1) {
            return PdfSplitValidationError.SinglePagePdf
        }
        if (strictPageCountRequired && totalPages == null) {
            return PdfSplitValidationError.SinglePagePdf
        }
        if (options.mode == PdfSplitMode.EveryPage) {
            return null
        }

        val start = options.startPage ?: 1
        if (start < 1) {
            return PdfSplitValidationError.InvalidStartPage
        }
        if (totalPages != null && start > totalPages) {
            return PdfSplitValidationError.StartExceedsPageCount
        }

        val end = options.endPage
        if (end != null) {
            if (end < 1) {
                return PdfSplitValidationError.InvalidEndPage
            }
            if (end < start) {
                return PdfSplitValidationError.EndBeforeStart
            }
            if (totalPages != null && end > totalPages) {
                return PdfSplitValidationError.EndExceedsPageCount
            }
        }

        val maxEnd = end ?: totalPages
        if (options.splitAfterPages.isNotEmpty()) {
            var previousCut = start - 1
            for (cutAfter in options.splitAfterPages) {
                if (cutAfter < start) {
                    return PdfSplitValidationError.SplitPointsOutOfRange
                }
                if (cutAfter <= previousCut) {
                    return PdfSplitValidationError.SplitPointsOrder
                }
                if (maxEnd != null && cutAfter >= maxEnd) {
                    return PdfSplitValidationError.SplitPointsOutOfRange
                }
                previousCut = cutAfter
            }
        } else {
            val isFullRangeWithKnownCount = totalPages != null && start == 1 && (end == null || end == totalPages)
            val isUnconfiguredWithUnknownCount = totalPages == null && options.startPage == null && options.endPage == null
            if (isFullRangeWithKnownCount || isUnconfiguredWithUnknownCount) {
                return PdfSplitValidationError.NoSplitOrTrimConfigured
            }
        }
        return null
    }

    fun nextSuggestedSplitPage(options: PdfSplitOptions, totalPages: Int): Int? {
        if (options.mode == PdfSplitMode.EveryPage) return null
        if (options.splitAfterPages.size >= PdfSplitOptions.MAX_CUSTOM_SPLIT_POINTS) return null
        val start = options.effectiveStartPage(totalPages)
        val end = options.effectiveEndPage(totalPages)
        if (start >= end) return null

        val sanitized = sanitizedSplitAfterPages(options.splitAfterPages, start, end)
        val ranges = options.copy(startPage = start, endPage = end, splitAfterPages = sanitized)
            .resolvedRanges(totalPages)
        val widest = ranges.maxByOrNull { it.last - it.first } ?: return null
        if (widest.last <= widest.first) return null
        val candidate = (widest.first + widest.last) / 2
        return candidate.takeIf { it in start until end && it !in sanitized }
    }

    fun sanitizedSplitAfterPages(
        splitAfterPages: List<Int>,
        startPage: Int,
        endPage: Int
    ): List<Int> {
        if (startPage >= endPage) return emptyList()
        return splitAfterPages
            .filter { it in startPage until endPage }
            .distinct()
            .sorted()
    }
}
