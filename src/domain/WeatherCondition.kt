package domain

/**
 * Weather condition configured for a level.
 *
 * The [jsonValue] strings are intentionally English because level files use English JSON keys and
 * values. User-facing German labels can be mapped in the UI layer without changing persistence.
 *
 * [adhesionMultiplier] scales the level's base adhesion modifier. `DRY` is `1.0` so existing
 * standard-weather levels keep their previous driving behavior, while wet and snowy roads lower
 * the effective friction used by curve limits, acceleration, and braking.
 *
 * @property jsonValue English value written to and read from level JSON files.
 * @property adhesionMultiplier Multiplier applied to the level's base adhesion modifier.
 */
enum class WeatherCondition(val jsonValue: String, val adhesionMultiplier: Double) {
    /** Dry roads with the default driving conditions. */
    DRY("dry", 1.0),

    /** Wet roads, intended for rainy levels. */
    WET("wet", 0.7),

    /** Snow-covered roads, intended for snowy levels. */
    SNOWY("snowy", 0.4);

    companion object {
        /** Default condition used for levels that do not define a weather value yet. */
        val DEFAULT: WeatherCondition = DRY

        /**
         * Resolves a JSON value to a supported weather condition.
         *
         * @param value raw value read from `weatherCondition`.
         * @return the matching condition.
         * @throws IllegalArgumentException when [value] is not one of `dry`, `wet`, or `snowy`.
         */
        fun fromJsonValue(value: String): WeatherCondition {
            return entries.firstOrNull { it.jsonValue == value }
                ?: throw IllegalArgumentException(
                    "Unsupported weatherCondition '$value'. Supported values are: " +
                        entries.joinToString { it.jsonValue }
                )
        }
    }
}
