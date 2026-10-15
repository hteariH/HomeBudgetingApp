# PersonalMoney

Android-приложение для учёта личных финансов — клиент собственного (self-hosted) сервера PersonalMoney: адрес сервера вводится при входе, данные хранятся на устройстве и синхронизируются с ним. Основной фокус — быстрое внесение расходов, включая виджет на главный экран. Без рекламы и покупок.

> Этот файл частично устарел (написан до перехода на свой сервер); актуальное описание — [CLAUDE.md](CLAUDE.md) и [README в корне](../README.md).

## Что реализовано

- ✅ **Quick Add** — главный экран ввода: своя numpad-клавиатура с калькулятором, селектор счётов и категорий, заметки, дата, мгновенное сохранение
- ✅ **Glance-виджет** быстрого расхода (deep link на Quick Add из домашнего экрана)
- ✅ **Счета** — CRUD с иконками, цветами, мультивалютностью, балансами в базовой валюте
- ✅ **Категории** — раздельно доходы/расходы, иконки, цвета, защита от удаления используемых
- ✅ **Транзакции** — лента с группировкой по датам, фильтры по типу/счёту/категории, поиск
- ✅ **Переводы** между счетами с учётом разных валют и комиссии
- ✅ **Мультивалютность** — **80+ встроенных валют** (фиат + крипто USDT/USDC/BTC/ETH + золото/серебро/платина), поиск, включение/выключение, **добавление кастомных** (код / название / символ / точность 0–6), курсы через бесплатный Frankfurter API (ECB), кэш в Room, фоновое обновление WorkManager
- ✅ **Подписки** — CRUD с частотами (день/неделя/месяц/год), ежедневный worker, авто-начисление, агрегаты «в месяц / в год»
- ✅ **Дашборд** — общий капитал, расходы/доходы за месяц, **горизонтальный bar-chart расходов по категориям**, быстрые действия, последние операции
- ✅ **Биометрический lock** (отпечаток / лицо / device PIN) с переключателем в настройках
- ✅ **Бэкап** — экспорт/импорт JSON через SAF, всё на устройстве
- ✅ **Настройки** — базовая валюта, тема (system/light/dark), Material You Dynamic Color, биометрия, ссылка на Privacy Policy
- ✅ **Локализация** — `ru`, `en`, `uk`
- ✅ **Тесты** — unit для Calculator, SubscriptionScheduler, FxSnapshot

## Стек

- Kotlin 2.1 + Jetpack Compose
- Material 3 (Dynamic Color на Android 12+)
- Room + DataStore + WorkManager + Hilt
- Glance 1.1 (виджет)
- Vico (графики, заложено)
- Frankfurter API через Retrofit + OkHttp + kotlinx-serialization

## Требования

- Android Studio Ladybug (2024.2.1) или новее
- JDK 17
- Android SDK 35
- Min SDK: 26 (Android 8.0)

## Первый запуск

1. Откройте проект в Android Studio (`File → Open → /home/mamoru/Projects/PersonalMoney`).
2. Android Studio автоматически загрузит Gradle 8.10.2 и зависимости.
3. Запустите конфигурацию **app** на эмуляторе (API 26+) или устройстве.

### Из командной строки

```bash
cd /home/mamoru/Projects/PersonalMoney
# Один раз: сгенерируйте gradle wrapper (требуется системный gradle 8.10+)
gradle wrapper --gradle-version 8.10.2

# Затем
./gradlew assembleDebug         # сборка
./gradlew test                  # unit-тесты
./gradlew installDebug          # установка на подключённое устройство
```

## Структура проекта

```
app/src/main/java/com/personalmoney/
├── core/
│   ├── calc/             # калькулятор выражений
│   ├── currency/         # FX-курсы, конвертер, Frankfurter API, WorkManager
│   ├── data/             # репозитории, UserPreferences (DataStore)
│   ├── database/         # Room: entities, DAOs, seeding
│   ├── di/               # Hilt модули
│   ├── domain/           # модели и enums
│   └── ui/               # тема Material 3, переиспользуемые composables, IconLibrary
├── feature/
│   ├── accounts/         # CRUD счетов
│   ├── categories/       # CRUD категорий
│   ├── dashboard/        # главный экран
│   ├── quickadd/         # экран быстрого ввода (главный фокус)
│   ├── settings/         # настройки + бэкап
│   ├── subscriptions/    # подписки + worker
│   ├── transactions/     # история и фильтры
│   └── transfers/        # переводы между счетами
├── navigation/           # Routes + NavHost
└── widget/               # Glance виджет
```

## Релиз

Подписанный APK публикуется в GitHub Releases при пуше тега версии; шаги — в [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md).

## Архитектурные принципы

- ViewModel(StateFlow) → Compose, однонаправленный поток
- Hilt для DI
- Room + Flow для реактивных запросов
- Денежные суммы хранятся как `Long` минорных единиц
- `kotlinx.serialization` для бэкапа JSON
- Никаких сетевых запросов, кроме своего сервера и FX-курсов; никакой рекламы и аналитики

## Лицензия

GNU AGPL v3.0, см. [LICENSE](../LICENSE).
