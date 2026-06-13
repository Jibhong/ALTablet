package com.jibhong.altablet.fragment

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jibhong.altablet.StylusInputView
import kotlin.math.roundToInt

class TabletFragment {
    setContent {
        MaterialTheme {
            // Parent container to allow the box to sit anywhere
            Box(modifier = Modifier.fillMaxSize()) {
                ResizableBox()

            }
        }
    }
}

@Composable
fun ResizableBox() {
    var width by remember { mutableStateOf(200.dp) }
    var height by remember { mutableStateOf(200.dp) }

    // Optional: add position state so you can move it away from the screen edge
    var offsetX by remember { mutableStateOf(50f) }
    var offsetY by remember { mutableStateOf(50f) }

    val density = LocalDensity.current

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(width, height)
            .background(Color.LightGray)
            .border(2.dp, Color.Black)
    ) {


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
}