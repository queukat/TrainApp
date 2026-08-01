# AGENTS.md

## Назначение

Это быстрый внутренний контекст для будущих Codex/agent-сессий по TrainMe. Держи его практичным: сначала понять текущую систему, потом менять минимально нужное.

TrainMe - независимое пассажирское Android-приложение для поездок по железной дороге Черногории. Это не официальный продукт, канал поддержки или билетная система ZPCG AD Podgorica. Пакет приложения: `com.queukat.train`.

## Первые правила работы

- Всегда начинай с `git status --short`. На момент создания этого файла рабочее дерево уже было грязным, поэтому не откатывай и не нормализуй чужие изменения.
- Если правишь код, держи изменение узким и следуй существующим паттернам проекта.
- Для поиска используй `rg` / `rg --files`.
- Для ручных правок используй патчи, не переписывай файлы случайными shell-write командами.
- Временные папки и файлы используй только внутри корня проекта; не создавай их в системных или внешних каталогах.
- Публичный текст, README, Play listing, changelogs и screenshot copy должны следовать `docs/presentation_style.md`.
- Внутренние заметки, тестовые планы и handoff можно писать проще и технически прямее.

## Правила распределения задач и проверок

- Сложные задачи следует делить между несколькими Terra-субагентами; координатор отвечает за план, решения, изменения и финальную проверку.
- Для длительных задач вести единый журнал в `docs/work-notes/`.
- Gradle-проверки выполняет только один назначенный исполнитель; параллельные сборки одного checkout запрещены.
- Проверки должны быть целевыми, не дублироваться без причины и фиксироваться в verification ledger.
- До 2026-08-01 локальные проверки считаются основными, поскольку GitHub Actions недоступен из-за исчерпанных минут.
- Архитектурные дефекты имеют высший приоритет и блокируют зависимую работу.
- Нельзя маскировать сломанный дизайн костылями, suppressions или дополнительными адаптерами.
- Доменные значения должны иметь корректные типы; деньги — integer minor units + currency/scale, а не `String`.
- Парсинг допустим только на внешней границе ввода и выполняется один раз.
- Миграции должны сохранять пользовательские данные и обратную совместимость.
- При более чем трёх связанных исправлениях нужно вести запись в `docs/architecture-findings.md`.
- SonarQube исправлять небольшими логическими пакетами: не менять поведение без тестов, не делать массовое форматирование, предпочитать исправление кода подавлению предупреждений.
- Security hotspots требуют отдельного анализа и документированного решения.
- Перед финальным ответом по SonarQube нужно указать изменённые файлы, команды проверок и оставшиеся риски.

Основные команды проверки:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat check --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat verifyTranslations --console=plain
.\gradlew.bat <module>:testDebugUnitTest --console=plain
```

## Стек и сборка

- Android: `minSdk = 24`, `targetSdk = 35`, `compileSdk = 36`.
- Kotlin: `2.1.10`; Java/JVM target: `17`; Android Gradle Plugin: `8.9.3`.
- UI: Jetpack Compose + Material 3.
- Persistence: Room (`zpcg.db`).
- Network: Retrofit + Gson converter + OkHttp.
- Build system: Gradle Kotlin DSL, version catalog в `gradle/libs.versions.toml`.
- Static checks: detekt, ktlint, JaCoCo/Sonar wiring.

## Карта проекта

- `app/src/main/java/com/queukat/train/MainActivity.kt` - запуск приложения, начальные intent-аргументы, переход в настройки.
- `app/src/main/java/com/queukat/train/SettingsActivity.kt` - настройки языка станций, напоминаний, автообновления и тестовые уведомления.
- `data/api/` - Retrofit gateway к `https://api.zpcg.me/`.
- `data/model/` - Gson-контракты расписаний, станций, цен и сохраненных маршрутов.
- `data/db/` - Room entities/DAO/database для станций и route metadata.
- `data/repository/` - получение станций/маршрутов, кэш cumulative routes, восстановление координат и full-route.
- `ui/` - Compose command center: поиск, cards, dialogs, settings, saved/recent route surfaces, UI state models.
- `util/` - время, локаль, уведомления, alarms, calendar/maps intents и dispatchers.
- `docs/` - публичная подача, release setup, Play release governance.
- `tools/` и `scripts/` - Play upload/listing/screenshot/feature graphic tooling.
- `.github/workflows/` - служебная автоматизация репозитория; публичные Android-релизы выпускаются только через Google Play.

## Product Glossary

- **Signal Acquisition Layer** - Retrofit/Repository слой, который стабилизирует доступ к удаленным timetable/station signals.
- **Localized Station Intelligence** - кэш и выбор названий станций на `en`, `me`, `meCyr`; основа поиска и saved/recent labels.
- **Route Reconstruction Core** - превращает direct/connected responses в route cards, timing, price, route endpoints и full-route views.
- **Transfer Clarity Surface** - раскрытие промежуточных остановок, пересадок, dwell/arrival/departure state и stop labels.
- **Departure Awareness Engine** - расчет времени до отправления, timezone Montenegro и автообновление countdown.
- **Reminder Orchestration Layer** - push reminders, exact alarms, notification permission, calendar intent и failure/status messages.
- **Continuity Vault** - saved routes, recent searches, legacy migration и повторный запуск commuter routes.
- **Operational Telemetry Surface** - user-visible notices для cache/network/server/reminder/permission states.
- **Release Governance Rail** - подписанные AAB, changelog staging, Play validation и explicit upload intent.

## Runtime Facts

- API base URL: `https://api.zpcg.me/`.
- Endpoints:
  - `GET api/stops`
  - `GET api/routes?start=<id>&finish=<id>&date=<yyyy-MM-dd>`
  - `GET api/routes/cumulative`
- Room DB: `zpcg.db`; schema version `1`; destructive migration включена.
- SharedPreferences: `train_prefs`.
- Важные prefs/keys: `appLanguage`, `defaultReminderAction`, `defaultMinutesBefore`, `autoRefreshTime`, `stops_last_update`, `saved_routes_v2`, legacy `saved_routes`, `recent_searches_v1`.
- Reminder extras: `trainNumber`, `minutesBefore`, `stationName`.
- Station language values: `en`, `me`, `meCyr`; `ru` в моделях обычно мапится на Cyrillic fallback.
- App locales: `en`, `ru`, `cnr-Cyrl-ME`, `cnr-Latn-ME`, `sr-Cyrl`, `sr-Latn`.
- Timetable timezone: `Europe/Podgorica`, fallback `Europe/Belgrade`.
- Caches: stops refresh roughly every 24h; cumulative routes in-memory TTL roughly 12h.
- Recent searches limit: `5`.

## Основные команды

Запускай из корня репозитория в PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
.\gradlew.bat :app:bundleRelease
.\gradlew.bat :app:jacocoDebugUnitTestReport
```

Минимальный smoke-check для обычной правки:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Перед handoff полезно проверить:

```powershell
git status --short
git diff --stat
git diff --check
```

## Тестовые ориентиры

- Модели/API mapping: `app/src/test/java/com/queukat/train/data/model/TrainApiModelTest.kt`.
- DB/entity behavior: `app/src/test/java/com/queukat/train/data/db/DbModelTest.kt`.
- Repository result models: `app/src/test/java/com/queukat/train/data/repository/RepositoryResultModelsTest.kt`.
- UI state/result mapping: `RouteSearchMappingTest.kt`, `UiStateModelsTest.kt`.
- Saved/recent route behavior: `SavedRouteSupportTest.kt`, `RecentSearchSupportTest.kt`.
- Timezone/countdown behavior: `DateTimeUtilsTest.kt`.

Если меняешь Compose UI, проверь хотя бы сборку и, когда возможно, экран на устройстве/эмуляторе. Если меняешь строки, проверь affected locales и placeholders.

## Release Guardrails

- Production package по умолчанию: `com.queukat.train`.
- Signing secrets не коммитить. Используются untracked `keystore.properties` или env:
  - `TRAINAPP_KEYSTORE_FILE`
  - `TRAINAPP_KEYSTORE_PASSWORD`
  - `TRAINAPP_KEY_ALIAS`
  - `TRAINAPP_KEY_PASSWORD`
- Play access: `PLAY_KEY_FILE`; опционально `PLAY_PACKAGE_NAME`, `PLAY_DEV_ID`.
- Play changelogs живут в `app/fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
- Текущие changelog locales: `en-GB`, `en-US`, `ru-RU`, `sr`.
- Публикация и любые другие действия с Play Console выполняются только через API.
- Play upload только через явный release rail intent. Validation-only поведение безопаснее по умолчанию.
- Listing images/screenshots не трогать вместе с AAB release, если задача явно не про listing assets.
- Перед публичным релизом changelog должен быть user-facing и без внутреннего implementation jargon.

## Публичный язык

Для README, store copy, release notes и публичных описаний не пиши "просто загрузили станции" или "починили JSON". Используй продуктовый язык из `docs/presentation_style.md`: какой беспорядок обнаружен, какой контроль добавлен, что видит пассажир.

Пример направления:

- Плохо: "Fixed route parsing."
- Лучше: "**Route Reconstruction Core** теперь устойчивее превращает timetable signals в понятные варианты поездки."

Не придумывай официальную аффилиацию, партнерства, гарантии uptime, revenue, масштаб или coverage сверх того, что реально есть в проекте.

## Полезные следующие упрощения

- Добавить `docs/dev_checklist.md` с коротким handoff-чеклистом: status, tests, affected strings/locales, screenshots for UI, release notes for Play-facing changes.
- Добавить `scripts/dev-check.ps1`, который оборачивает `:app:testDebugUnitTest`, опционально `ktlintCheck`, `detekt` и `git diff --check`.
- Если этот файл разрастется, вынести глубокие детали в `docs/architecture_notes.md`, а здесь оставить fast entry point.
- Позже централизовать SharedPreferences keys и reminder extras в коде: сейчас они разбросаны по `MainActivity`, `SettingsActivity`, `TrainViewModel` и `ReminderUtils`.
