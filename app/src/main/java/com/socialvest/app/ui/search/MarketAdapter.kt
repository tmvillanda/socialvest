package com.socialvest.app.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.R
import com.socialvest.app.data.remote.MarketQuoteSource
import com.socialvest.app.databinding.ItemMarketBinding
import com.socialvest.app.utils.Display
import com.socialvest.app.viewmodel.MarketRow

/** Catalogue rows. Prices are labelled snapshots, never a live feed. */
class MarketAdapter(
    private val onInstrumentClick: (MarketRow) -> Unit
) : ListAdapter<MarketRow, MarketAdapter.MarketViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarketViewHolder {
        val binding = ItemMarketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MarketViewHolder(binding, onInstrumentClick)
    }

    override fun onBindViewHolder(holder: MarketViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MarketViewHolder(
        private val binding: ItemMarketBinding,
        private val onInstrumentClick: (MarketRow) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: MarketRow) {
            val context = binding.root.context
            val instrument = row.instrument
            val quote = row.quote

            binding.textName.text = instrument.name
            binding.textTicker.text = context.getString(
                R.string.search_ticker_format,
                instrument.ticker,
                context.getString(instrument.category.labelRes)
            )

            binding.textPrice.text = if (quote != null) {
                Display.rupiah(quote.priceSen)
            } else {
                context.getString(R.string.value_undefined)
            }

            binding.textSource.text = when {
                quote != null -> context.getString(
                    R.string.search_quote_source,
                    context.getString(
                        when (quote.source) {
                            MarketQuoteSource.YAHOO -> R.string.search_source_yahoo
                            MarketQuoteSource.COINGECKO -> R.string.search_source_coingecko
                        }
                    ),
                    Display.time(quote.fetchedAt)
                )

                row.unavailable -> context.getString(R.string.search_price_unavailable)
                else -> context.getString(R.string.search_price_missing)
            }

            binding.root.contentDescription = buildString {
                append(instrument.name)
                append(", ")
                append(binding.textPrice.text)
                append(", ")
                append(binding.textSource.text)
            }
            binding.root.setOnClickListener { onInstrumentClick(row) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MarketRow>() {
            override fun areItemsTheSame(oldItem: MarketRow, newItem: MarketRow): Boolean =
                oldItem.instrument.ticker == newItem.instrument.ticker

            override fun areContentsTheSame(oldItem: MarketRow, newItem: MarketRow): Boolean =
                oldItem == newItem
        }
    }
}
