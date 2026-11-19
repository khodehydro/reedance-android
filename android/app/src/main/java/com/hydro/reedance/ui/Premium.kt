package com.hydro.reedance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hydro.reedance.R

private val PaywallBackground = Color(0xFF151716)
private val PaywallAccent = Color(0xFF34C6D6)
private val PaywallMuted = Color(0xFFB4BCBC)
private val PaywallColors = darkColorScheme(
    primary = PaywallAccent, onPrimary = Color(0xFF102629),
    surface = PaywallBackground, onSurface = Color.White,
    onSurfaceVariant = PaywallMuted
)

/** Never compose hidden text/actions: blur is presentation, not access control.
 * Placeholder bars also work below API 31, where Compose's blur is unsupported. */
@Composable internal fun PremiumPreview() {
    Panel {
        Column(Modifier.fillMaxWidth().then(if(android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(7.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(.8f, 1f, .65f).forEach { width ->
                Box(Modifier.fillMaxWidth(width).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)))
            }
        }
        Text(tr("ادامهٔ آموزش با اشتراک", "More learning with a subscription"), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun PremiumScreen(onClose: () -> Unit) {
    MaterialTheme(colorScheme = PaywallColors, typography = MaterialTheme.typography) {
        Surface(color = PaywallBackground, modifier = Modifier.fillMaxSize()) {
            PremiumScreenContent(onClose)
        }
    }
}

@Composable private fun PremiumScreenContent(onClose: () -> Unit) {
    var quarterly by rememberSaveable { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val period = if(quarterly) tr("سه‌ماهه", "Three months") else tr("ماهیانه", "Monthly")
    val price = if(quarterly) tr("۵۶۰٬۰۰۰", "560,000") else tr("۲۸۰٬۰۰۰", "280,000")
    val unit = tr("تومان", "toman")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        PaywallHero(onClose) { dialog = "restore" }
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaywallPlan(tr("ماهیانه", "Monthly"), tr("۲۸۰٬۰۰۰", "280,000"), unit,
                    tr("اشتراک یک‌ماهه", "One-month plan"), !quarterly, Modifier.weight(1f)) { quarterly = false }
                PaywallPlan(tr("سه‌ماهه", "Three months"), tr("۵۶۰٬۰۰۰", "560,000"), unit,
                    tr("یک ماه رایگان", "One month free"), quarterly, Modifier.weight(1f)) { quarterly = true }
            }
            PressButton(onClick = { dialog = "subscribe" }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = PaywallAccent, contentColor = Color(0xFF102629))) {
                Text(tr("خرید اشتراک", "Subscribe"), style = MaterialTheme.typography.titleMedium)
            }
            Text(tr("پرداخت در این نسخه فعال نیست.", "Payments are not active in this version."),
                style = MaterialTheme.typography.bodySmall, color = PaywallMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            SnappPayBanner()
        }
    }
    if(dialog != null) {
        val restoring = dialog == "restore"
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if(restoring) tr("بازیابی خرید", "Restore purchase") else tr("خرید اشتراک پریمیوم", "Subscribe to Premium")) },
            text = { Text(if(restoring) tr("بازیابی خرید هنوز در این نسخه فعال نیست.", "Purchase restoration is not available in this version.")
                else tr("طرح $period · $price $unit\nپرداخت هنوز فعال نیست و مبلغی دریافت نمی‌شود.",
                    "$period · $price $unit\nPayments are not active yet. You won’t be charged.")) },
            confirmButton = { PressTextButton({ dialog = null }) { Text(tr("متوجه شدم", "Got it")) } }
        )
    }
}

@Composable private fun PaywallHero(onClose: () -> Unit, onRestore: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.premium_placeholder), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color(0xD500080B), .30f to Color(0x2900080B), .55f to Color.Transparent,
            .72f to PaywallBackground.copy(alpha = .65f), 1f to PaywallBackground
        )))
        Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                val close = tr("بستن", "Close")
                PressIconButton(onClose) {
                    Text("×", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = close })
                }
                PressTextButton(onRestore) { Text(tr("بازیابی", "Restore"), color = Color.White) }
            }
            Text(tr("ری‌دنس پریمیوم", "Reedance Premium"), modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(tr("با موسیقی، بی‌مرز یاد بگیر", "Learn without limits, with music"),
                modifier = Modifier.padding(start = 20.dp, top = 6.dp, end = 20.dp),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(180.dp))
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf(
                    tr("دسترسی به همهٔ آهنگ‌های پریمیوم", "Access all Premium songs"),
                    tr("آموزش کامل همهٔ آهنگ‌ها", "Full lessons for every song"),
                    tr("نشان‌گذاری نامحدود", "Unlimited saved items"),
                    tr("پخش در پس‌زمینه", "Background playback")
                ).forEach { benefit ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(UiIcons.Check, null, Modifier.size(18.dp), tint = PaywallAccent)
                        Text(benefit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable private fun PaywallPlan(title: String, price: String, unit: String, detail: String,
    selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(modifier.fillMaxHeight().heightIn(min = 128.dp).clip(shape)
        .selectable(selected, role = Role.RadioButton, onClick = onSelect), shape = shape,
        color = if(selected) Color(0xFF18282A) else Color(0xFF1C1F1E), contentColor = Color.White,
        border = BorderStroke(1.dp, if(selected) PaywallAccent else Color(0xFF636969))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = if(selected) PaywallAccent else Color.White,
                    style = MaterialTheme.typography.labelLarge)
                if(selected) AppIcon(UiIcons.CheckFilled, null, Modifier.size(16.dp), tint = PaywallAccent)
            }
            Text(price, color = if(selected) PaywallAccent else Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = PaywallMuted)
            Spacer(Modifier.weight(1f))
            Text(detail, color = if(selected) PaywallAccent else PaywallMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Informational only: this card does not initiate checkout or imply an active payment integration. */
@Composable private fun SnappPayBanner() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = Color(0xFF007DFA), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                    Image(painterResource(R.drawable.snapppay_wordmark), contentDescription = null,
                        modifier = Modifier.size(66.dp).padding(10.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("خرید قسطی با اسنپ‌پی", "Pay in installments with SnappPay"),
                        style = MaterialTheme.typography.titleMedium)
                    Text(tr("اطلاع‌رسانی", "Information only"),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(tr("پرداخت قسطی پریمیوم با اسنپ‌پی هنوز در برنامه فعال نیست.",
                "SnappPay installments for Premium are not active in the app yet."),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
