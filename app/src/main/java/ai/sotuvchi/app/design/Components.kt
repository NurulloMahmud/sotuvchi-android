package ai.sotuvchi.app.design

import ai.sotuvchi.app.core.l10n.L
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// region Card

/** A white card lifted off the cream canvas by a 1px hairline. No shadow. */
@Composable
fun Modifier.cardBackground(radius: Dp = Radius.lg): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(Theme.colors.surface, shape)
        .border(BorderStroke(1.dp, Theme.colors.hairline), shape)
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: Dp = Space.md,
    radius: Dp = Radius.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardBackground(radius)
            .padding(padding),
        content = content,
    )
}

/** Card whose whole surface is a button. */
@Composable
fun Modifier.tappable(onClick: () -> Unit, role: Role = Role.Button): Modifier =
    this.clickable(role = role, onClick = onClick)

// endregion

// region Section header

/** Sentence-case eyebrow. The design doc forbids all-caps tracked eyebrows. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Theme.type.eyebrow, color = Theme.colors.inkMuted, modifier = Modifier.weight(1f))
        if (trailing != null && onTrailing != null) {
            Text(
                trailing,
                style = Theme.type.bodySmall,
                color = Theme.colors.ink,
                modifier = Modifier
                    .clickable(onClick = onTrailing)
                    .padding(vertical = Space.xxs),
            )
        }
    }
}

// endregion

// region Pill tabs

data class PillOption<T>(val value: T, val title: String, val count: Int? = null)

/** Pill toggle from the design doc: cream track, the selected option lifts onto white. */
@Composable
fun <T> PillTabs(
    options: List<PillOption<T>>,
    selection: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Theme.colors
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surfaceAlt.copy(alpha = 0.6f))
            .horizontalScroll(rememberScrollState())
            .padding(Space.xxs),
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        for (option in options) {
            val selected = option.value == selection
            Row(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) Modifier
                            .background(colors.surface, CircleShape)
                            .border(1.dp, colors.hairline, CircleShape)
                        else Modifier,
                    )
                    .clickable(role = Role.Tab) { onSelect(option.value) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    option.title,
                    style = Theme.type.button,
                    color = if (selected) colors.ink else colors.inkMuted,
                    maxLines = 1,
                )
                val count = option.count
                if (count != null && count > 0) {
                    Text(
                        count.toString(),
                        style = Theme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) colors.onPrimary else colors.ink,
                        modifier = Modifier
                            .background(if (selected) colors.primary else colors.surfaceAlt, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// endregion

// region Badges

enum class BadgeTone { Neutral, Accent, Success, Warning, Error, Inverse }

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Neutral, icon: ImageVector? = null) {
    val colors = Theme.colors
    val (fg, bg) = when (tone) {
        BadgeTone.Neutral -> colors.inkMuted to colors.surfaceAlt
        BadgeTone.Accent -> colors.accent to colors.accentSoft
        BadgeTone.Success -> colors.successInk to colors.successSoft
        BadgeTone.Warning -> colors.warningInk to colors.warningSoft
        BadgeTone.Error -> colors.error to colors.errorSoft
        BadgeTone.Inverse -> colors.onPrimary to colors.primary
    }
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(Radius.sm))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, style = Theme.type.caption.copy(fontWeight = FontWeight.Medium), color = fg, maxLines = 1)
    }
}

/** Small AI marker with the brand sparkle. */
@Composable
fun AIMark(modifier: Modifier = Modifier, label: String = "AI") {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Theme.colors.accent, modifier = Modifier.size(11.dp))
        Text(label, style = Theme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Theme.colors.accent)
    }
}

// endregion

// region Avatar

object Initials {
    /**
     * "Dilnoza Karimova" gives "DK"; an email or a handle only uses the part
     * before the @, so "makhammatoffb@gmail.com" gives "M".
     */
    fun of(name: String?): String? {
        val local = name?.split("@")?.firstOrNull() ?: return null
        val letters = local
            .split(' ', '_', '.', '-')
            .filter { it.isNotEmpty() }
            .take(2)
            .mapNotNull { part -> part.firstOrNull { it.isLetterOrDigit() } }
            .map { it.toString().uppercase() }
        return if (letters.isEmpty()) null else letters.joinToString("")
    }
}

@Composable
fun Avatar(name: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val initials = Initials.of(name)
    Box(
        modifier = modifier
            .size(size)
            .background(Theme.colors.surfaceAlt, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(
                initials,
                style = Theme.type.body.copy(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Medium),
                color = Theme.colors.ink,
            )
        } else {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = Theme.colors.inkSubtle,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

// endregion

// region States

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xl, horizontal = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Theme.colors.surfaceAlt, RoundedCornerShape(Radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Theme.colors.inkSubtle, modifier = Modifier.size(28.dp))
        }
        Text(title, style = Theme.type.subhead, color = Theme.colors.ink, textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, style = Theme.type.bodySmall, color = Theme.colors.inkMuted, textAlign = TextAlign.Center)
        }
        if (actionTitle != null && onAction != null) {
            Spacer8()
            SButton(actionTitle, onAction, kind = ButtonKind.Secondary, compact = true)
        }
    }
}

@Composable
private fun Spacer8() = Box(Modifier.height(Space.xxs))

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Theme.colors.errorSoft, RoundedCornerShape(Radius.md))
            .padding(Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Theme.colors.error, modifier = Modifier.size(20.dp))
        Text(message, style = Theme.type.bodySmall, color = Theme.colors.ink, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            Text(
                L("Retry"),
                style = Theme.type.button,
                color = Theme.colors.ink,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

enum class NoticeTone { Info, Warning, Accent }

/** Inline notice for warnings and informational copy. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.Info, icon: ImageVector? = null) {
    val colors = Theme.colors
    val iconColor = when (tone) {
        NoticeTone.Info -> colors.inkMuted
        NoticeTone.Warning -> colors.warningInk
        NoticeTone.Accent -> colors.accent
    }
    val background = when (tone) {
        NoticeTone.Info -> colors.surfaceAlt
        NoticeTone.Warning -> colors.warningSoft
        NoticeTone.Accent -> colors.accentSoft
    }
    val defaultIcon = when (tone) {
        NoticeTone.Info -> Icons.Outlined.Info
        NoticeTone.Warning -> Icons.Outlined.ErrorOutline
        NoticeTone.Accent -> Icons.Filled.AutoAwesome
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(Radius.md))
            .padding(Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon ?: defaultIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Text(text, style = Theme.type.bodySmall, color = Theme.colors.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Theme.colors.inkMuted, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 18.dp, color: Color = Theme.colors.inkMuted) {
    CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = modifier.size(size))
}

// endregion

// region Rows

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Theme.colors.ink) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(key, style = Theme.type.bodySmall, color = Theme.colors.inkMuted)
        Text(
            value,
            style = Theme.type.body,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(Theme.colors.hairlineSoft))
}

/** A tappable row inside a settings-style card. */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    badge: Int? = null,
    onClick: (() -> Unit)? = null,
    showsChevron: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = Theme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(if (destructive) colors.errorSoft else colors.surfaceAlt, RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (destructive) colors.error else colors.ink, modifier = Modifier.size(17.dp))
        }
        Text(
            title,
            style = Theme.type.body,
            color = if (destructive) colors.error else colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (badge != null && badge > 0) {
            Text(
                badge.toString(),
                style = Theme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
                modifier = Modifier
                    .background(colors.primary, CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        if (detail != null) {
            Text(detail, style = Theme.type.bodySmall, color = colors.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showsChevron) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

// endregion

// region Stat tile

/** "+12% vs previous 7 days". */
@Composable
fun TrendLabel(change: Double, modifier: Modifier = Modifier, caption: String? = null) {
    val colors = Theme.colors
    val color = if (change >= 0) colors.successInk else colors.error
    val label = ai.sotuvchi.app.core.model.Trend.label(change)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            (if (change >= 0) "↗ " else "↘ ") + label,
            style = Theme.type.caption.copy(fontWeight = FontWeight.Medium),
            color = color,
        )
        if (caption != null) Text(caption, style = Theme.type.caption, color = colors.inkSubtle)
    }
}

@Composable
fun StatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    change: Double? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = Theme.colors
    Column(
        modifier = modifier
            .cardBackground()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Theme.type.bodySmall, color = colors.inkMuted, maxLines = 1, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.inkTertiary, modifier = Modifier.size(16.dp))
            }
        }
        Text(value, style = Theme.type.headline, color = colors.ink)
        if (change != null) TrendLabel(change)
    }
}

// endregion
