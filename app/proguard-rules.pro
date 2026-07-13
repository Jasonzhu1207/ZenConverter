# Project-specific shrinker rules go here.
# Keep this file minimal and tied to concrete release build issues.

# PDFBox-Android references optional Gemalto JPEG2000 helpers from JPXFilter.
# Current PDFBox use is true PDF merge and selectable-text extraction, not
# JPEG2000 image encode/decode, so do not add another binary just for this path.
-dontwarn com.gemalto.jp2.**
