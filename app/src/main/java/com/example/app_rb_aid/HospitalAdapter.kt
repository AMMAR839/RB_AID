package com.example.app_rb_aid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HospitalAdapter(
    private val hospitals: List<Hospital>,
    private val onItemClick: (Hospital) -> Unit,
    private val onSendEmailClick: (Hospital) -> Unit
) : RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder>() {

    inner class HospitalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.textView_hospital_name)
        private val distanceText: TextView = itemView.findViewById(R.id.textView_distance)
        private val sendButton: ImageButton = itemView.findViewById(R.id.button_send_email)

        fun bind(hospital: Hospital) {
            nameText.text = hospital.name
            distanceText.text = "${String.format("%.2f", hospital.distance)} KM"

            // klik item → buka maps
            itemView.setOnClickListener { onItemClick(hospital) }
            // klik tombol → kirim email
            sendButton.setOnClickListener { onSendEmailClick(hospital) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_item_hospital, parent, false)
        return HospitalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        holder.bind(hospitals[position])
    }

    override fun getItemCount() = hospitals.size
}
