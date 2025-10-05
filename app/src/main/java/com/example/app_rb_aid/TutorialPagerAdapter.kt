package com.example.app_rb_aid

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent


class TutorialPagerAdapter(
    private val context: Context,
    private val pages: List<Int>
) : RecyclerView.Adapter<TutorialPagerAdapter.TutorialViewHolder>() {

    class TutorialViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TutorialViewHolder {
        val view = LayoutInflater.from(context).inflate(viewType, parent, false)
        return TutorialViewHolder(view)
    }

    override fun onBindViewHolder(holder: TutorialViewHolder, position: Int) {
        // Halaman terakhir memiliki tombol kamera
        if (position == pages.size - 1) {
            val cameraButton = holder.itemView.findViewById<View>(R.id.cameraButton)
            cameraButton?.setOnClickListener {
                val intent = Intent(context, BerandaActivity::class.java)
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = pages[position]
}
