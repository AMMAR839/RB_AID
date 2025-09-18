import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.app_rb_aid.R
import com.example.app_rb_aid.TutorialPageFragment

class TutorialPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {


    private val tutorialPages = listOf(
        Pair("Pertama Pastikan \n Alat sudah siap", R.drawable.gambar_tutor),
        Pair("Pastikan jarak mata dan \n lensa adalah 5 cm", R.drawable.satu),
    )

    override fun getItemCount(): Int {
        return tutorialPages.size
    }

    override fun createFragment(position: Int): Fragment {
        val (title, imageResId) = tutorialPages[position]
        return TutorialPageFragment.newInstance(title, imageResId)
    }
}