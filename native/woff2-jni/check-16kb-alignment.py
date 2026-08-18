#!/usr/bin/env python3
"""Check that ELF shared libraries have 16 KB-aligned LOAD segments.

Android 15+ devices and Google Play (from Nov 2025) require native libraries
to support 16 KB page sizes. A library fails when any PT_LOAD segment has
p_align not a multiple of 16384 (i.e. it was linked with a smaller page size,
typically 4096).

Usage:
    python3 check-16kb-alignment.py lib1.so [lib2.so ...]

Exit status: 0 when every file passes, 1 when any file fails, 2 on usage error.
"""
import struct
import sys

MIN_ALIGN = 16384


def load_segments(path):
    with open(path, "rb") as handle:
        data = handle.read()

    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError(f"{path}: not an ELF file")
    if data[4] != 2:
        raise ValueError(f"{path}: not a 64-bit ELF file")

    endian = "<" if data[5] == 1 else ">"
    e_phoff = struct.unpack_from(endian + "Q", data, 32)[0]
    e_phentsize = struct.unpack_from(endian + "H", data, 54)[0]
    e_phnum = struct.unpack_from(endian + "H", data, 56)[0]

    segments = []
    for index in range(e_phnum):
        off = e_phoff + index * e_phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        if p_type != 1:  # PT_LOAD
            continue
        p_offset = struct.unpack_from(endian + "Q", data, off + 8)[0]
        p_vaddr = struct.unpack_from(endian + "Q", data, off + 16)[0]
        p_filesz = struct.unpack_from(endian + "Q", data, off + 32)[0]
        p_memsz = struct.unpack_from(endian + "Q", data, off + 40)[0]
        p_align = struct.unpack_from(endian + "Q", data, off + 48)[0]
        segments.append((p_offset, p_vaddr, p_filesz, p_memsz, p_align))
    return segments


def main():
    if len(sys.argv) < 2:
        sys.stderr.write(__doc__)
        return 2

    failed = False
    for path in sys.argv[1:]:
        try:
            segments = load_segments(path)
        except (OSError, ValueError) as error:
            print(f"ERROR {error}")
            failed = True
            continue

        ok = True
        for offset, vaddr, _filesz, _memsz, align in segments:
            aligned = align % MIN_ALIGN == 0
            if not aligned:
                ok = False
            print(
                f"{'OK ' if aligned else 'BAD'} {path} "
                f"LOAD off=0x{offset:x} vaddr=0x{vaddr:x} align=0x{align:x}"
            )

        if ok:
            print(f"PASS {path}")
        else:
            print(
                f"FAIL {path} - rebuild with -Wl,-z,max-page-size=16384 "
                f"-Wl,-z,common-page-size=16384"
            )
            failed = True

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
