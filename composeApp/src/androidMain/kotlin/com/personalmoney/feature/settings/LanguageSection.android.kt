package com.personalmoney.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalmoney.core.locale.LocaleManager
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun LanguageSection() {
    SectionLabel(stringResource(Res.string.settings_language))
    var currentLangTag by remember { mutableStateOf(LocaleManager.currentTag()) }
    val availableLangs = remember(currentLangTag) { LocaleManager.availableLanguages() }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item {
            FilterChip(
                selected = currentLangTag == null,
                onClick = {
                    LocaleManager.apply(null)
                    currentLangTag = null
                },
                label = { Text(stringResource(Res.string.settings_language_system)) }
            )
        }
        items(availableLangs) { lang ->
            FilterChip(
                selected = currentLangTag.equals(lang.tag, ignoreCase = true),
                onClick = {
                    LocaleManager.apply(lang.tag)
                    currentLangTag = lang.tag
                },
                label = { Text(stringResource(lang.labelRes)) }
            )
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
}
