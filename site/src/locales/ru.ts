import type { en } from './en';

export const ru: Record<keyof typeof en, string> = {
    'meta.title': 'PersonalMoney — личные и семейные финансы на вашем сервере',

    'nav.features': 'Возможности',
    'nav.install': 'Установка',
    'nav.android': 'Android',
    'header.language': 'Язык',
    'header.toLight': 'Включить светлую тему',
    'header.toDark': 'Включить тёмную тему',

    'hero.badge': 'Свой сервер · открытый код',
    'hero.title': 'Понимайте, куда',
    'hero.accent': 'уходят ваши деньги',
    'hero.subtitle':
        'PersonalMoney — программа с открытым кодом (AGPL-3.0), которая работает на вашем NAS или домашнем сервере. Счета, операции и переводы в любом количестве валют остаются на вашем железе, а данные никуда не уходят. Подключите свою ИИ-модель, чтобы читать чеки, — скоро.',
    'hero.ctaInstall': 'Установить',
    'hero.ctaSource': 'Исходный код',

    'preview.netWorth': 'Всего',
    'preview.income': 'Доходы',
    'preview.expenses': 'Расходы',
    'preview.thisMonth': 'За месяц',
    'preview.pieTitle': 'Расходы по категориям (за месяц)',
    'preview.cat.groceries': 'Продукты',
    'preview.cat.rent': 'Аренда',
    'preview.cat.transport': 'Транспорт',
    'preview.cat.other': 'Всё остальное',

    'features.title': 'Всё, что нужно домашней бухгалтерии',
    'features.soon': 'Скоро',
    'features.1.title': 'Счета в любой валюте',
    'features.1.body':
        'Дебетовые, накопительные, наличные, кредитные и инвестиционные счета — каждый в своей валюте и со своей точностью: от иены без копеек до биткоина с восемью знаками. Курсы валют подтягиваются автоматически.',
    'features.2.title': 'Быстрый ввод и честные переводы',
    'features.2.body':
        'Записывайте доходы и расходы в несколько касаний и помечайте их своими категориями. Деньги между счетами перемещаются так, что обе стороны остаются честными: перевод между валютами сохраняет тот курс, который вы получили, а не сегодняшний.',
    'features.3.title': 'Импорт из банка и CSV-выписки',
    'features.3.body':
        'Подключите Monobank или загрузите CSV из любого другого банка. Разбор колонок — предположение, которое можно поправить, дубликаты находятся сами, а категории подсказываются по прошлому выбору.',
    'features.4.title': 'Аналитика и бюджеты',
    'features.4.body':
        'Общий итог, доходы против расходов за полгода и расходы месяца по категориям — на одном экране, а рядом бюджеты, с которыми можно сверять месяц.',
    'features.5.title': 'Один бюджет на всю семью',
    'features.5.body':
        'Пригласите тех, с кем живёте, в общий дом — и ведите один набор счетов и операций на всех.',
    'features.6.title': 'Чеки читает ваша ИИ-модель',
    'features.6.body':
        'Сфотографируйте чек, и выбранная вами модель превратит его в операцию. Ничего не уходит в сервис, который вы не выбирали.',

    'install.title': 'Установите на своём компьютере',
    'install.lead': 'Нужна машина с Docker: NAS, мини-ПК или VPS.',
    'install.tab.compose': 'Docker Compose',
    'install.tab.synology': 'Synology',
    'install.tab.unraid': 'Unraid',
    'install.tab.truenas': 'TrueNAS',
    'install.tab.portainer': 'Portainer',
    'install.compose.1': 'Создайте для него папку.',
    'install.compose.2': 'Скачайте файл compose и шаблон переменных окружения.',
    'install.compose.3': 'Задайте DB_PASSWORD в .env. Это единственное значение, которое нужно указать.',
    'install.compose.4':
        'Запустите, откройте http://<машина>:8080 и создайте учётную запись администратора — первая учётная запись становится администратором. Данные лежат в ./data и ./postgres рядом с файлом compose.',
    'install.synology.1': 'Установите Container Manager из Центра пакетов.',
    'install.synology.2': 'Откройте Проект → Создать, укажите путь /docker/personalmoney и загрузите docker-compose.yml.',
    'install.synology.3': 'Положите рядом файл .env с DB_PASSWORD.',
    'install.synology.4': 'Соберите проект, откройте порт 8080 и создайте учётную запись администратора.',
    'install.unraid.1': 'Установите плагин Docker Compose Manager.',
    'install.unraid.2': 'Добавьте стек с файлом compose и .env, в котором задан DB_PASSWORD.',
    'install.unraid.3': 'Нажмите Compose Up, откройте порт 8080 и создайте учётную запись администратора.',
    'install.truenas.1': 'Откройте Apps → Discover → Custom App и выберите «Install via YAML».',
    'install.truenas.2': 'Вставьте файл compose и впишите пароль базы данных вместо каждого ${DB_PASSWORD:?Set DB_PASSWORD}; необязательные настройки добавьте в environment: сервиса app.',
    'install.truenas.3': 'Запустите, откройте порт 8080 и создайте учётную запись администратора.',
    'install.portainer.1': 'Откройте Stacks → Add stack и загрузите файл compose.',
    'install.portainer.2': 'Добавьте DB_PASSWORD в Environment variables; необязательные настройки добавьте в редакторе в environment: сервиса app.',
    'install.portainer.3': 'Разверните стек, откройте порт 8080 и создайте учётную запись администратора.',
    'install.https.title': 'Доступ извне',
    'install.https.body':
        'Поставьте его за свой обратный прокси (Caddy, Traefik, Nginx Proxy Manager) или заходите через Tailscale. За прокси задайте SERVER_FORWARD_HEADERS_STRATEGY=native, чтобы сервер видел адрес каждого посетителя. Укажите в APP_BASE_URL публичный адрес, чтобы работали ссылки в письмах и импорт из HomeMoney. Android-приложение предупреждает, когда сервер отвечает по обычному http.',
    'install.update.title': 'Обновление',
    'install.backup.title': 'Резервные копии',
    'install.backup.body':
        'Дамп базы и папка ./data — это всё. Если потерять ./data/secrets, все выйдут из системы, а сохранённые банковские подключения станет невозможно прочитать.',
    'install.reset.title': 'Не можете войти?',
    'install.reset.body':
        'Любой, у кого есть доступ к консоли машины, может задать новый пароль для учётной записи. Выполните там, где работает стек:',

    'android.title': 'Приложение для Android',
    'android.body': 'Скачайте APK из последнего релиза и укажите адрес своего сервера при входе.',
    'android.cta': 'Последний релиз',

    'ai.title': 'Подключите свою ИИ-модель',
    'ai.body':
        'Скоро: подключите любой OpenAI-совместимый сервер (Ollama, LM Studio, vLLM, OpenRouter), Anthropic или Gemini, чтобы читать чеки, и MCP, чтобы ваш собственный ассистент мог работать с вашим бюджетом.',

    'footer.tagline': 'Личные финансы — на вашем железе.',
    'footer.license': 'Открытый код под лицензией AGPL-3.0',
    'footer.source': 'Исходный код',
    'footer.privacy': 'Конфиденциальность',
};
