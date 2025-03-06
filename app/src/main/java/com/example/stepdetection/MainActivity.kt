package com.example.stepdetection

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

class MainActivity : ComponentActivity(), SensorEventListener {

    // Sensor Manager
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Data collection
    private val accelerometerData = mutableListOf<AccelerometerData>()
    private val windowSize = 15  // For dynamic threshold calculation
    private val rollingWindow = ArrayDeque<Double>(windowSize * 2)

    // Recording timing
    private var recordingStartTime: Double = 0.0
    private var lastSavedTimestamp = 0.0
    private val SAMPLING_INTERVAL = 0.1 // in seconds

    // Delayed processing variables (deliberate lag for better accuracy)
    private val FUTURE_WINDOW = 1 // 0.6 seconds of future data
    private val dataBuffer = mutableListOf<BufferedAccelData>() // Store raw data for delayed processing

    // Visualization data
    private val smoothedAccelerationData = mutableListOf<Pair<Double, Double>>() // Timestamp, SmoothedAccel
    private val stepDetectionData = mutableListOf<Pair<Double, Double>>() // Timestamp, SmoothedAccel

    // CSV Export
    private var isRecording = false
    private var lastSavedFile: File? = null

    // Step Detection Parameters
    private val k = 0.26  // Kim's step length coefficient
    private val minStepInterval = 0.4  // Minimum time interval between steps in seconds
    private val minStepThreshold = 7.5  // Minimum peak value to be considered a step
    private val smoothingFactor = 0.1   // Exponential smoothing factor

    // Step tracking
    private var lastStepTime: Double? = null
    private val stepTimes = mutableListOf<Double>()
    private val stepLengths = mutableListOf<Double>()
    private val velocities = mutableListOf<Double>()
    private var lastSmoothedValue = 0.0
    private var lastProcessedTimestamp = 0.0

    // UI State
    private val _stepCount = mutableStateOf(0)
    private val _stepLength = mutableStateOf(0.0)
    private val _velocity = mutableStateOf(0.0)
    private val _alertMessage = mutableStateOf("Status: Ready")
    private val _showSnackbar = mutableStateOf(false)
    private val _snackbarMessage = mutableStateOf("")
    private val _showShareDialog = mutableStateOf(false)
    private val _showVisualization = mutableStateOf(false)


    // Inside your MainActivity class
    private lateinit var vibrator: Vibrator
    private lateinit var toneGenerator: ToneGenerator
    private var lastAlertTime: Long = 0
    private val ALERT_COOLDOWN_MS = 1500 // Minimum time between alerts in milliseconds
    // Add near other state variables
    private val _stepHistory = mutableStateOf(listOf<String>())

    // Permission handling
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            showSnackbar("Storage permission is required to save data")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Inside your onCreate method in MainActivity
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

        // Check for accelerometer
        if (accelerometer == null) {
            showSnackbar("No accelerometer detected on this device!")
        }

        setContent {
            StepDetectionTheme {
                StepDetectionApp(
                    stepCount = _stepCount.value,
                    stepLength = _stepLength.value,
                    velocity = _velocity.value,
                    alertMessage = _alertMessage.value,
                    showSnackbar = _showSnackbar.value,
                    snackbarMessage = _snackbarMessage.value,
                    showShareDialog = _showShareDialog.value,
                    showVisualization = _showVisualization.value,
                    onSnackbarDismiss = { _showSnackbar.value = false },
                    stepHistory = _stepHistory.value,
                    onStartRecording = {
                        if (checkPermission()) {
                            startRecording()
                        } else {
                            requestPermission()
                        }
                    },
                    onStopRecording = {
                        stopRecording()
                    },
                    onReset = { resetTracking() },
                    onDismissShareDialog = { _showShareDialog.value = false },
                    onShareFile = { shareFileViaEmail() },
                    onToggleVisualization = { _showVisualization.value = !_showVisualization.value },
                    accelerometerData = accelerometerData,
                    smoothedData = smoothedAccelerationData,
                    stepData = stepDetectionData
                )
            }
        }
    }

    private fun showSnackbar(message: String) {
        _snackbarMessage.value = message
        _showSnackbar.value = true
    }



    // With these two methods:
    private fun provideFeedbackForSlow() {
        // Check if enough time has passed since last alert
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN_MS) return
        lastAlertTime = currentTime

        // Play a descending tone for slow walking
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_NETWORK_BUSY, 400)

        // Vibrate with a slow, long pattern
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createWaveform(
                longArrayOf(0, 500, 200, 500),
                -1
            )
            vibrator.vibrate(vibrationEffect)
        } else {
            vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
        }
    }

    private fun provideFeedbackForFast() {
        // Check if enough time has passed since last alert
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN_MS) return
        lastAlertTime = currentTime

        // Play a distinctive urgency pattern for fast walking
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)

        // Vibrate with a quick pattern
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createWaveform(
                longArrayOf(0, 100, 100, 100, 100, 100),
                -1
            )
            vibrator.vibrate(vibrationEffect)
        } else {
            vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
        }
    }
    // Inside your MainActivity class
    override fun onDestroy() {
        super.onDestroy()
        toneGenerator.release()
    }
    // Inside your MainActivity class
    private fun analyzeStepLengthImproved(currentStepLength: Double): String {
        // Need at least 8 steps for reliable analysis
        if (stepLengths.size < 8) {
            return "Building baseline (${stepLengths.size}/8 steps)..."
        }

        // Get the most recent 8 steps
        val recentStepLengths = stepLengths.takeLast(8)

        // Calculate mean and standard deviation
        val mean = recentStepLengths.average()
        val variance = recentStepLengths.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        // Calculate Z-score
        val zScore = if (stdDev > 0) {
            (currentStepLength - mean) / stdDev
        } else {
            0.0
        }

        // Log the details for debugging
        Log.d("GaitAnalysis", "Step length: $currentStepLength, Mean: $mean, " +
                "StdDev: $stdDev, Z-score: $zScore")

        // Determine if step is too fast or too slow using Z-score
        val message = "Step ${stepLengths.size}: Length = ${String.format("%.2f", currentStepLength)}m"

        return when {
            zScore < -1.5 -> {
                // Too slow - provide strong feedback
                provideFeedbackForSlow()
                "$message (ALERT: Walking too slow!)"
            }
            zScore > 1.5 -> {
                // Too fast - provide strong feedback
                provideFeedbackForFast()
                "$message (ALERT: Walking too fast!)"
            }
            else -> "$message (Within normal range)"
        }
    }


    private fun startRecording() {
        isRecording = true
        resetTracking()
        recordingStartTime = System.currentTimeMillis() / 1000.0  // Current time in seconds
        _alertMessage.value = "Recording started..."
    }

    private fun stopRecording() {
        if (isRecording) {
            isRecording = false
            // Process any remaining data in the buffer
            processBufferedData(true)
            lifecycleScope.launch {
                saveDataToCsv()
            }
        }
    }

    private fun resetTracking() {
        _stepCount.value = 0
        _stepLength.value = 0.0
        _velocity.value = 0.0
        _alertMessage.value = "Status: Ready"

        lastStepTime = null
        stepTimes.clear()
        stepLengths.clear()
        velocities.clear()
        accelerometerData.clear()
        rollingWindow.clear()
        smoothedAccelerationData.clear()
        stepDetectionData.clear()
        dataBuffer.clear()
        lastSavedTimestamp = 0.0
        lastSmoothedValue = 0.0
        lastProcessedTimestamp = 0.0
        _stepHistory.value = emptyList()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (isRecording) {
            stopRecording()
        }
    }

    private suspend fun saveDataToCsv() = withContext(Dispatchers.IO) {
        try {
            // Create a timestamp for the filename with date and time
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = sdf.format(Date())
            val filename = "step_data_${timestamp}.csv"

            // Use app's internal storage - no permission required on any Android version
            val file = File(filesDir, filename)

            FileOutputStream(file).use { fos ->
                fos.write("Timestamp,X,Y,Z\n".toByteArray())
                for (data in accelerometerData) {
                    fos.write("${data.timestamp},${data.x},${data.y},${data.z}\n".toByteArray())
                }
            }

            lastSavedFile = file

            withContext(Dispatchers.Main) {
                showSnackbar("Data saved as: $filename")
                // Show dialog to offer sharing
                _showShareDialog.value = true
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                showSnackbar("Error saving data: ${e.message}")
            }
        }
    }

    private fun shareFileViaEmail() {
        lastSavedFile?.let { originalFile ->
            try {
                // Create a copy in a location that's definitely shared by FileProvider
                val sharedFileName = "step_data_${System.currentTimeMillis()}.csv"
                val sharedFile = File(cacheDir, sharedFileName)

                // Copy the file content
                originalFile.inputStream().use { input ->
                    sharedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Get URI using FileProvider with the new location
                val uri = FileProvider.getUriForFile(
                    this,
                    "com.example.stepdetection.fileprovider",
                    sharedFile
                )

                // Create email intent
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Step Detection Data")
                    putExtra(Intent.EXTRA_TEXT, "Here is my step detection data.")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                try {
                    startActivity(Intent.createChooser(intent, "Send email using..."))
                } catch (e: ActivityNotFoundException) {
                    showSnackbar("No email clients installed.")
                }
            } catch (e: Exception) {
                showSnackbar("Error sharing file: ${e.message}")
            }
        } ?: showSnackbar("No file to share")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val timestamp = System.currentTimeMillis() / 1000.0
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Add to buffer and process with delay for better accuracy
            bufferAccelerometerData(timestamp, x, y, z)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation, but must be implemented as part of SensorEventListener
    }

    // Data structure for buffered acceleration data
    data class BufferedAccelData(
        val timestamp: Double,
        val x: Float,
        val y: Float,
        val z: Float,
        val magnitude: Double,
        val smoothedMagnitude: Double
    )

    // Buffer accelerometer data for delayed processing with future window
    private fun bufferAccelerometerData(timestamp: Double, x: Float, y: Float, z: Float) {
        if (!isRecording) return

        // Calculate relative timestamp (time since recording started)
        val relativeTimestamp = timestamp - recordingStartTime

        // Only store data at regular intervals
        if (lastSavedTimestamp == 0.0 || relativeTimestamp - lastSavedTimestamp >= SAMPLING_INTERVAL) {
            // Round the timestamp to 1 decimal place (0.1s precision)
            val roundedTimestamp = (Math.round(relativeTimestamp * 10) / 10.0)
            lastSavedTimestamp = relativeTimestamp

            // Calculate acceleration magnitude
            val accelMagnitude = sqrt(x.pow(2) + y.pow(2) + z.pow(2).toDouble())

            // Apply exponential smoothing
            val smoothedAccel = if (lastSmoothedValue == 0.0) {
                accelMagnitude
            } else {
                smoothingFactor * accelMagnitude + (1 - smoothingFactor) * lastSmoothedValue
            }
            lastSmoothedValue = smoothedAccel

            // Add to buffer
            dataBuffer.add(BufferedAccelData(
                roundedTimestamp, x, y, z, accelMagnitude, smoothedAccel
            ))

            // Process buffer when we have enough future data
            processBufferedData(false)
        }
    }

    // Process buffered data with look-ahead window for improved accuracy
    private fun processBufferedData(processAll: Boolean) {
        if (dataBuffer.isEmpty()) return

        val currentTime = dataBuffer.last().timestamp
        val processingCutoff = if (processAll) Double.MAX_VALUE else currentTime - FUTURE_WINDOW

        // Process all data points up to the cutoff time
        while (dataBuffer.isNotEmpty() && dataBuffer.first().timestamp <= processingCutoff) {
            val data = dataBuffer.removeAt(0)

            // Store raw data for CSV export
            accelerometerData.add(AccelerometerData(data.timestamp, data.x, data.y, data.z))

            // Store smoothed data for visualization
            smoothedAccelerationData.add(Pair(data.timestamp, data.smoothedMagnitude))

            // Add to rolling window for threshold calculation
            rollingWindow.add(data.smoothedMagnitude)
            if (rollingWindow.size > windowSize * 2) {
                rollingWindow.removeFirst()
            }

            // Update last processed timestamp
            lastProcessedTimestamp = data.timestamp

            // Only run step detection if we have enough data
            if (smoothedAccelerationData.size >= 5) {
                detectStep(data.timestamp, data.smoothedMagnitude)
            }
        }
    }

    // Calculate dynamic threshold using rolling window
    private fun calculateDynamicThreshold(): Double {
        if (rollingWindow.size < windowSize) {
            return minStepThreshold
        }

        // Calculate mean and standard deviation from rolling window
        val mean = rollingWindow.average()
        val variance = rollingWindow.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        // Match Python implementation: mean + std * 0.5
        return mean + stdDev * 0.5
    }

    // Step detection with future window
    private fun detectStep(timestamp: Double, smoothedAccel: Double) {
        // Get current index in smoothed data
        val currentIdx = smoothedAccelerationData.indexOfFirst { it.first == timestamp }
        if (currentIdx < 2) return // Need at least 2 prior points

        // Calculate dynamic threshold
        val dynamicThreshold = calculateDynamicThreshold()

        // Debug info
        Log.d("StepDetection", "Time: $timestamp, Smoothed: $smoothedAccel, Threshold: $dynamicThreshold")

        // Check if this point exceeds thresholds
        if (smoothedAccel > dynamicThreshold && smoothedAccel > minStepThreshold) {
            // Define look-ahead and look-behind windows
            val lookBehindIdx = maxOf(0, currentIdx - 2)
            val lookAheadIdx = minOf(smoothedAccelerationData.size - 1, currentIdx + 2)

            // Check if current point is higher than neighbors (both past and future)
            var isPeak = true

            // Check past 2 points
            for (i in lookBehindIdx until currentIdx) {
                if (smoothedAccel <= smoothedAccelerationData[i].second) {
                    isPeak = false
                    break
                }
            }

            // Check future 2 points (within our future window)
            if (isPeak) {
                for (i in (currentIdx + 1)..lookAheadIdx) {
                    if (i < smoothedAccelerationData.size &&
                        smoothedAccel <= smoothedAccelerationData[i].second) {
                        isPeak = false
                        break
                    }
                }
            }

            // If it's a peak, we need to look for potentially higher peaks in our future window
            if (isPeak) {
                var maxPeak = smoothedAccel
                var maxPeakTimestamp = timestamp
                var maxPeakIdx = currentIdx

                // Look for higher peaks within a small future window
                val peakSearchEndIdx = minOf(smoothedAccelerationData.size - 1,
                    lookAheadIdx + 3) // Look a bit further ahead

                for (i in currentIdx + 1..peakSearchEndIdx) {
                    val checkAccel = smoothedAccelerationData[i].second
                    val checkTime = smoothedAccelerationData[i].first

                    // If we find a higher peak, update
                    if (checkAccel > maxPeak) {
                        maxPeak = checkAccel
                        maxPeakTimestamp = checkTime
                        maxPeakIdx = i
                    }
                }

                // Check minimum time since last step
                if (lastStepTime == null || (maxPeakTimestamp - lastStepTime!!) >= minStepInterval) {
                    // We have a confirmed step
                    stepTimes.add(maxPeakTimestamp)

                    if (lastStepTime != null) {
                        val stepInterval = maxPeakTimestamp - lastStepTime!!

                        // Get accelerometer data around this step
                        val startIdx = maxOf(0, maxPeakIdx - 5)
                        val endIdx = minOf(accelerometerData.size - 1, maxPeakIdx + 5)

                        // Calculate step length
                        val stepAccelerations = mutableListOf<Double>()
                        for (i in startIdx..endIdx) {
                            if (i < accelerometerData.size) {
                                val accelData = accelerometerData[i]
                                val magnitude = sqrt(accelData.x.pow(2) + accelData.y.pow(2) +
                                        accelData.z.pow(2).toDouble())
                                stepAccelerations.add(magnitude)
                            }
                        }

                        // Get mean absolute acceleration
                        val meanAbsAcceleration = stepAccelerations.map { abs(it) }.average()

                        // Apply Kim's Step Length Formula
                        val stepLength = k * Math.cbrt(meanAbsAcceleration)
                        stepLengths.add(stepLength)

                        // Compute velocity
                        val velocity = stepLength / stepInterval
                        velocities.add(velocity)

                        // Update UI values
                        _stepCount.value += 1
                        _stepLength.value = stepLength
                        _velocity.value = velocity

                        // Update UI with step information
                        if (stepLengths.size >= 8) {
                            // Monitor steps for gait analysis with improved method
                            val alertMessage = analyzeStepLengthImproved(stepLength)
                            _alertMessage.value = alertMessage

                            // ADD THIS NEW CODE BLOCK RIGHT HERE ⬇️
                            val currentHistory = _stepHistory.value.toMutableList()
                            currentHistory.add(alertMessage)

                            // Limit history to last 20 steps
                            if (currentHistory.size > 20) {
                                currentHistory.removeAt(0)
                            }

                            _stepHistory.value = currentHistory
                        } else {
                            // Simple message for initial steps
                            _alertMessage.value = "Step ${_stepCount.value}: Length = ${String.format("%.2f", stepLength)}m"

                            // ADD THE SAME CODE BLOCK HERE FOR INITIAL STEPS ⬇️
                            val currentHistory = _stepHistory.value.toMutableList()
                            currentHistory.add(_alertMessage.value)

                            // Limit history to last 20 steps
                            if (currentHistory.size > 20) {
                                currentHistory.removeAt(0)
                            }

                            _stepHistory.value = currentHistory
                        }
                    } else {
                        // First step detected
                        _alertMessage.value = "First step detected!"

                        // ADD THE SAME CODE BLOCK HERE FOR FIRST STEP ⬇️
                        val currentHistory = _stepHistory.value.toMutableList()
                        currentHistory.add(_alertMessage.value)

                        // Limit history to last 20 steps
                        if (currentHistory.size > 20) {
                            currentHistory.removeAt(0)
                        }

                        _stepHistory.value = currentHistory
                    }
                    lastStepTime = maxPeakTimestamp

                    // Store step for visualization
                    stepDetectionData.add(Pair(maxPeakTimestamp, maxPeak))

                    // Debug log
                    Log.d("StepDetection", "Step detected at time: $maxPeakTimestamp, " +
                            "accel: $maxPeak, threshold: $dynamicThreshold")
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        // On Android 10+ (Q), we can use app-specific storage without needing permission
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        // On Android 10+ (Q), we don't need to request permission for app-specific storage
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            // No permission needed on newer Android versions for app-specific storage
            startRecording()
        }
    }
}

@Composable
fun StepDetectionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}

@Composable
fun StepDetectionApp(
    stepCount: Int,
    stepLength: Double,
    velocity: Double,
    alertMessage: String,
    stepHistory: List<String>,
    showSnackbar: Boolean,
    snackbarMessage: String,
    showShareDialog: Boolean,
    showVisualization: Boolean,
    onSnackbarDismiss: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onReset: () -> Unit,
    onDismissShareDialog: () -> Unit,
    onShareFile: () -> Unit,
    onToggleVisualization: () -> Unit,
    accelerometerData: List<AccelerometerData>,
    smoothedData: List<Pair<Double, Double>>,
    stepData: List<Pair<Double, Double>>
) {
    // Share Dialog
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = onDismissShareDialog,
            title = { Text("Share Recorded Data") },
            text = { Text("Would you like to share the saved data file via email?") },
            confirmButton = {
                Button(onClick = {
                    onShareFile()
                    onDismissShareDialog()
                }) {
                    Text("Share")
                }
            },
            dismissButton = {
                Button(onClick = onDismissShareDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = {
            if (showSnackbar) {
                Snackbar(
                    action = {
                        TextButton(onClick = onSnackbarDismiss) {
                            Text("Dismiss")
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(snackbarMessage)
                }
            }
        }
    ) { paddingValues ->
        if (showVisualization) {
            AccelerationChartScreen(
                accelerometerData = accelerometerData,
                smoothedData = smoothedData,
                stepData = stepData,
                onBackPressed = onToggleVisualization
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Step Detection",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Steps: $stepCount",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Step Length: ${String.format("%.2f", stepLength)} m",
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Velocity: ${String.format("%.2f", velocity)} m/s",
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = alertMessage,
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }

            // Step History Section
            Text(
                text = "Step History",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(8.dp),
                    reverseLayout = true // Most recent steps at the top
                ) {
                    items(stepHistory) { stepInfo ->
                        Text(
                            text = stepInfo,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onStartRecording,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                ) {
                    Text("Start")
                }

                Button(
                    onClick = onStopRecording,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                ) {
                    Text("Stop")
                }

                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B))
                ) {
                    Text("Reset")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleVisualization,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
            ) {
                Text(if (showVisualization) "Hide Visualization" else "Show Visualization")
            }
        }
    }
}




// Note: The AccelerationChartScreen composable is referenced but not included
// in the original code. If needed, add a placeholder implementation here.