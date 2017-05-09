package org.kframework.kale.pretty

import org.kframework.kale.builtin.HasSTRING
import org.kframework.kale.util.Named
import org.kframework.kale._

class PrettyWrapperLabel(implicit envv: Environment with HasPretty) extends Named("œ") with Label3 {

  import env._

  override def apply(_1: Term, _2: Term, _3: Term): Term = {
    assert(_1.label == STRING)
    assert(_2.label != this)
    assert(_3.label == STRING)
    PrettyWrapperHolder(this, _1, _2, _3)
  }

  val self = this

  def unwrap(t: Term) = t match {
    case self(_, c, _) => c
    case _ => t
  }
}

case class PrettyWrapperHolder(label: PrettyWrapperLabel, prefix: Term, content: Term, suffix: Term) extends Node3 with IsNotPredicate {
  override def toString = "⦅" + _1.toString + "|" + _2 + "|" + _3 + "⦆"

  override def _1: Term = prefix

  override def _2: Term = content

  override def _3: Term = suffix
}

trait HasPretty extends HasSTRING {
  self: Environment =>

  val PrettyWrapper = new PrettyWrapperLabel()(this)

  def pretty(t: Term): String

  implicit class PrettyTerm(t: Term) {
    def pretty: String = HasPretty.this.pretty(t)
  }

}

