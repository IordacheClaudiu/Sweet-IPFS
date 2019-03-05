package fragments

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ro.uaic.info.ipfs.R

class FeedFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater , container: ViewGroup? , savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.activity_console, container, false)
    }


}