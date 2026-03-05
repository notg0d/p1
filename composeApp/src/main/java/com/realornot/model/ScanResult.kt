package com.realornot.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_results")
data class ScanResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val mediaType: String,           // "IMAGE", "VIDEO", "AUDIO"
    val verdict: String,             // "REAL" or "AI-GENERATED"
    val confidence: Float,           // 0-100%
    val modelUsed: String,
    val processingTimeMs: Long,      // milliseconds
    val timestamp: Long = System.currentTimeMillis(),
    // Video-specific dual analysis
    val videoVerdict: String? = null,
    val videoConfidence: Float? = null,
    val audioVerdict: String? = null,
    val audioConfidence: Float? = null,
    val reasoning: String? = null,
)
