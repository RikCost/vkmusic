# План работ — промпт для следующей сессии

Контекст: Android-клиент VK Музыки (`com.vkmusic`, Kotlin + Compose + Hilt + Room + Media3).
Дальше делать эти два блока, в этом порядке.

## 0. Правила работы в этом проекте

- Сборку запускать **только по явной команде**. Рабочая команда (из корня проекта, Git Bash):
  `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" GRADLE_USER_HOME="D:/Repository/.gradle" TEMP="D:/Repository/.tmp" TMP="D:/Repository/.tmp" ./gradlew.bat :app:assembleDebug --console=plain > /d/Repository/.tmp/vk-bc.log 2>&1`
  (≈6 минут, `sdk.dir` уже в `local.properties`; APK → `app/build/outputs/apk/debug/MuVK-<version>-debug.apk`).
- Тесты (`:app:testDebugUnitTest`) каждый раз не гонять.
- `local.properties` в git не попадает — политика клиента блокирует коммит файлов с ключами.
  Значения (`client_id`/`api_id` = `2274003`, `client_secret`) зафиксированы текстом в [vk-auth.md](vk-auth.md).
- **Проксирование через `https://api.lkmp.app/method/...` не использовать** — там чужой бэкенд
  (`execute.getUserInfo`, `execute` c кодом). Всё делаем напрямую в `api.vk.ru`.

## 1. Вход по схеме эталонного APK (LKMP_30_1.2.9.7.apk), прямый VK ID

Симптом: логин+пароль проходит, экран кода появляется, но «Отправить код на почту» → `invalid request`,
код не приходит. Причина по реверсу эталона: не those параметры и неверный шаг подтверждения.

Извлечено из `classes.dex` эталона (обфусцированные классы `LV0/j`, `Lp1/c`; см. offsets ниже):

| Шаг | Endpoint | Параметры |
|---|---|---|
| анонимный токен | `method/auth.getAnonymToken` (`p1.c->c`, `0x120888`) | `client_id`, `client_secret`, `device_id`, `lang=ru`, `v=5.276`, `https=1`, `api_id=2274003` |
| обмен токена | `oauth/token` (`V0/j->a`, `0xc9bd8`) | form: `sid?`, `username`, `password`, `anonymous_token`; пары: `libverify_support=1`, `scope=all`, `device_trusted_hash_supported=1`, `grant_type=password` (первый шаг) / `phone_confirmation_sid` (шаг 2FA), `2fa_supported=1`, `supported_ways=push,email`, `lang=ru`, `device_id`, `sak_version=1.144.0`, `flow_type=tg_flow`, `gaid`, `access_token=<анонимный>`, `v=5.276`, `https=1`, `api_id` |
| рассылка кода | `method/ecosystem.sendOtp{Email\|Sms\|Push}` (`V0/j->g`, `0xca408`) | `sid=<validation_sid>`, `access_token=<анонимный>`, `lang`, `device_id`, `sak_version=1.144.0`, `flow_type=tg_flow`, `gaid`, `v`, `https=1`, `api_id`; ответ: `response.info` |
| проверка кода | `method/ecosystem.checkOtp` (`p1.c->a`, `0x120a58`) | `sid`, `code` + тот же набор экосистемных параметров |
| список способов | `method/ecosystem.getVerificationMethods` (`p1.c->d`, `0x120bc4`) | `sid` + экосистемные параметры |
| заголовок/транспорт | `p1.c->e`, `0x120d10` | `POST`, `application/x-www-form-urlencoded; charset=utf-8`, `User-Agent`, `X-VK-Android-Client: new`, `Accept: image/webp`, `Accept-Encoding: gzip`, `Cache-Control: no-cache`, `X-Screen` |

Что изменить ([VkAuthApi.kt](../app/src/main/java/com/vkmusic/data/api/vk/VkAuthApi.kt)):

1. `ecosystemParams()` — вернуть `gaid` (у эталона это стабильный hex-id, генератор `p1.c->h` на `0x1209c8`
   использует `"0123456789abcdef"`; достаточно сгенерировать один раз и хранить как `device_id`).
2. тело `oauth/token` — добавить `access_token=<анонимный>`, `device_trusted_hash_supported=1`,
   `supported_ways=push,email`, `flow_type=tg_flow`, `gaid`, `lang`; `grant_type` переключать:
   `password` при первом запросе, `phone_confirmation_sid` при завершении 2FA.
3. `auth.validateAccount` и `flowSid`-фолбэк **убрать**: эталон его не вызывает, а `sid` для `sendOtp*`
   обязан быть ровно `validation_sid` из `need_validation` (сейчас подстановка чужого `sid` и даёт `invalid request`).
4. Подтверждение кода — **два шага**, а не один: сначала `ecosystem.checkOtp(sid, code)`, и только после
   успеха повторить `oauth/token` с `grant_type=phone_confirmation_sid` + `sid` (без `code`).
5. `2fa_app` кода не требует (у эталона бросается исключение «подтвердите в приложении») — оставить только
   `2fa_email`/`2fa_push`/`2fa_sms` как каналы `OtpChannel`.
6. Не трогать `method/auth.refreshToken`/`oauth/auth_by_exchange_token` — это путь прокси, он не нужен.

Трогаемые файлы: `VkAuthApi.kt`, `VkDtos.kt` (`SendOtpResponse`, добавить `CheckOtpResponse`),
`AuthRepository.kt` (`verifyCode`), `LoginViewModel.kt`, `LoginScreen.kt`, тесты `VkAuthApiTest.kt`.
Инструменты реверса, которые уже работают на этой машине: `D:\Repository\.tmp\lkmp-re\find.py`
(поиск метода по строке) и `D:\Repository\.tmp\lkmp-re\dumpclass.py` (дамп строк/вызовов класса),
запуск: `python dumpclass.py <apk> "LV0/j;"`. `jadx`/`apktool`/`baksmali` отсутствуют.

## 2. Раздельное хранение: кэш vs MP3 + путь в настройках

Сейчас «кэш» и «офлайн» — одно и то же: `SimpleCache(filesDir/downloads)` с `NoOpCacheEvictor`
([MediaModule.kt:45-48](../app/src/main/java/com/vkmusic/di/MediaModule.kt#L45-L48)), а плеер в кэш
не пишет вовсе (`setCacheWriteDataSinkFactory(null)`,
[MediaModule.kt:57-64](../app/src/main/java/com/vkmusic/di/MediaModule.kt#L57-L64)). Реального `.mp3`
на трек нет: данные лежат чанками под ключом `track.id`.

### 2.1 Кэш (исчезающий)
- `SimpleCache(File(context.cacheDir, "music_cache"), LruCacheEvictor(<лимит из настроек>), databaseProvider)`.
  `LruCacheEvictor.maxCacheBytes` меняется на лету — лимит применять из `SettingsRepository`.
- Плееру запись включить (`setCacheWriteDataSinkFactory` вернуть), `setIsCacheLocked(false)` —
  просмотренные треки кэшируются и вытесняются по LRU.
- Офлайн-загрузки писать в тот же кэш с `setIsCacheLocked(true)`: залоченный контент `LruCacheEvictor`
  не вытесняет, офлайн остаётся.
- В настройках: размер кэша (`SimpleCache.getCacheBytes()` по подписке на `Listener`), кнопка «Очистить кэш»
  (`cache.removeResources(keys)`), лимит в МБ.

### 2.2 MP3 (постоянный файл)
- VK отдаёт прогрессивный MP3 по `track.url` → файл получается простой записью байтов, перекодировка не нужна.
- Путь: выбор через SAF `ACTION_OPEN_DOCUMENT_TREE` + `rememberLauncherForActivityResult`,
  `contentResolver.takePersistableUriPermission`, запись через `DocumentFile.createFile("audio/mpeg")`;
  значение URI хранить в DataStore. Дефолт без SAF — `context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)`
  (新的 разрешения не нужны: `minSdk 26`, `targetSdk 36`, см. `app/build.gradle.kts:28-29`).
- Имя файла: `<Artist> - <Title>.mp3` (+ `_<id>` при коллизии).
- Скачивание — `Worker` на уже подключённой обвязке WorkManager
  (`VkMusicApp.kt:10-16`, инициализатор удалён в `AndroidManifest.xml:48-57`), прогресс в
  `setProgress`/`StateFlow`; отмена по `track.id`.
- Приоритет источника в плеере: если локальный файл есть — `MediaItem` строится на `file://`
  ([MediaItemMapper.kt:11-31](../app/src/main/java/com/vkmusic/player/queue/MediaItemMapper.kt#L11-L31)),
  иначе `track.url`/кэш.
- Room: `AppDatabase` `version = 1` без миграций → `version = 2` + `Migration` c
  `ALTER TABLE tracks ADD COLUMN ...` (`fileUri`, `fileName`, `fileSize`, `savedAt`);
  колонки в [TrackEntity.kt:9-19](../app/src/main/java/com/vkmusic/data/db/TrackEntity.kt#L9-L19).
  Деструктив fallback не применять — там список «моей музыки».
- UI: пункт меню «Сохранить как MP3» / «Удалить MP3» в
  [TrackList.kt:155-173](../app/src/main/java/com/vkmusic/ui/components/TrackList.kt#L155-L173)
  (сейчас там «Скачать» на `168-172`), действия — в `TrackActionsViewModel.kt:69-74`,
  кнопка в плеере — `PlayerScreen.kt:138-143`, экран `DownloadsScreen.kt` → два режима в одном экране
  (Кэш / MP3) либо отдельный маршрут.

### 2.3 Экран настроек (новый)
Маршрутов настроек нет: `Routes.kt`, `VkMusicRoot.kt:104-156`; точка входа — `TracksScreen.kt:39-49`
(рядом с Info/Logout). Persist — тот же DataStore `"settings"`
(`SettingsRepository.kt:12-35`, `DataModule.kt:32-33`): добавить `cacheMaxBytes`, `mp3TreeUri`,
`defaultSaveMode`. Строкие следуют конвенции проекта — литералы на русском в Compose
(в `res/values/strings.xml` всего 3 строки).

## 3. Что проверять руками после правок

1. Реальный вход: экран кода → «Код отправлен: почта» → письмо пришло → `checkOtp` → `access_token`.
   Если снова `invalid request` — снять `validation_type`, `response.info` и полный текст ошибки VK.
2. Офлайн-трек играет в airplane; просмотренный трек не доезжает второй раз (кэш).
3. Экспорт MP3 в выбранную папку, файл играет с `file://`, переживает перезапуск и «Очистить кэш».
4. `signOut` (см. `AuthRepository.kt:59-64`) кэш чистит, MP3 — **не** удаляет без явного запроса.
