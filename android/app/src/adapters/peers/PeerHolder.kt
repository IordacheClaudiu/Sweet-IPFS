package adapters.peers

import android.support.v7.widget.RecyclerView
import android.view.View
import com.google.gson.Gson
import io.ipfs.api.IPFS
import io.ipfs.api.Peer
import io.ipfs.multihash.Multihash
import kotlinx.android.synthetic.main.recyclerview_peer_row.view.*
import models.RepositoryDTO
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import org.jetbrains.anko.uiThread
import java.util.concurrent.Future

enum class State {
    LOADING, ERROR, EMPTY, DATA
}

class PeerHolder(v: View , private val ipfs: IPFS): RecyclerView.ViewHolder(v) , View.OnClickListener , AnkoLogger {

    private var view: View = v
    lateinit var peer: Peer
    lateinit var listener: OnPeerClickListener
    private var asyncUnit: Future<Unit>? = null
    private var nrOfFiles: Int? = null
    private var username: String? = null

    init {
        v.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        listener.onPeerClick(peer)
    }

    fun bind(peer: Peer, listener: OnPeerClickListener) {
        asyncUnit?.let { it.cancel(true) }
        this.peer = peer
        this.listener = listener
        updateUI()
    }

    private fun updateUI() {
        view.peer_name.text = peer.id.toString()
        updateUI(State.LOADING)
        asyncUnit = doAsync ({
            updateUI(State.ERROR)
        }, {
            val ipns = ipfs.name.resolve(peer.id)
            val hash = ipns.split("/").last()
            try {
                val bytes = ipfs.cat(Multihash.fromBase58(hash))
                val json = String(bytes)
                val repositoryDTO = Gson().fromJson<RepositoryDTO>(json, RepositoryDTO::class.java)
                if (repositoryDTO.multiHashes.isNotEmpty()) {
                    uiThread {
                        nrOfFiles = repositoryDTO.multiHashes.size
                        username = repositoryDTO.peer.username
                        updateUI(State.DATA)
                    }
                } else {
                    uiThread {
                       updateUI(State.EMPTY)
                    }
                }
            } catch (ex: Throwable) {
                info { "${peer.id} no repo" }
                uiThread {
                    updateUI(State.EMPTY)
                }
            }
        })
    }

    private fun updateUI(state: State) {
        when(state) {
            State.ERROR -> {
                view.peer_file_counter.text = "An error occured."
            }
            State.LOADING -> {
                view.peer_file_counter.text = "Fetching data..."
            }
            State.EMPTY -> {
                view.peer_file_counter.text = "No files hosted"
            }
            State.DATA -> {
                view.peer_file_counter.text = "${nrOfFiles!!} files hosted"
                view.peer_name.text = "${username!!}"
            }
        }
    }
}