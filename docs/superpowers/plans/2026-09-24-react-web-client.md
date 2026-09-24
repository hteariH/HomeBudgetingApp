# React Web Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Compose/Wasm web client at `/app` with React, in one SPA together with the existing landing and auth pages, and remove the wasm target.

**Architecture:**
- `src/main/frontend` moves from CRA to Vite. Landing and auth pages go into `src/landing/`, the app into `src/app/`.
- Pure logic (money, calculator, FX, dates, routes) lives in `src/app/lib/`, has no React imports, and is tested with Vitest cases ported from `client/composeApp/src/commonTest`.
- Server state goes through TanStack Query over a fetch client. The client refreshes the token single-flight, matching the Ktor `Auth` plugin.
- Every screen is ported from its Compose `*Screen.kt` + `*ViewModel.kt` pair. Those files are the behaviour spec.

**Tech Stack:** Vite 6, React 19, TypeScript 5, react-router 7, @tanstack/react-query 5, decimal.js, Recharts, Tailwind 3, lucide-react, Vitest + Testing Library + jsdom.

**Spec:** `docs/superpowers/specs/2026-09-24-react-web-client-design.md`

## Global Constraints

- Work on branch `react-web-client`. The wasm client is deleted only in the last task.
- localStorage keys must be kept exactly:
  - session: `pm_access_token`, `pm_refresh_token`, `pm_email`;
  - app preferences: `theme` (write `system`/`light`/`dark`; read these and the legacy `SYSTEM`/`LIGHT`/`DARK`), `lang`, `onboarded` (`"true"`), `default_account`, `base_currency`, `homemoney_auth_state`.
- Amounts: API strings are parsed into `Decimal`. Never do arithmetic on a JS `number`. Rounding is `Decimal.ROUND_HALF_UP`, which in decimal.js means half away from zero.
- Width breakpoints (CSS px):
  - inline header nav at ≥1240;
  - wordmark at ≥480;
  - inline entry panel at ≥900, and below 900 the header shows "+";
  - content cap 1400;
  - form cap 480.
- i18n uses the existing `src/i18n` module: `t(key, vars)` with `{{name}}` interpolation and `_one/_few/_many/_other` plural keys via `vars.count`. App strings keep their Android names (`dashboard_account_count`).
- Keep comment style: comments explain *why*, as in the existing code.
- Dev server proxy target is `http://localhost:8085`, as `package.json` has it today.
- Commit after every task. Commit messages are plain English sentences in the repo's style, e.g. "Move the landing page from CRA to Vite". No attribution lines.

## Screen porting protocol (applies to Tasks 8–16)

For each screen:
1. Read the Compose `*Screen.kt` and `*ViewModel.kt` named in the task, and the matching `wasmJsMain/.../Rest*Repository.kt` for how data is fetched and mapped.
2. List every user-visible state and action: loading, empty, error, validation, confirm dialogs, plan-limit (402) handling that links to `/app/premium`, and what is refreshed after a write.
3. Put non-trivial pure derivations (totals, grouping, validation) in a `*.logic.ts` beside the screen, and cover them with Vitest before writing JSX.
4. Build the screen with components from Task 7. Use only strings from the dictionary.
5. Check it in the browser against the local backend (`mvn spring-boot:run -Dspring-boot.run.profiles=local` plus `npm run dev`), next to the wasm client on the same data.

---

### Task 1: Move the landing page from CRA to Vite

**Files:**
- Modify: `src/main/frontend/package.json`, `src/main/frontend/tsconfig.json`, `src/main/frontend/tailwind.config.js` (comment only), `src/main/frontend/.gitignore`, `pom.xml` (npm-build env vars)
- Create: `src/main/frontend/vite.config.ts`, `src/main/frontend/index.html` (moved from `public/index.html`), `src/main/frontend/postcss.config.js`, `src/main/frontend/src/main.tsx`, `src/main/frontend/src/vite-env.d.ts`, `src/main/frontend/src/test/setup.ts`
- Move: `src/pages/*` → `src/landing/*`, `src/session.ts` → `src/landing/session.ts`, `src/api/index.ts` → `src/landing/authApi.ts`
- Delete: `src/index.tsx`, `src/reportWebVitals.ts`, `src/react-app-env.d.ts`, `src/setupTests.ts`, `src/logo.svg`, `src/App.css` (if unused), `public/index.html`

**Interfaces:**
- Produces: `npm run dev` / `build` / `test` / `typecheck`; build output in `build/`; Vitest configured with jsdom and `src/test/setup.ts`.

- [ ] **Step 1: Replace dependencies**

```bash
cd src/main/frontend
npm remove react-scripts web-vitals @types/jest @testing-library/user-event
npm install -D vite@^6 @vitejs/plugin-react@^4 typescript@^5.6 vitest@^2 jsdom@^25 @testing-library/user-event@^14 postcss@^8 autoprefixer@^10 @types/node@^22
npm install @tanstack/react-query@^5 decimal.js@^10 recharts@^2
```

- [ ] **Step 2: Set scripts in `package.json`, and drop `eslintConfig`, `jest`, `browserslist` and `proxy`**

```json
"type": "module",
"scripts": {
  "dev": "vite",
  "build": "tsc --noEmit && vite build",
  "preview": "vite preview",
  "test": "vitest run",
  "test:watch": "vitest",
  "typecheck": "tsc --noEmit"
}
```

Rename `tailwind.config.js` to `tailwind.config.cjs`, because the package becomes `"type": "module"`. Remove its comment paragraph about react-scripts turning Tailwind on, and replace it with: "Tailwind stays on v3 through PostCSS; see postcss.config.js."

- [ ] **Step 3: Create `vite.config.ts`**

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Built into build/ because pom.xml copies that directory onto the jar's static root.
export default defineConfig({
    plugins: [react()],
    build: { outDir: 'build', sourcemap: false },
    server: {
        port: 3000,
        proxy: { '/api': 'http://localhost:8085' },
    },
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/test/setup.ts'],
        globals: true,
    },
});
```

`postcss.config.js`:

```js
export default { plugins: { tailwindcss: { config: './tailwind.config.cjs' }, autoprefixer: {} } };
```

`src/test/setup.ts`:

```ts
import '@testing-library/jest-dom/vitest';
```

- [ ] **Step 4: Move `public/index.html` to `index.html` at the frontend root**

Replace every `%PUBLIC_URL%` with the empty string, so `%PUBLIC_URL%/favicon.ico` becomes `/favicon.ico`. Add this before `</body>`:

```html
<script type="module" src="/src/main.tsx"></script>
```

- [ ] **Step 5: Create `src/main.tsx`**

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
);
```

`src/vite-env.d.ts`: `/// <reference types="vite/client" />`

- [ ] **Step 6: Update `tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["DOM", "DOM.Iterable", "ES2022"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noEmit": true,
    "isolatedModules": true,
    "skipLibCheck": true,
    "resolveJsonModule": true,
    "forceConsistentCasingInFileNames": true,
    "noFallthroughCasesInSwitch": true,
    "types": ["vite/client", "vitest/globals"]
  },
  "include": ["src", "vite.config.ts"]
}
```

- [ ] **Step 7: Move files and fix imports**

```bash
git mv src/pages src/landing
git mv src/session.ts src/landing/session.ts
git mv src/api/index.ts src/landing/authApi.ts
git rm src/index.tsx src/reportWebVitals.ts src/react-app-env.d.ts src/setupTests.ts src/logo.svg public/index.html
```

Fix the import paths in `App.tsx` and in `src/landing/*` (`'../api'` → `'./authApi'`, `'../session'` → `'./session'`). In `src/i18n/index.tsx`, replace `process.env.NODE_ENV === 'development'` with `import.meta.env.DEV`, and delete the comment paragraph about CRA pinning TypeScript 4.9. `App.css` is not imported anywhere after the move, so delete it too.

- [ ] **Step 8: Port `App.test.tsx` to Vitest**

Read the existing `src/App.test.tsx`. Keep its assertions and replace `jest.*` with `vi.*`. Delete the `jest.moduleNameMapper` workaround from `package.json`; Vite resolves ESM natively.

- [ ] **Step 9: Update `pom.xml`**

In the `npm-build` execution, delete the `<environmentVariables>` block (`CI`, `GENERATE_SOURCEMAP`). Both were CRA switches.

- [ ] **Step 10: Verify**

Run: `npm run typecheck && npm test && npm run build`
Expected: all pass, and `build/index.html` references `/assets/index-*.js`.

Run `npm run dev`, open `http://localhost:3000/` and `/login` in the browser pane, switch theme and language. Expected: identical to before.

- [ ] **Step 11: Commit**

```bash
git add -A src/main/frontend pom.xml
git commit -m "Move the landing page from CRA to Vite"
```

---

### Task 2: Money, calculator and input sanitising

**Files:**
- Create: `src/app/lib/currencyRegistry.ts`, `src/app/lib/money.ts`, `src/app/lib/calculator.ts`, `src/app/lib/expressionInput.ts`
- Test: `src/app/lib/money.test.ts`, `src/app/lib/calculator.test.ts`, `src/app/lib/expressionInput.test.ts`

**Interfaces:**
- Produces:
  - `CurrencyRegistry.update(list: {code,symbol,decimals}[])`, `.lookup(code)`, `.decimalsOrFallback(code): number`
  - `type Money = { amount: Decimal; currency: string }`
  - `parseAmount(s: string | null | undefined): Decimal` (null or invalid → 0)
  - `roundTo(d: Decimal, decimals: number): Decimal`
  - `toFixedString(d: Decimal, decimals: number): string` (wire format)
  - `toTrimmedString(d: Decimal): string`
  - `Calculator.evaluate(expr: string, decimals: number): Decimal | null`, `Calculator.hasOperator(expr): boolean`
  - `sanitizeExpression(typed: string): string`

- [ ] **Step 1: Write the failing tests**

`src/app/lib/calculator.test.ts`. Every case is ported from `CalculatorTest.kt`:

```ts
import { describe, expect, it } from 'vitest';
import { Calculator } from './calculator';

const ev = (e: string, d = 2) => Calculator.evaluate(e, d)?.toFixed(d) ?? null;

describe('Calculator', () => {
    it('evaluates plain number', () => expect(ev('12.34')).toBe('12.34'));
    it('evaluates simple addition', () => expect(ev('10+5')).toBe('15.00'));
    it('evaluates subtraction', () => expect(ev('10-5.5')).toBe('4.50'));
    it('respects operator precedence', () => expect(ev('10+2*3')).toBe('16.00'));
    it('evaluates division with rounding', () => expect(ev('10/3')).toBe('3.33'));
    it('unary minus is allowed', () => expect(ev('-5')).toBe('-5.00'));
    it('accepts unicode operators', () => {
        expect(ev('12÷2')).toBe('6.00');
        expect(ev('12×2')).toBe('24.00');
        expect(ev('12−2')).toBe('10.00');
    });
    it('comma as decimal separator works', () => expect(ev('12,34')).toBe('12.34'));
    it('trailing operator is dropped', () => expect(ev('12+')).toBe('12.00'));
    it('division by zero returns null', () => expect(ev('10/0')).toBeNull());
    it('multiple decimal points return null', () => expect(ev('12.3.4')).toBeNull());
    it('empty expression returns null', () => expect(ev('')).toBeNull());
    it('hasOperator detects operators', () => {
        expect(Calculator.hasOperator('10+5')).toBe(true);
        expect(Calculator.hasOperator('10×2')).toBe(true);
        expect(Calculator.hasOperator('123')).toBe(false);
        expect(Calculator.hasOperator('-123')).toBe(false);
    });
    it('decimal scale respects passed decimals', () => {
        expect(ev('10', 0)).toBe('10');
        expect(ev('10', 3)).toBe('10.000');
    });
    it('rounds half away from zero', () => {
        expect(ev('0.125')).toBe('0.13');
        expect(ev('-0.125')).toBe('-0.13');
    });
});
```

`src/app/lib/expressionInput.test.ts`. Every case is ported from `ExpressionInputTest.kt`:

```ts
import { expect, it } from 'vitest';
import { sanitizeExpression as s } from './expressionInput';

it('digits pass through', () => expect(s('1234')).toBe('1234'));
it('a decimal point is kept', () => expect(s('12.34')).toBe('12.34'));
it('a comma is kept as typed', () => expect(s('12,34')).toBe('12,34'));
it('a second separator in the same number is ignored', () => {
    expect(s('12.34.')).toBe('12.34');
    expect(s('12.34,')).toBe('12.34');
});
it('only the separator is dropped, not what follows it', () => expect(s('12.34.5')).toBe('12.345'));
it('each number gets its own separator', () => expect(s('1.5+2.5')).toBe('1.5+2.5'));
it('letters are dropped', () => expect(s('1abc2')).toBe('12'));
it('spaces and currency symbols are dropped', () => expect(s('1 249.90 ₴')).toBe('1249.90'));
it('pasted operators survive', () => expect(s('12+3−4×5÷6')).toBe('12+3−4×5÷6'));
it('a leading minus is kept', () => expect(s('-5')).toBe('-5'));
it('empty stays empty', () => expect(s('')).toBe(''));
```

`src/app/lib/money.test.ts`:

```ts
import Decimal from 'decimal.js';
import { beforeEach, expect, it } from 'vitest';
import { CurrencyRegistry } from './currencyRegistry';
import { parseAmount, roundTo, toFixedString, toTrimmedString } from './money';

beforeEach(() => CurrencyRegistry.update([]));

it('multiplication rounds half away from zero to minor units', () => {
    expect(roundTo(new Decimal('1.00').times('2.5'), 2).toFixed(2)).toBe('2.50');
    expect(roundTo(new Decimal('0.005'), 2).toFixed(2)).toBe('0.01');
    expect(roundTo(new Decimal('-0.005'), 2).toFixed(2)).toBe('-0.01');
});
it('parses API strings and treats garbage as zero', () => {
    expect(parseAmount('12.50').toFixed(2)).toBe('12.50');
    expect(parseAmount(null).isZero()).toBe(true);
    expect(parseAmount('abc').isZero()).toBe(true);
});
it('keeps the scale on the wire', () => {
    expect(toFixedString(new Decimal('90'), 2)).toBe('90.00');
    expect(toFixedString(new Decimal('12.5'), 2)).toBe('12.50');
    expect(toFixedString(new Decimal('10'), 0)).toBe('10');
});
it('trims for display', () => {
    expect(toTrimmedString(new Decimal('12.50'))).toBe('12.5');
    expect(toTrimmedString(new Decimal('12.00'))).toBe('12');
});
it('falls back to known decimals for codes the user has not configured', () => {
    expect(CurrencyRegistry.decimalsOrFallback('JPY')).toBe(0);
    expect(CurrencyRegistry.decimalsOrFallback('KWD')).toBe(3);
    expect(CurrencyRegistry.decimalsOrFallback('BTC')).toBe(8);
    expect(CurrencyRegistry.decimalsOrFallback('XYZ')).toBe(2);
    CurrencyRegistry.update([{ code: 'XYZ', symbol: 'X', decimals: 4 }]);
    expect(CurrencyRegistry.decimalsOrFallback('XYZ')).toBe(4);
});
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `npx vitest run src/app/lib`
Expected: FAIL, modules not found.

- [ ] **Step 3: Implement `currencyRegistry.ts`**

```ts
export interface CurrencyInfo { code: string; symbol: string; decimals: number }

// Zero, three and eight-decimal codes a user may meet without having configured them.
// Mirrors CurrencyRegistry.kt; everything else defaults to 2.
const FALLBACK_DECIMALS: Record<string, number> = {
    JPY: 0, KRW: 0, VND: 0, CLP: 0, ISK: 0, HUF: 0, PYG: 0, RWF: 0, UGX: 0, VUV: 0,
    XAF: 0, XOF: 0, XPF: 0, KMF: 0, DJF: 0, GNF: 0, BIF: 0,
    BHD: 3, IQD: 3, JOD: 3, KWD: 3, LYD: 3, OMR: 3, TND: 3,
    BTC: 8, ETH: 8,
};

let snapshot = new Map<string, CurrencyInfo>();

/**
 * The user's own currencies, read synchronously by the money helpers, so a code like BTC
 * gets the symbol and precision the user chose. Refreshed whenever the currency list loads.
 */
export const CurrencyRegistry = {
    update(list: CurrencyInfo[]) {
        snapshot = new Map(list.map(c => [c.code, c]));
    },
    lookup: (code: string) => snapshot.get(code),
    decimalsOrFallback: (code: string): number =>
        snapshot.get(code)?.decimals ?? FALLBACK_DECIMALS[code.toUpperCase()] ?? 2,
};
```

- [ ] **Step 4: Implement `money.ts`**

```ts
import Decimal from 'decimal.js';

// Half away from zero, the rounding every money path in the Kotlin client uses.
Decimal.set({ rounding: Decimal.ROUND_HALF_UP, precision: 40 });

export type Money = { amount: Decimal; currency: string };

/** Every amount arrives as a JSON string, so it never touches a double. */
export function parseAmount(s: string | null | undefined): Decimal {
    if (s == null || s === '') return new Decimal(0);
    try {
        return new Decimal(s);
    } catch {
        return new Decimal(0);
    }
}

export const roundTo = (d: Decimal, decimals: number) =>
    d.toDecimalPlaces(Math.max(0, decimals), Decimal.ROUND_HALF_UP);

/** The wire format: fixed scale, so "12.50" never becomes "12.5" on the way out. */
export const toFixedString = (d: Decimal, decimals: number) =>
    roundTo(d, decimals).toFixed(Math.max(0, decimals));

/** Display form with no trailing zeros. */
export const toTrimmedString = (d: Decimal) => d.toFixed();
```

- [ ] **Step 5: Implement `calculator.ts`**

This is a straight port of `Calculator.kt`: tokenize, then multiply/divide, then add/subtract.

```ts
import Decimal from 'decimal.js';
import { roundTo } from './money';

type Token = { num: Decimal } | { op: '+' | '-' | '*' | '/' };
const isOp = (t: Token): t is { op: '+' | '-' | '*' | '/' } => 'op' in t;

function tokenize(expr: string): Token[] | null {
    const s = expr.replace(/×/g, '*').replace(/÷/g, '/').replace(/−/g, '-').replace(/,/g, '.').replace(/ /g, '');
    if (s === '') return [];
    const tokens: Token[] = [];
    let i = 0;
    let prevWasOp = true;
    while (i < s.length) {
        const ch = s[i];
        if (/[0-9.]/.test(ch)) {
            let j = i;
            let dot = false;
            while (j < s.length && /[0-9.]/.test(s[j])) {
                if (s[j] === '.') {
                    if (dot) return null;
                    dot = true;
                }
                j++;
            }
            const text = s.slice(i, j);
            if (text === '.') return null;
            tokens.push({ num: new Decimal(text.startsWith('.') ? `0${text}` : text.endsWith('.') ? text.slice(0, -1) : text) });
            i = j;
            prevWasOp = false;
        } else if ('+-*/'.includes(ch)) {
            if (prevWasOp && ch === '-') tokens.push({ num: new Decimal(0) });
            else if (prevWasOp) return null;
            tokens.push({ op: ch as '+' | '-' | '*' | '/' });
            prevWasOp = true;
            i++;
        } else {
            return null;
        }
    }
    // A trailing operator is what a half-typed expression looks like - drop it.
    if (tokens.length && isOp(tokens[tokens.length - 1])) tokens.pop();
    return tokens.length ? tokens : null;
}

function mulDiv(tokens: Token[]): Token[] | null {
    const out: Token[] = [];
    for (let i = 0; i < tokens.length; i++) {
        const t = tokens[i];
        if (isOp(t) && (t.op === '*' || t.op === '/')) {
            const left = out.pop();
            const right = tokens[i + 1];
            if (!left || isOp(left) || !right || isOp(right)) return null;
            if (t.op === '/' && right.num.isZero()) return null;
            out.push({ num: t.op === '*' ? left.num.times(right.num) : left.num.div(right.num) });
            i++;
        } else {
            out.push(t);
        }
    }
    return out;
}

function addSub(tokens: Token[]): Decimal | null {
    const first = tokens[0];
    if (!first || isOp(first)) return null;
    let acc = first.num;
    for (let i = 1; i < tokens.length; i += 2) {
        const op = tokens[i];
        const right = tokens[i + 1];
        if (!isOp(op) || !right || isOp(right)) return null;
        acc = op.op === '+' ? acc.plus(right.num) : op.op === '-' ? acc.minus(right.num) : acc;
    }
    return acc;
}

export const Calculator = {
    evaluate(expression: string, decimals: number): Decimal | null {
        const tokens = tokenize(expression);
        if (!tokens || tokens.length === 0) return null;
        const first = mulDiv(tokens);
        if (!first) return null;
        const result = addSub(first);
        return result === null ? null : roundTo(result, decimals);
    },
    /** True if the expression has an operator other than a leading unary minus. */
    hasOperator(expression: string): boolean {
        let t = expression.replace(/ /g, '');
        if (t.startsWith('-') || t.startsWith('−')) t = t.slice(1);
        return /[+\-*/×÷−]/.test(t);
    },
};
```

- [ ] **Step 6: Implement `expressionInput.ts`**

```ts
const OPERATORS = '+-*/−×÷';
const SEPARATORS = '.,';

/**
 * Keeps only what Calculator can read. Both separators are kept as typed: ru/uk keyboards
 * emit a comma, and Calculator folds it onto a dot.
 */
export function sanitizeExpression(typed: string): string {
    let out = '';
    let separatorInNumber = false;
    for (const ch of typed) {
        if (ch >= '0' && ch <= '9') out += ch;
        else if (SEPARATORS.includes(ch)) {
            if (!separatorInNumber) {
                separatorInNumber = true;
                out += ch;
            }
        } else if (OPERATORS.includes(ch)) {
            separatorInNumber = false;
            out += ch;
        }
    }
    return out;
}
```

- [ ] **Step 7: Run the tests**

Run: `npx vitest run src/app/lib`
Expected: PASS. If `'12.'` or `'.5'` edge handling in `tokenize` breaks a ported case, the Kotlin `BigDecimal.parseString` behaviour is the reference: it accepts `12.` and `.5`.

- [ ] **Step 8: Commit** — "Port money, calculator and input sanitising to TypeScript"

---

### Task 3: FX snapshot, formatting, dates, scheduling, routes

**Files:**
- Create: `src/app/lib/fx.ts`, `src/app/lib/format.ts`, `src/app/lib/dates.ts`, `src/app/lib/scheduler.ts`, `src/app/lib/routes.ts`
- Test: `fx.test.ts`, `format.test.ts`, `dates.test.ts`, `scheduler.test.ts`, `routes.test.ts`

**Interfaces:**
- Consumes: `CurrencyRegistry`, `Money`, `roundTo` (Task 2).
- Produces:
  - `FxSnapshot.from(rates: {baseCode,quoteCode,rate:number}[])`, `.rate(base, quote): number | null`, `.convert(m: Money, target: string): Money | null`, `FxSnapshot.EMPTY`
  - `formatMoney(m: Money): string`, `formatPlain(m: Money): string`, `formatMediumDate(iso: string): string`, `shortMonthName(month1: number): string`
  - `todayIso(): string`, `isoDate(d: Date): string` (local calendar date), `monthRange(year, month1): { from: Date; to: Date }` (`to` exclusive), `apiDateTo(toExclusive: Date): string` (the day before an exclusive midnight bound, matching 6d9a525), `inRange(occurredAt: string, from?: Date, to?: Date): boolean`
  - `nextChargeDate(currentIso: string, frequency: 'DAILY'|'WEEKLY'|'MONTHLY'|'YEARLY', interval: number): string`
  - `AppRoutes` (path builders, below), `isKnownRoute(route: string, patterns: string[]): boolean`, `LEGACY_PATTERNS`, `legacyFragmentToPath(fragment: string): string | null`

- [ ] **Step 1: Write `fx.test.ts`, ported from `FxSnapshotTest.kt` case by case**

```ts
import Decimal from 'decimal.js';
import { expect, it } from 'vitest';
import { FxSnapshot } from './fx';

const r = (baseCode: string, quoteCode: string, rate: number) => ({ baseCode, quoteCode, rate });

it('same currency returns 1', () => expect(FxSnapshot.from([]).rate('USD', 'USD')).toBe(1));
it('direct rate', () => expect(FxSnapshot.from([r('USD', 'EUR', 0.9)]).rate('USD', 'EUR')).toBeCloseTo(0.9, 4));
it('inverse rate', () => expect(FxSnapshot.from([r('USD', 'EUR', 0.9)]).rate('EUR', 'USD')).toBeCloseTo(1.111, 3));
it('pivot via USD', () =>
    expect(FxSnapshot.from([r('USD', 'EUR', 0.9), r('USD', 'GBP', 0.8)]).rate('EUR', 'GBP')).toBeCloseTo((1 / 0.9) * 0.8, 3));
it('unknown currencies return null', () => expect(FxSnapshot.from([]).rate('XXX', 'EUR')).toBeNull());
it('zero stored rate is ignored, not inverted to infinity', () => {
    const fx = FxSnapshot.from([r('USD', 'EUR', 0)]);
    expect(fx.rate('USD', 'EUR')).toBeNull();
    expect(fx.rate('EUR', 'USD')).toBeNull();
});
it('non-finite stored rate is ignored', () => {
    const fx = FxSnapshot.from([r('USD', 'EUR', NaN)]);
    expect(fx.rate('USD', 'EUR')).toBeNull();
    expect(fx.rate('EUR', 'USD')).toBeNull();
});
it('negative stored rate is ignored', () => expect(FxSnapshot.from([r('USD', 'EUR', -0.9)]).rate('USD', 'EUR')).toBeNull());
it('a dollar stablecoin converts with no stored rate at all', () => {
    const fx = FxSnapshot.from([]);
    expect(fx.rate('USDT', 'USD')).toBeCloseTo(1, 4);
    expect(fx.rate('USD', 'USDC')).toBeCloseTo(1, 4);
});
it('a pegged coin reaches every currency the feed does carry', () =>
    expect(FxSnapshot.from([r('EUR', 'USD', 1.08)]).rate('USDT', 'EUR')).toBeCloseTo(1 / 1.08, 4));
it('a stored rate beats the peg', () => expect(FxSnapshot.from([r('USDT', 'USD', 0.93)]).rate('USDT', 'USD')).toBeCloseTo(0.93, 4));
it('an unpegged crypto still needs a rate of its own', () => expect(FxSnapshot.from([r('EUR', 'USD', 1.08)]).rate('BTC', 'EUR')).toBeNull());
it('convert respects target decimals', () => {
    const out = FxSnapshot.from([r('USD', 'JPY', 150)]).convert({ amount: new Decimal('1.00'), currency: 'USD' }, 'JPY');
    expect(out?.amount.toFixed()).toBe('150');
});
```

- [ ] **Step 2: Implement `fx.ts`**

```ts
import Decimal from 'decimal.js';
import { CurrencyRegistry } from './currencyRegistry';
import { roundTo, type Money } from './money';

export interface FxRateInput { baseCode: string; quoteCode: string; rate: number }

/** Dollar stablecoins: a rate by definition, which no feed carries. A stored rate wins. */
export const FX_HUB = 'USD';
export const FX_PEGS: Record<string, number> = { USDT: 1, USDC: 1 };
export const isPegged = (code: string) => code.toUpperCase() in FX_PEGS;

const ok = (v: number | undefined): v is number => v !== undefined && Number.isFinite(v) && v > 0;

export class FxSnapshot {
    private constructor(private readonly byPair: Map<string, number>) {}

    static readonly EMPTY = new FxSnapshot(new Map());

    static from(rates: FxRateInput[]): FxSnapshot {
        const pairs = new Map<string, number>();
        const quoted = new Set<string>();
        for (const r of rates) {
            if (!ok(r.rate)) continue;
            pairs.set(`${r.baseCode}>${r.quoteCode}`, r.rate);
            quoted.add(r.baseCode);
            quoted.add(r.quoteCode);
        }
        for (const [code, v] of Object.entries(FX_PEGS)) {
            if (!quoted.has(code)) pairs.set(`${code}>${FX_HUB}`, v);
        }
        return new FxSnapshot(pairs);
    }

    private direct(b: string, q: string) {
        const v = this.byPair.get(`${b}>${q}`);
        return ok(v) ? v : null;
    }

    private inverse(b: string, q: string) {
        const v = this.byPair.get(`${q}>${b}`);
        return ok(v) ? 1 / v : null;
    }

    rate(base: string, quote: string): number | null {
        if (base === quote) return 1;
        const d = this.direct(base, quote) ?? this.inverse(base, quote);
        if (d !== null) return d;
        const toUsd = this.direct(base, FX_HUB) ?? this.inverse(base, FX_HUB);
        const fromUsd = this.direct(FX_HUB, quote) ?? this.inverse(FX_HUB, quote);
        return toUsd !== null && fromUsd !== null ? toUsd * fromUsd : null;
    }

    convert(m: Money, target: string): Money | null {
        const r = this.rate(m.currency, target);
        if (r === null) return null;
        return { amount: roundTo(m.amount.times(new Decimal(r)), CurrencyRegistry.decimalsOrFallback(target)), currency: target };
    }
}
```

- [ ] **Step 3: Write `format.test.ts` and implement `format.ts`**

Test (pinned to `en-US` so the result does not depend on the machine's locale):

```ts
import Decimal from 'decimal.js';
import { beforeEach, expect, it } from 'vitest';
import { CurrencyRegistry } from './currencyRegistry';
import { formatMediumDate, formatMoney, formatPlain, setFormatLocale } from './format';

beforeEach(() => { setFormatLocale('en-US'); CurrencyRegistry.update([]); });

it('uses the symbol the user configured', () => {
    CurrencyRegistry.update([{ code: 'UAH', symbol: '₴', decimals: 2 }]);
    expect(formatMoney({ amount: new Decimal('1250.5'), currency: 'UAH' })).toBe('₴ 1,250.50');
});
it('falls back to Intl for ISO codes', () =>
    expect(formatMoney({ amount: new Decimal('3'), currency: 'EUR' })).toBe('€3.00'));
it('prefixes the raw code for anything Intl rejects', () =>
    expect(formatMoney({ amount: new Decimal('1'), currency: 'USDTX' })).toBe('USDTX 1.00'));
it('plain has digits only', () =>
    expect(formatPlain({ amount: new Decimal('1250.75'), currency: 'USD' })).toBe('1,250.75'));
it('a date is not shifted by the time zone', () =>
    expect(formatMediumDate('2026-02-03')).toBe('Feb 3, 2026'));
```

Implementation:

```ts
import { CurrencyRegistry } from './currencyRegistry';
import type { Money } from './money';

// Follows the UI language; set by I18nProvider when the language changes.
let locale: string | undefined;
export const setFormatLocale = (l: string | undefined) => { locale = l; };

const num = (m: Money, decimals: number) =>
    new Intl.NumberFormat(locale, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
        // Display only: a double holds any real balance exactly, and Intl rounds to decimals.
        .format(m.amount.toNumber());

/** Mirrors MoneyFormatter.wasmJs.kt: user symbol, then Intl currency, then raw code. */
export function formatMoney(m: Money): string {
    const info = CurrencyRegistry.lookup(m.currency);
    if (info) return `${info.symbol} ${num(m, info.decimals)}`;
    const decimals = CurrencyRegistry.decimalsOrFallback(m.currency);
    try {
        return new Intl.NumberFormat(locale, {
            style: 'currency', currency: m.currency, minimumFractionDigits: decimals, maximumFractionDigits: decimals,
        }).format(m.amount.toNumber());
    } catch {
        return `${m.currency} ${num(m, decimals)}`;
    }
}

export const formatPlain = (m: Money) => num(m, CurrencyRegistry.decimalsOrFallback(m.currency));

/** Built and read in UTC: a bare date must not move by a day west of Greenwich. */
export function formatMediumDate(iso: string): string {
    const [y, mo, d] = iso.slice(0, 10).split('-').map(Number);
    return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
        .format(new Date(Date.UTC(y, mo - 1, d)));
}

export const shortMonthName = (month1: number) =>
    new Intl.DateTimeFormat(locale, { month: 'short', timeZone: 'UTC' }).format(new Date(Date.UTC(2000, month1 - 1, 1)));
```

- [ ] **Step 4: Write `dates.test.ts` and `scheduler.test.ts`, then implement both**

```ts
// dates.test.ts
import { expect, it } from 'vitest';
import { apiDateTo, inRange, isoDate, monthRange } from './dates';

it('a month range is [first day, first day of next month)', () => {
    const { from, to } = monthRange(2026, 12);
    expect(isoDate(from)).toBe('2026-12-01');
    expect(isoDate(to)).toBe('2027-01-01');
});
it('dateTo sent to the server is the last included day, not the exclusive bound', () =>
    expect(apiDateTo(monthRange(2026, 2).to)).toBe('2026-02-28'));
it('a transaction at the exclusive bound is outside the range', () => {
    const { from, to } = monthRange(2026, 3);
    expect(inRange(new Date(2026, 3, 1, 0, 0, 0).toISOString(), from, to)).toBe(false);
    expect(inRange(new Date(2026, 2, 31, 23, 59).toISOString(), from, to)).toBe(true);
    expect(inRange(new Date(2026, 2, 1, 0, 0).toISOString(), from, to)).toBe(true);
});
```

```ts
// scheduler.test.ts
import { expect, it } from 'vitest';
import { nextChargeDate as n } from './scheduler';

it('daily and weekly add days', () => {
    expect(n('2026-01-30', 'DAILY', 3)).toBe('2026-02-02');
    expect(n('2026-01-30', 'WEEKLY', 2)).toBe('2026-02-13');
});
it('monthly clamps to the end of a shorter month, like kotlinx-datetime', () => {
    expect(n('2026-01-31', 'MONTHLY', 1)).toBe('2026-02-28');
    expect(n('2028-01-31', 'MONTHLY', 1)).toBe('2028-02-29');
});
it('yearly from a leap day lands on Feb 28', () => expect(n('2028-02-29', 'YEARLY', 1)).toBe('2029-02-28'));
it('an interval below 1 counts as 1', () => expect(n('2026-01-01', 'DAILY', 0)).toBe('2026-01-02'));
```

`dates.ts`:

```ts
const pad = (n: number) => String(n).padStart(2, '0');

/** The local calendar date - what the user means by "today" - as yyyy-MM-dd. */
export const isoDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const todayIso = () => isoDate(new Date());

export const monthRange = (year: number, month1: number) => ({
    from: new Date(year, month1 - 1, 1),
    to: new Date(year, month1, 1),
});

/**
 * The server reads dateTo as an inclusive day, so an exclusive midnight bound is sent as
 * the day before it. Without this a month's list picked up the 1st of the next month.
 */
export const apiDateTo = (toExclusive: Date) => isoDate(new Date(toExclusive.getTime() - 1));

/** The second half of the same fix: filter on the instant as well, [from, to). */
export function inRange(occurredAt: string, from?: Date, to?: Date): boolean {
    const t = new Date(occurredAt).getTime();
    return (!from || t >= from.getTime()) && (!to || t < to.getTime());
}
```

`scheduler.ts`:

```ts
export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

function addMonths(y: number, m0: number, d: number, months: number) {
    const total = m0 + months;
    const ny = y + Math.floor(total / 12);
    const nm = ((total % 12) + 12) % 12;
    const last = new Date(Date.UTC(ny, nm + 1, 0)).getUTCDate();
    return new Date(Date.UTC(ny, nm, Math.min(d, last)));
}

/** Port of SubscriptionScheduler.kt, including kotlinx-datetime's end-of-month clamping. */
export function nextChargeDate(currentIso: string, frequency: Frequency, interval: number): string {
    const n = Math.max(1, interval);
    const [y, m, d] = currentIso.split('-').map(Number);
    let out: Date;
    switch (frequency) {
        case 'DAILY': out = new Date(Date.UTC(y, m - 1, d + n)); break;
        case 'WEEKLY': out = new Date(Date.UTC(y, m - 1, d + 7 * n)); break;
        case 'MONTHLY': out = addMonths(y, m - 1, d, n); break;
        case 'YEARLY': out = addMonths(y, m - 1, d, 12 * n); break;
    }
    return out.toISOString().slice(0, 10);
}
```

- [ ] **Step 5: Write `routes.test.ts` (ported from `RoutesTest.kt`, plus legacy mapping), then implement `routes.ts`**

```ts
import { expect, it } from 'vitest';
import { isKnownRoute, legacyFragmentToPath } from './routes';

const registered = ['dashboard', 'accounts', 'account/edit?accountId={accountId}', 'transaction/edit/{transactionId}',
    'quickadd?type={type}&categoryId={categoryId}', 'import/homemoney'];

it('a plain section is known', () => {
    expect(isKnownRoute('accounts', registered)).toBe(true);
    expect(isKnownRoute('import/homemoney', registered)).toBe(true);
});
it('arguments do not have to be spelled the way the pattern spells them', () => {
    expect(isKnownRoute('account/edit', registered)).toBe(true);
    expect(isKnownRoute('account/edit?accountId=33', registered)).toBe(true);
    expect(isKnownRoute('quickadd?type=EXPENSE&categoryId=', registered)).toBe(true);
});
it('a path argument matches whatever is in its place', () => {
    expect(isKnownRoute('transaction/edit/34', registered)).toBe(true);
    expect(isKnownRoute('transaction/edit/anything', registered)).toBe(true);
});
it('an address no screen answers to is not known', () => {
    expect(isKnownRoute('nosuchscreen', registered)).toBe(false);
    expect(isKnownRoute('account', registered)).toBe(false);
    expect(isKnownRoute('account/edit/33', registered)).toBe(false);
    expect(isKnownRoute('transaction/edit', registered)).toBe(false);
});
it('an empty address is not known', () => {
    expect(isKnownRoute('', registered)).toBe(false);
    expect(isKnownRoute('/', registered)).toBe(false);
});
it('old wasm fragments map onto the new paths', () => {
    expect(legacyFragmentToPath('accounts')).toBe('/app/accounts');
    expect(legacyFragmentToPath('dashboard')).toBe('/app');
    expect(legacyFragmentToPath('transaction/edit/12')).toBe('/app/transactions/12');
    expect(legacyFragmentToPath('account/edit?accountId=3')).toBe('/app/accounts/edit?accountId=3');
    expect(legacyFragmentToPath('category/edit?type=INCOME')).toBe('/app/categories/edit?type=INCOME');
    expect(legacyFragmentToPath('quickadd?type=TRANSFER')).toBe('/app/quickadd?type=TRANSFER');
    expect(legacyFragmentToPath('more')).toBeNull();
    expect(legacyFragmentToPath('bogus')).toBeNull();
});
```

`routes.ts`:

```ts
export const APP = '/app';

export const AppRoutes = {
    dashboard: APP,
    transactions: `${APP}/transactions`,
    transactionEdit: (id: number | string) => `${APP}/transactions/${id}`,
    quickAdd: (type = 'EXPENSE', categoryId?: number) =>
        `${APP}/quickadd?type=${type}${categoryId != null ? `&categoryId=${categoryId}` : ''}`,
    accounts: `${APP}/accounts`,
    accountEdit: (id?: number) => `${APP}/accounts/edit${id != null ? `?accountId=${id}` : ''}`,
    categories: `${APP}/categories`,
    categoryEdit: (id?: number, type = 'EXPENSE') => `${APP}/categories/edit?type=${type}${id != null ? `&categoryId=${id}` : ''}`,
    subscriptions: `${APP}/subscriptions`,
    subscriptionEdit: (id?: number) => `${APP}/subscriptions/edit${id != null ? `?subscriptionId=${id}` : ''}`,
    currencies: `${APP}/currencies`,
    currencyEdit: (code?: string) => `${APP}/currencies/edit${code ? `?code=${encodeURIComponent(code)}` : ''}`,
    analytics: `${APP}/analytics`,
    banks: `${APP}/banks`,
    homeMoney: `${APP}/import/homemoney`,
    premium: `${APP}/premium`,
    settings: `${APP}/settings`,
    onboarding: `${APP}/onboarding`,
} as const;

/** Routes.kt's isKnown: matched by shape, and the query is ignored on both sides. */
export function isKnownRoute(route: string, patterns: string[]): boolean {
    const path = route.split('?')[0].replace(/^\/+|\/+$/g, '');
    if (!path) return false;
    const segs = path.split('/');
    return patterns.some(p => {
        const exp = p.split('?')[0].replace(/^\/+|\/+$/g, '').split('/');
        return exp.length === segs.length && exp.every((e, i) => e.startsWith('{') || e === segs[i]);
    });
}

/** The wasm client's routes, as its fragments spelled them, with where each one lives now. */
const LEGACY: [pattern: string, to: (seg: string[], query: string) => string][] = [
    ['dashboard', () => AppRoutes.dashboard],
    ['quickadd', (_, q) => `${APP}/quickadd${q}`],
    ['transactions', () => AppRoutes.transactions],
    ['transaction/edit/{id}', s => AppRoutes.transactionEdit(s[2])],
    ['accounts', () => AppRoutes.accounts],
    ['account/edit', (_, q) => `${APP}/accounts/edit${q}`],
    ['categories', () => AppRoutes.categories],
    ['category/edit', (_, q) => `${APP}/categories/edit${q}`],
    ['subscriptions', () => AppRoutes.subscriptions],
    ['subscription/edit', (_, q) => `${APP}/subscriptions/edit${q}`],
    ['settings', () => AppRoutes.settings],
    ['premium', () => AppRoutes.premium],
    ['currencies', () => AppRoutes.currencies],
    ['currency/edit', (_, q) => `${APP}/currencies/edit${q}`],
    ['banks', () => AppRoutes.banks],
    ['import/homemoney', () => AppRoutes.homeMoney],
    ['analytics', () => AppRoutes.analytics],
    ['onboarding', () => AppRoutes.onboarding],
];

export function legacyFragmentToPath(fragment: string): string | null {
    for (const [pattern, to] of LEGACY) {
        if (isKnownRoute(fragment, [pattern])) {
            const [path, query] = fragment.split('?');
            return to(path.replace(/^\/+/, '').split('/'), query ? `?${query}` : '');
        }
    }
    return null;
}
```

- [ ] **Step 6: Run the tests**

Run: `npx vitest run src/app/lib`
Expected: PASS.

- [ ] **Step 7: Commit** — "Port FX, formatting, dates, scheduling and routes to TypeScript"

---

### Task 4: API client with single-flight refresh

**Files:**
- Create: `src/app/api/session.ts`, `src/app/api/http.ts`, `src/app/api/types.ts`, `src/app/api/endpoints.ts`, `src/app/api/queryClient.ts`
- Modify: `src/landing/session.ts` (re-export from `app/api/session.ts`, keep `hasSession`, and replace `enterApp` with `nextPath(search): string`)
- Test: `src/app/api/http.test.ts`, `src/app/api/errors.test.ts`

**Interfaces:**
- Produces:
  - `session.get(): {access,refresh,email} | null`, `session.save(res: AuthResponse)`, `session.clear()`, `session.subscribe(fn): () => void` (fires on save and clear)
  - `class ApiError extends Error { status: number; serverMessage: string | null; isPlanLimit: boolean }`
  - `api<T>(path: string, init?: { method?, json?, query?, form?: FormData }): Promise<T>`
  - `endpoints` (every method listed in Step 5)
  - DTO types that mirror `RestDtos.kt`, `BankDtos.kt`, `CsvDtos.kt`, `HomeMoneyDtos.kt` and `ApiDtos.kt` (`UserDto`, `BillingStatusDto`)
  - `queryClient`, and the query-key factory `qk`

- [ ] **Step 1: Write `errors.test.ts`, ported from `ApiErrorsTest.kt`, using the same bodies copied from the server**

```ts
import { expect, it } from 'vitest';
import { ApiError, apiErrorFrom } from './http';

const res = (status: number, body: string) => new Response(body, { status, headers: { 'Content-Type': 'application/json' } });

it("a rejected bank token reaches the user in the bank's own words", async () => {
    const e = await apiErrorFrom(res(422, '{"timestamp":"2026-08-19T21:55:27.9500155","status":422,"error":"Bank Rejected Credentials","message":"Monobank rejected the token"}'));
    expect(e.serverMessage).toBe('Monobank rejected the token');
});
it('a not-found says what was not found', async () => {
    const e = await apiErrorFrom(res(404, '{"status":404,"error":"Not Found","message":"Bank connection not found: 999999"}'));
    expect(e.serverMessage).toBe('Bank connection not found: 999999');
});
it('an internal error is not repeated to the user', async () => {
    const e = await apiErrorFrom(res(500, '{"status":500,"error":"Internal Server Error","message":"could not execute statement"}'));
    expect(e.serverMessage).toBeNull();
});
it("a body that is not the server's error shape yields nothing", async () => {
    expect((await apiErrorFrom(res(400, '<html>gateway error</html>'))).serverMessage).toBeNull();
});
it("the free plan's refusal is recognised as one", async () => {
    const body = '{"status":402,"error":"Plan Limit Reached","message":"The free plan covers 5 accounts. Subscribe to add more."}';
    expect((await apiErrorFrom(res(402, body))).isPlanLimit).toBe(true);
    expect((await apiErrorFrom(res(404, body))).isPlanLimit).toBe(false);
});
it('a non-HTTP failure has no server message', () => {
    expect(new ApiError(0, null).serverMessage).toBeNull();
});
```

- [ ] **Step 2: Write `http.test.ts` for the refresh behaviour**

```ts
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from './http';
import { session } from './session';

const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
    localStorage.clear();
    session.save({ token: 'old', refreshToken: 'r1', email: 'a@b.c' });
});
afterEach(() => vi.unstubAllGlobals());

it('sends the bearer token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, []));
    vi.stubGlobal('fetch', fetchMock);
    await api('/accounts');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer old');
});

it('spends one refresh token for several concurrent 401s', async () => {
    let refreshes = 0;
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
        if (url.endsWith('/auth/refresh')) {
            refreshes++;
            return json(200, { token: 'new', refreshToken: 'r2', email: 'a@b.c' });
        }
        const auth = (init.headers as Record<string, string>).Authorization;
        return auth === 'Bearer new' ? json(200, { ok: true }) : json(401, {});
    }));
    const results = await Promise.all([api('/a'), api('/b'), api('/c')]);
    expect(results).toEqual([{ ok: true }, { ok: true }, { ok: true }]);
    expect(refreshes).toBe(1);
    expect(session.get()?.refresh).toBe('r2');
});

it('clears the session when the refresh is refused', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => (url.endsWith('/auth/refresh') ? json(401, {}) : json(401, {}))));
    await expect(api('/a')).rejects.toMatchObject({ status: 401 });
    expect(session.get()).toBeNull();
});

it('never sends the bearer to the endpoints that mint tokens', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, {}));
    vi.stubGlobal('fetch', fetchMock);
    await api('/auth/login', { method: 'POST', json: {} });
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
});
```

- [ ] **Step 3: Run the tests to confirm they fail.** Run `npx vitest run src/app/api`. Expected: FAIL.

- [ ] **Step 4: Implement `session.ts` and `http.ts`**

```ts
// session.ts
/**
 * The browser session. These keys are what both the old wasm client and this one read, so
 * everyone signed in before the switch stays signed in after it.
 */
const ACCESS = 'pm_access_token';
const REFRESH = 'pm_refresh_token';
const EMAIL = 'pm_email';

export interface AuthResponse { token: string; refreshToken: string; email: string }
type Listener = () => void;
const listeners = new Set<Listener>();

const read = (k: string) => { try { return localStorage.getItem(k); } catch { return null; } };

export const session = {
    get() {
        const access = read(ACCESS);
        return access ? { access, refresh: read(REFRESH) ?? '', email: read(EMAIL) } : null;
    },
    save(res: AuthResponse) {
        localStorage.setItem(ACCESS, res.token);
        localStorage.setItem(REFRESH, res.refreshToken);
        if (res.email) localStorage.setItem(EMAIL, res.email); else localStorage.removeItem(EMAIL);
        listeners.forEach(l => l());
    },
    clear() {
        [ACCESS, REFRESH, EMAIL].forEach(k => localStorage.removeItem(k));
        listeners.forEach(l => l());
    },
    subscribe(l: Listener) { listeners.add(l); return () => { listeners.delete(l); }; },
};
```

```ts
// http.ts
import { session } from './session';

const BASE = '/api';
const TOKEN_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh'];
// Statuses whose `message` the server writes for a person. 500 is excluded on purpose:
// that message is a raw exception.
const USER_FACING = new Set([400, 402, 404, 422, 502]);

export class ApiError extends Error {
    constructor(public readonly status: number, public readonly serverMessage: string | null) {
        super(serverMessage ?? `HTTP ${status}`);
    }
    get isPlanLimit() { return this.status === 402; }
}

export async function apiErrorFrom(res: Response): Promise<ApiError> {
    let message: string | null = null;
    if (USER_FACING.has(res.status)) {
        try {
            const body = JSON.parse(await res.text());
            if (typeof body?.message === 'string' && body.message.trim()) message = body.message;
        } catch { /* not the server's error shape */ }
    }
    return new ApiError(res.status, message);
}

export interface RequestOptions {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    json?: unknown;
    query?: Record<string, string | number | boolean | null | undefined>;
    form?: FormData;
}

// One refresh at a time. Every 401 that arrives while it runs awaits the same promise,
// so a burst of requests spends exactly one refresh token, as the Ktor Auth plugin does.
let refreshing: Promise<boolean> | null = null;

function refreshOnce(): Promise<boolean> {
    if (!refreshing) {
        refreshing = (async () => {
            const refresh = session.get()?.refresh;
            if (!refresh) { session.clear(); return false; }
            try {
                const res = await fetch(`${BASE}/auth/refresh`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ refreshToken: refresh }),
                });
                if (!res.ok) throw new Error(String(res.status));
                session.save(await res.json());
                return true;
            } catch {
                // A revoked refresh token is terminal. Clearing is what sends the UI to sign-in.
                session.clear();
                return false;
            }
        })().finally(() => { refreshing = null; });
    }
    return refreshing;
}

function buildUrl(path: string, query?: RequestOptions['query']) {
    const qs = new URLSearchParams();
    Object.entries(query ?? {}).forEach(([k, v]) => { if (v !== null && v !== undefined && v !== '') qs.set(k, String(v)); });
    const s = qs.toString();
    return `${BASE}${path}${s ? `?${s}` : ''}`;
}

async function send(path: string, opts: RequestOptions): Promise<Response> {
    const headers: Record<string, string> = {};
    if (opts.json !== undefined) headers['Content-Type'] = 'application/json';
    const token = session.get()?.access;
    if (token && !TOKEN_ENDPOINTS.includes(path)) headers.Authorization = `Bearer ${token}`;
    return fetch(buildUrl(path, opts.query), {
        method: opts.method ?? 'GET',
        headers,
        body: opts.form ?? (opts.json !== undefined ? JSON.stringify(opts.json) : undefined),
    });
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
    let res = await send(path, opts);
    if (res.status === 401 && !TOKEN_ENDPOINTS.includes(path) && (await refreshOnce())) {
        res = await send(path, opts);
    }
    if (!res.ok) throw await apiErrorFrom(res);
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
}
```

- [ ] **Step 5: Implement `types.ts` and `endpoints.ts`**

`types.ts` transcribes every `@Serializable` class in these files into a TS interface, field for field. Money fields stay `string`, nullables become `?: T | null`, and enums become string-literal unions.
- `client/composeApp/src/commonMain/kotlin/com/personalmoney/core/network/RestDtos.kt`
- `BankDtos.kt`
- `CsvDtos.kt`
- `HomeMoneyDtos.kt`
- from `ApiDtos.kt`: `UserDto`, `BillingStatusDto`, `JwtResponseDto`

Nothing may be invented: every field comes from the Kotlin source. Start the file with:

```ts
export type TransactionType = 'EXPENSE' | 'INCOME' | 'TRANSFER';
export type CategoryType = 'EXPENSE' | 'INCOME';
export type SubscriptionFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
export interface Paged<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
```

`endpoints.ts` has one function per `RestApi.kt` method, plus these `HomeBudgetApi.kt` methods: `me`, `deleteMe`, `logout`, `billingStatus`. Same names, same paths, same query parameters. Examples of the shape:

```ts
import { api } from './http';
import type * as T from './types';

export const endpoints = {
    accounts: () => api<T.RestAccount[]>('/accounts'),
    account: (id: number) => api<T.RestAccount>(`/accounts/${id}`),
    createAccount: (body: T.CreateAccountRequest) => api<T.RestAccount>('/accounts', { method: 'POST', json: body }),
    updateAccount: (id: number, body: T.UpdateAccountRequest) => api<T.RestAccount>(`/accounts/${id}`, { method: 'PUT', json: body }),
    deleteAccount: (id: number) => api<void>(`/accounts/${id}`, { method: 'DELETE' }),
    categories: (type?: T.CategoryType) => api<T.RestCategory[]>('/categories', { query: { type } }),
    transactions: (q: { accountId?: number; categoryId?: number; type?: T.TransactionType; dateFrom?: string; dateTo?: string; page?: number; size?: number }) =>
        api<T.Paged<T.RestTransaction>>('/transactions', { query: { page: 0, size: 50, ...q } }),
    setBaseCurrency: (currencyId: number) => api<void>('/users/me/base-currency', { method: 'PUT', query: { currencyId } }),
    previewCsv: (accountId: number, file: File, mapping?: T.RestCsvMapping) => {
        const form = new FormData();
        form.append('file', file, file.name);
        return api<T.RestCsvPreview>('/import/csv/preview', { method: 'POST', form, query: { accountId, ...(mapping ?? {}) } });
    },
    // ...every remaining RestApi.kt / HomeBudgetApi.kt method, same path, same verb
};
```

`queryClient.ts`:

```ts
import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './http';

export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            // A 4xx will not improve by asking again.
            retry: (n, e) => !(e instanceof ApiError && e.status >= 400 && e.status < 500) && n < 2,
        },
    },
});

export const qk = {
    accounts: ['accounts'] as const,
    categories: ['categories'] as const,
    currencies: ['currencies'] as const,
    fx: ['fx'] as const,
    transactions: (f: object = {}) => ['transactions', f] as const,
    transfers: ['transfers'] as const,
    subscriptions: ['subscriptions'] as const,
    me: ['me'] as const,
    billing: ['billing'] as const,
    bank: ['bank'] as const,
    homeMoney: ['homemoney'] as const,
};

/** A change to the ledger moves balances, the overview and the analytics together. */
export const invalidateLedger = () =>
    Promise.all(['transactions', 'transfers', 'accounts', 'subscriptions'].map(k => queryClient.invalidateQueries({ queryKey: [k] })));
```

- [ ] **Step 6: Point the landing auth at the shared session**

In `src/landing/session.ts`, keep `saveSession` / `hasSession` as thin wrappers over `session`. Replace `enterApp()` with:

```ts
/**
 * Where to go after signing in. Only a path this app answers to is accepted; anything else
 * (another origin, a crafted value) falls back to the overview. Legacy `next` values
 * written by the wasm client are fragments like `accounts`, and are translated.
 */
export function nextPath(search: string): string {
    const next = new URLSearchParams(search).get('next');
    if (!next) return AppRoutes.dashboard;
    if (next.startsWith('/app') && !next.startsWith('//') && /^[A-Za-z0-9/?&=_.,:%-]{1,200}$/.test(next)) return next;
    return legacyFragmentToPath(next) ?? AppRoutes.dashboard;
}
```

`Login.tsx` and `Register.tsx` (and anything else that called `enterApp`) now call `navigate(nextPath(location.search), { replace: true })`. Add `nextPath` cases to `routes.test.ts`:
- `?next=/app/accounts` → `/app/accounts`
- `?next=accounts` → `/app/accounts`
- `?next=https://evil` → `/app`
- `?next=//evil` → `/app`

- [ ] **Step 7: Run all tests.** Run `npm test`. Expected: PASS.

- [ ] **Step 8: Commit** — "Give the web app a typed API client with single-flight token refresh"

---

### Task 5: Convert the Android strings into the web dictionary

**Files:**
- Create: `scripts/strings-to-ts.mjs` (repository root `scripts/`), `src/main/frontend/src/i18n/locales/app.en.ts`, `app.ru.ts`, `app.uk.ts`
- Modify: `src/main/frontend/src/i18n/locales/en.ts`, `ru.ts`, `uk.ts` (spread the app dictionaries in)
- Test: `src/main/frontend/src/i18n/app-strings.test.ts`

**Interfaces:**
- Produces: every `<string name="x">` from `strings.xml` becomes key `x`. Every `<plurals name="x">` item becomes `x_one` / `x_few` / `x_many` / `x_other`. Placeholders:
  - `%1$s` and `%1$d` in a `<string>` become `{{0}}`, and `%2$…` becomes `{{1}}`;
  - inside `<plurals>`, `%1$d` becomes `{{count}}`, and later indices stay `{{1}}`, `{{2}}`.

- [ ] **Step 1: Write the test**

```ts
import { expect, it } from 'vitest';
import { en } from './locales/en';
import { ru } from './locales/ru';
import { uk } from './locales/uk';
import { translate } from '.';

it('every app key exists in all three languages', () => {
    const keys = Object.keys(en).filter(k => /^[a-z0-9_]+$/.test(k));
    expect(keys.length).toBeGreaterThan(400);
    for (const k of keys) {
        expect(ru, k).toHaveProperty(k);
        expect(uk, k).toHaveProperty(k);
    }
});
it('placeholders are converted', () => {
    expect(translate('en', 'dashboard_more_categories', { 0: 3 })).toBe('+3 more categories');
    expect(translate('en', 'accounts_delete_warning', { 0: 'Cash', 1: 2 })).toBe('Delete "Cash"? Its 2 transactions will also be permanently deleted.');
});
it('plurals follow Intl rules', () => {
    expect(translate('en', 'dashboard_account_count', { count: 1 })).toBe('1 account');
    expect(translate('ru', 'dashboard_account_count', { count: 5 })).toMatch(/^5 /);
});
it("android escapes are removed", () => expect(en.settings_import_confirm_message).toContain("can't"));
```

- [ ] **Step 2: Write `scripts/strings-to-ts.mjs`**

A Node script with no dependencies. For each of `values`, `values-ru` and `values-uk`, it:
1. reads `client/composeApp/src/commonMain/composeResources/<dir>/strings.xml`;
2. extracts `<string name="…">…</string>` and `<plurals name="…"><item quantity="…">…</item></plurals>` with regexes (the file has no CDATA or string-arrays; the script checks for them and exits 1 if they ever appear);
3. unescapes `\'`, `\"`, `\n`, `\@`, `\?`, and the XML entities `&amp; &lt; &gt; &quot; &apos;`;
4. converts the placeholders as described under Interfaces;
5. writes `src/main/frontend/src/i18n/locales/app.<lang>.ts` as `export const app<Lang> = { … } as const;` with keys sorted and values `JSON.stringify`'d.

Header comment of each generated file: `// Generated once from the Android strings by scripts/strings-to-ts.mjs. Edit this file directly from now on.`

- [ ] **Step 3: Run it and merge**

Run: `node scripts/strings-to-ts.mjs`

In `en.ts`, add `import { appEn } from './app.en';` and put `...appEn,` as the first entry of the object, so landing keys win on any clash. There are none, because landing keys contain dots. Do the same in `ru.ts` and `uk.ts`.

- [ ] **Step 4: Run the tests.** `npx vitest run src/i18n`. Expected: PASS.

- [ ] **Step 5: Commit** — "Bring the app's strings into the web dictionary"

---

### Task 6: App shell: routing, guard, header, theme, redirects

**Files:**
- Create:
  - `src/app/AppRoot.tsx` (providers and the `/app/*` routes)
  - `src/app/shell/AuthGuard.tsx`, `src/app/shell/AppHeader.tsx`, `src/app/shell/AppDrawer.tsx`, `src/app/shell/AccountMenu.tsx`, `src/app/shell/Page.tsx` (`PageContainer` with `content|form` width)
  - `src/app/shell/useMediaQuery.ts`
  - `src/app/boot.ts` (legacy hash rewrite and HomeMoney query capture)
  - `src/app/prefs.ts` (typed localStorage preferences)
- Modify: `src/App.tsx` (mount `/app/*`, drop `RedirectToApp`, and map `LEGACY_ROUTES` onto `AppRoutes`), `src/theme.tsx` (add `system` mode and the legacy value read), `src/index.css` (light-theme values from `Color.kt`), `src/i18n/index.tsx` (call `setFormatLocale(lang)`)
- Test: `src/app/boot.test.ts`, `src/app/prefs.test.ts`, `src/app/shell/AuthGuard.test.tsx`

**Interfaces:**
- Consumes: `session`, `AppRoutes`, `legacyFragmentToPath`, `queryClient`.
- Produces:
  - `prefs` with `onboarded` / `setOnboarded`, `defaultAccountId` / `setDefaultAccountId`, `baseCurrency` / `setBaseCurrency`, `homeMoneyAuthState` / `setHomeMoneyAuthState`, and `clearAccountScoped()` (removes `default_account`, `base_currency`, `homemoney_auth_state`, `premium`, `premium_expires_at_ms`, `sync_cursor`, `sync_last_ok_ms`)
  - `takeHomeMoneyRedirect(): { kind: 'pending'; code: string } | { kind: 'denied' } | null`
  - `<Page width="content"|"form" title? actions?>`
  - `useSignOut()`
  - `ThemeMode = 'system' | 'light' | 'dark'` in `useTheme()`, as `{ mode, setMode, resolved: 'light'|'dark', toggleTheme }`

- [ ] **Step 1: Write `boot.test.ts`**

```ts
import { beforeEach, expect, it } from 'vitest';
import { bootLocation, takeHomeMoneyRedirect } from './boot';

beforeEach(() => window.history.replaceState(null, '', '/'));

it('rewrites a known wasm bookmark to its path', () => {
    window.history.replaceState(null, '', '/app/#account/edit?accountId=3');
    bootLocation();
    expect(location.pathname + location.search + location.hash).toBe('/app/accounts/edit?accountId=3');
});
it('drops an unknown fragment', () => {
    window.history.replaceState(null, '', '/app/#more');
    bootLocation();
    expect(location.pathname + location.hash).toBe('/app');
});
it('captures the HomeMoney redirect once and cleans the address bar', () => {
    window.history.replaceState(null, '', '/app/?homemoney=pending&code=abc');
    bootLocation();
    expect(location.pathname + location.search).toBe('/app/import/homemoney');
    expect(takeHomeMoneyRedirect()).toEqual({ kind: 'pending', code: 'abc' });
    expect(takeHomeMoneyRedirect()).toBeNull();
});
it('treats anything but a pending code as denied', () => {
    window.history.replaceState(null, '', '/app/?homemoney=error');
    bootLocation();
    expect(takeHomeMoneyRedirect()).toEqual({ kind: 'denied' });
});
```

- [ ] **Step 2: Implement `boot.ts`**

```ts
import { AppRoutes, legacyFragmentToPath } from './lib/routes';

export type HomeMoneyRedirect = { kind: 'pending'; code: string } | { kind: 'denied' };
let pending: HomeMoneyRedirect | null = null;

/**
 * Runs once, before the router reads the address. Two kinds of URL arrive from outside the
 * app: wasm-era bookmarks (/app/#accounts) and HomeMoney's return trip
 * (/app/?homemoney=pending&code=…). Both are rewritten with replaceState, so Back does not
 * return to them and a one-time code cannot be spent twice.
 */
export function bootLocation(): void {
    if (!location.pathname.startsWith('/app')) return;
    const params = new URLSearchParams(location.search);
    const outcome = params.get('homemoney');
    if (outcome !== null) {
        const code = params.get('code');
        pending = outcome === 'pending' && code ? { kind: 'pending', code } : { kind: 'denied' };
        history.replaceState(null, '', AppRoutes.homeMoney);
        return;
    }
    const fragment = location.hash.slice(1);
    if (fragment) history.replaceState(null, '', legacyFragmentToPath(fragment) ?? AppRoutes.dashboard);
}

export function takeHomeMoneyRedirect(): HomeMoneyRedirect | null {
    const v = pending;
    pending = null;
    return v;
}
```

Call `bootLocation()` in `main.tsx` before `render`.

- [ ] **Step 3: `prefs.ts` with a test covering the legacy theme read and `clearAccountScoped`, then implement**

The theme lives in `theme.tsx`. `readThemeMode()` maps `light`/`LIGHT` → `light`, `dark`/`DARK` → `dark`, and anything else → `system`. The inline script in `index.html` resolves `system` through `matchMedia('(prefers-color-scheme: dark)')`. `ThemeProvider` listens to that media query while the mode is `system`. `toggleTheme` flips between the resolved light and dark values and stores the explicit choice, which is what the header button does in the wasm client.

- [ ] **Step 4: Update the light theme tokens in `index.css`**

Under `[data-theme="light"]`, set the values from `client/.../ui/theme/Color.kt`:
- `--bg-base-rgb: 230 235 243`, `--bg-surface-rgb: 255 255 255`, `--bg-card-rgb: 255 255 255`, `--bg-hover-rgb: 212 221 233`
- `--border-rgb: 195 204 218`, `--border-light-rgb: 148 163 184`
- `--text-primary-rgb: 15 23 42`, `--text-secondary-rgb: 71 85 105`, `--text-muted-rgb: 90 103 121`

Take the income/expense light values from the `// ── Money` section of `Color.kt`. Keep any light value the landing page already has if it equals these.

- [ ] **Step 5: `AuthGuard.test.tsx`, then implement the guard**

The test renders `<MemoryRouter initialEntries={['/app/accounts?x=1']}>` with no session and asserts navigation to `/login?next=%2Fapp%2Faccounts%3Fx%3D1`. It then saves a session, calls `session.clear()` while the guard is mounted, and asserts that it redirects.

```tsx
export function AuthGuard({ children }: { children: React.ReactNode }) {
    const signedIn = useSyncExternalStore(session.subscribe, () => session.get() !== null);
    const loc = useLocation();
    if (!signedIn) {
        const next = encodeURIComponent(loc.pathname + loc.search);
        return <Navigate to={`/login?next=${next}`} replace />;
    }
    return <>{children}</>;
}
```

- [ ] **Step 6: Shell components**

Port `navigation/AppHeader.kt` (`AppHeader`, `AppDrawerSheet`, `BrandMark`, `HeaderNavLink`, `AccountChip`):
- Header sections, in order:

  | Route | Label key | Icon |
  | --- | --- | --- |
  | dashboard | `nav_overview` | Home |
  | transactions | `nav_transactions` | List |
  | analytics | `nav_analytics` | BarChart3 |
  | accounts | `nav_accounts` | Wallet |
  | categories | `nav_categories` | Tags |
  | subscriptions | `dashboard_subscriptions` | Repeat |
  | currencies | `nav_currencies` | Globe |
  | import/homemoney | `nav_homemoney` | ArrowLeftRight |
  | banks | `nav_banks` | Landmark |

  Settings is **not** in the header row, only in the drawer and the account menu. Icons are lucide-react.
- Nav links are inline at `(min-width: 1240px)`. Below that, a menu button opens `AppDrawer`, which lists all sections plus Settings and closes on navigation, on Escape and on backdrop click. It is closed automatically when the width reaches 1240.
- The wordmark shows at `(min-width: 480px)`.
- "+" (`action_add_expense_fab`) shows only below 900px and not on onboarding. It opens `AppRoutes.quickAdd('EXPENSE')`.
- The account chip shows the email (or `header_user`), with a menu of `premium_menu` → premium, `dashboard_settings` → settings, and `header_sign_out`. It sits next to the theme toggle.
- Sign-out: call `endpoints.logout()` with errors ignored, then `session.clear()`, `prefs.clearAccountScoped()` and `queryClient.clear()`, then navigate to `/login`. Check `AppShellViewModel.kt` for anything else it clears.
- The header is hidden on `/app/onboarding`.

`AppRoot.tsx`: `QueryClientProvider` + `AuthGuard` + the header + `<Routes>` with one route per `AppRoutes` entry, each lazy-loaded with `React.lazy`. The index route checks `prefs.onboarded` and redirects to onboarding when it is false. It also loads currencies once through `useQuery(qk.currencies)` and calls `CurrencyRegistry.update` in an effect, so formatting knows the user's symbols. Every screen that is not built yet renders a plain `<Page title=…>` placeholder. The placeholders are replaced task by task and must all be gone by Task 17.

In `App.tsx`, `<Route path="/app/*" element={<Suspense><AppRoot /></Suspense>} />` is lazy, so the landing page does not download the app. `LEGACY_ROUTES` now maps the old top-level React paths (`/accounts` etc.) with `<Navigate replace>` to `AppRoutes.*`.

- [ ] **Step 7: Verify**

Run `npm test && npm run typecheck`. Run `npm run dev` with the backend running, then:
1. Sign in at `/login?next=/app/accounts`; you land on `/app/accounts`.
2. Open `/app/#categories` and check it becomes `/app/categories`.
3. Resize across 1240, 900 and 480 and watch the header change.
4. Toggle the theme.
5. Sign out.
6. Remove `pm_access_token` in devtools and reload; you are sent to `/login?next=…`.

- [ ] **Step 8: Commit** — "Add the app shell: routes, sign-in guard, header and drawer"

---

### Task 7: Shared components

**Files:**
- Create in `src/app/components/`: `AmountField.tsx`, `CurrencySelect.tsx`, `ConfirmDialog.tsx`, `Dialog.tsx`, `EmptyState.tsx`, `IconCircle.tsx`, `MoneyText.tsx`, `SelectorChip.tsx`, `AccountPicker.tsx`, `CategoryBreakdownChart.tsx`, `ErrorBanner.tsx`, `icons.tsx`
- Test: `AmountField.test.tsx`, `ConfirmDialog.test.tsx`, `icons.test.ts`

**Interfaces:**
- Consumes: `Calculator`, `sanitizeExpression`, `formatMoney`, `ApiError`.
- Produces:
  - `<AmountField value onChange decimals currency autoFocus? />`: sanitises input, and shows `= result` under the field when `Calculator.hasOperator`. `onChange(expr: string)`.
  - `evaluateAmount(expr, decimals): Decimal | null`, re-exported from `Calculator`.
  - `<ConfirmDialog open title message confirmLabel destructive onConfirm onCancel />`
  - `<Dialog>`: focus trap, Escape and backdrop close.
  - `<MoneyText money colored? />`: income/expense colouring by sign.
  - `<AccountPicker open accounts selectedId onPick onClose />`
  - `<EmptyState icon title message action? />`
  - `<ErrorBanner error />`: shows `serverMessage`, or the localised fallback `error_generic`. When `isPlanLimit`, it adds a link to Premium (key `premium_see`; check the key names in strings.xml).
  - `iconFor(name: string): LucideIcon`, which maps the Material icon names stored on accounts, categories and subscriptions (`wallet`, `category`, `subscriptions`, …) to lucide icons, with a generic fallback.

- [ ] **Step 1: Tests**

`AmountField.test.tsx`: typing `1a2+3` gives `onChange('12+3')` and shows `= 15.00`; typing `12.34.` keeps `12.34`.

`ConfirmDialog.test.tsx`: Escape calls `onCancel`, and the confirm button calls `onConfirm`.

`icons.test.ts`: every icon name in `core/ui/icons/IconLibrary.kt` maps to a component, and an unknown name returns the fallback.

- [ ] **Step 2: Implement**

Port the look and behaviour of each component from its Compose file in `core/ui/components/` and from `core/ui/icons/IconLibrary.kt`. Read the list of icon names from `IconLibrary.kt`. `CategoryBreakdownChart` uses a Recharts `PieChart` with `CATEGORY_COLORS` and `useChartColors()` from `theme.tsx`, and ports `CategoryBreakdownChart.kt`'s grouping of small slices into "+N more" (`dashboard_more_categories`).

- [ ] **Step 3: Run the tests; commit** — "Add the app's shared components"

---

### Task 8: Settings and currencies

**Source:**
- `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `PlatformSettings.kt` (the wasm actual in `wasmJsMain/.../PlatformSettings.wasmJs.kt`)
- `feature/currencies/*` (4 files)
- `wasmJsMain/.../RestCurrencyRepository.kt`, `RestExchangeRateRepository.kt`

**Files:** `src/app/features/settings/SettingsPage.tsx`, `src/app/features/currencies/CurrenciesPage.tsx`, `CurrencyEditPage.tsx`, `currencies.logic.ts` (+ test)

**Interfaces:** `useCurrencies()` and `useFxRates()` hooks in `src/app/api/hooks.ts`. They return the parsed lists (`rate` → number via `Number(rate)`, for `FxSnapshot` input only). `useFxSnapshot(): FxSnapshot` is memoised.

Follow the protocol, and additionally:
- Settings keeps the web-only sections the wasm client shows: theme, base currency (links to currencies), account deletion (`endpoints.deleteMe`, confirm dialog, then sign out), and the language picker. The language picker is new on the web: the wasm client had none, but the landing page already has `LanguageSwitcher`, so reuse it.
- Choosing the base currency: type three letters (commit a75bdef). Port the filter from `CurrencySelect.kt` into `currencies.logic.ts` and test it: prefix match on code first, then substring on name.
- FX override: `setFxOverride(code, perUsd)` / `clearFxOverride(code)`, then invalidate `qk.fx`.
- Commit — "Port settings and currencies to React"

### Task 9: Categories

**Source:** `feature/categories/*` (4 files), `wasmJsMain/.../RestCategoryRepository.kt`, `RestColors.kt`

**Files:** `src/app/features/categories/CategoriesPage.tsx`, `CategoryEditPage.tsx`, `categories.logic.ts` (+ test)

Follow the protocol. The expense/income tabs come from the `type` search param. The edit page takes `categoryId` and `type`. Port the colour palette from `RestColors.kt`. Put `budgetLimit` through `AmountField`, and send it via `toFixedString`. After a write, invalidate `qk.categories`.

Commit — "Port categories to React"

### Task 10: Accounts

**Source:** `feature/accounts/AccountsScreen.kt`, `AccountsViewModel.kt`, `AccountEditScreen.kt`, `AccountEditViewModel.kt`, `wasmJsMain/.../RestAccountRepository.kt`

**Files:** `src/app/features/accounts/AccountsPage.tsx`, `AccountEditPage.tsx`, `accounts.logic.ts` (+ test)

Follow the protocol. Move the totals per currency and the converted total (with the "missing rate" count, `dashboard_total_missing_rate`) into `accounts.logic.ts`. `Dashboard` uses them too. Test them with `FxSnapshot`: a USDT account counts, a BTC account without a rate is counted as missing, and archived accounts plus `includeInTotal=false` are excluded.
- The delete warning uses `accounts_delete_warning` with the transaction count.
- A 402 on create shows `ErrorBanner` with the Premium link.
- After a write, run `invalidateLedger()`.

Commit — "Port accounts to React"

### Task 11: Transactions and transfers

**Source:**
- `feature/transactions/*` (4 files), `feature/transfers/TransferViewModel.kt`
- `wasmJsMain/.../RestTransactionRepository.kt`, `LazyRefresh.kt`
- `core/data/LedgerFilterRequest.kt`

**Files:** `src/app/features/transactions/TransactionsPage.tsx`, `TransactionEditPage.tsx`, `ledger.logic.ts` (+ test), `useLedger.ts`

Follow the protocol, and additionally:
- The list merges transactions and transfers exactly as `RestTransactionRepository` / `TransactionsViewModel` do, grouped by day with `formatMediumDate` headers, and filtered by month/account/category/type.
- Date bounds use `monthRange`, `apiDateTo` and `inRange` from Task 3. Test in `ledger.logic.test.ts` that a transaction at 00:00 on the 1st of the next month is excluded.
- Transfer rows show from → to. Deleting a transfer uses `deleteTransfer(id)`, and a transfer-group transaction opened for editing routes to the transfer's delete, as the Kotlin does.
- The total count uses `transactions_total_count`, a plural.
- At ≥900px, the page shows `QuickEntryPanel` from Task 12 inline. Build this task with the panel slot empty, and fill it in Task 12.

Commit — "Port transactions and transfers to React"

### Task 12: Quick add

**Source:**
- `feature/quickadd/*` (8 files, including `components/`), `feature/transfers/TransferViewModel.kt`, `feature/quickadd/EntryMode.kt`

**Files:** `src/app/features/quickadd/QuickAddPage.tsx`, `QuickEntryPanel.tsx`, `quickadd.logic.ts` (+ test)

Follow the protocol, and additionally:
- The three modes are EXPENSE / INCOME / TRANSFER (`EntryMode.kt`). The initial mode and category come from `?type=` and `?categoryId=`.
- The amount goes through `AmountField` and is evaluated with the account currency's decimals.
- Transfers across currencies: port the auto-computed `toAmount` from `TransferViewModel.kt` (rate from `useFxSnapshot`, editable, and the fee). Test the conversion and fee arithmetic in `quickadd.logic.test.ts`.
- The account comes from `prefs.defaultAccountId`, else the first non-archived account. Picking one goes through the `AccountPicker` dialog.
- When no categories exist, show the `NoCategoriesHint` equivalent.
- On save: create, then `invalidateLedger()`. The page navigates back; the inline panel resets and stays.
- Mount `QuickEntryPanel` in the Task 11 slot and on the dashboard (Task 13) at ≥900px.

Commit — "Port quick add to React"

### Task 13: Dashboard

**Source:** `feature/dashboard/DashboardScreen.kt`, `DashboardViewModel.kt`

**Files:** `src/app/features/dashboard/DashboardPage.tsx`, `dashboard.logic.ts` (+ test)

Follow the protocol. Port every section: total balance with the missing-rate note, accounts list with `dashboard_account_count`, this month's income and expense, the 6-month chart (commit a0e120d fixed it), the category breakdown with `CategoryBreakdownChart`, recent transactions, upcoming subscriptions, and the inline entry panel at ≥900px.

In `dashboard.logic.ts`, test the 6-month bucketing: months with no transactions show 0, the current month is last, amounts are converted to base with `FxSnapshot`, and transfers are excluded. Clicking an account opens `/app/transactions?accountId=…`.

Commit — "Port the overview to React"

### Task 14: Subscriptions

**Source:** `feature/subscriptions/*` (5 files), `wasmJsMain/.../RestSubscriptionRepository.kt`

**Files:** `src/app/features/subscriptions/SubscriptionsPage.tsx`, `SubscriptionEditPage.tsx`, `subscriptions.logic.ts` (+ test)

Follow the protocol. `nextChargeDate` comes from Task 3. Test the monthly-cost normalisation (daily/weekly/yearly × interval, converted to base) in `subscriptions.logic.ts`. Build the frequency label from `freq_interval_prefix` and the frequency strings. `reminderMinutesBefore` defaults to 1440.

Commit — "Port subscriptions to React"

### Task 15: Analytics, Premium, Onboarding

**Source:**
- `feature/analytics/*`, `feature/premium/*`, `feature/onboarding/*`
- `core/billing/EntitlementRepository.kt`, and the `UnavailableStoreBilling` behaviour in `WebModules.kt`

**Files:**
- `src/app/features/analytics/AnalyticsPage.tsx` + `analytics.logic.ts` (+ test)
- `src/app/features/premium/PremiumPage.tsx`
- `src/app/features/onboarding/OnboardingPage.tsx` + `onboarding.logic.ts` (+ test)

Follow the protocol, and additionally:
- **Analytics:** the period selection (a0e120d) and the category breakdown. Test the period ranges and per-category totals in base currency.
- **Premium:** `billingStatus()` shows the plan and expiry. There is no purchase button; the page says where to buy, as `PremiumScreen` does with `UnavailableStoreBilling`.
- **Onboarding:** port the three steps (base currency, first account, default categories if the Kotlin does that). Finishing calls `prefs.setOnboarded(true)` and navigates to the dashboard with `replace`. Test the step validation.

Commit — "Port analytics, premium and onboarding to React"

### Task 16: Banks, CSV import, HomeMoney

**Source:**
- `feature/banks/*` (5 files), `feature/homemoney/*` (3 files)
- `wasmJsMain/.../RestBankRepository.kt`, `RestCsvImportRepository.kt`, `RestHomeMoneyImportRepository.kt`
- `docs/monobank-corporate.md`

**Files:**
- `src/app/features/banks/BanksPage.tsx`, `CsvImportCard.tsx`, `banks.logic.ts` (+ test)
- `src/app/features/homemoney/HomeMoneyPage.tsx`, `homemoney.logic.ts` (+ test)

Follow the protocol, and additionally:
- **Banks:** providers and connections, connect with a token, the consent flow (`beginBankConsent`, then poll `bankConsentStatus` at the interval `BanksViewModel` uses, stopping on unmount), verify, update credentials, disconnect, link and unlink accounts, and import preview/confirm with the `ImportChoice.kt` choices (as a transfer vs spending, c89ae74). Test the choice mapping.
- **CSV:** file input, preview with the column mapping controls, then confirm. The file goes through `endpoints.previewCsv` as multipart.
- **HomeMoney:**
  1. `authorizeHomeMoney()` returns a URL and a state. Store the state with `prefs.setHomeMoneyAuthState`, then `location.assign(url)`.
  2. On return, `takeHomeMoneyRedirect()`: if it is pending, call `connectHomeMoneyWithCode(code, prefs.homeMoneyAuthState)` and clear the state; if denied, show the denial.
  3. Then plan, then apply, then poll status until it finishes, as `HomeMoneyImportViewModel` does. Invalidate everything when done.
  4. Test the state machine transitions in `homemoney.logic.test.ts`.

Commit — "Port bank connections, CSV import and HomeMoney to React"

---

### Task 17: Serve the React app at /app on the server

**Files:**
- Modify: `src/main/kotlin/com/homebudget/app/config/WebConfig.kt`, `src/main/kotlin/com/homebudget/app/controller/SpaFallbackController.kt`
- Test: `src/test/kotlin/com/homebudget/app/config/WebConfigTest.kt` (new, `@SpringBootTest` + `MockMvc`, profile `local`)

- [ ] **Step 1: Write the failing MockMvc test**

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WebConfigTest(@Autowired val mvc: MockMvc) {
    @Test fun `app root serves the React shell`() {
        mvc.get("/app").andExpect { status { isOk() }; forwardedUrl("/index.html") }
        mvc.get("/app/").andExpect { status { isOk() }; forwardedUrl("/index.html") }
    }
    @Test fun `deep app routes serve the React shell`() {
        mvc.get("/app/accounts/edit").andExpect { status { isOk() } }
        mvc.get("/app/transactions/12").andExpect { status { isOk() } }
    }
    @Test fun `api is not swallowed by the shell`() {
        mvc.get("/api/accounts").andExpect { status { isUnauthorized() } }
    }
}
```

If the test context cannot see `static/index.html` because the frontend was not built, create `src/test/resources/static/index.html` containing `<!doctype html><title>test</title>`, and assert on status only.

- [ ] **Step 2: Rewrite `WebConfig`**

Keep a resource handler on `/app/**`, and add a `PathResourceResolver` override that always returns `ClassPathResource("static/index.html")`. Update the KDoc: why it stays a resource handler (PathPatternParser cannot express a multi-segment no-dot fallback), and that `/app/**` has no files of its own any more. Add a handler for `/assets/**` → `classpath:/static/assets/` with `CacheControl.maxAge(365, DAYS).cachePublic().immutable()`, because Vite content-hashes every file there. Delete the `.wasm` handler.

In `SpaFallbackController`, make `/app` and `/app/` forward to `/index.html`, and fix the comment.

- [ ] **Step 3: Run the server tests**

Run: `./mvnw -B -Dfrontend.skip=true test`
Expected: PASS.

- [ ] **Step 4: Full build and manual check**

Run `./mvnw -B package -DskipTests`, then `java -jar target/homebudgeting-*.jar --spring.profiles.active=local`. Open `http://localhost:8080/`, `/login`, `/app/accounts`, and reload on `/app/transactions/1`. Every one renders the React app.

- [ ] **Step 5: Commit** — "Serve the React app at /app"

### Task 18: Remove the wasm client

**Files:**
- Modify: `client/composeApp/build.gradle.kts`, `Dockerfile`, `.gitignore`, `.github/workflows/deploy.yml`, `README.md`, `client/README.md`, `client/CLAUDE.md`
- Delete: `client/composeApp/src/wasmJsMain/`, `web-dist/`, `client/kotlin-js-store/` (if only wasm uses it)

- [ ] **Step 1: Gradle**

Delete the `wasmJs { … }` target block and `wasmJsMain.dependencies { … }` from `build.gradle.kts`. Remove any wasm-only plugin or dependency catalogue entries that become unused. Then:

```bash
git rm -r client/composeApp/src/wasmJsMain
```

Run: `cd client && ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL. If `commonMain` has an `expect` whose only other `actual` lived in wasm, Android's `actual` still satisfies it. A compile error here means a wasm-only `expect` with no Android actual: add the minimal Android actual, not a deletion, because the web-only code stays by decision.

- [ ] **Step 2: Docker and ignore rules**

Remove the `COPY web-dist/ …` line and its comment block from `Dockerfile`. `git rm -r web-dist`. Remove the `/web-dist/*`, `!/web-dist/.gitkeep` lines and their comments from `.gitignore`.

- [ ] **Step 3: CI**

In `deploy.yml`:
- the `client` job keeps checkout, JDK, Android SDK, Gradle, and "Run client tests". Delete "Build the bundle", "Collect" and "Upload the bundle", and rename it "Android Client Tests";
- add a `frontend` job: checkout, `actions/setup-node@v4` with `node-version: '20'`, `cache: npm`, `cache-dependency-path: src/main/frontend/package-lock.json`, then in `src/main/frontend` run `npm ci`, `npm run typecheck`, `npm test`;
- `build-and-push`: set `needs: [test, client, frontend]`, and delete "Download the web bundle" and "Check the bundle arrived".

Update the comment in the `test` job, which mentions the React bundle being built inside the Docker image. That is still true, so keep its meaning.

- [ ] **Step 4: Docs**

- **Root README:** the web app is the React SPA at `/` and `/app`; `npm run dev` on port 3000 proxies to 8085; replace CRA script names with the Vite ones; `client/` is the Android app only.
- **`client/README.md`:** Android only.
- **`client/CLAUDE.md`:** remove the wasm build-and-stage section (around lines 55–75) and the note that strings live in composeResources because of wasm. Replace that note with: "strings stay in composeResources; the web copy is src/main/frontend/src/i18n/locales/app.*.ts and is no longer generated".

- [ ] **Step 5: Commit** — "Remove the Compose web client now that React serves /app"

### Task 19: Parity check

- [ ] **Step 1:** Run `cd src/main/frontend && npm ci && npm run typecheck && npm test && npm run build`. Expected: PASS.
- [ ] **Step 2:** Run `./mvnw -B -Dfrontend.skip=true test` and `cd client && ./gradlew assembleDebug testDebugUnitTest`. Expected: PASS.
- [ ] **Step 3:** Walk the manual list from the spec's "Verification before merge" section in the browser pane, against the local backend with seeded data:
  - sign-in and sign-out; an expired access token refreshed silently (set `pm_access_token` to garbage and reload);
  - the `/app/#accounts` bookmark;
  - create, edit and delete for accounts, categories, transactions, transfers, subscriptions and currencies;
  - quick add with `12+3*2`;
  - CSV preview and confirm;
  - the HomeMoney flow up to the external redirect;
  - dashboard and analytics numbers compared with the wasm client from `master` on the same database;
  - language and theme;
  - a 375px viewport.

  Fix what differs and commit each fix separately.
- [ ] **Step 4:** Use superpowers:finishing-a-development-branch.
