package CNFConverter


import java.io.{BufferedWriter, FileWriter, PrintWriter}

import org.sat4j.minisat.SolverFactory
import org.sat4j.reader.DimacsReader
import org.sat4j.specs.{IProblem, IVecInt}
import ldfi.akka.BooleanFormulas.BooleanFormula._
import ldfi.akka.BooleanFormulas._
import ldfi.akka.Parser.AkkaParser.Row
import org.scalatest.FunSuite

import scala.io.Source

class CNFConverterSuite extends FunSuite {



  testaddLiteralsToClause()
  testgetMessage()
  testgetNode()

  def testaddLiteralsToClause(): Unit = {
    val formula = new Formula
    val clause = new Clause
    val row = Row("A", "B", "1")
    val msg = Message("A", "B", "1")

    CNFConverter.addRowToClause(clause, row)
    test("testing addLiteralToClause"){
      assert(clause.literalExistsInFormula(msg))
    }
  }

  def testgetMessage(): Unit = {
    val row = Row("A", "B", "1")
    val msg = CNFConverter.getMessage(row)
    test("testing getMessage") {
      assert(msg.sender == row.sender)
      assert(msg.recipient == row.recipient)
      assert(msg.time == row.time)
    }
  }

  def testgetNode(): Unit = {
    val row = Row("A", "B", "1")
    val msg = CNFConverter.getSenderNode(row)
    test("testing getNode") {
      assert(msg.node == row.sender)
      assert(msg.time == row.time)
    }
  }

}
