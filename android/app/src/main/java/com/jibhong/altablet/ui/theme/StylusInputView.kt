package com.jibhong.altablet.ui.theme

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.ServerSocket

class StylusInputView(context: Context) : View(context) {

    // 1. Detect Pen Touching Screen (Down/Move/Up)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            handleInput(event, isHovering = false)
            return true
        }
        return super.onTouchEvent(event)
    }

    // 2. Detect Pen Hovering (Movement without contact)
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            when (event.action) {
                MotionEvent.ACTION_HOVER_MOVE -> handleInput(event, isHovering = true)
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

    fun startServer() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket(6789)

                while (true) {
                    Log.d("USB_COMM", "Waiting for connection...")
                    val client = serverSocket!!.accept()
                    Log.d("USB_COMM", "Client connected!")

                    outStream = client.getOutputStream()

                    try {
                        while (true) {
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
                Log.e("USB_COMM", "Server crashed", e)
            }
        }
    }
    private fun handleInput(event: MotionEvent, isHovering: Boolean) {
        val normX = (event.x / width).coerceIn(0f, 1f)
        val normY = (event.y / height).coerceIn(0f, 1f)

        val x = (normX * Short.MAX_VALUE).toInt().toShort()
        val y = (normY * Short.MAX_VALUE).toInt().toShort()
        val pressure = if (isHovering) 0f else event.pressure

        // Advanced S Pen data
        val tilt = event.getAxisValue(MotionEvent.AXIS_TILT)
        val orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION)

        // Log or send this data to your virtual tablet receiver
//        Log.d("StylusInput", "Pos: ($x, $y) | Pressure: $pressure | Hover: $isHovering")

        // Use invalidate() if you want to draw a cursor or ink on screen
        invalidate()


        val data = "$x,$y,$pressure,$isHovering\n"
        Log.d("STYLUS_OUT", "Sending: $data")
        // Send data over USB tunnel
        scope.launch(Dispatchers.IO) {
            try {
                outStream?.let {
                    it.write(data.toByteArray())
                    it.flush() // <--- CRITICAL: Force the data out immediately
                }
            } catch (e: Exception) {
                Log.e("USB_COMM", "Send failed", e)
                // if send fail reconnect
                outStream = null
                startServer()
            }
        }
    }
}