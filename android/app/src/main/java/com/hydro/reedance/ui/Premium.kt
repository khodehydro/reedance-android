package com.hydro.reedance.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

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

@Composable internal fun SubscriptionOffer(onClick: () -> Unit) {
    Surface(color = Color(0xFFFFE69A), contentColor = Color(0xFF493300), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(UiIcons.Crown, null, tint = Color(0xFF936000))
                Text(tr("آموزش کامل با اشتراک پریمیوم", "Unlock the complete lesson"), style = MaterialTheme.typography.titleMedium)
            }
            Text(tr("برای دسترسی به تمام محتوای آموزشی، امکانات پریمیوم را ببین.", "Explore Premium for full access to all learning content."))
            PressButton(onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF493300), contentColor = Color(0xFFFFE69A))) {
                Text(tr("مشاهدهٔ پریمیوم", "Explore Premium"))
            }
        }
    }
}

/** Warm, full-screen golden haze with slow drifting dust; kept behind cards and copy for legibility. */
@Composable internal fun PremiumBackdrop(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "premium-gold-dust")
    val phase by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "premium-dust-drift"
    )
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(Color(0xFFFFFDF4), Color(0xFFFFE87A), Color(0xFFFFF8D9), Color(0xFFEFCB45), Color(0xFFFFFCF2))
            )
        )
    ) {
        Canvas(Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            drawRect(Brush.radialGradient(
                listOf(Color(0xFFFFC928).copy(alpha = .54f), Color.Transparent),
                center = Offset(width * .12f, height * .27f), radius = width * .78f
            ))
            drawRect(Brush.radialGradient(
                listOf(Color.White.copy(alpha = .92f), Color.Transparent),
                center = Offset(width * .86f, height * .58f), radius = width * .8f
            ))
            drawRect(Brush.radialGradient(
                listOf(Color(0xFFFFD83D).copy(alpha = .52f), Color.Transparent),
                center = Offset(width * .92f, height * .06f), radius = width * .58f
            ))
            repeat(92) { index ->
                val seedX = ((index * 83) % 997) / 997f
                val seedY = ((index * 229) % 991) / 991f
                val y = (seedY + phase * (.18f + (index % 5) * .025f)) % 1f
                val x = (seedX + sin(phase * (2.0 * PI) + index) * .008f).toFloat()
                val radius = ((index % 3) + 1).dp.toPx() * if(index % 11 == 0) 1.45f else .72f
                val tint = if(index % 4 == 0) Color.White else Color(0xFFFFF8D6)
                val alpha = if(index % 9 == 0) .38f else .2f
                drawCircle(tint.copy(alpha = alpha), radius, Offset(x * width, y * height))
            }
        }
    }
}

@Composable internal fun PremiumScreen() {
    var checkoutInfo by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp + LocalOverlayBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PremiumHero()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("با پریمیوم چی به دست میاری؟", "What you get with Premium"), style = MaterialTheme.typography.titleLarge)
            PremiumBenefit(
                UiIcons.CrownFilled,
                tr("دسترسی به آهنگ‌های پریمیوم", "Access to Premium songs"),
                tr("همهٔ آهنگ‌های پریمیوم را برای یادگیری انتخاب کن.", "Choose any Premium song for your learning."),
                tr("محدودیت ندارد", "No limit")
            )
            PremiumBenefit(
                UiIcons.BookmarkFilled,
                tr("نشان‌گذاری نامحدود", "Unlimited saved items"),
                tr("لغت‌ها، نکته‌های گرامری و بقیهٔ موارد آموزشی را بدون سقف ذخیره کن.", "Save vocabulary, grammar notes and other learning items without a cap."),
                tr("رایگان: ۳۰ مورد از هر دسته", "Free: 30 per category")
            )
            PremiumBenefit(
                UiIcons.PlayFilled,
                tr("پخش در پس‌زمینه", "Background playback"),
                tr("موسیقی را هنگام استفاده از برنامه‌های دیگر هم ادامه بده.", "Keep listening while you use other apps."),
                tr("رایگان: با خروج از برنامه متوقف می‌شود", "Free: stops when you leave the app")
            )
        }
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = .84f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("همین حالا آمادهٔ خرید نیست", "Checkout isn’t available yet"), style = MaterialTheme.typography.titleMedium)
                Text(tr("این صفحه پیش‌نمایش امکانات و خرید لایسنس است. اتصال به مایکت هنوز انجام نشده و با زدن دکمه هیچ پرداختی انجام نمی‌شود.", "This page previews Premium and license purchase. Myket isn’t connected yet, and tapping the button will not charge you."), style = MaterialTheme.typography.bodyMedium)
            }
        }
        PressButton(
            onClick = { checkoutInfo = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF493300), contentColor = Color(0xFFFFE69A))
        ) {
            AppIcon(UiIcons.CrownFilled, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("خرید لایسنس پریمیوم", "Buy Premium license"), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            tr("قیمت و مدت اعتبار پس از اتصال خرید از مایکت نمایش داده می‌شود.", "Price and license duration will appear after Myket checkout is integrated."),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if(checkoutInfo) AlertDialog(
        onDismissRequest = { checkoutInfo = false },
        title = { Text(tr("خرید هنوز فعال نیست", "Purchase isn’t available yet")) },
        text = { Text(tr("اتصال به مایکت در این نسخه انجام نشده؛ هیچ مبلغی دریافت نمی‌شود و لایسنسی فعال نخواهد شد.", "Myket isn’t connected in this version. No payment will be taken and no license will be activated.")) },
        confirmButton = { PressTextButton({ checkoutInfo = false }) { Text(tr("متوجه شدم", "Got it")) } }
    )
}

@Composable private fun PremiumHero() {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF3A2C0C),
        contentColor = Color.White
    ) {
        Box {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 25.dp, y = (-34).dp).size(150.dp).clip(CircleShape).background(Color(0xFF99721A).copy(alpha = .42f)))
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = Color(0xFFFFDF80).copy(alpha = .18f), contentColor = Color(0xFFFFDF80)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(UiIcons.CrownFilled, null, Modifier.size(17.dp), Color(0xFFFFDF80))
                        Text(tr("ری‌دنس پریمیوم", "REEDANCE PREMIUM"), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(tr("یادگیری، بی‌وقفه و بی‌مرز", "Learn without limits"), style = MaterialTheme.typography.headlineSmall)
                Text(tr("با موسیقی بیشتر یاد بگیر؛ همهٔ امکانات پریمیوم، یک‌جا.", "Go further with music and unlock the full learning experience."), color = Color.White.copy(alpha = .86f))
            }
        }
    }
}

@Composable private fun PremiumBenefit(icon: Int, title: String, description: String, freeLimit: String) {
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Surface(Modifier.size(42.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFFFE69A).copy(alpha = .5f), contentColor = Color(0xFF765000)) {
                Box(contentAlignment = Alignment.Center) { AppIcon(icon, null, Modifier.size(22.dp), Color(0xFF765000)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(freeLimit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
