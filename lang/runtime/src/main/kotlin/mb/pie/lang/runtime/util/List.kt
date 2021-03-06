package mb.pie.lang.runtime.util

import mb.pie.api.Out
import java.util.*

fun <T : Out> list(vararg elements: T): ArrayList<T> {
  val list = ArrayList<T>()
  list.addAll(elements)
  return list
}

operator fun <T : Out> ArrayList<T>.plus(other: T): ArrayList<T> {
  val list = ArrayList<T>(this)
  list.add(other)
  return list
}

operator fun <T : Out> ArrayList<T>.plus(other: ArrayList<T>): ArrayList<T> {
  val list = ArrayList<T>(this)
  list.addAll(other)
  return list
}
