# Вход через VK ID

Приложение авторизуется по схеме VK ID (экосистемный OAuth) на `api.vk.ru`:

1. `auth.getAnonymToken` — анонимный токен приложения (по `client_id` + `client_secret`);
2. `auth.validateAccount` — проверка логина, подсказывает доступные способы подтверждения;
3. `POST /oauth/token` (`grant_type=password`) — финальный `access_token`,
   либо `need_validation` (2FA) или `need_captcha`.

## Код подтверждения на почту

При `need_validation` VK отдаёт `validation_sid` и `validation_type` (`2fa_email`, `2fa_sms`, `2fa_push`, `2fa_app`):

1. `POST /method/ecosystem.sendOtpEmail` с `sid`, анонимным `access_token` и параметрами экосистемы
   (`lang`, `device_id`, `sak_version`, `flow_type=tg_flow`, `api_id`, `https=1`) — **только этот запрос
   заставляет VK разослать код**; сам `need_validation` кода не отправляет. Есть и `sendOtpSms`/`sendOtpPush`.
   Приложение делает это автоматически при `validation_type` = `2fa_email`/`2fa_sms`/`2fa_push`,
   а в UI остаётся кнопка «Отправить код на почту» для повторной отправки;
2. повтор `POST /oauth/token` с теми же логином/паролем и **`sid` + `code`** — только такая пара
   подтверждает вход, без `sid` VK игнорирует код.

Код: [`VkAuthApi.login`](../app/src/main/java/com/vkmusic/data/api/vk/VkAuthApi.kt) (`sid`, `code`) и
`VkAuthApi.sendOtp`, состояние шага — [`LoginViewModel`](../app/src/main/java/com/vkmusic/ui/screens/login/LoginViewModel.kt).

Код: [`VkAuthApi`](../app/src/main/java/com/vkmusic/data/api/vk/VkAuthApi.kt),
параметры: [`VkIdConfig`](../app/src/main/java/com/vkmusic/data/api/vk/VkConfig.kt).

## Откуда берутся CLIENT_ID и CLIENT_SECRET

Это реквизиты эталонного Android-клиента VK, извлечённые из его APK (`classes.dex`), а не нашего приложения:
`client_id`/`api_id` = `2274003`, `client_secret` = `hHbZxrka2uZ6jB1inYsH` (место в коде — `0x1208a2`/`0x1208b4`,
сбор параметров `auth.getAnonymToken`). Значения лежат в `local.properties` (в `.gitignore`) и попадают в `BuildConfig`.

Что это значит на практике:

- вход работает только пока VK считает это приложение доверенным; API может отдать `invalid_client`
  или потребовать подтверждение устройства в любой момент;
- использование чужих реквизитов и выдача себя за официальный клиент (`User-Agent`, `X-VK-Android-Client`)
  нарушает правила VK — вариант для личного использования, а не для публичной раздачи;
- заголовки UA + `X-VK-Android-Client: new` эталонный клиент ставит на все запросы одним билдером
  (`0x120d4a`—`0x120dca`), поэтому они уходят и в `api.*`, и при скачивании ссылок/HLS
  (см. [`VkHeadersInterceptor`](../app/src/main/java/com/vkmusic/data/api/vk/VkHeadersInterceptor.kt));
- `v=5.276` и `scope=all` в [`VkIdConfig`](../app/src/main/java/com/vkmusic/data/api/vk/VkConfig.kt) —
  те же значения, что эталонный клиент отправляет в `oauth/token` (`0xc9cee`, `0xc9c6c`).

## Как получить свои значения легально

`client_secret` для `grant_type=password` выдаётся только **доверенным** приложениям —
обычная кнопка «Создать приложение» его для этого потока не включает. Варианты, по порядку реалистичности:

1. **VK ID SDK вместо пароля (рекомендуется).** Официальный и поддерживаемый способ входа —
   это [VK ID SDK](https://id.vk.com/about/business/go/docs/ru/vkid/latest/vk-id/intro/start-page):
   вход открывается через браузер/приложение VK, пароль пользователь вводит на стороне VK, а приложение
   получает токен по OAuth 2.1 (PKCE) — **без** `client_secret` в коде. Нужен только публичный `client_id`
   (app_id) из [vk.com/apps?act=manage](https://vk.com/apps?act=manage) → «Создать приложение» → платформа
   «Android», указать имя пакета `com.vkmusic` и SHA‑256 подписи. Это убирает весь ручной поток пароля/2FA/капчи.
   Минус: такой пользовательский токен обычно **не** открывает методы `audio.*` — ими пользуются только
   «официальные» клиенты, и это та самая серая зона, из‑за которой сторонние плееры берут чужие `client_id`.

2. **Свой server‑side обмен.** Поднять собственный бэкенд, получать там VK ID‑токен и обменивать/хранить —
   `client_secret` остаётся на сервере, а не в APK. Для личного использования это самый чистый вариант,
   но требует сервера.

3. **Запросить доверенный статус у VK.** Написать в поддержку VK ID / для бизнеса и запросить параметры
   для `password`‑потока. На практике сторонним аудио‑клиентам его не дают.

Коротко: легально и просто — **VK ID SDK** (пункт 1), но он, скорее всего, не даст `audio.*`.
Поток с паролем (`VkAuthApi`) готов и заработает, как только в `VkIdConfig` появятся рабочие
`CLIENT_ID`/`CLIENT_SECRET` доверенного приложения.

## Как заполнить

Значения лежат в `local.properties` (он в `.gitignore`, в репозиторий не попадает). Впишите свои:

```properties
vk.clientId=<ваш client_id>
vk.clientSecret=<ваш client_secret>
```

Gradle читает их и кладёт в `BuildConfig.VK_CLIENT_ID` / `BuildConfig.VK_CLIENT_SECRET`
(см. `app/build.gradle.kts`), а [`VkIdConfig`](../app/src/main/java/com/vkmusic/data/api/vk/VkConfig.kt)
берёт их оттуда. Пустые значения — приложение работает в демо-режиме. Шаблон: `local.properties.sample`.
