package org.kframework.kale

import org.kframework.kale.standard.ReferenceLabel
import org.kframework.kale.util.LabelNamed
import cats._
import cats.implicits._

trait Convert[A, B] {
  def convert(a: A): B
}

trait Up[O] {
  def up(o: O): Term
}

trait Down[O] {
  def down(t: Term): Option[O]

  object extract {
    def unapply(t: Term): Option[O] = down(t)
  }
}

trait Isomorphism[A, B] {
  def to: A => B
  def from: B => A
}

object UpDown {
  final def apply[O](implicit instance: Up[O]): Up[O] = instance

  final def apply[O](f: O => Term): Up[O] = (o: O) => f(o)

  final implicit def updownFromLeafLabel[O](implicit l: LeafLabel[O]): UpDown[O] = new UpDown[O] {
    override def down(t: Term) = l.unapply(t)

    override def up(o: O) = l.apply(o)
  }
}

trait UpDown[O] extends Up[O] with Down[O]

trait MonoidLabeled[O[_]] {
  def monoidLabel: MonoidLabel
}

trait LiftedCatsMixin extends Mixin with highcats.Free {
  _: Environment =>

  def lift(funcName: String, func: Term => Term)(implicit oenv: Environment): Label1 =
    new LabelNamed(funcName) with Label1 with FunctionLabel {
      override def apply(_1: Term): Term = func(_1)

      override val isPredicate = Some(false)
    }

  def lift(funcName: String, func: (Term, Term) => Term)(implicit oenv: Environment): Label2 =
    new LabelNamed(funcName) with Label2 with FunctionLabel {
      override def apply(_1: Term, _2: Term): Term = func(_1, _2)

      override lazy val isPredicate = Some(false)
    }

  def lift(funcName: String, func: Term => Option[Term], isPred: Option[Boolean])(implicit oenv: Environment): Label1 =
    new LabelNamed(funcName) with FunctionLabel1 {
      override def f(_1: Term): Option[Term] = func(_1)

      override lazy val isPredicate = isPred
    }

  def monoid[O: Monoid : UpDown](name: String) = PrimitiveMonoid(name)

  def define[A: UpDown, B: UpDown, R: UpDown](name: String, f: (A, B) => R): PrimitiveFunction2[A, B, R] =
    PrimitiveFunction2(name, implicitly[UpDown[A]], implicitly[UpDown[B]], implicitly[Up[R]], f)

  def define[A: UpDown, B: UpDown, C: UpDown, R: UpDown](name: String, f: (A, B, C) => R): PrimitiveFunction3[A, B, C, R] =
    PrimitiveFunction3(name, implicitly[UpDown[A]], implicitly[UpDown[B]], implicitly[UpDown[C]], implicitly[Up[R]], f)

  def define[A: UpDown, R: UpDown](name: String, f: A => R): PrimitiveFunction1[A, R] =
    PrimitiveFunction1(name, implicitly[UpDown[A]], implicitly[Up[R]], f)

  def define[T](name: String)(implicit ConvertFromString: Convert[String, T]): ReferenceLabel[T] = new ReferenceLabel[T](name) {
    override protected[this] def internalInterpret(s: String): T = ConvertFromString.convert(s)
  }

  import highcats._

  implicit def autoUp[O](o: O)(implicit up: Up[O]): Term = up.up(o)

  implicit def upMonoidLabeled[O[_] : MonoidLabeled : Traverse : Applicative : MonoidK, E: UpDown]: UpDown[O[E]] = new UpDown[O[E]] {
    val eUpDown = implicitly[UpDown[E]]
    val fTraverse = implicitly[Traverse[O]]
    val label = implicitly[MonoidLabeled[O]].monoidLabel
    val oMonoid = implicitly[MonoidK[O]].algebra[E]
    val oApplicative = implicitly[Applicative[O]]

    override def up(o: O[E]): Term = {
      fTraverse.foldMap(o)(eUpDown.up)(label)
    }

    override def down(t: Term): Option[O[E]] = t match {
      case label.iterable(seq: Iterable[Term]) =>
        def ff(t: Term) = oApplicative.pure(eUpDown.down(t).get)

        Some(implicitly[Traverse[scala.List]].foldMap(seq.toList)(ff)(oMonoid))
    }
  }
}