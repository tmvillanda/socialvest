package com.socialvest.app.domain.price

/**
 * Turns a user-typed ticker into the symbol expected by a public price API.
 *
 * The app never guesses silently: when a ticker cannot be mapped, the asset is
 * reported as "tetap manual" in the refresh summary instead of receiving a
 * made-up price.
 */
object SymbolResolver {

    /**
     * Common crypto tickers -> CoinGecko ids. CoinGecko ids are not the same as
     * tickers (`BTC` -> `bitcoin`), so a small curated table covers the coins a
     * student portfolio is likely to hold. A ticker that is already a valid id
     * (e.g. `bitcoin`) passes through.
     */
    private val CRYPTO_IDS: Map<String, String> = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "BNB" to "binancecoin",
        "SOL" to "solana",
        "XRP" to "ripple",
        "ADA" to "cardano",
        "DOGE" to "dogecoin",
        "TRX" to "tron",
        "AVAX" to "avalanche-2",
        "DOT" to "polkadot",
        "MATIC" to "matic-network",
        "POL" to "polygon-ecosystem-token",
        "LTC" to "litecoin",
        "BCH" to "bitcoin-cash",
        "LINK" to "chainlink",
        "XLM" to "stellar",
        "ATOM" to "cosmos",
        "UNI" to "uniswap",
        "ETC" to "ethereum-classic",
        "XMR" to "monero",
        "NEAR" to "near",
        "APT" to "aptos",
        "ARB" to "arbitrum",
        "OP" to "optimism",
        "SUI" to "sui",
        "TON" to "the-open-network",
        "SHIB" to "shiba-inu",
        "PEPE" to "pepe",
        "USDT" to "tether",
        "USDC" to "usd-coin",
        "DAI" to "dai",
        "AAVE" to "aave",
        "FIL" to "filecoin",
        "ICP" to "internet-computer",
        "HBAR" to "hedera-hashgraph",
        "VET" to "vechain",
        "ALGO" to "algorand",
        "INJ" to "injective-protocol",
        "TIA" to "celestia",
        "SEI" to "sei-network",
        "STX" to "blockstack",
        "IMX" to "immutable-x",
        "GRT" to "the-graph",
        "SAND" to "the-sandbox",
        "MANA" to "decentraland",
        "AXS" to "axie-infinity",
        "EOS" to "eos",
        "XTZ" to "tezos",
        "THETA" to "theta-token",
        "RUNE" to "thorchain",
        "KAS" to "kaspa",
        "WIF" to "dogwifcoin",
        "BONK" to "bonk",
        "JUP" to "jupiter-exchange-solana",
        "PYTH" to "pyth-network",
        "WLD" to "worldcoin-wld",
        "ONDO" to "ondo-finance",
        "ENA" to "ethena",
        "TAO" to "bittensor"
    )

    private val CRYPTO_SLUG = Regex("[a-z0-9][a-z0-9-]{1,49}")

    /** CoinGecko id for a crypto ticker, or `null` when it cannot be mapped. */
    fun cryptoId(ticker: String): String? {
        val normalized = ticker.trim()
        if (normalized.isEmpty()) return null

        CRYPTO_IDS[normalized.uppercase()]?.let { return it }

        val slug = normalized.lowercase()
        return slug.takeIf { CRYPTO_SLUG.matches(it) }
    }

    /**
     * Yahoo Finance candidates for a stock, most likely first:
     * - an explicit suffix (`.JK`) or a stored symbol is used as-is;
     * - otherwise the Indonesian listing is tried first, then the bare ticker.
     */
    fun stockCandidates(ticker: String, preferredSymbol: String? = null): List<String> {
        val preferred = preferredSymbol?.trim()?.uppercase().orEmpty()
        if (preferred.isNotEmpty()) return listOf(preferred)

        val cleaned = ticker.trim().uppercase()
            .filter { it.isLetterOrDigit() || it == '.' || it == '-' }
        if (cleaned.isEmpty()) return emptyList()

        return if (cleaned.contains('.')) {
            listOf(cleaned)
        } else {
            listOf("$cleaned.JK", cleaned)
        }
    }

    /** Ticker to store after a successful refresh. */
    fun displayedSymbol(ticker: String, resolved: String): String =
        resolved.ifBlank { ticker.trim().uppercase() }
}
