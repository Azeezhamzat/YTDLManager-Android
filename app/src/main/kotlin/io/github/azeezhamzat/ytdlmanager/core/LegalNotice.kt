package io.github.azeezhamzat.ytdlmanager.core

/**
 * The notice shown on every screen. Same wording as the desktop app's
 * `config.LEGAL_NOTICE`, minus the clause about its optional YouTube sign-in:
 * this app has no sign-in at all.
 */
object LegalNotice {
    const val TEXT =
        "Only download videos you own, that are in the public domain, that carry a " +
            "licence permitting download, or for which you otherwise have the rights " +
            "holder's explicit permission. This application does not bypass DRM, " +
            "CAPTCHAs, paywalls, or other access controls. It uses no logins or " +
            "cookies. It cannot guarantee that every item on YouTube, TikTok, " +
            "Twitter/X, Instagram, Facebook, or Vimeo is legally or technically " +
            "downloadable."
}
