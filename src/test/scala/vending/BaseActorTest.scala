package vending

import java.time.LocalDate

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{Matchers, WordSpecLike}
import vending.Domain._
import vending.VendingMachineSm.VendingMachineState

abstract class BaseActorTest extends TestKit(ActorSystem("test"))
  with WordSpecLike
  with ImplicitSender
  with Matchers {
  private val beer = Product(3, "1", Symbols.beer, LocalDate.of(2020, 12, 10))
  private val pizza = Product(100, "2", Symbols.pizza, LocalDate.of(2018, 12, 10))

  private val quantity = Map(beer -> 5, pizza -> 3)

  def createActor(quantity: Map[Domain.Product, Int],
                  userOutputActor: ActorRef,
                  reportsActor: ActorRef): ActorRef

  implicit val timeout: Timeout = Timeout(3 seconds)

  "Actor" should {

    "successfully buy and give change" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity, userOutput.ref, reports.ref)
      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      userOutput.expectMsgType[Display]
      underTest ! SelectProduct("1")

      userOutput.expectMsg(GiveProductAndChange(beer, 7))
      userOutput.expectMsgType[Display]

      val state = Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds)
      state.quantity.get(beer) shouldBe Some(4)

    }

    "refuse to buy if not enough of money" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity, userOutput.ref, reports.ref)

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      userOutput.expectMsgType[Display]
      underTest ! SelectProduct("2")

      userOutput.expectMsg(NotEnoughOfCredit(90))
      userOutput.expectMsgType[Display]

    }

    "refuse to buy for wrong product selection" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity, userOutput.ref, reports.ref)

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      userOutput.expectMsgType[Display]

      underTest ! SelectProduct("4")

      userOutput.expectMsg(WrongProduct)
      userOutput.expectMsgType[Display]
    }

    "refuse to buy if out of stock" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity.updated(beer, 0), userOutput.ref, reports.ref)

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      userOutput.expectMsgType[Display]

      underTest ! SelectProduct("1")

      userOutput.expectMsg(OutOfStock(beer))
      userOutput.expectMsgType[Display]
    }

    "track credit" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity, userOutput.ref, reports.ref)

      Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds).credit shouldBe 0

      underTest ! Credit(2)
      userOutput.expectMsg(CreditInfo(2))
      userOutput.expectMsgType[Display]

      underTest ! Credit(3)
      userOutput.expectMsg(CreditInfo(5))
      userOutput.expectMsgType[Display]
    }

    "track income" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity, userOutput.ref, reports.ref)

      Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds).income shouldBe 0

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      userOutput.expectMsgType[Display]

      underTest ! SelectProduct("1")
      userOutput.expectMsg(GiveProductAndChange(beer, 7))
      userOutput.expectMsgType[Display]
      Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds).income shouldBe 3

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      userOutput.expectMsgType[Display]
      underTest ! SelectProduct("1")
      userOutput.expectMsg(GiveProductAndChange(beer, 7))
      userOutput.expectMsgType[Display]
      Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds).income shouldBe 6
    }

    "give back all money if withdraw" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity, userOutput.ref, reports.ref)

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      userOutput.expectMsgType[Display]

      underTest ! Withdrawn

      userOutput.expectMsg(CollectYourMoney)
      userOutput.expectMsgType[Display]
    }

    "report if money box is almost full" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity, userOutput.ref, reports.ref)

      underTest ! Credit(100)
      userOutput.expectMsg(CreditInfo(100))
      userOutput.expectMsgType[Display]

      underTest ! SelectProduct("2")

      userOutput.expectMsg(GiveProductAndChange(pizza, 0))
      userOutput.expectMsgType[Display]
      reports.expectMsg(MoneyBoxAlmostFull(100))

    }

    "do not report if money box is almost full  for a second time" ignore {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity, userOutput.ref, reports.ref)

      underTest ! Credit(100)
      userOutput.expectMsg(CreditInfo(100))
      userOutput.expectMsgType[Display]

      underTest ! SelectProduct("2")
      userOutput.expectMsg(GiveProductAndChange(pizza, 0))
      userOutput.expectMsgType[Display]
      reports.expectMsg(MoneyBoxAlmostFull(100))

      underTest ! Credit(100)
      userOutput.expectMsg(CreditInfo(100))
      userOutput.expectMsgType[Display]

      underTest ! SelectProduct("2")
      userOutput.expectMsg(GiveProductAndChange(pizza, 0))
      userOutput.expectMsgType[Display]

      reports.expectNoMessage()
    }

    "report shortage of product" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(quantity.updated(beer, 1), userOutput.ref, reports.ref)

      underTest ! Credit(beer.price)
      userOutput.expectMsg(CreditInfo(beer.price))
      userOutput.expectMsgType[Display]

      underTest ! SelectProduct("1")
      userOutput.expectMsg(GiveProductAndChange(beer, 0))
      userOutput.expectMsgType[Display]
      reports.expectMsg(NotifyAboutShortage(beer))

    }

    "report issues with expiry date" in {
      val oldPaprika = pizza.copy(expiryDate = LocalDate.now().minusDays(1))
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(Map(oldPaprika -> 2), userOutput.ref, reports.ref)

      underTest ! CheckExpiryDate
      reports.expectMsg(ExpiredProducts(List(oldPaprika)))
    }

    "do not report issues with expiry date for a second time" in {
      val oldPaprika = pizza.copy(expiryDate = LocalDate.now().minusDays(1))
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(Map(oldPaprika -> 2), userOutput.ref, reports.ref)

      underTest ! CheckExpiryDate
      reports.expectMsg(ExpiredProducts(List(oldPaprika)))

      underTest ! CheckExpiryDate
      reports.expectNoMessage()
    }

  }

}
