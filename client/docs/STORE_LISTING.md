# Google Play store listing — PersonalMoney

Copy each block into the corresponding language column in **Play Console → Main store listing**. Character counts are the Play Console hard limits as of 2026.

---

## Image assets (required)

| Asset | Spec | Source |
| --- | --- | --- |
| App icon | 512 × 512 PNG, 32-bit RGBA | export from Android Studio Image Asset |
| Feature graphic | 1024 × 500 JPG/PNG, no transparency, no embedded UI screenshots | design — see template idea below |
| Phone screenshots | 4–8 images, 1080 × 1920 (or 9:16), PNG/JPG | take on emulator or device with `adb shell screencap` |
| 7" tablet screenshots (optional but ranks better) | 1200 × 1920 | optional |
| Promo video (optional) | YouTube URL | optional |

**Feature graphic template:** dark indigo background (`#3D4499`), large white "PersonalMoney" wordmark on left, on right a Pixel-style phone mockup showing the Dashboard's "Total balance" card. No words that imply gambling, scams, or financial services regulation.

Take Dashboard, Quick Add, Widget, Subscriptions and Settings screenshots for the gallery.

---

## English (`en-US` — default)

### App name (max 30)
```
PersonalMoney — Local Expense Tracker
```

### Short description (max 80)
```
Track expenses offline. Multi-currency, fast widget, subscriptions, no cloud.
```

### Full description (max 4000)
```
PersonalMoney is a finance tracker that lives entirely on your device. No account, no
cloud sync, no waiting for a backend — your data stays on your phone.

WHAT IT'S FOR
• Logging expenses in 2 seconds from the Quick Add widget on your home screen
• Following spending across many accounts and currencies in one base view
• Managing recurring subscriptions (Netflix, Spotify, gym, taxes) with automatic
  monthly charges and reminders
• Transfers between your own accounts (including across currencies with manual or
  ECB exchange rates)

WHAT MAKES IT DIFFERENT
• Quick Add screen with a built-in calculator: type 12.50+3, hit save, done
• Home-screen widget — no need to open the app for everyday expenses
• 80+ built-in currencies plus crypto (BTC, ETH, USDT, USDC) and metals
  (gold, silver, platinum). Add your own custom currencies in seconds.
• Multi-currency total balance, automatic exchange rates from the European
  Central Bank (free, no API key)
• Beautiful Material You design that follows your wallpaper
• Optional biometric lock with fingerprint, face, or device PIN
• Full JSON export and import — your data is yours

PRIVACY
Your financial data never leaves your device. The app makes only two network calls:
exchange rates from the public Frankfurter API (currency codes only) and ad delivery
through Google AdMob. See the full privacy policy in Settings.

LANGUAGES
English, Русский, Українська.

FREE WITH ADS
Banner ads on Dashboard and Transactions screens keep the app free. The Quick Add
screen and the home-screen widget are always ad-free.
```

### Release notes (max 500 per release)
```
First public release. Expense tracking, multi-currency accounts, home-screen widget,
recurring subscriptions, transfers, JSON backup, biometric lock, Material You theme,
80+ currencies including BTC/ETH/USDT and gold. Russian, English, Ukrainian.
```

---

## Русский (`ru-RU`)

### Название приложения (макс 30)
```
PersonalMoney — учёт расходов
```

### Краткое описание (макс 80)
```
Учёт финансов оффлайн. Мультивалюты, виджет, подписки, без облака.
```

### Полное описание (макс 4000)
```
PersonalMoney — трекер личных финансов, который работает полностью на вашем
устройстве. Без аккаунта, без облачной синхронизации, без ожидания серверов —
ваши данные остаются у вас.

ЗАЧЕМ ОНО ВАМ
• Внести расход за 2 секунды через виджет на главном экране
• Видеть расходы по нескольким счетам и валютам в единой базовой валюте
• Управлять подписками (Netflix, Spotify, спортзал, налоги) с автоматическим
  начислением и напоминаниями
• Переводы между своими счетами, в том числе мультивалютные с ручными или
  ECB-курсами

ЧТО ВЫДЕЛЯЕТ
• Quick Add экран с встроенным калькулятором: 12.50+3 → сохранить, готово
• Виджет на главный экран — не нужно открывать приложение
• 80+ встроенных валют плюс криптовалюты (BTC, ETH, USDT, USDC) и
  драгметаллы (золото, серебро, платина). Добавьте свои собственные валюты
  за секунды.
• Общий капитал в мультивалюте, автоматические курсы от Европейского
  центробанка (бесплатно, без API-ключа)
• Material You дизайн, подстраивающийся под обои
• Опциональная биометрическая блокировка (отпечаток / лицо / PIN)
• Полный экспорт и импорт JSON — ваши данные принадлежат вам

ПРИВАТНОСТЬ
Финансовые данные не покидают устройство. Приложение делает только два сетевых
запроса: курсы валют через публичный Frankfurter API (только коды валют) и
показ рекламы через Google AdMob. Полная политика конфиденциальности — в
Настройках.

ЯЗЫКИ
Русский, English, Українська.

БЕСПЛАТНО, С РЕКЛАМОЙ
Баннер на дашборде и в истории транзакций. На экране быстрого ввода и в
виджете рекламы нет.
```

### Что нового (макс 500)
```
Первый публичный релиз. Учёт расходов, мультивалюта, виджет на главный экран,
подписки, переводы, JSON-бэкап, биометрический lock, Material You, 80+ валют,
включая BTC/ETH/USDT и драгметаллы. Русский, английский, украинский.
```

---

## Українська (`uk`)

### Назва застосунку (макс 30)
```
PersonalMoney — облік витрат
```

### Короткий опис (макс 80)
```
Облік фінансів офлайн. Мультивалюти, віджет, підписки, без хмари.
```

### Повний опис (макс 4000)
```
PersonalMoney — трекер особистих фінансів, який працює повністю на вашому
пристрої. Без облікового запису, без хмарної синхронізації, без очікування
серверів — ваші дані залишаються у вас.

ДЛЯ ЧОГО ВІН
• Внести витрату за 2 секунди через віджет на головному екрані
• Бачити витрати по кількох рахунках і валютах у єдиній базовій валюті
• Керувати підписками (Netflix, Spotify, спортзал, податки) з автоматичним
  списанням і нагадуваннями
• Перекази між своїми рахунками, зокрема мультивалютні з ручними або
  ECB-курсами

ЩО ВИРІЗНЯЄ
• Quick Add екран із вбудованим калькулятором: 12.50+3 → зберегти, готово
• Віджет на головний екран — застосунок відкривати не треба
• 80+ вбудованих валют плюс крипто (BTC, ETH, USDT, USDC) та дорогоцінні
  метали (золото, срібло, платина). Свої власні валюти додаються за секунди.
• Загальний капітал у мультивалюті, автоматичні курси від Європейського
  центробанку (безкоштовно, без API-ключа)
• Material You дизайн, що узгоджується зі шпалерами
• Опціональне біометричне блокування (відбиток / обличчя / PIN)
• Повний експорт та імпорт JSON — ваші дані належать вам

ПРИВАТНІСТЬ
Фінансові дані не покидають пристрій. Застосунок робить лише два мережеві
запити: курси валют через публічний Frankfurter API (тільки коди валют)
та показ реклами через Google AdMob. Повна політика конфіденційності — у
Налаштуваннях.

МОВИ
Українська, English, Русский.

БЕЗКОШТОВНО, З РЕКЛАМОЮ
Банер на дашборді та в історії транзакцій. На екрані швидкого введення і у
віджеті реклами немає.
```

### Що нового (макс 500)
```
Перший публічний реліз. Облік витрат, мультивалюта, віджет на головний екран,
підписки, перекази, JSON-бекап, біометричне блокування, Material You, 80+
валют, включно з BTC/ETH/USDT та дорогоцінними металами. Українська,
англійська, російська.
```

---

## Categorization (English only)

- **Application type:** Apps
- **Category:** Finance
- **Tags:** budget, expense tracker, finance, personal finance, money manager
- **Content rating:** Everyone (no gambling, no profanity, no scary content)
- **Target audience:** Ages 13+

---

## Contact details

- **Email:** _your support email_
- **Phone:** _optional, leave empty unless required by jurisdiction_
- **Website:** _your privacy policy host or repo URL_
