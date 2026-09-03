/**
 * English is the reference locale: `ru` and `uk` are typed against this object's
 * keys, so removing a key here surfaces as a compile error in both of them.
 *
 * Keys ending in `_one` / `_other` are plural variants picked by Intl.PluralRules.
 * Russian and Ukrainian add `_few` / `_many` on top of these.
 */
export const en = {
    // Kept in step with the <title> in index.html, which can only be static.
    'meta.title': 'PersonalMoney — personal finance, clearly',

    // ─── Shared ──────────────────────────────────────────────────────────
    'common.save': 'Save',
    'common.saving': 'Saving…',
    'common.cancel': 'Cancel',
    'common.delete': 'Delete',
    'common.edit': 'Edit',
    'common.close': 'Close',
    'common.optional': 'Optional',
    'common.loading': 'Loading…',
    'common.none': '—',
    'common.required': 'required',
    'common.choose': 'Choose…',
    'common.from': 'From',
    'common.to': 'To',
    'common.date': 'Date',
    'common.amount': 'Amount',
    'common.description': 'Description',
    'common.name': 'Name',
    'common.type': 'Type',
    'common.color': 'Color',
    'common.category': 'Category',
    'common.account': 'Account',
    'common.actions': 'Actions',
    'common.uncategorised': 'Uncategorised',
    'common.income': 'Income',
    'common.expense': 'Expense',
    'common.transfer': 'Transfer',

    // ─── Navigation & header ─────────────────────────────────────────────
    'nav.menu': 'Menu',
    'nav.dashboard': 'Dashboard',
    'nav.accounts': 'Accounts',
    'nav.transactions': 'Transactions',
    'nav.transfers': 'Transfers',
    'nav.categories': 'Categories',
    'nav.currencies': 'Currencies',
    'nav.banks': 'Banks',
    'nav.openMenu': 'Open navigation',
    'nav.closeMenu': 'Close navigation',

    'header.signOut': 'Sign out',
    'header.toLight': 'Switch to light mode',
    'header.toDark': 'Switch to dark mode',
    'header.language': 'Language',
    'header.user': 'User',

    // ─── Authentication ──────────────────────────────────────────────────
    'auth.email': 'Email address',
    'auth.emailPlaceholder': 'you@example.com',
    'auth.password': 'Password',
    'auth.firstName': 'First name',
    'auth.lastName': 'Last name',

    'login.title': 'Welcome back',
    'login.subtitle': 'Sign in to manage your finances',
    'login.submit': 'Sign in',
    'login.submitting': 'Signing in…',
    'login.failed': 'Could not sign in',
    'login.noAccount': 'Don’t have an account?',
    'login.createOne': 'Create one now',
    'login.forgot': 'Forgot your password?',

    'register.title': 'Create an account',
    'register.subtitle': 'Join PersonalMoney to manage your finances',
    'register.submit': 'Create account',
    'register.submitting': 'Creating account…',
    'register.failed': 'Could not create the account',
    'register.hasAccount': 'Already have an account?',
    'register.signIn': 'Sign in instead',

    'forgot.title': 'Reset your password',
    'forgot.subtitle': 'Enter your address and we will send a link to choose a new password.',
    'forgot.submit': 'Send reset link',
    'forgot.submitting': 'Sending…',
    'forgot.failed': 'Could not send the link. Try again shortly.',
    'forgot.sentTitle': 'Check your inbox',
    'forgot.sentBody': 'If that address has an account, a reset link is on its way.',
    'forgot.sentNote': 'The link works once and expires in an hour.',
    'forgot.backToSignIn': 'Back to sign in',

    'reset.title': 'Choose a new password',
    'reset.newPassword': 'New password',
    'reset.repeat': 'Repeat it',
    'reset.tooShort': 'At least {{count}} characters.',
    'reset.mismatch': 'These do not match.',
    'reset.submit': 'Set new password',
    'reset.failed': 'This link is not valid.',
    'reset.noTokenTitle': 'Link is incomplete',
    'reset.noTokenBody': 'This link is missing its token.',
    'reset.requestNew': 'Request a new link',
    'reset.doneTitle': 'Password changed',
    'reset.doneBody': 'You have been signed out everywhere. Sign in with the new password.',
    'reset.expiryNote': 'Links expire after an hour and work once.',
    'reset.requestAnother': 'Request another',

    'verify.working': 'Confirming…',
    'verify.doneTitle': 'Address confirmed',
    'verify.doneBody': 'Your address is confirmed.',
    'verify.failedTitle': 'Could not confirm',
    'verify.failedBody': 'This link is not valid.',
    'verify.noToken': 'This link is missing its token.',
    'verify.failedNote': 'Confirmation links expire after 24 hours and work once. Sign in and ask for a new one.',

    // ─── Landing ─────────────────────────────────────────────────────────
    'landing.nav.features': 'Features',
    'landing.nav.how': 'How it works',
    'landing.nav.faq': 'FAQ',
    'landing.nav.signIn': 'Sign in',
    'landing.nav.openApp': 'Open app',
    'landing.nav.getStarted': 'Get started',

    'landing.hero.badge': 'Multi-currency budgeting',
    // Split so the second half can carry the gradient without slicing a word out
    // of a sentence - which does not survive translation.
    'landing.hero.titleLead': 'Know where your money',
    'landing.hero.titleAccent': 'actually goes',
    'landing.hero.subtitle':
        'PersonalMoney keeps your accounts, transactions and transfers in one place — across as many currencies as you hold — and turns them into a picture you can read in a glance.',
    'landing.hero.ctaPrimary': 'Create a free account',
    'landing.hero.ctaSecondary': 'I already have an account',
    'landing.hero.note': 'No card required. Setting up takes a minute.',

    'landing.preview.netWorth': 'Net worth',
    'landing.preview.income': 'Income',
    'landing.preview.expenses': 'Expenses',
    'landing.preview.thisMonth': 'This month',
    // Sample category names inside the mock dashboard.
    'landing.preview.cat.groceries': 'Groceries',
    'landing.preview.cat.rent': 'Rent',
    'landing.preview.cat.transport': 'Transport',
    'landing.preview.cat.other': 'Everything else',

    'landing.features.title': 'Everything a household ledger needs',
    'landing.features.subtitle': 'No feature bloat — just the pieces you reach for every week.',

    'landing.features.accounts.title': 'Accounts in any currency',
    'landing.features.accounts.body':
        'Checking, savings, cash, credit and investment accounts, each in its own currency with its own precision — from yen with no decimals to bitcoin with eight.',

    'landing.features.transactions.title': 'Transactions worth reviewing',
    'landing.features.transactions.body':
        'Record income and expenses, tag them with your own categories, then filter by account, type or date range to answer a specific question.',

    'landing.features.transfers.title': 'Transfers that keep the rate',
    'landing.features.transfers.body':
        'Move money between accounts and both sides stay honest. Cross-currency transfers store the actual rate you got, not today’s.',

    // Bank connections are deliberately not advertised here: the feature is
    // experimental and currently reaches exactly one Ukrainian bank.
    'landing.features.interface.title': 'Reads in your language',
    'landing.features.interface.body':
        'English, Russian and Ukrainian, picked up from your browser and switchable whenever you like — each with a light and a dark theme.',

    'landing.features.csv.title': 'Import any statement',
    'landing.features.csv.body':
        'Every bank exports CSV. Column detection is a guess you can correct, duplicates are spotted, and categories are suggested from what you picked before.',

    'landing.features.insights.title': 'A dashboard that answers',
    'landing.features.insights.body':
        'Net worth, income against expenses over six months, and this month’s spending split by category — all on one screen.',

    'landing.how.title': 'Three steps to a real picture',
    'landing.how.step1.title': 'Create your account',
    'landing.how.step1.body': 'Sign up with an email address and confirm it. That is the whole setup.',
    'landing.how.step2.title': 'Add your accounts',
    'landing.how.step2.body': 'Enter each account with its currency and starting balance, and the categories you think in.',
    'landing.how.step3.title': 'Import or record',
    'landing.how.step3.body': 'Drop in a CSV statement or add transactions by hand — then read the dashboard.',

    'landing.trust.title': 'Careful with your money, by design',
    'landing.trust.body':
        'The parts of a budgeting app that are easy to get quietly wrong — exchange rates, repeated imports, rounding — are the parts handled most deliberately here.',
    'landing.trust.point1': 'Re-importing a period you have already loaded is safe: rows that are on file are recognised and left alone.',
    'landing.trust.point2': 'Resetting your password signs you out everywhere, so a recovered account is genuinely recovered.',
    'landing.trust.point3': 'Amounts are stored as exact decimals rather than floating point, so balances add up to the cent.',

    'landing.faq.title': 'Questions worth asking first',
    'landing.faq.q1': 'Does it handle more than one currency?',
    'landing.faq.a1':
        'That is the point of it. Every account has its own currency and decimal precision, transfers between currencies record the rate that was actually used, and the dashboard totals are shown per account so nothing is silently converted.',
    'landing.faq.q2': 'Do I have to import files?',
    'landing.faq.a2':
        'No. CSV import is there for when a whole month is easier to bring in at once — you can run the entire thing by entering transactions yourself, and plenty of people do.',
    'landing.faq.q3': 'What happens to duplicates when I import twice?',
    'landing.faq.a3':
        'Imported rows are matched against what is already there and shown as already imported, greyed out and excluded. Re-importing an overlapping date range is safe.',
    'landing.faq.q4': 'Who does the app talk to?',
    'landing.faq.a4':
        'Its own backend. There is no ad tracking and no third-party analytics built into it, and asking for a password reset never reveals whether an address has an account here.',

    'landing.cta.title': 'Start with this month',
    'landing.cta.body': 'Add your accounts, import a statement, and see the month you just had.',
    'landing.cta.button': 'Create a free account',

    'landing.footer.tagline': 'Personal finance, clearly.',
    'landing.footer.product': 'Product',
    'landing.footer.account': 'Account',
    'landing.footer.rights': '© {{year}} PersonalMoney',

    // ─── Dashboard ───────────────────────────────────────────────────────
    'dashboard.title': 'Dashboard',
    'dashboard.subtitle': 'Your financial overview',
    'dashboard.netWorth': 'Net worth',
    'dashboard.monthIncome': 'This month’s income',
    'dashboard.monthExpenses': 'This month’s expenses',
    'dashboard.netThisMonth': 'Net this month',
    'dashboard.currentMonth': 'Current month',
    'dashboard.incomeMinusExpenses': 'Income − expenses',
    'dashboard.accountCount_one': '{{count}} account',
    'dashboard.accountCount_other': '{{count}} accounts',
    'dashboard.chartTitle': 'Income vs expenses (6 months)',
    'dashboard.pieTitle': 'Expense categories (this month)',
    'dashboard.noExpenseData': 'No expense data this month',
    'dashboard.accounts': 'Accounts',
    'dashboard.noAccounts': 'No accounts yet',
    'dashboard.recent': 'Recent transactions',
    'dashboard.noTransactions': 'No transactions yet',

    // ─── Accounts ────────────────────────────────────────────────────────
    'accounts.title': 'Accounts',
    'accounts.subtitle': 'Manage your bank accounts and wallets',
    'accounts.new': 'New account',
    'accounts.edit': 'Edit account',
    'accounts.totalBalance': 'Total balance (all accounts)',
    'accounts.emptyTitle': 'No accounts yet',
    'accounts.emptyBody': 'Add your first account to start tracking.',
    'accounts.nameLabel': 'Account name',
    'accounts.namePlaceholder': 'e.g. Main Checking',
    'accounts.currency': 'Currency',
    'accounts.initialBalance': 'Initial balance',
    'accounts.nameRequired': 'Name is required',
    'accounts.currencyRequired': 'Select a currency',
    'accounts.confirmDelete': 'Delete this account?',

    'accountType.CHECKING': 'Checking',
    'accountType.SAVINGS': 'Savings',
    'accountType.CASH': 'Cash',
    'accountType.CREDIT': 'Credit',
    'accountType.INVESTMENT': 'Investment',

    // ─── Transactions ────────────────────────────────────────────────────
    'transactions.title': 'Transactions',
    'transactions.add': 'Add transaction',
    'transactions.editTitle': 'Edit transaction',
    'transactions.totalCount_one': '{{count}} transaction',
    'transactions.totalCount_other': '{{count}} transactions',
    'transactions.allAccounts': 'All accounts',
    'transactions.allTypes': 'All types',
    'transactions.empty': 'No transactions found',
    'transactions.emptyBody': 'Try widening the filters, or add one.',
    'transactions.selectAccount': 'Select account…',
    'transactions.selectCategory': 'Select category…',
    'transactions.descriptionPlaceholder': 'Optional note',
    'transactions.accountRequired': 'Select an account',
    'transactions.categoryRequired': 'Select a category',
    'transactions.amountRequired': 'Enter a valid amount',
    'transactions.confirmDelete': 'Delete this transaction?',
    'transactions.page': 'Page {{page}} of {{total}}',

    // ─── Transfers ───────────────────────────────────────────────────────
    'transfers.title': 'Transfers',
    'transfers.subtitle': 'Move money between your accounts',
    'transfers.new': 'New transfer',
    'transfers.create': 'Create transfer',
    'transfers.creating': 'Creating…',
    'transfers.emptyTitle': 'No transfers yet',
    'transfers.emptyBody': 'Move money between accounts to see it here.',
    'transfers.detail': 'Transfer detail',
    'transfers.rate': 'Rate',
    'transfers.fromAccount': 'From account',
    'transfers.toAccount': 'To account',
    'transfers.selectAccount': 'Select account',
    'transfers.amountFrom': 'Amount (from)',
    'transfers.amountTo': 'Amount (to)',
    'transfers.descriptionPlaceholder': 'e.g. Monthly savings',
    'transfers.needBothAccounts': 'Select both the source and the destination account',
    'transfers.needAmounts': 'Enter both amounts',
    'transfers.confirmDelete': 'Delete this transfer?',
    'transfers.deleteTitle': 'Delete transfer',

    // ─── Categories ──────────────────────────────────────────────────────
    'categories.title': 'Categories',
    'categories.subtitle': 'Organise your income and expenses',
    'categories.new': 'New category',
    'categories.editTitle': 'Edit category',
    'categories.emptyExpense': 'No expense categories yet',
    'categories.emptyIncome': 'No income categories yet',
    'categories.emptyBody': 'Add one to start sorting your transactions.',
    'categories.namePlaceholder': 'e.g. Groceries',
    'categories.iconKey': 'Icon key',
    'categories.nameRequired': 'Name is required',
    'categories.confirmDelete': 'Delete this category?',

    // ─── Currencies ──────────────────────────────────────────────────────
    'currencies.title': 'Currencies',
    'currencies.subtitle': 'Manage supported currencies and exchange rates',
    'currencies.add': 'Add currency',
    'currencies.editTitle': 'Edit currency',
    'currencies.empty': 'No currencies yet',
    'currencies.code': 'Code',
    'currencies.symbol': 'Symbol',
    'currencies.codeLabel': 'Code (3 characters)',
    'currencies.rateToBase': 'Rate to USD',
    'currencies.rateHint': 'How many units of this currency equal 1 USD',
    'currencies.base': 'Base',
    'currencies.markAsBase': 'Mark as base currency',
    'currencies.fieldsRequired': 'Code, name and symbol are required',
    'currencies.codeLength': 'Currency code must be exactly 3 characters',
    'currencies.confirmDelete': 'Delete this currency?',

    // ─── Banks ───────────────────────────────────────────────────────────
    'banks.title': 'Banks',
    'banks.connectionCount_one': '{{count}} connection',
    'banks.connectionCount_other': '{{count}} connections',
    'banks.addConnection': 'Add a connection',
    'banks.noProviders': 'No providers available',
    'banks.verify': 'Verify',
    'banks.accounts': 'Accounts',
    'banks.disconnect': 'Disconnect',
    'banks.confirmDisconnect': 'Disconnect? Imported transactions stay.',
    'banks.credentialsRejected': 'credentials rejected',
    'banks.connectTitle': 'Connect {{provider}}',
    'banks.checking': 'Checking…',
    'banks.checkAndConnect': 'Check and connect',
    'banks.whereFrom': 'Where do I get this?',
    'banks.loadingAccounts': 'Loading accounts…',
    'banks.atTheBank': 'At the bank',
    'banks.balance': 'Balance',
    'banks.linkedTo': 'Linked to',
    'banks.linkTo': 'Link to…',
    'banks.unlink': 'Unlink',
    'banks.importTitle': 'Import a statement',
    'banks.preview': 'Preview',
    'banks.imported': 'Imported {{imported}}, skipped {{skipped}}',
    'banks.importCount': 'Import {{count}}',

    // ─── CSV import ──────────────────────────────────────────────────────
    'csv.title': 'Import from a file',
    'csv.statementFile': 'Statement file (CSV)',
    'csv.read': 'Read file',
    'csv.reading': 'Reading…',
    'csv.columns': 'Columns',
    'csv.summary': '{{fresh}} new, {{duplicates}} already imported',
    'csv.alreadyImported': 'imported',
    'csv.skippedCount_one': '{{count}} line could not be read',
    'csv.skippedCount_other': '{{count}} lines could not be read',
    'csv.currencyMismatch':
        'The file contains {{currencies}} but {{account}} is in {{accountCurrency}}. Amounts are imported as-is, without conversion.',
    'csv.truncated': 'Showing the first 200 of {{total}}; all of them are imported.',
};
