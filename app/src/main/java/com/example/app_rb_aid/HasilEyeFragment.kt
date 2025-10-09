package com.example.app_rb_aid

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment

class HasilEyeFragment : Fragment() {

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_URI   = "arg_uri"
        private const val ARG_LABEL = "arg_label"
        private const val ARG_SCORE = "arg_score"

        fun newInstance(title: String, imageUri: String?, label: String, score: Float) =
            HasilEyeFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_URI   to imageUri,
                    ARG_LABEL to label,
                    ARG_SCORE to score
                )
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_hasil_eye, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvEyeTitle = view.findViewById<TextView>(R.id.tvEyeTitle)
        val ivEye      = view.findViewById<ImageView>(R.id.ivEye)
        val tvLabel    = view.findViewById<TextView>(R.id.tvLabel)
        val tvScore    = view.findViewById<TextView>(R.id.tvScore)

        val title = arguments?.getString(ARG_TITLE) ?: ""
        val uri   = arguments?.getString(ARG_URI)
        val label = arguments?.getString(ARG_LABEL) ?: "?"
        val score = arguments?.getFloat(ARG_SCORE) ?: -1f

        tvEyeTitle.text = "Mata $title"

        uri?.let { ivEye.setImageURI(Uri.parse(it)) }

        tvLabel.text = "Hasil: $label"
        tvScore.text = if (score >= 0f) "Skor: ${"%.2f".format(score)}" else "Skor: -"
    }
}
