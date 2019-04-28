package vending

import akka.actor.ActorRef
import akka.persistence.PersistentActor
import cats.syntax.option._
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

import scala.concurrent.duration._
import scala.language.postfixOps

class VendingMachinePersistentActor(val persistenceId: String)(
  var quantity: Map[Product, Int],
  userReportActor: Option[ActorRef],
  reportsActor: ActorRef,
  statePublisher: ActorRef
) extends PersistentActor {

  var credit: Int = 0
  var income: Int = 0
  var expiryNotified: Set[Domain.Product] = Set.empty

  override def preStart(): Unit = {
    super.preStart()
    context.system.scheduler
      .schedule(5 seconds, 5 seconds, self, CheckExpiryDate)(context.system.dispatcher)
  }

  override def receiveRecover: Receive = {
    case a: Action => updateState(a)
    case _ => //ignore
  }

  override def receiveCommand: Receive = {

    case a: Action =>
      persist(a) { action =>
        val result = updateState(action)
        result.userOutputs.foreach(userReportActor.getOrElse(sender()) ! _)
        result.displayState.foreach(statePublisher ! _)
        result.systemReports.foreach(reportsActor ! _)
      }

    case GetState =>
      sender() ! currentState()
  }

  private def updateState(action: Action): ActionResult = {
    action match {
      case Credit(value) => credit += value
        ActionResult(userOutputs = List(CreditInfo(credit)), displayState = Display(currentState()).some)
      case Withdrawn =>
        credit = 0
        ActionResult(userOutputs = List(CollectYourMoney), displayState = Display(currentState()).some)
      case CheckExpiryDate => ActionResult.empty() //TODO ???
      case SelectProduct(number) =>
        val selected = number.toString
        val maybeProduct = quantity.keys.find(_.code == selected)
        val maybeQuantity = maybeProduct.map(quantity)
        (maybeProduct, maybeQuantity) match {
          case (Some(product), Some(q)) if product.price <= credit && q > 0 =>
            val giveChange = credit - product.price // calculating new state
          val newQuantity = q - 1 // .....................

            credit = 0 // Changing internal state
            income += product.price // .......................
            quantity = quantity.updated(product, newQuantity) // ......................

            val shortage: Option[SystemReporting] = if (newQuantity == 0) ProductShortage(product).some else none[SystemReporting]
            val collectMoney: Option[SystemReporting] = if (income > 10) MoneyBoxAlmostFull(income).some else none[SystemReporting]
            ActionResult(
              userOutputs = List(GiveProductAndChange(product, giveChange)),
              systemReports = List(shortage, collectMoney).flatten,
              displayState = Display(currentState()).some
            )


          case (Some(product), Some(q)) if q < 1 =>
            ActionResult(
              userOutputs = List(OutOfStock(product)),
              displayState = Display(currentState()).some
            )

          case (Some(product), _) =>

            ActionResult(
              userOutputs = List(NotEnoughOfCredit(product.price - credit)),
              displayState = Display(currentState()).some
            )


          case (None, _) =>
            ActionResult(
              userOutputs = List(WrongProduct),
              displayState = Display(currentState()).some
            )
        }
      case Quit =>
        ActionResult.empty()
    }
  }

  def currentState(): VendingMachineState = {
    VendingMachineState(
      credit, income,
      quantity,
      reportedExpiryDate = expiryNotified)
  }
}