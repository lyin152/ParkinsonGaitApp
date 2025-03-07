# Step Detection App for Parkinson's Disease

An accelerometer-based gait monitoring application designed specifically for Parkinson's disease patients to analyze walking patterns and provide real-time feedback on gait abnormalities.

## Core Features

- **Advanced Step Detection**: Algorithm using real-time dynamic thresholds and future window analysis 
- **Comprehensive Gait Analysis**: Quantifies step length, velocity, cadence, asymmetry, and variability
- **Real-time Feedback System**: Audio-haptic alerts for gait abnormalities (too fast/slow walking)
- **Visual Analysis Tools**: Charts for velocity trends and acceleration patterns
- **Step History Tracking**: Records detailed information about each step for post-hoc analysis
- **Data Export**: CSV export functionality for sharing with healthcare professionals
- ![1741300654341 (1)](https://github.com/user-attachments/assets/fb1168e5-2368-4e98-abdd-b861559e54f8)


## Technical Implementation

### Step Detection Algorithm

Our step detection algorithm uses a multi-stage approach:

1. **Signal Processing**:
   - Accelerometer data sampling at ≈10Hz (SAMPLING_INTERVAL = 0.1s)
   - Exponential smoothing with α=0.1 to reduce noise
   - Dynamic threshold calculation using rolling window statistics

2. **Peak Detection Logic**:
   - Future window buffering (1.0s) for improved accuracy
   - Confirmation of peaks by comparing with nearby values
   - Local maxima verification with look-ahead/look-behind windows
   - Temporal filtering with minimum step interval (0.4s)

3. **Step Parameter Calculation**:
   - Step length estimation using Kim's formula: k * ∛(mean acceleration)
   - Velocity calculation based on step length and timing
   - Z-score analysis for detecting abnormal gait patterns

### Gait Analysis System

The system performs statistical analysis on walking patterns:

```
Step → [Dynamic Threshold Detection] → [Peak Verification] → [Step Parameter Extraction] → [Statistical Analysis] → [Pattern Classification] → [Feedback]
```

Key metrics include:
- Step length variability (CV)
- Walking rhythm consistency
- Gait asymmetry index
- Acceleration pattern analysis
- Velocity fluctuation

### Feedback Mechanisms

- **Audio**: Different tones for different abnormalities
- **Haptic**: Customized vibration patterns (rapid pulses for fast walking, slow pulses for slow walking)
- **Visual**: Color-coded alerts and detailed step history

## Technical Implementation

### Step Detection Algorithm

Our algorithm employs a sophisticated approach optimized for Parkinson's gait patterns:

1. **Pre-processing and Signal Conditioning**:
   - **Sampling**: Raw accelerometer data is collected at 10Hz (SAMPLING_INTERVAL = 0.1s)
   - **Magnitude Calculation**: 3D acceleration vector magnitude is computed: √(x² + y² + z²)
   - **Exponential Smoothing**: Noise reduction using exponential smoothing: Smoothed = α * Current + (1-α) * Previous, where α = 0.1
   - **Data Buffering**: Implementation of a future window buffer (1.0s) to enable look-ahead analysis for more accurate peak detection

2. **Dynamic Threshold Computation**:
   - **Rolling Window**: Maintains a window of recent acceleration values (windowSize = 15)
   - **Statistical Analysis**: Calculates mean and standard deviation of the rolling window
   - **Adaptive Threshold**: Sets threshold as: mean + (stdDev * 0.5)
   - **Minimum Threshold**: Enforces a minimum threshold (minStepThreshold = 7.5) to prevent false positives during very slow movement

3. **Peak Detection and Verification**:
   - **Primary Condition**: Identifies points where smoothed acceleration exceeds the dynamic threshold
   - **Neighborhood Analysis**: Verifies that the point is higher than surrounding points (2 before, 2 after)
   - **Future Window Optimization**: Searches ahead in the buffer for potentially higher peaks that may represent the true step
   - **Temporal Filtering**: Enforces minimum time between steps (minStepInterval = 0.4s) to prevent double-counting

4. **False Positive Rejection**:
   - **Confirmation Window**: Analyzes acceleration patterns around detected peaks
   - **Shape Verification**: Confirms characteristic acceleration shape of a true step
   - **Consistency Check**: Compares with patterns of previously detected steps

### Step Length Calculation Algorithm

The step length calculation builds on established biomechanical models:

1. **Kim's Step Length Formula**:
   - **Base Equation**: Step Length = k * ∛(mean absolute acceleration)
   - **Coefficient**: k = 0.26 (derived from empirical studies on walking biomechanics)

2. **Implementation Process**:
   ```
   For each detected step:
     1. Identify window of acceleration data around the step (±5 samples)
     2. Calculate magnitude of acceleration for each sample in window
     3. Compute mean absolute acceleration across the window
     4. Apply Kim's formula: stepLength = k * Math.cbrt(meanAbsAcceleration)
     5. Store step length for statistical analysis
   ```

3. **Velocity Computation**:
   - **Time Interval**: Calculate time elapsed since previous step
   - **Velocity Formula**: velocity = stepLength / stepInterval
   - **Smoothing**: Optional exponential smoothing to reduce noise in velocity estimates

4. **Gait Parameter Analysis**:
   - **Statistical Processing**: Calculate mean, standard deviation, and Z-scores
   - **Anomaly Detection**: Identify steps with Z-scores beyond ±1.5 as abnormal
   - **Pattern Recognition**: Classify based on combined parameters (length, timing, acceleration profile)

### Algorithm Calibration and Parameters

The detection algorithm includes several tunable parameters:

- **minStepInterval = 0.4s**: Minimum time between consecutive steps
- **minStepThreshold = 7.5**: Minimum acceleration peak to be considered a step
- **smoothingFactor = 0.1**: Exponential smoothing coefficient
- **windowSize = 15**: Size of rolling window for dynamic threshold
- **FUTURE_WINDOW = 1.0s**: Look-ahead time window for improved peak detection

These parameters were optimized based on testing with normal and Parkinsonian gait patterns, but may be adjusted for different populations or walking conditions.


## Development Setup

1. Clone the repository
```bash
git clone https://github.com/yourusername/ParkinsonStepDetection.git
```

2. Open with Android Studio
3. Build and run on Android device with accelerometer

## Code Architecture

- **MainActivity**: Core logic, sensor handling, and gait analysis algorithms
- **Composable UI**: Material Design 3 components using Jetpack Compose
- **Gait Analysis**: Statistical analysis and pattern recognition
- **Data Management**: CSV storage and file sharing capabilities

## Project Structure Guide for Developers

### Key Files and Directories

#### `/app/src/main/java/com/example/stepdetection/`

- **MainActivity.kt**: The heart of the application containing:
  - Sensor management and data collection
  - Step detection algorithm implementation
  - Gait analysis logic
  - File operations for data export
  - To modify core detection logic, focus on the `detectStep()` and `processBufferedData()` methods
  
- **AccelerometerData.kt**: Data class for storing raw sensor readings
  - Add additional fields here if you need to track more sensor attributes
  
- **StepDetectionApp.kt**: Main composable UI components
  - Contains all screens and UI elements
  - Modify this to change the app's appearance and layout
  - Add new visualization components here

- **VelocityChart.kt**: Implementation of the velocity visualization
  - Customize this file to change chart appearance or add new data series

- **AccelerationChartScreen.kt**: Contains the visualization screen for acceleration data
  - Enhance this file to add more complex visualizations

#### `/app/src/main/res/`

- **values/themes.xml**: Defines the app's visual theme
  - Modify colors, typography, and other visual elements here

- **xml/file_paths.xml**: Configuration for file sharing functionality
  - Update if you change how files are stored or shared

#### `/app/src/main/AndroidManifest.xml`

- Defines app permissions and components
- Add any new permissions or activities here

### How to Modify Key Components

1. **Adjusting Step Detection Sensitivity**:
   - In `MainActivity.kt`, find the `minStepThreshold` variable
   - Decreasing this value increases sensitivity, increasing it reduces false positives

2. **Modifying Feedback Parameters**:
   - Adjust vibration and sound patterns in `provideFeedbackForSlow()` and `provideFeedbackForFast()`
   - Change the Z-score thresholds in `analyzeStepLengthImproved()` 

3. **Extending Gait Analysis**:
   - Add new analysis methods in the `MainActivity.kt` class
   - Create new data classes to store your additional metrics
   - Connect your analysis to the UI by updating state variables

4. **Adding New Visualizations**:
   - Create new Composable functions in separate files
   - Include them in the main UI by updating the `StepDetectionApp.kt` file
   - Pass relevant data as parameters from MainActivity

5. **Improving Data Export**:
   - Modify the `saveDataToCsv()` method to include additional data columns
   - Update the `shareFileViaEmail()` method to customize sharing functionality

## Implementation Details

- **Platform**: Android (min SDK 21, target SDK 33)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Sensor Sampling Rate**: ≈10Hz (adjustable via SAMPLING_INTERVAL)
- **Step Detection Sensitivity**: Customizable via thresholds (currently minStepThreshold = 7.5)

## License

This project is licensed under the MIT License - see the LICENSE file for details.

