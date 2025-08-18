package com.ozzybozy.qrhub

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtils {
    fun setLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}