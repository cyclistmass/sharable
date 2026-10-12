package com.mauro.wgbg

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Optional Google sign-in (#83): link keeps the player id; an account already in use switches; sign-out gives a guest. */
class GoogleAccountTest {
    /** A pretend Firebase Auth: Google accounts by token, each owned by at most one player id. */
    private class FakeAuth(start: String? = "guest-1") : GoogleAccount.Ops {
        override var uid: String? = start
        val owners = mutableMapOf<String, String>()     // google token -> uid that owns it
        val emails = mapOf("tok-mauro" to "mauro@example.com", "tok-erin" to "erin@example.com")
        var online = true
        private var guests = 1
        override val email get() = owners.entries.firstOrNull { it.value == uid }?.let { emails[it.key] }
        override val isGoogle get() = owners.containsValue(uid)
        override suspend fun link(idToken: String) {
            if (!online) error("Network error")
            val owner = owners[idToken]
            if (owner != null && owner != uid) throw GoogleAccount.AlreadyInUse()
            owners[idToken] = uid!!
        }
        override suspend fun signInWith(idToken: String) { if (!online) error("Network error"); uid = owners.getValue(idToken) }
        override suspend fun freshGuest() { if (!online) error("Network error"); uid = "guest-${++guests}" }
    }

    @Test
    fun `signing in links Google to the guest account and keeps its id`() = runBlocking {
        val auth = FakeAuth()
        assertFalse(GoogleAccount.status(auth).signedIn)
        val o = GoogleAccount.signIn(auth, "tok-mauro")
        assertEquals(GoogleAccount.Outcome.Linked("mauro@example.com"), o)
        assertEquals("guest-1", auth.uid)
        assertEquals(GoogleAccount.Status(true, "mauro@example.com", "guest-1"), GoogleAccount.status(auth))
        assertTrue(GoogleAccount.message(o).contains("kept"))
    }

    @Test
    fun `a Google account already used by another player switches to it and says the guest games stay`() = runBlocking {
        val auth = FakeAuth("phone-a")
        auth.owners["tok-mauro"] = "phone-b"   // signed in on another phone first
        val o = GoogleAccount.signIn(auth, "tok-mauro")
        assertEquals(GoogleAccount.Outcome.Switched("mauro@example.com", "phone-a", "phone-b"), o)
        assertEquals("phone-b", auth.uid)
        assertTrue(GoogleAccount.message(o).contains("stay with the guest"))
    }

    @Test
    fun `sign out gives a fresh guest, and signing back in finds the same account`() = runBlocking {
        val auth = FakeAuth()
        GoogleAccount.signIn(auth, "tok-erin")
        GoogleAccount.signOut(auth)
        assertNotEquals("guest-1", auth.uid)
        assertFalse(GoogleAccount.status(auth).signedIn)
        val back = GoogleAccount.signIn(auth, "tok-erin")
        assertTrue(back is GoogleAccount.Outcome.Switched)
        assertEquals("guest-1", auth.uid)
        assertEquals("erin@example.com", GoogleAccount.status(auth).email)
    }

    @Test
    fun `offline or not yet connected fails without changing who you are`() = runBlocking {
        val auth = FakeAuth(); auth.online = false
        val o = GoogleAccount.signIn(auth, "tok-mauro")
        assertTrue(o is GoogleAccount.Outcome.Failed)
        assertEquals("guest-1", auth.uid)
        assertFalse(GoogleAccount.status(auth).signedIn)
        assertTrue(GoogleAccount.signIn(FakeAuth(start = null), "tok-mauro") is GoogleAccount.Outcome.Failed)
    }

    @Test
    fun `a cancelled picker says nothing`() {
        assertEquals("", GoogleAccount.message(GoogleAccount.Outcome.Cancelled))
    }
}
