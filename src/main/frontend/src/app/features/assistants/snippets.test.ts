import { expect, it } from 'vitest';
import { claudeCodeCommand, mcpJson } from './snippets';

const url = 'https://money.example.com/mcp';
const token = 'pm_AbCdEf0123456789AbCdEf0123456789AbCdEf01';

it('puts the URL and the token into the Claude Code command exactly', () => {
    expect(claudeCodeCommand(url, token)).toBe(
        `claude mcp add --transport http personalmoney ${url} --header "Authorization: Bearer ${token}"`,
    );
});

it('puts the URL and the token into the Claude Desktop / Cursor JSON exactly', () => {
    expect(JSON.parse(mcpJson(url, token))).toEqual({
        mcpServers: { personalmoney: { type: 'http', url, headers: { Authorization: `Bearer ${token}` } } },
    });
});
