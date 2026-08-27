package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import java.io.File

/**
 * Shared helper for unit tests that exercise repository implementations.
 *
 * It copies `assets/table.db` into the Robolectric context's `filesDir` under a
 * unique name so each test run starts from a clean copy of the reference database.
 * Use [setup] to obtain a writable [DatabaseState] backed by the copy.
 *
 * Typical usage:
 * ```
 * val context = ApplicationProvider.getApplicationContext<Context>()
 * val (state, dbFile) = RepositoryTestHelper.setup(context)
 * // ... exercise repository ...
 * dbFile.delete()  // optional cleanup
 * ```
 */
object RepositoryTestHelper {

    private const val ASSET_DB_NAME = "table.db"

    /**
     * Copies `assets/table.db` to [context]'s `filesDir` under [destFileName] and
     * returns a pair of ([DatabaseState], [File]) where the state points to the copy.
     *
     * @param context       Application context provided by Robolectric.
     * @param destFileName  Name for the copied file inside `filesDir`. Defaults to a
     *                      timestamp-based unique name to avoid cross-test interference.
     * @return Pair of the writable [DatabaseState] and the backing [File].
     */
    fun setup(
        context: Context,
        destFileName: String = "test_db_${System.nanoTime()}.db"
    ): Pair<DatabaseState, File> {
        val destFile = File(context.filesDir, destFileName)
        copyAssetToFile(context, ASSET_DB_NAME, destFile)

        val state = DatabaseState(
            url = "local://test",
            localFileName = destFileName,
            status = DatabaseStatus.READY,
            isReadOnly = false
        )
        return Pair(state, destFile)
    }

    /**
     * Opens [assetName] from [context]'s asset manager and writes it to [dest].
     */
    private fun copyAssetToFile(context: Context, assetName: String, dest: File) {
        dest.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
