# vkmusic

Музыкальный клиент ВКонтакте для Android: поиск, рекомендации, «моя музыка», плейлисты и друзья
с воспроизведением через Media3 и локальным хранением треков. Аккаунт можно не использовать —
есть демо-режим на локальных данных.

- Package: `com.vkmusic`, версия 0.2.0
- Минимум Android 8.0 (`minSdk 26`), собран против SDK 36 (`targetSdk 36`)
- UI полностью на русском, строки захардкожены в Compose

## Возможности

- Вход по логину и паролю через VK ID (экосистемный OAuth на `api.vk.ru`), включая 2FA
  (код на почту / по SMS / в push) и капчу — подробности в [docs/vk-auth.md](docs/vk-auth.md).
- Плеер на Media3 ExoPlayer: `MediaSessionService`, очередь, перемотка, фоновое воспроизведение.
- Офлайн-загрузка треков (`DownloadManager` Media3) и прогресс в уведомлении.
- Поиск, рекомендации, альбомы/плейлисты, музыка друзей, создание плейлистов.
- Демо-режим без аккаунта (`FakeVkAudioRepository`): тот же UI на локальных WAV-файлах.
- Токен хранится в DataStore в виде AES-GCM-шифра, ключ — в AndroidKeyStore (`KeystoreCipher`).

## Требования

JDK 21, Android SDK 36 (platform-tools + build-tools), Gradle берётся из wrapper'а.

## Настройка

```bash
cp local.properties.sample local.properties
```

и заполнить три значения:

| Ключ | Что туда |
|---|---|
| `sdk.dir` | путь к Android SDK на вашей машине |
| `vk.clientId` | `client_id`/`api_id` эталонного Android-клиента VK |
| `vk.clientSecret` | его `client_secret` |

`local.properties` в `.gitignore`, ключи в историю не попадают. Значения `vk.clientId` /
`vk.clientSecret` — реквизиты официального клиента, извлечённые из его APK (не нашего приложения);
обоснование и следствия — в [docs/vk-auth.md](docs/vk-auth.md). Если их не указать, приложение
соберётся и будет работать в демо-режиме, но вход по паролю недоступен (`VkIdConfig.isConfigured`).

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
├── data/api/vk/        транспорт и DTO VK: VkApiClient (api.vk.com, audio.*),
│                       VkAuthApi (api.vk.ru, VK ID), VkHeadersInterceptor (UA + X-VK-Android-Client)
├── data/api/fake/      демо-репозиторий без сети
├── data/auth/          SessionStore + KeystoreCipher (шифрование токена)
├── data/db/            Room: TrackEntity/TrackDao/AppDatabase (кэш «моей музыки»)
├── data/repository/    AuthRepository, TrackRepository, DownloadRepository, SettingsRepository
├── di/                 Hilt-модули: NetworkModule, MediaModule (SimpleCache, ExoPlayer, DownloadManager)
├── player/             PlaybackService (MediaSessionService), очередь, MediaItemMapper
├── ui/screens/         login, tracks, search, playlists, friends, similar, downloads, player
└── ui/components/      TrackList, Artwork, Common (общие элементы и меню трека)
```

Стек: Kotlin, Jetpack Compose + Material 3, Hilt, Room (KSP), Media3 (ExoPlayer/OkHttpDataSource),
WorkManager, Coil 3, kotlinx.serialization, OkHttp 4.

## Планы

Незавершённое и разведка по эталонному APK — в [docs/next-session-plan.md](docs/next-session-plan.md):
подтверждение кода через `ecosystem.checkOtp` + `grant_type=phone_confirmation_sid`,
раздельное хранение «кэш (исчезающий) vs MP3 (файл по выбранному пути)» и экран настроек.
