package activities

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v7.app.AppCompatActivity
import fragments.FeedFragment
import kotlinx.android.synthetic.main.activity_tabbar.*
import ro.uaic.info.ipfs.R

class MyPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> {
                FeedFragment()
            }
            1 -> FeedFragment()
            else -> {
                return FeedFragment()
            }
        }
    }

    override fun getCount(): Int {
        return 2
    }

    override fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> "Feed"
            1 -> "Peers"
            else -> {
                return "Third Tab"
            }
        }
    }
}

class TabsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tabbar)
        val fragmentManager = MyPagerAdapter(supportFragmentManager)
        viewpager_main.adapter = fragmentManager
        tabs_main.setupWithViewPager(viewpager_main)
    }
}
