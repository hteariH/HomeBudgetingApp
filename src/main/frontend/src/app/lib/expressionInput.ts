const OPERATORS = '+-*/−×÷';
const SEPARATORS = '.,';

/**
 * Keeps only what Calculator can read, so a paste or an autocompleting keyboard cannot
 * leave the amount field permanently invalid with nothing on screen saying why.
 *
 * Both separators are kept as typed: ru/uk keyboards emit a comma, and Calculator folds it
 * onto a dot, so rewriting it would only make the field disagree with the key pressed.
 * A second separator within one number is dropped - just the separator, not what follows.
 */
export function sanitizeExpression(typed: string): string {
    let out = '';
    let separatorInNumber = false;
    for (const ch of typed) {
        if (ch >= '0' && ch <= '9') {
            out += ch;
        } else if (SEPARATORS.includes(ch)) {
            if (!separatorInNumber) {
                separatorInNumber = true;
                out += ch;
            }
        } else if (OPERATORS.includes(ch)) {
            // An operator ends the number, so the next one may have its own separator.
            separatorInNumber = false;
            out += ch;
        }
    }
    return out;
}
