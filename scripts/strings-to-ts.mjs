#!/usr/bin/env node
/**
 * Converts the Compose client's strings.xml (en, ru, uk) into TypeScript dictionaries for
 * the React web app.
 *
 * Run once, when the web client moved from Compose to React; the generated files are the
 * source of truth for web strings from then on. Kept in the repository so the conversion
 * can be read, and repeated if a batch of Android strings ever needs bringing across.
 *
 *   node scripts/strings-to-ts.mjs
 *
 * - `<string name="x">` becomes key `x`.
 * - `<plurals name="x">` items become `x_one`, `x_few`, `x_many`, `x_other` - the suffixes the
 *   web i18n module picks with Intl.PluralRules.
 * - `%1$s` / `%1$d` become `{{0}}`, `%2$…` becomes `{{1}}`, and so on. Inside a plural,
 *   `%1$d` becomes `{{count}}`, since that is the value the plural is chosen by.
 * - Android escapes (`\uXXXX`, `\'`, `\"`, `\n`, `\@`, `\?`, `\\`), `%%` and XML entities
 *   are undone.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const resources = join(root, 'client/composeApp/src/commonMain/composeResources');
const out = join(root, 'src/main/frontend/src/i18n/locales');

const LOCALES = [
    { dir: 'values', lang: 'en', name: 'appEn' },
    { dir: 'values-ru', lang: 'ru', name: 'appRu' },
    { dir: 'values-uk', lang: 'uk', name: 'appUk' },
];

function unescape(text) {
    return text
        .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
        .replace(/\\n/g, '\n')
        .replace(/\\(['"@?\\])/g, '$1')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&amp;/g, '&');
}

function placeholders(text, inPlural) {
    return text
        .replace(/%(\d+)\$[sd]/g, (_, n) => (inPlural && n === '1' ? '{{count}}' : `{{${Number(n) - 1}}}`))
        .replace(/%%/g, '%');
}

function convert(xml, file) {
    if (/<!\[CDATA\[|<string-array/.test(xml)) {
        console.error(`${file}: CDATA or string-array found - this script does not handle them`);
        process.exit(1);
    }
    const entries = {};
    for (const m of xml.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
        entries[m[1]] = placeholders(unescape(m[2]), false);
    }
    for (const m of xml.matchAll(/<plurals\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)) {
        for (const item of m[2].matchAll(/<item\s+quantity="([a-z]+)"\s*>([\s\S]*?)<\/item>/g)) {
            entries[`${m[1]}_${item[1]}`] = placeholders(unescape(item[2]), true);
        }
    }
    return entries;
}

for (const { dir, lang, name } of LOCALES) {
    const file = join(resources, dir, 'strings.xml');
    const entries = convert(readFileSync(file, 'utf8'), file);
    const body = Object.keys(entries)
        .sort()
        .map(k => `    ${k}: ${JSON.stringify(entries[k])},`)
        .join('\n');
    const header =
        '// Generated once from the Android strings by scripts/strings-to-ts.mjs.\n' +
        '// Edit this file directly from now on.\n';
    writeFileSync(join(out, `app.${lang}.ts`), `${header}export const ${name} = {\n${body}\n};\n`);
    console.log(`${lang}: ${Object.keys(entries).length} keys`);
}
