package com.rangemate.data.protocol

data class BasicInfo(
    val voltage: Float,
    val current: Float,
    val capacity: Float,
    val totalCapacity: Float,
    val cycles: Int,
    val soc: Int,
    val temperature: Float,
    val cellCount: Int,
    val cellVoltages: List<Float> = emptyList(),
    val protectionStatus: Int = 0,
    val fetStatus: Int = 0,
    val balanceStatus: Long = 0L,
    val version: Int = 0,
    val manufacturer: String = "",
    val hardwareVersion: String = "",
    val firmwareVersion: String = ""
)