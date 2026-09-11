# 📺 Video Viewer TV (Android TV)

Тонкая **WebView-обёртка** вокруг веб-интерфейса основного проекта
[`../video_viewer`](../video_viewer). Весь интерфейс (каталог, плеер,
авторизация, «продолжить просмотр», локализация) живёт на сервере — приложение
просто открывает его в TV-режиме: `http://<сервер>/?tv=1`.

> **Почему «99% обновлений с основного проекта»:** вы правите `web/*` в
> `video_viewer` — телевизор получает изменения сразу, **без пересборки APK**.
> В APK остаётся лишь оболочка (~1%): адрес сервера, экран ошибки, проброс
> Back и медиа-клавиш.

## Архитектура

```
┌─ video_viewer (основной проект) ────────────────────────────┐
│  web/index.html + app.js + tv.js + tv.css  ← TV-режим       │
│  gateway :8080 раздаёт статику и /api/*                     │
└───────────────▲─────────────────────────────────────────────┘
                │ http://<сервер>/?tv=1   (+ UA-маркер VVTV/1.0)
┌─ video_viewer_android ───────────────────────────────────────┐
│  MainActivity   = WebView (весь сайт)                       │
│  SettingsActivity = адрес сервера (кнопка MENU на пульте)    │
└──────────────────────────────────────────────────────────────┘
```

TV-режим на веб-стороне (`web/tv.js`) включается по `?tv=1`, `localStorage
vv_tv=1` или маркеру `VVTV/1.0` в User-Agent и добавляет:
- фокус-навигацию с пульта (стрелки/D-pad к ближайшему элементу),
- активацию по Enter/OK,
- закрытие оверлеев/плеера кнопкой Back (`window.__vvBack()` → `consumed|exit`).

## Подключение к серверу

На первом запуске приложение открывает экран настроек (потом — кнопка **MENU**
на пульте). Вводится только адрес сервера:

| Где смотрите | Что вводить | Что откроется |
|---|---|---|
| Домашняя сеть (Wi-Fi) | `192.168.0.10` — IP компьютера с Docker | `http://192.168.0.10:8080/?tv=1` |
| Интернет | `vv-home.duckdns.org` (ваш домен) | `https://vv-home.duckdns.org/?tv=1` |

- Схема `http://` и порт `8080` подставляются автоматически, если не указаны
  (для `https://` порт не подставляется — там 443).
- Готовые адреса и настройка проброса портов/HTTPS описаны в README основного
  проекта: [«Доступ извне»](../video_viewer/README.md#-доступ-извне-телевизор-телефон).
- Вход выполняется один раз на каждый адрес: сессия (httpOnly-кука) привязана
  к origin, поэтому локальный и публичный адреса — это два разных входа.
- HTTP разрешён на уровне приложения (`usesCleartextTraffic`), так что
  локальный вариант работает без сертификатов.

## Возможности оболочки

- **Локальная сеть или интернет**: адрес сервера задаётся на экране настроек;
  `http://` и порт `8080` подставляются сами, если их не указать.
- **Куки** (`httpOnly`-сессия входа) сохраняются между запусками.
- **Back** спрашивает страницу: закрыть плеер/модалку или выйти.
- **Медиа-клавиши пульта** (Play/Pause/FF/RW) пробрасываются в плеер страницы.
- **Экран ошибки** с «Повторить» и «Настройки», если сервер недоступен.
- **Remote-отладка** через `chrome://inspect` (включена в debug-сборке).

## Сборка

Нужны **JDK 17+ (рекомендуется 21)** и Android SDK. В проекте есть
gradle-wrapper:

```bash
# Windows (PowerShell), при JDK 21 в JAVA_HOME:
.\gradlew.bat assembleDebug      # неподписанная debug-сборка
.\gradlew.bat assembleRelease    # release, подписанная keystore (см. ниже)
```

Если `JAVA_HOME` не указывает на рабочий JDK, задайте его явно, например:

```powershell
$env:JAVA_HOME = 'C:\Users\Andrey\Android\jdk-21'   # портативный JDK 21
.\gradlew.bat assembleRelease
```

Готовые APK:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

### Release-подпись

`app/build.gradle.kts` читает данные подписи из локального файла
**`keystore.properties`** (в `.gitignore`, не коммитится):

```properties
storeFile=C:/path/to/video-viewer-tv-release.keystore
storePassword=...
keyAlias=videoviewertv
keyPassword=...
```

Если файла нет — release собирается с debug-подписью (удобно для CI). Для
публикации в Google Play **обязательно** используйте свой собственный
keystore и храните пароли в надёжном месте (потеря ключа = невозможность
обновить приложение).

## Установка на Android TV

1. Включите на ТВ **ADB-отладку** (Настройки → О системе → О телевизоре →
   собирайте на «Номер сборки» → Разработчики → USB-отладка).
2. С ТВ-пульта узнайте IP: Настройки → Сеть.
3. С компьютера (adb из Android SDK):

```powershell
adb connect <IP-телевизора>:5555
adb install -r app\build\outputs\apk\release\app-release.apk
```

4. Запустите «Video Viewer TV» с пульта, на первом экране введите адрес
   сервера (например `192.168.1.10:8080`) и нажмите Save.

## Иконки и баннер

Ассеты генерируются скриптом `tools/gen_icons.ps1` (нужен .NET / PowerShell):
перегенерировать можно после смены цветов в скрипте:

```powershell
powershell -ExecutionPolicy Bypass -File tools\gen_icons.ps1
```

- Launcher-иконка: `res/mipmap-*/ic_launcher.png` + adaptive (`mipmap-anydpi-v26`).
- TV-баннер (320×180@xhdpi): `res/drawable-*/tv_banner.png`.

## Структура

```
app/src/main/java/com/zeoril/videoviewertv/
  MainActivity.kt      — WebView-оболочка (настройка, ошибки, Back, медиа-клавиши)
  SettingsActivity.kt  — экран ввода адреса сервера
  Prefs.kt             — константы (SharedPreferences, UA-маркер)
app/src/main/AndroidManifest.xml
tools/gen_icons.ps1    — генератор иконок/баннера
```
