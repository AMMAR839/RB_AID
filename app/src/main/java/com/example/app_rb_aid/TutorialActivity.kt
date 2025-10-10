package com.example.app_rb_aid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isNotEmpty
import androidx.core.net.toUri

class TutorialActivity : AppCompatActivity() {

    private lateinit var mainContainer: ConstraintLayout
    private lateinit var nextButton: Button
    private var currentStep = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        mainContainer = findViewById(R.id.main_container)
        nextButton = findViewById(R.id.nextButton)

        // Step awal
        showStepLayout(R.layout.tutorial_1)

        nextButton.setOnClickListener {
            when (currentStep) {
                1 -> {
                    currentStep = 2
                    animateToLayout(R.layout.tutorial_2)
                }
                2 -> {
                    currentStep = 3
                    animateToLayout(R.layout.tutorial_3)
                }
                3 -> {
                    val intent = Intent(this, BerandaActivity::class.java)
                    intent.putExtra("FROM_TUTORIAL", true)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun showStepLayout(layoutRes: Int) {
        mainContainer.removeAllViews()
        val view = LayoutInflater.from(this).inflate(layoutRes, mainContainer, false)
        mainContainer.addView(view)
        if (layoutRes == R.layout.tutorial_3) setupVideo(view)
    }

    private fun animateToLayout(layoutRes: Int, duration: Long = 300L) {
        val oldView: View? = if (mainContainer.isNotEmpty()) mainContainer.getChildAt(0) else null
        val density = resources.displayMetrics.density

        val newView = LayoutInflater.from(this).inflate(layoutRes, mainContainer, false).apply {
            alpha = 0f
            translationX = 24f * density  // geser dikit dari kanan
        }
        mainContainer.addView(newView)

        // cegah double-tap saat animasi
        nextButton.isEnabled = false

        // Hentikan video jika meninggalkan step 3
        oldView?.findViewById<VideoView>(R.id.tutorialVideoView)?.stopPlayback()

        oldView?.animate()
            ?.alpha(0f)
            ?.translationX(-8f * density)
            ?.setDuration(duration - 80)
            ?.withEndAction {
                mainContainer.removeView(oldView)
            }
            ?.start()

        if (layoutRes == R.layout.tutorial_3) {
            setupVideo(newView)
        }

        newView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(duration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    nextButton.isEnabled = true
                }
            })
            .start()
    }

    private fun setupVideo(containerView: View) {
        val videoView = containerView.findViewById<VideoView>(R.id.tutorialVideoView) ?: return
        val uri = "android.resource://$packageName/${R.raw.video}".toUri()
        videoView.setVideoURI(uri)

        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            videoView.start()
        }
    }

    override fun onPause() {
        super.onPause()
        // pause bila app ke background
        mainContainer.findViewById<VideoView>(R.id.tutorialVideoView)?.pause()
    }

    override fun onDestroy() {
        // pastikan pelepasan resource video
        mainContainer.findViewById<VideoView>(R.id.tutorialVideoView)?.stopPlayback()
        super.onDestroy()
    }
}
