package com.nfrdev.blockblitzhost.blockblitz


data class Brick(val location: Point = Point.of(0, 0)) {
    companion object {
        fun of(pointList: List<Point>) = pointList.map { Brick(it) }

        fun of(spirit: Spirit) = of(spirit.location)

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
