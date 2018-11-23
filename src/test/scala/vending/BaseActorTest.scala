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
  val beer = Product(3, "1", Symbols.beer, LocalDate.of(2020, 12, 10))
  val pizza = Product(100, "2", Symbols.pizza, LocalDate.of(2018, 12, 10))
  private val productsDef = List(beer, pizza)
  val quantity = Map("1" -> 5, "2" -> 3)
  def createActor(productsDef: List[Domain.Product],
                  quantity: Map[String, Int],
                  userOutputActor: ActorRef,
                  reportsActor: ActorRef): ActorRef

  implicit val timeout: Timeout = Timeout(3 seconds)

  "Actor" should {

    "successfully buy and give change" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity, userOutput.ref, reports.ref)
      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      underTest ! SelectProduct("1")

      userOutput.expectMsg(GiveProductAndChange(beer, 7))

      val state = Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds)
      state.quantity.get("1") shouldBe Some(4)

    }

    "refuse to buy if not enough of money" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity, userOutput.ref, reports.ref)

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))

      underTest ! SelectProduct("2")

      userOutput.expectMsg(NotEnoughOfCredit(90))

    }

    "refuse to buy for wrong product selection" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity, userOutput.ref, reports.ref)

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))

      underTest ! SelectProduct("4")

      userOutput.expectMsg(WrongProduct)
    }

    "refuse to buy if out of stock" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity.updated(beer.code, 0), userOutput.ref, reports.ref)

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))

      underTest ! SelectProduct("1")

      userOutput.expectMsg(OutOfStock(beer))
    }

    "track credit" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity, userOutput.ref, reports.ref)

      Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds).credit shouldBe 0

      underTest ! Credit(2)
      userOutput.expectMsg(CreditInfo(2))

      underTest ! Credit(3)
      userOutput.expectMsg(CreditInfo(5))
    }

    "track income" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity, userOutput.ref, reports.ref)

      Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds).income shouldBe 0

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      underTest ! SelectProduct("1")
      userOutput.expectMsg(GiveProductAndChange(beer, 7))
      Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds).income shouldBe 3

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))
      underTest ! SelectProduct("1")
      userOutput.expectMsg(GiveProductAndChange(beer, 7))
      Await.result((underTest ? GetState).mapTo[VendingMachineState], 3 seconds).income shouldBe 6
    }

    "give back all money if withdraw" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity, userOutput.ref, reports.ref)

      underTest ! Credit(10)
      userOutput.expectMsg(CreditInfo(10))

      underTest ! Withdrawn

      userOutput.expectMsg(CollectYourMoney)
    }

    "report if money box is almost full" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity, userOutput.ref, reports.ref)

      underTest ! Credit(100)
      userOutput.expectMsg(CreditInfo(100))

      underTest ! SelectProduct("2")

      userOutput.expectMsg(GiveProductAndChange(pizza, 0))
      reports.expectMsg(MoneyBoxAlmostFull(100))

    }

    "do not report if money box is almost full  for a second time" ignore {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity, userOutput.ref, reports.ref)

      underTest ! Credit(100)
      userOutput.expectMsg(CreditInfo(100))
      underTest ! SelectProduct("2")
      userOutput.expectMsg(GiveProductAndChange(pizza, 0))
      reports.expectMsg(MoneyBoxAlmostFull(100))
      underTest ! Credit(100)
      userOutput.expectMsg(CreditInfo(100))
      underTest ! SelectProduct("2")
      userOutput.expectMsg(GiveProductAndChange(pizza, 0))

      reports.expectNoMessage()
    }

    "report shortage of product" in {
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(productsDef, quantity.updated(beer.code, 1), userOutput.ref, reports.ref)

      underTest ! Credit(beer.price)
      userOutput.expectMsg(CreditInfo(beer.price))

      underTest ! SelectProduct("1")
      userOutput.expectMsg(GiveProductAndChange(beer, 0))
      reports.expectMsg(NotifyAboutShortage(beer))

    }

    "report issues with expiry date" in {
      val oldPaprika = pizza.copy(expiryDate = LocalDate.now().minusDays(1))
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(List(beer, oldPaprika), quantity, userOutput.ref, reports.ref)

      underTest ! CheckExpiryDate
      reports.expectMsg(ExpiredProducts(List(oldPaprika)))
    }

    "do not report issues with expiry date for a second time" in {
      val oldPaprika = pizza.copy(expiryDate = LocalDate.now().minusDays(1))
      val userOutput = TestProbe("userOutput")
      val reports = TestProbe("reports")
      val underTest = createActor(List(beer, oldPaprika), quantity, userOutput.ref, reports.ref)

      underTest ! CheckExpiryDate
      reports.expectMsg(ExpiredProducts(List(oldPaprika)))

      underTest ! CheckExpiryDate
      reports.expectNoMessage()
    }

  }

}
