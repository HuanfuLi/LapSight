package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.huanfuli.lapsight.shared.storage.LocalSessionStore

/**
 * Review tab (Task 1 stub): lists saved tracks/markings.
 *
 * Task 2 replaces this with the full saved-Track list (DEMO badges, source
 * metadata, empty state) re-read from [LocalSessionStore.readIndex] whenever the
 * Drive tab saves a track ([savedVersion] changes).
 */
@Composable
fun ReviewScreen(
    sessionStore: LocalSessionStore,
    savedVersion: Long,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Review (populated in Task 2)")
    }
}
