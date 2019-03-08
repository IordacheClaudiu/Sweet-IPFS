package fragments

import adapters.resources.ResourcesRecyclerAdapter
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import application.clipboard
import application.ipfs
import io.ipfs.multiaddr.MultiAddress
import kotlinx.android.synthetic.main.fragment_feed.*
import models.IIpfsResource
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import org.jetbrains.anko.uiThread
import ro.uaic.info.ipfs.R
import services.ipfsDaemon
import java.io.IOException
import java.util.function.Consumer
import java.util.stream.Collectors


class FeedFragment : Fragment() , AnkoLogger {
    interface FeedFragmentListener {
        fun feedFragmentOnAddTextPressed(fragment: FeedFragment)
        fun feedFragmentOnAddFilePressed(fragment: FeedFragment)
        fun feedFragmentOnAddImagePressed(fragment: FeedFragment)
    }

    private var mContext: Context? = null

    var delegate: FeedFragmentListener? = null
    // UI
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: ResourcesRecyclerAdapter

    private val notImplemented = { AlertDialog.Builder(mContext).setMessage("This feature is not yet implemented. Sorry").show(); true }

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
        setupRecyclerView()
        setupActionBtn(notImplemented)
        setupConfigBtn()
        setupFloatingBtns()
    }

    fun add(resource: IIpfsResource) {
        adapter.add(resource)
    }

    private fun setupRecyclerView() {
        linearLayoutManager = LinearLayoutManager(mContext)
        recyclerView.layoutManager = linearLayoutManager
        val resources: MutableList<IIpfsResource> = mutableListOf()
        adapter = ResourcesRecyclerAdapter(ipfs , resources)
        recyclerView.adapter = adapter
    }

    private fun setupConfigBtn() {
        configbtn.setOnClickListener {
            PopupMenu(mContext , it).apply {
                menu.apply {
                    addSubMenu(getString(R.string.menu_identity)).apply {
                        add(getString(R.string.menu_identity_peerid)).setOnMenuItemClickListener {
                            val id = mContext !!.ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PeerID").asString
                            AlertDialog.Builder(mContext).apply {
                                setTitle(getString(R.string.title_peerid))
                                setMessage(id)
                                setPositiveButton(getString(R.string.copy)) { _ , _ -> }
                                setNeutralButton(getString(R.string.close)) { _ , _ -> }
                            }.show().apply {
                                getButton(AlertDialog.BUTTON_POSITIVE)
                                        .setOnClickListener { clipboard(id) }
                            }; true
                        }
                        add(getString(R.string.menu_identity_privatekey)).setOnMenuItemClickListener {
                            val key = mContext !!.ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PrivKey").asString
                            AlertDialog.Builder(mContext).apply {
                                setTitle(getString(R.string.title_privatekey))
                                setMessage(key)
                                setPositiveButton(getString(R.string.copy)) { _ , _ -> }
                                setNeutralButton(getString(R.string.close)) { _ , _ -> }
                            }.show().apply {
                                getButton(AlertDialog.BUTTON_POSITIVE)
                                        .setOnClickListener { clipboard(key) }
                            }; true
                        }
                    }
                    add(getString(R.string.menu_peers)).setOnMenuItemClickListener {
                        doAsync({
                            info { "Swarm Peers error." }
                        } , {
                            var representation = "No peers."
                            var peers = ipfs.swarm.peers()
                            if (peers.isNotEmpty()) {
                                representation = peers.joinToString(separator = "\n" , transform = {
                                    it.id.toString()
                                })
                            }
                            info { "Swarm Peers success." }
                            uiThread {
                                AlertDialog.Builder(mContext).apply {
                                    setTitle(getString(R.string.menu_peers))
                                    setMessage(representation)
                                    setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                }.show()
                            }
                        });true
                    }

                    add(getString(R.string.menu_others)).setOnMenuItemClickListener {
                        doAsync({} , {
                            ipfs.version()
                            val addresses = mContext !!.ipfsDaemon.config.getAsJsonObject("Addresses")
                            AlertDialog.Builder(mContext).apply {
                                setTitle(getString(R.string.title_others))
                                setMessage("""
                                            ${getString(R.string.others_goipfs_version)}: $it
                                            ${getString(R.string.others_api_address)}: ${addresses.getAsJsonPrimitive("API").asString}
                                            ${getString(R.string.others_gateway_address)}: ${addresses.getAsJsonPrimitive("Gateway").asString}
                                        """.trimIndent())
                                setNeutralButton(getString(R.string.close)) { _ , _ -> }
                            }.show()
                        }); true
                    }
                    addSubMenu(getString(R.string.menu_bootstrap)).apply {
                        add(getString(R.string.menu_bootstrap_list_all)).setOnMenuItemClickListener {
                            doAsync {
                                try {
                                    var representation = "No bootstrap nodes."
                                    val multiAddresses = ipfs.bootstrap.list()
                                    if (multiAddresses.isNotEmpty()) {
                                        representation = multiAddresses.joinToString(separator = "\n")
                                    }
                                    info { "Bootstrap list success." }
                                    uiThread {
                                        AlertDialog.Builder(mContext).apply {
                                            setTitle(getString(R.string.menu_bootstrap_list_all))
                                            setMessage(representation)
                                            setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                        }.show()
                                    }
                                } catch (ex: IOException) {
                                    info { "Bootstrap list error." }
                                }
                            };true
                        }
                        add(getString(R.string.menu_bootstrap_add_node)).setOnMenuItemClickListener {
                            AlertDialog.Builder(mContext).apply {
                                setTitle(getString(R.string.title_bootstrap_add_node))
                                val txtView = EditText(mContext).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    setView(this)
                                }
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val txtInput = txtView.text.toString()
                                    if (txtInput.isBlank()) {
                                        return@setPositiveButton
                                    }
                                    val nodeAddress: MultiAddress
                                    try {
                                        nodeAddress = MultiAddress(txtInput)
                                    } catch (e: IllegalStateException) {
                                        info { "Bootstrap add node error:" + e.localizedMessage }
                                        return@setPositiveButton
                                    }
                                    doAsync({
                                        info { "Bootstrap add node error." }
                                    } , {
                                        ipfs.bootstrap.add(nodeAddress)
                                        info { "Bootstrap add node success." }
                                    })
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                    }
                    addSubMenu(getString(R.string.menu_pubsub)).apply {
                        add(getString(R.string.menu_pubsub_list_rooms)).setOnMenuItemClickListener {
                            doAsync({ info { "PubSub list rooms error." } } , {
                                val list = ipfs.pubsub.ls()
                                val map = list as Map<String , List<String>>
                                val rooms = map["Strings"]
                                var text = "No rooms."
                                if (rooms != null && rooms.isNotEmpty()) {
                                    text = rooms.joinToString(separator = "\n")
                                }
                                AlertDialog.Builder(mContext).apply {
                                    setTitle(getString(R.string.menu_pubsub_list_rooms))
                                    setMessage(text)
                                    setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                }.show()
                                info { "PubSub list rooms success." }
                            }); true
                        }
                        add(getString(R.string.menu_pubsub_join_room)).setOnMenuItemClickListener {
                            AlertDialog.Builder(mContext).apply {
                                setTitle(getString(R.string.menu_pubsub_join_room))
                                val txtView = EditText(mContext).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    setView(this)
                                }
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val room = txtView.text.toString()
                                    if (room.isBlank()) {
                                        return@setPositiveButton
                                    }
                                    doAsync({ info { "PubSub join room error." } } , {
                                        ipfs.pubsub.sub(room)
                                        info { "PubSub join room succedeed." }
                                    })
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                        add(getString(R.string.menu_pubsub_post_to_room)).setOnMenuItemClickListener {
                            AlertDialog.Builder(mContext).apply {
                                val layout = LinearLayout(mContext)
                                layout.orientation = LinearLayout.VERTICAL
                                val topicTextView = EditText(mContext).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    hint = "Enter room name"
                                }

                                layout.addView(topicTextView)
                                val messageTextView = EditText(mContext).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    hint = "Enter message"
                                }
                                layout.addView(messageTextView)
                                setView(layout)
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val room = topicTextView.text.toString()
                                    val message = messageTextView.text.toString()
                                    if (room.isNotBlank() && message.isNotBlank()) {
                                        doAsync({ info { "PubSub post room ${room} failed.\")" } } , {
                                            ipfs.pubsub.pub(room , message)
                                            info { "PubSub post to room $room succedeed." }
                                        })
                                    } else {

                                    }
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                    }
                }
            }.show()
        }
    }

    private fun setupActionBtn(notimpl: () -> Boolean) {
        actionbtn.setOnClickListener { btn ->
            PopupMenu(mContext , btn).apply {
                menu.apply {
                    add(getString(R.string.menu_garbage_collect)).setOnMenuItemClickListener {
                        true.also {
                            doAsync {
                                ipfs.repo.gc()
                                uiThread {
                                    AlertDialog.Builder(mContext)
                                            .setMessage(getString(R.string.garbage_collected)).show()
                                }
                            }
                        }
                    }

                    add(getString(R.string.menu_pins)).setOnMenuItemClickListener { notimpl() }
                    add(getString(R.string.menu_keys)).setOnMenuItemClickListener { notimpl() }
                    addSubMenu(getString(R.string.menu_swarm)).apply {
                        add(getString(R.string.menu_swarm_connect)).setOnMenuItemClickListener { notimpl() }
                        add(getString(R.string.menu_swarm_disconnect)).setOnMenuItemClickListener { notimpl() }
                    }
                    addSubMenu(getString(R.string.menu_dht)).apply {
                        add(getString(R.string.menu_dht_findpeer)).setOnMenuItemClickListener { notimpl() }
                        add(getString(R.string.menu_dht_findprovs)).setOnMenuItemClickListener { notimpl() }
                        add(getString(R.string.menu_dht_query)).setOnMenuItemClickListener { notimpl() }
                    }
                }
            }.show()
        }
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