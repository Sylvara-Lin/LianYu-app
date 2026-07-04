package com.lianyu.ai.database.repository

/**
 * Open-source secret codec.
 *
 * The private VMP/KMS implementation is intentionally not shipped. API keys are
 * stored using the normal repository path so downstream forks can plug in their
 * own storage policy without native security dependencies.
 */
object S0 : ApiConfigRepository.SecretCodec {
    override fun encrypt(plaintext: String): String = plaintext
    override fun decrypt(value: String): String? = value
    override fun isEncrypted(value: String): Boolean = false
}
