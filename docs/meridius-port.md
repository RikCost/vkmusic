# Порт Meridius на Android

[Meridius](https://github.com/PurpleHorrorRus/Meridius) — проприетарный десктопный плеер VK (Electron + Nuxt 2),
исходников в репозитории нет. Функции и дизайн перенесены в этот проект по результатам статического разбора
релиза `meridius-3.4.0.tar.gz` (Linux) по методике [leanrea](https://github.com/aicodeking/leanrea).
Код Meridius не копировался: всё переписано на Kotlin поверх нашего слоя официального API (`api.vk.com`).
Из бандла взяты только факты: имена методов и параметров VK, частоты эквалайзера, значения пресетов,
формула нормализатора, палитры тем.

## Как разбирали

1. `tar -xzf meridius-3.4.0.tar.gz meridius-3.4.0/resources/app.asar` → `npx @electron/asar extract`.
   Внутри: `dist/main/index.js` (main), `dist/renderer/_nuxt/*.js` (422 чанка Nuxt 2),
   `node_modules/meridius-core/dist/core.js` (вся работа с VK), `dist/renderer/{presets,themes}.json`.
2. Весь JS обработан `javascript-obfuscator` (массив строк + ротация + self-defending). `webcrack` снимает
   только верхний слой; у `core.js` строки закодированы base64 с отдельным декодером на каждый модуль.
   Свой проход на Babel: исполнить в `vm` только функции-массивы, декодеры и IIFE ротации, затем
   подставить литералы вместо вызовов `_0xNNNN(123)` и всех их алиасов (3618 строк, 0 ошибок).
3. Дальше — `rg -uu` по расшифрованному дереву.

## Что найдено (seen) и как перенесено

| Meridius | Где видно | В этом проекте |
|---|---|---|
| VK Микс: `audio.getStreamMixAudios` POST, `v=5.285`, `mix_id=common`, `count=3`, `options=JSON {categoryId: [optionId]}`, ответ — массив аудио | `core.js` `ExploreRequests.vkMix` | `VkApiAudioRepository.getMix`, бесконечная подгрузка в `PlayerConnection` |
| Настройки микса: `audio.getStreamMixSettings(mix_id=common)` → `settings.mix_categories[{id,title,options[{id,title,icon}]}]` | `core.js` `getStreamMixSettings`, стор `LOAD_VK_MIX_SETTINGS` | `getMixSettings`, лист «Настроить микс» на главной |
| Микс по треку: `mix_id=track_mix`, `entity_id=<owner_id>_<id>` | стор renderer, `full_id` | пункт меню трека «Микс по треку» |
| Эквалайзер: 18 фильтров `peaking`, `Q=1`, частоты `31 63 87 125 175 250 350 500 700 1000 1400 2000 2800 4000 5600 8000 11200 16000`, 26 пресетов (дБ) | renderer `createFrequencies`, `presets.json` | `player/audio/EqualizerAudioProcessor` (биквады RBJ в `AudioProcessor` Media3) |
| Нормализатор: K-взвешивание (полка + ФВЧ 38 Гц), громкость в LUFS, `gain = clamp(target − L, ±max)`, `target=-14`, `max=10` | renderer `calculateGain` / `measureLoudness` | `player/audio/LoudnessNormalizerAudioProcessor` |
| «Перемотка в начало»: «назад» перезапускает трек, если прошло ≥ 5 с | `service.json` `rewind: 5`, `SETTINGS.PLAYER.REWIND` | `ForwardingPlayer` в `PlaybackService` |
| Сохранение текущего трека и воспроизведение при запуске | `SETTINGS.PLAYER.LATEST` | `PlaybackStateStore` + восстановление в `PlayerConnection` |
| Скорость воспроизведения | `config.player.playbackRate` | кнопка скорости в плеере |
| Темы: `classic #141414/#E9003F`, `modern #242529/#8c78f3`, `vk-black #121212/#71aaeb`, `vk-white #ffffff/#5181b8`, `spotify #181818/#1DB954`, `higurashi #ffffff/#ff0000` | `themes.json` | `ui/theme/MeridiusThemes.kt` |
| Навигация: Главная / Музыка / Обзор / Друзья / Сообщества; «Музыка» с вкладками и фильтром «Поиск музыки» | `langs/ru.json` `NAVIGATION`, `PLAYLIST.TABS`, скриншоты | нижняя навигация и вкладки «Музыка · Плейлисты · Загрузки» |
| Шапка плейлиста: обложка, описание, «N прослушиваний • N треков • длительность», «Обновлено…», круглая кнопка | скриншот 0, `PLAYLIST.*` | `PlaylistHeader` |
| Меню трека: добавить, в плейлист, микс по треку, текст, поделиться, скачать, убрать из очереди | `CONTEXT_MENU.SONG` | меню `TrackRow` |
| Меню плейлиста: подписаться/отписаться, редактировать, поделиться, скачать, удалить | `CONTEXT_MENU.PLAYLIST` | меню на экране плейлиста |
| Очередь «Сейчас играет» | `QUEUE.NOW_PLAYING`, скриншоты | лист очереди в плеере |
| Страница исполнителя: «Популярное», «Плейлисты» | `ARTIST_PAGE` | экран исполнителя |

## Вход и веб-API музыки (seen, meridius-core `HTTPClient`, `AudioRequests`, `PlaylistsRequest`)

Вход в Meridius — только QR-код VK ID, без пароля:

1. `GET id.vk.ru/auth` с `configuration.auth.pageParams` (`action`=base64 `{"name":"qr_auth",…}`,
   `response_type=silent_token`, `app_id=7913379`, случайный `uuid` из 6 букв) → из HTML берётся `window.init`
   (`auth.host_app_id`, `auth.anonymous_token`).
2. `POST api.vk.ru/method/auth.getAuthCode?v=5.258&client_id=<host_app_id>` → `auth_hash`, `auth_url` (в QR),
   `expires_in` (unix-время). Заголовки: Firefox UA, `Origin/Referer: https://id.vk.ru`.
3. Каждые 3 с `auth.checkAuthCode` (`auth_hash`, `web_auth=1`): 0 — ждём, 5 — нужен код из приложения
   (`auth.validateAuthCode`), 2 — готово, есть `super_app_token`.
4. `POST login.vk.ru/?act=connect_code_auth` (`token`, `uuid`, `app_id`, `oauth_response_type=silent_token`,
   `to`=base64 `https://vk.ru/`, `version=1`) → переход на `data.next_step_url` ставит cookie vk.ru.
5. `POST login.vk.ru/?act=web_token` (`version=1`, `app_id=6287487`) по cookie → `access_token`; обновляется
   каждые 300 с (`webTokenRefreshTimeout`).

Музыка — `POST vk.ru/al_audio.php` по cookie, ответ `{"payload":[code,[...]]}`:
музыка владельца — плейлист `playlist_id=-1` через `load_section type=playlist` (так в Meridius заведена
«Моя музыка»), запасной путь — `act=section` (`all`) с продолжением `load_catalog_section` по `next_from`;
поиск — `act=section section=search`, треки из `playlists[0].list` с продолжением по `playlist.nextOffset` (как `queryExtended`); в Android-версии это запасной путь, основной — официальный `audio.search` с web-токеном (веб-выдача давала неполный и нерелевантный список); `reload_audios` (ссылки по `owner_id_id_actionHash_urlHash`, пачками по 10), `owner_playlists`,
`load_section type=playlist`, `save_playlist` (хеш `newPlaylistHash` со страницы), `delete_playlist`,
`follow_playlist`, `more_playlists_add` + `save_audio_in_playlists`, `add`, `delete_audio`, `get_lyrics`.
Аудио — массив, индексы полей из `Static.AudioObject`; ссылки замаскированы (`audio_api_unavailable`),
маска снимается `ExposeSource` ([AudioUrlDecoder.kt](../app/src/main/java/com/vkmusic/data/web/AudioUrlDecoder.kt)).
Официальный API с web-токеном Meridius использует для VK Микса (и проверяет, доступен ли с ним `audio.get`).

Отличия Android-версии: ссылки получаются не на весь список сразу, а для текущего и двух следующих
треков очереди; на телефоне QR можно подтвердить кнопкой «Подтвердить в приложении VK» (открывает
`auth_url`); дополнительно есть вход через WebView (страница vk.ru → cookie → тот же `web_token`).
Профиль, друзья, сообщества, исполнители, похожие и рекомендации идут через официальный API с web-токеном —
Meridius разбирает для них HTML-страницы, это не переносилось.

## Inferred (в Meridius не видно)

- Резервные официальные методы с web-токеном: `audio.getLyrics` (если веб `get_lyrics` пуст),
  `audio.getAudiosByArtist`, `audio.getAlbumsByArtist`, `audio.getRecommendations`, `audio.searchAlbums`,
  `groups.get`, `friends.get`, `users.get`. Работает ли с web-токеном каждый из них — не проверено
  (Meridius сам проверяет только `audio.get`); при ошибке экран покажет её текст.
- Поля плейлиста в `owner_playlists`/`load_section` разбираются по именам из `getPlaylistInfo`
  (`id`/`playlist_id`, `ownerId`, `accessHash`, `editHash`, `followHash`, `isFollowed`, `listens`, `size`…);
  поле автора (`authorName`) и поле `begin` в таймкодах текста — предположение.
- Вход через WebView считается успешным, когда `web_token` отвечает `okay` по cookie страницы vk.ru.
- Ссылка из QR (`auth_url`) открывается приложением VK на том же телефоне — не проверено.

## Не перенесено (только десктоп)

Discord Rich Presence, вывод для OBS, встроенный веб-сервер, трей, горячие клавиши, мини-окно, акрил,
масштаб интерфейса, прокси, импорт из YouTube, патчи CSS. Плавный переход между треками (crossfade) требует
второго плеера — не делали. Last.fm-скробблинг — кандидат на следующий шаг.
