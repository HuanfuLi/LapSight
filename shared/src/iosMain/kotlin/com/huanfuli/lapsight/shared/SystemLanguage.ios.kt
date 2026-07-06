package com.huanfuli.lapsight.shared

import platform.Foundation.NSLocale

actual fun systemLanguageTag(): String =
    NSLocale.preferredLanguages.firstOrNull()?.toString() ?: "en"
