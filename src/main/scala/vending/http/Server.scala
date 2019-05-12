package vending.http

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.Timeout
import vending.Domain.{Credit, GetState, SelectProduct, Withdrawn, _}
import vending.VendingMachineSm.VendingMachineState
import vending.http.json.Api._

object Server extends App {
  implicit val system: ActorSystem = ActorSystem("system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private val actorShard = new ActorShard()
  (0 until 1000).foreach {index =>
    actorShard.getOrUpdate(index,ActorShard.ActorWithoutPersistence)
    actorShard.getOrUpdate(index,ActorShard.ActorWithPersistence)
    actorShard.getOrUpdate(index,ActorShard.StateMonadWithPersistence)
    actorShard.getOrUpdate(index,ActorShard.StateMonadWithoutPersistence)
  }

  implicit val timeout: Timeout = Timeout(10 seconds)
  val route =
    pathPrefix("api" / Segment / IntNumber) { (actorType, number) =>
      val as = actorShard.getOrUpdate(number, ActorShard.toActorType(actorType))
      val actor = as.actor
      val eventSource = as.eventSource
      get {
        path("select" / IntNumber) { selection =>
          actor ! SelectProduct(s"$selection")
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>Selected $selection in $number</h1>"))
        } ~ path("credit" / IntNumber) { credit =>
          actor ! Credit(credit)
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>Credit $credit inserted into $number</h1>"))
        } ~ path("withdrawn") {
          actor ! Withdrawn
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>Withdraw of $number</h1>"))
        } ~ path("status") {
          import akka.http.scaladsl.model.StatusCodes.InternalServerError
          onComplete((actor ? GetState).mapTo[VendingMachineState].map(VendingMachineStateV1.toApi)) {
            case Success(value) => complete(value)
            case Failure(ex) => complete((InternalServerError, s"An error occurred: ${ex.getMessage}"))
          }
        } ~ path("events") {
          complete {
            eventSource
          }
        }

      }
    } ~ get {
      path("") {
        getFromResource("gui/index.html")
      } ~ getFromResourceDirectory("gui/")
    }

  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

  println(s"Server online at http://localhost:8080/")

  class StreamingActor(source: SourceQueueWithComplete[ResultV1]) extends Actor {

    import vending.http.json.Api._

    override def receive: Receive = {
      case msg: UserOutput =>
        val output = msg match {
          case CollectYourMoney => CollectYourMoneyV1()
          case CreditInfo(value) => CreditInfoV1(value)
          case WrongProduct => WrongProductV1()
          case NotEnoughOfCredit(diff) => NotEnoughOfCreditV1(diff)
          case OutOfStock(product) => OutOfStockV1(product.code)
          case GiveProductAndChange(selected, change) => GiveProductAndChangeV1(selected.code, change)
        }
        source.offer(output)
      case display: Display =>
        val v1 = DisplayV1(VendingMachineStateV1.toApi(display.vendingMachineState))
        source.offer(v1)
      case report: SystemReporting =>
        val result = report match {
          case MoneyBoxAlmostFull(amount) => MoneyBoxAlmostFullV1(amount)
          case ProductShortage(product) => ProductShortageV1(product)
          case ExpiredProducts(products) => ExpiredProductsV1(products)
        }
        source.offer(result)
      case x: Any => println(s"Received any: $x")
    }
  }

}
