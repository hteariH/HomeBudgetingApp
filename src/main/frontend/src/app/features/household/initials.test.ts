import { expect, it } from 'vitest';
import { initialsOf } from './initials';

it('takes the first letters of both names, upper-cased', () => {
    expect(initialsOf('Anna', 'Ivanova')).toBe('AI');
    expect(initialsOf('anna', 'ivanova')).toBe('AI');
});

it('copes with a missing or blank name', () => {
    expect(initialsOf('anna', '')).toBe('A');
    expect(initialsOf('', 'Ivanova')).toBe('I');
    expect(initialsOf('  ', '  ')).toBe('');
    expect(initialsOf(undefined, undefined)).toBe('');
});

it('never gives more than two letters', () => {
    expect(initialsOf('Anna Maria', 'Ivanova Petrova')).toBe('AI');
});
