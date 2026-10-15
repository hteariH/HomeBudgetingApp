/** The first letters of a member's names, upper-cased, at most two: "Anna Ivanova" is "AI". */
export function initialsOf(firstName?: string, lastName?: string): string {
    return [firstName, lastName]
        .map(name => name?.trim().charAt(0).toUpperCase() ?? '')
        .join('');
}
