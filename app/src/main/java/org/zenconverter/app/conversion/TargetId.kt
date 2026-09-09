package org.zenconverter.app.conversion

enum class TargetId(val key: String) {
    Mp4("MP4"),
    Mkv("MKV"),
    Mov("MOV"),
    Gif("GIF"),
    ContactSheetJpg("contact_sheet_jpg"),
    ContactSheetPng("contact_sheet_png"),
    M4a("M4A (AAC)"),
    Mp3("MP3"),
    Wav("WAV"),
    Flac("FLAC"),
    Wma("WMA"),
    Opus("OPUS"),
    Jpg("JPG"),
    Jfif("JFIF"),
    Png("PNG"),
    Webp("WEBP"),
    Ico("ICO"),
    Pdf("PDF"),
    PdfCompress("pdf_compress"),
    Txt("TXT"),
    Md("MD"),
    PdfEncrypt("pdf_encrypt"),
    PdfDecrypt("pdf_decrypt"),
    Woff2("WOFF2"),
    Woff("WOFF"),
    Sfnt("TTF/OTF"),
    Srt("SRT"),
    Vtt("VTT"),
    Lrc("LRC"),
    Ass("ASS");

    val isContactSheet: Boolean
        get() = this == ContactSheetJpg || this == ContactSheetPng

    companion object {
        fun fromKey(key: String): TargetId? = entries.firstOrNull { it.key == key }
    }
}
