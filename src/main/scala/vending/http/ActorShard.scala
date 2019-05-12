package vending.http

import java.time.LocalDate

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.syntax.option._
import vending.Domain.Product
import vending.http.ActorShard._
import vending.http.Server.StreamingActor
import vending.http.json.Api.ResultV1
import vending.{BaseVendingMachineActor, SmActor, SmPersistentActor, Symbols, VendingMachinePersistentActor}

class ActorShard(implicit val materializer: ActorMaterializer, val system: ActorSystem) {

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

  case class Key(id: Int, actorType: ActorType)
  private var map: Map[Key, ActorWithStream] = Map.empty

  def getOrUpdate(id: Int, actorType: ActorType): ActorWithStream = {
    val key = Key(id, actorType)

    map.get(key) match {
      case Some(result) => result
      case None =>
        val actorWithStream: ActorWithStream = create(key)
        map = map.updated(key, actorWithStream)
        actorWithStream
    }
  }

  private def create(key: Key): ActorWithStream = {
    val quantityToUse = if (key.id == 0) {
      quantity.map(kv => kv._1 -> Random.nextInt(5))
    } else {
      quantity
    }
    val (sourceQueue, eventsSource) = stream()
    val streamingActor = system.actorOf(Props(new StreamingActor(sourceQueue)))
    val props = key.actorType match {
      case ActorWithoutPersistence =>
        Props(new BaseVendingMachineActor(
          quantityToUse,
          streamingActor.some,
          streamingActor,
          streamingActor))
      case ActorWithPersistence =>
        Props(new VendingMachinePersistentActor(s"vm${key.id}")(
          quantityToUse,
          streamingActor.some,
          streamingActor,
          streamingActor))

      case StateMonadWithoutPersistence =>
        Props(new SmActor(
          quantityToUse,
          streamingActor.some,
          streamingActor,
          streamingActor))
      case StateMonadWithPersistence =>
        Props(new SmPersistentActor(s"sm${key.id}")(
          quantityToUse,
          streamingActor.some,
          streamingActor,
          streamingActor))
    }
    val actorRef = system.actorOf(props, s"${key.actorType.getClass.getSimpleName}_${key.id}")
    ActorWithStream(actorRef, eventsSource)

  }

  private def stream(): (SourceQueueWithComplete[ResultV1], Source[ServerSentEvent, NotUsed]) =
    Source.queue[ResultV1](Int.MaxValue, OverflowStrategy.backpressure)
      .map(message => ServerSentEvent(message.toJson.compactPrint))
      .keepAlive(20 seconds, () => ServerSentEvent.heartbeat)
      .toMat(BroadcastHub.sink[ServerSentEvent])(Keep.both)
      .run()
}

object ActorShard {
  case class ActorWithStream(actor: ActorRef, eventSource: Source[ServerSentEvent, NotUsed])
  sealed trait ActorType
  object ActorWithoutPersistence extends ActorType
  object ActorWithPersistence extends ActorType
  object StateMonadWithoutPersistence extends ActorType
  object StateMonadWithPersistence extends ActorType

  def toActorType(string: String): ActorType = {
    string match {
      case "actor" => ActorWithoutPersistence
      case "smPersistent" => StateMonadWithPersistence
      case "persistentActor" => ActorWithPersistence
      case _ => StateMonadWithoutPersistence
    }
  }
}
