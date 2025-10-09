package com.example.app_rb_aid

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

data class EyePage(
    val title: String,
    val imageUri: String?,
    val label: String,
    val score: Float
)

class HasilPagerAdapter(
    activity: AppCompatActivity,
    private val pages: List<EyePage>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment {
        val page = pages[position]
        return HasilEyeFragment.newInstance(
            title = page.title,
            imageUri = page.imageUri,
            label = page.label,
            score = page.score
        )
    }
}
