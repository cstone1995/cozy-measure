package org.remmeasure.core.model

enum class TrackingStatus(val message: String) {
    INITIALIZING("Initializing camera and AR..."),
    SEARCHING_FOR_SURFACES("Move phone slowly from left to right"),
    EXCESSIVE_MOTION("Moving too fast"),
    INSUFFICIENT_LIGHT("Too dark to detect surfaces"),
    TRACKING_READY("Aim at a surface and tap + to start measuring"),
    POINT_PLACED("Move to the endpoint and tap ✓ to finish"),
    STOPPED("AR session paused")
}
