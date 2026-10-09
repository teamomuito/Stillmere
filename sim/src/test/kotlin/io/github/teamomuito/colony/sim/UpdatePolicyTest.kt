package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test fun readsTheBuildNumberFromATag() {
        assertEquals(19, UpdatePolicy.buildOf("build-19"))
        assertEquals(19, UpdatePolicy.buildOf(" build-19 "))
        assertNull(UpdatePolicy.buildOf("v2.0.0"))
        assertNull(UpdatePolicy.buildOf("build-"))
        assertNull(UpdatePolicy.buildOf("build-19-beta"))
    }

    @Test fun offersOnlyANewerBuild() {
        assertTrue(UpdatePolicy.isNewer("build-20", 19))
        assertFalse("the same build is not an update", UpdatePolicy.isNewer("build-19", 19))
        assertFalse("an older build is never offered", UpdatePolicy.isNewer("build-18", 19))
        assertFalse("an unknown tag is never offered", UpdatePolicy.isNewer("nightly", 19))
    }
}
