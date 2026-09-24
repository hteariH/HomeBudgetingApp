import { describe, expect, it } from 'vitest';
import { Calculator } from './calculator';

// Ported case by case from CalculatorTest.kt.
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
    it('accepts a number with a trailing or leading separator', () => {
        expect(ev('12.')).toBe('12.00');
        expect(ev('.5')).toBe('0.50');
    });
});
