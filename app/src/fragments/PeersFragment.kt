package fragments

import adapters.peers.PeersRecyclerAdapter
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import application.ipfs
import kotlinx.android.synthetic.main.fragment_peers.*
import org.jetbrains.anko.*
import ro.uaic.info.ipfs.R
import utils.RVEmptyObserver

class PeersFragment : Fragment() , AnkoLogger {

    private var mContext: Context? = null

    // UI
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: PeersRecyclerAdapter

    override fun onCreateView(inflater: LayoutInflater , container: ViewGroup? , savedInstanceState: Bundle?): View? {
        info { "onCreateView" }
        return inflater.inflate(R.layout.fragment_peers , container , false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        swipe_container.setOnRefreshListener {
            refreshPeers()
        }

        setupRecyclerView()
        refreshPeers()
        info { "onActivityCreated" }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        mContext = context
        info { "onAttach" }
    }


    override fun onDetach() {
        super.onDetach()
        mContext = null
        info { "onDetach" }
    }

    private fun refreshPeers() {

        doAsync({
            error { it }
            swipe_container.isRefreshing = false
        } , {
            val peers = ipfs.swarm.peers()
            uiThread {
                adapter.clear()
                adapter.add(peers)
                swipe_container.isRefreshing = false
            }
        })
    }

    private fun setupRecyclerView() {
        linearLayoutManager = LinearLayoutManager(mContext)
        recyclerView.layoutManager = linearLayoutManager
        adapter = PeersRecyclerAdapter(ipfs)
        recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(RVEmptyObserver(recyclerView, emptyView))
    }
}