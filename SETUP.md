# Socialvest — Setup & Status Proyek

Dokumen ini menjelaskan kondisi proyek saat ini, cara menjalankan, konfigurasi Firebase,
dan langkah manual yang masih relevan (publish Firestore rules; seeding kursus kini
opsional karena katalog playlist sudah dibundel di aplikasi).

Brief lengkap ada di [`Socialvest_Project_Brief.md`](Socialvest_Project_Brief.md).
Laporan ringkas untuk presentasi/pengumpulan (fitur, arsitektur, screenshot, hasil uji)
ada di [`LAPORAN_UAS.md`](LAPORAN_UAS.md).

---

## 1. Status saat ini

| Fase | Cakupan | Status |
|---|---|---|
| 0 | Scaffold Gradle, tema, bottom navigation 5 tab, layar placeholder | **Selesai** — `assembleDebug` berhasil dan sudah dijalankan di emulator API 37 (light & dark mode), kelima tab berfungsi tanpa crash |
| 1 | Auth (register/login/logout/session) + profil | **Selesai** — Firebase Auth terhubung, validasi form & error mapping berfungsi, profil + edit profil + logout siap diuji |
| 2 | Model Firestore, repository, `firestore.rules`, index | **Selesai** — model `User`, `AuthRepository`, dan `firestore.rules` (users/portfolios/assets/transactions/social/learning) sudah dipublikasikan |
| 3 | Invest: aset, transaksi, kalkulasi portfolio + unit test | **Selesai** — CRUD aset, BUY/SELL, rata-rata & P/L dihitung ulang dari transaksi, 38 unit test hijau (saat fase ini); sudah diuji di emulator (termasuk light/dark mode) |
| 3b | Refresh harga semi-otomatis (di luar brief, disetujui) | **Selesai** — tombol "Refresh semua" mengambil harga kripto/saham/emas dari CoinGecko & Yahoo Finance, dengan label sumber + batasan yang jujur; diuji di emulator untuk 4 aset sekaligus |
| 4 | Social: feed, post, like, komentar | **Selesai** — feed index-free dengan paging, buat/edit post (teks + topik), like anti-duplikat lewat transaksi, komentar + hapus komentar, profil publik, dan hapus post; 45 unit test hijau (saat fase ini) dan seluruh alur diuji end-to-end di emulator |
| 5 | Learn: katalog, detail, player YouTube, progres | **Selesai** — katalog bawaan berisi kursus playlist **Belajar Saham dari Nol!** (36 video, kanal Saham dari Nol) tanpa perlu seed; detail (thumbnail, silabus, tautan playlist), enroll, pemutar embed YouTube, dan progres selesai per modul. Total suite kini **90 unit test hijau**. Seluruh alur diuji di emulator: video diputar (0:01/7:32), modul ditandai selesai (1 dari 36, 2%), dan progres bertahan setelah aplikasi ditutup lalu dibuka lagi |
| 6 | Home dashboard, Settings, disclaimer | **Selesai** — Home menampilkan sapaan + avatar, ringkasan portfolio milik sendiri (nilai, modal, unrealized/realisasi P/L, return), 3 post terbaru, 2 kursus unggulan, dan shortcut ke tab lain; Settings berisi data akun (read-only), ringkasan privasi, disclaimer wajib, kebijakan penghapusan data, versi, dan logout. 90 unit test hijau; light & dark mode diuji di emulator |
| 7 | Uji acceptance criteria + dokumentasi akhir | **Selesai** — semua kriteria dipetakan di bagian 16; kriteria 10–11 kini lulus end-to-end karena modul sudah tersedia di katalog bawaan |
| 8 | Redesign navigasi + tab Cari + prefill aset | **Selesai** — Profile pindah ke drawer kiri, bottom navigation menjadi **Home · Social · Cari · Learn · Portofolio**. Tab Cari memakai katalog 51 simbol dan kutipan on-demand (Yahoo/CoinGecko) berlabel sumber & jam; mengetuk hasil mengisi formulir aset, bukan mengeksekusi transaksi. Diuji di emulator: kutipan tampil, prefill tersimpan, total Portofolio ikut berubah |
| 9 | Akun & keamanan, tema, dokumen legal | **Selesai** — reset kata sandi Firebase (nyata, dengan catatan batas akun demo), tema Sistem/Terang/Gelap persisten lewat `SharedPreferences`, halaman Syarat penggunaan & Kebijakan privasi statis. Suite kini **90 unit test hijau**; seluruh alur diuji di emulator |

Semua tab utama sekarang berisi data nyata. Tab Home hanya membaca data milik akun yang
sedang masuk (portfolio difilter UID) dan menampilkan pratinjau terbatas; daftar lengkap
aset ada di tab **Portofolio**, feed di **Social**, katalog di **Learn**, dan pencarian
simbol di **Cari**. Katalog Learn langsung terisi dari `StarterCatalog` (satu playlist
YouTube = satu kursus), jadi **tidak perlu seed manual**; enrollment dan progres tetap
disimpan di Firestore. Kursus tambahan dari koleksi `courses` tetap didukung dan
digabungkan (bagian 6).

> **Catatan cakupan:** brief menyatakan harga pasar real-time sebagai *out of scope*.
> Fitur "Refresh semua" ditambahkan atas permintaan pengguna sebagai **semi-otomatis**:
> harga diambil sekali saat tombol ditekan, selalu diberi label sumber, dan tidak pernah
> diklaim sebagai harga real-time. Lihat bagian 8.

---

## 2. Lingkungan & versi

| Komponen | Versi |
|---|---|
| Gradle | 9.6.0 (wrapper sudah disertakan) |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin) |
| Kotlin Gradle Plugin | 2.4.21 (dinaikkan via `buildscript` di `build.gradle.kts` root) |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 |
| Java compatibility | 11 |
| Firebase BoM | 35.2.0 |
| Material Components | 1.14.0 |

Catatan penting: AGP 9 **tidak lagi** memakai plugin `org.jetbrains.kotlin.android`.
Kotlin ditangani built-in. Karena Firebase 35.x dikompilasi dengan metadata Kotlin 2.4,
KGP dinaikkan lewat `buildscript` (cara resmi AGP 9 untuk memakai KGP lebih baru).
Jangan menambahkan plugin `kotlin-android` — build akan gagal.

---

## 3. Struktur proyek

```text
UAS/
├── app/
│   ├── google-services.json          # konfigurasi Firebase (sesuai package)
│   └── src/main/
│       ├── java/com/socialvest/app/
│       │   ├── SocialvestApp.kt             # container dependensi manual
│       │   ├── MainActivity.kt
│       │   ├── data/model/ (User, Asset, AssetCategory, PriceSource, Transaction, Portfolio,
│       │   │                 Post, Comment, Course, CourseLevel, CourseModule, Enrollment,
│       │   │                 ModuleProgress)
│       │   ├── data/remote/ (PriceService, PriceJsonParser, HttpTextClient,
│       │   │                  MarketQuoteService)
│       │   ├── data/repository/ (AuthRepository, InvestmentRepository, SocialRepository,
│       │   │                     LearningRepository)
│       │   ├── domain/PortfolioCalculator.kt # rata-rata (average cost), bebas Android
│       │   ├── domain/PortfolioSummary.kt    # total portfolio Home & Invest, bebas Android
│       │   ├── domain/price/ (RefreshPlan, SymbolResolver, PriceMath)  # policy harga, murni
│       │   ├── domain/market/MarketCatalog.kt # katalog simbol Cari + ranking (murni)
│       │   ├── domain/social/SocialFeed.kt   # urutan, paging, validasi teks (murni)
│       │   ├── domain/learning/ (Learning.kt, YoutubeVideo.kt)  # progres & id video, murni
│       │   ├── utils/ (Validators, ErrorMapper, WindowInsets, Money, Quantity, Display, Initials,
│       │   │           RemoteImage, ThemePreference)
│       │   ├── viewmodel/ (Login, Register, Profile, EditProfile,
│       │   │                Invest, AssetForm, AssetDetail, Transaction,
│       │   │                Social, CreatePost, PostDetail, PublicProfile,
│       │   │                Learn, CourseDetail, ModulePlayer,
│       │   │                Home, Settings, Search, AccountSecurity)
│       │   └── ui/
│       │       ├── auth/ (Splash, Login, Register)
│       │       ├── home/ (HomeFragment, HomePostAdapter, HomeCourseAdapter)
│       │       ├── invest/ (InvestFragment, AssetFormActivity, AssetDetailActivity,
│       │       │            TransactionActivity, AssetAdapter, TransactionAdapter)
│       │       ├── search/ (SearchFragment, MarketAdapter)
│       │       ├── social/ (SocialFragment, CreatePostActivity, PostDetailActivity,
│       │       │            PublicProfileActivity, PostAdapter, CommentAdapter)
│       │       ├── learning/ (LearnFragment, CourseDetailActivity, ModuleActivity,
│       │       │               CourseAdapter, ModuleAdapter)
│       │       ├── profile/ (ProfileFragment, EditProfileActivity)
│       │       └── settings/ (SettingsActivity, AccountSecurityActivity, LegalActivity)
│       ├── test/java/com/socialvest/app/
│       │   ├── domain/PortfolioCalculatorTest.kt
│       │   ├── domain/PortfolioSummaryTest.kt
│       │   ├── domain/price/ (PriceMathTest, PriceRefreshPlannerTest)
│       │   ├── domain/market/MarketCatalogTest.kt
│       │   ├── domain/social/SocialFeedTest.kt
│       │   ├── domain/learning/LearningTest.kt
│       │   └── data/remote/PriceJsonParserTest.kt
│       └── res/
│           ├── layout/ (activity_main, activity_splash, activity_login,
│           │            activity_register, activity_edit_profile,
│           │            fragment_profile, nav_header_drawer,
│           │            fragment_home, item_home_post, item_home_course,
│           │            fragment_invest, item_asset, activity_asset_form,
│           │            activity_asset_detail, item_transaction,
│           │            activity_transaction, dialog_price,
│           │            fragment_search, item_market,
│           │            fragment_social, item_post, activity_create_post,
│           │            activity_post_detail, item_comment, activity_public_profile,
│           │            fragment_learn, item_course, activity_course_detail,
│           │            item_module, activity_module,
│           │            activity_settings, activity_account_security, activity_legal)
│           ├── navigation/nav_main.xml
│           ├── menu/ (menu_bottom_nav.xml, menu_drawer.xml)
│           ├── values/ + values-night/ (warna, tema, dimens, style, bools)
│           └── drawable/ (ikon navigasi & drawer, splash, avatar, ic_add, ic_delete,
│                          ic_refresh, ic_like, ic_like_filled, ic_comment, ic_send,
│                          ic_check_circle, ic_play_circle, ic_search, ic_security,
│                          ic_settings, ic_theme, ic_notifications, ic_language,
│                          ic_privacy, ic_document, ic_logout, bg_chip)
├── firestore.rules                   # aturan keamanan (publish manual)
├── gradle/libs.versions.toml         # version catalog
├── docs/screenshots/                 # screenshot untuk laporan
├── LAPORAN_UAS.md                    # laporan ringkas presentasi
└── SETUP.md
```

Alur data: `Activity/Fragment → ViewModel → Repository → Firebase`.

---

## 4. Cara menjalankan

### Android Studio

1. **File → Open** → pilih folder `UAS` (bukan folder `app`).
2. Tunggu Gradle sync selesai. Gunakan **Embedded JDK (JBR)** bawaan Android Studio.
3. Pilih device/emulator (API 24+), lalu **Run**.

### Command line

```powershell
.\gradlew.bat :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Pastikan `local.properties` berisi `sdk.dir` yang benar dan `google-services.json`
berada di folder `app/` (bukan root proyek).

---

## 5. Konfigurasi Firebase

| Item | Nilai |
|---|---|
| Firebase project | `socialvest-uas` |
| Package / applicationId | `com.socialvest.app` |
| Config file | `app/google-services.json` (sudah terpasang) |
| Auth method | Email/Password |
| Firestore | Mode production, lokasi `asia-southeast2` |

Akun demo yang sudah dibuat di Firebase Authentication:

- `demo1@socialvest.test`
- `demo2@socialvest.test`

Akun kedua dipakai untuk menguji like/komentar dari user lain pada skenario demo.

### Firestore rules

File aturan lengkap ada di [`firestore.rules`](firestore.rules) di root proyek.

**WAJIB dipublikasikan sebelum registrasi dan profil dapat dipakai.** Selama database
masih memakai default deny-all, aplikasi akan menampilkan pesan
"Akses ditolak oleh Firestore...".

Cara manual (paling sederhana):

1. Firebase Console → **Firestore Database** → tab **Rules**.
2. Hapus isi default, tempel seluruh isi `firestore.rules`.
3. Klik **Publish**.

Cara CLI (opsional, jika Firebase CLI terpasang):

```powershell
firebase login
firebase use --add        # pilih project socialvest-uas
firebase deploy --only firestore:rules
```

Verifikasi cepat di tab **Rules Playground**:

- `get /users/{uid}` sebagai user lain → **allowed** (profil publik, tanpa email).
- `update /users/{uid}` yang mengubah `role` menjadi `admin` → **denied**.
- `get /assets/{id}` milik user lain → **denied** (dipakai pada pengujian Fase 3).
- `update /assets/{id}` yang menulis `priceSource` di luar `manual/coingecko/yahoo`
  → **denied** (helper `validPriceMetadata`).
- `update /posts/{id}` oleh pemilik yang hanya mengubah `content`/`category` →
  **allowed**; bila ikut mengubah `likeCount`/`commentCount`/`createdAt` → **denied**.
- `delete /posts/{id}` oleh bukan pemilik → **denied**.
- `create /posts/{id}/likes/{uid}` dengan `likeUserId != uid()` → **denied**
  (duplikat juga mustahil karena ID dokumen = UID).
- `get /courses/{id}` yang `isPublished == true` → **allowed** (hanya baca);
  `create/update /courses/{id}` dari klien → **denied** (seed hanya via Console).
- `create /enrollments/{uid}_{courseId}` dengan `userId != uid()` atau ID dokumen
  tidak sesuai → **denied**; dengan `userId == uid()` dan `enrolledAt == request.time`
  → **allowed**.
- `update /progress/{id}` milik pengguna lain → **denied**; `create` progres milik
  sendiri dengan `completed` boolean dan `lastPositionSeconds >= 0` → **allowed**.

> **Catatan:** `firestore.rules` di repo sudah memuat helper `validPriceMetadata()`
> (Fase 3b) dan aturan edit post (Fase 4). Jika versi yang dipublikasikan di Console
> masih lama, publikasikan ulang seluruh file agar validasi tersebut aktif.

Catatan: dokumen `users/{uid}` **tidak menyimpan email**. Email tetap di Firebase
Authentication, dan ditampilkan di layar Profil dari sesi Auth.

---

## 6. Katalog kursus: bawaan + seed opsional

### 6.0 Katalog bawaan (tanpa Firebase Console)

Kursus default dibundel di
`app/src/main/java/com/socialvest/app/domain/learning/StarterCatalog.kt`:

| Item | Nilai |
|---|---|
| Kursus | `Belajar Saham dari Nol!` (`starter-belajar-saham-dari-nol`) |
| Sumber | `https://www.youtube.com/playlist?list=PLkynsxDYXZVyBn3pxYFWMRV5oeOTyjBVc` |
| Kanal | `Saham dari Nol` (`UCswSgqh8pCrDohjvE71KUtw`) |
| Modul | 36 video, `mod-01` … `mod-36`, urut sesuai playlist |
| Level / kategori | `beginner` / `stock` |
| Total durasi | ±9 jam 28 menit |

Katalog ini tampil tanpa seed dan digabungkan dengan kursus Firestore oleh
`LearningRepository.coursesFlow()`. Untuk mengganti/menambah playlist, edit
`StarterCatalog` lalu rilis ulang aplikasi. ID `starter-…` selalu dilayani dari katalog
bawaan; ID lain dibaca dari Firestore.

### 6.1 (Opsional) Dokumen `courses/{courseId}` di Firestore

Hanya diperlukan untuk kursus tambahan di luar katalog bawaan. Buat collection
`courses`, lalu satu dokumen. Field dan **tipe** harus tepat:

| Field | Type | Contoh |
|---|---|---|
| `title` | string | `Investasi untuk Pemula` |
| `description` | string | `Dasar-dasar saham, reksa dana, dan obligasi.` |
| `thumbnailUrl` | string | *(boleh kosong)* |
| `instructorId` | string | `admin` |
| `instructorName` | string | `Tim Socialvest` |
| `category` | string | `stock` |
| `level` | string | `beginner` |
| `isPublished` | **boolean** | `true` (wajib `true`) |
| `sourceUrl` | string | *(opsional)* URL playlist/sumber asli |
| `sortOrder` | **number** | *(opsional)* `0`; kursus bawaan memakai `1`, `2`, … |
| `createdAt` | **timestamp** | pilih tanggal hari ini |

### 6.2 (Opsional) Subkoleksi `courses/{courseId}/modules/{moduleId}`

Buka dokumen course → **+ Start collection** → collection ID `modules` → Document ID
manual (`mod-1`, `mod-2`, ...).

| Field | Type | Contoh |
|---|---|---|
| `title` | string | `Apa Itu Saham?` |
| `youtubeVideoId` | string | 11 karakter ID video YouTube |
| `durationSeconds` | **number** | `600` |
| `order` | **number** | `1` |
| `description` | string | `Pengantar singkat tentang saham.` |

### 6.3 Mengambil `youtubeVideoId`

| URL video | ID |
|---|---|
| `https://www.youtube.com/watch?v=abc123DEF45` | `abc123DEF45` |
| `https://youtu.be/abc123DEF45?t=30` | `abc123DEF45` |
| `https://www.youtube.com/shorts/abc123DEF45` | `abc123DEF45` |
| `https://www.youtube.com/embed/abc123DEF45` | `abc123DEF45` |

Pastikan video **mengizinkan embed** (Share → tersedia opsi **Embed**) dan gunakan
video dari channel yang kontennya boleh diputar. Jangan mengunduh/mengunggah ulang video.

### 6.4 Verifikasi

- Katalog menampilkan kursus bawaan + kursus Firestore yang `isPublished == true`
  (boolean, bukan string `"true"`), tanpa duplikat ID.
- Modul tampil berurutan berdasarkan `order`; kursus bawaan memakai urutan playlist.
- Jika kursus Firestore tidak muncul, periksa tipe `isPublished`. Query tetap
  `isPublished == true` + `limit`, jadi **tidak perlu composite index**.
- Enrollment/progres tidak memerlukan dokumen kursus di Firestore; rules hanya
  memeriksa kepemilikan `userId` dan format ID.

---

## 7. Checklist uji Fase 1

Setelah `firestore.rules` dipublikasikan:

1. **Registrasi**: buat akun baru (mis. `uji1@socialvest.test`). Harus masuk ke
   aplikasi; dokumen `users/{uid}` muncul di Firestore dengan field
   `uid, fullName, username, photoUrl, bio, role, isActive, createdAt`.
2. **Session restore**: tutup aplikasi sepenuhnya, buka lagi → langsung masuk tanpa
   login ulang.
3. **Login salah**: email benar + kata sandi salah → pesan "Email atau kata sandi salah."
4. **Validasi**: submit form kosong → pesan per field; password < 6 karakter; email
   tanpa `@`; username berisi huruf besar/spasi.
5. **Profil**: buka drawer → Profil saya; tampil nama, `@username`, email, bio, inisial
   avatar, dan disclaimer. Bottom navigation tersembunyi selama di layar ini.
6. **Edit profil**: ubah nama/username/bio → simpan → data berubah dan tetap setelah
   aplikasi dibuka ulang.
7. **Logout**: konfirmasi dialog → kembali ke layar Masuk; tekan back tidak bisa
   kembali ke aplikasi.
8. **Rule admin**: coba ubah `role` lewat Rules Playground → harus **denied**.

## 8. Refresh harga semi-otomatis (CoinGecko + Yahoo Finance)

Fitur tambahan di luar brief (disetujui pengguna). Tombol **Refresh semua** di tab Portofolio
mengambil harga sekali jalan untuk aset yang punya sumber publik gratis, lalu menyimpannya
ke `currentPrice` yang sama dengan harga manual — sehingga seluruh perhitungan portfolio
tidak berubah.

### 8.1 Sumber data per kategori

| Kategori | Sumber | Yang diambil | Catatan |
|---|---|---|---|
| Kripto | CoinGecko `simple/price` (IDR) | harga per koin dalam IDR | Satu permintaan untuk semua koin sekaligus |
| Saham | Yahoo Finance `v8/finance/chart` | `regularMarketPrice` | Coba `TICKER.JK` dulu, lalu `TICKER`; simbol yang berhasil disimpan |
| Emas | Yahoo Finance `GC=F` | USD per troy ounce → IDR/gram | **Acuan emas dunia, bukan harga Antam/Logam Mulia** |
| Reksa dana | — | — | Tetap manual: tidak ada sumber gratis yang stabil & sesuai ToS |
| Obligasi | — | — | Tetap manual, alasan sama |
| Lainnya | — | — | Tetap manual |

Saham/emas berdenominasi USD dikonversi memakai kurs `IDR=X` dari Yahoo Finance
(`PriceMath.usdToIdr`, `BigDecimal`, tanpa `Double`). Emas dibagi 31,1034768 gram/oz.

### 8.2 Perilaku

- Konfirmasi dulu (menyebut sumber, potensi delay, dan bahwa reksa dana/obligasi tetap manual).
- Maksimal **10 aset** per sekali refresh; sisanya dilaporkan "melebihi batas".
- Aset tanpa ticker / kategori tak didukung muncul di ringkasan sebagai **tetap manual**,
  bukan dilewati diam-diam.
- Hanya hasil yang berhasil ditulis, dalam satu `WriteBatch`; aset yang gagal tetap memakai
  harga terakhirnya. Ringkasan: `N diperbarui · N gagal · N tetap manual` + alasan per aset.
- Cache 60 detik, jeda 250 ms antar permintaan Yahoo, timeout 8 detik, satu kali retry untuk
  429/5xx, dan User-Agent browser (endpoint Yahoo tidak resmi).
- Menyimpan harga manual (dialog "Perbarui Harga") atau mengubah ticker/kategori akan
  mengembalikan `priceSource` ke `manual` dan mengosongkan `priceSymbol`.

### 8.3 Metadata harga pada dokumen `assets`

| Field | Tipe | Contoh | Arti |
|---|---|---|---|
| `currentPrice` | number | `550000` | Harga per unit, **sen** IDR (sama seperti sebelumnya) |
| `priceSource` | string | `manual` \| `coingecko` \| `yahoo` | Asal harga terakhir |
| `priceSymbol` | string | `BBCA.JK`, `bitcoin`, `GC=F` | Simbol yang benar-benar dipakai |
| `priceUpdatedAt` | timestamp | — | Waktu harga diambil/disimpan |

Dokumen lama tanpa field ini tetap valid (dibaca sebagai `manual`).

### 8.4 Batasan yang harus disebut saat demo

- Yahoo Finance adalah endpoint **tidak resmi** dan bisa membatasi permintaan sewaktu-waktu.
- Harga bisa **tertunda** (delayed), bukan real-time; UI menulis "bukan real-time dan dapat tertunda".
- Emas memakai acuan emas dunia per gram, **bukan** harga Antam/Pegadaian.
- Reksa dana & obligasi tidak punya sumber otomatis, jadi tetap manual.

---

## 9. Social (Fase 4)

### 9.1 Struktur data

| Path | Isi |
|---|---|
| `posts/{postId}` | `userId`, `authorName`, `authorUsername`, `content`, `imageUrl`, `category`, `status`, `likeCount`, `commentCount`, `createdAt` |
| `posts/{postId}/likes/{uid}` | `userId`, `createdAt` (ID dokumen = UID) |
| `posts/{postId}/comments/{commentId}` | `userId`, `authorName`, `authorUsername`, `content`, `createdAt` |

Nama/username penulis disalin ke post saat dibuat supaya feed tidak perlu join dan
perubahan profil tidak menulis ulang riwayat. `status` selalu `published` pada fase ini.

### 9.2 Query & urutan

- Feed: `whereEqualTo("status", "published").limit(100)` **tanpa `orderBy`** agar tidak
  perlu composite index. Urutan terbaru + paging dilakukan di klien
  (`SocialFeed.sortNewestFirst`, 10 per halaman, tombol "Muat lebih banyak").
  Konsekuensi yang dicatat: hanya maksimal 100 post terbaru yang dimuat per sesi.
- Komentar: `orderBy("createdAt", ASCENDING)` (single-field, index otomatis).

### 9.3 Perilaku

- **Like**: ID dokumen like = UID, jadi duplikat mustahil di level database. Counter
  `likeCount` diubah dalam transaksi yang sama dengan dokumen like.
- **Sinkronisasi ikon hati**: feed hanya bisa memantau dokumen post, bukan subkoleksi
  like. Karena itu status like dibaca sekali per post, lalu dibaca ulang saat feed
  kembali tampil (`refreshLikedState`) — mis. setelah like dari layar detail.
- **Komentar**: penambahan/penghapusan mengubah `commentCount` dalam transaksi yang sama.
  Hapus komentar diizinkan untuk penulis komentar **atau** pemilik post.
- **Hapus post**: komentar dihapus lebih dulu (batch 400), lalu dokumen post. Like milik
  pengguna lain tidak bisa dihapus oleh pemilik post di bawah rules saat ini sehingga
  tertinggal sebagai dokumen yatim yang tidak terlihat dari UI (batasan).
- **Edit post**: hanya pemilik; `content`/`category` boleh berubah, `likeCount`,
  `commentCount`, `status`, dan `createdAt` wajib tetap (ditegakkan `firestore.rules`).
- **Profil publik**: hanya membaca `users/{uid}` (nama, username, bio, tanggal bergabung).
  Email dan nilai portfolio tidak pernah ditampilkan.
- **Label opini**: header feed dan helper form menuliskan bahwa semua post adalah opini
  pengguna, bukan nasihat keuangan terverifikasi.
- Lampiran gambar belum disediakan (opsional pada brief).

### 9.4 Checklist uji Fase 4

Dijalankan di emulator dengan akun `demo1` ("Akun Demo"):

1. **Empty state**: feed kosong menampilkan "Belum ada postingan" + tombol Buat Post.
2. **Buat post**: isi 60 karakter → counter `60/2000`, topik Saham; setelah Terbitkan,
   post langsung muncul dengan inisial "AD", nama, `@demo1 · <tanggal, jam>`, chip topik.
3. **Like anti-duplikat**: like 0 → 1, tap lagi 1 → 0; dokumen
   `posts/{id}/likes/{uid}` hanya satu.
4. **Komentar**: kirim komentar → muncul di daftar, counter berubah "1 komentar" di
   layar detail **dan** kartu feed, kolom input kembali kosong.
5. **Hapus komentar**: konfirmasi → "0 komentar" + empty state komentar.
6. **Sinkron ikon hati**: like dari layar detail → kembali ke feed → ikon hati terisi;
   unlike dari feed → 0 dan ikon kembali kosong.
7. **Profil publik**: tap avatar → nama, `@demo1`, bio, "Bergabung 14 Nov 2026", dan
   catatan privasi; tidak ada email/portfolio.
8. **Edit post**: tombol "Edit postingan" (pemilik) membuka form terisi
   (`44/2000`, "Simpan perubahan"), teks diubah → layar detail langsung menampilkan
   versi baru, `createdAt`/counter tetap.
9. **Hapus post**: konfirmasi → layar detail tertutup, feed kembali ke empty state.
10. **Keyboard**: saat komentar diketik, bar input ikut naik di atas keyboard
    (padding IME, lihat bagian 15).
11. **Unit test**: `SocialFeedTest` (7) hijau — total suite 45 test, 0 gagal.
12. **Dark mode**: feed dan layar detail (termasuk tombol Edit/Hapus dan bar komentar)
    tetap terbaca.

Belum diuji manual (disarankan saat demo):

- **Akun kedua**: tombol Edit/Hapus postingan seharusnya tidak tampil untuk post
  pengguna lain (`isOwner`), dan `firestore.rules` tetap menolak update/delete dari
  non-pemilik. Perlu akun kedua untuk membuktikan tampilan dan penolakannya.
- **Hapus komentar oleh pemilik post** (bukan penulis komentar): aturan mengizinkan,
  tetapi belum dicoba dengan dua akun.

---

## 10. Learning Center (Fase 5)

### 10.1 Struktur data

| Path | Isi |
|---|---|
| `StarterCatalog.kt` (kode) | Katalog bawaan: 1 kursus playlist + 36 modul; tidak perlu seed |
| `courses/{courseId}` | `title`, `description`, `thumbnailUrl`, `instructorId`, `instructorName`, `category`, `level`, `isPublished`, `sourceUrl`, `sortOrder`, `createdAt` |
| `courses/{courseId}/modules/{moduleId}` | `title`, `description`, `youtubeVideoId`, `videoUrl`, `durationSeconds`, `order` |
| `enrollments/{uid}_{courseId}` | `userId`, `courseId`, `status` (`in_progress`/`completed`), `enrolledAt` |
| `progress/{uid}_{courseId}_{moduleId}` | `userId`, `courseId`, `moduleId`, `completed`, `lastPositionSeconds`, `updatedAt` |

`category` memakai id `AssetCategory` (`stock`, `crypto`, …) sehingga labelnya sama dengan
tab Portofolio. `level` dipetakan ke `CourseLevel` (`beginner`/`intermediate`/`advanced`);
nilai tak dikenal tidak ditampilkan. Koleksi `courses`/`modules` hanya dibaca aplikasi
(seed opsional, lihat bagian 6); `enrollments` dan `progress` hanya boleh ditulis pemiliknya.
Kursus bawaan tidak meninggalkan jejak di Firestore: hanya enrollment/progres pengguna
yang disimpan.

### 10.2 Query & index

- Katalog: `whereEqualTo("isPublished", true).limit(50)` **tanpa `orderBy`**, lalu
  `StarterCatalog.mergeCourses()` menambahkan kursus bawaan (ID yang sama tidak
  diduplikasi) dan urutan dihitung di klien (`Learning.sortNewestFirst`: kursus bertanggal
  lebih dulu, lalu `sortOrder`, lalu ID). Tidak perlu composite index.
- Modul: ID `starter-…` dilayani dari `StarterCatalog`; kursus Firestore memakai
  `orderBy("order", ASCENDING)` (single-field, index otomatis).
- Enrollment/progres per kursus: query `whereEqualTo("userId", uid)` (+ `courseId`)
  dengan `limit`, **bukan** `get()` pada dokumen yang mungkin belum ada — dokumen yang
  belum ada akan gagal `resource.data.userId == uid()` dan menghasilkan `PERMISSION_DENIED`.
- Penulisan enrollment/progres memakai `set(..., SetOptions.merge())` dengan ID dokumen
  deterministik, jadi tidak pernah membaca dokumen yang belum ada.

### 10.3 Perilaku

- **Sumber katalog**: tab Learn menampilkan banner "36 video dari kanal Saham dari Nol"
  + tombol **Buka playlist**. Detail kursus juga menampilkan tautan playlist dan atribusi
  bahwa video diputar dari YouTube tanpa diunduh. Katalog bawaan tidak memerlukan
  koneksi ke `courses`, tetapi enrollment/progres tetap butuh login Firebase.
- **Enroll**: tombol "Mulai kursus" membuat `enrollments/{uid}_{courseId}` berstatus
  `in_progress`; setelah itu tombol menjadi nonaktif bertuliskan "Kursus diikuti".
  Menandai modul selesai juga otomatis membuat enrollment bila belum ada.
- **Progres**: status disimpan per modul (`completed: true/false`) dan dapat dibatalkan.
  Persentase = jumlah modul selesai / total modul, dihitung di `domain/learning/Learning.kt`
  (dibulatkan ke bawah, tidak pernah membagi nol). Status enrollment berubah menjadi
  `completed` hanya ketika semua modul selesai.
- **Player**: video diputar sebagai embed resmi YouTube di dalam WebView. URL memakai
  `origin` + `widget_referrer` dan header `Referer`/`Origin` `https://socialvest.app`;
  tanpa identitas itu YouTube menampilkan error 152/153. ID diambil dari
  `youtubeVideoId`, atau diekstrak dari `videoUrl` (watch/youtu.be/shorts/embed/live).
  Ada tombol "Buka di YouTube" sebagai fallback. Aplikasi tidak mengunduh/mengunggah
  ulang video.
- **Thumbnail**: opsional. Diambil sekali dari `thumbnailUrl` lewat HTTP dengan batas
  ukuran/timeout (`utils/RemoteImage.kt`); bila kosong/gagal, ditampilkan placeholder
  inisial judul — tidak pernah gambar rusak atau area kosong.
- **State jujur**: tanpa kursus terbit, tab Learn menampilkan empty state (bukan kartu
  palsu). Kursus/modul yang hilang atau tidak terbit menampilkan pesan, bukan layar
  kosong. Error koneksi menampilkan pesan + tombol "Coba lagi".

### 10.4 Checklist uji Fase 5

Dijalankan di emulator `Pixel_7a` dengan akun `demo1`, **tanpa seed Firestore** (koleksi
`courses` kosong; katalog berasal dari `StarterCatalog`):

1. **Katalog**: tab Learn menampilkan banner "36 video dari kanal Saham dari Nol" +
   tombol **Buka playlist**, lalu "1 kursus" dan kartu `Belajar Saham dari Nol!` dengan
   meta "36 modul · oleh Saham dari Nol · Pemula · Saham". Status awal "Belum dimulai".
2. **Detail**: thumbnail video pertama tampil, deskripsi, catatan sumber + tombol
   **Buka playlist di YouTube**, progres "0 dari 36 modul selesai · 0% selesai",
   tombol "Mulai kursus", dan daftar modul 1–36 lengkap dengan durasi.
3. **Enroll**: "Mulai kursus" → tombol menjadi "Kursus diikuti" (nonaktif); kartu Learn
   menampilkan chip `Diikuti`. Enrollment tersimpan di Firestore dan bertahan setelah
   aplikasi ditutup.
4. **Player**: modul 1 menampilkan thumbnail embed YouTube; setelah play, indikator
   berjalan `0:01 / 7:32` dan kontrol volume/CC/pengaturan tampil. Tombol
   **Buka di YouTube** membuka video yang sama di aplikasi YouTube.
5. **Progres**: "Tandai selesai" → tombol berubah menjadi "Batalkan selesai", modul
   mendapat centang, detail menampilkan "1 dari 36 modul selesai · 2% selesai", dan
   kartu Learn menampilkan "1 modul selesai". Setelah `force-stop` + buka ulang,
   enrollment dan progres tetap sama (dibaca dari Firestore).
6. **Embed bermasalah**: bila suatu video menonaktifkan embed, YouTube menampilkan
   pesan error di dalam WebView; tombol **Buka di YouTube** tetap menjadi fallback yang
   jujur (aplikasi tidak memaksa unduh).
7. **Dark mode**: katalog, detail, daftar modul, dan banner sumber tetap terbaca.
8. **Unit test**: `StarterCatalogTest` (10) + `LearningTest` (12) hijau; total suite
   **90 test, 0 gagal**. `assembleDebug` juga berhasil.

Catatan: pada emulator, frame video sempat tampak hitam saat decoder masih dimuat;
setelah beberapa detik thumbnail/kontrol muncul dan pemutaran berjalan. Ini bukan error
aplikasi, melainkan keterbatasan codec emulator.

---

## 11. Home dashboard & Settings (Fase 6)

### 11.1 Isi Home

Home hanya membaca data akun yang sedang masuk dan menampilkan **pratinjau**; daftar
lengkap tetap ada di tab masing-masing.

| Bagian | Sumber data | Catatan |
|---|---|---|
| Sapaan + avatar | `users/{uid}` (snapshot) + sesi Auth | Inisial dari nama/username; email hanya terlihat oleh pemilik akun |
| Ringkasan portfolio | `assets` milik UID (`assetsFlow()`) | Nilai, total modal, unrealized P/L, realisasi P/L, dan return dihitung oleh `PortfolioSummary.of()` - rumus yang sama dengan tab Portofolio |
| Post terbaru | `posts` `status == published`, `limit(3)` | Urutan terbaru di klien (`SocialFeed.sortNewestFirst`); kartu menampilkan penulis, waktu, isi (maks. 3 baris), jumlah suka/komentar |
| Kursus unggulan | `courses` `isPublished == true`, `limit(2)` | Urutan terbaru di klien (`Learning.sortNewestFirst`) |
| Shortcut | Bottom navigation | "Invest/Social/Learn" dan "Lihat semua" memilih item `bottom_nav` — tidak membuka activity baru |

- Kartu portfolio selalu menyertakan catatan bahwa nilainya memakai **harga tersimpan
  terakhir** (manual atau hasil "Refresh semua"), bukan harga pasar real-time.
- Tanpa aset, kartu menampilkan "Belum ada aset tercatat…" + tombol "Buka Portofolio"
  (bukan Rp0 yang menyesatkan).
- Postingan di Home adalah opini pengguna; label disclaimer komunitas tampil di bawah
  daftar. Semua angka (rupiah, persen, tanggal) diformat lewat `Display`.
- Error koneksi menampilkan pesan + "Coba lagi" yang berlangganan ulang ke snapshot
  listener (`HomeViewModel.retry()`), karena flow yang sudah `catch` berhenti permanen.

### 11.2 Settings & privacy

Dibuka dari drawer → **Pengaturan** atau **Privasi** (`SettingsActivity`).

- **Akun (read-only)**: nama, username, email. Email berasal dari sesi Firebase Auth dan
  tidak pernah disimpan di `users/{uid}`; penjelasan ini tampil di layar.
- **Privasi**: nilai portfolio/aset/transaksi hanya untuk pemilik; nama/username/bio
  terlihat di profil publik; post/komentar dapat dibaca pengguna lain; progres belajar
  privat.
- **Catatan penting**: menampilkan `disclaimer_finance` persis seperti brief bagian 10.
- **Data & akun**: menjelaskan bahwa aplikasi **belum** punya tombol hapus akun; akun
  dan seluruh data Firestore dihapus manual oleh pengembang atas permintaan. Tidak ada
  klaim bahwa penghapusan otomatis sudah tersedia.
- **Versi** aplikasi dibaca dari `PackageManager` (bukan angka hardcode) dan **logout**
  memakai dialog konfirmasi yang sama dengan layar Profil.

### 11.3 Query & index

- `postsFlow(limit)` dan `coursesFlow(limit)` sekarang menerima batas; Home memakai 3
  dan 2 dokumen. Filter tetap hanya equality (`status`/`isPublished`), jadi **tidak ada
  composite index baru**.
- Portfolio memakai `assetsFlow()` yang sudah difilter `userId == uid`; tidak ada
  pembacaan dokumen milik pengguna lain dan tidak ada nilai portfolio yang ditulis ke
  dokumen publik.
- Tidak ada penulisan data dari Home/Settings selain `signOut()`.

### 11.4 Checklist uji Fase 6

Dijalankan di emulator dengan akun `demo1` (aset BBCA: 100 unit, harga tersimpan
Rp5.500):

1. **Home light mode**: sapaan "Halo, Akun Demo", `@demo1 · demo1@socialvest.test`,
   avatar "AD"; kartu portfolio menampilkan Rp550.000, modal Rp2.133.333,33,
   unrealized −Rp1.583.333,33, realisasi +Rp233.333,33, return −74,22%, dan catatan
   harga tersimpan terakhir. Angka identik dengan tab Portofolio.
2. **Pratinjau post**: post demo tampil dengan penulis, waktu, isi, dan "0 suka ·
   0 komentar"; tap membuka layar Postingan yang sama dengan feed.
3. **Pratinjau kursus**: "Belajar Saham dari Nol!" tampil dengan thumbnail video pertama
   dan meta 36 modul/instruktur/level/kategori; tap membuka Detail kursus.
4. **Shortcut**: "Portofolio", "Social", "Learn" memilih tab yang sesuai pada bottom
   navigation; "Lihat semua" pada post membuka tab Social dan pada kursus membuka tab
   Learn.
5. **Settings light mode**: data akun benar, empat catatan privasi tampil, disclaimer
   wajib tampil utuh, kebijakan penghapusan data menjelaskan keterbatasannya, versi
   "0.1.0", dan tombol "Keluar" memakai warna error (dialog konfirmasi tidak dieksekusi
   agar sesi demo tetap aktif).
6. **Dark mode**: Home dan Settings (kartu akun/privasi, kartu disclaimer, tombol
   outlined, chip avatar) tetap terbaca; tidak ada crash di logcat.
7. **Unit test**: `PortfolioSummaryTest` (5) hijau — total suite 62 test, 0 gagal.

Belum dijalankan:

8. **Portfolio kosong di Home**: memerlukan akun tanpa aset; kredensial `demo1`/`demo2`
   tidak dicatat di repo dan sengaja tidak logout agar data demo tidak terkunci. Jalur
   ini ditutup unit test `PortfolioSummary.of(emptyList())` dan render yang sama
   menampilkan pesan + tombol "Buka Portofolio".
9. **Hapus akun**: tidak tersedia di aplikasi (lihat bagian 11.2 dan batasan).

---

## 12. Checklist uji Fase 3 (Portofolio)

Data uji yang dipakai saat verifikasi di emulator (akun demo yang sedang login):
aset **Bank Central Asia / BBCA**, harga manual Rp27.000, transaksi
100 unit @ Rp20.000 → 50 unit @ Rp24.000 → jual 50 unit @ Rp26.000.
Setelah pengujian, data demo dikembalikan ke kondisi ini.

Sudah dijalankan dan lolos:

1. **Empty state**: akun tanpa aset menampilkan "Belum ada aset" + tombol Tambah Aset
   (bukan angka Rp0 palsu).
2. **Tambah aset + pembelian awal**: BBCA, kategori Saham, harga manual Rp25.000,
   pembelian awal 100 unit @ Rp20.000 → ringkasan Rp2.500.000 / modal Rp2.000.000 /
   +Rp500.000 / Return 25,00%.
3. **Detail aset**: harga manual, 100 unit, nilai Rp2.500.000, rata-rata Rp20.000,
   realisasi Rp0, unrealized +Rp500.000.
4. **BUY**: 50 unit @ Rp24.000 → preview rata-rata Rp21.333,33, tersimpan 150 unit,
   modal Rp3.200.000, unrealized +Rp550.000.
5. **SELL**: 50 unit @ Rp26.000 → preview realisasi +Rp233.333,33, tersimpan 100 unit,
   rata-rata tetap Rp21.333,33, realisasi +Rp233.333,33, unrealized +Rp366.666,67.
6. **Perbarui harga manual**: dialog terisi `25.000` (hanya satu "Rp", dari
   `prefixText`), ubah ke `27000` → nilai Rp2.700.000, unrealized +Rp566.666,67,
   Return 26,56%.
7. **Validasi jual melebihi kepemilikan**: isi 999 unit saat dimiliki 100 unit →
   pesan inline "Jumlah jual melebihi kepemilikan (100 unit)." dan tidak ada data
   yang tersimpan.
8. **Hapus transaksi**: hapus baris JUAL → agregat dihitung ulang (150 unit, modal
   Rp3.200.000, realisasi Rp0, unrealized +Rp850.000, Return 26,56%). Tambah lagi
   penjualan yang sama → kembali ke kondisi semula.
9. **Hapus aset**: konfirmasi → aset + seluruh transaksinya hilang, layar detail
   tertutup sendiri dengan toast "Aset dan riwayat transaksinya dihapus.", daftar
   aset kembali benar.
10. **Unit test**: `.\gradlew.bat :app:testDebugUnitTest` → 90 test hijau
    (rata-rata, fee, jual sebagian/semua, oversell, urutan tanggal, parsing
    `10.000` vs `10.5`, format rupiah, resolusi simbol, konversi USD→IDR & emas/gram,
    parser CoinGecko/Yahoo, ringkasan portfolio, urutan feed sosial, progres belajar,
    katalog playlist bawaan, serta katalog Cari dan perencana pengambilan kutipan Cari).
11. **Dark mode**: tab Portofolio dan detail aset tetap terbaca (teks, chip, warna P/L).
12. **Edit aset**: ubah nama dari halaman detail (mis. "Bank Central Asia Tbk") →
    tersimpan, angka kepemilikan tidak berubah, lalu dikembalikan ke nama semula.

### Checklist refresh harga (Fase 3b)

Sudah dijalankan di emulator dengan 5 aset sementara (AAPL, BBCA, BTC, Emas, RDPU),
lalu aset sementara dihapus kembali:

1. **Konfirmasi**: tombol "Refresh semua" membuka dialog yang menyebut CoinGecko/Yahoo,
   keterbatasan data, dan bahwa reksa dana/obligasi tetap manual.
2. **Ringkasan benar**: `4 diperbarui · 0 gagal · 1 tetap manual`, dengan alasan
   "kategori ini belum punya sumber gratis" untuk RDPU.
3. **Kripto**: BTC memperbarui harga lewat CoinGecko (mis. Rp1.225.129.502) dan detail
   menampilkan "Harga otomatis (bukan real-time)" + "Harga bitcoin via CoinGecko, …".
4. **Saham IDX**: BBCA tetap benar (Rp5.500 dari `BBCA.JK`), daftar menampilkan
   "BBCA · Saham · Yahoo Finance".
5. **Saham AS**: AAPL (USD) dikonversi ke IDR (mis. Rp6.480.551,30) memakai kurs `IDR=X`.
6. **Emas**: detail menampilkan harga per gram hasil konversi `GC=F` **dan** catatan
   "bukan harga Antam/Logam Mulia".
7. **Harga manual menimpa sumber**: simpan harga manual → label berubah menjadi
   "Harga manual" dan `priceSource` kembali `manual`; refresh berikutnya bisa
   mengembalikannya ke otomatis.
8. **Unit test**: `PriceJsonParserTest` (6), `PriceMathTest` (6),
   `PriceRefreshPlannerTest` (8) semuanya hijau.

Belum diuji manual (disarankan saat demo):

- **Form invalid**: harga kosong/tidak valid, jumlah unit `0`, fee negatif → pesan
  per field, tidak ada data tersimpan.
- **Jual semua unit**: setelah semua unit terjual, nilai dan rata-rata harus
  tampil `—` (bukan Rp0 yang menyesatkan).

---

## 13. Navigasi baru, Cari, akun, tema, dan legal (Fase 8–9)

### 13.1 Struktur navigasi

- `MainActivity` memakai `DrawerLayout` + `MaterialToolbar` + `NavigationView`.
- Bottom navigation berisi lima tujuan: **Home · Social · Cari · Learn · Portofolio**
  (`menu_bottom_nav.xml`). Tab tengah adalah Cari.
- **Portofolio** memakai ulang seluruh layar Invest lama (ringkasan, daftar aset,
  "Refresh semua", FAB, detail, riwayat). Nama kelas/layout internal tetap `Invest*`
  agar riwayat Fase 3 tidak perlu ditulis ulang; hanya id navigasi dan label yang berubah.
- **Profile** tetap ada di `nav_main.xml` tetapi tidak lagi ada di bottom navigation.
  Profil dibuka dari drawer dan diperlakukan sebagai layar detail: bottom bar
  disembunyikan, ikon toolbar menjadi panah kembali, dan drawer dikunci.
- Drawer berisi header akun (inisial, nama, username, email) dan item: Profil saya,
  Akun & keamanan, Pengaturan, Tema aplikasi, Notifikasi, Bahasa, Privasi, Syarat
  penggunaan, Kebijakan privasi, Keluar.
- Notifikasi dan Bahasa sengaja hanya menampilkan penjelasan (belum ada push
  notification; aplikasi hanya berbahasa Indonesia), bukan toggle palsu.
- "Privasi" membuka layar Pengaturan & privasi yang sudah ada; "Syarat penggunaan" dan
  "Kebijakan privasi" membuka `LegalActivity`.
- `applySystemBarInsets()` dipanggil pada `content_root`, bukan pada `DrawerLayout`
  (DrawerLayout tidak menghormati padding pada dirinya sendiri). Panel drawer memakai
  `applyDrawerInsets()` agar header tidak tertutup status bar.

### 13.2 Tab Cari (katalog + kutipan on-demand)

| Aspek | Perilaku |
|---|---|
| Sumber daftar | `MarketCatalog` statis: 26 saham IDX + 25 kripto = 51 simbol |
| Pencarian | Tidak peka huruf besar/kecil, ticker eksak diprioritaskan, filter Semua/Saham/Kripto |
| Kutipan saham | Yahoo Finance `TICKER.JK`; satu penekanan mencakup **semua saham terlihat**, dikirim bertahap 8 simbol dengan jeda 500 ms antar batch |
| Kutipan kripto | CoinGecko `simple/price?vs_currencies=idr`, satu permintaan untuk semua koin terlihat |
| Kapan diambil | Sekali saat tab pertama kali dibuka (semua simbol terlihat), lalu hanya saat "Perbarui harga" ditekan |
| Urutan target | Simbol yang gagal/belum punya harga diminta lebih dulu, baru yang sudah berharga, tanpa duplikat |
| Label | Setiap harga disertai sumber (`Yahoo Finance`/`CoinGecko`) dan jam pengambilan |
| Kegagalan | Baris tetap kosong dengan "Harga tidak tersedia"; ringkasan menyebut jumlah gagal dan sisa yang belum diambil |
| Ketuk hasil | Membuka `AssetFormActivity` dengan nama, ticker, kategori, dan harga terisi; pengguna tetap harus menyimpan sendiri |
| Cache | Kutipan saham & kripto di-cache 120 detik di `MarketQuoteService` dan dipakai bersama antar tab; tombol manual memaksa data baru (`forceRefresh`) |

Katalog kripto harus tetap sinkron dengan `SymbolResolver.cryptoId()`; `MarketCatalogTest`
memverifikasi pemetaan itu agar simbol di Cari selalu bisa di-refresh di Portofolio.

Pemilihan target dan pembagian batch adalah fungsi murni `QuoteFetchPlanner`, diuji oleh
`QuoteFetchPlannerTest` (10 kasus: urutan retry-first, ukuran batch, tanpa duplikat,
cakupan seluruh katalog, filter kategori, daftar kosong). Dengan begitu urutan permintaan
bisa diuji tanpa jaringan atau Android.

### 13.3 Akun & keamanan

- Layar baru `AccountSecurityActivity` menampilkan nama, username, email (read-only).
- Tombol "Kirim email reset kata sandi" memanggil `FirebaseAuth.sendPasswordResetEmail`
  untuk email sesi aktif. Tidak ada kata sandi yang dibaca/disimpan aplikasi.
- Hasil nyata ditampilkan: sukses, gagal (dengan pesan Firebase), atau email tidak
  tersedia. Tidak ada pesan sukses palsu.
- **Batasan akun demo:** `demo1@socialvest.test` memakai domain contoh, sehingga email
  reset tidak akan benar-benar sampai. UI menampilkan catatan ini secara eksplisit.
- Penghapusan akun tetap manual oleh pengembang (belum ada alur Auth + Firestore +
  subkoleksi yang aman).

### 13.4 Tema aplikasi

- `AppTheme` (Sistem/Terang/Gelap) disimpan di `SharedPreferences`
  (`ThemePreference`) dan diterapkan lewat `AppCompatDelegate`.
- Nilai dibaca saat `Application.onCreate`, sehingga tema berlaku sebelum layar pertama.
- Dialog tema memakai `MaterialAlertDialogBuilder` dengan pilihan tunggal; memilih tema
  langsung membuat ulang activity tanpa kehilangan sesi.
- Tema bukan data akun, jadi tidak disimpan di Firestore.

### 13.5 Dokumen legal

- `LegalActivity` menampilkan dua dokumen statis: Syarat penggunaan dan Kebijakan privasi.
- Keduanya memuat catatan eksplisit bahwa dokumen disusun untuk tugas akademik, bukan
  nasihat hukum.
- Isi mencakup batasan harga (bukan real-time), sifat konten komunitas, data yang
  dikumpulkan, visibilitas data, pihak ketiga (Firebase, Yahoo, CoinGecko), dan
  kebijakan penghapusan data secara manual.

### 13.6 Checklist uji Fase 8–9

- [x] Toolbar, judul, dan hamburger terlihat; konten tidak tertutup status bar.
- [x] Bottom navigation berada di atas gesture bar, label tidak terpotong.
- [x] Drawer terbuka dari kiri; header menampilkan profil demo; semua item membuka
      layar/dialog yang benar.
- [x] Profil dari drawer: bottom bar hilang, panah kembali mengembalikan bottom bar.
- [x] Cari: kutipan otomatis terisi untuk semua simbol terlihat dengan label sumber + jam;
      filter dan pencarian bekerja; "Perbarui harga" manual mengambil ulang semua simbol
      (jam berubah) dan pengambilan bertahap 8 saham dijelaskan di layar.
- [x] Ketuk hasil Cari → formulir terisi → simpan → aset muncul di Portofolio dan total
      ringkasan berubah.
- [x] Akun & keamanan: data akun benar; dialog konfirmasi reset; hasil sukses/gagal nyata.
- [x] Tema Terang/Gelap/Sistem diterapkan dan bertahan setelah aplikasi dibuka ulang.
- [x] Syarat penggunaan & Kebijakan privasi terbuka dari drawer dan dapat ditutup.

---

## 14. Known limitations

- Home menampilkan pratinjau terbatas (3 post, 2 kursus) dan ringkasan portfolio milik
  sendiri; tidak ada notifikasi push atau personalisasi rekomendasi.
- Profile tidak lagi menjadi tab; profil dibuka lewat drawer dan menyembunyikan bottom
  navigation. Jumlah tab utama tetap lima.
- Settings bersifat read-only. **Reset kata sandi sudah nyata** lewat Firebase
  (`AccountSecurityActivity`), tetapi **hapus akun masih manual** oleh pengembang
  (Auth + Firestore + subkoleksi belum diotomasi). Akun demo memakai domain
  `socialvest.test`, sehingga email reset tidak akan sampai.
- Katalog Cari bersifat **statis** (51 simbol pilihan) dan tidak memuat seluruh bursa.
  Kutipan saham diambil bertahap **8 simbol per batch** (jeda 500 ms) agar tetap ramah
  ke Yahoo Finance; satu penekanan tetap mencakup semua saham terlihat. Kutipan kripto
  memakai satu permintaan batch.
- Kutipan Cari bukan real-time dan tidak diperbarui otomatis setelah pemuatan pertama.
  Tab Cari hanya mengisi formulir; transaksi tetap dibuat manual oleh pengguna.
- Notifikasi belum tersedia (tidak ada FCM) dan aplikasi hanya berbahasa Indonesia;
  kedua item drawer menampilkan penjelasan, bukan toggle palsu.
- Social belum punya lampiran gambar, follow/unfollow, laporan konten, atau moderasi
  (semuanya opsional pada brief kecuali edit post, yang sudah ada).
- Like milik pengguna lain tertinggal sebagai dokumen yatim saat post dihapus, karena
  pemilik post tidak boleh menghapus like orang lain di bawah rules saat ini.
- Feed memuat maksimal 100 post terbaru per sesi (query index-free) dan mengurutkan
  di klien; belum ada pencarian/filter topik.
- Belum ada upload foto profil (`photoUrl` masih string kosong, tanpa Firebase Storage).
- Username belum dicek unik (baru validasi format). Pengecekan unik memerlukan
  query/index dan akan dicatat sebagai batasan.
- Katalog Learn berasal dari **kode aplikasi** (`StarterCatalog`: satu playlist = satu
  kursus) dan digabung dengan kursus Firestore. Menambah playlist berarti mengubah kode
  dan merilis ulang aplikasi, atau menambahkan dokumen `courses` lewat Console; aplikasi
  tidak punya admin UI.
- Video milik pihak ketiga (kanal **Saham dari Nol**). Kanal dapat menghapus video,
  membatasi embed, atau menonaktifkan playlist kapan saja; aplikasi hanya menautkan dan
  memutar dari YouTube, tidak menyimpan salinan.
- Progres belajar hanya menyimpan **status selesai** per modul
  (`lastPositionSeconds` selalu 0), sesuai pilihan "completed state and/or last playback
  position" pada brief. Resume otomatis dari posisi tontonan belum didukung karena
  posisi video YouTube di dalam embed tidak dilacak.
- Video diputar lewat WebView embed `youtube.com` dengan origin/referrer aplikasi; tidak
  ada unduhan, penyimpanan offline, atau pemutar kustom. Video yang menonaktifkan embed
  tidak dapat diputar di aplikasi (tombol "Buka di YouTube" tetap tersedia).
- Thumbnail kursus diambil langsung lewat HTTP (tanpa library gambar); bila URL
  kosong/gagal, placeholder inisial ditampilkan. Gambar tidak di-cache antar sesi.
- Katalog Learn memuat maksimal 50 kursus per sesi dan progres maksimal 500 baris
  (batas query yang jauh di atas kebutuhan demo).
- Harga di aplikasi adalah **harga tersimpan terakhir** (input manual atau hasil
  "Refresh semua"), bukan feed real-time. Semua layar memberi label sumber dan
  peringatan "bukan real-time".
- Refresh otomatis hanya untuk kripto, saham, dan emas (acuan dunia). Reksa dana dan
  obligasi tetap manual karena tidak ada sumber gratis yang stabil; Yahoo Finance
  bersifat tidak resmi sehingga bisa membatasi permintaan.
- Transaksi belum bisa diedit; koreksi dilakukan dengan menghapus transaksi (agregat
  otomatis dihitung ulang) lalu mencatat ulang.
- Satu portfolio per akun (dibuat otomatis, id = uid). Belum ada multi-portfolio,
  multi-currency, atau grafik performa (grafik di luar cakupan brief).
- Tanggal transaksi disimpan per hari (tanpa jam); urutan transaksi di hari yang sama
  memakai waktu pembuatan dokumen.

---

## 15. Catatan pengembangan

- Jangan menambahkan plugin `org.jetbrains.kotlin.android`.
- Jangan menaruh `google-services.json` di root proyek; harus di `app/`.
- `local.properties` tidak boleh di-commit (sudah ada di `.gitignore`).
- Harga di aplikasi adalah **data tersimpan terakhir**, bukan feed real-time. Jangan
  pernah menampilkan angka tanpa label sumber.

### Konvensi data Portofolio (kelas `Invest*`)

- Uang disimpan sebagai `Long` **sen** (1/100 rupiah), jumlah unit sebagai `Long`
  dikali 1.000.000 (agar `0,5` unit tetap eksak). Perkalian selalu lewat
  `BigInteger`/`BigDecimal` — jangan pakai `Double`.
- Parsing menerima format Indonesia **dan** keyboard: `10.000` = sepuluh ribu,
  `10.5` = sepuluh koma lima (titik dengan 1–2 digit dianggap desimal).
- `transactions` adalah sumber kebenaran. Dokumen `assets` hanya cache agregat
  (`quantity`, `totalCost`, `averageBuyPrice`, `realizedPl`) dan ditulis dalam satu
  `WriteBatch` bersama transaksinya, jadi P/L tidak mungkin berbeda dari riwayat.
- Query selalu `whereEqualTo("userId", uid)` (± `assetId`) tanpa `orderBy`; urutan
  dilakukan di klien agar tidak perlu composite index.
- `ensurePortfolio` memakai query, bukan `get()` pada id tetap: aturan Firestore
  tidak bisa mengevaluasi `resource.data.userId == uid()` untuk dokumen yang belum ada.
- Membaca dokumen yang sudah dihapus menghasilkan `PERMISSION_DENIED` (bukan snapshot
  kosong). `AssetDetailViewModel` memperlakukan kode itu sebagai "aset sudah hilang"
  agar layar detail tertutup, bukan menampilkan angka basi.
- `currentPrice` selalu dalam sen IDR per unit, baik manual maupun hasil refresh.
  Asal harga dicatat di `priceSource` / `priceSymbol` / `priceUpdatedAt`; `firestore.rules`
  memvalidasi ketiganya lewat helper `validPriceMetadata()` (field opsional, jadi dokumen
  lama tetap diterima). Publikasikan ulang rules agar validasi ini aktif.

### Konvensi Social

- Query feed **tanpa `orderBy`** (hanya equality pada `status`) supaya proyek tidak
  membutuhkan composite index yang harus dibuat manual sebelum aplikasi bisa dipakai.
  Urutan, paging, dan validasi panjang teks ada di `domain/social/SocialFeed.kt`
  (murni, ada unit test).
- Like memakai UID sebagai ID dokumen dan selalu mengubah counter di transaksi yang
  sama; jangan pernah menaikkan counter dengan `FieldValue.increment` terpisah, karena
  angka di kartu bisa berbeda dari jumlah dokumen like.
- Status like di feed dibaca sekali per post (`probedLikeIds`) dan dibaca ulang saat
  `onResume` (`refreshLikedState`). Kalau menambah aksi like di layar lain, pastikan
  feed me-refresh juga.
- Edit post memakai layar yang sama dengan create (`CreatePostActivity` +
  `EXTRA_POST_ID`), dan `firestore.rules` hanya mengizinkan `content`/`category` berubah.
- Jangan menampilkan email atau nilai portfolio di profil publik; dokumen `users/{uid}`
  memang tidak menyimpan email.

### Konvensi Learning

- Koleksi `courses`/`modules` bersifat **read-only dari aplikasi**; kursus tambahan
  hanya lewat Firebase Console (bagian 6). Katalog utama ada di `StarterCatalog` sebagai
  data ter-bundle — jangan menambahkan tombol admin di aplikasi.
- ID dokumen enrollment (`uid_courseId`) dan progres (`uid_courseId_moduleId`) dibuat
  deterministik dan ditegakkan `firestore.rules`; jangan membaca dokumen itu dengan
  `get()` sebelum ada — gunakan query `whereEqualTo("userId", uid)` seperti repository.
- Persentase progres hanya boleh dihitung lewat `Learning.progressPercent` dan jumlah
  modul selesai lewat `Learning.countCompleted` (ada unit test); jangan membagi manual
  agar tidak terjadi pembagian nol.
- Hanya status selesai yang disimpan (`lastPositionSeconds = 0`). Jika kelak ingin
  resume posisi tontonan, perlu integrasi YouTube IFrame API untuk membaca `getCurrentTime`.
- Semua video wajib berasal dari YouTube dan diputar lewat embed resmi
  (`YoutubeVideo.embedUrl`). URL embed **wajib** menyertakan `origin`/`widget_referrer`
  dan pemanggilan `loadUrl` **wajib** mengirim header `Referer`/`Origin` non-YouTube
  (`YoutubeVideo.EMBED_ORIGIN`); tanpa itu YouTube menampilkan error 152/153.
  Jangan mengunduh, menyimpan, atau mengunggah ulang materi berhak cipta.
- Atribusi kanal/playlist harus tetap terlihat di tab Learn dan detail kursus. Jangan
  menyalin deskripsi/referral dari video; tulis ringkasan netral sendiri.

### Konvensi Home & Settings

- Home **tidak boleh** menjadi sumber angka kedua. Semua total portfolio memakai
  `PortfolioSummary.of(assets)`, yang juga dipakai `InvestViewModel`, sehingga Home dan
  Invest tidak mungkin berbeda. Jangan menjumlah ulang di Fragment/adapter.
- Home hanya membaca `assetsFlow()` milik UID sendiri dan memakai batas kecil untuk
  post (`limit(3)`) dan kursus (`limit(2)`). Jangan menambahkan query publik yang
  membocorkan nilai portfolio.
- Shortcut Home memindahkan tab lewat `BottomNavigationView.selectedItemId`
  (`R.id.bottom_nav`), bukan `startActivity`, agar tidak ada dua NavHost.
- Pratinjau post/kursus sengaja read-only; aksi like/komentar dan enroll tetap di layar
  detailnya. Jangan menambah like langsung dari Home tanpa menyamakan state feed.
- Settings read-only dan menampilkan `disclaimer_finance` yang sama dengan Profile.
  Jangan menambahkan tombol "Hapus akun" palsu sebelum alur penghapusan benar-benar
  diimplementasikan (Auth + Firestore + subkoleksi).
- Versi aplikasi dibaca dari `PackageManager`; jangan hardcode nomor versi di layout.

### Konvensi Navigasi, Cari, dan tema

- Bottom navigation hanya berisi lima tujuan; jangan menambahkan Profile kembali sebagai
  tab. Profil adalah layar detail yang dibuka dari drawer.
- Jangan memberi padding pada `DrawerLayout`; padding tidak memengaruhi child. Panggil
  `applySystemBarInsets()` pada `content_root` dan `applyDrawerInsets()` pada
  `NavigationView`.
- Harga di Cari adalah **kutipan sesekali**, bukan feed. Jangan menambahkan refresh
  otomatis berulang atau menyembunyikan label sumber/jam.
- Katalog Cari harus tetap sinkron dengan `SymbolResolver.cryptoId()` (dijaga unit test).
  Menambah simbol baru berarti menambah pasangan ticker ↔ id CoinGecko.
- Mengetuk hasil Cari tidak boleh membuat transaksi. Selalu buka formulir aset dan biarkan
  pengguna menyimpan sendiri.
- `QuoteFetchPlanner.STOCK_BATCH_SIZE` (8) hanya menentukan ukuran burst permintaan saham,
  bukan batas jumlah simbol yang bisa dihargai. Jangan menaikkan ukuran batch tanpa
  mengukur dampaknya ke sumber gratis.
- Tema disimpan lokal lewat `ThemePreference`, bukan Firestore. Jangan menerapkan tema
  hanya di satu activity.
- Dokumen legal adalah teks statis; perbarui `strings.xml` bila isi kebijakan berubah.

### Keyboard & edge-to-edge

Aplikasi berjalan edge-to-edge (targetSdk 36), sehingga `adjustResize` **tidak** lagi
mengecilkan jendela. `applySystemBarInsets()` karena itu memberi padding bawah sebesar
`max(systemBars.bottom, ime.bottom)`. Tanpa ini, bar komentar di layar detail post
tertutup keyboard. Layar baru yang punya input di bagian bawah wajib memanggil helper
yang sama pada root layout. Khusus layar utama, helper dipasang pada `content_root`
(bukan `DrawerLayout`) dan panel drawer memakai `applyDrawerInsets()`.

---

## 16. Peta acceptance criteria (brief bagian 11)

Status per akhir Fase 9. "Lulus" berarti sudah dijalankan di emulator; "Lulus (kode +
test)" berarti jalur kode lengkap dan/atau unit test hijau. Semua kriteria Learn (9–11)
kini diuji end-to-end memakai katalog bawaan tanpa seed manual.

| # | Kriteria | Status | Bukti / catatan |
|---|---|---|---|
| 1 | Register, login, logout | Lulus | Diuji pada Fase 1 (validasi, error mapping, sesi bertahan, logout + dialog konfirmasi) |
| 2 | CRUD aset milik sendiri | Lulus | Fase 3: tambah, edit, detail, hapus (beserta transaksi); akun lain tidak melihat aset |
| 3 | BUY/SELL dan holdings konsisten | Lulus | Fase 3: rata-rata & P/L dihitung ulang dari transaksi; oversell ditolak |
| 4 | Nilai portfolio & P/L benar | Lulus | `PortfolioCalculatorTest` (18) + `PortfolioSummaryTest` (5); angka Home = Portofolio |
| 5 | Tidak bisa membaca/mengubah data privat pengguna lain | Lulus (rules) | `firestore.rules` dibatasi `userId == uid()`; skenario allow/deny diuji di Rules Playground (bagian 5) |
| 6 | Menerbitkan post | Lulus | Fase 4: create/edit/hapus post, label opini pengguna |
| 7 | Like/unlike tanpa duplikat | Lulus | Fase 4: ID dokumen = UID, counter di transaksi yang sama |
| 8 | Menambah & melihat komentar | Lulus | Fase 4: komentar + hapus komentar, sinkron real-time |
| 9 | Katalog & detail kursus | Lulus | Fase 5: `limit(50)` + urutan klien, detail dengan silabus/thumbnail |
| 10 | Memutar video kursus resmi | Lulus | Modul 1 playlist "Belajar Saham dari Nol!" diputar di embed YouTube (0:01 / 7:32) dengan kontrol lengkap; "Buka di YouTube" membuka video yang sama |
| 11 | Progres belajar tersimpan & terbaca | Lulus | "Tandai selesai" → 1 dari 36 (2%), centang modul, dan kartu Learn "1 modul selesai"; tetap sama setelah `force-stop` + buka ulang (Firestore) |
| 12 | Bottom navigation membuka modul yang benar | Lulus | Fase 8: lima tab **Home · Social · Cari · Learn · Portofolio**; Profile lewat drawer, shortcut Home, "Lihat semua" |
| 13 | Loading, empty, validation, error state | Lulus | Diuji di Portofolio/Cari/Social/Learn/Home/Settings (spinner, empty state jujur, pesan + "Coba lagi") |
| 14 | Tidak crash pada alur demo utama | Lulus | Logcat bersih setelah Fase 4-6; tidak ada `FATAL EXCEPTION` |

### Alur demo yang disarankan

1. Login `demo1@socialvest.test`.
2. Home: tunjukkan sapaan, ringkasan portfolio (beri catatan "harga tersimpan terakhir"),
   pratinjau post & kursus, lalu shortcut ke tab lain.
3. Portofolio: buka aset BBCA, tunjukkan detail transaksi dan P/L; opsional "Refresh semua"
   dengan label sumber.
4. Cari: tunjukkan katalog dan label sumber/jam, ketik `BBCA`, ketuk hasilnya untuk
   memperlihatkan formulir terisi, lalu simpan dan kembali ke Portofolio untuk melihat
   total berubah.
5. Social: buat post singkat, like/unlike, buka detail dan tambah komentar (akun kedua
   opsional; like/unlike sudah membuktikan anti-duplikat).
6. Learn: buka katalog playlist, tunjukkan 36 modul dan tautan sumber, enroll, putar
   modul 1, tandai selesai, lalu buka ulang aplikasi untuk membuktikan progres tetap
   tersimpan (tidak perlu seed apa pun).
7. Drawer: buka menu, tunjukkan header akun, buka **Profil saya**, lalu **Akun &
   keamanan** (data read-only + catatan reset kata sandi), **Tema aplikasi** (ganti ke
   Gelap), **Notifikasi/Bahasa** (penjelasan jujur), dan **Syarat penggunaan**.
8. Tutup dengan disclaimer: aplikasi edukasi, bukan nasihat keuangan, harga bukan
   real-time.

### Sisa pekerjaan teknis (di luar cakupan demo)

- Menambah playlist/kursus baru: edit `StarterCatalog` (rilis aplikasi) atau buat dokumen
  `courses` + `modules` di Console. Belum ada admin UI maupun importer playlist otomatis.
- Publikasikan ulang `firestore.rules` versi terbaru di Firebase Console bila Console
  masih memakai versi lama (lihat catatan bagian 5).
- Hapus akun masih manual (tidak ada tombol di aplikasi); lihat batasan bagian 14.
