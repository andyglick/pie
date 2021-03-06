package mb.pie.api

import java.io.Serializable

/**
 * Type for task outputs. It must adhere to the following properties:
 *
 * * Implements [Serializable].
 * * Implements [equals][Object.equals] and [hashCode][Object.hashCode].
 *
 * Failure to adhere to these properties will cause unsound incrementality.
 */
typealias Out = Serializable?


/**
 * Wrapper for transient outputs; outputs that cannot be serialized. A transient output will be recreated when an attempt is made to
 * deserialize it, and then cached.
 */
interface OutTransient<out T : Any?> : Out {
  val v: T
  val consistent: Boolean
}

data class OutTransientImpl<out T : Any?>(
  @Transient override val v: T,
  @Transient override val consistent: Boolean = false
) : OutTransient<T> {
  constructor(v: T) : this(v, true)
}


/**
 * Specialization of [OutTransient], where a serializable value [e] is used for change detection through equality.
 */
interface OutTransientEquatable<out T : Any?, out E : Out> : OutTransient<T> {
  val e: E
}

data class OutTransientEquatableImpl<out T : Any?, out E : Out>(
  @Transient override val v: T,
  @Transient override val consistent: Boolean = false,
  override val e: E
) : OutTransientEquatable<T, E> {
  constructor(v: T, e: E) : this(v, true, e)
}
