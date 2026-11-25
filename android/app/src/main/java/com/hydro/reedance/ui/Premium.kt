package com.hydro.reedance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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

/** Never compose hidden text/actions: blur is presentation, not access control.
 * Placeholder bars also work below API 31, where Compose's blur is unsupported. */
@Composable internal fun PremiumPreview() {
    Panel {
        Column(Modifier.fillMaxWidth().then(if(android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(7.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(.8f, 1f, .65f).forEach { width ->
                Box(Modifier.fillMaxWidth(width).height(16.dp).clip(DetailShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)))
            }
        }
        Text(tr("ادامهٔ آموزش با اشتراک", "More learning with a subscription"), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun PremiumScreen(onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, contentColor = colors.onBackground, modifier = Modifier.fillMaxSize()) {
        PremiumScreenContent(onClose)
    }
}

@Composable private fun PremiumScreenContent(onClose: () -> Unit) {
    var quarterly by rememberSaveable { mutableStateOf(true) }
    var showSubscribeDialog by remember { mutableStateOf(false) }
    val period = if(quarterly) tr("سه‌ماهه", "Three months") else tr("ماهیانه", "Monthly")
    val price = if(quarterly) tr("۵۶۰٬۰۰۰", "560,000") else tr("۲۸۰٬۰۰۰", "280,000")
    val unit = tr("تومان", "toman")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        PaywallHero(onClose)
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaywallPlan(tr("ماهیانه", "Monthly"), tr("۲۸۰٬۰۰۰", "280,000"), unit,
                    tr("اشتراک یک‌ماهه", "One-month plan"), !quarterly, Modifier.weight(1f)) { quarterly = false }
                PaywallPlan(tr("سه‌ماهه", "Three months"), tr("۵۶۰٬۰۰۰", "560,000"), unit,
                    tr("یک ماه رایگان", "One month free"), quarterly, Modifier.weight(1f),
                    savings = tr("۲۸۰٬۰۰۰ تومان صرفه‌جویی", "Save 280,000 T")) { quarterly = true }
            }
            PressButton(onClick = { showSubscribeDialog = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)) {
                Text(tr("خرید اشتراک", "Subscribe"), style = MaterialTheme.typography.titleMedium)
            }
            Text(tr("پرداخت در این نسخه فعال نیست.", "Payments are not active in this version."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            SnappPayBanner()
        }
    }
    if(showSubscribeDialog) {
        AlertDialog(
            onDismissRequest = { showSubscribeDialog = false },
            title = { Text(tr("خرید اشتراک پریمیوم", "Subscribe to Premium")) },
            text = { Text(tr("طرح $period · $price $unit\nپرداخت هنوز فعال نیست و مبلغی دریافت نمی‌شود.",
                    "$period · $price $unit\nPayments are not active yet. You won’t be charged.")) },
            confirmButton = { PressTextButton({ showSubscribeDialog = false }) { Text(tr("متوجه شدم", "Got it")) } }
        )
    }
}

@Composable private fun PaywallHero(onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background = colors.background
    Box(Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.premium_placeholder), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to background, .24f to background.copy(alpha = .88f), .52f to Color.Transparent,
            .69f to background.copy(alpha = .78f), .84f to background, 1f to background
        )))
        Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                val close = tr("بستن", "Close")
                PressIconButton(onClose) {
                    Text("×", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = close })
                }
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
                        AppIcon(UiIcons.Check, null, Modifier.size(18.dp), tint = colors.primary)
                        Text(benefit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable private fun PaywallPlan(title: String, price: String, unit: String, detail: String,
    selected: Boolean, modifier: Modifier, savings: String? = null, onSelect: () -> Unit) {
    val shape = GroupedCardShape
    val colors = MaterialTheme.colorScheme
    val ink = if(selected) colors.primary else colors.onSurface
    Surface(modifier.fillMaxHeight().heightIn(min = 128.dp).clip(shape)
        .selectable(selected, role = Role.RadioButton, onClick = onSelect), shape = shape,
        color = Color.Transparent, contentColor = ink,
        border = BorderStroke(1.dp, if(selected) colors.primary else colors.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = ink,
                    style = MaterialTheme.typography.labelLarge)
                if(selected) AppIcon(UiIcons.CheckFilled, null, Modifier.size(16.dp), tint = colors.primary)
            }
            Text(price, color = ink, style = MaterialTheme.typography.headlineSmall)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            if(savings != null) {
                Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                    Text(savings, Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(detail, color = if(selected) colors.primary else colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Informational only: this card does not initiate checkout or imply an active payment integration. */
@Composable private fun SnappPayBanner() {
    Surface(Modifier.fillMaxWidth(), shape = GroupedCardShape,
        color = Color(0xFF007DFA), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = NestedTileShape, color = Color.White) {
                    Image(painterResource(R.drawable.snapppay_wordmark), contentDescription = null,
                        modifier = Modifier.size(66.dp).padding(10.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("خرید قسطی با اسنپ‌پی", "Pay in installments with SnappPay"),
                        style = MaterialTheme.typography.titleMedium)
                    Text(tr("هزینهٔ خرید را به‌جای پرداخت یکجا، در چند قسط بپرداز.",
                        "Spread the cost of a purchase across installments instead of paying all at once."),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
