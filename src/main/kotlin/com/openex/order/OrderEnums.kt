package com.openex.order

enum class OrderSide { BUY, SELL }

enum class OrderType { LIMIT, MARKET }

enum class OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED }
