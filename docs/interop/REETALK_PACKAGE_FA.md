# قرارداد پیشنهادی ReeTalk — تحویل به ایجنت محتوا، Windows Studio و Android

**وضعیت: طرح پیاده‌سازی، نه فرمت فعال در Android فعلی.** اپ فعلی فقط دموهای محلی دارد؛ کاور، چت زمان‌دار نمایشی، پخش فایل محلی برای هر نوبت، ترجمهٔ جمله‌ای/واژه‌ای، نقش گوینده‌ها و تب‌های آموزش را دارد. صفحهٔ دانلود دمو به نصب‌کننده متصل نیست. پخش بازه‌ای، schema و نصب بستهٔ ReeTalk باید در هر دو برنامه پیاده و با fixture مشترک تأیید شوند. خروجی Windows قبل از آن صرفاً برای QA است و نباید به‌عنوان سازگار با APK فعلی منتشر شود.

## ۱. یک پسوند، دو پروفایل محتوا

| لایه | آهنگ Reedance | مکالمهٔ ReeTalk پیشنهادی |
|---|---|---|
| خروجی نهایی | `.rdpack` | `.rdpack`؛ همان ظرف، نه فرمت رمزنگاری تازه |
| نوع محتوا | `song`، ضمنی در manifest قدیمی | `contentType: "reetalk"` در manifest امضاشده و JSON داخلی |
| JSON اصلی ZIP | `lesson.json` | `dialogue.json` |
| صوت | `audio.mp3/m4a/wav` | همان نام‌ها؛ یک فایل ضبط‌شده و بازهٔ جداگانه برای هر نوبت |
| کاور | اختیاری | یک `cover.jpg/png/webp` الزامی برای هر مکالمه؛ کاور دسته جدا از بسته |
| واحد انتشار | یک آهنگ | یک مکالمه، نه یک دسته |

**پسوند، magic، AES-256-GCM، AAD، nonce، tag و روش امضای Ed25519 تغییر نمی‌کنند.** تعریف دقیق بایت‌ها، نام فایل نسخه‌دار، ZIP و قواعد کلید را از [PACKAGE_V1](../reference/PACKAGE_V1.md) و fixtureهای فعلی بگیرید. نوع محتوا از پسوند یا نام ZIP حدس زده نمی‌شود. manifest آهنگ v1 بدون تغییر می‌ماند؛ برای ReeTalk یک schema payload صریح با `manifestVersion: 2` و `contentType: "reetalk"` اضافه کنید؛ همان envelope و همان دامنهٔ امضا `REEDANCE-MANIFEST-V1\n` (نسخهٔ لایهٔ امضا، مستقل از نسخهٔ payload). فقط ترکیب‌های شناخته‌شده پذیرفته شوند. نصب‌کننده پس از اعتبارسنجی امضا، سیاست نوع محتوا و allowlist مخصوص همان نوع را انتخاب کند. کلید امضای خصوصی افشاشدهٔ Studio به هیچ وجه برای اعتماد Release پذیرفته نشود؛ میان QA و انتشار واقعی همان مرز فعلی حفظ شود.

## ۲. ورودی ایجنت محتوا: `.reetalkdraft.json`

یک JSON سادهٔ UTF-8 بدون BOM، بدون کلید تکراری، با فیلدهای دقیق زیر. ایجنت **هیچ** صوت، URL، مسیر فایل، کلید، زمان‌بندی یا هش تولید نمی‌کند. Studio منبع نهایی رسانه و تایم‌کد است.

| کلید | قرارداد پیشنهادی |
|---|---|
| `draftSchemaVersion`, `contentType` | دقیقاً `1` و `"reetalk"` |
| `dialogueId`, `categoryId` | slug کوچک و پایدار، `^[a-z0-9]+(?:-[a-z0-9]+)*$`، حداکثر ۶۴؛ شناسهٔ مکالمه در namespace ری‌تاک یکتا |
| `targetLanguage`, `locale` | کد زبان مقصد مثل `tr` و locale مثل `tr-TR`؛ با زبان متن یکی باشند. برای انتشار در هر زبان، `dialogueId` مستقل |
| `level` | یکی از `A1`, `A2`, `B1`, `B2`, `C1`, `C2`؛ سطح پیچیدگی زبان، نه دسته‌بندی موضوع |
| `title`, `objective` | هرکدام `{ "fa": "…", "en": "…" }`؛ هدف باید قابل سنجش باشد |
| `speakers` | دقیقاً دو عضو `{id, name, gender}`؛ `id` ثابت، `gender` دقیقاً `woman` یا `man`؛ ترکیب دو زن یا دو مرد هم مجاز است. نام از جنسیت استنباط نشود |
| `turns` | ۱۰ تا ۱۲۰ نوبت؛ حداقل ۵ نوبت برای هر گوینده؛ هر عضو `{id, speakerId, text, translation, tokens}`؛ `translation` دارای `fa/en` و توکن `{surface, gloss}` با `gloss.fa/en`. ترتیب آرایه ترتیب مکالمه است |
| `learning` | `{words, grammar, phrases, questions}`؛ هر بخش برای درس منتشرشدنی غیرخالی. تعریف دقیق پایین |

### شکل فیلدهای آموزشی

- `words[]`: `{id, text, pronunciation, meaning, example}`؛ `pronunciation` شامل `faReading` و `ipa`؛ `meaning` محلی‌سازی‌شده؛ `example` دارای `text` و `translation.fa/en`.
- `grammar[]`: `{id, title, explanation, example, takeaway, tables}`؛ عنوان، توضیح و جمع‌بندی `fa/en`؛ حداقل یک جدول. جدول `{title, columns, rows}`؛ `columns` آرایهٔ `fa/en`، `rows` آرایهٔ ردیف‌های هم‌عرض. هر سلول دقیقاً یکی از `{text}` برای متن زبان مقصد یا `{fa,en}` برای محتوای محلی‌سازی‌شده. Android این ساختار را به مدل کارت و جدول فعلی نگاشت کند؛ جدول در همان کارت و در صورت نیاز با اسکرول افقی.
- `phrases[]`: `{id, text, pronunciation, meaning, context, example}`؛ عبارت یا اصطلاح واقعیِ همین موقعیت، تلفظ نوشتاری، معنی، کاربرد و مثال. جدول مرور لغات و اصطلاحات از همین داده‌ها ساخته شود، نه از نسخهٔ تکراریِ مستقل.
- `questions[]`: `{id, stage, type, prompt, options, correctOptionId, explanation, evidenceTurnIds}`؛ در v1 فقط `type: "multiple_choice"`، `stage: "gist" | "detail" | "review"`، ۲ تا ۶ گزینهٔ `{id, text: {fa,en}}`، یک جواب درست، توضیح `fa/en` و حداقل یک شناسهٔ نوبت معتبر. `gist` برداشت کلی و `detail` جزئیات شنیداری؛ دست‌کم یکی از هر کدام. دیکته، تصویر به‌عنوان گزینه، ضبط زبان‌آموز و آزمون با صوت تازه، توسعهٔ بعدی‌اند و در v1 دادهٔ تعریف‌نشده صادر نشود.

### نمونهٔ کامل پیش‌نویس (ترکی)

```json
{
  "draftSchemaVersion": 1,
  "contentType": "reetalk",
  "dialogueId": "at-the-station-tr",
  "categoryId": "travel",
  "targetLanguage": "tr",
  "locale": "tr-TR",
  "level": "A1",
  "title": {"fa":"در ایستگاه","en":"At the station"},
  "objective": {"fa":"ساعت حرکت و شمارهٔ سکو را از روی صدا تشخیص بده.","en":"Identify the departure time and platform by listening."},
  "speakers": [
    {"id":"ece","name":"Ece","gender":"woman"},
    {"id":"mert","name":"Mert","gender":"man"}
  ],
  "turns": [
    {"id":"t01","speakerId":"ece","text":"Affedersiniz.","translation":{"fa":"ببخشید.","en":"Excuse me."},"tokens":[{"surface":"Affedersiniz.","gloss":{"fa":"ببخشید","en":"excuse me"}}]},
    {"id":"t02","speakerId":"mert","text":"Buyurun.","translation":{"fa":"بفرمایید.","en":"How can I help?"},"tokens":[{"surface":"Buyurun.","gloss":{"fa":"بفرمایید","en":"go ahead"}}]},
    {"id":"t03","speakerId":"ece","text":"Tren ne zaman kalkıyor?","translation":{"fa":"قطار کی حرکت می‌کند؟","en":"When does the train leave?"},"tokens":[{"surface":"Tren","gloss":{"fa":"قطار","en":"train"}},{"surface":"ne","gloss":{"fa":"چه","en":"what"}},{"surface":"zaman","gloss":{"fa":"زمان","en":"time"}},{"surface":"kalkıyor?","gloss":{"fa":"حرکت می‌کند؟","en":"leaves?"}}]},
    {"id":"t04","speakerId":"mert","text":"Saat üçte.","translation":{"fa":"ساعت سه.","en":"At three o’clock."},"tokens":[{"surface":"Saat","gloss":{"fa":"ساعت","en":"hour"}},{"surface":"üçte.","gloss":{"fa":"در سه","en":"at three"}}]},
    {"id":"t05","speakerId":"ece","text":"Hangi perondan?","translation":{"fa":"از کدام سکو؟","en":"From which platform?"},"tokens":[{"surface":"Hangi","gloss":{"fa":"کدام","en":"which"}},{"surface":"perondan?","gloss":{"fa":"از سکو؟","en":"from the platform?"}}]},
    {"id":"t06","speakerId":"mert","text":"İkinci perondan.","translation":{"fa":"از سکوی دوم.","en":"From platform two."},"tokens":[{"surface":"İkinci","gloss":{"fa":"دوم","en":"second"}},{"surface":"perondan.","gloss":{"fa":"از سکو","en":"from the platform"}}]},
    {"id":"t07","speakerId":"ece","text":"Asansör var mı?","translation":{"fa":"آسانسور هست؟","en":"Is there a lift?"},"tokens":[{"surface":"Asansör","gloss":{"fa":"آسانسور","en":"lift"}},{"surface":"var","gloss":{"fa":"هست","en":"there is"}},{"surface":"mı?","gloss":{"fa":"آیا؟","en":"question particle"}}]},
    {"id":"t08","speakerId":"mert","text":"Evet, sağda.","translation":{"fa":"بله، سمت راست است.","en":"Yes, on the right."},"tokens":[{"surface":"Evet,","gloss":{"fa":"بله","en":"yes"}},{"surface":"sağda.","gloss":{"fa":"سمت راست","en":"on the right"}}]},
    {"id":"t09","speakerId":"ece","text":"Teşekkür ederim.","translation":{"fa":"ممنونم.","en":"Thank you."},"tokens":[{"surface":"Teşekkür","gloss":{"fa":"تشکر","en":"thanks"}},{"surface":"ederim.","gloss":{"fa":"می‌کنم","en":"I give"}}]},
    {"id":"t10","speakerId":"mert","text":"Rica ederim.","translation":{"fa":"خواهش می‌کنم.","en":"You’re welcome."},"tokens":[{"surface":"Rica","gloss":{"fa":"خواهش","en":"request"}},{"surface":"ederim.","gloss":{"fa":"می‌کنم","en":"I make"}}]}
  ],
  "learning": {
    "words": [{"id":"tren","text":"tren","pronunciation":{"faReading":"تْرِن","ipa":"/tɾen/"},"meaning":{"fa":"قطار","en":"train"},"example":{"text":"Tren ne zaman kalkıyor?","translation":{"fa":"قطار کی حرکت می‌کند؟","en":"When does the train leave?"}}}],
    "grammar": [{"id":"time-da","title":{"fa":"ساعت وقوع با ‎-DA","en":"Time with -DA"},"explanation":{"fa":"برای بیان زمان وقوع، به عددِ ساعت پسوند مکانی اضافه می‌شود. پس از ç شکل با t می‌آید.","en":"Add the locative ending to the hour. After voiceless ç, the ending starts with t."},"example":{"text":"Saat üçte.","translation":{"fa":"ساعت سه.","en":"At three o’clock."}},"takeaway":{"fa":"سه + te می‌شود üçte.","en":"üç + te becomes üçte."},"tables":[{"title":{"fa":"ساعت وقوع","en":"Time of an event"},"columns":[{"fa":"ترکی","en":"Turkish"},{"fa":"معنی","en":"Meaning"}],"rows":[[{"text":"üçte"},{"fa":"ساعت سه","en":"at three"}],[{"text":"ikide"},{"fa":"ساعت دو","en":"at two"}]]}]}],
    "phrases": [{"id":"excuse-me","text":"Affedersiniz.","pronunciation":{"faReading":"آفِدِرسینیز","ipa":"/afːedeɾsiniz/"},"meaning":{"fa":"ببخشید","en":"Excuse me"},"context":{"fa":"برای جلب توجه مؤدبانه.","en":"Use it to get someone’s attention politely."},"example":{"text":"Affedersiniz, hangi perondan?","translation":{"fa":"ببخشید، از کدام سکو؟","en":"Excuse me, from which platform?"}}}],
    "questions": [
      {"id":"topic","stage":"gist","type":"multiple_choice","prompt":{"fa":"موضوع مکالمه چیست؟","en":"What is the conversation about?"},"options":[{"id":"train","text":{"fa":"گرفتن اطلاعات حرکت قطار","en":"Getting train departure information"}},{"id":"cafe","text":{"fa":"سفارش نوشیدنی","en":"Ordering a drink"}}],"correctOptionId":"train","explanation":{"fa":"گوینده دربارهٔ حرکت قطار و سکو می‌پرسد.","en":"The speaker asks about the train departure and platform."},"evidenceTurnIds":["t03","t05"]},
      {"id":"platform","stage":"detail","type":"multiple_choice","prompt":{"fa":"قطار از کدام سکو حرکت می‌کند؟","en":"Which platform does the train leave from?"},"options":[{"id":"one","text":{"fa":"سکوی اول","en":"Platform one"}},{"id":"two","text":{"fa":"سکوی دوم","en":"Platform two"}}],"correctOptionId":"two","explanation":{"fa":"İkinci یعنی دوم.","en":"İkinci means second."},"evidenceTurnIds":["t06"]}
    ]
  }
}
```

**دستور تولید محتوا:** مکالمهٔ طبیعی با آغاز، هدف و پایان تولید کن؛ جمله‌ها برای پر کردن تعداد تکراری نشوند. سطح A1 جمله‌های کوتاه و اطلاعات مستقیم؛ سطح‌های بالاتر تنوع سرعت، اصلاح اطلاعات و استنباط. توکن‌ها را با فاصلهٔ تک به همان متن بازسازی کن، ترتیب و نشانه‌گذاری را نگه دار، معنی در بافت بده و اصطلاح را تحت‌اللفظی به‌عنوان ترجمهٔ جمله ننویس. اطلاعات پاسخ را روکش کاور لو نده. شناسه‌ها را بین بازبینی‌ها حفظ کن؛ شناسهٔ حذف‌شده برای معنای دیگری استفاده نشود. متن و صوت تولیدی/وارداتی باید حق انتشار داشته باشند.

## ۳. مسئولیت Windows Studio و JSON نهایی

۱. پروژهٔ `Song` و `ReeTalk` را جدا کن؛ فایل خام ری‌تاک را به ابزار هم‌ترازی لیریک آهنگ نفرست. ابتدا ساختار، ارجاع شناسه‌ها و تعداد نوبت‌ها را اعتبارسنجی کن. فایل خام کد اجرایی نیست.

۲. اپراتور کاور و **صوت ضبط‌شدهٔ طبیعی دو گوینده** را محلی وارد کند. یک فایل کامل یا کلیپ‌های جدا وارد شوند، اما خروجی نهایی دقیقاً یک ترک باشد. در حالت کلیپ، کلیپ‌ها را به ترتیب نوبت ترکیب کن؛ اگر تبدیل فرمت رخ داد، زمان را از صوت خروجی بسنج. **نه TTS و نه زمان تخمینی متن.** برای ترک کامل، ویراستار waveform با پیش‌شنیدن هر نوبت و ثبت دقیق شروع/پایان فراهم کن. سکوت میان نوبت‌ها مجاز است؛ نوبت‌ها هم‌پوشانی نداشته باشند. کاور پیشنهادی نسبت ۵:۳، بدون متن ضروری در حاشیه.

۳. `dialogue.json` از همان دادهٔ پیش‌نویس تولید می‌شود با این تبدیل‌های دقیق:

- `draftSchemaVersion` حذف، `schemaVersion: 1` و `contentVersionCode` اضافه شوند. سایر فیلدهای پیش‌نویس حفظ شوند.
- به **هر** عضو `turns`، اعداد صحیح `startMs` و `endMs` اضافه شوند. بازه `[startMs,endMs)` نسبت به فایل صوتی نهایی، مبدأ صفر. `0 <= startMs < endMs <= media.audio.durationMs` و `previous.endMs <= current.startMs`.
- `media: {audio, cover}` الزامی. `audio: {path, mimeType, durationMs, sha256}`؛ `cover: {path, mimeType, sha256}`. مسیر فقط basename با نام مجاز، هش SHA-256 lowercase از بایت‌های واقعی. جفت‌های مجاز: `audio.mp3`/`audio/mpeg`، `audio.m4a`/`audio/mp4`، `audio.wav`/`audio/wav`؛ `cover.jpg`/`image/jpeg`، `cover.png`/`image/png`، `cover.webp`/`image/webp`. MIME و رسانهٔ قابل Decode را بررسی کن.
- JSON نهایی حداکثر ۴ MiB، کاور ۸ MiB، صوت ۲۴۰ MiB، مجموع اندازهٔ استخراج‌شده حداکثر ۲۵۲ MiB؛ ظرف و سقف طول رمز مانند قرارداد موجود. ZIP ری‌تاک دقیقاً سه ورودی `dialogue.json`، یک `audio.*` و یک `cover.*` دارد. `lesson.json`، `story.*`، مسیرها، متادیتای ZIP ممنوع و رسانهٔ ارجاع‌نشده رد شوند.

۴. export اتمیک، nonce تازه، تولید manifest با همان envelope. payload پیشنهادی دقیقاً این فیلدها را دارد: `manifestVersion: 2`, `contentType: "reetalk"`, `dialogueId`, `targetLanguage`, `dialogueSchemaVersion: 1`, `contentVersionCode`, `package: {fileName, sizeBytes, sha256, encryptionKeyId}`. شمارهٔ نسخه برای هر `dialogueId` صعودی و تکرارنشدنی، basename نسخه‌دار و immutable؛ اول فایل پکیج را منتشر کن، بعد `latest.json`. تطابق نوع و شناسه، نسخهٔ schema، زبان، نسخهٔ محتوا و شناسهٔ رمزنگاری در لایه‌های مربوطه الزامی است. schema آهنگ v1 و manifestهای جاری تغییر نکنند.

## ۴. کاتالوگ، نصب و رفتار Android

- کاتالوگ ReeTalk جدا از آهنگ‌ها. `categories[]`: `{id,title,coverUrl,publishedAt}`؛ `dialogues[]`: `{id,categoryId,title,targetLanguage,level,coverUrl,publishedAt,manifestUrl,minAppVersionCode}`. عنوان `fa/en`، تاریخ UTC ISO-8601، URL محدود به HTTPS و origin مجاز. ابتدا bootstrap قابل انتشار همراه اپ یا کاتالوگ با احراز اصالت مشخص؛ کاتالوگ غیرامضاشده حق نصب نمی‌دهد. دسته‌ها با تاریخ انتشار مرتب شوند، هوم دو دستهٔ جدید، کارت‌ها فهرست همان دسته و «دیدن همه» کل دسته‌ها را باز کند. کاور کاتالوگ برای پیش از نصب است و کاور داخلی برای استفادهٔ آفلاین؛ نبود کاور شبکه نباید صفحه را خراب کند.
- پایپ‌لاین: catalog → manifest امضاشده → هم‌خوانی مسیر و نوع → دریافت فایل و SHA-256 → GCM → ZIP محدود به پروفایل → JSON و قواعد معنایی/رسانه → فعال‌سازی اتمیک. نوع را به دادهٔ نامطمئن در ZIP واگذار نکن. مسیر نصب و anti-rollback را با کلید `(contentType, contentId)` مجزا نگه دار؛ بستهٔ قبلی سالم بعد از شکست به‌روزرسانی قابل پخش بماند. بدون auto-install یا تغییر تم هنگام ورود. نصب و مطالعه پس از دانلود آفلاین، لغو امن و بدون اعلان موفقیت اضافی.
- مدل نگاشت: `speakerId` به index نمایشی، `gender` به آواتار، `translation` و `tokens` به حالت نمایش، کاور پیش از شروع و محوشدن به بالا، `learning` به کارت‌های لغات/گرامر/اصطلاحات/سؤالات. `turn.id` کلید ردیف است، نه متن یا index. پنل تنظیمات دستگاه و حالت هر گوینده از پکیج نمی‌آیند؛ برای هر گفت‌وگو با `dialogueId` پایدار ذخیره شوند. جایگزینی state دموها با مخزن بسته باید مسیر Home/Learn و برگشت را حفظ کند.
- پخش بازه‌ای با یک player متعلق به صفحه: بازپخش نوبت فقط همان بازه و بدون پریدن به بعد، پخش پیوسته بر اساس timeline، مکث/ادامه بدون بازنشانی، سرعت واحد، توقف هنگام خروج یا پس‌زمینه، جلوگیری از هم‌زمانی با آهنگ. حالت صدا/پیام مستقل برای هر نفر؛ نوبت بی‌صدا در زمان خودش جلو می‌رود. پیام مخفی هر نفر به `...` با همان کلید ردیف تبدیل شود؛ مخفی‌کردن کل چت فقط نمایش را تغییر دهد. موج نشانهٔ پخش است، نه نمودار اندازه‌گیری فایل. فایل صوتی ناموجود/خراب به خطای قابل‌فهم برسد، نه TTS و نه پخش نمایشی به‌جای صوت.
- مسیر آموزشی هدف: ابتدا **تمرین شنیداری** بدون متن/ترجمه و سپس **مرور چت**. در `gist/detail` گزینه قبل از دیدن متن پاسخ داده شود؛ بعد از پاسخ توضیح و بازپخش نوبت‌های شاهد ارائه شود. نقش‌آفرینی و لغات/گرامر بعد از تلاش کاربر در دسترس‌اند. این مسیر آموزشی کامل نیز هنوز پیاده نشده و از تب سؤال دموی فعلی جداست.

## ۵. تحویل و آزمون بین دو تیم

1. روی قرارداد توافق کنید؛ schemaهای مستقل برای draft، `dialogue.json` و payload نسخهٔ ۲ بسازید، با `additionalProperties: false` و محدودیت طول/تعداد. قواعد معنایی (شناسه یکتا، اتصال ارجاع‌ها، ترتیب/حدود بازه‌ها، عرض سطر جدول، تطابق توکن و متن، حداقل پنج نوبت هر نفر) را علاوه بر schema در هر دو سمت enforce کنید.
2. Windows یک fixture ری‌تاک **آزمایشی** شامل پیش‌نویس بدون زمان، JSON زمان‌دار کامل، صوت و کاور با مجوز، `.rdpack` و `latest.json` با کلید مخصوص تست تولید کند. Android آن را نصب، آفلاین پخش و شیوهٔ نشان‌دادن جمله/واژه/سؤال را تأیید کند. fixture آهنگ موجود نیز بدون تغییر نصب شود.
3. تست منفی مشترک: نوع یا نسخهٔ ناشناخته، جابه‌جایی آهنگ/ری‌تاک، هش یا امضای نامعتبر، nonce/tag دستکاری‌شده، نسخهٔ قدیمی، ورودی ZIP اضافه یا تکراری، رسانهٔ ناسازگار، بازهٔ هم‌پوشان یا خارج از مدت، گوینده/نوبت ناشناخته، کلید JSON تکراری و نبود حداقل نوبت. هیچ شکست نباید محتوای فعال سالم را حذف کند.
4. معیار پذیرش دستگاه: باز و بسته‌کردن اپ، لغو/تلاش دوباره، پخش آفلاین، تغییر سرعت، توقف روی نوبتِ انتخاب‌شده به‌خصوص انتهای بازه، نقش‌آفرینی و بازگشت بدون پخش باقی‌مانده، سؤال بدون لو رفتن متن. بعد از قبولی و انتشار APK سازگار، `minAppVersionCode` در انتشار عمومی را تنظیم کنید.

### نشان پریمیوم در کاتالوگ

برای هر دسته و هر گفت‌وگو یک فیلد اختیاری `access` با مقدار `free` یا `paid` در متادیتای کاتالوگ در نظر بگیرید (پیش‌فرض `free`). دستهٔ `paid` در Home و فهرست دسته‌ها تاج دارد. گفت‌وگو تاج دارد اگر خودش `paid` باشد یا به دستهٔ `paid` تعلق داشته باشد؛ یک گفت‌وگوی پولی در دستهٔ رایگان، کل دسته را پولی نمی‌کند. تاج نشان رابط کاربری است؛ کنترل واقعی اشتراک باید جدا از این متادیتای نمایشی و بر اساس سیاست معتبر نصب و دسترسی اعمال شود. دموهای محلی صرفاً نمایش نشان را نمونه‌سازی می‌کنند.
