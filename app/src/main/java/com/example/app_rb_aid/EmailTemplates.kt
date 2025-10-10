package com.example.app_rb_aid.util

object EmailTemplates {

    fun subject(patientName: String, examDate: String): String =
        "Rujukan Skrining Retinoblastoma — $patientName ($examDate)"

    fun body(
        rsName: String,
        doctorOrTeam: String,
        patientName: String,
        nik: String?,
        dob: String?,
        contact: String?,
        address: String?,
        examDate: String,
        examTime: String,
        diagnosis: String?,
        senderName: String,
        appOrOrg: String
    ): String {
        fun s(x: Float?) = if (x == null || x < 0f) "-" else String.format("%.2f", x)
        return """
Yth. dr./Tim $doctorOrTeam ($rsName),

Kami mengirimkan hasil skrining retinoblastoma untuk:

Data Pasien
• Nama          : $patientName
• NIK           : ${nik ?: "-"}
• Tgl. Lahir    : ${dob ?: "-"}
• Kontak        : ${contact ?: "-"}
• Alamat (ops)  : ${address ?: "-"}

Detail Pemeriksaan
• Tanggal/Waktu : $examDate $examTime
• Ringkasan     : ${diagnosis ?: "-"}

Lampiran
• Foto mata kanan (jika ada)
• Foto mata kiri (jika ada)

Catatan
Hasil ini adalah skrining awal berbasis komputer (online) dan bukan diagnosis akhir.
Mohon evaluasi klinis lanjutan sesuai prosedur di fasilitas kesehatan Anda.

Terima kasih,
$senderName — $appOrOrg
""".trim()
    }
}
