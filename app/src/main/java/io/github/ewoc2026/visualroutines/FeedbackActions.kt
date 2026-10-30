package io.github.ewoc2026.visualroutines

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Public project contact; never populate feedback with private routine or device data. */
internal const val FEEDBACK_EMAIL = "visual-routines@oikumene.works"

/** Opens a user-controlled email draft. No message is sent by this application. */
internal fun openFeedbackEmail(context: Context): Boolean = try {
    context.startActivity(feedbackEmailIntent())
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** ACTION_SENDTO excludes generic sharing targets and needs no Internet permission. */
internal fun feedbackEmailIntent(): Intent = Intent(
    Intent.ACTION_SENDTO,
    Uri.fromParts("mailto", FEEDBACK_EMAIL, null),
).putExtra(Intent.EXTRA_SUBJECT, "Visual Routines feedback")
