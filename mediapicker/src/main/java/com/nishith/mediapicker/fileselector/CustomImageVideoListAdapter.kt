package com.nishith.mediapicker.fileselector

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nishith.mediapicker.R
import com.nishith.mediapicker.data.FileEntry
import com.nishith.mediapicker.databinding.RowItemCustomImageVideoListItemBinding
import com.nishith.mediapicker.extention.hide
import com.nishith.mediapicker.extention.show
import com.nishith.mediapicker.fileselector.callback.FileEntryDiffCallback

open class CustomImageVideoListAdapter(
    private var items: List<FileEntry> = emptyList(),
    private val canSelectMultipleFlag: Boolean = false,
    private val canSelectMultipleVideo: Boolean = false,
    private val onClickListener: ((FileEntry) -> Unit)? = null
) : RecyclerView.Adapter<CustomImageVideoListAdapter.ViewHolder>() {

    protected var selectedItemPos = -1
    protected var lastItemSelectedPos = -1

    inner class ViewHolder(private val binding: RowItemCustomImageVideoListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileEntry) = with(binding) {
            Glide.with(imageViewPhoto.context)
                .load(item.uri)
                .placeholder(R.color.black)
                .into(imageViewPhoto)

            //Log.e("++++Type", item.mimeType)
            if (item.mimeType == "video/mp4") {
                imageViewThumb.show()
            } else {
                imageViewThumb.hide()
            }

            imageViewCheckBox.isSelected = item.isImageSelected

            root.setOnClickListener {
                if (canSelectMultipleFlag || canSelectMultipleVideo) {
                    item.isImageSelected = !item.isImageSelected
                    imageViewCheckBox.isSelected = !imageViewCheckBox.isSelected
                } else {
                    selectSingleItem(adapterPosition) { prevSelectedItem ->
                        prevSelectedItem.isImageSelected = false
                    }
                    item.isImageSelected = true
                    notifyItemChanged(adapterPosition)
                }
                onClickListener?.invoke(item)
            }
        }

        // Helper method to handle single item selection logic
        private fun selectSingleItem(
            adapterPosition: Int, onSingleItemSelected: (prevSelectedItem: FileEntry) -> Unit
        ) {
            selectedItemPos = adapterPosition
            if (lastItemSelectedPos == -1) {
                lastItemSelectedPos = selectedItemPos
            } else if (lastItemSelectedPos != selectedItemPos) {
                onSingleItemSelected(items[lastItemSelectedPos])
                notifyItemChanged(lastItemSelectedPos)
                lastItemSelectedPos = selectedItemPos
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowItemCustomImageVideoListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<FileEntry> = items

    fun updateItems(newItems: List<FileEntry>) {
        val diffCallback = FileEntryDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }
}
