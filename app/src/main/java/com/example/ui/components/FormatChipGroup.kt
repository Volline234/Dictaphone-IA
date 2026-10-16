package com.example.ui.components

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.DeliverableType

@Composable
fun FormatChipGroup(
    selectedType: DeliverableType,
    onTypeSelected: (DeliverableType) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DeliverableType.values().forEach { type ->
            val isSelected = type == selectedType
            val icon = when (type) {
                DeliverableType.EMAIL_PRO -> Icons.Default.Email
                DeliverableType.MEETING_TASKS -> Icons.Default.FactCheck
                DeliverableType.CONTENT_CREATOR -> Icons.Default.Campaign
                DeliverableType.CUSTOM -> Icons.Default.Tune
            }

            FilterChip(
                selected = isSelected,
                onClick = { onTypeSelected(type) },
                label = { Text(stringResource(type.titleRes), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(type.titleRes),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("format_chip_${type.name.lowercase()}")
            )
        }
    }
}
