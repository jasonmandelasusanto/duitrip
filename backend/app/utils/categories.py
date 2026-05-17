DEFAULT_CATEGORIES = [
    "Flight",
    "Accommodation",
    "Food & Drink",
    "Transport",
    "Tour & Activities",
    "Entertainment",
    "Shopping",
    "Gift",
    "Health & Medical",
    "Communication",
    "Other",
]

CATEGORY_EMOJI = {
    "Flight": "✈️",
    "Accommodation": "🏨",
    "Food & Drink": "🍽️",
    "Transport": "🚗",
    "Tour & Activities": "🎟️",
    "Entertainment": "🎉",
    "Shopping": "🛍️",
    "Gift": "🎁",
    "Health & Medical": "💊",
    "Communication": "📱",
    "Other": "📌",
}


def validate_category(category: str, custom_categories: list[str]) -> bool:
    return category in DEFAULT_CATEGORIES + custom_categories


def get_emoji(category: str, custom_emoji_map: dict[str, str] | None = None) -> str:
    if custom_emoji_map and category in custom_emoji_map:
        return custom_emoji_map[category]
    return CATEGORY_EMOJI.get(category, "🏷️")
