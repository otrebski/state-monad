package vending

import java.time.LocalDate

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorRef}
import cats.syntax.show._
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

class SmActor(productsDef: List[Product],
              quantity: Map[String, Int],
              userReportActor: ActorRef,
              reportsActor: ActorRef)
  extends Actor {

  var vendingMachineState = VendingMachineState(
    credit = 0,
    income = 0,
    productsDef = productsDef,
    quantity = quantity
  )

  override def preStart(): Unit = {
    super.preStart()
    context.system.scheduler
      .schedule(5 seconds, 5 seconds, self, CheckExpiryDate)(context.system.dispatcher)
  }

  override def receive: Receive = {
    case a: Action =>
      val (newState, results) = VendingMachineSm.process(a, vendingMachineState.copy(now = LocalDate.now()))
      vendingMachineState = newState
      results.systemReports.foreach(reportsActor ! _)
      results.userOutputs.foreach(userReportActor ! _)

    case GetState => sender() ! vendingMachineState

    case AskForStateAsString => sender() ! vendingMachineState.show
  }
}
