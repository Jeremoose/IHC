package com.novodin.ihc.model

enum class QuantityType {
    ITEM, PU, UNIT;

    companion object {
        fun fromInt(value: Int): QuantityType {
            return when (value) {
                1 -> ITEM
                2 -> PU
                3 -> UNIT
                else -> {
                    ITEM
                }
            }
        }
    }
}