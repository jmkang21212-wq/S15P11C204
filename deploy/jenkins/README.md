# LAIVE Jenkins

Jenkins is isolated from the application Compose projects and is exposed only
on the host loopback interface at `127.0.0.1:9090`.

```sh
printf 'CI_POSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 24)" > .env
chmod 600 .env
docker compose up -d --build
```

The host Docker socket is deliberately not mounted. Deployment access should
be added later through a restricted mechanism instead of granting Jenkins full
control of the production Docker daemon.

The PostgreSQL and Redis services in this Compose project are isolated CI test
dependencies. They publish no host ports and use `tmpfs`, so Pipeline tests do
not access or mutate the production databases.

The custom Jenkins image includes Java 21, Python 3.12+ and `uv`. The uv cache
is stored under `JENKINS_HOME` so repeated AI test runs reuse downloaded
packages.
