package com.example.app_rb_aid

import TutorialPagerAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Menghubungkan Activity dengan layout XML activity_tutorial.xml
        setContentView(R.layout.activity_tutorial)

        // Menginisialisasi ViewPager2 dan TabLayout dari layout
        val viewPage: ViewPager2 = findViewById(R.id.view_page)

        // Membuat adapter untuk menyediakan halaman-halaman tutorial
        val adapter = TutorialPagerAdapter(supportFragmentManager, lifecycle)
        viewPage.adapter = adapter

        // Menghubungkan TabLayout dengan ViewPager2 untuk indikator titik

    }
}