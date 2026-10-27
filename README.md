# MuVK

Музыкальный клиент ВКонтакте для Android: поиск, рекомендации, «моя музыка», плейлисты и друзья
с воспроизведением через Media3 и локальным хранением треков. Интерфейс и функции перенесены из
десктопного плеера [Meridius](https://github.com/PurpleHorrorRus/Meridius) — как именно, описано в
[docs/meridius-port.md](docs/meridius-port.md).

- Package: `com.vkmusic`, версия 0.4.3
- Минимум Android 8.0 (`minSdk 26`), собран против SDK 36 (`targetSdk 36`)
- UI полностью на русском, строки захардкожены в Compose

## Возможности

- Вход как в Meridius: QR-код VK ID с подтверждением в приложении VK (и кодом, если VK попросит);
  дополнительно — вход на странице VK во встроенном браузере (WebView). Дальше работает
  веб-сессия vk.ru по cookie, токен API выдаёт `login.vk.ru/?act=web_token` и обновляется раз в 5 минут.
- Музыка через веб-API VK (`al_audio.php`), как в Meridius: списки, плейлисты, поиск, тексты;
  ссылки на треки получаются лениво (`reload_audios`) перед воспроизведением и скачиванием.
  Перед первым запросом к `al_audio.php` один раз за процесс открывается главная `vk.ru` по cookie
  (`VkWebClient.warmUpSession`) — без этого шага VK после перезапуска отвечает пустой страницей.
- Плеер на Media3 ExoPlayer: `MediaSessionService`, очередь «Сейчас играет», скорость,
  «перемотка в начало трека», восстановление очереди после перезапуска.
- Звук как в Meridius: 18-полосный эквалайзер с 26 пресетами и нормализация громкости по LUFS —
  собственные `AudioProcessor` Media3 (`player/audio`).
- VK Микс с фильтрами настроения/узнаваемости и «Микс по треку» — бесконечная очередь.
- Темы Meridius (Classic, Modern, VK Black/White, Spotify, Higurashi) и Material You.
- Тексты песен (синхронизированные — с подсветкой строки), страницы исполнителей и сообществ,
  управление плейлистами: редактирование, удаление, подписка, «Поделиться».
- Офлайн-загрузка треков (`DownloadManager` Media3) и прогресс в уведомлении — в постоянный кэш
  `filesDir/downloads`; прослушанное дополнительно оседает в исчезающий LRU-кэш `cacheDir/music_cache`
  с лимитом из настроек и кнопкой «Очистить кэш».
- Сохранение треков и плейлистов файлами в папку из настроек (SAF; по умолчанию «Музыка/MuVK»):
  прямой MP3 скачивается как есть, HLS расшифровывается (AES-128) и аудио вынимается из MPEG-TS
  без перекодирования; теги ID3v2.3 с обложкой. Если VK отдаёт AAC, файл сохраняется как `.aac`.
  Сохранённый файл становится источником воспроизведения: адрес лежит в Room (`exported_tracks`),
  трек играет без сети и без `reload_audios`, из меню трека его можно удалить вместе с записью.
- Поиск, рекомендации, альбомы/плейлисты, музыка друзей, создание плейлистов.
- Токен и cookie веб-сессии хранятся в DataStore в виде AES-GCM-шифра, ключ — в AndroidKeyStore (`KeystoreCipher`).

## Требования

JDK 21, Android SDK 36 (platform-tools + build-tools), Gradle берётся из wrapper'а.

## Настройка

```bash
cp local.properties.sample local.properties
```

и указать `sdk.dir` — путь к Android SDK. Ключей приложения VK не нужно: вход идёт через веб-VK ID,
как в Meridius (подробности — в [docs/meridius-port.md](docs/meridius-port.md)).

## Сборка

```bash
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/MuVK-<version>-debug.apk
./gradlew :app:testDebugUnitTest      # юнит-тесты (MockWebServer)
./gradlew :app:assembleRelease
```

Gradle-кэш и SDK можно переопределить переменными `GRADLE_USER_HOME`, `ANDROID_HOME`, `JAVA_HOME`.

## Структура

```
app/src/main/java/com/vkmusic/
├── data/api/vk/        официальный API с web-токеном: VkApiClient, VkApiAudioRepository (микс, друзья,
│                       сообщества, исполнители), VkHeadersInterceptor (UA + X-VK-Android-Client)
├── data/web/           веб-VK как в Meridius: VkWebClient (cookie), VkQrAuth (QR-вход, web_token),
│                       VkWebAudioRepository (al_audio.php), AudioUrlDecoder, WebAudioParser
├── data/export/        сохранение файлами: HlsPlaylist, TsAudioExtractor, Id3Writer, ExportStorage,
│                       Mp3Exporter, Mp3ExportWorker (WorkManager), Mp3ExportRepository,
│                       SavedAudioStore (реестр сохранённых файлов — источник воспроизведения)
├── data/auth/          SessionStore, WebSessionManager (обновление токена), KeystoreCipher
├── data/db/            Room: TrackEntity/TrackDao (кэш «моей музыки»), ExportedTrackEntity/Dao, AppDatabase
├── data/repository/    AuthRepository, TrackRepository, DownloadRepository, SettingsRepository,
│                       CacheRepository (объём и очистка кэша)
├── data/storage/       TunableLruCacheEvictor — LRU с лимитом, который меняется из настроек
├── di/                 Hilt-модули: NetworkModule, MediaModule (два SimpleCache, DownloadManager), DataModule
├── player/             PlaybackService (MediaSessionService), очередь, MediaItemMapper
├── player/datasource/  PlayerDataSourceFactory: сохранённые файлом треки читаются мимо кэша
├── player/audio/       эквалайзер и нормализатор громкости (AudioProcessor Media3)
├── ui/screens/         login, home, music, search (Обзор), friends (друзья/сообщества), playlists,
│                       artist, similar, settings, player
├── ui/components/      TrackList, Artwork, Common, LyricsSheet (общие элементы и меню трека)
└── ui/theme/           темы Meridius → ColorScheme Material 3
```

Стек: Kotlin, Jetpack Compose + Material 3, Hilt, Room (KSP), Media3 (ExoPlayer/OkHttpDataSource),
WorkManager, Coil 3, kotlinx.serialization, OkHttp 4.

## Планы

Незавершённое и разведка по эталонному APK — в [docs/next-session-plan.md](docs/next-session-plan.md):
подтверждение кода через `ecosystem.checkOtp` + `grant_type=phone_confirmation_sid`,
раздельное хранение «кэш (исчезающий) vs MP3 (файл по выбранному пути)» и экран настроек.
