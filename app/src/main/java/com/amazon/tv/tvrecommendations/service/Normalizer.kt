package com.amazon.tv.tvrecommendations.service

class Normalizer {
    private var sum = 0.0

    fun addNormalizeableValue(value: Double) { sum += value }

    fun getNormalizedValue(value: Double) = if (sum != 0.0) value / sum else 0.0

    fun reset() { sum = 0.0 }
}