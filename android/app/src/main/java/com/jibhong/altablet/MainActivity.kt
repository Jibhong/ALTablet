package com.jibhong.altablet

import com.jibhong.altablet.ui.theme.StylusInputView
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import coil.compose.AsyncImage
import kotlin.math.roundToInt

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
                    MainComponent()

                }
            }
        }
    }
}

@Composable
fun MainComponent() {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("TabletPrefs", Context.MODE_PRIVATE)

    var width by remember { mutableStateOf(sharedPref.getFloat("width", 200f).dp) }
    var height by remember { mutableStateOf(sharedPref.getFloat("height", 200f).dp) }

    // Optional: add position state so you can move it away from the screen edge
    var offsetX by remember { mutableStateOf(sharedPref.getFloat("offsetX", 50f)) }
    var offsetY by remember { mutableStateOf(sharedPref.getFloat("offsetY", 50f)) }

    var isLocked by remember { mutableStateOf(sharedPref.getBoolean("isLocked", false)) }
    var showSideBar by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf(sharedPref.getFloat("aspectRatio", 16f / 9f)) }
    var imageUriString by remember { mutableStateOf(sharedPref.getString("imageUri", null)) }

    var ratioWidthText by remember { mutableStateOf("") }
    var ratioHeightText by remember { mutableStateOf("") }
    var floatRatioText by remember { mutableStateOf(aspectRatio.toString()) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val uriStr = uri.toString()
                imageUriString = uriStr
                sharedPref.edit().putString("imageUri", uriStr).apply()
            }
        }

    val density = LocalDensity.current

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    BackHandler(enabled = showSideBar) {
        showSideBar = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background Image Layer - Isolated from interactive layer
        if (imageUriString != null) {
            AsyncImage(
                model = imageUriString,
                contentDescription = "Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer() // Offloads to a separate hardware layer
            )
        }

        // Interactive Digitizer Layer
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(width, height)
                .then(
                    if (!isLocked || showSideBar) Modifier.border(
                        2.dp,
                        Color.White
                    ) else Modifier
                )
                .graphicsLayer() // Isolates this moving box into its own layer
        ) {

            if (!isLocked) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(0.dp.roundToPx(), -24.dp.roundToPx())
                        }
                        .size(width, 24.dp) // Large touch target
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
                        .size(48.dp, 24.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Green)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                offsetX = 0f
                                // Correct way to get pixel value from Dp
                                val screenHeightPx = with(density) { screenHeight.toPx() }
                                val boxHeightPx =
                                    with(density) { (screenWidth / aspectRatio).toPx() }

                                // Center it: (ScreenHeight / 2) - (BoxHeight / 2)
                                offsetY = (screenHeightPx - boxHeightPx) / 2

                                width = screenWidth
                                height = screenWidth / aspectRatio
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
                                    height = width / aspectRatio
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

        // Click-outside overlay: hides sidebar when tapping outside the panel
        if (showSideBar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { showSideBar = false }
                    }
            )
        }

        // Settings Button and Side Bar
        IconButton(
            onClick = { showSideBar = !showSideBar },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            if (!isLocked && !showSideBar) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = showSideBar,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .graphicsLayer() // Helps with smooth animation
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(16.dp)
                    .pointerInput(Unit) {} // Consume clicks so they don't hit the background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        IconButton(
                            onClick = { showSideBar = false }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Settings",
                                tint = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            sharedPref.edit()
                                .putFloat("width", width.value)
                                .putFloat("height", height.value)
                                .putFloat("offsetX", offsetX)
                                .putFloat("offsetY", offsetY)
                                .putBoolean("isLocked", isLocked)
                                .putFloat("aspectRatio", aspectRatio)
                                .apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            width = sharedPref.getFloat("width", 200f).dp
                            height = sharedPref.getFloat("height", 200f).dp
                            offsetX = sharedPref.getFloat("offsetX", 50f)
                            offsetY = sharedPref.getFloat("offsetY", 50f)
                            aspectRatio = sharedPref.getFloat("aspectRatio", 16f / 9f)
                            floatRatioText = aspectRatio.toString()
                            ratioWidthText = ""
                            ratioHeightText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            isLocked = !isLocked
                            sharedPref.edit().putBoolean("isLocked", isLocked).apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isLocked) "Unlock Controls" else "Lock & Hide Controls")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            launcher.launch(arrayOf("image/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Background Image")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            imageUriString = null
                            sharedPref.edit().remove("imageUri").apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove Background Image")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Aspect Ratio", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("W:H Mode", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = ratioWidthText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }
                                val value = filtered.toIntOrNull()
                                if (value != null) {
                                    val coerced = value.coerceIn(1, 100)
                                    ratioWidthText = coerced.toString()
                                    ratioHeightText.toFloatOrNull()?.let { h ->
                                        if (h != 0f) floatRatioText =
                                            (coerced.toFloat() / h).toString()
                                    }
                                } else {
                                    ratioWidthText = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Text(" : ", modifier = Modifier.padding(horizontal = 4.dp))
                        OutlinedTextField(
                            value = ratioHeightText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }
                                val value = filtered.toIntOrNull()
                                if (value != null) {
                                    val coerced = value.coerceIn(1, 100)
                                    ratioHeightText = coerced.toString()
                                    ratioWidthText.toFloatOrNull()?.let { w ->
                                        if (coerced != 0) floatRatioText =
                                            (w / coerced.toFloat()).toString()
                                    }
                                } else {
                                    ratioHeightText = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Float Mode (0.1 - 10.0)", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = floatRatioText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            floatRatioText = filtered
                            filtered.toFloatOrNull()?.let { f ->
                                ratioWidthText = f.toString()
                                ratioHeightText = "1"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            floatRatioText.toFloatOrNull()?.let { r ->
                                val finalRatio = r.coerceIn(0.1f, 10.0f)
                                aspectRatio = finalRatio
                                height = width / aspectRatio

                                floatRatioText = finalRatio.toString()
                                ratioWidthText = finalRatio.toString()
                                ratioHeightText = "1"
                            }
                        },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.End)
                    ) {
                        Text("Apply")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Button(
                        onClick = {
                            width = 200f.dp
                            height = 200f.dp
                            offsetX = 50f
                            offsetY = 50f
                            aspectRatio = 16f / 9f
                            floatRatioText = aspectRatio.toString()
                            ratioWidthText = ""
                            ratioHeightText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}