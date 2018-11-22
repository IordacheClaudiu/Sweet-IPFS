package fr.rhaz.ipfs.sweet

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.format.Formatter
import android.widget.EditText
import android.widget.PopupMenu
import kotlinx.android.synthetic.main.activity_console.*
import android.text.InputType
import android.util.Log
import io.ipfs.api.Peer
import io.ipfs.multiaddr.MultiAddress
import android.widget.LinearLayout


class ConsoleActivity : AppCompatActivity() {
    private val LOGTAG = ConsoleActivity::class.java.name

    private val ctx = this as Context

    override fun onBackPressed() {}

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {

        setContentView(R.layout.activity_console)

        input.setOnEditorActionListener { textview, i, ev ->
            true.also {
                val cmd = input.text.toString()
                console.apply {
                    text = "${console.text}\n> $cmd"
                    post {
                        val y = layout.getLineTop(lineCount) - height
                        if (y > 0) scrollTo(0, y)
                    }
                }
                Thread {
                    ipfsDaemon.run(cmd).apply {
                        inputStream.bufferedReader().readLines().forEach {
                            runOnUiThread {
                                console.apply {
                                    text = "${console.text}\n$it"
                                    post {
                                        val y = layout.getLineTop(lineCount) - height
                                        if (y > 0) scrollTo(0, y)
                                    }
                                }
                            }
                        }
                        errorStream.bufferedReader().readLines().forEach {
                            runOnUiThread {
                                console.apply {
                                    text = "${console.text}\n$it"
                                    post {
                                        val y = layout.getLineTop(lineCount) - height
                                        if (y > 0) scrollTo(0, y)
                                    }
                                }
                            }
                        }
                    }
                }.start()
                input.text.clear()
            }
        }

        val notimpl = { AlertDialog.Builder(ctx).setMessage("This feature is not yet implemented. Sorry").show(); true }

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
                            setPositiveButton(getString(R.string.apply)) { d, _ ->
                                Intent(ctx, ShareActivity::class.java).apply {
                                    type = "text/plain"
                                    putExtra(EXTRA_TEXT, txt.text.toString())
                                }
                            }
                            setNegativeButton(getString(R.string.cancel)) { d, _ -> }
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

        configbtn.setOnClickListener {
            PopupMenu(ctx, it).apply {
                menu.apply {
                    addSubMenu(getString(R.string.menu_identity)).apply {
                        add(getString(R.string.menu_identity_peerid)).setOnMenuItemClickListener {
                            val id = ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PeerID").asString
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.title_peerid))
                                setMessage(id)
                                setPositiveButton(getString(R.string.copy)) { d, _ -> }
                                setNeutralButton(getString(R.string.close)) { d, _ -> }
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
                                setPositiveButton(getString(R.string.copy)) { d, _ -> }
                                setNeutralButton(getString(R.string.close)) { d, _ -> }
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
                                setNeutralButton(getString(R.string.close)) { d, _ -> }
                            }.show()
                            Log.d(LOGTAG, "Swarm Peers success.")
                        }, {
                            Log.e(LOGTAG, "Swarm Peers error.")
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
                                Log.d(LOGTAG, "Bootstrap list success.")
                            }, {
                                Log.d(LOGTAG, "Bootstrap list error.")
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
                                        Log.d(LOGTAG, "Bootstrap add node error:" + e.localizedMessage)
                                        return@setPositiveButton
                                    }
                                    async(60, { ipfs.bootstrap.add(nodeAddress) },
                                            {
                                                Log.d(LOGTAG, "Bootstrap add node success.")
                                            }, {
                                        Log.d(LOGTAG, "Bootstrap add node error.")
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

                                Log.d(LOGTAG, "PubSub list rooms success.")
                            }, {

                                Log.d(LOGTAG, "PubSub list rooms error.")
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
                                                Log.d(LOGTAG, "PubSub join room succedeed.")
                                            }, {
                                        Log.d(LOGTAG, "PubSub join room error.")
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
                                                { Log.d(LOGTAG, "PubSub post to room $room succedeed.") },
                                                { Log.d(LOGTAG, "PubSub post room ${room} failed.") }
                                        )
                                    } else {

                                    }
                                }
                                setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                            }.show(); true
                        }

                    }
                }
            }.show()
        }

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

    fun Long.format() = Formatter.formatFileSize(ctx, this)

}
