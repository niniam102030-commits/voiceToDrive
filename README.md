# Audio → Google Drive (بدون سرور)

اپلیکیشن اندروید که صدا را ضبط می‌کند (یا فایل صوتی موجود را با SAF انتخاب می‌کند)، آن را با FFmpeg به یک **MP3 با بیتریت ۶۴ kbps** تبدیل می‌کند و مستقیماً از روی گوشی با **Google Drive REST API v3** روی درایو آپلود می‌کند. هیچ بک‌اند یا سروری در کار نیست؛ ورود به گوگل با **OAuth 2.0 (AppAuth)** و توکن refresh انجام می‌شود و کلیدهای محرمانه روی خود دستگاه و به‌صورت رمزنگاری‌شده نگهداری می‌شوند.

> نکته: روی سیستم شما Java / Android SDK / Android Studio نصب نیست، بنابراین بیلد فقط داخل **GitHub Actions** انجام می‌شود. خروجی بیلد، خودِ فایل `app-debug.apk` و همچنین `keystore.jks` است.

---

## ۱. ساختار پروژه

```
.github/workflows/android-build.yml   ← پایپ‌لاین ساخت APK
settings.gradle.kts                  ← مخازن google() و mavenCentral()
build.gradle.kts                     ← نسخهٔ پلاگین‌ها: AGP 8.7.3، Kotlin 2.0.21، KSP 2.0.21-1.0.28
gradle.properties                    ← تنظیمات JVM/AndroidX
app/build.gradle.kts                 ← تنظیمات ماژول، امضا (signing)، لیست وابستگی‌ها
app/proguard-rules.pro
app/src/main/AndroidManifest.xml      ← دسترسی‌ها + intent-filter مربوط به AppAuth
app/src/main/java/com/personal/audioapp/
├── AudioApp.kt                       ← Application + کانال نوتیفیکیشن
├── data/                             ← Room: FileTask، DAO، AppDatabase، SettingsRepository
├── auth/AuthRepository.kt            ← ورود گوگل، نگهداری/تمدید توکن
├── network/DriveApi.kt, DriveClient.kt← فراخوانی‌های Retrofit روی Drive REST v3
├── service/AudioRecordService.kt     ← Foreground Service + MediaRecorder
├── util/                             ← FileUtils، Notifications، JwtUtils
├── work/                             ← CompressionWorker، UploadWorker، WorkScheduler
└── ui/                               ← Compose: HomeScreen، SettingsScreen، AppViewModel، MainActivity
```

نسخه‌های مهم قفل‌شده: AGP `8.7.3`، Kotlin `2.0.21`، KSP `2.0.21-1.0.28`، Compose BOM `2024.10.01`، Room `2.6.1`، WorkManager `2.9.1`، AppAuth `0.11.1`، Retrofit/Gson `2.11.0`، `androidx.security:security-crypto:1.1.0-alpha06` و موتور صدا:

```kotlin
implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7")
```

بستهٔ اصلی `com.arthenica` از Maven Central حذف شده است، بنابراین از فورک نگهداری‌شدهٔ بالا استفاده می‌شود (پکیج جاوا همان `com.arthenica.ffmpegkit` باقی مانده است).

---

## ۲. گرفتن APK از GitHub Actions

1. یک ریپازیتوری روی GitHub بسازید و کد این پوشه را push کنید:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<USER>/<REPO>.git
   git push -u origin main
   ```
2. وارد تب **Actions** شوید؛ workflow با نام **Android Build** خودکار شروع می‌شود (با `workflow_dispatch` هم می‌توانید دستی اجرا کنید).
3. کاری که runner انجام می‌دهد، به‌ترتیب:
   - نصب **JDK 17 (temurin)** و Android SDK (`platforms;android-35`، `build-tools;35.0.0`)
   - ساخت Gradle wrapper با نسخهٔ **8.9** اگر فایل `gradlew` وجود نداشته باشد (در یک پروژهٔ خالی، تا به نسخهٔ Gradle نصب‌شده روی runner حساس نباشد)
   - برگرداندن `app/keystore.jks` از کش Actions (`actions/cache@v4`) و در صورت سرد بودن کش، ساخت آن با `keytool`
     (alias: `my-alias` / گذرواژهٔ store و key: `123456` / اعتبار: `10000` روز)
   - اجرای `./gradlew signingReport` و چاپ **SHA-1** در لاگ
   - اجرای `./gradlew assembleDebug --stacktrace`
4. در پایان بیلد، از پایین صفحهٔ اجرا دو artifact دانلود کنید:
   - **app-debug-apk** → همان `app-debug.apk` (این را روی گوشی نصب کنید)
   - **keystore** → فایل `app/keystore.jks`

> **پایداری امضا (مهم):** امضای APK از `app/keystore.jks` می‌آید و مرحلهٔ `actions/cache@v4` این فایل را بین اجراهای CI نگه می‌دارد؛ پس SHA-1 ثابت می‌ماند و نیازی به کار اضافه نیست. اگر کش Actions منقضی شود (بعد از بی‌استفاده‌ماندن طولانی)، کافی است فایل artifact `keystore` را دانلود کنید، در `app/keystore.jks` بگذارید و یک‌بار commit کنید — در `.gitignore` برای همین یک فایل استثنا گذاشته شده است. توجه: این یک کلید debug با گذرواژهٔ کاملاً عمومی `123456` است، نه کلید انتشار واقعی؛ فقط برای همین اپ شخصی مناسب است.

### مقدار SHA-1 برای Google Cloud

در لاگ مرحلهٔ `signingReport` دنبال چیزی شبیه این بگردید:

```
Variant: debug
Config: debug
Store: /home/runner/work/.../app/keystore.jks
Alias: my-alias
MD5:  ...
SHA1: AB:CD:EF:...
SHA-256: ...
```

مقدار **SHA1** را کپی کنید؛ در مرحلهٔ بعد لازم است.

---

## ۳. ساخت OAuth Client در Google Cloud

1. وارد [Google Cloud Console](https://console.cloud.google.com/) شوید و یک پروژهٔ جدید بسازید.
2. از بخش **APIs & Services → Library** سرویس **Google Drive API** را فعال (Enable) کنید.
3. در **APIs & Services → OAuth consent screen**:
   - نوع کاربر: **External**
   - نام اپ و ایمیل پشتیبان را وارد کنید
   - در بخش Scopes فقط موارد لازم را اضافه کنید (پیشنهاد: `.../auth/drive.file`، `openid`، `email`)
   - در بخش **Test users** ایمیل خودتان را اضافه کنید (تا وقتی اپ در حالت تست است)
4. در **APIs & Services → Credentials → Create Credentials → OAuth client ID** یکی از این دو مسیر را انتخاب کنید:

   **مسیر پیشنهادی — نوع `Android`:**
   - Application type: **Android**
   - Package name: `com.personal.audioapp`
   - SHA-1: همان مقداری که از `signingReport` کپی کردید

   این نوع، «client عمومی» است: نه `client_secret` لازم دارد و نه در کنسول فیلد Redirect URI دارد. پس همین مسیر ساده‌ترین حالت برای اپ شخصی است. اگر گوگل هنگام ورود `redirect_uri_mismatch` داد، فقط مقدار بازگشت در اپ باید عوض شود (یک تنظیم در `gradle.properties`، بدون دست‌زدن به منطق اپ).

   **مسیر جایگزین — نوع `Desktop app` (اگر مسیر بالا کار نکرد):**
   - Application type: **Desktop app**
   - در **Authorized redirect URIs** دقیقاً این مقدار را اضافه کنید:
     `com.personal.audioapp:/oauth2callback`
   - این نوع به SHA-1 گره نخورده، ولی ممکن است در تبادل توکن `client_secret` بخواهد؛ در آن صورت باید فیلد اختیاری secret هم به تنظیمات اپ اضافه شود (کد فعلی این فیلد را ندارد).

5. بعد از ساخت، مقدار **Client ID** (چیزی شبیه `1234567890-xxxxxxxx.apps.googleusercontent.com`) را کپی کنید.

در این اپ از «Authorization Code + PKCE» استفاده می‌شود و redirect URI ثابت زیر است (در خود اپ هم در صفحهٔ تنظیمات نشان داده و قابل کپی است):

```
com.personal.audioapp:/oauth2callback
```

در درخواست ورود، `access_type=offline` و `prompt=consent` ارسال می‌شود تا گوگل **refresh token** بدهد؛ به این ترتیب آپلود در پس‌زمینه حتی بعد از بستن اپ هم کار می‌کند.

> دسترسی درخواستی `drive.file` است؛ یعنی اپ فقط به فایل‌ها و پوشه‌هایی دسترسی دارد که **خودش** ساخته است. پس اگر «شناسهٔ پوشهٔ درایو» را دستی پر می‌کنید، باید شناسهٔ پوشه‌ای باشد که خود اپ ساخته؛ برای یک پوشهٔ قدیمی دراپ‌شده در Drive خطای «File not found» می‌گیرید. ساده‌ترین حالت: این فیلد را خالی بگذارید.

---

## ۴. استفاده از اپ

1. APK را نصب و اپ را باز کنید.
2. آیکن **تنظیمات** (بالا سمت چپ در چیدمان راست‌چین) → فیلد **Client ID** را با مقدار مرحلهٔ قبل پر کنید → **ذخیرهٔ تنظیمات**.
   - اختیاری: اگر می‌خواهید فایل‌ها داخل یک فولدر مشخص از درایو بروند، **شناسهٔ پوشهٔ درایو** را هم وارد کنید. اگر خالی باشد، اپ خودش یک فولدر به نام `AudioApp` می‌سازد.
3. دکمهٔ **اتصال** → مرورگر باز می‌شود → حساب گوگل را انتخاب کنید.
   - اولین بار که اپ را باز می‌کنید، دسترسی **میکروفون** (و در اندروید ۱۳+ اجازهٔ نمایش نوتیفیکیشن) درخواست می‌شود.
4. در صفحهٔ اصلی:
   - **ضبط جدید**: شروع ضبط؛ یک نوتیفیکیشن پایدار با دکمهٔ **توقف** نمایش داده می‌شود. با زدن توقف، فایل خام (`m4a`) در صف قرار می‌گیرد.
   - **انتخاب فایل صوتی**: انتخاب فایل صوتی موجود از حافظه (SAF). فایل داخل حافظهٔ خصوصی اپ کپی می‌شود تا بعداً قابل خواندن باشد.
5. هر آیتم صف این چرخه را طی می‌کند:
   `در انتظار → در حال فشرده‌سازی → در حال آپلود → تمام شد`
   و در صورت خطا **خطا** می‌شود (دلیل خطا زیر همان آیتم نوشته می‌شود). با آیکن ⟳ می‌توانید دوباره تلاش کنید و با سبد آشغال، آیتم و فایل محلی‌اش را پاک کنید.
6. دستور FFmpeg که برای فشرده‌سازی اجرا می‌شود:

```
-y -hide_banner -i "<input>" -vn -map_metadata -1 -ac 2 -ar 44100 -c:a libmp3lame -b:a 64k -f mp3 "<output>"
```

7. سه کلید خودکارسازی در تنظیمات:
   - **فشرده‌سازی و آپلود خودکار پس از ضبط**: اگر خاموش باشد، فایل‌ها در حالت «در انتظار» می‌مانند و باید دستی «تلاش دوباره» بزنید.
   - **حذف فایل گوشی پس از آپلود**: پاک کردن فایل گوشی بعد از تأیید درایو.
   - **حذف فایل خام پس از فشرده‌سازی**: نگه‌داشتن فقط نسخهٔ MP3.

---

## ۵. نکات فنی و عیب‌یابی

- **زبان رابط کاربری**: تمام متن‌های اپ در `app/src/main/res/values/strings.xml` و به فارسی هستند و چیدمان در `AudioAppTheme` به‌صورت ثابت راست‌چین (RTL) تنظیم شده است؛ یعنی حتی اگر زبان گوشی انگلیسی باشد، باز هم رابط فارسی و راست‌به‌چپ نمایش داده می‌شود. برای اضافه کردن زبان دیگر کافی است یک پوشهٔ `values-xx/strings.xml` بسازید.
- **توکن‌ها کجا ذخیره می‌شوند؟** در `EncryptedSharedPreferences` (فایل `audio_app_secure_prefs`) شامل Client ID، refresh/access token و ایمیل حساب. اگر keystore دستگاه خراب باشد، اپ به‌جای کرش کردن روی `SharedPreferences` معمولی fallback می‌کند.
- **چرا برای اپ هیچ «سروری» لازم نیست؟** احراز هویت با Flow استاندارد گوگل برای اپ‌های موبایل (PKCE) انجام می‌شود و درخواست‌های آپلود مستقیم از خود دستگاه به `www.googleapis.com` می‌رود.
- **خطای «Google Drive account is not connected»**: یعنی refresh token وجود ندارد یا باطل شده؛ دوباره Connect کنید. اگر بازهم تکرار شد، Client ID را چک کنید و مطمئن شوید SHA-1/OAuth client روی همین keystore ساخته شده است.
- **خطای `invalid_client` یا `redirect_uri_mismatch`**: Package name و SHA-1 در Google Cloud با APK نصب‌شده یکی نیست. کلیدهای debug با هر بار ساخت keystore جدید تغییر می‌کنند؛ keystore را از artifact دانلود و در `app/` نگه دارید.
- **ضبط کوتاه‌تر از یک ثانیه**: `MediaRecorder.stop()` خطا می‌دهد و فایل ناقص حذف می‌شود؛ این رفتار عمدی است.
- **خطای «Source file is missing or empty»**: معمولاً چون تیک «Delete raw recording after compression» فعال بوده و فایل خام پاک شده، اما CompressionWorker دوباره اجرا شده است. آیتم را Retry کنید (نسخهٔ MP3 اگر موجود باشد مستقیم آپلود می‌شود).
- **آپلود از نوع resumable**: آپلود در دو مرحله انجام می‌شود (ساخت نشست + PUT بایت‌ها). بنابراین برخلاف `uploadType=multipart` که سقف ۵ مگابایت دارد (حدود ۱۰ دقیقه صدای ۶۴ kbps)، هر طول ضبطی آپلود می‌شود. تایم‌اوت‌های `OkHttpClient` هم برای آپلود چند دقیقه‌ای بالا برده شده‌اند.
- **سقف تلاش آپلود**: هر بار آپلود حداکثر ۳ تلاش (با نیاز به اینترنت) دارد؛ بعد از آن وضعیت **خطا** می‌شود.
- **اندازهٔ APK**: چون FFmpeg داخل اپ است، حجم APK نسبتاً زیاد می‌شود (فورک `audio` فقط کدک‌های صوتی را دارد).
- **فایل‌های خروجی روی گوشی**: پوشه‌های `recordings`، `compressed` و `incoming` داخل `Android/data/com.personal.audioapp/files/` هستند.
