package com.nikolaspaci.app.llamallmlocal.ui.huggingface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.data.curated.CuratedFacets
import com.nikolaspaci.app.llamallmlocal.data.curated.CuratedFilter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CuratedFiltersBar(
    filter: CuratedFilter,
    facets: CuratedFacets,
    onFilterChange: ((CuratedFilter) -> CuratedFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (facets.paramsLabels.isNotEmpty()) {
            FacetChipGroup(
                title = stringResource(R.string.hf_filter_params),
                values = facets.paramsLabels,
                selected = filter.paramsLabels,
                onToggle = { value, isOn ->
                    onFilterChange { f ->
                        f.copy(paramsLabels = f.paramsLabels.toggle(value, isOn))
                    }
                }
            )
        }

        if (facets.quantizations.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FacetChipGroup(
                title = stringResource(R.string.hf_filter_quantization),
                values = facets.quantizations,
                selected = filter.quantizations,
                onToggle = { value, isOn ->
                    onFilterChange { f ->
                        f.copy(quantizations = f.quantizations.toggle(value, isOn))
                    }
                }
            )
        }

        if (facets.providers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FacetChipGroup(
                title = stringResource(R.string.hf_filter_provider),
                values = facets.providers,
                selected = filter.providers,
                onToggle = { value, isOn ->
                    onFilterChange { f ->
                        f.copy(providers = f.providers.toggle(value, isOn))
                    }
                }
            )
        }

        if (!filter.isEmpty) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.hf_filter_clear))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FacetChipGroup(
    title: String,
    values: List<String>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { value ->
                val isSelected = value in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(value, !isSelected) },
                    label = { Text(value) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }
    }
}

private fun Set<String>.toggle(value: String, on: Boolean): Set<String> =
    if (on) this + value else this - value
