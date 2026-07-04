package com.lianyu.ai.network

import okhttp3.CertificatePinner

/**
 * Open-source certificate pin placeholder.
 *
 * The public edition does not pin any private relay certificate. Forks can add
 * their own pins when they operate a known backend.
 */
object CertificatePins {
    val certificatePinner: CertificatePinner = CertificatePinner.DEFAULT
    val PINNER: CertificatePinner = certificatePinner
}
