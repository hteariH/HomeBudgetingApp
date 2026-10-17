export const en = {
    'meta.title': 'PersonalMoney — self-hosted personal and family finances',

    'nav.features': 'Features',
    'nav.install': 'Install',
    'nav.android': 'Android',
    'header.language': 'Language',
    'header.toLight': 'Switch to light theme',
    'header.toDark': 'Switch to dark theme',

    'hero.badge': 'Self-hosted · open source',
    'hero.title': 'Know where your money',
    'hero.accent': 'actually goes',
    'hero.subtitle':
        'PersonalMoney is open source (AGPL-3.0) and runs on your NAS or home server. Your accounts, transactions and transfers stay on your hardware, in as many currencies as you hold, and your data never leaves it. Bring your own AI model to read receipts, and connect your AI assistant to your books.',
    'hero.ctaInstall': 'Install it',
    'hero.ctaSource': 'Source code',

    'preview.netWorth': 'Net worth',
    'preview.income': 'Income',
    'preview.expenses': 'Expenses',
    'preview.thisMonth': 'This month',
    'preview.pieTitle': 'Expense categories (this month)',
    'preview.cat.groceries': 'Groceries',
    'preview.cat.rent': 'Rent',
    'preview.cat.transport': 'Transport',
    'preview.cat.other': 'Everything else',

    'features.title': 'Everything a household ledger needs',
    'features.soon': 'Coming soon',
    'features.1.title': 'Accounts in any currency',
    'features.1.body':
        'Checking, savings, cash, credit and investment accounts, each in its own currency with its own precision — from yen with no decimals to bitcoin with eight. Exchange rates are fetched automatically.',
    'features.2.title': 'Fast entry, honest transfers',
    'features.2.body':
        'Record income and expenses in a few taps and tag them with your own categories. Move money between accounts and both sides stay honest: cross-currency transfers store the actual rate you got, not today’s.',
    'features.3.title': 'Bank import and CSV statements',
    'features.3.body':
        'Connect Monobank, or drop in a CSV from any other bank. Column detection is a guess you can correct, duplicates are spotted, and categories are suggested from what you picked before.',
    'features.4.title': 'Analytics and budgets',
    'features.4.body':
        'Net worth, income against expenses over six months, and this month’s spending split by category — all on one screen — with budgets to hold the month against.',
    'features.5.title': 'One ledger for the whole family',
    'features.5.body':
        'Invite the people you live with into a shared household and keep one set of accounts and transactions between you.',
    'features.6.title': 'Receipts read by your own AI model',
    'features.6.body':
        'Photograph a receipt and let a model you choose turn it into a transaction. Nothing goes to a service you did not pick.',

    'features.7.title': 'Your own assistant, with limits',
    'features.7.body':
        'Connect Claude, ChatGPT or Cursor over MCP to read your accounts and spending and to add entries. It cannot edit or delete anything, and you can revoke access at any time.',

    'install.title': 'Install it on your own machine',
    'install.lead': 'You need a machine that runs Docker: a NAS, a mini PC, a VPS.',
    'install.tab.compose': 'Docker Compose',
    'install.tab.synology': 'Synology',
    'install.tab.unraid': 'Unraid',
    'install.tab.truenas': 'TrueNAS',
    'install.tab.portainer': 'Portainer',
    'install.compose.1': 'Create a folder for it.',
    'install.compose.2': 'Download the compose file and the environment template.',
    'install.compose.3': 'Set DB_PASSWORD in .env. It is the only value you must provide.',
    'install.compose.4':
        'Start it, open http://<machine>:8080 and create the administrator account — the first account becomes the administrator. Your data stays in ./data and ./postgres next to the compose file.',
    'install.synology.1': 'Install Container Manager from Package Center.',
    'install.synology.2': 'Open Project → Create, set the path to /docker/personalmoney and upload docker-compose.yml.',
    'install.synology.3': 'Put a .env file with DB_PASSWORD next to it.',
    'install.synology.4': 'Build the project, open port 8080 and create the administrator account.',
    'install.unraid.1': 'Install the Docker Compose Manager plugin.',
    'install.unraid.2': 'Add a stack with the compose file and a .env that holds DB_PASSWORD.',
    'install.unraid.3': 'Press Compose Up, open port 8080 and create the administrator account.',
    'install.truenas.1': 'Open Apps → Discover → Custom App and choose “Install via YAML”.',
    'install.truenas.2': 'Paste the compose file and type your database password in place of each ${DB_PASSWORD:?Set DB_PASSWORD}; any optional setting is typed in place of its ${…} entry.',
    'install.truenas.3': 'Start it, open port 8080 and create the administrator account.',
    'install.portainer.1': 'Open Stacks → Add stack and upload the compose file.',
    'install.portainer.2': 'Add DB_PASSWORD under Environment variables, together with any optional setting you want; the compose file picks them all up.',
    'install.portainer.3': 'Deploy it, open port 8080 and create the administrator account.',
    'install.https.title': 'Reaching it from outside',
    'install.https.body':
        'Put it behind your reverse proxy (Caddy, Traefik, Nginx Proxy Manager) or reach it over Tailscale. Behind a proxy, set SERVER_FORWARD_HEADERS_STRATEGY=native so the server sees each visitor’s address. Set APP_BASE_URL to the public address so the links in emails and the HomeMoney import work. The Android app warns before it talks to a server over plain http.',
    'install.update.title': 'Updating',
    'install.backup.title': 'Backups',
    'install.backup.body':
        'The database dump and the ./data folder are everything. Losing ./data/secrets signs everyone out and makes stored bank connections unreadable.',
    'install.reset.title': 'Locked out?',
    'install.reset.body':
        'Anyone with shell access to the machine can set a new password for an account. Run this where the stack runs:',

    'android.title': 'Android app',
    'android.body': 'Download the APK from the latest release and enter your server’s address when signing in.',
    'android.cta': 'Latest release',

    'ai.title': 'Bring your own AI model',
    'ai.body':
        'Connect any OpenAI-compatible endpoint (Ollama, LM Studio, vLLM, OpenRouter), Anthropic or Gemini to read your receipts.',

    'footer.tagline': 'Personal finance, on your own hardware.',
    'footer.license': 'Open source under AGPL-3.0',
    'footer.source': 'Source code',
    'footer.privacy': 'Privacy',
};
