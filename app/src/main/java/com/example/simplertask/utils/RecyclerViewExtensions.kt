package com.example.simplertask.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.setupVertical(adapter: RecyclerView.Adapter<*>) {
    this.layoutManager = LinearLayoutManager(this.context)
    this.adapter = adapter
    this.setHasFixedSize(true)
}
