export const REPO = 'https://github.com/hteariH/HomeBudgetingApp';
export const RELEASES = `${REPO}/releases/latest`;

export const COMPOSE_STEPS = [
    'mkdir personalmoney && cd personalmoney',
    `curl -fsSLO ${REPO}/releases/latest/download/docker-compose.yml`,
    `curl -fsSL ${REPO}/releases/latest/download/env.example -o .env`,
    '# set DB_PASSWORD in .env (e.g. openssl rand -base64 24)',
    'docker compose up -d',
];

export const UPDATE_STEPS = ['docker compose pull', 'docker compose up -d'];

export const BACKUP_STEPS = [
    'docker compose exec db pg_dump -U personalmoney personalmoney > personalmoney-$(date +%F).sql',
    '# and copy ./data (it holds the generated secrets)',
];

export const RESET_PASSWORD = 'docker compose exec app personalmoney reset-password you@example.com';

/** One entry per platform tab; `steps` is how many `install.<id>.<n>` sentences it has. */
export const PLATFORMS = [
    { id: 'compose', steps: 4 },
    { id: 'synology', steps: 4 },
    { id: 'unraid', steps: 3 },
    { id: 'truenas', steps: 3 },
    { id: 'portainer', steps: 3 },
] as const;
