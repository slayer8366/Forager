#!/usr/bin/env bash
# Fetches the fonts and icons the style uses into assets/ (not committed), at a pinned version, and
# checks them. Run from this folder: ./fetch-assets.sh
#
# Source: protomaps/basemaps-assets at commit 028c18f7 (2025-10-31; the repository has no tags).
# GitHub publishes no checksum for a commit's archive, so the sha256 below is the one recorded when
# this script was written (dispatch 2026-09-28-485): trust on first use. If GitHub ever re-compresses
# the archive the check fails loudly, and the files must be compared before the new sum is accepted.
#
# Since dispatch 2026-10-22-03 Amendment 1 (RECORD -1561) it also fetches the eight ruled Trail Map
# cut-outs (six icons, the tree glyph and the wetland tufts, day, 1x and 2x) from
# slayer8366/Forager-cartography, pinned to commit 236957d3, the newest commit that holds them unchanged
# since they were filed at ef4cb60. The archive's sha256 is trust on first use, as above (read twice
# on 2026-10-22, identical); each extracted file is then checked against sprite-v5-items.sha256, the
# record's own sums. They are Forager's own art (the owner, 2026-10-22: "As the project's own art").
# tools/build-sprite-v5.mjs builds sprite v5 from v4 and these.
set -euo pipefail
cd "$(dirname "$0")"

COMMIT=028c18f713baecad011301ff7a69acc39bcc2ae7
SHA256=57e40e8c512bd8042d0a3a251f19d0d1c8523ad963c666c3c6643bada4dc92d0
URL="https://codeload.github.com/protomaps/basemaps-assets/tar.gz/$COMMIT"

CARTO_COMMIT=236957d3c7ea4feb7d1a2521322f06dadbec5b9c
CARTO_SHA256=7ed1e0f1287dfa865365b8a249a1cfefadcf67633ef7376d736c8c1d03d1e48e
CARTO_URL="https://codeload.github.com/slayer8366/Forager-cartography/tar.gz/$CARTO_COMMIT"
CARTO_DRAFTS="Forager-cartography-$CARTO_COMMIT/assets/sprite-drafts/2026-10-22"

mkdir -p assets
archive="assets/basemaps-assets-$COMMIT.tar.gz"
if [ ! -f "$archive" ]; then
  curl -sSfL -o "$archive.part" "$URL"
  mv "$archive.part" "$archive"
fi
echo "$SHA256  $archive" | sha256sum -c -

rm -rf assets/fonts assets/sprites/v4
tar -xzf "$archive" -C assets --strip-components=1 \
  "basemaps-assets-$COMMIT/fonts/Noto Sans Regular" \
  "basemaps-assets-$COMMIT/fonts/Noto Sans Medium" \
  "basemaps-assets-$COMMIT/fonts/Noto Sans Italic" \
  "basemaps-assets-$COMMIT/sprites/v4"
echo "fonts: $(ls assets/fonts | tr '\n' ',' ); sprites: $(ls assets/sprites/v4 | wc -l) files"

carto="assets/forager-cartography-$CARTO_COMMIT.tar.gz"
if [ ! -f "$carto" ]; then
  curl -sSfL -o "$carto.part" "$CARTO_URL"
  mv "$carto.part" "$carto"
fi
echo "$CARTO_SHA256  $carto" | sha256sum -c -

rm -rf assets/sprite-v5-items
mkdir -p assets/sprite-v5-items
mapfile -t members < <(grep -v '^#' sprite-v5-items.sha256 | awk -v d="$CARTO_DRAFTS" '{print d "/" $2}')
tar -xzf "$carto" -C assets/sprite-v5-items --strip-components=4 "${members[@]}"
(cd assets/sprite-v5-items && grep -v '^#' ../../sprite-v5-items.sha256 | sha256sum -c -)
echo "sprite v5 cut-outs: $(ls assets/sprite-v5-items/day | wc -l) files"
