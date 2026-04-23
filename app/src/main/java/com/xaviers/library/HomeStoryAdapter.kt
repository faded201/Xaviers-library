package com.xaviers.library

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xaviers.library.databinding.ItemHomeStoryBinding

class HomeStoryAdapter(
    private val onClick: (HomeRailItem) -> Unit
) : RecyclerView.Adapter<HomeStoryAdapter.HomeStoryViewHolder>() {

    private var items: List<HomeRailItem> = emptyList()
    private var accentColor: Int = 0

    fun submit(items: List<HomeRailItem>, accentColor: Int) {
        this.items = items
        this.accentColor = accentColor
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeStoryViewHolder {
        val binding = ItemHomeStoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HomeStoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeStoryViewHolder, position: Int) {
        holder.bind(items[position], accentColor, onClick)
    }

    override fun getItemCount(): Int = items.size

    class HomeStoryViewHolder(
        private val binding: ItemHomeStoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeRailItem, accentColor: Int, onClick: (HomeRailItem) -> Unit) {
            val context = binding.root.context
            binding.homeStoryGenre.text = item.genre
            binding.homeStoryTitle.text = item.title
            binding.homeStoryMeta.text = item.meta
            binding.homeStoryHook.text = item.hookLine
            binding.homeStoryGenre.backgroundTintList = ColorStateList.valueOf(accentColor)
            binding.homeStoryCard.strokeColor = accentColor
            binding.homeStoryTitle.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
