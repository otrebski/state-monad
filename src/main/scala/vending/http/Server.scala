package vending.http

import java.time.LocalDate

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import cats.syntax.option._
import vending.Domain.{Credit, GetState, Product, SelectProduct, Withdrawn, _}
import vending.VendingMachineSm.VendingMachineState
import vending.http.json.Api._
import vending.{BaseVendingMachineActor, SmActor, SmPersistentActor, Symbols, VendingMachinePersistentActor}

object Server extends App {
  implicit val system: ActorSystem = ActorSystem("system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val expiryDate: LocalDate = LocalDate.now().plusDays(2)
  val symbols = List(Symbols.banana,
    Symbols.candy,
    Symbols.beer,
    Symbols.pizza,
    Symbols.hamburger,
    Symbols.spaghetti,
    Symbols.fries)
  private val quantity: Map[Product, Int] = symbols
    .zipWithIndex
    .map {
      case (symbol, i) => Product(price = 5, code = s"${i + 1}", symbol = symbol, expiryDate = expiryDate) -> Integer.MAX_VALUE
    }
    .toMap

  case class ActorWithStream(actor: ActorRef, eventSource: Source[ServerSentEvent, NotUsed])

  private def stream(): (SourceQueueWithComplete[ResultV1], Source[ServerSentEvent, NotUsed]) =
    Source.queue[ResultV1](Int.MaxValue, OverflowStrategy.backpressure)
      .map(message => ServerSentEvent(message.toJson.compactPrint))
      .keepAlive(20 seconds, () => ServerSentEvent.heartbeat)
      .toMat(BroadcastHub.sink[ServerSentEvent])(Keep.both)
      .run()

  private val vmCount = 1000
  val baseActorMap: Map[Int, ActorWithStream] = (0 to vmCount)
    .map { i =>
      val (sourceQueue, eventsSource) = stream()
      val actorRef = system
        .actorOf(Props(new BaseVendingMachineActor(quantity,
          system.actorOf(Props(new StreamingActor(sourceQueue))).some,
          system.actorOf(Props(new StreamingActor(sourceQueue))),
          system.actorOf(Props(new StreamingActor(sourceQueue)))
        )), s"actor_$i")
      i -> ActorWithStream(actorRef, eventsSource)
    }
    .toMap
  val basePersistentActorMap: Map[Int, ActorWithStream] = (0 to vmCount)
    .map { i =>
      val (sourceQueue, eventsSource) = stream()
      val actorRef = system
        .actorOf(Props(new VendingMachinePersistentActor(s"vm_persistent_$i")(quantity,
          system.actorOf(Props(new StreamingActor(sourceQueue))).some,
          system.actorOf(Props(new StreamingActor(sourceQueue))),
          system.actorOf(Props(new StreamingActor(sourceQueue)))
        )), s"persistentActor_$i")
      i -> ActorWithStream(actorRef, eventsSource)
    }
    .toMap

  val smActorMap: Map[Int, ActorWithStream] = (0 to vmCount)
    .map(i => {
      val (sourceQueue, eventsSource) = stream()
      val actor = system
        .actorOf(Props(new SmActor(quantity,
          system.actorOf(Props(new StreamingActor(sourceQueue))).some,
          system.actorOf(Props(new StreamingActor(sourceQueue))),
          system.actorOf(Props(new StreamingActor(sourceQueue)))
        )), s"sm_$i")
      i -> ActorWithStream(actor, eventsSource)
    })
    .toMap

  val smPersistentActorMap: Map[Int, ActorWithStream] = (0 to vmCount)
    .map(i => {
      val (sourceQueue, eventsSource) = stream()
      val actor = system
        .actorOf(Props(new SmPersistentActor(s"sm_$i")(quantity,
          system.actorOf(Props(new StreamingActor(sourceQueue))).some,
          system.actorOf(Props(new StreamingActor(sourceQueue))),
          system.actorOf(Props(new StreamingActor(sourceQueue)))
        )), s"smPersistent_$i")
      i -> ActorWithStream(actor, eventsSource)
    })
    .toMap

  implicit val timeout: Timeout = Timeout(10 seconds)
  val route =
    pathPrefix("api" / Segment / IntNumber) { (actorType, number) =>
      val as = if (actorType == "actor") {
        baseActorMap(number)
      } else if (actorType == "smPersistent") {
        smPersistentActorMap(number)
      } else if (actorType == "persistentActor") {
        basePersistentActorMap(number)
      } else {
        smActorMap(number)
      }
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
      getFromResourceDirectory("gui/vending-gui/build/")
    }

  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

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
