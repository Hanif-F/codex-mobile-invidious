package net.wingress.mobivious.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.wingress.mobivious.data.AiListKind

@Composable
internal fun AiThumbnail(kind: AiListKind, modifier: Modifier = Modifier, compact: Boolean = false) {
    val warning = if (kind == AiListKind.BLOCKLIST) "Likely AI-generated" else "Possibly AI-generated"
    val source = if (kind == AiListKind.BLOCKLIST) "AiSList Blocklist" else "AiSList Warnlist"
    Column(modifier.background(Color(0xFF25282C)).testTag("ai-thumbnail-${kind.wire}")
        .semantics { contentDescription = "$warning. $source" }.padding(if (compact) 3.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(warning, color = Color(0xFFADB0B5), fontWeight = FontWeight.Normal, fontSize = if (compact) 10.sp else 16.sp,
            lineHeight = if (compact) 11.sp else 20.sp, textAlign = TextAlign.Center)
        Text(source, color = Color(0xFF92969C), fontWeight = FontWeight.Normal, fontSize = if (compact) 8.sp else 12.sp,
            lineHeight = if (compact) 9.sp else 16.sp, textAlign = TextAlign.Center)
    }
}
