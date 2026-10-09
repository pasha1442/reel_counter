package com.reelcounter.core.data

/** The public legal pages in docs/, bundled into the app too. Values are file names. */
object LegalPages {
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val DELETE_ACCOUNT = "delete-account"

    fun title(page: String): String = when (page) {
        PRIVACY -> "Privacy Policy"
        TERMS -> "Terms of Use"
        DELETE_ACCOUNT -> "Delete your account"
        else -> "Reel Counter"
    }
}
