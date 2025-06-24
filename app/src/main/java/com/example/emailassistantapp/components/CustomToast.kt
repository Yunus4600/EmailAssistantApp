package com.example.emailassistantapp.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.zIndex

enum class ToastType {
    SUCCESS,
    ERROR
}

data class ToastData(
    val message: String,
    val type: ToastType,
    val duration: Long = if (type == ToastType.SUCCESS) 1500L else 2000L
)

@Composable
fun CustomToast(
    toastData: ToastData?,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    var progress by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = toastData != null,
        enter = slideInVertically {
            with(density) { -100.dp.roundToPx() }
        } + fadeIn(),
        exit = slideOutVertically {
            with(density) { -100.dp.roundToPx() }
        } + fadeOut()
    ) {
        toastData?.let { toast ->
            LaunchedEffect(toast, isPaused) {
                if (!isPaused) {
                    progress = 0f
                    val updateInterval = 16L // ~60 FPS
                    val steps = toast.duration / updateInterval
                    while (progress < 1f) {
                        delay(updateInterval)
                        progress += 1f / steps
                    }
                    onDismiss()
                }
            }

            Popup(
                alignment = Alignment.TopCenter,
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    excludeFromSystemGesture = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .zIndex(100f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = { isPaused = true },
                                    onTap = { onDismiss() }
                                )
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (toast.type) {
                                ToastType.SUCCESS -> Color(0xFF4CAF50) // Light green
                                ToastType.ERROR -> Color(0xFFD32F2F) // Dark red
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = when (toast.type) {
                                        ToastType.SUCCESS -> Icons.Default.CheckCircle
                                        ToastType.ERROR -> Icons.Default.Error
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = toast.message,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onDismiss() }
                                )
                            }
                            // Progress bar
                            LinearProgressIndicator(
                                progress = 1f - progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = Color.White.copy(alpha = 0.5f),
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberToastState(): MutableState<ToastData?> {
    return remember { mutableStateOf(null) }
}

fun MutableState<ToastData?>.showSuccessToast(message: String) {
    this.value = ToastData(message, ToastType.SUCCESS)
}

fun MutableState<ToastData?>.showErrorToast(message: String) {
    this.value = ToastData(message, ToastType.ERROR)
}

fun MutableState<ToastData?>.showToast(
    message: String,
    type: ToastType = ToastType.SUCCESS,
    duration: Long? = null
) {
    this.value = ToastData(
        message = message,
        type = type,
        duration = duration ?: if (type == ToastType.SUCCESS) 1500L else 2000L
    )
} 