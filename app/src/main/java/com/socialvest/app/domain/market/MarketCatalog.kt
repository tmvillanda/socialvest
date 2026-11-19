package com.socialvest.app.domain.market

import com.socialvest.app.data.model.AssetCategory

/** Public source that can price one instrument. */
enum class MarketProvider { YAHOO_STOCK, COINGECKO }

/**
 * One searchable symbol. The catalogue is deliberately static: it is a list of
 * instruments, not a price feed. Prices are only fetched when the user asks for
 * them and are always labelled with their source.
 */
data class MarketInstrument(
    val name: String,
    val ticker: String,
    val category: AssetCategory,
    val provider: MarketProvider,
    /** Yahoo symbol (`BBCA.JK`) or CoinGecko id (`bitcoin`). */
    val quoteKey: String
)

/**
 * Curated IDX stocks and crypto coins. Kept pure so filtering is unit-testable
 * without Android or network access.
 *
 * Crypto tickers must stay in sync with [com.socialvest.app.domain.price.SymbolResolver];
 * a unit test asserts that mapping, so the app never shows a symbol that the
 * "Refresh semua" flow cannot price later.
 */
object MarketCatalog {

    private val stocks = listOf(
        stock("Bank Central Asia", "BBCA"),
        stock("Bank Rakyat Indonesia", "BBRI"),
        stock("Bank Mandiri", "BMRI"),
        stock("Bank Negara Indonesia", "BBNI"),
        stock("Bank Syariah Indonesia", "BRIS"),
        stock("Telkom Indonesia", "TLKM"),
        stock("Astra International", "ASII"),
        stock("GoTo Gojek Tokopedia", "GOTO"),
        stock("Aneka Tambang", "ANTM"),
        stock("Adaro Energy", "ADRO"),
        stock("Merdeka Copper Gold", "MDKA"),
        stock("Indofood CBP Sukses Makmur", "ICBP"),
        stock("Indofood Sukses Makmur", "INDF"),
        stock("Unilever Indonesia", "UNVR"),
        stock("Kalbe Farma", "KLBF"),
        stock("Semen Indonesia", "SMGR"),
        stock("Chandra Asri Pacific", "TPIA"),
        stock("Barito Pacific", "BRPT"),
        stock("Perusahaan Gas Negara", "PGAS"),
        stock("Medco Energi Internasional", "MEDC"),
        stock("Bank Jago", "ARTO"),
        stock("Bukalapak", "BUKA"),
        stock("Sido Muncul", "SIDO"),
        stock("Sarana Menara Nusantara", "TOWR"),
        stock("Ciputra Development", "CTRA"),
        stock("Pakuwon Jati", "PWON")
    )

    private val crypto = listOf(
        coin("Bitcoin", "BTC", "bitcoin"),
        coin("Ethereum", "ETH", "ethereum"),
        coin("BNB", "BNB", "binancecoin"),
        coin("Solana", "SOL", "solana"),
        coin("XRP", "XRP", "ripple"),
        coin("Cardano", "ADA", "cardano"),
        coin("Dogecoin", "DOGE", "dogecoin"),
        coin("TRON", "TRX", "tron"),
        coin("Avalanche", "AVAX", "avalanche-2"),
        coin("Polkadot", "DOT", "polkadot"),
        coin("Polygon (MATIC)", "MATIC", "matic-network"),
        coin("Polygon Ecosystem Token", "POL", "polygon-ecosystem-token"),
        coin("Chainlink", "LINK", "chainlink"),
        coin("Litecoin", "LTC", "litecoin"),
        coin("Bitcoin Cash", "BCH", "bitcoin-cash"),
        coin("Stellar", "XLM", "stellar"),
        coin("Cosmos", "ATOM", "cosmos"),
        coin("Uniswap", "UNI", "uniswap"),
        coin("NEAR Protocol", "NEAR", "near"),
        coin("Aptos", "APT", "aptos"),
        coin("Arbitrum", "ARB", "arbitrum"),
        coin("Optimism", "OP", "optimism"),
        coin("Sui", "SUI", "sui"),
        coin("Toncoin", "TON", "the-open-network"),
        coin("Shiba Inu", "SHIB", "shiba-inu")
    )

    val instruments: List<MarketInstrument> = stocks + crypto

    /**
     * Case-insensitive search over ticker and name. Exact tickers rank first so
     * typing `BBCA` never buries the matching row under a name match.
     */
    fun search(query: String, category: AssetCategory? = null): List<MarketInstrument> {
        val needle = query.trim().lowercase()
        return instruments
            .asSequence()
            .filter { category == null || it.category == category }
            .mapNotNull { instrument ->
                val score = score(instrument, needle)
                if (score == null) null else instrument to score
            }
            .sortedWith(compareBy({ it.second }, { it.first.name }))
            .map { it.first }
            .toList()
    }

    private fun score(instrument: MarketInstrument, needle: String): Int? {
        if (needle.isEmpty()) return 0
        val ticker = instrument.ticker.lowercase()
        val name = instrument.name.lowercase()
        return when {
            ticker == needle -> 0
            ticker.startsWith(needle) -> 1
            name.startsWith(needle) -> 2
            name.contains(needle) -> 3
            ticker.contains(needle) -> 4
            else -> null
        }
    }

    private fun stock(name: String, ticker: String) = MarketInstrument(
        name = name,
        ticker = ticker,
        category = AssetCategory.STOCK,
        provider = MarketProvider.YAHOO_STOCK,
        quoteKey = "$ticker.JK"
    )

    private fun coin(name: String, ticker: String, coinGeckoId: String) = MarketInstrument(
        name = name,
        ticker = ticker,
        category = AssetCategory.CRYPTO,
        provider = MarketProvider.COINGECKO,
        quoteKey = coinGeckoId
    )
}
