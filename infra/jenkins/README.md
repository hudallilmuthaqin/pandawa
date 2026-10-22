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

## Notifikasi (email + WhatsApp)

Pipeline mengirim pesan lewat email (SMTP) dan WhatsApp ([Fonnte](https://fonnte.com)) saat:

| Kejadian | Isi pesan |
|---|---|
| `main` berhasil di-deploy | ✅ versi, commit, durasi, link aplikasi & log |
| `main` gagal | ❌ gagal sebelum deploy (versi lama tetap jalan), atau deploy gagal + hasil rollback |
| Branch lain gagal tes/build | ❌ nama branch, commit, link log |

Pengaturannya ada di **`~/.config/pandawa/notify.env`** (di luar repo), dimuat sebagai credential `pandawa-notify-env`:

```bash
cp infra/jenkins/notify.env.example ~/.config/pandawa/notify.env   # sekali saja; lalu isi nilainya
docker compose -f infra/jenkins/docker-compose.yml up -d            # muat ulang credential
```

- **Email (Gmail):** aktifkan Verifikasi 2 Langkah, buat *App Password* di https://myaccount.google.com/apppasswords,
  isi `SMTP_USER`/`MAIL_FROM` dengan alamat Gmail dan `SMTP_PASSWORD` dengan App Password (boleh dengan spasi).
- **WhatsApp (Fonnte):** daftar di fonnte.com, tambahkan device dan scan QR dengan HP pengirim, salin token device ke
  `FONNTE_TOKEN`. `WA_TARGET` berisi nomor (`0812…`) dan/atau ID grup (`…@g.us`), dipisah koma.
- Jaringan yang memblokir port 465/587 (mis. firewall kantor): pakai `SMTP_URL=smtp://smtp.gmail.com:25`.
  Koneksi tetap terenkripsi karena `notify.sh` mewajibkan STARTTLS.
- Channel yang nilainya kosong dilewati. Notifikasi yang gagal terkirim hanya dicatat di log build, tidak menggagalkan build.
- Uji tanpa build: `docker exec -e NOTIFY_ENV=/run/secrets/pandawa-notify.env -e NOTIFY_SUBJECT="Tes" -e NOTIFY_BODY="Halo" jenkins bash -s < infra/jenkins/notify.sh`

## Cara kerja trigger

Jenkins berjalan di laptop sehingga webhook GitHub tidak bisa menjangkaunya. Job multibranch memeriksa repo
**setiap 1 menit** dan otomatis membuild branch yang berubah. Branch selain `main` hanya di-test dan di-build (tanpa deploy).

## File

| File | Fungsi |
|---|---|
| `Dockerfile` | Image Jenkins + Docker CLI/buildx/compose + plugin |
| `plugins.txt` | Daftar plugin Jenkins |
| `casc/jenkins.yaml` | Configuration as Code: job `pandawa`, credential `pandawa-prod-env` dan `pandawa-notify-env` |
| `notify.sh`, `notify.env.example` | Pengirim notifikasi email/WhatsApp dan contoh pengaturannya |
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

# Perbarui base image (pipeline tidak memakai --pull karena Docker Hub lambat dari jaringan ini);
# jalankan sesekali, misalnya sebulan sekali, lalu push commit apa saja agar image dibuild ulang
for i in eclipse-temurin:21-jdk eclipse-temurin:21-jre node:22-alpine nginx:1.27-alpine postgres:16-alpine; do docker pull $i; done

# Rollback manual ke versi tertentu (lihat tag: docker image ls pandawa-backend)
IMAGE_TAG=<sha> docker compose -p pandawa --env-file ~/.config/pandawa/prod.env up -d --no-build db backend frontend
```

Catatan: Jenkins memakai `/var/run/docker.sock`, artinya Jenkins punya kontrol penuh atas Docker di VM Colima.
Wajar untuk satu mesin lokal, tetapi jangan buka port 8080 ke internet.
