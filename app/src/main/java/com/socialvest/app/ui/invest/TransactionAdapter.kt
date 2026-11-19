package com.socialvest.app.ui.invest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.socialvest.app.R
import com.socialvest.app.data.model.Transaction
import com.socialvest.app.data.model.TransactionType
import com.socialvest.app.data.repository.dateMillis
import com.socialvest.app.databinding.ItemTransactionBinding
import com.socialvest.app.utils.Display
import com.socialvest.app.utils.Money
import com.socialvest.app.utils.Quantity
import java.util.Locale

/** Transaction history rows. Deleting is confirmed by the Activity. */
class TransactionAdapter(
    private val onDelete: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding, onDelete)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TransactionViewHolder(
        private val binding: ItemTransactionBinding,
        private val onDelete: (Transaction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            val context = binding.root.context
            val isBuy = transaction.type == TransactionType.BUY

            val gross = Quantity.multiplyToCents(transaction.quantity, transaction.pricePerUnit)
            val amount = if (isBuy) gross + transaction.fee else gross - transaction.fee

            binding.textType.text = context.getString(
                if (isBuy) R.string.transaction_buy_short else R.string.transaction_sell_short
            ).uppercase(Locale.getDefault())
            binding.textType.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isBuy) R.color.gain else R.color.loss
                )
            )

            binding.textDate.text = Display.date(transaction.dateMillis())
            binding.textAmount.text = Money.format(amount)

            binding.textDetail.text = context.getString(
                R.string.transaction_detail_format,
                Quantity.format(transaction.quantity),
                Money.format(transaction.pricePerUnit)
            )

            val meta = buildList {
                if (transaction.fee > 0L) {
                    add(context.getString(R.string.transaction_fee_format, Money.format(transaction.fee)))
                }
                if (transaction.notes.isNotBlank()) add(transaction.notes)
            }.joinToString(" · ")
            binding.textMeta.text = meta
            binding.textMeta.visibility = if (meta.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            binding.buttonDelete.setOnClickListener { onDelete(transaction) }
            binding.buttonDelete.contentDescription = context.getString(R.string.delete)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean =
                oldItem == newItem
        }
    }
}
