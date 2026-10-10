#!/bin/sh
# Renders the SVG sources into the .icns / .ico files and the PNGs the app loads at runtime.
# Needs rsvg-convert (brew install librsvg) and python3; iconutil is part of macOS.
set -eu

here=$(cd "$(dirname "$0")" && pwd)
resources="$here/../../src/main/resources/captions/icons"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

render() { # svg size out
    rsvg-convert -w "$2" -h "$2" "$here/$1" -o "$3"
}

# Windows: hand-tuned small sizes, the full design from 48 px
win_svg() {
    if [ "$1" -le 20 ]; then echo icon-win-16.svg
    elif [ "$1" -le 40 ]; then echo icon-win-32.svg
    else echo icon-win.svg
    fi
}

mkdir -p "$resources"
set -- # the PNGs for the .ico, kept as arguments so paths with spaces stay whole
for size in 16 20 24 32 40 48 64 256; do
    render "$(win_svg "$size")" "$size" "$work/win-$size.png"
    cp "$work/win-$size.png" "$resources/win-$size.png"
    set -- "$@" "$work/win-$size.png"
done
# every entry is stored as PNG (Windows Vista and later); ImageMagick would store 256 px as a 260 KB bitmap
python3 - "$here/SystemSoundsCaptions.ico" "$@" <<'EOF'
import struct, sys
out, pngs = sys.argv[1], [open(p, "rb").read() for p in sys.argv[2:]]
offset = 6 + 16 * len(pngs)
head = struct.pack("<HHH", 0, 1, len(pngs))
for png in pngs:
    w, h = struct.unpack(">II", png[16:24])
    head += struct.pack("<BBBBHHII", w % 256, h % 256, 0, 0, 1, 32, len(png), offset)
    offset += len(png)
open(out, "wb").write(head + b"".join(pngs))
EOF

# macOS
iconset="$work/SystemSoundsCaptions.iconset"
mkdir "$iconset"
for size in 16 32 128 256 512; do
    render icon-mac.svg "$size" "$iconset/icon_${size}x${size}.png"
    render icon-mac.svg $((size * 2)) "$iconset/icon_${size}x${size}@2x.png"
done
iconutil -c icns "$iconset" -o "$here/SystemSoundsCaptions.icns"
cp "$iconset/icon_512x512.png" "$resources/mac-512.png"
