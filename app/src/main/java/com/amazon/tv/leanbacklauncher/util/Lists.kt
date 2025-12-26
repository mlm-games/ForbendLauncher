package com.amazon.tv.leanbacklauncher.util

object Lists {

    data class Change(
        val type: Type,
        val index: Int,
        val count: Int = 1
    ) {
        enum class Type { INSERTION, REMOVAL }

        override fun toString() = buildString {
            append(type.name.lowercase())
            append("@$index")
            if (count > 1) append("x$count")
        }
    }

    private data class Cursor<T>(
        var element: T? = null,
        var index: Int = 0
    )

    fun <T> getChanges(left: List<T>, right: List<T>, comparator: Comparator<T>): List<Change> {
        val changes = mutableListOf<Change>()
        val lSize = left.size
        val rSize = right.size
        var offset = 0
        
        val l = Cursor<T>()
        val r = Cursor<T>()
        val lookAhead = Cursor<T>()

        while (l.index < lSize && r.index < rSize) {
            l.element = left[l.index]
            r.element = right[r.index]

            if (l.element == r.element) {
                l.index++
                r.index++
                continue
            }

            val comparison = comparator.compare(l.element, r.element)
            
            when {
                comparison < 0 -> {
                    val count = scanWhileLessThan(left, l, r, comparator)
                    changes.add(Change(Change.Type.REMOVAL, l.index + offset, count))
                    offset -= count
                    l.index += count
                }
                comparison > 0 -> {
                    val count = scanWhileLessThan(right, r, l, comparator)
                    changes.add(Change(Change.Type.INSERTION, l.index + offset, count))
                    offset += count
                    r.index += count
                }
                else -> {
                    lookAhead.index = r.index
                    var count = scanForElement(right, l, comparator, lookAhead)
                    
                    if (lookAhead.element != null) {
                        changes.add(Change(Change.Type.INSERTION, l.index + offset, count))
                        offset += count
                        l.index++
                        r.index = lookAhead.index + 1
                    } else {
                        lookAhead.index = l.index
                        count = scanForElement(left, r, comparator, lookAhead)
                        changes.add(Change(Change.Type.REMOVAL, l.index + offset, count))
                        offset -= count
                        l.index += count
                    }
                }
            }
        }

        when {
            l.index < lSize -> changes.add(Change(Change.Type.REMOVAL, l.index + offset, lSize - l.index))
            r.index < rSize -> changes.add(Change(Change.Type.INSERTION, lSize + offset, rSize - r.index))
        }

        return changes
    }

    private fun <T> scanWhileLessThan(
        list: List<T>,
        cursor: Cursor<T>,
        reference: Cursor<T>,
        comparator: Comparator<T>
    ): Int {
        var i = cursor.index
        while (++i < list.size && comparator.compare(list[i], reference.element) < 0) {
            // Continue scanning
        }
        return i - cursor.index
    }

    private fun <T> scanForElement(
        list: List<T>,
        reference: Cursor<T>,
        comparator: Comparator<T>,
        outLookAhead: Cursor<T>
    ): Int {
        val startIndex = outLookAhead.index
        
        while (++outLookAhead.index < list.size) {
            outLookAhead.element = list[outLookAhead.index]
            
            if (reference.element == outLookAhead.element) {
                return outLookAhead.index - startIndex
            }
            
            if (comparator.compare(reference.element, outLookAhead.element) != 0) {
                break
            }
        }
        
        outLookAhead.element = null
        return outLookAhead.index - startIndex
    }
}