# ReMeasure

ReMeasure is an open-source augmented reality (AR) measurement application for Android. The application uses ARCore, Sceneview, and Jetpack Compose to measure distances, heights, and areas in physical space. It recreates and modernizes the core capabilities of the discontinued Google Measure app.

## Features

### Measurement Capabilities

- **AR Distance Measurement**: Measure line segments between two points on detected 3D surfaces.
- **Axis and Point Snapping**: Snap the reticle to 90-degree axes and existing measurement points.
- **Surface Level**: Check surface tilt and angles with a digital bubble level.
- **Camera Snapshot**: Capture and save photos with measurement overlays directly to storage.

### User Experience

- **Measurement History**: Inspect, review, and delete previous measurements.
- **Unit Selection**: Switch between metric (m, cm) and imperial (ft, in) units.
- **Material 3 Interface**: Dynamic color theme with dark and light mode support.

## Requirements

- Android 8.0 (API level 26) or higher
- A device that supports Google Play Services for AR (ARCore)
- Camera permission enabled on the device

## Tech Stack

- Kotlin
- Jetpack Compose with Material 3
- Sceneview (Filament 3D engine with ARCore)
- Android Architecture Components (ViewModel, StateFlow)
- AndroidX Navigation Compose

## Build Instructions

1. Clone the repository to your computer.
2. Open the project in Android Studio Ladybug or newer.
3. Connect an ARCore-compatible Android device with USB debugging enabled.
4. Run the Gradle build task to install the application:

```sh
./gradlew installDebug
```

## License

The project uses the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for the full license text.
