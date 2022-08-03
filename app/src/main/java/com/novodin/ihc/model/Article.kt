package com.novodin.ihc.model

data class Article(val id: Int, val barcode: String, val name: String, val number: String, val quantityType: QuantityType, var count: Int)
