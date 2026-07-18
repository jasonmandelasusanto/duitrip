package com.duitrip.app.domain

/** Ported 1:1 from backend/app/utils/categories.py and frontend DEFAULT_CATEGORIES. */
object Categories {

    data class DefaultCategory(val name: String, val emoji: String)

    val DEFAULT_CATEGORIES: List<DefaultCategory> = listOf(
        DefaultCategory("Flight", "✈️"),
        DefaultCategory("Accommodation", "🏨"),
        DefaultCategory("Food & Drink", "🍽️"),
        DefaultCategory("Transport", "🚗"),
        DefaultCategory("Tour & Activities", "🎟️"),
        DefaultCategory("Entertainment", "🎉"),
        DefaultCategory("Shopping", "🛍️"),
        DefaultCategory("Gift", "🎁"),
        DefaultCategory("Health & Medical", "💊"),
        DefaultCategory("Communication", "📱"),
        DefaultCategory("Other", "📌"),
    )

    private val EMOJI_BY_NAME: Map<String, String> =
        DEFAULT_CATEGORIES.associate { it.name to it.emoji }

    val DEFAULT_NAMES: List<String> = DEFAULT_CATEGORIES.map { it.name }

    /** Max custom categories per trip — matches the old backend invariant. */
    const val MAX_CUSTOM_CATEGORIES = 20

    fun isValid(category: String, customNames: List<String>): Boolean =
        category in DEFAULT_NAMES || category in customNames

    fun emojiFor(category: String, customEmojiMap: Map<String, String>? = null): String {
        customEmojiMap?.get(category)?.let { return it }
        return EMOJI_BY_NAME[category] ?: "🏷️"
    }
}
