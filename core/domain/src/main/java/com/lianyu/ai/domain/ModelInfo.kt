package com.lianyu.ai.domain

/**
 * Lightweight model metadata for domain-level model management.
 * Decouples feature modules from feature:localmodel implementation details.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long
)
