# Filvless: происхождение исходников

Filvless 0.1 — производная работа на основе v2rayNG.

- Android-клиент: https://github.com/2dust/v2rayNG
- Ревизия исходной основы: `a9b1242686aff04f4b3d06f03f29379642fbfd26`.
- Лицензия клиента: GNU GPL v3, полный текст в `LICENSE`.
- Xray Android wrapper: https://github.com/2dust/AndroidLibXrayLite,
  `d0c6c4ae1b09c912070c8288bd0dbcc2e492ac29`, релиз `v26.9.9`.
- HEV tunnel: https://github.com/heiher/hev-socks5-tunnel,
  `64cc609f945253b0e9ebc56317d544268f3c68c1`.

Лицензии нативных компонентов находятся в соответствующих подмодулях.
Оригинальное описание проекта сохранено в `docs/UPSTREAM-README.md`.

Изменения Filvless: отдельный applicationId `ru.filvless.vpn`, название и
иконка, фиолетовый главный экран и настройки, импорт через главный экран,
индикация отказа провайдера, таймер от момента запуска ядра,
отключение Android backup и ссылки на репозиторий Filvless.

Исходники и ресурсы VPNUS не включены. Предоставленные пользователем скриншоты
использованы как ориентир по расположению элементов и цветовой теме.

## Filvless 0.2.0-preview
Added purple settings UI, persisted preferences, application language selection, subscription expiry metadata and Telegram support links.

## Filvless 0.3.0-preview
Correct localized app labels; compact fixed connection controls, flat server list, clipboard prefill and subscription HWID compatibility.

## Filvless 0.4.0-preview
User-supplied launcher artwork with an adaptive vector rendition; compact server rows, header ping action, connection vibration and stronger purple connection animation.

## Filvless 0.5.0-preview
Opt-in periodic subscription refresh with retry handling; Filvless universal APK update discovery and Android notifications; explicit domain routing overlays for generated and custom Xray configurations; application routing access and restart handling. Added unit and Android worker integration tests.

## Filvless 0.6.0-preview
Device-management Android UI and a separate Python WSGI gateway for Remnawave v3.
Gateway authenticates subscription bearer credentials, checks device ownership,
and keeps panel API credentials server-side. Added gateway security tests,
Android parsing tests and Compose confirmation/error-state tests.
