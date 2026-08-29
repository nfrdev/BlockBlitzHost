package com.nfrdev.blockblitzhost.blockblitz


data class Brick(
    val location: Point = Point.of(0, 0),
    val colorIndex: Int = 0
) {
    companion object {
        fun of(pointList: List<Point>, colorIndex: Int = 0) = pointList.map { Brick(it, colorIndex) }

        fun of(spirit: Spirit) = spirit.location.map { Brick(it, spirit.colorIndex) }

        fun of(xRange: IntRange, yRange: IntRange) =
            of(mutableListOf<Point>().apply {
                xRange.forEach { x ->
                    yRange.forEach { y ->
                        this += Point.of(x, y)
                    }
                }
            })

    }

    fun offsetBy(step: Pair<Int, Int>) =
        copy(location = Point(location.x + step.first, location.y + step.second))

}
