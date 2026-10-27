# План работ — промпт для следующей сессии

Контекст: Android-клиент MuVK (`com.vkmusic`, Kotlin + Compose + Hilt + Room + Media3).
Дальше — блок ниже.

## 0. Правила работы в этом проекте

- Сборку запускать **только по явной команде**. Рабочая команда (из корня проекта, Git Bash):
  `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" GRADLE_USER_HOME="D:/Repository/.gradle" TEMP="D:/Repository/.tmp" TMP="D:/Repository/.tmp" ./gradlew.bat :app:assembleDebug --console=plain > /d/Repository/.tmp/vk-bc.log 2>&1`
  (≈6 минут, `sdk.dir` уже в `local.properties`; APK → `app/build/outputs/apk/debug/MuVK-<version>-debug.apk`).
- Тесты (`:app:testDebugUnitTest`) каждый раз не гонять.
- **Проксирование через `https://api.lkmp.app/method/...` не использовать** — там чужой бэкенд
  (`execute.getUserInfo`, `execute` c кодом). Всё делаем напрямую в `api.vk.ru`.

## 1. Раздельное хранение: кэш vs MP3 + путь в настройках

Было: «кэш» и «офлайн» — одно и то же, `SimpleCache(filesDir/downloads)` с `NoOpCacheEvictor`, а плеер
в кэш не писал вовсе (`setCacheWriteDataSinkFactory(null)`). Реального `.mp3` на трек нет: данные лежат
чанками под ключом `track.id`. Пункты 2.1 и 2.2 закрыты.

### 2.1 Кэш (исчезающий) — сделано

Два кэша вместо одного залоченного: в Media3 1.11 нет ни `setIsCacheLocked`, ни изменяемого предела
у `LeastRecentlyUsedCacheEvictor` (`LruCacheEvictor` переименован, `maxBytes` — `private final`).
- `di/MediaModule.kt`: `@StreamingCache` — `SimpleCache(cacheDir/music_cache, TunableLruCacheEvictor)`,
  плеер в него пишет; `@DownloadCache` — прежний `SimpleCache(filesDir/downloads, NoOpCacheEvictor)`,
  офлайн вечный и под «Очистить кэш» не попадает.
- Постоянный кэш — upstream потокового (`@OfflineUpstream`), поэтому загруженный офлайн трек играет
  и в самолёте, и второй раз не качается.
- `data/storage/TunableLruCacheEvictor` — LRU с лимитом, который меняется на лету (решение о вытеснении
  по `Cache.getCacheSpace()`, порядок — по `lastTouchTimestamp`, как в Media3).
- Настройки: `cacheMaxBytes` (128 МБ…2 ГБ), «Занято X из Y», «Очистить кэш» (`CacheRepository`).

### 2.2 MP3 (постоянный файл) — сделано

Реализовано в `data/export` (см. README). Отличия от исходного плана: веб-VK отдаёт в основном HLS,
поэтому файл собирается из сегментов (AES-128, MPEG-TS → аудио без перекодирования, как `hls.download()`
Meridius, но без FFmpeg). Теперь файл — и источник воспроизведения:
- Таблица `exported_tracks` (Room v4, миграция 3→4) и `SavedAudioStore`: адрес файла, место, mime, размер.
  Запись появляется после успешного экспорта и переживает чистку истории WorkManager и перезапуск.
- `Track.toMediaItem(localUri)` ставит в очередь `file://`/`content://` вместо ссылки: трек играется
  без сети и без `reload_audios`; `PlayerConnection` и `PlaybackService` берут адрес из реестра,
  `TrackUrlResolver` для таких треков не вызывается.
- `player/datasource/PlayerDataSourceFactory`: локальные схемы читаются напрямую, без копии в кэше
  плеера (иначе файл занял бы место вдвое и уехал бы по LRU).
- Статус «Сохранено» — из реестра, а не из `outputData` задачи; в меню трека появился
  «Удалить сохранённый файл», в настройках — число и объём сохранённых.
Что осталось проверить руками: какой кодек внутри HLS VK (ожидается MP3; если AAC — файл будет `.aac`),
запись и чтение в папке SAF на разных провайдерах, игра `content://` в самолёте.

### 2.3 Экран настроек — есть

`ui/screens/settings/SettingsScreen.kt` (маршрут `Routes.Settings`). Persist — DataStore `"settings"`
(`SettingsRepository.kt`): `cacheMaxBytes`, `exportTreeUri`. Литералы на русском прямо в Compose
(в `res/values/strings.xml` всего 3 строки).

## 2. Риски — что закрыто и что осталось

Закрыто:
- Сохранение трека больше не держит его в памяти: `ExportStorage.save` принимает приёмник, HLS
  разбирается сегмент за сегментом прямо в файл (`TsAudioExtractor.drainTo`), прямой MP3
  копируется потоком. По первому сегменту определяется кодек — имя и mime нужны до создания файла.
- `app/proguard-rules.pro` был пустым при включённом `isMinifyEnabled` в release: добавлены
  атрибуты и правила для сериализаторов `@Serializable` (трек едет в `inputData` WorkManager),
  `AppDatabase_Impl` (Room ищет реализацию по имени) и `Mp3ExportWorker`.

Осталось:
- Release не подписан: в `build.gradle.kts` нет `signingConfig`, `assembleRelease` отдаёт unsigned APK.
  Ключ в репозиторий не кладём — нужен либо `keystore.properties` вне git, либо подпись в CI-секретах.
- Ни одна release-сборка не проверялась на устройстве: после настройки подписки прогнать вход,
  воспроизведение и «Сохранить в MP3» на minify-сборке.

## 3. Что проверять руками после правок

1. Реальный вход: экран кода → «Код отправлен: почта» → письмо пришло → `checkOtp` → `access_token`.
   Если снова `invalid request` — снять `validation_type`, `response.info` и полный текст ошибки VK.
2. Офлайн-трек играет в airplane; просмотренный трек не доезжает второй раз (кэш).
3. Экспорт MP3 в выбранную папку, файл играет с `file://`, переживает перезапуск и «Очистить кэш».
4. `signOut` (см. `AuthRepository.kt:59-64`) кэш чистит, MP3 — **не** удаляет без явного запроса.
