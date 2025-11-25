# alur kerja kamera app 📸

oke, jadi gini cara kerja codingan kamera yang baru kita benerin. biar jelas kenapa bagian izin dan simpan foto agak panjang, ini alurnya:

---

## 1. masalah perizinan (permission)

android makin baru makin ketat soal privasi user, jadi kita harus cek versi hp dulu.

- **android 9 ke bawah**  
  kita wajib minta izin `WRITE_EXTERNAL_STORAGE` kalau mau simpen foto ke galeri. kalo gak dikasih, foto gak bakal kesimpen.

- **android 10 - 12**  
  udah pake scoped storage. kita bisa simpen foto hasil jepretan aplikasi sendiri tanpa minta izin WRITE.

- **android 13+**  
  izin baca diganti jadi lebih spesifik: `READ_MEDIA_IMAGES`. kita cuma butuh ini buat nampilin thumbnail foto terakhir.

intinya, di kode kita cek versi hp (`Build.VERSION.SDK_INT`) dulu, baru tentuin izin mana yang diminta.

---

## 2. mediastore (simpan ke galeri)

kita gak bisa lagi asal taruh file di folder tertentu. sekarang harus lewat **MediaStore** supaya foto kelihatan di galeri dan aman kalo aplikasi dihapus.

- **siapin content**  
  bikin `ContentValues` buat data foto: nama, format, folder tujuan (misal `Pictures/KameraKu`).

- **daftarin ke sistem**  
  pakai `ContentResolver` supaya sistem nyediain tempat buat file foto.

- **jepret & tulis**  
  CameraX ambil gambar terus simpen ke URI yang udah didaftarin. hasilnya bakal kelihatan di galeri.

---

## 3. masalah rotasi (biar gak miring)

sering preview tegak tapi pas difoto hasilnya miring. solusinya:

- ambil rotasi layar dari `view.display.rotation`.
- set `setTargetRotation(rotation)` pas bikin `ImageCapture` dan `Preview`.

hasilnya foto sesuai posisi hp saat ditekan tombol.

---

## kesimpulan

kode ini aman dipakai di berbagai versi android. hp lama (android 9) bisa jalan, hp baru (android 14) juga aman.
