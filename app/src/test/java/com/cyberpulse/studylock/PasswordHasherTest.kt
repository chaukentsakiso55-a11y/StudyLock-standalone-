package com.cyberpulse.studylock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun recordVerifiesOnlyTheCorrectPassword() {
        val record = PasswordHasher.createRecord("2468")

        assertTrue(PasswordHasher.verify("2468", record))
        assertFalse(PasswordHasher.verify("8642", record))
    }

    @Test
    fun equalPasswordsUseDifferentSalts() {
        val first = PasswordHasher.createRecord("study-lock")
        val second = PasswordHasher.createRecord("study-lock")

        assertNotEquals(first, second)
        assertTrue(PasswordHasher.verify("study-lock", first))
        assertTrue(PasswordHasher.verify("study-lock", second))
    }
}
