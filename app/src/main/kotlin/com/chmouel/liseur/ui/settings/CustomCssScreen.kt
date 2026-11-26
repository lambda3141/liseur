package com.chmouel.liseur.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.R
import com.chmouel.liseur.data.settings.CustomCssSetting
import com.chmouel.liseur.domain.CustomCss
import com.chmouel.liseur.domain.CustomCssSnippet
import com.chmouel.liseur.ui.contentWidthCap
import com.chmouel.liseur.ui.windowWidth
import kotlinx.coroutines.delay

/**
 * The reader's own stylesheet, for every book
 * (`docs/adr/0079-custom-css.md`).
 *
 * Saved as it is typed, a moment after the last key, and once more on the
 * way out, so leaving the screen never loses the end of a line. What the
 * page will make of it is shown under the switch: a stylesheet that paints
 * colours over the reading themes, and rules left out for loading a file,
 * are both said out loud rather than discovered on the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCssScreen(
    setting: CustomCssSetting,
    onText: (String) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(setting.text) }
    val saved = remember { mutableStateOf(setting.text) }
    val save by rememberUpdatedState(onText)
    val latest by rememberUpdatedState(text)
    LaunchedEffect(text) {
        if (text == saved.value) return@LaunchedEffect
        delay(SAVE_DELAY_MS)
        save(text)
        saved.value = text
    }
    DisposableEffect(Unit) {
        onDispose { if (latest != saved.value) save(latest) }
    }
    val prepared = remember(text) { CustomCss.prepare(text) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.custom_css_title)) },
                navigationIcon = { PaneBackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = contentWidthCap(windowWidth()))
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    SwitchRow(
                        title = stringResource(R.string.custom_css_use),
                        subtitle = stringResource(R.string.custom_css_use_detail),
                        checked = setting.enabled,
                        onCheckedChange = onEnabled,
                    )
                }
                Text(
                    text = stringResource(R.string.custom_css_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (prepared.setsColours) {
                    CssNotice(stringResource(R.string.custom_css_colour_warning))
                }
                if (prepared.leftOut > 0) {
                    CssNotice(
                        pluralStringResource(R.plurals.custom_css_left_out, prepared.leftOut, prepared.leftOut),
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.custom_css_editor_label)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = EDITOR_MIN_LINES,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmClear = true }, enabled = text.isNotBlank()) {
                        Text(stringResource(R.string.clear))
                    }
                }
                SettingsGroup(title = stringResource(R.string.custom_css_snippets)) {
                    Text(
                        text = stringResource(R.string.custom_css_snippets_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    CustomCssSnippet.entries.forEachIndexed { index, snippet ->
                        if (index > 0) RowDivider()
                        val title = stringResource(snippet.title)
                        val added = snippet.isIn(text)
                        ListItem(
                            headlineContent = { Text(title) },
                            trailingContent = {
                                if (added) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = stringResource(R.string.custom_css_snippet_added),
                                    )
                                } else {
                                    Icon(Icons.Outlined.Add, contentDescription = null)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(enabled = !added, role = Role.Button) {
                                text = snippet.appendTo(text, title)
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.custom_css_clear_title)) },
            text = { Text(stringResource(R.string.custom_css_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    text = ""
                    confirmClear = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** The Reading appearance row that opens [CustomCssScreen], saying where it stands. */
@Composable
internal fun CustomCssRow(setting: CustomCssSetting, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val summary = when {
        setting.text.isBlank() -> R.string.custom_css_summary
        setting.enabled -> R.string.custom_css_summary_on
        else -> R.string.custom_css_summary_off
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.custom_css_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun CssNotice(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@get:StringRes
private val CustomCssSnippet.title: Int
    get() = when (this) {
        CustomCssSnippet.DROP_CAP -> R.string.custom_css_snippet_drop_cap
        CustomCssSnippet.SMALL_CAPS -> R.string.custom_css_snippet_small_caps
        CustomCssSnippet.HEADING_COLOUR -> R.string.custom_css_snippet_heading_colour
        CustomCssSnippet.CENTRED_HEADINGS -> R.string.custom_css_snippet_centred_headings
        CustomCssSnippet.NO_INDENT -> R.string.custom_css_snippet_no_indent
        CustomCssSnippet.SCENE_BREAK -> R.string.custom_css_snippet_scene_break
        CustomCssSnippet.BALANCED -> R.string.custom_css_snippet_balanced
        CustomCssSnippet.OLDSTYLE_FIGURES -> R.string.custom_css_snippet_oldstyle
        CustomCssSnippet.DIM_IMAGES_IN_DARK -> R.string.custom_css_snippet_dim_images
    }

private const val SAVE_DELAY_MS = 500L
private const val EDITOR_MIN_LINES = 8
