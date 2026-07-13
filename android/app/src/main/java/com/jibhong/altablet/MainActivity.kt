package com.jibhong.altablet

import com.jibhong.altablet.ui.theme.StylusInputView
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt
import android.content.Context

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive Mode Setup
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            MaterialTheme {
                // Parent container to allow the box to sit anywhere
                Box(modifier = Modifier.fillMaxSize()) {
                    ResizableBox()

                }
            }
        }
    }
}
@Composable
fun ResizableBox() {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("TabletPrefs", Context.MODE_PRIVATE)

    var width by remember { mutableStateOf(sharedPref.getFloat("width", 200f).dp) }
    var height by remember { mutableStateOf(sharedPref.getFloat("height", 200f).dp) }

    // Optional: add position state so you can move it away from the screen edge
    var offsetX by remember { mutableStateOf(sharedPref.getFloat("offsetX", 50f)) }
    var offsetY by remember { mutableStateOf(sharedPref.getFloat("offsetY", 50f)) }

    var isLocked by remember { mutableStateOf(sharedPref.getBoolean("isLocked", false)) }
    var showDropdown by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(width, height)
                .background(Color.LightGray)
                .border(2.dp, Color.Black)
        ) {

            if (!isLocked) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(0.dp.roundToPx(), -24.dp.roundToPx())
                        }
                        .size(width,48.dp) // Large touch target
                        .align(Alignment.TopCenter)
                        .background(Color.Yellow) // Using Red so you can see if you're hitting it
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                with(density) {
                                    // Update width/height and prevent them from becoming negative
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(0.dp.roundToPx(), -24.dp.roundToPx())
                        }
                        .size(48.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Green)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                offsetX = 0f
                                // Correct way to get pixel value from Dp
                                val screenHeightPx = with(density) { screenHeight.toPx() }
                                val boxHeightPx = with(density) { (screenWidth / 16 * 9).toPx() }

                                // Center it: (ScreenHeight / 2) - (BoxHeight / 2)
                                offsetY = (screenHeightPx - boxHeightPx) / 2

                                width = screenWidth
                                height = screenWidth / 16 * 9
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(24.dp.roundToPx(), 24.dp.roundToPx())
                        }
                        .size(48.dp) // Large touch target
                        .align(Alignment.BottomEnd)
                        .background(Color.Red) // Using Red so you can see if you're hitting it
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                with(density) {
                                    // Update width/height and prevent them from becoming negative
                                    width = (width + dragAmount.x.toDp()).coerceAtLeast(50.dp)
                                    height = width/16*9
                                }
                            }
                        }
                )
            }
            AndroidView(
                factory = { context ->
                    // Replace this with your actual StylusInputView class
                    StylusInputView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        startServer()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Top right button for control panel
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            IconButton(onClick = { showDropdown = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Save Position & Size") },
                    onClick = {
                        sharedPref.edit()
                            .putFloat("width", width.value)
                            .putFloat("height", height.value)
                            .putFloat("offsetX", offsetX)
                            .putFloat("offsetY", offsetY)
                            .putBoolean("isLocked", isLocked)
                            .apply()
                        showDropdown = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isLocked) "Unlock Controls" else "Lock Controls") },
                    onClick = {
                        isLocked = !isLocked
                        sharedPref.edit().putBoolean("isLocked", isLocked).apply()
                        showDropdown = false
                    }
                )
            }
        }
    }
}