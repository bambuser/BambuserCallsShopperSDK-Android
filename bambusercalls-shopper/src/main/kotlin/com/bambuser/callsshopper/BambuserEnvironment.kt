package com.bambuser.callsshopper

/**
 * Which Bambuser region the SDK talks to. Hosts pick a value on
 * `BambuserCallConfiguration.environment`; the SDK holds the URL.
 *
 * `custom(url)` exists for local development against a self-hosted or
 * forked embed. Not intended for production use — merchants ship with
 * `US` or `EU`.
 */
class BambuserEnvironment private constructor(
    val embedUrl: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BambuserEnvironment) return false
        return embedUrl == other.embedUrl
    }

    override fun hashCode(): Int = embedUrl.hashCode()

    override fun toString(): String = "BambuserEnvironment(embedUrl=$embedUrl)"

    companion object {
        /** Global / US production server. Use with `lcx.bambuser.com` BamHubs. */
        val US: BambuserEnvironment = BambuserEnvironment(
            embedUrl = "https://one-to-one.bambuser.com/embed.js"
        )

        /** EU production server. Use with `lcx-eu.bambuser.com` BamHubs. */
        val EU: BambuserEnvironment = BambuserEnvironment(
            embedUrl = "https://one-to-one.bambuser.com/eu/embed.js"
        )

        /**
         * Bambuser-internal staging on the US region.
         *
         * Debug-only — gated on [BuildConfig.DEBUG] so release
         * builds resolve this to the US production URL. Nothing
         * about the staging endpoint should ever reach a published
         * binary; consumers pinning `stageUS` in DEBUG get staging,
         * and the same call site auto-falls-back to `US` in release.
         *
         * Mirrors iOS's `#if DEBUG static let stageUS`.
         */
        val stageUS: BambuserEnvironment =
            if (com.bambuser.callsshopper.BuildConfig.DEBUG) {
                BambuserEnvironment(embedUrl = "https://lvs-121-dev.web.app/embed.js")
            } else {
                US
            }

        /** Custom embed URL — for local dev / forks. */
        fun custom(embedUrl: String): BambuserEnvironment = BambuserEnvironment(embedUrl)
    }
}
