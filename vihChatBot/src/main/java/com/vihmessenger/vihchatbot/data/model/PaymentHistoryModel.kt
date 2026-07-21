package com.vihmessenger.vihchatbot.data.model

data class PaymentHistoryModel(
    var paymentMethod: String,
    var paymentHistoryTxnId: String,
    var paymentHistoryTxnAmount: String,
    var paymentMethodCardType: String,
)