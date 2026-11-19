package com.socialvest.app.ui.invest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.R
import com.socialvest.app.data.model.PriceSource
import com.socialvest.app.databinding.ItemAssetBinding
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Money
import com.socialvest.app.utils.Quantity
import com.socialvest.app.viewmodel.AssetRow

/** Asset list rows. The numbers come pre-computed from the ViewModel. */
class AssetAdapter(
    private val onAssetClick: (assetId: String) -> Unit
) : ListAdapter<AssetRow, AssetAdapter.AssetViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemAssetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AssetViewHolder(binding, onAssetClick)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AssetViewHolder(
        private val binding: ItemAssetBinding,
        private val onAssetClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: AssetRow) {
            val context = binding.root.context
            val asset = row.asset

            binding.textName.text = asset.assetName
            binding.textTicker.text = buildList {
                add(asset.ticker)
                add(context.getString(asset.category.labelRes))
                if (asset.hasAutomaticPrice) {
                    add(
                        context.getString(
                            when (asset.priceSource) {
                                PriceSource.COINGECKO -> R.string.price_source_coingecko
                                PriceSource.YAHOO -> R.string.price_source_yahoo
                                PriceSource.MANUAL -> R.string.asset_detail_current_price
                            }
                        )
                    )
                }
            }.filter { it.isNotBlank() }.joinToString(" · ")

            binding.textValue.text = Display.rupiah(row.marketValue)
            binding.textQuantity.text =
                context.getString(R.string.invest_qty_format, Quantity.format(asset.quantity))

            binding.textCategory.text = context.getString(asset.category.labelRes)

            binding.textAverage.text = if (asset.quantity > 0L) {
                context.getString(
                    R.string.invest_avg_format,
                    Money.format(asset.averageBuyPrice)
                )
            } else {
                context.getString(R.string.value_undefined)
            }

            val percent = Display.percent(row.returnPercent)
            binding.textPl.text = buildString {
                append(Display.signedRupiah(row.unrealizedPl))
                if (percent != null) {
                    append(" (")
                    append(percent)
                    append(')')
                }
            }
            binding.textPl.setTextColor(
                ContextCompat.getColor(context, Display.plColorRes(row.unrealizedPl))
            )

            binding.root.contentDescription = buildString {
                append(asset.assetName)
                append(", ")
                append(binding.textValue.text)
            }
            binding.root.setOnClickListener { onAssetClick(asset.id) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AssetRow>() {
            override fun areItemsTheSame(oldItem: AssetRow, newItem: AssetRow): Boolean =
                oldItem.asset.id == newItem.asset.id

            override fun areContentsTheSame(oldItem: AssetRow, newItem: AssetRow): Boolean =
                oldItem == newItem
        }
    }
}
