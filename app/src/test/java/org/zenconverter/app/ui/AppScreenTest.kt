package org.zenconverter.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the navigation contract introduced when the glass hub became the home
 * screen: the app opens on [AppScreen.Home], and the converter it replaced stays
 * reachable as [AppScreen.Converter].
 */
class AppScreenTest {

    @Test
    fun homeIsTheRootScreen() {
        assertEquals(AppScreen.Home, AppScreen.entries.first())
    }

    @Test
    fun converterRemainsReachable() {
        assertTrue(AppScreen.Converter in AppScreen.entries)
    }
}
