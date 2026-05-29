package com.example.expensetracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ExpenseDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "expenses.db"
        private const val DB_VERSION = 1
        const val TABLE = "expenses"
        const val COL_ID = "id"
        const val COL_CATEGORY = "category"
        const val COL_AMOUNT = "amount"
        const val COL_SOURCE = "source"
        const val COL_CHANNEL = "channel"
        const val COL_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CATEGORY TEXT NOT NULL,
                $COL_AMOUNT REAL,
                $COL_SOURCE TEXT,
                $COL_CHANNEL TEXT,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun insertExpense(category: String, amount: Double, source: String, channel: String = "unknown"): Long {
        val values = ContentValues().apply {
            put(COL_CATEGORY, category)
            put(COL_AMOUNT, amount)
            put(COL_SOURCE, source)
            put(COL_CHANNEL, channel)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE, null, values)
    }
}
