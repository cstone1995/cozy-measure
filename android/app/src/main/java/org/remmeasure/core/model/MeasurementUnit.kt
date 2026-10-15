package org.remmeasure.core.model

enum class MeasurementUnit(val label: String, val shortSymbol: String) {
    METRIC("Meters & centimeters", "m / cm"),
    IMPERIAL("Feet & inches", "ft / in"),
    INCHES("Inches only", "in")
}
