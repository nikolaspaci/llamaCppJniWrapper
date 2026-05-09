package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.util.DeviceCapabilities

@Composable
fun HardwareInfoCard(
    capabilities: DeviceCapabilities,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.hardware_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            HorizontalDivider()

            // CPU
            HardwareInfoRow(
                label = stringResource(R.string.hardware_cpu),
                value = stringResource(
                    R.string.hardware_cpu_value,
                    capabilities.cpuCores,
                    capabilities.cpuArchitecture
                )
            )

            // RAM
            HardwareInfoRow(
                label = stringResource(R.string.hardware_ram),
                value = stringResource(
                    R.string.hardware_ram_value,
                    formatBytesDisplay(capabilities.totalRamBytes),
                    formatBytesDisplay(capabilities.availableRamBytes)
                )
            )

            // GPU / Vulkan
            if (capabilities.hasVulkan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.hardware_gpu),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = capabilities.gpuName ?: "Vulkan ${capabilities.vulkanVersion}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (capabilities.gpuVramBytes > 0) {
                            Text(
                                text = stringResource(R.string.hardware_vram, formatBytesDisplay(capabilities.gpuVramBytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.hardware_gpu_available),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                if (capabilities.recommendedGpuLayers > 0) {
                    Text(
                        text = stringResource(R.string.hardware_gpu_layers, capabilities.recommendedGpuLayers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.hardware_gpu_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }

            // NPU
            if (capabilities.hasNpu) {
                HardwareInfoRow(
                    label = stringResource(R.string.hardware_npu),
                    value = capabilities.npuType ?: stringResource(R.string.hardware_npu_available)
                )
            }
        }
    }
}

@Composable
private fun HardwareInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatBytesDisplay(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) {
        "%.1f GB".format(gb)
    } else {
        val mb = bytes / (1024.0 * 1024.0)
        "%.0f MB".format(mb)
    }
}
