package com.nfrdev.blockblitzhost.blockblitz

data class Point(val x: Float, val y: Float) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)

    companion object {
        fun of(x: Int, y: Int) = Point(x.toFloat(), y.toFloat())
    }
}
