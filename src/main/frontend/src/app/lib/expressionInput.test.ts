import { expect, it } from 'vitest';
import { sanitizeExpression as s } from './expressionInput';

// Ported case by case from ExpressionInputTest.kt.
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
