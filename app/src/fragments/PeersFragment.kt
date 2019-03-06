package fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.jetbrains.anko.AnkoLogger
import ro.uaic.info.ipfs.R

class PeersFragment : Fragment() , AnkoLogger {
    override fun onCreateView(inflater: LayoutInflater , container: ViewGroup? , savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_peers , container , false)
    }
}