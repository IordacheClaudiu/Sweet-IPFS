package utils

import android.support.v7.widget.RecyclerView
import android.view.View

class RVEmptyObserver(private val recyclerView: RecyclerView , private val emptyView: View) : RecyclerView.AdapterDataObserver() {

    init {
        checkIfEmpty()
    }

    private fun checkIfEmpty() {
        var emptyViewVisible = recyclerView.adapter.itemCount == 0
        when (emptyViewVisible) {
            true -> {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
            false -> {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    override fun onChanged() {
        checkIfEmpty()
    }

    override fun onItemRangeInserted(positionStart: Int , itemCount: Int) {
        checkIfEmpty()
    }

    override fun onItemRangeRemoved(positionStart: Int , itemCount: Int) {
        checkIfEmpty()
    }

}