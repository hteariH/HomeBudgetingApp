/** What a person pastes into their assistant to connect it, with the address and token filled in. */

export const claudeCodeCommand = (url: string, token: string): string =>
    `claude mcp add --transport http personalmoney ${url} --header "Authorization: Bearer ${token}"`;

/** The `mcpServers` entry Claude Desktop and Cursor read. */
export const mcpJson = (url: string, token: string): string =>
    JSON.stringify(
        { mcpServers: { personalmoney: { type: 'http', url, headers: { Authorization: `Bearer ${token}` } } } },
        null,
        2,
    );
