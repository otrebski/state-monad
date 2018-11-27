package vending

import java.time.LocalDate

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

import akka.actor.{ActorRef, ActorSystem, Props}
import cats.effect.IO
import akka.pattern.ask
import akka.util.Timeout
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

//TODO remove IO
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
      Product(price = 4, code = "1", symbol = Symbols.candy, expiryDate = expiryDate)  -> 1,
      Product(price = 2, code = "2", symbol = Symbols.pizza, expiryDate = expiryDate)  -> 6,
      Product(price = 1, code = "3", symbol = Symbols.banana, expiryDate = expiryDate) -> 2,
      Product(price = 8, code = "4", symbol = Symbols.beer, expiryDate = expiryDate)   -> 3
    )
  )

  private val system = ActorSystem("vending_machine")

  private implicit val timeout: Timeout = Timeout(1 second)

  val clear = IO(print("\u001b[2J"))

  println(
    """Pick Vending machine implementation:
      | 1 -> Logic in actor
      | 2 -> Logic in State Monad""".stripMargin)

  def chooseActor(userOutputActor: ActorRef, reportsActor: ActorRef): IO[Props] = {
    for {
      answer <- IO(StdIn.readLine())
      props <- answer match {
        case "1" => IO(Props(new BaseVendingMachineActor(
          vendingMachineState.quantity,
          userOutputActor,
          reportsActor)))
        case "2" => IO(Props(new SmActor(
          vendingMachineState.quantity,
          userOutputActor,
          reportsActor)))
        case _ => chooseActor(userOutputActor, reportsActor)
      }
    } yield props
  }

  def loop(actor: ActorRef): IO[Unit] = {
    for {
      status <- IO(Await.result((actor ? AskForStateAsString).mapTo[String], 1 second))
      _ <- IO(println(status))
      _ <- IO(println(manual))
      line <- IO(StdIn.readLine())
      action = parseAction(line)
      _ <- clear
      _ <- action match {
        case Some(Quit) => IO(())
        case Some(a) =>
          actor ! a
          IO(() => ())
          loop(actor)
        case None => loop(actor)
      }
    } yield ()
  }

  val program = for {
    userOutputActor <- IO(system.actorOf(Props(new UserOutputsActor)))
    reportsActor <- IO(system.actorOf(Props(new SystemReportsActor)))
    props <- chooseActor(userOutputActor, reportsActor)
    actor = system.actorOf(props, "vm")
    _ <- loop(actor)
    _ <- IO(system.terminate())
  } yield ()

  program.unsafeRunSync()

  //TODO parse to long number like 111111111111111111111111111
  private def parseAction(line: String): Option[Action] = {
    import cats.syntax.option._
    if (line.matches("\\+[\\d]+")) {
      Credit(line.toInt).some
    } else if (line.matches("\\d+")) {
      SelectProduct(line).some
    } else if (line == "-") {
      Withdrawn.some
    } else if (line == "q") {
      Quit.some
    } else {
      none[Action]
    }
  }

}