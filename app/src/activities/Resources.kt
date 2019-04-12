package activities

import adapters.resources.ResourcesRecyclerAdapter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import com.google.gson.Gson
import io.ipfs.multihash.Multihash
import kotlinx.android.synthetic.main.fragment_feed.*
import models.IIpfsResource
import models.RepositoryDTO
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import org.jetbrains.anko.uiThread
import ro.uaic.info.ipfs.R
import services.ipfs
import utils.Constants
import utils.ResourceParser

class ResourcesActivity: AppCompatActivity(), AnkoLogger {

    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: ResourcesRecyclerAdapter
    private lateinit var userHash: Multihash
    private val parser = ResourceParser()

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        val userHashString = intent.getStringExtra(Constants.INTENT_USER_HASH) ?: throw IllegalStateException()
        userHash = Multihash.fromBase58(userHashString)
        info { userHash }
        setContentView(R.layout.activity_resources)
        setupRecyclerView()
    }

    override fun onResume() = super.onResume().also { 
        refreshPeerContent()
    }

    private fun setupRecyclerView() {
        linearLayoutManager = LinearLayoutManager(applicationContext)
        recyclerView.layoutManager = linearLayoutManager
        val resources: MutableList<IIpfsResource> = mutableListOf()
        adapter = ResourcesRecyclerAdapter(ipfs , resources)
        recyclerView.adapter = adapter
//        adapter.registerAdapterDataObserver(RVEmptyObserver(recyclerView, emptyView))
    }

    private fun refreshPeerContent() {
        doAsync {
            val ipns = ipfs.name.resolve(userHash)
            val hash = ipns.split("/").last()
            try {
                val bytes = ipfs.cat(Multihash.fromBase58(hash))
                val json = String(bytes)
                val repositoryDTO = Gson().fromJson<RepositoryDTO>(json , RepositoryDTO::class.java)
                if (repositoryDTO.multiHashes.isNotEmpty()) {
                    val resources = repositoryDTO.multiHashes.map {
                        val bytes = ipfs.cat(it)
                        val json = String(bytes)
                         parser.parseResource(json)
                    }.filterNotNull()
                    uiThread {
                        adapter.addAll(resources)
                    }
                }
            } catch (ex: Throwable) {

            }
        }
    }
}