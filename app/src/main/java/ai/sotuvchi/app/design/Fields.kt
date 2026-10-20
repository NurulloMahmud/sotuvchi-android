package ai.sotuvchi.app.design

import ai.sotuvchi.app.core.format.Money
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Labeled text input: white field, 8px radius, hairline border that darkens to ink while focused. */
@Composable
fun LabeledField(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Theme.type.bodySmall, color = Theme.colors.inkMuted)
        content()
        if (error != null) {
            Text(error, style = Theme.type.caption, color = Theme.colors.error)
        } else if (hint != null) {
            Text(hint, style = Theme.type.caption, color = Theme.colors.inkSubtle)
        }
    }
}

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    multiline: Boolean = false,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = !multiline,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
) {
    val colors = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.md)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        interactionSource = interaction,
        textStyle = Theme.type.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, capitalization = capitalization),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .background(colors.surface, shape)
                    .border(BorderStroke(1.dp, if (focused) colors.ink else colors.hairline), shape)
                    .padding(horizontal = 14.dp, vertical = if (multiline) 12.dp else 0.dp),
                verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = Theme.type.body, color = colors.inkTertiary)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** Toggle row used in settings cards. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    tint: Color = Theme.colors.primary,
) {
    val colors = Theme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Theme.type.body, color = colors.ink)
            if (subtitle != null) Text(subtitle, style = Theme.type.caption, color = colors.inkSubtle)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = tint,
                checkedThumbColor = if (tint == colors.primary) colors.onPrimary else Color.White,
                checkedBorderColor = tint,
                uncheckedTrackColor = colors.surfaceAlt,
                uncheckedThumbColor = colors.inkSubtle,
                uncheckedBorderColor = colors.hairline,
            ),
        )
    }
}

/** Whole-number UZS input. Shows grouped digits while typing. */
@Composable
fun MoneyField(
    amount: Int?,
    onAmountChange: (Int?) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val text = amount?.let { Money.grouped(it) } ?: ""
    InputField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(10)
            onAmountChange(digits.toLongOrNull()?.toInt())
        },
        placeholder = placeholder,
        keyboardType = KeyboardType.Number,
        modifier = modifier,
        trailing = {
            Text(Money.currencyLabel, style = Theme.type.bodySmall, color = Theme.colors.inkSubtle, modifier = Modifier.padding(start = Space.xs))
        },
    )
}
