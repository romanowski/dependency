package dependency.literal

import java.util.UUID

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

abstract class LiteralMacros(val c: blackbox.Context) {
  import c.universe._

  protected def unsafeGetPrefixString(): String =
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(string: String)) :: Nil))) => string
      case Apply(_, List(Apply(_, values))) => c.abort(c.enclosingPosition, s"Only a single String literal is allowed here (got $values)")
      case l => c.abort(c.enclosingPosition, "Only a single String literal is allowed here")
    }

  private def values(trees: List[Tree]): List[String] =
    trees match {
      case Nil => Nil
      case Literal(Constant(string: String)) :: tail =>
        string :: values(tail)
      case _ =>
        c.abort(c.enclosingPosition, "Only a String literal is allowed here")
    }

  protected def unsafeGetPrefixStrings(): List[String] =
    c.prefix.tree match {
      case Apply(_, List(Apply(_, trees))) => values(trees)
      case l => c.abort(c.enclosingPosition, "Only a String literal is allowed here")
    }

  private def indices(str: String, subString: String): List[Int] = {
    def helper(from: Int): List[Int] = {
      val idx = str.indexOf(subString, from)
      if (idx < 0) Nil
      else idx :: helper(idx + subString.length)
    }
    helper(0)
  }

  private def insertExpr(str: String, idLen: Int, insert: c.Expr[Any], indices: List[Int]): c.Tree =
    indices match {
      case Nil => q"$str"
      case idx :: tail =>
        val (prefix, suffix) = str.splitAt(idx)
        val prefixExpr = insertExpr(prefix, idLen, insert, tail)
        q"$prefixExpr + $insert + ${suffix.substring(idLen)}"
    }

  protected final type Mappings = Seq[(String, c.Expr[Any])]

  protected def applyMappings(str: String, mappings: Mappings): c.Expr[String] = {
    val matchOpt = mappings
      .iterator
      .zipWithIndex
      .map {
        case ((id, expr), i) =>
          val idx = str.indexOf(id)
          (id, expr, i, idx)
      }
      .find(_._4 >= 0)
    matchOpt match {
      case None => c.Expr(q"$str")
      case Some((id, expr, i, idx)) =>
        val indices0 = indices(str, id)
        val tree = insertExpr(str, id.length, expr, indices0.reverse)
        c.Expr(tree)
    }
  }

  def mappings(args: Seq[c.Expr[Any]]): Mappings =
    args.map(arg => (UUID.randomUUID().toString.filter(_ != '-'), arg))
  def input(inputs: Seq[String], mappings: Mappings): String =
    (inputs.zip(mappings).flatMap { case (s, (id, _)) => Seq(s, id) } ++ inputs.drop(mappings.length)).mkString
}