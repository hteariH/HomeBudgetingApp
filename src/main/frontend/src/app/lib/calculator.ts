import Decimal from 'decimal.js';
import { roundTo } from './money';

type Op = '+' | '-' | '*' | '/';
type Token = { num: Decimal } | { op: Op };

const isOp = (t: Token): t is { op: Op } => 'op' in t;
const isDigit = (ch: string) => ch >= '0' && ch <= '9';

/** Normalises the operators a keyboard or a paste can produce, then splits into tokens. */
function tokenize(expr: string): Token[] | null {
    const s = expr
        .replace(/×/g, '*')
        .replace(/÷/g, '/')
        .replace(/−/g, '-')
        .replace(/,/g, '.')
        .replace(/ /g, '');
    if (s === '') return [];

    const tokens: Token[] = [];
    let i = 0;
    let prevWasOp = true;
    while (i < s.length) {
        const ch = s[i];
        if (isDigit(ch) || ch === '.') {
            let j = i;
            let dot = false;
            while (j < s.length && (isDigit(s[j]) || s[j] === '.')) {
                if (s[j] === '.') {
                    if (dot) return null;
                    dot = true;
                }
                j++;
            }
            const text = s.slice(i, j);
            if (text === '.') return null;
            // BigDecimal.parseString accepts "12." and ".5"; decimal.js wants digits on both
            // sides, so pad them rather than reject what the Kotlin client accepted.
            const padded = (text.startsWith('.') ? `0${text}` : text).replace(/\.$/, '');
            tokens.push({ num: new Decimal(padded) });
            i = j;
            prevWasOp = false;
        } else if (ch === '+' || ch === '-' || ch === '*' || ch === '/') {
            if (prevWasOp && ch === '-') tokens.push({ num: new Decimal(0) });
            else if (prevWasOp) return null;
            tokens.push({ op: ch });
            prevWasOp = true;
            i++;
        } else {
            return null;
        }
    }
    // A trailing operator is what a half-typed expression looks like - drop it.
    if (tokens.length > 0 && isOp(tokens[tokens.length - 1])) tokens.pop();
    return tokens.length > 0 ? tokens : null;
}

function applyMulDiv(tokens: Token[]): Token[] | null {
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

function applyAddSub(tokens: Token[]): Decimal | null {
    const first = tokens[0];
    if (!first || isOp(first)) return null;
    let acc = first.num;
    for (let i = 1; i < tokens.length; i += 2) {
        const op = tokens[i];
        const right = tokens[i + 1];
        if (!isOp(op) || !right || isOp(right)) return null;
        if (op.op === '+') acc = acc.plus(right.num);
        else if (op.op === '-') acc = acc.minus(right.num);
        else return null;
    }
    return acc;
}

/**
 * Evaluates amount-field expressions: digits, one decimal separator per number, and
 * + − × ÷ with the usual precedence. A port of Calculator.kt. Null on anything it cannot
 * read, or on division by zero - the field then simply shows no result.
 */
export const Calculator = {
    evaluate(expression: string, decimals: number): Decimal | null {
        const tokens = tokenize(expression);
        if (!tokens || tokens.length === 0) return null;
        const firstPass = applyMulDiv(tokens);
        if (!firstPass) return null;
        const result = applyAddSub(firstPass);
        return result === null ? null : roundTo(result, decimals);
    },

    /** True if the expression has an operator other than a leading unary minus. */
    hasOperator(expression: string): boolean {
        let t = expression.replace(/ /g, '');
        if (t.startsWith('-') || t.startsWith('−')) t = t.slice(1);
        return /[+\-*/×÷−]/.test(t);
    },
};
