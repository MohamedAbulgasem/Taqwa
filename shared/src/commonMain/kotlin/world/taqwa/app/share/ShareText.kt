package world.taqwa.app.share

/** Hands [text] to the platform's share sheet (spec 2b §2.3). Fire and forget; never throws. */
expect fun shareText(text: String)
