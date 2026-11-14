# Reedance Android — ری‌دنس

پیاده‌سازی **بومی Kotlin + Jetpack Compose** بر اساس [شاخهٔ مرجع React](https://github.com/khodehydro/reedance/tree/arena/e6a6294d-reedance). بدون WebView یا React Native.

**وضعیت: نسخهٔ اولیهٔ بومی / debug demo، نه نسخهٔ کامل یا آمادهٔ انتشار.** کد صفحه‌ها و جریان دموی محلی پیاده‌سازی شده؛ ساخت و اجرای Android در محیط فعلی به علت نبود JDK و Android SDK تأیید نشده است. برابری پیکسل‌به‌پیکسل و حرکت‌ها هنوز به آزمون روی شبیه‌ساز/دستگاه نیاز دارد. [وضعیت دقیق و کارهای باقی‌مانده](docs/STATUS.md).

## باز کردن و ساخت

پوشهٔ **`android/`** را در Android Studio باز کنید (JDK 17، Android SDK 35، Android SDK Build Tools؛ Gradle Wrapper همراه مخزن است).

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# روی شبیه‌ساز یا دستگاه API 26+:
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
```

در ویندوز از `gradlew.bat` استفاده کنید. خروجی مورد انتظار:
`android/app/build/outputs/apk/debug/app-debug.apk`.

مخزن **مایکت (`https://maven.myket.ir`) در اولویت** resolution پلاگین‌ها و dependencyها است؛ Google/Maven Central fallback هستند. آدرس توزیع خود Gradle در Wrapper از منبع رسمی است، نه یک مسیر ساختگی در مایکت. دانلود dependencies/SDK به دسترسی شبکهٔ محیط ساخت وابسته است.

Workflow در `.github/workflows/android.yml` برای build، lint، unit test و نگهداری APK debug آماده است؛ در این نشست اجرا نشده و به معنی CI سبز نیست.

## مسیر تجربهٔ دمو

1. یک اسپلش سیستم با لوگوی اصلی تا آماده‌شدن داده‌های محلی؛ صفحهٔ اسپلش دوم حذف شده است.
2. در اولین اجرا زبان رابط را English / فارسی انتخاب کنید. این انتخاب مستقل از زبان آموزش است.
3. خانه و کشف بدون جست‌وجو، فیلترهای کپسولی متحرک و انتخاب زبان با پرچم؛ استارتر پنج‌مرحله‌ای انگلیسی، اسپانیایی و کره‌ای با داده‌های همان مرجع.
4. `Midnight Avenue` → **نصب و باز کردن دموی محلی**. فایل همراه APK به فضای خصوصی اپ کپی و هش صوت بررسی می‌شود. این عملیات **دانلود اینترنتی یا نصب `.rdpack` امضاشده نیست**.
5. پلیر Media3، seek/±۱۰ ثانیه/repeat/mute، لیریک زمان‌دار و سه شیوهٔ ترجمه، ورق معنی واژه، گرامر/جدول، لغت، جمله و نشان‌کردن.
6. آزمون مستقل چهارقالبی؛ ورود پلیر را مکث می‌کند. غلط‌ها به لایتنر می‌روند؛ خانه/موعد و نشان‌ها در Room نگه داشته می‌شوند.
7. کتابخانه، مرور کارت/سؤال، تنظیمات زبان/تم/اندازهٔ متن/کاهش حرکت، امتیاز صرفاً محلی و اشتراک‌گذاری.

آهنگ‌های مشهور کاتالوگ فقط پیش‌نمایش‌اند؛ صوت/لیریک/خرید جعلی برایشان وجود ندارد. دمو بی‌کلام است و متن برای نمایش آموزش نوشته شده است. TTS فقط در صورت وجود صدای آفلاین دستگاه استفاده می‌شود و در غیر این صورت پیام متنی دارد.

## معماری و امنیت

- `android/app/src/main/java/com/hydro/reedance/ui`: Compose، Material 3، RTL/LTR و حرکت‌های ورود/دسته‌کارت.
- `data`: Room و DataStore؛ دادهٔ کاربر مستقل از فایل‌های صوت/درس.
- `domain`: نرمال‌سازی Unicode، تصحیح چهار نوع سؤال و زمان‌بندی لایتنر.
- `media`: MediaSessionService / Media3 و TTS آفلاین.
- `contract`: parser سخت‌گیر JSON، بررسی Ed25519 و primitive رمزگشایی AES-GCM **محدود به fixture کوچک**؛ هنوز installer تولیدی نیست.
- `contracts/v1`: کپی بدون تغییر قراردادها و نمونه‌های مشترک مرجع. کلید آزمایشی فقط در منابع unit test استفاده می‌شود.
- `src/debug/assets`: درس و صوت دمو؛ در main/release قرار ندارند.

طبق معماری v1، Supabase، حساب کاربری و همگام‌سازی ابری لازم نیست و اضافه نشده است. انتشار واقعی **عمداً در Gradle مسدود است**؛ [دروازهٔ انتشار](docs/RELEASE.md) را ببینید. این مدل DRM نیست.

## آزمون‌های قابل اجرا بدون Android SDK

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements-test.txt
.venv/bin/python scripts/test_assets.py
```

ده بررسی میزبان (از جمله بررسی وکتورهای Uicons، حذف جست‌وجو و یک اسپلش): schema درس، bootstrap، هش/مدت/زمان‌های صوت، سه مسیر استارتر و جدول‌ها، ارجاعات و چهار قالب سؤال، جدا بودن کلید/دمو از main و امضا/رمزگشایی fixture مرجع. **عبور این تست‌ها اثبات build یا اجرای کد Kotlin نیست.**

## مهاجرت دوبارهٔ محتوای مرجع

```bash
node scripts/migrate-reference.mjs /path/to/reedance-reference-checkout
```

JS فقط ابزار زمان توسعه برای تولید JSON است؛ داخل اپ اجرا نمی‌شود. [منبع و تفاوت‌های آگاهانه](docs/REFERENCE.md)، [معماری اصلی](docs/reference/README.md).

تغییرات رابط و کارایی اخیر: [UI_REFRESH.md](docs/UI_REFRESH.md). آیکن‌های بومی از همان Uicons Regular Rounded 3.3.1 وب هستند؛ اعتبار کوتاه ناشر در پایین کاتالوگ است و متن لایسنس در بخش درباره نمایش داده نمی‌شود.
