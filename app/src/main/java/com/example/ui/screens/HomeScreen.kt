package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billing.SubscriptionStatus
import com.example.model.DeliverableType
import com.example.ui.components.AudioVisualizerWaveform
import com.example.ui.components.ChecklistComponent
import com.example.ui.components.DeliverableCard
import com.example.ui.components.FormatChipGroup
import com.example.ui.components.ThemeQuickToggleButton
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.RecordingRedGlow
import com.example.ui.viewmodel.MainViewModel
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRecording by viewModel.recorderManager.isRecording.collectAsStateWithLifecycle()
    val isPaused by viewModel.recorderManager.isPaused.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.recorderManager.elapsedSeconds.collectAsStateWithLifecycle()
    val amplitudes by viewModel.recorderManager.amplitudesHistory.collectAsStateWithLifecycle()
    val liveSpeechText by viewModel.speechHelper.liveTranscript.collectAsStateWithLifecycle()

    val rawTranscript by viewModel.rawTranscript.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val selectedTone by viewModel.selectedTone.collectAsStateWithLifecycle()
    val generatedContent by viewModel.generatedContent.collectAsStateWithLifecycle()
    val checklistItems by viewModel.checklistItems.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val subscriptionStatus by viewModel.billingManager.subscriptionStatus.collectAsStateWithLifecycle()
    val remainingFree by viewModel.billingManager.remainingFreeGenerations.collectAsStateWithLifecycle()
    val trialDaysLeft by viewModel.billingManager.trialDaysLeft.collectAsStateWithLifecycle()

    val activeNoteId by viewModel.currentActiveNoteId.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val currentNote = allNotes.firstOrNull { it.id == activeNoteId }

    // Permission launcher for Recording (Microphone)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    fun handleRecordClick() {
        if (isRecording) {
            viewModel.stopRecordingAndGenerate()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.startRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Quota / Trial Badge & Language Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subscription / Trial Pill (F-30, F-32)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (subscriptionStatus) {
                    SubscriptionStatus.FREE_TRIAL -> MaterialTheme.colorScheme.tertiaryContainer
                    SubscriptionStatus.PREMIUM_WEEKLY, SubscriptionStatus.PREMIUM_MONTHLY -> MaterialTheme.colorScheme.primaryContainer
                    SubscriptionStatus.FREE_TIER_EXPIRED -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToPaywall() }
                    .testTag("subscription_status_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (subscriptionStatus) {
                            SubscriptionStatus.FREE_TRIAL -> MaterialTheme.colorScheme.tertiary
                            SubscriptionStatus.PREMIUM_WEEKLY, SubscriptionStatus.PREMIUM_MONTHLY -> MaterialTheme.colorScheme.primary
                            SubscriptionStatus.FREE_TIER_EXPIRED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (subscriptionStatus) {
                            SubscriptionStatus.FREE_TRIAL -> stringResource(R.string.home_badge_trial, trialDaysLeft)
                            SubscriptionStatus.PREMIUM_WEEKLY, SubscriptionStatus.PREMIUM_MONTHLY -> stringResource(R.string.home_badge_premium)
                            SubscriptionStatus.FREE_TIER_EXPIRED -> stringResource(R.string.home_badge_free_remaining, remainingFree)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when (subscriptionStatus) {
                            SubscriptionStatus.FREE_TRIAL -> MaterialTheme.colorScheme.onTertiaryContainer
                            SubscriptionStatus.PREMIUM_WEEKLY, SubscriptionStatus.PREMIUM_MONTHLY -> MaterialTheme.colorScheme.onPrimaryContainer
                            SubscriptionStatus.FREE_TIER_EXPIRED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Quick Day/Night Toggle + Language Selector FR / EN (F-05)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeQuickToggleButton(viewModel = viewModel)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedLanguage == "fr") MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .clickable { viewModel.setLanguage("fr") }
                            .testTag("lang_fr_button")
                    ) {
                        Text(
                            text = "FR",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedLanguage == "fr") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedLanguage == "en") MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .clickable { viewModel.setLanguage("en") }
                            .testTag("lang_en_button")
                    ) {
                        Text(
                            text = "EN",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedLanguage == "en") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Format Selector Pills (F-10, F-11, F-12, F-14)
        Text(
            text = stringResource(R.string.home_output_format_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 6.dp)
        )
        FormatChipGroup(
            selectedType = selectedType,
            onTypeSelected = { type ->
                if (type == DeliverableType.CUSTOM) {
                    onNavigateToTemplates()
                } else {
                    viewModel.setDeliverableType(type)
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Central Recording Area / Live Waveform (Sleek 28dp Hero Card)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recording_studio_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRecording) {
                    // Recording Timer & Live Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isPaused) MaterialTheme.colorScheme.outline else RecordingRed,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (isPaused) R.string.home_status_paused else R.string.home_status_recording),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) MaterialTheme.colorScheme.outline else RecordingRed
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val minutes = elapsedSeconds / 60
                    val seconds = elapsedSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Audio Waveform
                    AudioVisualizerWaveform(
                        amplitudes = amplitudes,
                        isRecording = isRecording,
                        isPaused = isPaused
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Speech Preview
                    if (liveSpeechText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "« $liveSpeechText »",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Recording Controls: Pause/Resume, Stop, Cancel (F-04)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button
                        IconButton(
                            onClick = { viewModel.cancelRecording() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("cancel_record_button")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.home_cd_cancel_recording),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        // Stop & Generate FAB (F-01)
                        FilledIconButton(
                            onClick = { handleRecordClick() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = RecordingRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(72.dp)
                                .shadow(8.dp, CircleShape, spotColor = RecordingRedGlow)
                                .testTag("stop_record_button")
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = stringResource(R.string.home_cd_stop_and_structure),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Pause / Resume Button (F-04)
                        IconButton(
                            onClick = {
                                if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("pause_resume_record_button")
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = stringResource(if (isPaused) R.string.home_cd_resume else R.string.home_cd_pause),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    // Idle Record State (F-01: 1-Tap Record)
                    Text(
                        text = stringResource(R.string.home_idle_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_idle_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Big Pulse Mic Button (F-01)
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                            .clip(CircleShape)
                            .clickable { handleRecordClick() }
                            .testTag("start_record_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = stringResource(R.string.home_cd_start_recording),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Simulation / Demo Buttons for easy exploration
                    val demoTranscript = stringResource(R.string.home_demo_transcript)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.updateRawTranscript(demoTranscript)
                            },
                            modifier = Modifier.testTag("demo_memo_button")
                        ) {
                            Text(stringResource(R.string.home_demo_button), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Sleek 2-Column Stats Indicators (Grid style from design)
        if (!isRecording && generatedContent.isBlank() && !isGenerating) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stat 1: Total Memos
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToHistory() },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "${allNotes.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.home_stat_saved_notes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stat 2: Deliverables Ready
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToTemplates() },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "${allNotes.count { it.generatedContent.isNotBlank() }}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.home_stat_structured_deliverables),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active Deliverable Result Card (F-10, F-11, F-12, F-13, F-03)
        if (generatedContent.isNotBlank() || isGenerating) {
            DeliverableCard(
                deliverableType = selectedType,
                toneType = selectedTone,
                generatedText = generatedContent,
                rawTranscript = rawTranscript,
                isGenerating = isGenerating,
                isFavorite = currentNote?.isFavorite ?: false,
                onToneChanged = { tone -> viewModel.setTone(tone) },
                onTypeChanged = { type -> viewModel.setDeliverableType(type) },
                onRawTranscriptEdited = { edited -> viewModel.updateRawTranscript(edited) },
                onRegenerate = { viewModel.regenerate() },
                onToggleFavorite = {
                    currentNote?.let { viewModel.toggleFavorite(it) }
                }
            )

            // If Meeting Tasks, show interactive checklist below (F-11)
            if (selectedType == DeliverableType.MEETING_TASKS) {
                Spacer(modifier = Modifier.height(16.dp))
                ChecklistComponent(
                    items = checklistItems,
                    onItemToggle = { id, done -> viewModel.toggleChecklistItem(id, done) },
                    onItemDelete = { id -> viewModel.deleteChecklistItem(id) },
                    onItemAdd = { text -> viewModel.addChecklistItem(text) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // New Note action
            OutlinedButton(
                onClick = { viewModel.createNewNote() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_new_note_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.home_new_recording_button))
            }
        }
    }
}
