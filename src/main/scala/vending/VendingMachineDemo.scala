package vending

import java.time.LocalDate

import scala.annotation.tailrec
import scala.io.StdIn
import scala.util.Try

import akka.actor.{ActorRef, ActorSystem, Props}
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

object VendingMachineDemo extends App {

  println("Vending machine demo")
  private val manual: String =
    """| Commands:
       |   +digit -> insert coins, for example: +3
       |   digit  -> select number, for example: 2
       |   -      -> resign and take your money
       |   q      -> quit program
    """.stripMargin

  val expiryDate = LocalDate.now().plusDays(2)
  var vendingMachineState = VendingMachineState(
    credit = 0, income = 0,
    quantity = Map(
      Product(price = 4, code = "1", symbol = Symbols.candy, expiryDate = expiryDate) -> 1,
      Product(price = 2, code = "2", symbol = Symbols.pizza, expiryDate = expiryDate) -> 6,
      Product(price = 1, code = "3", symbol = Symbols.banana, expiryDate = expiryDate) -> 2,
      Product(price = 8, code = "4", symbol = Symbols.beer, expiryDate = expiryDate) -> 3
    )
  )

  private val system = ActorSystem("vending_machine")

  val clear = "\u001b[2J"

  println(
    """Pick Vending machine implementation:
      | 1 -> Logic in actor
      | 2 -> Logic in State Monad
      | 3 -> Logic in State Monad with persistence  """.stripMargin)

  private def parseAction(line: String): Option[Action] = {
    import cats.syntax.option._
    if (line.matches("\\+[\\d]+")) {
      Try(Credit(line.toInt)).toOption
    } else if (line.matches("\\d+")) {
      Try(SelectProduct(line)).toOption
    } else if (line == "-") {
      Withdrawn.some
    } else if (line == "q") {
      Quit.some
    } else {
      none[Action]
    }
  }

  def chooseActor(userOutputActor: ActorRef, reportsActor: ActorRef): Props = {
    val answer = StdIn.readLine()
    answer match {
      case "1" => Props(new BaseVendingMachineActor(
        vendingMachineState.quantity,
        userOutputActor,
        reportsActor))
      case "2" => Props(new SmActor(
        vendingMachineState.quantity,
        userOutputActor,
        reportsActor))
      case "3" => Props(new SmPersistentActor("p")(
        vendingMachineState.quantity,
        userOutputActor,
        reportsActor))
      case _ => chooseActor(userOutputActor, reportsActor)
    }
  }

  val userOutputActor = system.actorOf(Props(new UserOutputsActor))
  val reportsActor = system.actorOf(Props(new SystemReportsActor))
  val props = chooseActor(userOutputActor, reportsActor)
  val actor = system.actorOf(props, "vm")
  actor ! Credit(0)

  @tailrec
  def loop(actor: ActorRef): Unit = {
    println(manual)
    val line = StdIn.readLine()
    val action = parseAction(line)
    action match {
      case Some(Quit) => ()
      case Some(a) =>
        println(clear)
        actor ! a
        loop(actor)
      case None => loop(actor)
    }
  }

  loop(actor)
  system.terminate()
}
