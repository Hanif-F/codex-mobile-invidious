package net.wingress.mobivious.player

/** A failed subtitle load, never inferred from a temporary absence of cues. */
data class CaptionLoadFailure(val occurrence: String, val uri: String, val language: String?, val label: String?, val message: String)
