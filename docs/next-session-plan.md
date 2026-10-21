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

### 2.2 MP3 (постоянный файл) — сделано

Реализовано в `data/export` (см. README). Отличия от исходного плана: веб-VK отдаёт в основном HLS,
поэтому файл собирается из сегментов (AES-128, MPEG-TS → аудио без перекодирования, как `hls.download()`
Meridius, но без FFmpeg); источник воспроизведения на `file://` не переключается, отдельной таблицы
в Room нет — статусы берутся из WorkManager. Что осталось проверить руками: какой кодек внутри HLS VK
(ожидается MP3; если AAC — файл будет `.aac`), запись в папку SAF на разных провайдерах.

### 2.3 Экран настроек (новый)
Маршрутов настроек нет: `Routes.kt`, `VkMusicRoot.kt:104-156`; точка входа — `TracksScreen.kt:39-49`
(рядом с Info/Logout). Persist — тот же DataStore `"settings"`
(`SettingsRepository.kt:12-35`, `DataModule.kt:32-33`): добавить `cacheMaxBytes` (папка MP3 уже есть: `exportTreeUri`). Строкие следуют конвенции проекта — литералы на русском в Compose
(в `res/values/strings.xml` всего 3 строки).

## 2. Что проверять руками после правок

1. Реальный вход: экран кода → «Код отправлен: почта» → письмо пришло → `checkOtp` → `access_token`.
   Если снова `invalid request` — снять `validation_type`, `response.info` и полный текст ошибки VK.
2. Офлайн-трек играет в airplane; просмотренный трек не доезжает второй раз (кэш).
3. Экспорт MP3 в выбранную папку, файл играет с `file://`, переживает перезапуск и «Очистить кэш».
4. `signOut` (см. `AuthRepository.kt:59-64`) кэш чистит, MP3 — **не** удаляет без явного запроса.
