package com.bird.fiber.data.local

import com.bird.fiber.data.local.library.TagReferenceRow
import org.junit.Assert.*
import org.junit.Test

class TagTreeTest {
    private fun row(path: String, uri: String? = null, time: Long? = null, manual: Boolean = false, created: Long = 1) =
        TagReferenceRow(path, path, created, manual, uri, time)

    @Test fun parentsUnionNotesAndInheritLatestModification() {
        val root = buildTagTree(listOf(row("读书/心理", "a", 10), row("读书/历史", "a", 10), row("读书/历史", "b", 20))).single()
        assertEquals(2, root.noteCount)
        assertEquals(20L, root.sortTime)
        assertTrue(root.isVirtual)
        assertFalse(root.canDelete)
        assertEquals(listOf("读书/历史", "读书/心理"), root.children.map { it.path })
    }

    @Test fun explicitAndManualFactsRemainIndependent() {
        val root = buildTagTree(listOf(row("a", "note", 10), row("a/b", "note", 10))).single()
        assertTrue(root.hasDirectReference)
        assertTrue(root.isExplicit)
        assertFalse(root.isManual)
        assertEquals(1, root.noteCount)
        val manual = buildTagTree(listOf(row("a", manual = true), row("a/b", manual = true))).single()
        assertTrue(manual.canDelete)
        val virtual = buildTagTree(listOf(row("a/b", manual = true))).single()
        assertTrue(virtual.isVirtual)
        assertFalse(virtual.canDelete)
        assertTrue(virtual.children.single().canDelete)
    }

    @Test fun emptyBranchesSortByCreationAndTiesByPath() {
        val nodes = buildTagTree(listOf(row("z", manual = true, created = 5), row("b", "note", 10), row("a", "note", 10)))
        assertEquals(listOf("a", "b", "z"), nodes.map { it.path })
    }
}
