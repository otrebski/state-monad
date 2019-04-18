package vending

import java.util.UUID

import akka.actor.{ActorRef, Props}
import cats.syntax.option._

class SmPersistentActorTest extends BaseActorTest {
  override def createActor(quantity: Map[Domain.Product, Int],
                           userOutputActor: ActorRef,
                           reportsActor: ActorRef,
                           statePublisher: ActorRef
                          ): ActorRef =
    system
      .actorOf(Props(new SmPersistentActor(UUID.randomUUID().toString)(quantity,
        userOutputActor.some,
        reportsActor,
        statePublisher)))
}
