package com.cedrickgd.morgenbriefing.ui.discover

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoverRestoreTest {

    private val key = DiscoverViewModel.KEY_CATEGORY

    @Test
    fun `a fresh screen opens the category its route names`() {
        assertEquals("wirtschaft", DiscoverViewModel.restoredCategory(SavedStateHandle(), "wirtschaft"))
        assertNull(DiscoverViewModel.restoredCategory(SavedStateHandle(), null))
        // Sub-topics and unknown ids are no category: the grid.
        assertNull(DiscoverViewModel.restoredCategory(SavedStateHandle(), "wirtschaft/boerse"))
        assertNull(DiscoverViewModel.restoredCategory(SavedStateHandle(), "gibt-es-nicht"))
    }

    @Test
    fun `after process death the screen comes back where the user left it`() {
        assertEquals("sport", DiscoverViewModel.restoredCategory(SavedStateHandle(mapOf(key to "sport")), "wirtschaft"))
        // Went back to the grid from the category the route opened: the grid, not the route's category.
        assertNull(DiscoverViewModel.restoredCategory(SavedStateHandle(mapOf(key to null)), "wirtschaft"))
    }
}
