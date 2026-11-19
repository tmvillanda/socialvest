package com.socialvest.app.domain.learning

import com.socialvest.app.data.model.AssetCategory
import com.socialvest.app.data.model.Course
import com.socialvest.app.data.model.CourseLevel
import com.socialvest.app.data.model.CourseModule

/**
 * Bundled YouTube course catalogue.
 *
 * The project has no admin UI and courses are read-only from the client, so the
 * default catalogue lives in code instead of requiring a manual Firestore seed.
 * Each entry is one public YouTube playlist; the app only embeds/links the
 * videos and never downloads or re-uploads them.
 *
 * Enrollments and progress still live in Firestore, so the bundled course is
 * fully enrollable and its completion state survives reinstalls.
 */
object StarterCatalog {

    const val PLAYLIST_ID = "PLkynsxDYXZVyBn3pxYFWMRV5oeOTyjBVc"
    const val PLAYLIST_URL = "https://www.youtube.com/playlist?list=$PLAYLIST_ID"
    const val CHANNEL_ID = "UCswSgqh8pCrDohjvE71KUtw"
    const val CHANNEL_NAME = "Saham dari Nol"
    const val COURSE_ID = "starter-belajar-saham-dari-nol"

    private val modules: List<CourseModule> = listOf(
        module(1, "uGzToPCX8nU", "Apa itu Saham? | Ilustrasi Untuk Pemula", 453),
        module(2, "XQwUFRKLevg", "Bagaimana Cara Beli Saham? | Ilustrasi", 492),
        module(3, "ir625-6bSSI", "Perhatikan Ini Sebelum Belajar Saham!", 358),
        module(4, "gZtNJh2pq6w", "Saham dari Nol | Mengapa kanal ini dibuat?", 211),
        module(5, "N4L2e7vr6OM", "Analisa Fundamental Saham | Mengapa Penting?", 925),
        module(6, "DnxEFMbGHBI", "Perbandingan Sekuritas | Mulai dari 100 ribu!", 447),
        module(7, "Ttmz5fnOpBY", "Beli Saham dengan Modal 1 Juta!", 354),
        module(8, "KogejuJk6lY", "Darimana Asal Keuntungan Investasi Saham? | (Untuk Pemula)", 423),
        module(9, "xsQXz_glug4", "Apa itu Dividen? | Dividend Yield | Dividend Payout Ratio", 570),
        module(10, "VQdcQd53bZQ", "Cara Nabung Saham dengan Gaji UMR", 1112),
        module(11, "NiaXDTyn9Q0", "Apa itu Gross Margin?", 646),
        module(12, "gerGZN0W7PM", "Cara Prediksi Harga Saham Bank BRI!", 735),
        module(13, "3Ur-FrXdvjo", "Kenapa Saham Unilever Turun Terus? (Part 1)", 1246),
        module(14, "7cUCKpbW-JU", "Dimana Tempat Beli Saham?", 955),
        module(15, "XyERhBghzio", "Apa itu Stock Split Saham?", 813),
        module(16, "SVgKDk77BzQ", "Cara Beli Saham Pakai Aplikasi Stockbit!", 1040),
        module(17, "uMjzuBwoIRM", "Cara Memahami Jual dan Beli Saham di Aplikasi", 1264),
        module(18, "3c3GNgrYsTY", "Beli Saham atau Dana Darurat dulu ya?", 1075),
        module(19, "f5cVSTYswMk", "Apa itu Reksa Dana Saham?", 1031),
        module(20, "vyVXWCKeuOY", "Sektor Saham Paling Aman Untuk Pemula", 1316),
        module(21, "TlOn2z1zcLs", "Ini Dia 12 Saham 'Terbaik' di Indonesia!", 2095),
        module(22, "YFRtIxISOqw", "Kenapa Nabung Saham Bank BCA Cocok Untuk Pemula?", 2080),
        module(23, "2z-kRLZmmro", "20 Saham Penghasil Uang di Indonesia!", 1479),
        module(24, "Bb5Y33o-3JM", "Apa itu Resesi? (Panduan Untuk Pemula)", 1391),
        module(25, "BdiLlLZUXUA", "Strategi Beli Saham Paling Sederhana (Dollar-Cost Averaging)", 1157),
        module(26, "iwDkh5GUnUA", "Cara Analisa Fundamental Saham Sido Muncul (SIDO)", 1328),
        module(27, "_RUqdQRhOdo", "Beli Alfamart-nya vs Beli Saham Alfamart | Untungan yang mana?", 1106),
        module(28, "Y0z7jDCaWP0", "Cara Dapat 20jt Per Bulan Tanpa Kerja", 962),
        module(29, "ey9PvBPxR4Q", "Apa itu Saham Nyangkut?", 902),
        module(30, "oU40sOr3alg", "Kenapa Sih Kita Harus Berinvestasi?", 878),
        module(31, "RIaMaw1fIfQ", "Investasi Saham vs Obligasi | Mana yang lebih menguntungkan?", 1129),
        module(32, "C_d7ITwIUKk", "3 Risiko Terbesar Berinvestasi Saham", 757),
        module(33, "2tKCvFhpTRA", "Apakah Sudah TELAT Untuk Investasi Saham?", 754),
        module(34, "7NOLlEDLTuY", "Cara Dapat Untung +100% dari Saham Bank di Indonesia!", 1029),
        module(35, "MJQDb9U9XY4", "Nabung Emas vs Nabung Saham Bank BCA (BBCA) | 25 tahun terakhir", 799),
        module(36, "26zVgDh7-x0", "Mau Investasi Saham, Mulai Dari Mana?", 799)
    )

    val courses: List<Course> = listOf(
        Course(
            id = COURSE_ID,
            title = "Belajar Saham dari Nol!",
            description = "Seri 36 video dari kanal Saham dari Nol: mengenal saham, " +
                "membeli saham pertama, menganalisis perusahaan, dan mengelola risiko. " +
                "Video diputar langsung dari YouTube.",
            thumbnailUrl = thumbnailUrl("uGzToPCX8nU"),
            instructorId = CHANNEL_ID,
            instructorName = CHANNEL_NAME,
            category = AssetCategory.STOCK,
            level = CourseLevel.BEGINNER,
            isPublished = true,
            sourceUrl = PLAYLIST_URL,
            sortOrder = 1
        )
    )

    private val modulesByCourse: Map<String, List<CourseModule>> =
        mapOf(COURSE_ID to modules)

    fun course(courseId: String): Course? = courses.firstOrNull { it.id == courseId }

    /** `null` means "not bundled"; an empty list would mean a course without lessons. */
    fun modules(courseId: String): List<CourseModule>? = modulesByCourse[courseId]

    fun module(courseId: String, moduleId: String): CourseModule? =
        modules(courseId)?.firstOrNull { it.id == moduleId }

    /**
     * Remote (Firestore) courses stay authoritative when ids collide; bundled
     * courses are appended so an empty catalogue still has content.
     */
    fun mergeCourses(remote: List<Course>): List<Course> {
        val remoteIds = remote.map { it.id }.toSet()
        return remote + courses.filter { it.id !in remoteIds }
    }

    private fun module(
        order: Int,
        videoId: String,
        title: String,
        durationSeconds: Long
    ): CourseModule = CourseModule(
        id = "mod-" + order.toString().padStart(2, '0'),
        title = title,
        youtubeVideoId = videoId,
        videoUrl = YoutubeVideo.watchUrl(videoId),
        durationSeconds = durationSeconds,
        order = order.toLong()
    )

    private fun thumbnailUrl(videoId: String): String =
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
