package com.hydro.reedance.domain

object ReminderPolicy {
    private val milestones = listOf(1L, 2L, 3L, 6L, 14L)
    const val UNIT_MILLIS = 12 * 60 * 60 * 1000L
    fun stage(elapsed: Long): Long {
        val units = elapsed / UNIT_MILLIS
        if(elapsed < UNIT_MILLIS) return -1L
        val index = milestones.indexOfLast { units >= it }
        return if(index < 4) index.toLong() else 4L + (units - 14L) / 14L
    }
    fun eligible(now: Long, lastVisit: Long, lastReminder: Long): Boolean =
        lastVisit > 0L && now >= lastVisit && now >= lastReminder &&
            stage(now - lastVisit) > stage((lastReminder - lastVisit).coerceAtLeast(0L))

    fun nextAt(lastVisit: Long, lastReminder: Long): Long {
        val sentStage = stage((lastReminder - lastVisit).coerceAtLeast(0L))
        val nextUnits = if(sentStage < 4L) milestones[(sentStage + 1).toInt()] else 14L * (sentStage - 2L)
        return lastVisit + nextUnits * UNIT_MILLIS
    }
    fun message(stage: Long, due: Int, steps: Int, fa: Boolean): String {
        val invitation = when(stage) {
            0L -> if(fa) "ریتم یادگیریت منتظرته؛ با یک تمرین کوتاه برگرد." else "Your learning rhythm is waiting. Come back for a little practice."
            1L -> if(fa) "یک آهنگ می‌تونه شروع دوبارهٔ قشنگی باشه؛ امروز چی یاد بگیریم؟" else "A song is a lovely way to start again. What shall we learn today?"
            2L -> if(fa) "قدم‌های کوچک هم ارزشمندند؛ بیا یک کلمهٔ تازه به مسیرت اضافه کنیم." else "Small steps count. Let's add one new word to your journey."
            3L -> if(fa) "دلمون برای ریتمت تنگ شده؛ هر وقت آماده‌ای، از یک آهنگ شروع کن." else "We've missed your rhythm. Whenever you're ready, start with a song."
            else -> if(fa) "برای برگشتن هیچ‌وقت دیر نیست؛ مسیر یادگیریت همین‌جا منتظرته." else "It's never too late to return. Your learning journey is right here."
        }
        val activity = when {
            due > 0 -> if(fa) "$due واژه هم آمادهٔ مرور هستند." else "$due words are ready for review, too."
            steps in 1..14 -> if(fa) "می‌تونی قدم بعدی استارترت رو ادامه بدی." else "Your next Starter step is ready when you are."
            else -> ""
        }
        return listOf(invitation, activity).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
