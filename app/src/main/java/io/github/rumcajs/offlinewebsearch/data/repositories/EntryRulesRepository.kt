package io.github.rumcajs.offlinewebsearch.data.repositories

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data class representing a rule row in the `entryrules` table.
 *
 * Matches the SQLAlchemy model definition:
 * ```python
 * class EntryRules(Base):
 *     __tablename__ = "entryrules"
 *
 *     id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
 *     enabled: Mapped[bool] = mapped_column(default=True)
 *     priority: Mapped[int] = mapped_column(default=0)
 *     rule_name: Mapped[str] = mapped_column(String(1000))
 *     trigger_rule_url: Mapped[str] = mapped_column(String(1000))
 *     trigger_text: Mapped[str] = mapped_column(String(1000))
 *     trigger_text_hits: Mapped[int] = mapped_column(default=0)
 *     trigger_text_fields: Mapped[str] = mapped_column(String(1000))
 *     block: Mapped[bool] = mapped_column(default=False)
 *     trust: Mapped[bool] = mapped_column(default=False)
 *     auto_tag: Mapped[str] = mapped_column(String(1000))
 *     apply_age_limit: Mapped[int] = mapped_column(default=0)
 *     browser_id: Mapped[int] = mapped_column(default=0)
 * ```
 */
@Serializable
data class EntryRule(
    val id: Long? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val rule_name: String = "",
    val trigger_rule_url: String = "",
    val trigger_text: String = "",
    val trigger_text_hits: Int = 0,
    val trigger_text_fields: String = "",
    val block: Boolean = false,
    val trust: Boolean = false,
    val auto_tag: String = "",
    val apply_age_limit: Int = 0,
    val browser_id: Int = 0
)

/**
 * Type alias for [EntryRule] matching the plural model name in Python.
 */
typealias EntryRules = EntryRule

/**
 * Repository for accessing and managing the `entryrules` SQLite table.
 */
object EntryRulesRepository : RepositoryInterface {

    val COLUMNS = arrayOf(
        "id", "enabled", "priority", "rule_name",
        "trigger_rule_url", "trigger_text", "trigger_text_hits",
        "trigger_text_fields", "block", "trust", "auto_tag",
        "apply_age_limit", "browser_id"
    )

    override fun getTableName(): String = "entryrules"

    /**
     * Ensures that the `entryrules` table exists in the database.
     *
     * @param db SQLiteDatabase instance to execute creation statements against.
     */
    override fun ensureTableExists(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE IF NOT EXISTS ${getTableName()} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                enabled INTEGER NOT NULL DEFAULT 1,
                priority INTEGER NOT NULL DEFAULT 0,
                rule_name TEXT,
                trigger_rule_url TEXT,
                trigger_text TEXT,
                trigger_text_hits INTEGER NOT NULL DEFAULT 0,
                trigger_text_fields TEXT,
                block INTEGER NOT NULL DEFAULT 0,
                trust INTEGER NOT NULL DEFAULT 0,
                auto_tag TEXT,
                apply_age_limit INTEGER NOT NULL DEFAULT 0,
                browser_id INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    /**
     * Converts an active SQLite cursor row to an [EntryRule].
     *
     * @param cursor The cursor positioned at a valid row.
     * @return Converted [EntryRule] instance.
     */
    fun cursorToEntryRule(cursor: Cursor): EntryRule {
        fun getLong(col: String): Long? {
            val idx = cursor.getColumnIndex(col)
            return if (idx != -1 && !cursor.isNull(idx)) cursor.getLong(idx) else null
        }
        fun getInt(col: String, default: Int = 0): Int {
            val idx = cursor.getColumnIndex(col)
            return if (idx != -1 && !cursor.isNull(idx)) cursor.getInt(idx) else default
        }
        fun getBool(col: String, default: Boolean = false): Boolean {
            val idx = cursor.getColumnIndex(col)
            return if (idx != -1 && !cursor.isNull(idx)) cursor.getInt(idx) == 1 else default
        }
        fun getString(col: String, default: String = ""): String {
            val idx = cursor.getColumnIndex(col)
            return if (idx != -1 && !cursor.isNull(idx)) cursor.getString(idx) ?: default else default
        }

        return EntryRule(
            id = getLong("id"),
            enabled = getBool("enabled", true),
            priority = getInt("priority", 0),
            rule_name = getString("rule_name"),
            trigger_rule_url = getString("trigger_rule_url"),
            trigger_text = getString("trigger_text"),
            trigger_text_hits = getInt("trigger_text_hits", 0),
            trigger_text_fields = getString("trigger_text_fields"),
            block = getBool("block", false),
            trust = getBool("trust", false),
            auto_tag = getString("auto_tag"),
            apply_age_limit = getInt("apply_age_limit", 0),
            browser_id = getInt("browser_id", 0)
        )
    }

    /**
     * Converts an [EntryRule] to [ContentValues] for insert or update operations.
     *
     * @param rule The rule to convert.
     * @return Populated [ContentValues].
     */
    fun ruleToContentValues(rule: EntryRule): ContentValues {
        return ContentValues().apply {
            put("enabled", if (rule.enabled) 1 else 0)
            put("priority", rule.priority)
            put("rule_name", rule.rule_name.take(1000))
            put("trigger_rule_url", rule.trigger_rule_url.take(1000))
            put("trigger_text", rule.trigger_text.take(1000))
            put("trigger_text_hits", rule.trigger_text_hits)
            put("trigger_text_fields", rule.trigger_text_fields.take(1000))
            put("block", if (rule.block) 1 else 0)
            put("trust", if (rule.trust) 1 else 0)
            put("auto_tag", rule.auto_tag.take(1000))
            put("apply_age_limit", rule.apply_age_limit)
            put("browser_id", rule.browser_id)
        }
    }

    /**
     * Loads entry rules from the active database ordered by priority descending and id ascending.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param enabledOnly If true, only returns rules where `enabled = 1`.
     * @return List of [EntryRule] instances.
     */
    suspend fun getRules(
        context: Context,
        activeDatabaseState: DatabaseState?,
        enabledOnly: Boolean = false
    ): List<EntryRule> = withContext(Dispatchers.IO) {
        val rules = mutableListOf<EntryRule>()
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext rules
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext rules

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            ensureTableExists(db)
            val selection = if (enabledOnly) "enabled = 1" else null
            val cursor = db.query(
                getTableName(),
                COLUMNS,
                selection,
                null,
                null,
                null,
                "priority DESC, id ASC"
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    rules.add(cursorToEntryRule(c))
                }
            }
            db.close()
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Exception in $functionName", e.message)
            e.printStackTrace()
        }

        rules
    }

    /**
     * Retrieves an entry rule by its primary key [id].
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param id Primary key ID of the rule.
     * @return The [EntryRule], or null if not found.
     */
    suspend fun getRuleById(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): EntryRule? = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite) {
            return@withContext null
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext null

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            ensureTableExists(db)
            val cursor = db.query(
                getTableName(),
                COLUMNS,
                "id = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            )
            val rule = cursor.use { c ->
                if (c.moveToFirst()) {
                    cursorToEntryRule(c)
                } else null
            }
            db.close()
            rule
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Rule ID: $id Exception in $functionName", e.message)
            e.printStackTrace()
            null
        }
    }

    /**
     * Inserts a new entry rule into `entryrules`.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param rule The rule to insert.
     * @return Pair where first is the new row ID (or null on error), and second is an optional error message.
     */
    suspend fun insertRule(
        context: Context,
        activeDatabaseState: DatabaseState?,
        rule: EntryRule
    ): Pair<Long?, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(null, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(null, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val values = ruleToContentValues(rule)
            val rowId = db.insert(getTableName(), null, values)
            db.close()
            if (rowId != -1L) Pair(rowId, null) else Pair(null, "Failed to insert into ${getTableName()}")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Rule: ${rule.rule_name} Exception in $functionName", e.message)
            e.printStackTrace()
            Pair(null, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Updates an existing rule in `entryrules`.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param rule The rule containing updated fields and a non-null [EntryRule.id].
     * @return Pair where first is true on success, and second contains an optional error message.
     */
    suspend fun updateRule(
        context: Context,
        activeDatabaseState: DatabaseState?,
        rule: EntryRule
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (rule.id == null) {
            return@withContext Pair(false, "Rule ID is required for update")
        }
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            val values = ruleToContentValues(rule)
            val rows = db.update(getTableName(), values, "id = ?", arrayOf(rule.id.toString()))
            db.close()
            if (rows > 0) Pair(true, null) else Pair(false, "No rows updated; rule may not exist")
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Rule ID: ${rule.id} Exception in $functionName", e.message)
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Increments the `trigger_text_hits` counter by 1 for the specified rule.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @param id Primary key ID of the rule.
     * @return Pair where first is true on success, and second contains an optional error message.
     */
    suspend fun incrementTriggerHits(
        context: Context,
        activeDatabaseState: DatabaseState?,
        id: Long
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            db.execSQL("UPDATE ${getTableName()} SET trigger_text_hits = trigger_text_hits + 1 WHERE id = ?", arrayOf(id.toString()))
            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Rule ID: $id Exception in $functionName", e.message)
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }

    /**
     * Clears all records from the `entryrules` table.
     *
     * @param context Application context.
     * @param activeDatabaseState Current database state.
     * @return Pair where first is true on success, and second contains an optional error message.
     */
    override suspend fun clear(
        context: Context,
        activeDatabaseState: DatabaseState?
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (activeDatabaseState == null || !activeDatabaseState.isSQLite || activeDatabaseState.isReadOnly) {
            return@withContext Pair(false, "Database is not writable")
        }

        val file = File(context.filesDir, activeDatabaseState.localFileName)
        if (!file.exists()) return@withContext Pair(false, "Database file not found")

        try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            ensureTableExists(db)
            db.delete(getTableName(), null, null)
            db.close()
            Pair(true, null)
        } catch (e: Exception) {
            val functionName = object {}.javaClass.enclosingMethod?.name
            AppLoggingRepository.error(context, activeDatabaseState, "Clearing rules in $functionName", e.message)
            e.printStackTrace()
            Pair(false, e.message ?: "Unknown SQL error")
        }
    }
}
