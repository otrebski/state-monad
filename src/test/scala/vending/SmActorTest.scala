package vending

import akka.actor.{ActorRef, Props}

class SmActorTest extends BaseActorTest {
  override def createActor(productsDef: List[Domain.Product],
                           quantity: Map[String, Int],
                           userOutputActor: ActorRef,
                           reportsActor: ActorRef
                          ): ActorRef =
    system.actorOf(Props(new SmActor(productsDef, quantity, userOutputActor, reportsActor)))
}
