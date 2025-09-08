import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.app_rb_aid.R

class TutorialPageFragment : Fragment(R.layout.fragment_tutorial_page) {

    companion object {
        fun newInstance(title: String, imageResId: Int): TutorialPageFragment {
            val fragment = TutorialPageFragment()
            val args = Bundle().apply {
                putString("title", title)
                putInt("imageResId", imageResId)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val titleText = arguments?.getString("title")
        val imageResId = arguments?.getInt("imageResId") ?: 0

        view.findViewById<TextView>(R.id.title_text).text = titleText
        if (imageResId != 0) {
            view.findViewById<ImageView>(R.id.diagram_image).setImageResource(imageResId)
        }
    }
}