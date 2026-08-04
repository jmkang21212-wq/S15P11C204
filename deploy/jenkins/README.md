# LAIVE Jenkins

Jenkins is isolated from the application Compose projects and is exposed only
on the host loopback interface at `127.0.0.1:9090`.

```sh
printf 'CI_POSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 24)" > .env
chmod 600 .env
docker compose up -d --build
```

The host Docker socket is deliberately not mounted. Production deployment uses
a restricted SSH account whose key can only run
`/usr/local/sbin/laive-deploy`; Jenkins never receives unrestricted shell,
sudo, or Docker daemon access.

The PostgreSQL and Redis services in this Compose project are isolated CI test
dependencies. They publish no host ports and use `tmpfs`, so Pipeline tests do
not access or mutate the production databases.

The custom Jenkins image includes Java 21, Python 3.12+ and `uv`. The uv cache
is stored under `JENKINS_HOME` so repeated AI test runs reuse downloaded
packages.

`laive-ci` polls and tests the `dev` branch. `laive-cd` polls `master`, runs the
same test stages, and then runs `Deploy Production`. The host deployment script
refuses to run over a dirty production worktree and rolls back to the previous
revision when a replacement container fails its health check.
