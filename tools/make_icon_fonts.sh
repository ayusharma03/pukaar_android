#!/usr/bin/env bash
# Rebuilds the two small Material Symbols fonts from tools/icon-names.txt.
# Needs: python3, pip install fonttools
# Usage: add an icon name (from fonts.google.com/icons) to tools/icon-names.txt, run this,
#        then add a matching constant to Sym in Icons.kt (the script prints it).
set -euo pipefail
cd "$(dirname "$0")"
BASE="https://raw.githubusercontent.com/google/material-design-icons/master/variablefont"
NAME="MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D"
[ -f rounded.ttf ] || curl -sL -o rounded.ttf "$BASE/$NAME.ttf"
[ -f rounded.codepoints ] || curl -sL -o rounded.codepoints "$BASE/$NAME.codepoints"
UNI=$(python3 - <<'PY'
cp = {}
for line in open("rounded.codepoints"):
    n, c = line.split(); cp.setdefault(n, c)
names = [l.strip() for l in open("icon-names.txt") if l.strip()]
missing = [n for n in names if n not in cp]
if missing: raise SystemExit(f"Unknown icon names: {missing}")
print(",".join("U+" + cp[n].upper() for n in names))
for n in names:
    parts = n.split("_"); k = parts[0] + "".join(p.capitalize() for p in parts[1:])
    print(f'    const val {k} = "\\u{cp[n].upper()}"', file=__import__("sys").stderr)
PY
)
OUT=../app/src/main/res/font
for FILL in 0 1; do
  fonttools varLib.instancer rounded.ttf wght=400 GRAD=0 opsz=24 FILL=$FILL -o inst$FILL.ttf -q
  pyftsubset inst$FILL.ttf --unicodes="$UNI" --layout-features='' --no-hinting \
    --output-file="$OUT/material_symbols_rounded_fill$FILL.ttf"
  rm inst$FILL.ttf
done
echo "Done. Constants for Sym are printed above."
