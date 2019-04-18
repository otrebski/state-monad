package vending

import java.time.LocalDate

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorRef}
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

class SmActor(quantity: Map[Product, Int],
              userReportActor: Option[ActorRef],
              reportsActor: ActorRef,
              statePublisher: ActorRef)
  extends Actor {

  var vendingMachineState = VendingMachineState(
    credit = 0,
    income = 0,
    quantity = quantity
  )

  override def preStart(): Unit = {
    super.preStart()
    context.system.scheduler
      .schedule(5 seconds, 5 seconds, self, CheckExpiryDate)(context.system.dispatcher)
  }

  override def receive: Receive = {
    case a: Action =>
      val (newState, results) = VendingMachineSm
        .compose(a, LocalDate.now()) //creating state monad for action
        .run(vendingMachineState) //running state monad
        .value  //collecting result

      //updating state
      vendingMachineState = newState

      //Executing side effects
      results.systemReports
        .foreach(effect => reportsActor ! effect)

      results.userOutputs
        .foreach(effects => userReportActor.getOrElse(sender()) ! effects)

      results.displayState.foreach(statePublisher ! _)

    case GetState => sender() ! vendingMachineState
  }
}
