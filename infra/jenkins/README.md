# CI/CD – Jenkins

Setiap push ke GitHub (`hudallilmuthaqin/pandawa`) diproses oleh Jenkins lokal sesuai [`Jenkinsfile`](../../Jenkinsfile):

| Stage | Branch | Isi |
|---|---|---|
| Prepare | semua | Tag image = 7 karakter commit SHA |
| Test → Backend | semua | `./mvnw verify` di container `eclipse-temurin:21-jdk` (unit + integration test, PostgreSQL embedded) |
| Test → Frontend | semua | `npm ci`, Vitest, `npm run build` di container `node:22-alpine` (paralel dengan backend) |
| Build images | semua | `pandawa-backend:<sha>` dan `pandawa-frontend:<sha>` |
| Deploy | `main` | `docker compose -p pandawa up -d --wait` (db, backend, frontend) |
| Smoke test | `main` | SPA, `/api/settings/public` lewat Nginx, dan `/actuator/health` harus OK; lalu tag `latest` |

Bila deploy atau smoke test gagal, pipeline otomatis **rollback** ke image yang berjalan sebelumnya.
Hanya 5 image terbaru per aplikasi yang disimpan.

Hasil deploy: **http://localhost:8090** (`APP_PORT` di `~/.config/pandawa/prod.env`; port 8081 di laptop ini dipakai McAfee Agent).
Jenkins: **http://localhost:8080** (job *PB Pandawa*).

## Cara kerja trigger

Jenkins berjalan di laptop sehingga webhook GitHub tidak bisa menjangkaunya. Job multibranch memeriksa repo
**setiap 1 menit** dan otomatis membuild branch yang berubah. Branch selain `main` hanya di-test dan di-build (tanpa deploy).

## File

| File | Fungsi |
|---|---|
| `Dockerfile` | Image Jenkins + Docker CLI/buildx/compose + plugin |
| `plugins.txt` | Daftar plugin Jenkins |
| `casc/jenkins.yaml` | Configuration as Code: job `pandawa` dan credential `pandawa-prod-env` |
| `docker-compose.yml` | Menjalankan Jenkins (pakai volume `jenkins_home` yang sudah ada) |

User, password, dan keamanan Jenkins tetap diatur lewat UI; tidak disimpan di repo.

## Secrets

Variabel stack yang di-deploy (password DB, `JWT_SECRET`, admin pertama) ada di **`~/.config/pandawa/prod.env`**,
di luar repo (repo ini publik). Jenkins memuatnya sebagai credential *Secret file* `pandawa-prod-env`.
Setelah mengubah file tersebut, restart Jenkins agar credential diperbarui:

```bash
docker compose -f infra/jenkins/docker-compose.yml restart
```

Admin aplikasi pertama dibuat dari `ADMIN_BOOTSTRAP_USERNAME`/`ADMIN_BOOTSTRAP_PASSWORD` di file tersebut.
Setelah login dan mengganti password, kosongkan kedua nilai itu.

## Perintah

```bash
# Jalankan / perbarui Jenkins (setelah `colima start`)
docker compose -f infra/jenkins/docker-compose.yml up -d --build

# Status stack yang di-deploy
docker compose -p pandawa ps
docker compose -p pandawa logs -f backend

# Rollback manual ke versi tertentu (lihat tag: docker image ls pandawa-backend)
IMAGE_TAG=<sha> docker compose -p pandawa --env-file ~/.config/pandawa/prod.env up -d --no-build db backend frontend
```

Catatan: Jenkins memakai `/var/run/docker.sock`, artinya Jenkins punya kontrol penuh atas Docker di VM Colima.
Wajar untuk satu mesin lokal, tetapi jangan buka port 8080 ke internet.
