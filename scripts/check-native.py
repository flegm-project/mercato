#!/usr/bin/env python3
"""Fail when the packaged native layer cannot serve the Kotlin bindings.

Every native failure this app has shipped looked the same from the outside --
UnsatisfiedLinkError before the first screen -- and had a different cause each
time. Version 1.0.0 shipped a libmercato_ffi.so a day older than the bindings,
so the symbols they look up were not in it. Version code 2 shipped a JNA
libjnidispatch.so linked for 4 KB pages, which no 16 KB page device can map.
Both were found by a person running the app, which is the one check that does
not scale and does not run on every build.

check-16k.py covers the alignment. This covers the other two things that make
a well-formed artifact fail to start, read out of the finished .aab or .apk
because that is the only place that sees what the device sees:

  1. Every ABI the build packages carries BOTH native libraries. An ABI with
     libmercato_ffi.so but no libjnidispatch.so is a JNA crash on that device
     class and nowhere else, which is exactly the kind of failure that reaches
     production: it does not reproduce on the machine that built it.

  2. Every uniffi_* / ffi_* function the generated bindings declare is an
     exported dynamic symbol of the packaged libmercato_ffi.so. UniFFI resolves
     these by name at call time, so a stale .so is not a link error at build
     time: it is a crash on the first call, in whichever screen happens to
     reach the core first.

The ELF parsing is deliberately dependency-free and duplicated from
check-16k.py rather than shared. These scripts run on developer machines and on
CI, before anything is installed, and macOS ships no readelf.

Usage:
    scripts/check-native.py <file.aab|file.apk> [bindings.kt]

The bindings default to build/bindings/kotlin/uniffi/mercato_ffi/mercato_ffi.kt.
A missing artifact is skipped, so the Gradle build can point both the bundle and
the APK task at this and have whichever one was built be the one examined.
"""

import os
import re
import struct
import sys
import zipfile

# The helper JNA loads before it can call anything, and the core itself. An ABI
# directory that is missing either one is a guaranteed crash on that ABI.
REQUIRED = ("libjnidispatch.so", "libmercato_ffi.so")

# Resolved from this file, not from the working directory. Gradle runs tasks
# with a workingDir it chooses, and a relative default would make the check
# report "bindings not found" on a build that is perfectly fine.
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_BINDINGS = os.path.join(
    REPO_ROOT, "build", "bindings", "kotlin", "uniffi", "mercato_ffi", "mercato_ffi.kt"
)

SHT_DYNSYM = 11
SHN_UNDEF = 0

# The generated bindings declare one Kotlin function per native entry point,
# named exactly as the symbol. Anything else in the file (checksums stored as
# constants, comments) is not a declaration and must not be matched.
DECL = re.compile(r"^\s*fun\s+((?:uniffi|ffi)_[A-Za-z0-9_]+)\s*\(", re.MULTILINE)


def exported_symbols(data):
    """Names in .dynsym that the library actually defines."""
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    is64 = data[4] == 2
    if not is64:
        # 32-bit works the same way with narrower fields; the app ships
        # armeabi-v7a, so this path is exercised on every run.
        e_shoff = struct.unpack_from("<I", data, 0x20)[0]
        e_shentsize = struct.unpack_from("<H", data, 0x2E)[0]
        e_shnum = struct.unpack_from("<H", data, 0x30)[0]
        sym_size, name_off, shndx_off = 16, 0, 14
    else:
        e_shoff = struct.unpack_from("<Q", data, 0x28)[0]
        e_shentsize = struct.unpack_from("<H", data, 0x3A)[0]
        e_shnum = struct.unpack_from("<H", data, 0x3C)[0]
        sym_size, name_off, shndx_off = 24, 0, 6

    def section(i):
        off = e_shoff + i * e_shentsize
        if is64:
            sh_type = struct.unpack_from("<I", data, off + 4)[0]
            sh_link = struct.unpack_from("<I", data, off + 40)[0]
            sh_offset = struct.unpack_from("<Q", data, off + 24)[0]
            sh_size = struct.unpack_from("<Q", data, off + 32)[0]
        else:
            sh_type = struct.unpack_from("<I", data, off + 4)[0]
            sh_link = struct.unpack_from("<I", data, off + 24)[0]
            sh_offset = struct.unpack_from("<I", data, off + 16)[0]
            sh_size = struct.unpack_from("<I", data, off + 20)[0]
        return sh_type, sh_link, sh_offset, sh_size

    names = set()
    for i in range(e_shnum):
        sh_type, sh_link, sh_offset, sh_size = section(i)
        if sh_type != SHT_DYNSYM:
            continue
        _, _, str_off, str_size = section(sh_link)
        strtab = data[str_off : str_off + str_size]
        for s in range(0, sh_size, sym_size):
            base = sh_offset + s
            st_name = struct.unpack_from("<I", data, base + name_off)[0]
            st_shndx = struct.unpack_from("<H", data, base + shndx_off)[0]
            # An undefined symbol is one this library imports, not one it
            # provides. Counting those would let a .so that merely mentions a
            # function pass for one that implements it.
            if st_shndx == SHN_UNDEF or st_name == 0:
                continue
            end = strtab.find(b"\0", st_name)
            names.add(strtab[st_name:end].decode("utf-8", "replace"))
    return names


def libs_by_abi(path):
    """{abi: {filename: bytes}} for the native libraries in an .aab or .apk."""
    out = {}
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if not name.endswith(".so"):
                continue
            parts = name.split("/")
            # base/lib/<abi>/x.so in a bundle, lib/<abi>/x.so in an APK.
            if "lib" not in parts:
                continue
            i = parts.index("lib")
            if len(parts) < i + 3:
                continue
            out.setdefault(parts[i + 1], {})[parts[i + 2]] = z.read(name)
    return out


def main(argv):
    if len(argv) < 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2

    artifact = argv[1]
    bindings = argv[2] if len(argv) > 2 else DEFAULT_BINDINGS

    if not os.path.exists(artifact):
        print(f"check-native: {artifact} does not exist, nothing to check")
        return 0

    print(f"==> {artifact}")
    abis = libs_by_abi(artifact)
    if not abis:
        print("error: the artifact packages no native library at all.", file=sys.stderr)
        print(
            "       The Rust core is not optional: this build would crash on launch.",
            file=sys.stderr,
        )
        return 1

    problems = []

    for abi in sorted(abis):
        present = abis[abi]
        missing = [lib for lib in REQUIRED if lib not in present]
        if missing:
            problems.append(f"{abi}: missing {', '.join(missing)}")
            print(f"    BAD  {abi}: missing {', '.join(missing)}")
        else:
            print(f"    ok   {abi}: {', '.join(sorted(present))}")

    if not os.path.exists(bindings):
        print(
            f"\nerror: bindings not found at {bindings}; run scripts/build-native.sh.",
            file=sys.stderr,
        )
        return 1

    with open(bindings, encoding="utf-8") as fh:
        wanted = set(DECL.findall(fh.read()))
    if not wanted:
        print(
            f"\nerror: no uniffi entry point found in {bindings}. Either the file is\n"
            "       not the generated bindings, or uniffi changed how it declares\n"
            "       them and this check is now blind. Do not ignore this.",
            file=sys.stderr,
        )
        return 1

    print(f"\n    {len(wanted)} entry points declared by the bindings")

    for abi in sorted(abis):
        core = abis[abi].get("libmercato_ffi.so")
        if core is None:
            continue
        try:
            have = exported_symbols(core)
        except (ValueError, struct.error) as exc:
            problems.append(f"{abi}: libmercato_ffi.so unreadable: {exc}")
            print(f"    BAD  {abi}: libmercato_ffi.so unreadable: {exc}")
            continue
        absent = sorted(wanted - have)
        if absent:
            problems.append(
                f"{abi}: libmercato_ffi.so is missing {len(absent)} entry points"
            )
            print(f"    BAD  {abi}: {len(absent)} entry points not exported")
            for s in absent[:10]:
                print(f"           {s}")
            if len(absent) > 10:
                print(f"           ... and {len(absent) - 10} more")
        else:
            print(f"    ok   {abi}: all {len(wanted)} entry points exported")

    if problems:
        print("", file=sys.stderr)
        print("error: the packaged native layer would fail at runtime:", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        print(
            "\nA missing library on one ABI means the abiFilters and the ABIs\n"
            "build-native.sh produces have drifted apart. Missing entry points mean\n"
            "the .so predates the bindings: re-run scripts/build-native.sh android\n"
            "and rebuild. Neither shows up as a build failure, only as a crash on a\n"
            "device, which is why this runs on the artifact.",
            file=sys.stderr,
        )
        return 1

    print("\nnative layer consistent with the bindings on every packaged ABI.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
