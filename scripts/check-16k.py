#!/usr/bin/env python3
"""Fail when a 64-bit native library cannot be mapped on a 16 KB page device.

Android 15 runs on devices whose memory pages are 16 KB rather than 4 KB, and
since 1 November 2025 Play refuses uploads targeting Android 15+ whose 64-bit
.so files are not laid out for them. A library is fine when every one of its
PT_LOAD segments declares an alignment of at least 16384: the loader maps
segments at page granularity, so a 4 KB alignment leaves it nothing valid to
map onto and the app fails to start, or crashes once it does.

This is not something the app's own code can get wrong. It went wrong anyway,
in a dependency: JNA 5.14.0 shipped an x86_64 libjnidispatch.so linked for
4 KB pages, the release rolled out, and Play answered with "this release
contains new app bundles that are not compatible with 16 KB memory page
sizes". Reading the alignment out of the artifact is the only check that
notices, because nothing in the Gradle build is aware of it.

Usage:
    scripts/check-16k.py <file.aab | file.apk | file.so | directory> ...

Missing paths are skipped, so the Gradle build can pass both the APK and the
bundle and have whichever one was actually built be the one examined. 32-bit
libraries are reported and not judged: 16 KB pages are a 64-bit concern.
"""

import os
import struct
import sys
import zipfile

MIN_ALIGN = 16 * 1024

PT_LOAD = 1


def load_alignments(data):
    """Alignments of the PT_LOAD segments, and whether the ELF is 64-bit."""
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    is64 = data[4] == 2
    if is64:
        e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
        e_phentsize = struct.unpack_from("<H", data, 0x36)[0]
        e_phnum = struct.unpack_from("<H", data, 0x38)[0]
    else:
        e_phoff = struct.unpack_from("<I", data, 0x1C)[0]
        e_phentsize = struct.unpack_from("<H", data, 0x2A)[0]
        e_phnum = struct.unpack_from("<H", data, 0x2C)[0]

    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        if struct.unpack_from("<I", data, off)[0] != PT_LOAD:
            continue
        # p_align is the last field of the program header: at +48 in the
        # 64-bit layout, at +28 in the 32-bit one.
        if is64:
            aligns.append(struct.unpack_from("<Q", data, off + 48)[0])
        else:
            aligns.append(struct.unpack_from("<I", data, off + 28)[0])
    return aligns, is64


def collect(path):
    """(name, bytes) for every .so reachable from path."""
    if os.path.isdir(path):
        for root, _, files in os.walk(path):
            for f in sorted(files):
                if f.endswith(".so"):
                    p = os.path.join(root, f)
                    with open(p, "rb") as fh:
                        yield os.path.relpath(p, path), fh.read()
        return

    if path.endswith(".so"):
        with open(path, "rb") as fh:
            yield os.path.basename(path), fh.read()
        return

    with zipfile.ZipFile(path) as z:
        for name in sorted(z.namelist()):
            if name.endswith(".so"):
                yield name, z.read(name)


def main(argv):
    paths = [p for p in argv[1:] if os.path.exists(p)]
    if not argv[1:]:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    if not paths:
        print("check-16k: nothing to check (no input existed)", file=sys.stderr)
        return 0

    bad = []
    checked = 0
    for path in paths:
        print(f"==> {path}")
        for name, data in collect(path):
            try:
                aligns, is64 = load_alignments(data)
            except (ValueError, struct.error) as exc:
                bad.append((path, name, f"unreadable: {exc}"))
                continue
            if not is64:
                print(f"    --   {name} (32-bit, not subject to 16 KB pages)")
                continue
            checked += 1
            worst = min(aligns) if aligns else 0
            if worst < MIN_ALIGN:
                bad.append((path, name, f"segment alignment {hex(worst)}"))
                print(f"    BAD  {name}  align={hex(worst)}")
            else:
                print(f"    ok   {name}  align={hex(worst)}")

    if bad:
        print("", file=sys.stderr)
        print("error: these 64-bit libraries are not 16 KB aligned:", file=sys.stderr)
        for path, name, why in bad:
            print(f"  {path}!{name}: {why}", file=sys.stderr)
        print(
            "\nA library built here is fixed by building it with NDK r28 or newer\n"
            "(scripts/build-native.sh enforces that). A library that arrives from a\n"
            "dependency is fixed by raising that dependency's version: there is no\n"
            "linker flag to apply to somebody else's .so.",
            file=sys.stderr,
        )
        return 1

    print(f"\n{checked} 64-bit libraries checked, all 16 KB aligned.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
