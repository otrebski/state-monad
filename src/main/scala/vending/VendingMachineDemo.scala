package vending

import java.time.LocalDate

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.dispatch.ExecutionContexts
import akka.pattern.ask
import akka.util.Timeout
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

object VendingMachineDemo extends App {

  println("Vending machine demo")
  val expiryDate = LocalDate.now().plusDays(2)
  private val manual: String =
    """| Commands:
       |   insert 3 / +3        -> insert 3 coins
       |   select digit / digit -> select number (single digit)
       |   withdraw / -         -> resign and take your money
       |   q                    -> quit
    """.stripMargin
  private val system = ActorSystem("vending_machine")
  private val reportsActor = system.actorOf(Props(new SystemReportsActor))
  private val userOutputActor = system.actorOf(Props(new SystemReportsActor))
  private val actor: ActorRef = system.actorOf(chooseActor(), "vm")

  println(
    """Pick Vending machine implementation:
      | 1 -> Logic in actor
      | 2 -> Logic in State Monad""".stripMargin)
  var vendingMachineState = VendingMachineState(
    credit = 0, income = 0,

    quantity = Map(
      Product(price = 4, code = "1", symbol = Symbols.candy, expiryDate = expiryDate) -> 1,
      Product(price = 2, code = "2", symbol = Symbols.pizza, expiryDate = expiryDate) -> 6,
      Product(price = 1, code = "3", symbol = Symbols.banana, expiryDate = expiryDate) -> 2,
      Product(price = 8, code = "4", symbol = Symbols.beer, expiryDate = expiryDate) -> 3,
    )
  )
  def chooseActor(): Props = {
    StdIn.readLine().trim match {
      case "1" => Props(new BaseVendingMachineActor(
        vendingMachineState.quantity,
        userOutputActor,
        reportsActor))
      case "2" => Props(new SmActor(vendingMachineState.quantity, userOutputActor, reportsActor))
      case _ => chooseActor()
    }
  }
  private implicit val timeout: Timeout = Timeout(1 second)

  println(Await.result((actor ? AskForStateAsString).mapTo[String], 1 second))
  @tailrec
  def waitForInputAndProcess() {
    val line: String = StdIn.readLine()
    parseAction(line) match {
      case Some(action) =>
        println(String.format("\u001b[2J"))
        println(manual)
        actor ! action
        val status = Await.result((actor ? AskForStateAsString).mapTo[String], 1 second)
        println(status)

      case _ =>

    }
    waitForInputAndProcess()
  }

  waitForInputAndProcess()

  private def parseAction(line: String): Option[Action] = {
    import cats.syntax.option._
    if (line.matches("insert [\\d]+") || line.matches("\\+[\\d]+")) {
      val credit = line.replaceAll("[^\\d]", "").toInt
      Credit(credit).some
    } else if (line.matches("(select )?\\d+")) {
      val selected = line.replaceAll("[^\\d]", "")
      SelectProduct(selected).some
    } else if (line == "withdraw" || line == "-") {
      Withdrawn.some
    } else if (line == "q") {
      system.terminate().onComplete(sys.exit(0))(ExecutionContexts.global())
      none[Action]
    } else {
      none[Action]
    }
  }

}
