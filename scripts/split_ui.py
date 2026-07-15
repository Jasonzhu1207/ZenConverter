import os

def split_kt_file():
    src_file = r'e:\小机及cf部署\项目\OpenConverter\app\src\main\java\org\zenconverter\app\ui\ZenConverterApp.kt'
    with open(src_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    start_idx = -1
    for i, line in enumerate(lines):
        if 'private fun videoResolutionToShortSide' in line:
            start_idx = i
            break
            
    if start_idx != -1:
        util_lines = lines[start_idx:]
        with open(r'e:\小机及cf部署\项目\OpenConverter\app\src\main\java\org\zenconverter\app\ui\utils\FormatUtils.kt', 'w', encoding='utf-8') as f:
            f.write("package org.zenconverter.app.ui.utils\n\n")
            f.write("import android.content.Context\n")
            f.write("import android.content.Intent\n")
            f.write("import android.net.Uri\n")
            f.write("import android.widget.Toast\n")
            f.write("import android.content.ClipData\n")
            f.write("import android.content.ClipboardManager\n")
            f.write("import org.zenconverter.app.ui.TargetFormat\n")
            f.write("import org.zenconverter.app.ui.PdfImagePageMode\n")
            f.write("import org.zenconverter.app.ui.PdfRenderQuality\n")
            f.write("import org.zenconverter.app.ui.i18n.UiText\n\n")
            
            # replace private fun with internal fun
            out = "".join(util_lines).replace("private fun", "internal fun")
            f.write(out)
            
        with open(src_file, 'w', encoding='utf-8') as f:
            f.writelines(lines[:start_idx])
            
split_kt_file()
