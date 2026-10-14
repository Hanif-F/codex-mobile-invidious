package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

internal enum class AppDestination(val label: String, val icon: ImageVector) {
    POPULAR("Popular",Icons.Outlined.Explore),
    TRENDING("Trending",Icons.AutoMirrored.Outlined.TrendingUp),
    SUBSCRIPTIONS("Subscriptions",Icons.Outlined.Subscriptions),
    YOU("You",Icons.Outlined.AccountCircle);
    companion object { fun from(tab: String) = entries.firstOrNull { it.label==tab } }
}

/** Available window width also serves multi-window; no model/orientation assumptions. */
internal enum class AppWindowWidth { COMPACT, MEDIUM, EXPANDED;
    companion object { fun from(width: Float) = when { width<600 -> COMPACT; width<840 -> MEDIUM; else -> EXPANDED } }
}

@Composable
internal fun AppNavigation(selected: AppDestination?, rail: Boolean, enabled: Boolean = true,
    modifier: Modifier = Modifier, select: (AppDestination) -> Unit) {
    val colors=MaterialTheme.colorScheme
    if(rail) NavigationRail(modifier.width(112.dp).shadow(1.dp),containerColor=colors.surfaceContainer) {
        AppDestination.entries.forEach { item -> NavigationRailItem(
            selected=item==selected,onClick={select(item)},enabled=enabled,
            icon={Icon(item.icon,null)},label={Text(item.label,Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelMedium)},
            alwaysShowLabel=true,modifier=Modifier.testTag("navigation-${item.label}").semantics { contentDescription=item.label },
            colors=NavigationRailItemDefaults.colors(indicatorColor=colors.primaryContainer,
                selectedIconColor=colors.onPrimaryContainer,selectedTextColor=colors.primary)) }
    } else BoxWithConstraints(modifier.fillMaxWidth().shadow(1.dp)) {
        val density=LocalDensity.current
        val measurer=androidx.compose.ui.text.rememberTextMeasurer()
        val style=MaterialTheme.typography.labelMedium
        val labelWidth=with(density) {(maxWidth/4-8.dp).coerceAtLeast(1.dp).roundToPx()}
        val labelHeight=with(density) {AppDestination.entries.maxOf { item ->
            measurer.measure(item.label,style,constraints=androidx.compose.ui.unit.Constraints(maxWidth=labelWidth)).size.height
        }.toDp()}
        NavigationBar(Modifier.fillMaxWidth().heightIn(min=maxOf((80+((density.fontScale-1).coerceAtLeast(0f)*80)).dp,labelHeight+56.dp)),
            containerColor=colors.surfaceContainer,tonalElevation=0.dp) {
            AppDestination.entries.forEach { item -> NavigationBarItem(
                selected=item==selected,onClick={select(item)},enabled=enabled,
                icon={Icon(item.icon,null)},label={Box(Modifier.fillMaxWidth().height(labelHeight),contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
                    Text(item.label,Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=style)
                }},alwaysShowLabel=true,modifier=Modifier.testTag("navigation-${item.label}").semantics {contentDescription=item.label},
                colors=NavigationBarItemDefaults.colors(indicatorColor=colors.primaryContainer,
                    selectedIconColor=colors.onPrimaryContainer,selectedTextColor=colors.primary)) }
        }
    }
}
