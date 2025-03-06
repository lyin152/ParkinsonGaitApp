package com.example.stepdetection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
data class AccelerometerData(
    val timestamp: Double,
    val x: Float,
    val y: Float,
    val z: Float
)


@Composable
fun AccelerationChartScreen(
    accelerometerData: List<AccelerometerData>,
    smoothedData: List<Pair<Double, Double>>,
    stepData: List<Pair<Double, Double>>,
    onBackPressed: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Calculate step lengths using Kim's algorithm, matching the Python code
    val stepInfoList = calculateStepLengths(smoothedData, stepData)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Acceleration Data Visualization",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Go Back Button
            Button(
                onClick = onBackPressed,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Go Back to Main Screen")
            }

            // Original vs Smoothed Acceleration Chart
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Original vs Smoothed Acceleration",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Acceleration (m/s²) vs Time (s)",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ChartWithAxes(
                        accelerometerData = accelerometerData,
                        smoothedData = smoothedData,
                        stepData = stepData,
                        chartType = "comparison"
                    )

                    // Legend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LegendItem(color = Color(0xFF008080), text = "Raw Acceleration")
                        LegendItem(color = Color(0xFF0000FF), text = "Smoothed Acceleration")
                        LegendItem(color = Color(0xFFDC143C), text = "Detected Steps")
                    }
                }
            }

            // Step Detection Chart
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Step Detection",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Acceleration (m/s²) vs Time (s)",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ChartWithAxes(
                        accelerometerData = accelerometerData,
                        smoothedData = smoothedData,
                        stepData = stepData,
                        chartType = "stepDetection"
                    )

                    // Legend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LegendItem(color = Color(0xFF0000FF), text = "Smoothed Acceleration")
                        LegendItem(color = Color(0xFFDC143C), text = "Detected Steps")
                    }
                }
            }

            // Peak Times List
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Detected Peaks",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (stepData.isEmpty()) {
                        Text(
                            text = "No peaks detected yet",
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        // Table header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF9C27B0).copy(alpha = 0.1f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Peak #",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.2f)
                            )
                            Text(
                                text = "Time (s)",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = "Acceleration (m/s²)",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.4f),
                                textAlign = TextAlign.End
                            )
                        }

                        // Show peak data
                        Divider()

                        // Display peak data with timestamps
                        stepData.forEachIndexed { index, peak ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    modifier = Modifier.weight(0.2f)
                                )

                                // Format timestamp with 2 decimal places
                                val formattedTime = String.format("%.2f", peak.first)

                                Text(
                                    text = formattedTime,
                                    modifier = Modifier.weight(0.4f)
                                )

                                // Acceleration value
                                Text(
                                    text = String.format("%.2f", peak.second),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(0.4f),
                                    textAlign = TextAlign.End
                                )
                            }

                            if (index < stepData.size - 1) {
                                Divider(color = Color.LightGray, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            // Detected Steps List
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Detected Steps (Kim's Model)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (stepInfoList.isEmpty()) {
                        Text(
                            text = "No steps detected yet",
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        // Table header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF9C27B0).copy(alpha = 0.1f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Step #",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.2f)
                            )
                            Text(
                                text = "Time (s)",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.3f)
                            )
                            Text(
                                text = "Step Length (m)",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.End
                            )
                        }

                        // Show step data
                        Divider()

                        // Only display up to 20 steps to avoid performance issues
                        val displayedSteps = stepInfoList.takeLast(20)

                        displayedSteps.forEachIndexed { index, stepInfo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = (stepInfoList.size - displayedSteps.size + index + 1).toString(),
                                    modifier = Modifier.weight(0.2f)
                                )

                                // Format timestamp as seconds with 2 decimal places
                                val formattedTime = String.format("%.2f", stepInfo.timestamp)

                                Text(
                                    text = formattedTime,
                                    modifier = Modifier.weight(0.3f)
                                )

                                // Step length with color coding
                                val textColor = when {
                                    stepInfo.isTooSlow -> Color(0xFFE53935)
                                    stepInfo.isTooFast -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                }

                                Text(
                                    text = String.format("%.2f", stepInfo.stepLength),
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(0.5f),
                                    textAlign = TextAlign.End
                                )
                            }

                            if (index < displayedSteps.size - 1) {
                                Divider(color = Color.LightGray, thickness = 0.5.dp)
                            }
                        }

                        // Statistics
                        if (stepInfoList.size > 1) {
                            Divider(thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                            val stepLengths = stepInfoList.map { it.stepLength }
                            val meanStepLength = stepLengths.average()
                            val medianStepLength = stepLengths.sorted()[stepLengths.size / 2]
                            val rangeStepLength = stepLengths.maxOrNull()!! - stepLengths.minOrNull()!!

                            Text(
                                text = "Statistics:",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )

                            Text(
                                text = "Mean Step Length: ${String.format("%.4f", meanStepLength)} m",
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            Text(
                                text = "Median Step Length: ${String.format("%.4f", medianStepLength)} m",
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            Text(
                                text = "Step Length Range: ${String.format("%.4f", rangeStepLength)} m",
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Calculate step lengths using Kim's algorithm, matching the Python code exactly
fun calculateStepLengths(
    smoothedData: List<Pair<Double, Double>>,
    stepData: List<Pair<Double, Double>>
): List<StepInfo> {
    if (stepData.size <= 1) return emptyList()

    val k = 0.26 // Kim's step length coefficient
    val stepInfo = mutableListOf<StepInfo>()

    for (i in 1 until stepData.size) {
        val currentStepTime = stepData[i].first
        val previousStepTime = stepData[i-1].first
        val stepInterval = currentStepTime - previousStepTime

        // Find the relevant acceleration data around this step
        val stepTimeIndex = smoothedData.indexOfFirst { it.first >= currentStepTime }
            .takeIf { it >= 0 } ?: smoothedData.indexOfLast { it.first <= currentStepTime }

        if (stepTimeIndex >= 0) {
            // Get 10 data points around the step (5 before, 5 after) - similar to Python code
            val startIndex = maxOf(0, stepTimeIndex - 5)
            val endIndex = minOf(smoothedData.size - 1, stepTimeIndex + 5)
            val accelerations = smoothedData.subList(startIndex, endIndex + 1).map { it.second }

            // Calculate mean absolute acceleration
            val meanAbsAcceleration = accelerations.map { kotlin.math.abs(it) }.average()

            // Apply Kim's Step Length Formula: K * mean_abs_acceleration^(1/3)
            // Using Math.cbrt for cube root instead of pow(x, 1/3)
            val stepLength = k * Math.cbrt(meanAbsAcceleration)

            // Compute velocity
            val velocity = stepLength / stepInterval

            // Determine if the step is too slow or too fast
            var isTooSlow = false
            var isTooFast = false

            if (stepInfo.size >= 7) { // Equivalent to having 8 steps (current + 7 previous)
                val last8Steps = (stepInfo.takeLast(7) + StepInfo(currentStepTime, stepLength, velocity))
                val stepLengths = last8Steps.map { it.stepLength }
                val meanStepLength = stepLengths.average()
                val maxStepLength = stepLengths.maxOrNull() ?: 0.0
                val minStepLength = stepLengths.minOrNull() ?: 0.0
                val rangeStepLength = maxStepLength - minStepLength

                val lowerBound = meanStepLength - rangeStepLength * 0.5
                val upperBound = meanStepLength + rangeStepLength * 0.5

                isTooSlow = stepLength < lowerBound
                isTooFast = stepLength > upperBound
            }

            stepInfo.add(StepInfo(currentStepTime, stepLength, velocity, isTooSlow, isTooFast))
        }
    }

    return stepInfo
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color)
        )
        Text(text = text, fontSize = 12.sp)
    }
}

// A simpler chart component that doesn't use experimental APIs
@Composable
fun ChartWithAxes(
    accelerometerData: List<AccelerometerData>,
    smoothedData: List<Pair<Double, Double>>,
    stepData: List<Pair<Double, Double>>,
    chartType: String // "comparison" or "stepDetection"
) {
    if ((chartType == "comparison" && (accelerometerData.isEmpty() || smoothedData.isEmpty())) ||
        (chartType == "stepDetection" && smoothedData.isEmpty())) {
        Text(
            text = "No data available",
            modifier = Modifier.padding(vertical = 32.dp)
        )
        return
    }

    // Prepare data
    val timestamps = smoothedData.map { it.first }
    val minTime = timestamps.minOrNull() ?: 0.0
    val maxTime = timestamps.maxOrNull() ?: 1.0
    val timeRange = maxTime - minTime

    // Calculate raw magnitudes if needed
    val rawMagnitudes = if (chartType == "comparison") {
        accelerometerData.map { data ->
            Pair(data.timestamp, calculateMagnitude(data.x, data.y, data.z))
        }
    } else emptyList()

    // Determine y-axis scale
    val allValues = if (chartType == "comparison") {
        rawMagnitudes.map { it.second } + smoothedData.map { it.second }
    } else {
        smoothedData.map { it.second }
    }

    val maxAccel = allValues.maxOrNull() ?: 10.0
    val roundedMaxAccel = (Math.ceil(maxAccel / 5) * 5).toInt().coerceAtLeast(10) // At least 10 for scale

    // Define axis labels
    val yAxisValues = listOf(0, roundedMaxAccel / 4, roundedMaxAccel / 2, 3 * roundedMaxAccel / 4, roundedMaxAccel)
    val xAxisValues = List(5) { i -> minTime + (timeRange * i / 4) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        // Chart area
        Box(
            modifier = Modifier
                .padding(start = 40.dp, end = 10.dp, bottom = 30.dp)
                .fillMaxSize()
        ) {
            // The main chart canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F8F8))
            ) {
                val chartWidth = size.width
                val chartHeight = size.height

                // Draw grid lines
                drawGridLines(chartWidth, chartHeight, 5, 5)

                if (chartType == "comparison") {
                    // Draw raw acceleration line
                    if (rawMagnitudes.isNotEmpty()) {
                        val rawPath = Path()
                        val firstPoint = rawMagnitudes.first()
                        val normalizedX = ((firstPoint.first - minTime) / timeRange) * chartWidth
                        val normalizedY = chartHeight - ((firstPoint.second / roundedMaxAccel) * chartHeight)
                        rawPath.moveTo(normalizedX.toFloat(), normalizedY.toFloat())

                        for (i in 1 until rawMagnitudes.size) {
                            val point = rawMagnitudes[i]
                            val x = ((point.first - minTime) / timeRange) * chartWidth
                            val y = chartHeight - ((point.second / roundedMaxAccel) * chartHeight)
                            rawPath.lineTo(x.toFloat(), y.toFloat())
                        }

                        drawPath(
                            path = rawPath,
                            color = Color(0xFF008080), // Teal
                            style = Stroke(width = 2f, cap = StrokeCap.Round),
                            alpha = 0.5f
                        )
                    }
                }

                // Draw smoothed acceleration line - for both chart types
                if (smoothedData.isNotEmpty()) {
                    val smoothedPath = Path()
                    val firstPoint = smoothedData.first()
                    val normalizedX = ((firstPoint.first - minTime) / timeRange) * chartWidth
                    val normalizedY = chartHeight - ((firstPoint.second / roundedMaxAccel) * chartHeight)
                    smoothedPath.moveTo(normalizedX.toFloat(), normalizedY.toFloat())

                    for (i in 1 until smoothedData.size) {
                        val point = smoothedData[i]
                        val x = ((point.first - minTime) / timeRange) * chartWidth
                        val y = chartHeight - ((point.second / roundedMaxAccel) * chartHeight)
                        smoothedPath.lineTo(x.toFloat(), y.toFloat())
                    }

                    val lineColor = if (chartType == "comparison") {
                        Color(0xFF0000FF) // Blue for comparison chart (matching Python)
                    } else {
                        Color(0xFF0000FF) // Blue for step detection chart
                    }

                    drawPath(
                        path = smoothedPath,
                        color = lineColor,
                        style = Stroke(width = 3f, cap = StrokeCap.Round),
                        alpha = 0.8f
                    )
                }

                // Draw step detection points - red dots exactly like in the Python code
                for (step in stepData) {
                    val x = ((step.first - minTime) / timeRange) * chartWidth
                    val y = chartHeight - ((step.second / roundedMaxAccel) * chartHeight)

                    // Larger, more prominent red circles for step detection points
                    drawCircle(
                        color = Color(0xFFDC143C), // Crimson (red) to match Python
                        radius = 8f,
                        center = Offset(x.toFloat(), y.toFloat())
                    )

                    // Add peak marker - small vertical line above the point
                    drawLine(
                        color = Color(0xFFDC143C),
                        start = Offset(x.toFloat(), y.toFloat() - 8f),
                        end = Offset(x.toFloat(), y.toFloat() - 16f),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // Y-axis labels
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 5.dp)
                .height(270.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            yAxisValues.reversed().forEach { value ->
                Text(
                    text = value.toString(),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        // X-axis labels
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, end = 10.dp)
                .fillMaxWidth()
                .height(30.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xAxisValues.forEach { value ->
                Text(
                    text = String.format("%.1f", value),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// Helper function to draw grid lines on charts
private fun DrawScope.drawGridLines(width: Float, height: Float, horizontalLines: Int, verticalLines: Int) {
    val horizontalSpacing = height / (horizontalLines - 1)

    // Draw horizontal grid lines
    for (i in 0 until horizontalLines) {
        val y = i * horizontalSpacing
        drawLine(
            color = Color.LightGray,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 0.5f
        )
    }

    // Draw vertical grid lines
    val verticalSpacing = width / (verticalLines - 1)

    for (i in 0 until verticalLines) {
        val x = i * verticalSpacing
        drawLine(
            color = Color.LightGray,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 0.5f
        )
    }
}

// Data class to store step information
data class StepInfo(
    val timestamp: Double,
    val stepLength: Double,
    val velocity: Double,
    val isTooSlow: Boolean = false,
    val isTooFast: Boolean = false
)

private fun calculateMagnitude(x: Float, y: Float, z: Float): Double {
    return Math.sqrt((x * x + y * y + z * z).toDouble())
}