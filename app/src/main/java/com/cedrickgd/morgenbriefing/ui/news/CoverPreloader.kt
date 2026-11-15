package com.cedrickgd.morgenbriefing.ui.news

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale

/**
 * Loads the pictures of stories that are about to scroll in, so a card shows its picture the
 * moment it appears instead of fading it in a second later.
 *
 * Every picture is asked for in the size its card will ask for (a row's thumbnail, a lead
 * story's wide slot; cropped to fill, like the cards do): the app's image loader keeps decoded
 * pictures by address and hands one out when it is at least as large as wanted, so the card's
 * own request is answered from memory – and from the disk cache once memory let go of it. Each
 * address and size is asked for once (the last [MAX_REMEMBERED] are remembered).
 *
 * Call from the main thread only (the list's effects).
 */
internal class CoverPreloader(context: Context) {
    private val app = context.applicationContext
    private val asked = LinkedHashSet<String>()

    /** Loads [url] ahead for a slot of [width] × [height] pixels. */
    fun preload(url: String, width: Int, height: Int) {
        val address = safeImageUrl(url) ?: return
        if (width <= 0 || height <= 0) return
        if (!asked.add("$width $height $address")) return
        while (asked.size > MAX_REMEMBERED) asked.remove(asked.first())
        val request = ImageRequest.Builder(app)
            .data(address)
            // A few pixels more than the slot: a picture decoded a rounding step too small would be decoded again.
            .size(width + SLACK_PX, height + SLACK_PX)
            .scale(Scale.FILL)
            // Like the cards (AsyncImage): a picture a little larger than the slot is fine.
            .precision(Precision.INEXACT)
            .build()
        SingletonImageLoader.get(app).enqueue(request)
    }

    private companion object {
        const val MAX_REMEMBERED = 400
        const val SLACK_PX = 4
    }
}
