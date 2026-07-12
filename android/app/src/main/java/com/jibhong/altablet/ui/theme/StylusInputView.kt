package com.jibhong.altablet.ui.theme

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.ServerSocket

class StylusInputView(context: Context) : View(context) {
    init {
        // Set background color to Black
        setBackgroundColor(android.graphics.Color.BLACK)
    }

    // 1. Detect Pen Touching Screen (Down/Move/Up)
    // Extracts ALL batched historical samples for maximum polling rate
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            // Process ALL batched historical samples first (the key to 240Hz+)
            // Android batches 2-4 intermediate samples between display frames
            for (i in 0 until event.historySize) {
                sendSample(
                    event.getHistoricalX(i),
                    event.getHistoricalY(i),
                    event.getHistoricalPressure(i),
                    isHovering = false
                )
            }
            // Then process the current (most recent) sample
            sendSample(event.x, event.y, event.pressure, isHovering = false)
            return true
        }
        return super.onTouchEvent(event)
    }

    // 2. Detect Pen Hovering (Movement without contact)
    // Also extracts historical samples for hover events
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            when (event.action) {
                MotionEvent.ACTION_HOVER_MOVE -> {
                    for (i in 0 until event.historySize) {
                        sendSample(
                            event.getHistoricalX(i),
                            event.getHistoricalY(i),
                            0f,
                            isHovering = true
                        )
                    }
                    sendSample(event.x, event.y, 0f, isHovering = true)
                }
                MotionEvent.ACTION_HOVER_ENTER -> Log.d("Stylus", "Hover Entered")
                MotionEvent.ACTION_HOVER_EXIT -> Log.d("Stylus", "Hover Exited")
            }
            return true
        }
        return super.onHoverEvent(event)
    }

    private var outStream: OutputStream? = null
    private var serverSocket: ServerSocket? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    // Lock-free channel for sending data — avoids per-event coroutine overhead
    private val sendChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)

    fun startServer() {
        // Start the single dedicated writer coroutine
        startSender()

        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    serverSocket?.close()
                    serverSocket = ServerSocket(6789)

                    while (true) {
                        Log.d("USB_COMM", "Waiting for connection...")
                        val client = serverSocket!!.accept()
                        Log.d("USB_COMM", "Client connected!")

                        // Disable Nagle's algorithm — send packets immediately
                        client.tcpNoDelay = true

                        // Use BufferedOutputStream for efficient writes
                        outStream = BufferedOutputStream(client.getOutputStream(), 4096)

                        try {
                            while (true) {
                                // If startSender gets a broken pipe, it sets outStream to null.
                                // We must throw here to break the loop and go back to accept().
                                if (outStream == null) {
                                    throw Exception("Stream was closed by sender")
                                }
                                // Test connection every second
                                outStream?.write(0)  // harmless keepalive
                                outStream?.flush()
                                Thread.sleep(1000)
                            }
                        } catch (e: Exception) {
                            Log.e("USB_COMM", "Client disconnected, waiting for new client...")
                            outStream = null
                            client.close()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("USB_COMM", "Server crashed, restarting in 1s", e)
                    Thread.sleep(1000)
                }
            }
        }
    }

    // Single dedicated writer — drains the channel and writes to socket
    private fun startSender() {
        scope.launch(Dispatchers.IO) {
            for (data in sendChannel) {
                try {
                    outStream?.let {
                        it.write(data)
                        it.flush()
                    }
                } catch (e: Exception) {
                    Log.e("USB_COMM", "Send failed", e)
                    outStream = null
                }
            }
        }
    }

    // Called from UI thread — non-blocking enqueue to channel
    private fun sendSample(rawX: Float, rawY: Float, pressure: Float, isHovering: Boolean) {
        val normX = (rawX / width).coerceIn(0f, 1f)
        val normY = (rawY / height).coerceIn(0f, 1f)

        val x = (normX * Short.MAX_VALUE).toInt().toShort()
        val y = (normY * Short.MAX_VALUE).toInt().toShort()

        // Advanced S Pen data (available for future use)
        // val tilt = event.getAxisValue(MotionEvent.AXIS_TILT)
        // val orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION)

        val hoverInt = if (isHovering) 1 else 0
        val data = "$x,$y,$pressure,$hoverInt\n".toByteArray()
        sendChannel.trySend(data) // Non-blocking — never stalls the UI thread
    }
}