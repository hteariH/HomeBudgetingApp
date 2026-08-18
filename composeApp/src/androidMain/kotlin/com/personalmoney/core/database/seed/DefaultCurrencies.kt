package com.personalmoney.core.database.seed

import com.personalmoney.core.database.entity.CurrencyEntity

/**
 * Built-in currencies seeded on first launch. Users can toggle any of them on/off in
 * Settings → Manage currencies, and add their own custom currencies (BTC, ETH, internal
 * vouchers, etc.) via the same screen.
 *
 * Seeding uses INSERT OR IGNORE so a currency edited or activated by the user is not
 * clobbered on subsequent launches.
 */
object DefaultCurrencies {
    // Pre-activated set covers the most likely base currencies for first-time users.
    private val activeDefaults = setOf("USD", "EUR", "RUB", "UAH")

    val list: List<CurrencyEntity> = buildList {
        var order = 0
        fun add(code: String, name: String, symbol: String, decimals: Int) {
            add(
                CurrencyEntity(
                    code = code,
                    name = name,
                    symbol = symbol,
                    decimals = decimals,
                    active = activeDefaults.contains(code),
                    sortOrder = order++
                )
            )
        }

        // -------- Major world currencies --------
        add("USD", "US Dollar", "$", 2)
        add("EUR", "Euro", "€", 2)
        add("GBP", "British Pound", "£", 2)
        add("JPY", "Japanese Yen", "¥", 0)
        add("CHF", "Swiss Franc", "Fr", 2)
        add("CNY", "Chinese Yuan", "¥", 2)
        add("HKD", "Hong Kong Dollar", "HK$", 2)
        add("SGD", "Singapore Dollar", "S$", 2)
        add("CAD", "Canadian Dollar", "C$", 2)
        add("AUD", "Australian Dollar", "A$", 2)
        add("NZD", "New Zealand Dollar", "NZ$", 2)

        // -------- Europe --------
        add("SEK", "Swedish Krona", "kr", 2)
        add("NOK", "Norwegian Krone", "kr", 2)
        add("DKK", "Danish Krone", "kr", 2)
        add("PLN", "Polish Złoty", "zł", 2)
        add("CZK", "Czech Koruna", "Kč", 2)
        add("HUF", "Hungarian Forint", "Ft", 2)
        add("RON", "Romanian Leu", "lei", 2)
        add("BGN", "Bulgarian Lev", "лв", 2)
        add("RSD", "Serbian Dinar", "дин", 2)
        add("ISK", "Icelandic Króna", "kr", 0)
        add("MDL", "Moldovan Leu", "L", 2)
        add("ALL", "Albanian Lek", "L", 2)
        add("MKD", "North Macedonian Denar", "ден", 2)
        add("BAM", "Bosnia-Herzegovina Mark", "KM", 2)

        // -------- Post-Soviet --------
        add("RUB", "Russian Ruble", "₽", 2)
        add("UAH", "Ukrainian Hryvnia", "₴", 2)
        add("BYN", "Belarusian Ruble", "Br", 2)
        add("KZT", "Kazakhstani Tenge", "₸", 2)
        add("GEL", "Georgian Lari", "₾", 2)
        add("AMD", "Armenian Dram", "֏", 2)
        add("AZN", "Azerbaijani Manat", "₼", 2)
        add("UZS", "Uzbekistani Som", "soʼm", 2)
        add("KGS", "Kyrgyzstani Som", "с", 2)
        add("TJS", "Tajikistani Somoni", "ЅМ", 2)
        add("TMT", "Turkmenistani Manat", "T", 2)

        // -------- Middle East --------
        add("TRY", "Turkish Lira", "₺", 2)
        add("AED", "UAE Dirham", "د.إ", 2)
        add("SAR", "Saudi Riyal", "ر.س", 2)
        add("ILS", "Israeli New Shekel", "₪", 2)
        add("QAR", "Qatari Riyal", "ر.ق", 2)
        add("KWD", "Kuwaiti Dinar", "د.ك", 3)
        add("BHD", "Bahraini Dinar", "ب.د", 3)
        add("OMR", "Omani Rial", "ر.ع.", 3)
        add("JOD", "Jordanian Dinar", "د.ا", 3)
        add("LBP", "Lebanese Pound", "ل.ل", 2)
        add("EGP", "Egyptian Pound", "ج.م", 2)
        add("IRR", "Iranian Rial", "﷼", 2)
        add("IQD", "Iraqi Dinar", "ع.د", 3)

        // -------- Asia --------
        add("INR", "Indian Rupee", "₹", 2)
        add("KRW", "South Korean Won", "₩", 0)
        add("TWD", "Taiwan Dollar", "NT$", 2)
        add("THB", "Thai Baht", "฿", 2)
        add("VND", "Vietnamese Đồng", "₫", 0)
        add("IDR", "Indonesian Rupiah", "Rp", 2)
        add("MYR", "Malaysian Ringgit", "RM", 2)
        add("PHP", "Philippine Peso", "₱", 2)
        add("PKR", "Pakistani Rupee", "₨", 2)
        add("BDT", "Bangladeshi Taka", "৳", 2)
        add("LKR", "Sri Lankan Rupee", "Rs", 2)
        add("NPR", "Nepalese Rupee", "रु", 2)
        add("MMK", "Myanmar Kyat", "K", 2)
        add("KHR", "Cambodian Riel", "៛", 2)
        add("MNT", "Mongolian Tögrög", "₮", 2)
        add("MOP", "Macanese Pataca", "MOP$", 2)

        // -------- Africa --------
        add("ZAR", "South African Rand", "R", 2)
        add("NGN", "Nigerian Naira", "₦", 2)
        add("KES", "Kenyan Shilling", "KSh", 2)
        add("GHS", "Ghanaian Cedi", "₵", 2)
        add("MAD", "Moroccan Dirham", "د.م.", 2)
        add("TND", "Tunisian Dinar", "د.ت", 3)
        add("DZD", "Algerian Dinar", "د.ج", 2)
        add("XOF", "West African CFA Franc", "CFA", 0)
        add("XAF", "Central African CFA Franc", "FCFA", 0)
        add("ETB", "Ethiopian Birr", "Br", 2)
        add("UGX", "Ugandan Shilling", "USh", 0)
        add("TZS", "Tanzanian Shilling", "TSh", 2)
        add("RWF", "Rwandan Franc", "FRw", 0)
        add("ZMW", "Zambian Kwacha", "ZK", 2)

        // -------- Americas --------
        add("MXN", "Mexican Peso", "$", 2)
        add("BRL", "Brazilian Real", "R$", 2)
        add("ARS", "Argentine Peso", "$", 2)
        add("CLP", "Chilean Peso", "$", 0)
        add("COP", "Colombian Peso", "$", 2)
        add("PEN", "Peruvian Sol", "S/", 2)
        add("UYU", "Uruguayan Peso", "\$U", 2)
        add("VES", "Venezuelan Bolívar", "Bs.", 2)
        add("BOB", "Bolivian Boliviano", "Bs", 2)
        add("PYG", "Paraguayan Guaraní", "₲", 0)
        add("DOP", "Dominican Peso", "RD$", 2)
        add("CRC", "Costa Rican Colón", "₡", 2)
        add("GTQ", "Guatemalan Quetzal", "Q", 2)
        add("CUP", "Cuban Peso", "$", 2)
        add("JMD", "Jamaican Dollar", "J$", 2)

        // -------- Oceania --------
        add("FJD", "Fijian Dollar", "FJ$", 2)
        add("PGK", "Papua New Guinean Kina", "K", 2)
        add("XPF", "CFP Franc", "₣", 0)

        // -------- Precious metals --------
        add("XAU", "Gold (troy ounce)", "Au", 4)
        add("XAG", "Silver (troy ounce)", "Ag", 4)
        add("XPT", "Platinum (troy ounce)", "Pt", 4)

        // -------- Popular crypto (custom-friendly) --------
        add("BTC", "Bitcoin", "₿", 8)
        add("ETH", "Ethereum", "Ξ", 6)
        add("USDT", "Tether", "₮", 2)
        add("USDC", "USD Coin", "USDC", 2)
    }
}
