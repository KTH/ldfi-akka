package ldfi.akka.evaluation

import java.io.PrintWriter
import java.lang.reflect.InvocationTargetException

import ldfi.akka.booleanformulas._
import ldfi.akka.Main.Program
import ldfi.akka.parser.AkkaParser
import ldfi.akka.FailureSpec

import scala.io.Source

object Evaluator {


  def evaluate(prog: Program, freePassMessages: List[String]): Unit = {

    /************************************************
    Obtain a failure-free outcome of the program
      ************************************************/

    val tempFormula = new Formula
    val correctness = forwardStep(prog, Set.empty)
    val input = Source.fromFile("ldfi-akka/logs.log")

    if (!correctness) {
      sys.error("Error. Forwardstep: running main program: " + prog.mainClass.getName + ", " +
        "without failure injections violates the correctness specification")
    }
    //Format the program and convert it to CNF
    val format = AkkaParser.parse(input, Set.empty, freePassMessages)
    //Convert the formattedlogs to CNF formula
    CNFConverter.run(format, tempFormula)


    /************************************************
    Set initial failure spec and start evaluator
      ************************************************/
    //Initial failurespec from failure-free program
    val initFailureSpec = FailureSpec(
      eot = tempFormula.getLatestTime + 1,
      eff = 2,
      maxCrashes = 0,
      nodes = tempFormula.getAllNodes.toSet,
      messages = tempFormula.getAllMessages.toSet,
      crashes = Set.empty,
      cuts = Set.empty)

    val formula = new Formula

    //start evaluator with init failure spec and empty hypothesis
    val solutions = concreteEvaluator(prog, freePassMessages, formula, initFailureSpec, Set.empty)

    //Print Failure Injections
    prettyPrintFailureSpecs(solutions)

  }

  def concreteEvaluator(prog: Program,
                        freePassMessages: List[String],
                        formula: Formula,
                        failureSpec: FailureSpec,
                        hypothesis: Set[Literal]): Map[Set[Literal], FailureSpec] =
    evaluator(prog, freePassMessages, formula, failureSpec, hypothesis, Map.empty) match {
      case m: Map[Set[Literal], FailureSpec] if m.isEmpty =>
        if(failureSpec.eff < failureSpec.eot - 1){
          val EFF = failureSpec.eff
          concreteEvaluator(prog, freePassMessages, new Formula, failureSpec.copy(eff = EFF + 1), hypothesis)
        }
        else {
          Map.empty
        }
      case solutions => solutions
    }

  def evaluator(prog: Program,
                freePassMessages: List[String],
                formula: Formula,
                failureSpec: FailureSpec,
                hypothesis: Set[Literal],
                solutions: Map[Set[Literal], FailureSpec]
               ): Map[Set[Literal], FailureSpec] = {

    println("\n\n**********************************************************************\n" +
      "New run with following injection hypothesis: " + hypothesis + "\n" +
      "And the following failureSpec: " + failureSpec + "\n" +
      "**********************************************************************\n\n")
    val correct = forwardStep(prog, hypothesis)

    //if we did not violate the correctness property we keep looking for failures
    if(correct){
      //update failureSpec with new hypothesis
      val hypcuts = hypothesis.collect { case msg: MessageLit => msg }
      val hypcrashes = hypothesis collect { case n: Node => n }
      val updatedFailureSpec =
        failureSpec.copy(cuts = failureSpec.cuts ++ hypcuts, crashes = failureSpec.crashes ++ hypcrashes)

      //perform the backward step to obtain the new CNF formula
      val newHypotheses = backwardStep(formula, updatedFailureSpec, freePassMessages, hypothesis)

      //call evaluator recursively for every hypothesis
      val result = newHypotheses.map { hypo =>
        evaluator(prog, freePassMessages, formula, updatedFailureSpec, hypo, solutions)
      }
      if (result.isEmpty) Map.empty
      else result.reduceLeft(_ ++ _)

    }

    //hypothesis is real solution
    else{
      solutions + (hypothesis -> failureSpec)
    }
  }

  def forwardStep(program: Program, hypothesis: Set[Literal]): Boolean = {

    //Reset old info in Controller
    Controller.reset()
    //set controller injections
    Controller.setInjections(hypothesis)

    //clear the logs for each run
    new PrintWriter("ldfi-akka/logs.log") {
      write("")
      close()
    }

    //Invoke the main method
    try {
      program.mainMethod.setAccessible(true)
      program.mainMethod.invoke(null, Array[String]())
    } catch {
      case e: InvocationTargetException => sys.error("Invocation of main method failed. " + e.getCause.getMessage)
    }

    //Invoke the verify method
    val correctness = try {
      program.verifyMethod.setAccessible(true)

      //We only create new instance if veryify method is not in the main class
      if(program.verifyClass != program.mainClass){
        val freshInst = program.verifyClass.newInstance()
        program.verifyMethod.invoke(freshInst).asInstanceOf[Boolean]
      }
      else{
        program.verifyMethod.invoke(program.verifyClass).asInstanceOf[Boolean]
      }

    } catch {
      case ite: InvocationTargetException => sys.error("Invocation of verify method failed. " + ite.getCause.getMessage)
      case cce: ClassCastException => sys.error("Cast ver method ret type to boolean fail. " + cce.getCause.getMessage)
    }
    //return the correctness of the run
    correctness
  }

  def backwardStep(formula: Formula,
                   failureSpec: FailureSpec,
                   freePassMsgs: List[String],
                   hypothesis: Set[Literal]): Set[Set[Literal]] = {
    //Parse and format the program
    val input = Source.fromFile("ldfi-akka/logs.log")
    val format = AkkaParser.parse(input, hypothesis, freePassMsgs)

    //Convert the formattedlogs to CNF formula
    CNFConverter.run(format, formula)
    CNFConverter.prettyPrintFormula(formula)

    //get new hypotheses from SAT-solver
    val hypotheses = SAT4JSolver.solve(formula, failureSpec)
    hypotheses
  }


  def prettyPrintFailureSpecs(solutions: Map[Set[Literal], FailureSpec]): Unit = {
    println("\n\n" +
      "********************************************************\n" +
      "**************** FAILURE SPECIFICATIONS ****************\n" +
      "********************************************************")
    solutions.foreach { elem =>
      val fSpec = elem._2
      print("\nFailure injection: " + elem._1 + " with failure specification: <" + fSpec.eot + "," + fSpec.eff + "," +
        fSpec.maxCrashes + "> violated the correctness specification")
    }

  }

}
