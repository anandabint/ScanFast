# ScanFast

Aplikasi Android untuk memindai halaman dokumen dengan kamera. Hasilnya otomatis dipotong rapi, diperjelas warnanya, lalu disimpan sebagai PDF satu halaman per foto di `Documents/ScanFast/AKL {awal}-{akhir}/{nomor}.pdf`.

## Install (tanpa Play Store)

1. Di HP Android, buka link ini lalu unduh file APK:
   **https://github.com/anandabint/ScanFast/raw/main/release/ScanFast.apk**
2. Buka file yang terunduh. Kalau muncul peringatan, izinkan "Install dari sumber ini" untuk browser atau aplikasi File Anda.
3. Buka ScanFast dan izinkan akses kamera.

Syarat: Android 12 atau lebih baru, HP 64-bit (arm64).

Repo harus public, atau teman Anda harus diundang sebagai collaborator dan login GitHub, supaya link bisa diunduh.

## Build dari source

```
./gradlew assembleDebug
```
