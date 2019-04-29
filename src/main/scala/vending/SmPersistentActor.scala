package vending

import java.time.LocalDate

import akka.actor.ActorRef
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import vending.Domain.{Action, GetState, Product}
import vending.VendingMachineSm.VendingMachineState

class SmPersistentActor(val persistenceId: String)(
  quantity: Map[Product, Int],
  userReportActor: Option[ActorRef],
  reportsActor: ActorRef,
  statePublisher: ActorRef)
  extends PersistentActor {

  val snapShotInterval = 1000


  var vendingMachineState = VendingMachineState(
    credit = 0,
    income = 0,
    quantity = quantity
  )

  override def receiveRecover: Receive = {

    case action: Action =>
      val (newState, _) = VendingMachineSm
        .compose(action, LocalDate.now())
        .run(vendingMachineState)
        .value
      vendingMachineState = newState

    case SnapshotOffer(_, state: VendingMachineState) => vendingMachineState = state

    case RecoveryCompleted =>
  }

  override def receiveCommand: Receive = {
    case action: Action =>

      persist(action) { a =>
        val (newState, effects) = VendingMachineSm
          .compose(a, LocalDate.now())
          .run(vendingMachineState)
          .value
        vendingMachineState = newState
        effects.userOutputs.foreach(m => userReportActor.getOrElse(sender()) ! m)
        effects.systemReports.foreach(m => reportsActor ! m)
        effects.displayState.foreach(statePublisher ! _)
        if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
          saveSnapshot(vendingMachineState)
      }
    case GetState => sender() ! vendingMachineState
  }

}
