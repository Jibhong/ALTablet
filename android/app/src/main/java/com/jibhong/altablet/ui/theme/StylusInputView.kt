package com.jibhong.altablet.ui.theme

import android.content.Context
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.nio.ByteBuffer

class StylusInputView(context: Context) : View(context) {
    // Set background color to Transparent
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
//        Lower input latency
        requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_POINTER)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            sendSample(event.x, event.y, event.pressure, isHovering = false)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            when (event.action) {
                MotionEvent.ACTION_HOVER_MOVE -> sendSample(event.x, event.y, 0f, isHovering = true)
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

    // Lock-free conflated channel: keeps only the LATEST sample.
    // If the network or PC lags, older packets are instantly dropped, eliminating latency spikes.
    private val sendChannel = Channel<ByteArray>(capacity = Channel.CONFLATED)

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
                        client.trafficClass = 0x10 // IPTOS_LOWDELAY

                        // Write directly to the socket stream to bypass intermediate buffers
                        outStream = client.getOutputStream()

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

        val buffer = ByteBuffer.allocate(10)
        buffer.put(1.toByte()) // Type 1: Data
        buffer.putShort(x)
        buffer.putShort(y)
        buffer.putFloat(pressure)
        buffer.put(if (isHovering) 1.toByte() else 0.toByte())

        sendChannel.trySend(buffer.array()) // Non-blocking — never stalls the UI thread
    }
}