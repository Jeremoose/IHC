package com.novodin.ihc.placeholder

import com.novodin.ihc.model.Article
import com.novodin.ihc.model.QuantityType
import java.util.ArrayList
import java.util.HashMap

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 *
 * TODO: Replace all uses of this class before publishing your app.
 */
object PlaceholderContent {

    /**
     * An array of sample (placeholder) items.
     */
    val ARTICLE_LIST: MutableList<Article> = ArrayList()

    private val COUNT = 25

    private val BARCODES = arrayOf("301234018234", "2034812034", "023894012834", "023840134")
    private val NUMBER = arrayOf("moriqewr02394", "nmo435j049385", "93r24r0qj23r", "m320r9p3-")
    private val ARTICLES = arrayOf("EMY-64M", "Schlegel PRJSNGL-24V", "FBR51ND12-W1", "GJE CMA31DC", "HKE CMA31-DC", "Hongfa HKDF/012")
    private val QUANTITY_TYPE = arrayOf(QuantityType.ITEM, QuantityType.PU, QuantityType.UNIT)

    init {
        // Add some sample items.
        for (i in 1..COUNT) {
            addItem(createPlaceholderItem())
        }
    }

    private fun addItem(article: Article) {
        ARTICLE_LIST.add(article)
    }

    fun createPlaceholderItem(): Article {
        val randomId = (0..100).random()
        val randomBarcode = (0..3).random()
        val randomNumber = (0..3).random()
        val randomCount = (0..99).random()
        val randomArticle = (0..5).random()
        val randomQuantityType = (0..2).random()
        return Article(randomId, BARCODES[randomBarcode], ARTICLES[randomArticle], NUMBER[randomNumber], QUANTITY_TYPE[randomQuantityType], 1)
    }
}