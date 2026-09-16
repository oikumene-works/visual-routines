package io.github.ewoc2026.visualroutines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyPolicyScreen(state: VisualRoutinesState) {
    BackHandler(onBack = state::backToSettings)
    val title = stringResource(R.string.privacy_policy_title)
    val backActionText = stringResource(R.string.back_action)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = state::backToSettings,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = backActionText },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        modifier = Modifier.statusBarsPadding(),
    ) { padding ->
        ScreenSurface(
            modifier = Modifier.padding(padding),
            applySystemBarsPadding = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .semantics {
                        isTraversalGroup = true
                        paneTitle = title
                    },
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.privacy_policy_effective_date),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.privacy_policy_intro),
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrivacyPolicySection(
                    heading = stringResource(R.string.privacy_policy_local_data_heading),
                    body = stringResource(R.string.privacy_policy_local_data_body),
                )
                PrivacyPolicySection(
                    heading = stringResource(R.string.privacy_policy_collection_heading),
                    body = stringResource(R.string.privacy_policy_collection_body),
                )
                PrivacyPolicySection(
                    heading = stringResource(R.string.privacy_policy_transfer_heading),
                    body = stringResource(R.string.privacy_policy_transfer_body),
                )
                PrivacyPolicySection(
                    heading = stringResource(R.string.privacy_policy_retention_heading),
                    body = stringResource(R.string.privacy_policy_retention_body),
                )
                PrivacyPolicySection(
                    heading = stringResource(R.string.privacy_policy_security_heading),
                    body = stringResource(R.string.privacy_policy_security_body),
                )
                PrivacyPolicySection(
                    heading = stringResource(R.string.privacy_policy_changes_heading),
                    body = stringResource(R.string.privacy_policy_changes_body),
                )
                PrivacyPolicySection(
                    heading = stringResource(R.string.privacy_policy_contact_heading),
                    body = stringResource(R.string.privacy_policy_contact_body),
                )
            }
        }
    }
}

@Composable
private fun PrivacyPolicySection(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
