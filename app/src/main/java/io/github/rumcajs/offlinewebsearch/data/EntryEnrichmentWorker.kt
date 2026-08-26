package io.github.rumcajs.offlinewebsearch.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import io.github.rumcajs.offlinewebsearch.webtoolkit.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enriches a newly-created entry by fetching its web page and filling in missing
 * metadata (title, description, thumbnail, date_published) asynchronously.
 *
 * This is intentionally a lightweight object rather than an Android WorkManager job:
 * the user expects the entry to be in the database before enrichment starts, and the
 * operation is triggered from a coroutine scope that outlives the save action.
 */
object EntryEnrichmentWorker {

    /**
     * Fetches the web page at [link] and patches the database row identified by [entryId]
     * with any metadata fields that are still blank/null.
     *
     * Fields already filled in by the user are never overwritten.
     *
     * @param context           Android context used to locate the database file.
     * @param activeDbState     The currently active [DatabaseState]; must be a writable `.db`.
     * @param entryId           Primary key of the row to update.
     * @param link              URL to fetch metadata from.
     * @param currentTitle      User-supplied title (may be blank; enrichment will fill it in).
     * @param currentDescription User-supplied description (may be blank).
     */
    suspend fun enrich(
        context: Context,
        activeDbState: DatabaseState,
        entryId: Long,
        link: String,
        currentTitle: String,
        currentDescription: String
    ) = withContext(Dispatchers.IO) {
        if (activeDbState.extension != ".db" || activeDbState.isReadOnly) return@withContext

        val file = File(context.filesDir, activeDbState.localFileName)
        if (!file.exists()) return@withContext

        try {
            val urlObj = Url(link)
            // A single getPage() call caches the response so subsequent reads are free.
            val page = urlObj.getPage()

            val fetchedTitle       = if (currentTitle.isBlank()) page.getTitle()?.trim() else null
            val fetchedDescription = if (currentDescription.isBlank()) page.getDescription()?.trim() else null
            val fetchedThumbnail   = page.getThumbnails().firstOrNull()
            val fetchedDatePublished = page.getDatePublished()
                ?.let { DateUtils.toIsoString(it) }

            // Only open the database if there is at least one new value to write.
            val hasUpdate = !fetchedTitle.isNullOrBlank()
                || !fetchedDescription.isNullOrBlank()
                || !fetchedThumbnail.isNullOrBlank()
                || !fetchedDatePublished.isNullOrBlank()

            if (!hasUpdate) return@withContext

            val db = SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            )
            try {
                val values = ContentValues()
                if (!fetchedTitle.isNullOrBlank())        values.put("title", fetchedTitle)
                if (!fetchedDescription.isNullOrBlank())  values.put("description", fetchedDescription)
                if (!fetchedThumbnail.isNullOrBlank())    values.put("thumbnail", fetchedThumbnail)
                if (!fetchedDatePublished.isNullOrBlank()) values.put("date_published", fetchedDatePublished)

                db.update("linkdatamodel", values, "id = ?", arrayOf(entryId.toString()))
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            // Enrichment is best-effort; errors must not surface to the user.
            e.printStackTrace()
        }
    }
}
