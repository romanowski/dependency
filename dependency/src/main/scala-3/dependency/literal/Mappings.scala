package dependency.literal

import java.util.UUID

import scala.quoted.{Expr => QExpr, _}

private[literal] final case class Mappings(mappings: List[(String, QExpr[String])]) {


  def input(inputs: Seq[String]): String =
    (inputs.zip(mappings).flatMap { case (s, (id, _)) => Seq(s, id) } ++ inputs.drop(mappings.length)).mkString


  private def indices(str: String, subString: String): List[Int] = {
    def helper(from: Int): List[Int] = {
      val idx = str.indexOf(subString, from)
      if (idx < 0) Nil
      else idx :: helper(idx + subString.length)
    }
    helper(0)
  }

  private def insertExpr(str: String, idLen: Int, insert: QExpr[String], indices: List[Int])(using Quotes): QExpr[String] =
    indices match {
      case Nil => QExpr(str)
      case idx :: tail =>
        val (prefix, suffix) = str.splitAt(idx)
        val prefixExpr = insertExpr(prefix, idLen, insert, tail)
        '{$prefixExpr + $insert + ${QExpr(suffix.substring(idLen))}}
    }

  def Expr(str: String)(using Quotes): QExpr[String] =
    mappings match {
      case Nil => QExpr(str)
      case (id, expr) :: tail =>
        val indices0 = indices(str, id)
        insertExpr(str, id.length, expr, indices0)
    }

  def stringOption(opt: Option[String])(using Quotes): QExpr[Option[String]] =
    opt match {
      case None => '{None}
      case Some(value) => '{Some(${Expr(value)})}
    }
  def mapStringString(map: Map[String, String])(using Quotes): QExpr[Map[String, String]] = {
    val entries = map.toVector.sorted.map {
      case (k, v) => '{(${Expr(k)}, ${Expr(v)})}
    }
    '{Map(${Varargs(entries)}: _*)}
  }

  def mapStringStringOption(map: Map[String, Option[String]])(using Quotes): QExpr[Map[String, Option[String]]] = {
    val entries = map.toVector.sorted.map {
      case (k, v) => '{(${Expr(k)}, ${stringOption(v)})}
    }
    '{Map(${Varargs(entries)}: _*)}
  }

}

object Mappings {
  def from(inputs: Seq[String], argsExpr: QExpr[Seq[Any]])(using Quotes): Mappings = {
    val mappings = (0 until (inputs.length - 1)).toList.map { idx =>
      val id = UUID.randomUUID().toString.filter(_ != '-')
      (id, '{${argsExpr}.apply(${QExpr(idx)}).toString})
    }
    Mappings(mappings)
  }
}