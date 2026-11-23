# CI/CD setup — Fitouts (`Fit-Outs-Backend`)

Repository: **owais-sbs/Fit-Outs-Backend**  
Workflow: `.github/workflows/deploy.yml`

## What runs

On push to **`main`** (excluding `**.md` and `docs/**`) or **workflow_dispatch**:

1. Build root `Dockerfile` (Maven / Temurin 21; runtime stage uses JDK image as in repo).
2. Push `905694824089.dkr.ecr.ap-south-2.amazonaws.com/onepath:fitouts-backend-<sha7>`.
3. SSM: `sudo /opt/onepath/apps/deploy.sh fitouts fitouts-backend-<sha7>`.
4. Only **`fitouts`** service restarts.

Concurrency: `deploy-fitouts`.

## Image tags

`fitouts-backend-` + 7-character SHA.

## GitHub secret

`AWS_ROLE_ARN` with trust for `repo:owais-sbs/Fit-Outs-Backend:ref:refs/heads/main`.

## EC2 mapping

| Item | Value |
|---|---|
| Service | `fitouts` |
| Container | `fitouts-backend` |
| Health | `http://127.0.0.1:18084/actuator/health` |

## Manual deploy

Actions → **Build and Deploy** → **Run workflow**.

## Verify

```bash
docker ps --filter name=fitouts-backend
curl -fsS http://127.0.0.1:18084/actuator/health
```

## Build notes

- `.dockerignore` excludes `effective-pom.xml`, `docs/`, secrets.
- No build-args; no secrets in the image build.

## Rollback

Automatic on failed health check; manual tag redeploy supported.

See `CICD_OVERVIEW.md` and `infrastructure/ec2/INSTALL.md`.