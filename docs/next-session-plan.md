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
Meridius, но без FFmpeg). Файл — копия для стороннего плеера, не источник воспроизведения:
- Реестр сохранённых (таблица `exported_tracks` + `SavedAudioStore`) убран вместе с воспроизведением
  `file://`/`content://` в очереди и обходом кэша (`PlayerDataSourceFactory`): провайдер папки SAF
  отказывался удалять файл, отметка «Сохранено» при этом оставалась и врала.
- Статус снова берётся из `outputData` задач WorkManager, то есть «Сохранено» живёт столько,
  сколько система хранит историю задач (обычно сутки).
- В меню трека нет «Удалить сохранённый файл», в настройках — числа и объёма сохранённых:
  удалять файлы нужно в проводнике или плеере устройства.
Что осталось проверить руками: какой кодек внутри HLS VK (ожидается MP3; если AAC — файл будет `.aac`),
запись в папке SAF на разных провайдерах.

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

## 3. Холодный старт: «VK не отдал музыку через веб-версию» — исправлено

Было: сразу после запуска список музыки не загружался с ошибкой, а «Диагностика веб-VK» в настройках её
убирала. Диагностика начинается с GET главной `vk.ru` по cookie — этот шаг VK и считает «входом в веб»,
после него `al_audio.php` начинает отдавать плейлист. Приложение же первого запроса к главной не делало:
cookie для `web_token` достаточно, но для `al_audio.php` после перезапуска процесса — нет.
- `VkWebClient.warmUpSession()` — GET `VkWebConfig.BASE_URL` раз за процесс (под `Mutex`; при сетевом
  сбое флаг не ставится, следующий запрос прогреется снова), вызывается из `alAudio()`; страница входа
  в ответе — сигнал `onSessionLost`. `clearSession()` сбрасывает флаг.
- `VkWebAudioRepository.firstPage()`: если `section("all")` ответил без плейлиста, один ретрах после
  форсированного прогрева — и только потом `unexpected("музыку")`.
- `WebSessionManager.completeLogin()` прогревает сразу после QR/WebView-входа, чтобы первая страница
  музыки не ждала ретраха.

## 4. Что проверять руками после правок

1. Реальный вход: экран кода → «Код отправлен: почта» → письмо пришло → `checkOtp` → `access_token`.
   Если снова `invalid request` — снять `validation_type`, `response.info` и полный текст ошибки VK.
2. Офлайн-трек играет в airplane; просмотренный трек не доезжает второй раз (кэш).
3. Экспорт MP3 в выбранную папку: файл переживает перезапуск и «Очистить кэш», в приложении трек
   играет по ссылке VK, отметка «Сохранено» держится, пока жива история задач WorkManager.
4. `signOut` (см. `AuthRepository.kt:59-64`) кэш чистит, MP3 — **не** удаляет без явного запроса.
5. Холодный старт (kill + запуск) с сохранённым входом: музыка отдаёт сразу, без «Диагностики веб-VK».
