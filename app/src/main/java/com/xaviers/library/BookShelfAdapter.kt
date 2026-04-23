package com.xaviers.library

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xaviers.library.databinding.ItemBookShelfBinding

class BookShelfAdapter(
    private val onClick: (BookShelfEntry) -> Unit
) : RecyclerView.Adapter<BookShelfAdapter.BookShelfViewHolder>() {

    private var items: List<BookShelfEntry> = emptyList()
    private var selectedBookId: Int = -1
    private var accentColor: Int = 0

    fun submit(items: List<BookShelfEntry>, selectedBookId: Int, accentColor: Int) {
        this.items = items
        this.selectedBookId = selectedBookId
        this.accentColor = accentColor
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookShelfViewHolder {
        val binding = ItemBookShelfBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookShelfViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookShelfViewHolder, position: Int) {
        holder.bind(items[position], selectedBookId, accentColor, onClick)
    }

    override fun getItemCount(): Int = items.size

    class BookShelfViewHolder(
        private val binding: ItemBookShelfBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            entry: BookShelfEntry,
            selectedBookId: Int,
            accentColor: Int,
            onClick: (BookShelfEntry) -> Unit
        ) {
            val context = binding.root.context
            val isSelected = entry.id == selectedBookId
            val isFocused = entry.isFocused

            binding.bookShelfCode.text = entry.tomeCode
            binding.bookShelfTitle.text = entry.title
            binding.bookShelfMeta.text = "${entry.arcName} • ${entry.sceneSignature}"
            binding.bookShelfSeed.text = entry.seedLine
            binding.bookShelfState.text = when {
                isSelected && isFocused -> context.getString(R.string.book_state_selected_focused)
                isSelected -> context.getString(R.string.book_state_selected)
                isFocused -> context.getString(R.string.book_state_focused)
                else -> context.getString(R.string.book_state_shelved)
            }

            val strokeColor = when {
                isSelected -> accentColor
                isFocused -> ContextCompat.getColor(context, R.color.tome_awakened)
                else -> ContextCompat.getColor(context, R.color.card_stroke)
            }
            val backgroundColor = when {
                isSelected -> ContextCompat.getColor(context, R.color.card_surface_selected)
                isFocused -> ContextCompat.getColor(context, R.color.card_surface_focused)
                else -> ContextCompat.getColor(context, R.color.card_surface_soft)
            }

            binding.bookShelfCard.strokeColor = strokeColor
            binding.bookShelfCard.setCardBackgroundColor(backgroundColor)
            binding.bookShelfCode.setTextColor(
                if (isSelected) accentColor else ContextCompat.getColor(context, R.color.text_tertiary)
            )
            binding.bookShelfState.backgroundTintList = ColorStateList.valueOf(strokeColor)
            binding.bookShelfState.setTextColor(
                ContextCompat.getColor(context, R.color.nav_selected_text)
            )

            binding.root.setOnClickListener { onClick(entry) }
        }
    }
}
