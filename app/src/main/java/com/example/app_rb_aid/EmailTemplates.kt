package com.example.app_rb_aid.util

import java.util.Locale

object EmailTemplates {

    fun subject(patientName: String, examDate: String): String =
        "Rujukan Skrining Retinoblastoma — $patientName ($examDate)"

    fun body(
        rsName: String,
        doctorOrTeam: String,   // isi: "Dokter/Tim" atau "Dokter Sp.M"
        patientName: String,
        nik: String?,
        dob: String?,
        contact: String?,
        address: String?,
        examDate: String,
        examTime: String,
        rightLabel: String?, rightScore: Float?,
        leftLabel: String?,  leftScore: Float?,
        diagnosis: String?,
        senderName: String,
        appOrOrg: String
    ): String {
        fun clean(s: String?) = s?.takeIf { it.isNotBlank() } ?: "-"
        fun conf(score: Float?): String =
            if (score == null || score < 0f) ""
            else " (conf " + String.format(Locale("id","ID"), "%.2f", score) + ")"
        fun side(label: String?, score: Float?) = if (label.isNullOrBlank()) "-" else label.trim() + conf(score)

        val dateTime = (examDate + " " + examTime).trim()

        return """
Yth. $doctorOrTeam ($rsName),

Kami mengirimkan hasil skrining retinoblastoma.

Data Pasien
• Nama                : ${clean(patientName)}
• NIK                    : ${clean(nik)}
• Tgl. Lahir          : ${clean(dob)}
• Kontak              : ${clean(contact)}
• Alamat (ops)   : ${clean(address)}

Detail Pemeriksaan
• Tanggal/Waktu  : ${clean(dateTime)}

Hasil Model (Per Mata)
• Kanan             : ${side(rightLabel, rightScore)}
• Kiri                   : ${side(leftLabel,  leftScore)}
• Ringkasan      : ${clean(diagnosis)}

Lampiran

• Foto mata kanan 
• Foto mata kiri 

Catatan
Hasil ini adalah skrining awal berbasis komputer dan bukan diagnosis akhir.
Mohon evaluasi klinis lanjutan sesuai prosedur di fasilitas kesehatan Anda.

Terima kasih,
$senderName — $appOrOrg
""".trim()
    }
}
