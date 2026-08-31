package com.forager.app.data.local.fungiindex

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The bundled, read-only fungi name-search index — deliberately a separate database from
 * [com.forager.app.data.local.ForagerDatabase], which holds the user's own journal, tracks,
 * planned trips and search cache. Three reasons, from the dispatch this was built from:
 *
 * 1. The index is rebuilt and reshipped as its taxonomic scope changes (it already has, twice)
 *    — sharing a database would turn every one of those refreshes into a migration against the
 *    user's journal. A separate database makes an index update a file swap.
 * 2. [Room.databaseBuilder.createFromAsset] — how this database is always opened in production,
 *    via [create] — copies a prebuilt `.db` file in place of running `onCreate`, so opening it
 *    does no parsing or bulk-insert work on first launch. Doing that parse/insert inline for
 *    11,257 taxa would add real work to an app that already has an unexplained cold-start defect
 *    (see the dispatch); this keeps the two unrelated.
 * 3. [Room.databaseBuilder.createFromAsset] cannot be used on a database that already holds user
 *    data, so a separate database is the only way to get the prepackaged-asset path at all.
 *
 * The `.db` asset (`app/src/main/assets/databases/fungi_index.db`) is generated from
 * `data/species-index/fungi-us-species-index.json` by
 * `com.forager.app.tools.GenerateFungiIndexDbAsset`, run via `scripts/generate_fungi_index_db.sh`
 * (`./gradlew generateFungiIndexDbAsset`) — see that class's doc comment for why the generator has
 * to run a real Room database rather than being a plain Python/SQLite script like
 * `scripts/build_fungi_species_index.py`. `exportSchema` is `false`: this database is never
 * migrated in place, only replaced wholesale (see reason 1 above), so there is no migration
 * history worth exporting.
 */
@Database(
    entities = [FungiTaxonEntity::class, FungiTaxonNameEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FungiIndexDatabase : RoomDatabase() {
    abstract fun fungiIndexDao(): FungiIndexDao

    companion object {
        private const val ASSET_PATH = "databases/fungi_index.db"

        fun create(context: Context): FungiIndexDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FungiIndexDatabase::class.java,
                "fungi_index.db",
            )
                .createFromAsset(ASSET_PATH)
                .build()
        }
    }
}
