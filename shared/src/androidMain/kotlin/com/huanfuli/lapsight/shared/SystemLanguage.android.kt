package com.huanfuli.lapsight.shared

import java.util.Locale

actual fun systemLanguageTag(): String = Locale.getDefault().toLanguageTag()
