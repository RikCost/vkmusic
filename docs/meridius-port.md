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

## Inferred (в Meridius не видно, взято из официального клиента/документации VK)

Meridius получает тексты песен и страницы исполнителей скрейпингом веб-версии (`al_audio.php`,
`act=get_lyrics`) по cookie-сессии — на Android такой сессии нет. Поэтому здесь официальные методы:
`audio.getLyrics(audio_id)`, `audio.getAudiosByArtist`, `audio.getAlbumsByArtist`, `groups.get`,
`audio.getPlaylistById`, `audio.editPlaylist`, `audio.deletePlaylist`, `audio.followPlaylist`, `audio.getById`.
Если какой-то из них VK вернёт с ошибкой, экран покажет её текст.

## Не перенесено (только десктоп)

Discord Rich Presence, вывод для OBS, встроенный веб-сервер, трей, горячие клавиши, мини-окно, акрил,
масштаб интерфейса, прокси, импорт из YouTube, патчи CSS. Плавный переход между треками (crossfade) требует
второго плеера — не делали. Last.fm-скробблинг — кандидат на следующий шаг.
