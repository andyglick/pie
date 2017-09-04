package mb.ceres.impl

import mb.ceres.Out
import mb.ceres.OutputStamp
import mb.ceres.OutputStamper

class EqualsOutputStamper : OutputStamper {
  override fun <O : Out> stamp(output: O): OutputStamp {
    return ValueOutputStamp(output, this)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other?.javaClass != javaClass) return false
    return true
  }

  override fun hashCode(): Int {
    return 0
  }
}

class InconsequentialOutputStamper : OutputStamper {
  companion object {
    val instance = InconsequentialOutputStamper()
    val inconsequentialStamp = ValueOutputStamp(null, instance)
  }

  override fun <O : Out> stamp(output: O): OutputStamp {
    return inconsequentialStamp
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other?.javaClass != javaClass) return false
    return true
  }

  override fun hashCode(): Int {
    return 0
  }
}

data class ValueOutputStamp<out V>(val value: V?, override val stamper: OutputStamper) : OutputStamp