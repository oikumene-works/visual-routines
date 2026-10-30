package io.github.ewoc2026.visualroutines

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pins the outbound privacy boundary, including the minimum supported Android API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class FeedbackActionsTest {
    @Test fun draftContainsOnlyPublicRecipientAndSubject() {
        val intent = feedbackEmailIntent()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data!!.scheme)
        assertEquals(FEEDBACK_EMAIL, intent.data!!.schemeSpecificPart)
        assertEquals(setOf(Intent.EXTRA_SUBJECT), intent.extras!!.keySet())
        assertEquals("Visual Routines feedback", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertNull(intent.clipData)
        assertEquals(0, intent.flags)
    }

    @Test fun launchesOneDraftWithoutSending() {
        var launched: Intent? = null
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { check(launched == null); launched = intent }
        }
        assertTrue(openFeedbackEmail(context))
        assertEquals(Intent.ACTION_SENDTO, launched?.action)
    }

    @Test fun missingHandlerReturnsRecoverableResult() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertFalse(openFeedbackEmail(context))
    }
}
