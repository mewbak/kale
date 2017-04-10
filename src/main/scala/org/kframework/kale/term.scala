package org.kframework.kale

import org.kframework.minikore.interfaces.{pattern, tree}

import scala.collection._

trait Label extends MemoizedHashCode with pattern.Symbol {
  val env: Environment

  val name: String

  val id: Int = env.register(this)

  override def equals(other: Any): Boolean = other match {
    case that: Label => this.name == that.name
    case _ => false
  }

  override def computeHashCode: Int = name.hashCode

  override def toString: String = name

  // FOR KORE
  def str: String = name
}

trait Term extends Iterable[Term] with pattern.Pattern {
  def updateAt(i: Int)(t: Term): Term

  val label: Label

  val isGround: Boolean

  lazy val sort: Sort = label.env.sort(label, this.toSeq)

  def iterator(): Iterator[Term]

  /**
    * This method is called after `oldTerm` is updated resulting in `this` term.
    * Subclasses can override the method to attach functionality related to updating, e.g., updating attributes.
    * Should return `this`.
    */
  def updatePostProcess(oldTerm: Term): Term = this

  // TODO: should experiment with other implementations
  override def hashCode: Int = this.label.hashCode

  def copy(children: Seq[Term]): Term
}

trait LeafLabel[T] extends Label {
  def apply(t: T): Term

  def unapply(t: Term): Option[T] = t match {
    case t: Leaf[T] if t.label == this => Some(t.value)
    case _ => None
  }
}

trait Leaf[T] extends Term {
  def iterator(): Iterator[Term] = Iterator.empty

  def updateAt(i: Int)(t: Term): Term = throw new IndexOutOfBoundsException("Leaves have no children. Trying to update index _" + i)

  val label: LeafLabel[T]
  val value: T

  override def toString: String = label + "(" + value.toString + ")"

  def copy(): Term = label(value).updatePostProcess(this)

  def copy(children: Seq[Term]): Term = {
    assert(children.isEmpty)
    copy()
  }
}

trait NodeLabel extends Label {
  def unapplySeq(t: Term): Option[Seq[Term]] = t match {
    case t: Node if t.label == this => Some(t.iterator().toSeq)
    case _ => None
  }

  val arity: Int

  def apply(l: Iterable[Term]): Term = if (l.size == arity) {
    constructFromChildren(l)
  } else {
    throw new AssertionError("Incorrect number of children for constructing a " + name + ". Expected: " + arity + " but found: " + l.size)
  }

  protected def constructFromChildren(l: Iterable[Term]): Term
}

trait Node extends Term with Product with tree.Node {
  // FOR KORE
  override def args: Seq[pattern.Pattern] = iterator().toSeq

  override def build(children: Seq[pattern.Pattern]): pattern.Pattern = {
    // downcasting to Term, but it will fail somewhere in Label
    copy(children.asInstanceOf[Seq[Term]])
  }

  // /FOR KORE

  val label: NodeLabel

  def updateAt(i: Int)(t: Term): Term = if (i < 0 || i >= productArity) {
    throw new IndexOutOfBoundsException(label + " has " + productArity + " children. Trying to update index _" + i)
  } else {
    innerUpdateAt(i, t)
  }

  protected def innerUpdateAt(i: Int, t: Term): Term

  def iterator(): Iterator[Term]

  override def toString: String = label + "(" + iterator().mkString(", ") + ")"

  def copy(children: Seq[Term]): Term
}

object Node {
  def unapply(t: Term): Option[(NodeLabel, Iterator[Term])] = t match {
    case t: Node => Some(t.label, t.iterator())
    case _ => None
  }
}