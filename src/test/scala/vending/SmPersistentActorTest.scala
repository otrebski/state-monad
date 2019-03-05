package vending

import java.util.UUID

import akka.actor.{ActorRef, Props}

class SmPersistentActorTest extends BaseActorTest {
  override def createActor(quantity: Map[Domain.Product, Int],
                           userOutputActor: ActorRef,
                           reportsActor: ActorRef
                          ): ActorRef =
    system.actorOf(Props(new SmPersistentActor(UUID.randomUUID().toString)(quantity, userOutputActor, reportsActor)))
}
