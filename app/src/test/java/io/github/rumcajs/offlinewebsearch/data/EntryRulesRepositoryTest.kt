package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRulesRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [EntryRulesRepository].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryRulesRepositoryTest {

    private lateinit var context: Context
    private lateinit var dbState: DatabaseState
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val (state, file) = RepositoryTestHelper.setup(context)
        dbState = state
        dbFile = file
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `insertRule and getRuleById correctly persist and read all fields`() = runBlocking {
        val rule = EntryRule(
            enabled = true,
            priority = 10,
            rule_name = "Block Ads",
            trigger_rule_url = "https://ads.*",
            trigger_text = "sponsor",
            trigger_text_hits = 5,
            trigger_text_fields = "title,description",
            block = true,
            trust = false,
            auto_tag = "advertisement",
            apply_age_limit = 18,
            script = "console.log('hit');",
            browser_id = 2
        )

        val (rowId, err) = EntryRulesRepository.insertRule(context, dbState, rule)
        assertNull("Insert should not have error: $err", err)
        assertNotNull("Inserted row ID should not be null", rowId)
        assertTrue(rowId!! > 0)

        val fetched = EntryRulesRepository.getRuleById(context, dbState, rowId)
        assertNotNull("Rule should be found", fetched)
        assertEquals(rowId, fetched!!.id)
        assertEquals(true, fetched.enabled)
        assertEquals(10, fetched.priority)
        assertEquals("Block Ads", fetched.rule_name)
        assertEquals("https://ads.*", fetched.trigger_rule_url)
        assertEquals("sponsor", fetched.trigger_text)
        assertEquals(5, fetched.trigger_text_hits)
        assertEquals("title,description", fetched.trigger_text_fields)
        assertEquals(true, fetched.block)
        assertEquals(false, fetched.trust)
        assertEquals("advertisement", fetched.auto_tag)
        assertEquals(18, fetched.apply_age_limit)
        assertEquals("console.log('hit');", fetched.script)
        assertEquals(2, fetched.browser_id)
    }

    @Test
    fun `getRules respects priority order and enabledOnly filter`() = runBlocking {
        val rule1 = EntryRule(rule_name = "Rule Low Priority", priority = 1, enabled = true)
        val rule2 = EntryRule(rule_name = "Rule High Priority", priority = 100, enabled = true)
        val rule3 = EntryRule(rule_name = "Rule Disabled High Priority", priority = 200, enabled = false)

        EntryRulesRepository.insertRule(context, dbState, rule1)
        EntryRulesRepository.insertRule(context, dbState, rule2)
        EntryRulesRepository.insertRule(context, dbState, rule3)

        val allRules = EntryRulesRepository.getRules(context, dbState, enabledOnly = false)
        assertEquals(3, allRules.size)
        assertEquals("Rule Disabled High Priority", allRules[0].rule_name)
        assertEquals("Rule High Priority", allRules[1].rule_name)
        assertEquals("Rule Low Priority", allRules[2].rule_name)

        val enabledRules = EntryRulesRepository.getRules(context, dbState, enabledOnly = true)
        assertEquals(2, enabledRules.size)
        assertEquals("Rule High Priority", enabledRules[0].rule_name)
        assertEquals("Rule Low Priority", enabledRules[1].rule_name)
    }

    @Test
    fun `updateRule modifies existing record`() = runBlocking {
        val initial = EntryRule(rule_name = "Initial Name", enabled = true, priority = 5)
        val (rowId, _) = EntryRulesRepository.insertRule(context, dbState, initial)
        assertNotNull(rowId)

        val updated = initial.copy(
            id = rowId,
            rule_name = "Updated Name",
            enabled = false,
            priority = 20,
            block = true
        )
        val (ok, err) = EntryRulesRepository.updateRule(context, dbState, updated)
        assertTrue("Update should succeed: $err", ok)

        val reloaded = EntryRulesRepository.getRuleById(context, dbState, rowId!!)
        assertNotNull(reloaded)
        assertEquals("Updated Name", reloaded!!.rule_name)
        assertEquals(false, reloaded.enabled)
        assertEquals(20, reloaded.priority)
        assertEquals(true, reloaded.block)
    }

    @Test
    fun `incrementTriggerHits increases counter by 1`() = runBlocking {
        val rule = EntryRule(rule_name = "Hit Counter Test", trigger_text_hits = 0)
        val (rowId, _) = EntryRulesRepository.insertRule(context, dbState, rule)
        assertNotNull(rowId)

        val (hit1Ok, _) = EntryRulesRepository.incrementTriggerHits(context, dbState, rowId!!)
        assertTrue(hit1Ok)
        assertEquals(1, EntryRulesRepository.getRuleById(context, dbState, rowId)?.trigger_text_hits)

        val (hit2Ok, _) = EntryRulesRepository.incrementTriggerHits(context, dbState, rowId)
        assertTrue(hit2Ok)
        assertEquals(2, EntryRulesRepository.getRuleById(context, dbState, rowId)?.trigger_text_hits)
    }

    @Test
    fun `deleteById and clear remove rules`() = runBlocking {
        val (r1Id, _) = EntryRulesRepository.insertRule(context, dbState, EntryRule(rule_name = "Rule 1"))
        val (r2Id, _) = EntryRulesRepository.insertRule(context, dbState, EntryRule(rule_name = "Rule 2"))

        assertEquals(2L, EntryRulesRepository.count(context, dbState))

        val (delOk, _) = EntryRulesRepository.deleteById(context, dbState, r1Id!!)
        assertTrue(delOk)
        assertEquals(1L, EntryRulesRepository.count(context, dbState))
        assertNull(EntryRulesRepository.getRuleById(context, dbState, r1Id))
        assertNotNull(EntryRulesRepository.getRuleById(context, dbState, r2Id!!))

        val (clearOk, _) = EntryRulesRepository.clear(context, dbState)
        assertTrue(clearOk)
        assertEquals(0L, EntryRulesRepository.count(context, dbState))
    }
}
