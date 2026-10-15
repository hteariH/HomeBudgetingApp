# Security policy

## Reporting a vulnerability

Please report vulnerabilities privately. On the repository page, open the **Security** tab and
choose **Report a vulnerability** (a private GitHub security advisory). Do not open a public
issue or pull request for a security problem.

Include what you found, how to reproduce it, and which version you tested. We will acknowledge
the report, keep you informed while we work on a fix, and credit you in the release notes if
you want that.

## Supported versions

Only the latest release receives security fixes. Please update (`docker compose pull &&
docker compose up -d`) before reporting, to check the problem still exists.

## Scope

In scope:

- the server (authentication, authorisation, the API, stored secrets and bank credentials);
- the web app served at `/app`;
- the Android app;
- the published container image (`ghcr.io/htearih/personalmoney`) and the `docker-compose.yml`
  shipped with each release.

Out of scope: problems in how a particular instance is operated (weak passwords, an exposed
port, a missing reverse proxy), vulnerabilities in Docker, Postgres or Android themselves, and
the project page at personalmoney.app, which is static and collects nothing.
