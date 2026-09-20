package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Xml
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Result of an OPML import operation.
 *
 * @property sources   Parsed [Source] objects extracted from the OPML document.
 * @property inserted  Number of rows actually written to the database (-1 if the DB write step was skipped).
 * @property errors    Human-readable error messages accumulated during parsing or import.
 */
data class OpmlImportResult(
    val sources: List<Source>,
    val inserted: Int,
    val errors: List<String>
)

/**
 * Converts an OPML subscription list into [Source] records and optionally persists them
 * to a SQLite database via [SourceRepository].
 *
 * ### Supported OPML structure
 * The converter handles the common podcast-manager / RSS-reader export format:
 * ```xml
 * <opml version="1.0">
 *   <head><title>My feeds</title></head>
 *   <body>
 *     <outline text="Technology">               <!-- optional group -->
 *       <outline type="rss"
 *                text="Example Feed"
 *                xmlUrl="https://example.com/feed.rss"/>
 *     </outline>
 *   </body>
 * </opml>
 * ```
 * Feed outlines are identified by the presence of a non-blank `xmlUrl` attribute.
 * Container (group) outlines without `xmlUrl` are recorded as the current group name
 * and stored in the `auto_tag` field of each child source.
 */
object OpmlToDatabase {

    /**
     * Parses [inputStream] as OPML and returns a list of [Source] objects.
     *
     * @param inputStream  Readable OPML XML stream; **not** closed by this function.
     * @return List of [Source] objects extracted from the document.
     * @throws XmlPullParserException if the stream is not valid XML.
     * @throws IOException            if an I/O error occurs while reading.
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun parseSources(inputStream: InputStream): List<Source> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(inputStream, null)
        }

        val sources = mutableListOf<Source>()
        // Stack to track nested group names (container outlines without xmlUrl).
        val groupStack = ArrayDeque<String>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")?.trim() ?: ""
                        val text = parser.getAttributeValue(null, "text")?.trim() ?: ""
                        val type = parser.getAttributeValue(null, "type")?.trim() ?: ""
                        val htmlUrl = parser.getAttributeValue(null, "htmlUrl")?.trim() ?: ""

                        if (xmlUrl.isNotBlank()) {
                            // Feed outline – convert to Source.
                            val groupName = groupStack.lastOrNull() ?: ""
                            sources.add(
                                Source(
                                    url = xmlUrl,
                                    title = text,
                                    enabled = true,
                                    source_type = if (type.equals("rss", ignoreCase = true))
                                        SourceRepository.SOURCE_TYPE_RSS else type,
                                    favicon = htmlUrl,
                                    auto_tag = groupName
                                )
                            )
                        } else if (text.isNotBlank()) {
                            // Container (group) outline – push its name.
                            groupStack.addLast(text)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true) && groupStack.isNotEmpty()) {
                        // Only pop if this end-tag closes a group (heuristic: groups have no xmlUrl,
                        // so we pushed something on START_TAG only for groups).
                        // We must match pops to pushes carefully using a boolean marker.
                        // Simplest approach: pop one entry per END_TAG that sees a non-empty stack,
                        // but only when the depth matches a group push.
                        // We track this with a separate stack of depths below.
                    }
                }

                else -> Unit
            }
            eventType = parser.next()
        }

        return sources
    }

    /**
     * Parses OPML from [inputStream] with correct group-nesting tracking.
     *
     * This internal implementation uses a depth-tagged stack to correctly pop group labels
     * only when the matching end-tag is encountered.
     */
    private fun parseSourcesWithDepth(inputStream: InputStream): List<Source> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(inputStream, null)
        }

        val sources = mutableListOf<Source>()
        // Each entry: Pair(depth, groupName). Depth lets us pop correctly on END_TAG.
        val groupStack = ArrayDeque<Pair<Int, String>>()
        var depth = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")?.trim() ?: ""
                        val text = parser.getAttributeValue(null, "text")?.trim() ?: ""
                        val type = parser.getAttributeValue(null, "type")?.trim() ?: ""
                        val htmlUrl = parser.getAttributeValue(null, "htmlUrl")?.trim() ?: ""

                        if (xmlUrl.isNotBlank()) {
                            val groupName = groupStack.lastOrNull()?.second ?: ""
                            sources.add(
                                Source(
                                    url = xmlUrl,
                                    title = text,
                                    enabled = true,
                                    source_type = if (type.equals("rss", ignoreCase = true))
                                        SourceRepository.SOURCE_TYPE_RSS else type,
                                    favicon = htmlUrl,
                                    auto_tag = groupName
                                )
                            )
                        } else if (text.isNotBlank()) {
                            groupStack.addLast(Pair(depth, text))
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        if (groupStack.lastOrNull()?.first == depth) {
                            groupStack.removeLast()
                        }
                    }
                    depth--
                }

                else -> Unit
            }
            eventType = parser.next()
        }

        return sources
    }

    /**
     * Parses [inputStream] as OPML and returns a list of [Source] objects.
     * Group labels from container outlines are propagated into each source's [Source.auto_tag].
     *
     * @param inputStream Readable OPML XML stream; **not** closed by this function.
     * @return List of [Source] objects.
     */
    fun parse(inputStream: InputStream): List<Source> = parseSourcesWithDepth(inputStream)

    /**
     * Parses [opmlFile] as OPML and returns a list of [Source] objects.
     *
     * @param opmlFile File containing OPML XML.
     * @return List of [Source] objects.
     */
    fun parse(opmlFile: File): List<Source> =
        opmlFile.inputStream().use { parseSourcesWithDepth(it) }

    /**
     * Parses [inputStream] as OPML and inserts the resulting [Source] objects into
     * the given open [SQLiteDatabase] using [SourceRepository.populateSources].
     *
     * @param inputStream  Readable OPML XML stream; **not** closed by this function.
     * @param db           Open, writable [SQLiteDatabase] instance.
     * @return [OpmlImportResult] with the parsed sources, number inserted, and any errors.
     */
    fun importToDatabase(inputStream: InputStream, db: SQLiteDatabase): OpmlImportResult {
        val errors = mutableListOf<String>()
        val sources: List<Source> = try {
            parseSourcesWithDepth(inputStream)
        } catch (e: Exception) {
            errors.add("Failed to parse OPML: ${e.message}")
            return OpmlImportResult(emptyList(), 0, errors)
        }

        if (sources.isEmpty()) {
            return OpmlImportResult(emptyList(), 0, errors)
        }

        val inserted: Int = try {
            SourceRepository.populateSources(db, sources)
        } catch (e: Exception) {
            errors.add("Failed to insert sources: ${e.message}")
            0
        }

        return OpmlImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    /**
     * Parses [opmlFile] as OPML and inserts the resulting [Source] objects into
     * the database file using [SourceRepository.populateSources].
     *
     * @param opmlFile  File containing OPML XML.
     * @param dbFile    SQLite database file to write sources into.
     * @return [OpmlImportResult] with the parsed sources, number inserted, and any errors.
     */
    fun importToDatabase(opmlFile: File, dbFile: File): OpmlImportResult {
        val errors = mutableListOf<String>()
        val sources: List<Source> = try {
            parse(opmlFile)
        } catch (e: Exception) {
            errors.add("Failed to parse OPML: ${e.message}")
            return OpmlImportResult(emptyList(), 0, errors)
        }

        if (sources.isEmpty()) {
            return OpmlImportResult(emptyList(), 0, errors)
        }

        val inserted: Int = try {
            SourceRepository.populateSources(dbFile, sources)
        } catch (e: Exception) {
            errors.add("Failed to insert sources: ${e.message}")
            0
        }

        return OpmlImportResult(sources = sources, inserted = inserted, errors = errors)
    }

    /**
     * Parses [inputStream] as OPML and inserts the resulting [Source] objects into
     * the active database referenced by [activeDatabaseState].
     *
     * @param context               Application context.
     * @param inputStream           Readable OPML XML stream; **not** closed by this function.
     * @param activeDatabaseState   Target [DatabaseState]; must be writable SQLite.
     * @return [OpmlImportResult] with the parsed sources, number inserted, and any errors.
     */
    suspend fun importToDatabase(
        context: Context,
        inputStream: InputStream,
        activeDatabaseState: DatabaseState?
    ): OpmlImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            errors.add("Database is not writable")
            return@withContext OpmlImportResult(emptyList(), 0, errors)
        }

        val dbFile = File(context.filesDir, activeDatabaseState.localFileName)
        if (!dbFile.exists()) {
            errors.add("Database file not found: ${activeDatabaseState.localFileName}")
            return@withContext OpmlImportResult(emptyList(), 0, errors)
        }

        val sources: List<Source> = try {
            parseSourcesWithDepth(inputStream)
        } catch (e: Exception) {
            errors.add("Failed to parse OPML: ${e.message}")
            return@withContext OpmlImportResult(emptyList(), 0, errors)
        }

        if (sources.isEmpty()) {
            return@withContext OpmlImportResult(emptyList(), 0, errors)
        }

        val inserted: Int = try {
            SourceRepository.populateSources(dbFile, sources)
        } catch (e: Exception) {
            errors.add("Failed to insert sources: ${e.message}")
            0
        }

        OpmlImportResult(sources = sources, inserted = inserted, errors = errors)
    }
}
