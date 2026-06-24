package dev.jamlab.shipcomputer.model

data class RealtimeSessionData(
    val clientSecret: String,
    val model: String,
    val voice: String,
    val personaName: String,
    val turnDetectionEnabled: Boolean,
    val turnDetectionSilenceMs: Int,
    val maxListenSeconds: Int,
)
