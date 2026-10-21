# План работ — промпт для следующей сессии

Контекст: Android-клиент VK Музыки (`com.vkmusic`, Kotlin + Compose + Hilt + Room + Media3).
Дальше — блок ниже.

## 0. Правила работы в этом проекте

- Сборку запускать **только по явной команде**. Рабочая команда (из корня проекта, Git Bash):
  `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" GRADLE_USER_HOME="D:/Repository/.gradle" TEMP="D:/Repository/.tmp" TMP="D:/Repository/.tmp" ./gradlew.bat :app:assembleDebug --console=plain > /d/Repository/.tmp/vk-bc.log 2>&1`
  (≈6 минут, `sdk.dir` уже в `local.properties`; APK → `app/build/outputs/apk/debug/MuVK-<version>-debug.apk`).
- Тесты (`:app:testDebugUnitTest`) каждый раз не гонять.
- **Проксирование через `https://api.lkmp.app/method/...` не использовать** — там чужой бэкенд
  (`execute.getUserInfo`, `execute` c кодом). Всё делаем напрямую в `api.vk.ru`.

## 1. Раздельное хранение: кэш vs MP3 + путь в настройках

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

## 2. Что проверять руками после правок

1. Реальный вход: экран кода → «Код отправлен: почта» → письмо пришло → `checkOtp` → `access_token`.
   Если снова `invalid request` — снять `validation_type`, `response.info` и полный текст ошибки VK.
2. Офлайн-трек играет в airplane; просмотренный трек не доезжает второй раз (кэш).
3. Экспорт MP3 в выбранную папку, файл играет с `file://`, переживает перезапуск и «Очистить кэш».
4. `signOut` (см. `AuthRepository.kt:59-64`) кэш чистит, MP3 — **не** удаляет без явного запроса.
