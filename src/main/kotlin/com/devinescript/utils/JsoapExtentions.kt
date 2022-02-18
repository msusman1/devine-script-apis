package com.devinescript.utils

import org.jsoup.nodes.Element

fun <T> Sequence<T>.selectRecursive(recursiveSelector: T.() -> Sequence<T>): Sequence<T> = flatMap {
    sequence {
        yield(it)
        yieldAll(it.recursiveSelector().selectRecursive(recursiveSelector))
    }
}

fun Element.hasAnyChildClass(className: String): Boolean {
    val seq = children().asSequence().selectRecursive { children().asSequence() }
    seq.forEach {
        if (it.classNames().contains(className)) {
            return true
        }
    }

    return false
}

fun Element.childCountWithTag(tag: String): Int {
    val seq = children().asSequence().selectRecursive { children().asSequence() }
    var size = 0
    seq.forEach {
        if (it.tagName().equals(tag, true)) {
            size++
        }
    }

    return size
}

fun Element.hasAnyChildWithTag(tag: String): Boolean {
    val seq = children().asSequence().selectRecursive { children().asSequence() }
    seq.forEach {
        if (it.tagName().equals(tag, true)) {
            return true
        }
    }

    return false
}