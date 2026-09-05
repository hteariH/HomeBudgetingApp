# Google Play store listing — PersonalMoney

Paste each block into the matching language column in **Play Console → Grow → Store
presence → Main store listing**. The counts in the headings are Play's hard limits.

> Rewritten 2026-09-05. The previous copy sold an offline, account-free app ("no account,
> no cloud sync, your data never leaves your device"). PersonalMoney has an account, a
> server and bank imports now, so that text was not just stale — it contradicted the Data
> safety form, which is the fastest way to get a release rejected.

---

## Image assets

| Asset | Spec | Where |
| --- | --- | --- |
| App icon | 512 × 512 PNG, 32-bit, no transparency | `docs/icon-512.png` |
| Feature graphic | 1024 × 500 PNG/JPG, no transparency | `docs/feature-graphic.png` |
| Phone screenshots | 2–8, min 320 px, max 3840 px | `docs/screenshots/` — 4 present, stale |
| Tablet screenshots | optional, ranks better | not produced |

**The screenshots on disk are stale.** They pre-date the account flow, the Premium screen,
the Banks screen and the current header. Re-take them on the emulator against the release
build before uploading:

    $adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    & $adb shell screencap -p /sdcard/s.png
    & $adb pull /sdcard/s.png "client\docs\screenshots\01-dashboard.png"
    & $adb shell rm /sdcard/s.png

Worth showing: Dashboard with a real balance, Transactions, Quick Add with the calculator,
Analytics, Banks/import, and the home-screen widget.

---

## English (`en-US` — default)

### App name (max 30)

    PersonalMoney: Budget Tracker

### Short description (max 80)

    Track spending across accounts and currencies. Synced to all your devices.

### Full description (max 4000)

    PersonalMoney is a personal budgeting app built for people whose money does not sit in
    one place: several accounts, several currencies, and a phone that is not always online.

    FAST ENTRY
    • Quick Add with a built-in calculator — type 12.50+3, save, done
    • A home-screen widget, so an everyday expense never needs the app opened
    • Scan a paper receipt with the camera; the text is read on your device, never uploaded

    MANY ACCOUNTS, MANY CURRENCIES
    • 80+ world currencies, plus crypto (BTC, ETH, USDT, USDC) and metals (gold, silver,
      platinum) — and you can add your own
    • One total balance across all of them, converted with European Central Bank rates
    • Transfers between your own accounts, including across currencies

    WORKS OFFLINE, SYNCS WHEN IT CAN
    • Everything is stored on your device first, so the app is usable on a plane or in a
      basement
    • Sign in and the same data appears on your other devices and in the browser at
      personalmoney.app
    • Full JSON export and import — your data stays yours

    IMPORT INSTEAD OF TYPING
    • Connect Monobank with a personal token and let statements arrive on their own
    • Link a HomeMoney account and bring your history across
    • Import a CSV statement from any bank

    SUBSCRIPTIONS AND PLANNING
    • Track recurring charges — streaming, gym, rent, taxes — with automatic monthly
      entries and reminders
    • Analytics by category and by month, so the pattern is visible rather than guessed at

    MADE TO LIVE WITH
    • Material You design that follows your wallpaper
    • Optional biometric lock — fingerprint, face, or device PIN
    • English, Русский and Українська

    PRIVACY
    Your account data is stored on our own server in the EU and sent over HTTPS. Bank
    credentials are encrypted and never leave the server. Receipt photos are read on your
    device and deleted immediately. You can delete your account, and everything in it,
    from inside the app at any time. Full policy: https://personalmoney.app/privacy.html

    FREE AND PREMIUM
    PersonalMoney is free and shows ads. An optional Premium subscription raises the
    account limit and removes the ads. No trial gimmicks, no locked export.

### Release notes (max 500) — 0.1.0

    First closed test of PersonalMoney. Multi-currency accounts, offline-first entry with
    a Quick Add widget, receipt scanning, Monobank and HomeMoney import, CSV statements,
    subscriptions, analytics, and sync across your devices. Please report anything that
    breaks — that is what this test is for.

---

## Русский (`ru-RU`)

### Название (max 30)

    PersonalMoney: учёт расходов

### Краткое описание (max 80)

    Учёт трат по счетам и валютам. Синхронизация на всех ваших устройствах.

### Полное описание (max 4000)

    PersonalMoney — приложение для учёта личных финансов, рассчитанное на тех, у кого
    деньги лежат не в одном месте: несколько счетов, несколько валют и телефон, который
    не всегда в сети.

    БЫСТРЫЙ ВВОД
    • Быстрое добавление со встроенным калькулятором — наберите 12.50+3 и сохраните
    • Виджет на домашнем экране: обычную трату можно записать, не открывая приложение
    • Сканирование бумажного чека камерой — текст распознаётся на устройстве и никуда
      не отправляется

    МНОГО СЧЕТОВ, МНОГО ВАЛЮТ
    • 80+ валют мира, криптовалюты (BTC, ETH, USDT, USDC) и металлы (золото, серебро,
      платина); можно добавить свои
    • Общий баланс по всем счетам, пересчёт по курсам Европейского центробанка
    • Переводы между своими счетами, в том числе между валютами

    РАБОТАЕТ БЕЗ СЕТИ, СИНХРОНИЗИРУЕТСЯ КОГДА МОЖЕТ
    • Всё сначала сохраняется на устройстве, поэтому приложение работает и в самолёте
    • После входа те же данные появляются на других устройствах и в браузере
      на personalmoney.app
    • Полный экспорт и импорт в JSON — данные остаются вашими

    ИМПОРТ ВМЕСТО РУЧНОГО ВВОДА
    • Подключите Monobank по персональному токену, и выписки будут приходить сами
    • Свяжите аккаунт HomeMoney и перенесите историю
    • Импорт CSV-выписки из любого банка

    ПОДПИСКИ И ПЛАНИРОВАНИЕ
    • Учёт регулярных платежей — стриминг, спортзал, аренда, налоги — с автоматическими
      ежемесячными записями и напоминаниями
    • Аналитика по категориям и месяцам

    УДОБНО КАЖДЫЙ ДЕНЬ
    • Material You: оформление подстраивается под обои
    • Биометрический замок по желанию — отпечаток, лицо или PIN устройства
    • English, Русский и Українська

    КОНФИДЕНЦИАЛЬНОСТЬ
    Данные аккаунта хранятся на нашем сервере в ЕС и передаются по HTTPS. Банковские
    доступы шифруются и никогда не покидают сервер. Фотографии чеков распознаются
    на устройстве и сразу удаляются. Аккаунт вместе со всеми данными можно удалить прямо
    в приложении. Политика: https://personalmoney.app/privacy.html

    БЕСПЛАТНО И PREMIUM
    Приложение бесплатное и показывает рекламу. Подписка Premium снимает ограничение
    на количество счетов и убирает рекламу. Экспорт не заблокирован.

### Что нового (max 500) — 0.1.0

    Первое закрытое тестирование PersonalMoney. Мультивалютные счета, быстрый ввод
    и виджет, сканирование чеков, импорт из Monobank и HomeMoney, CSV-выписки, подписки,
    аналитика и синхронизация между устройствами. Сообщайте обо всём, что сломается —
    ради этого тест и нужен.

---

## Українська (`uk`)

### Назва (max 30)

    PersonalMoney: облік витрат

### Короткий опис (max 80)

    Облік витрат за рахунками й валютами. Синхронізація на всіх пристроях.

### Повний опис (max 4000)

    PersonalMoney — застосунок для обліку особистих фінансів для тих, у кого гроші лежать
    не в одному місці: кілька рахунків, кілька валют і телефон, який не завжди онлайн.

    ШВИДКЕ ВВЕДЕННЯ
    • Швидке додавання з вбудованим калькулятором — наберіть 12.50+3 і збережіть
    • Віджет на домашньому екрані: щоденну витрату можна записати, не відкриваючи
      застосунок
    • Сканування паперового чека камерою — текст розпізнається на пристрої й нікуди
      не надсилається

    БАГАТО РАХУНКІВ, БАГАТО ВАЛЮТ
    • 80+ світових валют, криптовалюти (BTC, ETH, USDT, USDC) і метали (золото, срібло,
      платина); можна додати власні
    • Спільний баланс за всіма рахунками, перерахунок за курсами Європейського
      центробанку
    • Перекази між власними рахунками, зокрема між валютами

    ПРАЦЮЄ БЕЗ МЕРЕЖІ, СИНХРОНІЗУЄТЬСЯ КОЛИ МОЖЕ
    • Усе спершу зберігається на пристрої, тож застосунок працює й у літаку
    • Після входу ті самі дані з'являються на інших пристроях і в браузері
      на personalmoney.app
    • Повний експорт та імпорт у JSON — дані залишаються вашими

    ІМПОРТ ЗАМІСТЬ РУЧНОГО ВВЕДЕННЯ
    • Підключіть Monobank за персональним токеном, і виписки надходитимуть самі
    • Прив'яжіть акаунт HomeMoney і перенесіть історію
    • Імпорт CSV-виписки з будь-якого банку

    ПІДПИСКИ ТА ПЛАНУВАННЯ
    • Облік регулярних платежів — стримінг, спортзал, оренда, податки — з автоматичними
      щомісячними записами й нагадуваннями
    • Аналітика за категоріями та місяцями

    ЗРУЧНО ЩОДНЯ
    • Material You: оформлення підлаштовується під шпалери
    • Біометричне блокування за бажанням — відбиток, обличчя або PIN пристрою
    • English, Русский та Українська

    КОНФІДЕНЦІЙНІСТЬ
    Дані акаунта зберігаються на нашому сервері в ЄС і передаються через HTTPS.
    Банківські доступи шифруються й ніколи не залишають сервер. Фото чеків розпізнаються
    на пристрої й одразу видаляються. Акаунт разом з усіма даними можна видалити просто
    в застосунку. Політика: https://personalmoney.app/privacy.html

    БЕЗКОШТОВНО ТА PREMIUM
    Застосунок безкоштовний і показує рекламу. Підписка Premium знімає обмеження
    на кількість рахунків і прибирає рекламу. Експорт не заблоковано.

### Що нового (max 500) — 0.1.0

    Перше закрите тестування PersonalMoney. Мультивалютні рахунки, швидке введення
    й віджет, сканування чеків, імпорт із Monobank і HomeMoney, CSV-виписки, підписки,
    аналітика та синхронізація між пристроями. Повідомляйте про все, що зламається —
    заради цього тест і потрібен.

---

## Contact details (Play Console → Store listing)

| Field | Value |
| --- | --- |
| Email | aberkunsky@gmail.com |
| Website | https://personalmoney.app |
| Privacy policy | https://personalmoney.app/privacy.html |
| Phone | optional, leave blank |

## Categorisation

| Field | Value |
| --- | --- |
| App or game | App |
| Category | Finance |
| Tags | Budgeting, Expense tracking, Personal finance |
