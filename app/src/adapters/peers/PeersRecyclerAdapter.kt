package adapters.peers

import android.support.v7.util.SortedList
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipfs.api.IPFS
import io.ipfs.api.Peer
import models.PeerDTO
import ro.uaic.info.ipfs.R

interface OnPeerClickListener {
    fun onPeerClick(peer: Peer)
}

class PeersRecyclerAdapter(private val ipfs: IPFS, private val listener: OnPeerClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mPeers: SortedList<Peer>

    init {
        mPeers = SortedList(Peer::class.java, object : SortedListAdapterCallback<Peer>(this) {
            override fun compare(o1: Peer, o2: Peer): Int = o1.id.toHex().compareTo(o2.id.toHex())

            override fun areContentsTheSame(oldItem: Peer, newItem: Peer): Boolean = oldItem == newItem

            override fun areItemsTheSame(item1: Peer, item2: Peer): Boolean = item1 == item2
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup , viewType: Int): RecyclerView.ViewHolder {
        val inflatedView = LayoutInflater.from(parent.context)
                .inflate(R.layout.recyclerview_peer_row , parent , false) as View
        return PeerHolder(inflatedView, ipfs)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder , position: Int) {
        val peerHash = mPeers[position]
        when(holder) {
            is PeerHolder -> holder.bind(peerHash, listener)
        }
    }

    override fun getItemCount(): Int {
        return mPeers.size()
    }

    fun add(peers: List<Peer>) {
        mPeers.addAll(peers)
    }

    fun clear() {
        mPeers.clear()
    }
}
