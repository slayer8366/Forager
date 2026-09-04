#!/usr/bin/env bash
# Regenerates app/src/main/assets/databases/fungi_index.db (the Room-prepackaged fungi search
# index) from data/species-index/fungi-us-species-index.json.
#
# Not a Python script like build_fungi_species_index.py, deliberately: the .db asset has to come
# out of a real, running Room database (com.forager.app.data.local.fungiindex.FungiIndexDatabase),
# because Room's createFromAsset() validates a copied-in database against an identity hash its own
# annotation processor computes at compile time -- a hand-built SQLite file has no way to carry the
# matching hash. See com.forager.app.tools.GenerateFungiIndexDbAsset's doc comment for the full
# reasoning, and app/build.gradle.kts's testDebugUnitTest doc comment for why this runs through the
# test task's classpath rather than a bespoke Gradle Test task.
#
# Run this after data/species-index/fungi-us-species-index.json changes, then commit the
# regenerated .db asset.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

./gradlew testDebugUnitTest \
    --tests "com.forager.app.tools.GenerateFungiIndexDbAsset" \
    -Pforager.generateFungiIndexDbAsset=true
