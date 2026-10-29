package com.example.gastos.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "expenses", indices = [Index(value = ["importKey"], unique = true)])
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val amount: Double,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'Não informado'") val paymentMethod: String = "Não informado",
    @ColumnInfo(defaultValue = "'Manual'") val origin: String = "Manual",
    val importKey: String? = null
)
