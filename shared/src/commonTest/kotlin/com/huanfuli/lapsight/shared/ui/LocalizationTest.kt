package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationTest {
    @Test
    fun systemLanguageMatchesSupportedPrimarySubtags() {
        assertEquals(AppLanguage.Chinese, resolveAppLanguage(LanguageMode.System, "zh-Hans-US"))
        assertEquals(AppLanguage.Korean, resolveAppLanguage(LanguageMode.System, "ko-KR"))
        assertEquals(AppLanguage.Japanese, resolveAppLanguage(LanguageMode.System, "ja_JP"))
        assertEquals(AppLanguage.French, resolveAppLanguage(LanguageMode.System, "fr-FR"))
        assertEquals(AppLanguage.Spanish, resolveAppLanguage(LanguageMode.System, "es-MX"))
    }

    @Test
    fun systemLanguageFallsBackToEnglish() {
        assertEquals(AppLanguage.English, resolveAppLanguage(LanguageMode.System, "de-DE"))
        assertEquals(AppLanguage.English, resolveAppLanguage(LanguageMode.System, ""))
    }

    @Test
    fun explicitLanguageOverridesSystemTag() {
        assertEquals(AppLanguage.Japanese, resolveAppLanguage(LanguageMode.Japanese, "fr-FR"))
        assertEquals(AppLanguage.English, resolveAppLanguage(LanguageMode.English, "zh-CN"))
    }
}
