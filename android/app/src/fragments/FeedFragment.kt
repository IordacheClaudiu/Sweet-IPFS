package fragments

import adapters.resources.ResourcesRecyclerAdapter
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import io.ipfs.multiaddr.MultiAddress
import kotlinx.android.synthetic.main.fragment_feed.*
import models.IIpfsResource
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import org.jetbrains.anko.uiThread
import ro.uaic.info.ipfs.R
import services.ipfs
import services.ipfsDaemon
import utils.RVEmptyObserver
import utils.clipboard
import java.io.IOException


class FeedFragment : Fragment() , AnkoLogger {


    interface FeedFragmentListener {
        fun feedFragmentOnAddTextPressed(fragment: FeedFragment)
        fun feedFragmentOnAddFilePressed(fragment: FeedFragment)
        fun feedFragmentOnAddImagePressed(fragment: FeedFragment)
    }

    private var mContext: Context? = null

    var delegate: FeedFragmentListener? = null

    // UI
    private lateinit var snackBar: Snackbar
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: ResourcesRecyclerAdapter

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        mContext = context
        if (context is FeedFragmentListener) {
            delegate = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        mContext = null
    }

    override fun onCreateView(inflater: LayoutInflater , container: ViewGroup? , savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_feed , container , false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        snackBar = Snackbar.make(feedConstraintLayout , R.string.feed_new_items_text , Snackbar.LENGTH_LONG)
        setupRecyclerView()
        setupFloatingBtns()
    }

    fun add(resources: List<IIpfsResource>) {
        adapter.addAll(resources)
        updateLayoutManager()
    }

    fun add(resource: IIpfsResource) {
        adapter.add(resource)
        updateLayoutManager()
    }

    private fun updateLayoutManager() {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val visiblePosition = layoutManager.findFirstVisibleItemPosition()
        if (visiblePosition > 0) {
            snackBar.setAction(R.string.feed_new_items_refresh) {
                snackBar.dismiss()
                layoutManager.smoothScrollToPosition(recyclerView , RecyclerView.State() , 0)
            }
            snackBar.show()
        }
    }

    private fun setupRecyclerView() {
        linearLayoutManager = LinearLayoutManager(mContext)
        recyclerView.layoutManager = linearLayoutManager
        val resources: MutableList<IIpfsResource> = mutableListOf()
        adapter = ResourcesRecyclerAdapter(ipfs , resources)
        recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(RVEmptyObserver(recyclerView, emptyView))
    }

    private fun setupFloatingBtns() {
        addFileBtn.setOnClickListener {
            floatingBtnMenu.close(true)
            delegate?.feedFragmentOnAddFilePressed(this)
        }
        addPhotoBtn.setOnClickListener {
            floatingBtnMenu.close(true)
            delegate?.feedFragmentOnAddImagePressed(this)
        }
        addTextBtn.setOnClickListener {
            floatingBtnMenu.close(true)
            delegate?.feedFragmentOnAddTextPressed(this)
        }

    }

}