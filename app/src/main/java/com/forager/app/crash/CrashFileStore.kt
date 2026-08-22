package com.forager.app.crash

import android.content.Context
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.SystemCurrentTimeProvider
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Writes uncaught-exception traces to timestamped plain-text files under [crashDir], and prunes
 * down to the [maxFiles] most recent on every write — a crash loop overwriting the one interesting
 * file with the next one before anyone reads it is worse than losing very old crashes.
 *
 * Pure `java.io.File` plus [CurrentTimeProvider] — no Android dependency — so this is testable as
 * a plain JVM class against a temp directory, no Robolectric needed. [crashDir] and the platform
 * API level are handed in by the caller (see `ForagerApplication`/`AppContainer` for the real
 * `getExternalFilesDir(null)/crashes` wiring and `Build.VERSION.SDK_INT` read), which is also why
 * [write] takes `apiLevel` as a plain `Int` rather than reading `android.os.Build` itself.
 */
class CrashFileStore(
    private val crashDir: File,
    private val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val maxFiles: Int = DEFAULT_MAX_CRASH_FILES,
) {

    /** Writes one crash file and returns it, then deletes anything past [maxFiles], oldest first. */
    fun write(thread: Thread, throwable: Throwable, apiLevel: Int): File {
        crashDir.mkdirs()
        val epochMillis = currentTime.nowEpochMillis()
        val file = File(crashDir, "crash-$epochMillis.txt")
        file.writeText(
            buildString {
                appendLine("Timestamp: ${DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))}")
                appendLine("Thread: ${thread.name}")
                appendLine("API level: $apiLevel")
                appendLine()
                // Kotlin's stackTraceToString() is exactly what Throwable.printStackTrace() would
                // print, "Caused by:" chain included.
                append(throwable.stackTraceToString())
            },
        )
        prune()
        return file
    }

    /** Every crash file this store wrote, newest first. */
    fun list(): List<File> =
        crashDir.listFiles()
            ?.mapNotNull { file -> epochMillisOf(file)?.let { file to it } }
            ?.sortedByDescending { (_, epochMillis) -> epochMillis }
            ?.map { (file, _) -> file }
            ?: emptyList()

    private fun prune() {
        // list() is already newest-first, so dropping the first maxFiles leaves exactly the
        // stale excess to delete.
        list().drop(maxFiles).forEach { it.delete() }
    }

    companion object {
        /**
         * The epoch-millis this store's own filenames encode, or null for anything else — e.g. a
         * stray file dropped into the same directory by something other than [write]. Exposed so
         * the Settings UI can format a display timestamp per row without re-deriving this parsing.
         */
        fun epochMillisOf(file: File): Long? = FILE_NAME_REGEX.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()

        /**
         * The one crash directory this app ever uses — app-external storage so the device's own
         * file manager can reach it with no root and no ADB (see this class's own doc comment).
         * The single source of truth for that path: both `AppContainer` (the real installed
         * handler) and `AvailabilityScreen`'s Settings UI default construct through this, rather
         * than each re-deriving the same `getExternalFilesDir(null)/crashes` logic.
         */
        fun forContext(context: Context): CrashFileStore =
            CrashFileStore(File(context.getExternalFilesDir(null) ?: context.filesDir, "crashes"))

        private val FILE_NAME_REGEX = Regex("""crash-(\d+)\.txt""")
    }
}

/** Enough crash files to see a pattern across several app opens, capped so a crash loop can't fill the disk. */
const val DEFAULT_MAX_CRASH_FILES = 10
