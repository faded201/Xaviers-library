package com.xaviers.library

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xaviers.library.databinding.ItemVaultCardBinding

class VaultDeckAdapter : RecyclerView.Adapter<VaultDeckAdapter.VaultCardViewHolder>() {

    private var items: List<CardStack> = emptyList()
    private var accentColor: Int = 0

    fun submit(items: List<CardStack>, accentColor: Int) {
        this.items = items
        this.accentColor = accentColor
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultCardViewHolder {
        val binding = ItemVaultCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VaultCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VaultCardViewHolder, position: Int) {
        holder.bind(items[position], accentColor)
    }

    override fun getItemCount(): Int = items.size

    class VaultCardViewHolder(
        private val binding: ItemVaultCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CardStack, accentColor: Int) {
            val context = binding.root.context
            binding.vaultCardTier.text = item.tier.label
            binding.vaultCardTitle.text = item.archetype.name
            binding.vaultCardMeta.text = "${item.archetype.family.name.lowercase().replaceFirstChar { it.uppercase() }} • ${item.copies} copies"
            binding.vaultCardLore.text = item.archetype.lore
            binding.vaultCardFoil.text = item.archetype.holographicFinish

            val strokeColor = when (item.tier) {
                CollectorTier.COMMON -> ContextCompat.getColor(context, R.color.tome_dormant)
                CollectorTier.HERO -> ContextCompat.getColor(context, R.color.tome_stirring)
                CollectorTier.MYTHICAL -> ContextCompat.getColor(context, R.color.tome_awakened)
                CollectorTier.LEGENDARY -> ContextCompat.getColor(context, R.color.tome_unleashed)
                CollectorTier.IMMORTAL -> accentColor
            }

            binding.vaultCardRoot.strokeColor = strokeColor
            binding.vaultCardTier.backgroundTintList = ColorStateList.valueOf(strokeColor)
            binding.vaultCardTier.setTextColor(
                ContextCompat.getColor(context, R.color.nav_selected_text)
            )
        }
    }
}
