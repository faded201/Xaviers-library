package com.xaviers.library

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

enum class TomeState(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val captionRes: Int,
    @DrawableRes val imageRes: Int,
    @ColorRes val accentRes: Int,
    @ColorRes val backgroundRes: Int
) {
    DORMANT(
        titleRes = R.string.state_dormant_title,
        subtitleRes = R.string.state_dormant_subtitle,
        captionRes = R.string.state_dormant_caption,
        imageRes = R.drawable.dormant_tome,
        accentRes = R.color.tome_dormant,
        backgroundRes = R.color.bg_dormant
    ),
    STIRRING(
        titleRes = R.string.state_stirring_title,
        subtitleRes = R.string.state_stirring_subtitle,
        captionRes = R.string.state_stirring_caption,
        imageRes = R.drawable.stirring_grimoire,
        accentRes = R.color.tome_stirring,
        backgroundRes = R.color.bg_stirring
    ),
    AWAKENED(
        titleRes = R.string.state_awakened_title,
        subtitleRes = R.string.state_awakened_subtitle,
        captionRes = R.string.state_awakened_caption,
        imageRes = R.drawable.awakened_codex,
        accentRes = R.color.tome_awakened,
        backgroundRes = R.color.bg_awakened
    ),
    UNLEASHED(
        titleRes = R.string.state_unleashed_title,
        subtitleRes = R.string.state_unleashed_subtitle,
        captionRes = R.string.state_unleashed_caption,
        imageRes = R.drawable.unleashed_manuscript,
        accentRes = R.color.tome_unleashed,
        backgroundRes = R.color.bg_unleashed
    );

    fun next(): TomeState {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
