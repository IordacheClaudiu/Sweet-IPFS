package ro.uaic.info.ipfs

import adapters.ResourcesRecyclerAdapter
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import io.ipfs.api.Peer
import io.ipfs.multiaddr.MultiAddress
import kotlinx.android.synthetic.main.activity_console.*
import models.IIpfsResource
import org.jetbrains.anko.*
import java.util.function.Consumer
import java.util.stream.Collectors


class ConsoleActivity : AppCompatActivity(), AnkoLogger {

    private val ctx = this as Context
    private val MY_PERMISSIONS_REQUEST_FINE_LOCATION = 100

    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: ResourcesRecyclerAdapter

    private val notImplemented = { AlertDialog.Builder(ctx).setMessage("This feature is not yet implemented. Sorry").show(); true }

    private val locationListener = object : LocationListener {

        override fun onLocationChanged(location: Location) {
            debug { location }
            // Called when a new location is found by the network location provider.
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            debug { "onStatusChanged: $status" }
        }

        override fun onProviderEnabled(provider: String) {
            debug { "onProviderEnabled: $provider" }
        }

        override fun onProviderDisabled(provider: String) {
            debug { "onProviderDisabled: $provider" }
        }
    }

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        setContentView(R.layout.activity_console)
        setupReciclerView()
        setupActionBtn(notImplemented)
        setupConfigBtn()
        setupFloatingButton()
        setupLocationManager()
    }

    override fun onActivityResult(req: Int, res: Int, rdata: Intent?) {
        super.onActivityResult(req, res, rdata)
        if (res != RESULT_OK) return
        when (req) {
            1 -> Intent(ctx, ShareActivity::class.java).apply {
                data = rdata?.data ?: return
                action = ACTION_SEND
                startActivity(this)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    try {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
                    } catch (e: SecurityException) {
                        error { e }
                    }
                } else {
                    error { "Location service not granted" }
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
    }

    private fun setupReciclerView() {
        linearLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = linearLayoutManager
        val resources: MutableList<IIpfsResource> = mutableListOf()
        adapter = ResourcesRecyclerAdapter(resources)
        recyclerView.adapter = adapter
//        doAsync {
//            val peers = ipfs.swarm.peers()
//            uiThread {
//                adapter.add(peers)
//            }
//        }
    }

    private fun setupLocationManager() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        MY_PERMISSIONS_REQUEST_FINE_LOCATION)
            }
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
        }
    }

    private fun setupConfigBtn() {
        configbtn.setOnClickListener {
            PopupMenu(ctx, it).apply {
                menu.apply {
                    addSubMenu(getString(R.string.menu_identity)).apply {
                        add(getString(R.string.menu_identity_peerid)).setOnMenuItemClickListener {
                            val id = ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PeerID").asString
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.title_peerid))
                                setMessage(id)
                                setPositiveButton(getString(R.string.copy)) { _, _ -> }
                                setNeutralButton(getString(R.string.close)) { _, _ -> }
                            }.show().apply {
                                getButton(AlertDialog.BUTTON_POSITIVE)
                                        .setOnClickListener { clipboard(id) }
                            }; true
                        }
                        add(getString(R.string.menu_identity_privatekey)).setOnMenuItemClickListener {
                            val key = ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PrivKey").asString
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.title_privatekey))
                                setMessage(key)
                                setPositiveButton(getString(R.string.copy)) { _, _ -> }
                                setNeutralButton(getString(R.string.close)) { _, _ -> }
                            }.show().apply {
                                getButton(AlertDialog.BUTTON_POSITIVE)
                                        .setOnClickListener { clipboard(key) }
                            }; true
                        }
                    }
                    add(getString(R.string.menu_peers)).setOnMenuItemClickListener {
                        async(50, { ipfs.swarm.peers() }, {
                            var representation = "No peers."
                            val peers = it as List<Peer>
                            if (peers.isNotEmpty()) {
                                representation = peers.joinToString(separator = "\n", transform = {
                                    it.address.toString() + it.id
                                })
                            }
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.menu_peers))
                                setMessage(representation)
                                setNeutralButton(getString(R.string.close)) { _, _ -> }
                            }.show()
                            debug { "Swarm Peers success." }
                        }, {
                            debug { "Swarm Peers error." }
                        }
                        );true
                    }

                    add(getString(R.string.menu_others)).setOnMenuItemClickListener {
                        async(60, { ipfs.version() },
                                {
                                    val addresses = ipfsDaemon.config.getAsJsonObject("Addresses")
                                    AlertDialog.Builder(ctx).apply {
                                        setTitle(getString(R.string.title_others))
                                        setMessage("""
                                            ${getString(R.string.others_goipfs_version)}: $it
                                            ${getString(R.string.others_api_address)}: ${addresses.getAsJsonPrimitive("API").asString}
                                            ${getString(R.string.others_gateway_address)}: ${addresses.getAsJsonPrimitive("Gateway").asString}
                                        """.trimIndent())
                                        setNeutralButton(getString(R.string.close)) { _, _ -> }
                                    }.show()
                                },
                                {
                                    val addresses = ipfsDaemon.config.getAsJsonObject("Addresses")
                                    AlertDialog.Builder(ctx).apply {
                                        setTitle(getString(R.string.title_others))
                                        setMessage("""
                                            ${getString(R.string.others_goipfs_version)}: ${getString(R.string.others_goipfs_version_unknown)}
                                            ${getString(R.string.others_api_address)}: ${addresses.getAsJsonPrimitive("API").asString}
                                            ${getString(R.string.others_gateway_address)}: ${addresses.getAsJsonPrimitive("Gateway").asString}
                                        """.trimIndent())
                                    }.show()
                                }
                        ); true
                    }
                    addSubMenu(getString(R.string.menu_bootstrap)).apply {
                        add(getString(R.string.menu_bootstrap_list_all)).setOnMenuItemClickListener {
                            async(60, { ipfs.bootstrap.list() }, {
                                var representation = "No bootstrap nodes."
                                val multiAddresses = it as List<MultiAddress>
                                if (multiAddresses.isNotEmpty()) {
                                    representation = multiAddresses.joinToString(separator = "\n")
                                }
                                AlertDialog.Builder(ctx).apply {
                                    setTitle(getString(R.string.menu_bootstrap_list_all))
                                    setMessage(representation)
                                    setNeutralButton(getString(R.string.close)) { _, _ -> }
                                }.show()
                                debug { "Bootstrap list success." }
                            }, {
                                debug { "Bootstrap list error." }
                            }); true
                        }
                        add(getString(R.string.menu_bootstrap_add_node)).setOnMenuItemClickListener {
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.title_bootstrap_add_node))
                                val txtView = EditText(ctx).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    setView(this)
                                }
                                setPositiveButton(getString(R.string.apply)) { _, _ ->
                                    val txtInput = txtView.text.toString()
                                    if (txtInput.isBlank()) {
                                        return@setPositiveButton
                                    }
                                    val nodeAddress: MultiAddress
                                    try {
                                        nodeAddress = MultiAddress(txtInput)
                                    } catch (e: IllegalStateException) {
                                        debug { "Bootstrap add node error:" + e.localizedMessage }
                                        return@setPositiveButton
                                    }
                                    async(60, { ipfs.bootstrap.add(nodeAddress) },
                                            {
                                                debug { "Bootstrap add node success." }
                                            }, {
                                        debug { "Bootstrap add node error." }
                                    })
                                }
                                setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                            }.show(); true
                        }
                    }
                    addSubMenu(getString(R.string.menu_pubsub)).apply {
                        add(getString(R.string.menu_pubsub_list_rooms)).setOnMenuItemClickListener {
                            async(60, {
                                ipfs.pubsub.ls()
                            }, {

                                val map = it as Map<String, List<String>>
                                val rooms = map["Strings"]
                                var text = "No rooms."
                                if (rooms != null && rooms.isNotEmpty()) {
                                    text = rooms.joinToString(separator = "\n")
                                }
                                AlertDialog.Builder(ctx).apply {
                                    setTitle(getString(R.string.menu_pubsub_list_rooms))
                                    setMessage(text)
                                    setNeutralButton(getString(R.string.close)) { _, _ -> }
                                }.show()
                                debug { "PubSub list rooms success." }
                            }, {
                                debug { "PubSub list rooms error." }
                            }); true
                        }
                        add(getString(R.string.menu_pubsub_join_room)).setOnMenuItemClickListener {
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.menu_pubsub_join_room))
                                val txtView = EditText(ctx).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    setView(this)
                                }
                                setPositiveButton(getString(R.string.apply)) { _, _ ->
                                    val room = txtView.text.toString()
                                    if (room.isBlank()) {
                                        return@setPositiveButton
                                    }
                                    async(60, { ipfs.pubsub.sub(room) },
                                            {
                                                debug { "PubSub join room succedeed." }
                                            }, {
                                        debug { "PubSub join room error." }
                                    })
                                }
                                setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                            }.show(); true
                        }
                        add(getString(R.string.menu_pubsub_post_to_room)).setOnMenuItemClickListener {
                            AlertDialog.Builder(ctx).apply {
                                val layout = LinearLayout(ctx)
                                layout.orientation = LinearLayout.VERTICAL
                                val topicTextView = EditText(ctx).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    hint = "Enter room name"
                                }

                                layout.addView(topicTextView)
                                val messageTextView = EditText(ctx).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    hint = "Enter message"
                                }
                                layout.addView(messageTextView)
                                setView(layout)
                                setPositiveButton(getString(R.string.apply)) { _, _ ->
                                    val room = topicTextView.text.toString()
                                    val message = messageTextView.text.toString()
                                    if (room.isNotBlank() && message.isNotBlank()) {
                                        async(60,
                                                { ipfs.pubsub.pub(room, message) },
                                                { debug { "PubSub post to room $room succedeed." } },
                                                { debug { "PubSub post room ${room} failed.\")" } }
                                        )
                                    } else {

                                    }
                                }
                                setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                            }.show(); true
                        }
                        add("Listen").setOnMenuItemClickListener {
                            doAsync {
                                val sub = ipfs.pubsub.sub("topic")
                                ipfs.pubsub.sub("topic", Consumer {
                                    print(it)
                                }, Consumer {
                                    print(it)
                                })
                                ipfs.pubsub.pub("topic", "mobile1")
                                ipfs.pubsub.pub("topic", "mobile2")
                                val results = sub.limit(2).collect(Collectors.toList())
                                uiThread {
                                    print(results)
                                }
                            }
                            true;
                        }

                    }
                }
            }.show()
        }
    }

    private fun setupActionBtn(notimpl: () -> Boolean) {
        actionbtn.setOnClickListener { btn ->
            PopupMenu(ctx, btn).apply {
                menu.apply {
                    add(getString(R.string.menu_add_file)).setOnMenuItemClickListener {
                        Intent(ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            startActivityForResult(createChooser(this, getString(R.string.title_add_file)), 1)
                        }; true
                    }

                    add(getString(R.string.menu_add_folder)).setOnMenuItemClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Intent(ACTION_OPEN_DOCUMENT_TREE).apply {
                                type = "*/*"
                                startActivityForResult(createChooser(this, getString(R.string.title_add_folder)), 2)
                            }
                        }; true
                    }

                    add(getString(R.string.menu_add_text)).setOnMenuItemClickListener {
                        AlertDialog.Builder(ctx).apply {
                            setTitle(getString(R.string.title_add_text))
                            val txt = EditText(ctx).apply {
                                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                                setView(this)
                            }
                            setPositiveButton(getString(R.string.apply)) { _, _ ->
                                Intent(ctx, ShareActivity::class.java).apply {
                                    type = "text/plain"
                                    putExtra(EXTRA_TEXT, txt.text.toString())
                                }
                            }
                            setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                        }.show(); true
                    }

                    add(getString(R.string.menu_garbage_collect)).setOnMenuItemClickListener {
                        true.also {
                            Thread {
                                ipfs.repo.gc()
                                runOnUiThread {
                                    AlertDialog.Builder(ctx)
                                            .setMessage(getString(R.string.garbage_collected)).show()
                                }
                            }.start()
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

    private fun setupFloatingButton() {
        floatingBtn.setOnClickListener {
            Snackbar.make(it, "Here's a Snackbar", Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .show()
        }
    }
}
