package vending.http.client

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Random, Success, Try}

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source

import io.gatling.core.stats.writer.BufferedFileChannelWriter

//https://github.com/nuxeo/gatling-report -> different gatling report processor

object TestClient extends App {
  def now() = System.currentTimeMillis()
  import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._

  implicit val as: ActorSystem = ActorSystem("client")
  implicit val m: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = as.dispatcher

  private val simulationStart: Long = now()

  val statsActor = as.actorOf(Props(new StatsActor(BufferedFileChannelWriter("test"))))

  def stream(): Future[Source[ServerSentEvent, NotUsed]] = Http()
    .singleRequest(HttpRequest(uri = "http://localhost:8080/api/sm/1/events"))
    .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])

  private val future: Future[Source[ServerSentEvent, NotUsed]] = stream().recoverWith { case _: Any => stream() }

  import spray.json._
  import vending.http.json.Api._

  def requestOn(url: String) = Http().singleRequest(HttpRequest(uri = url))

  def selectProduct(code: Int) = requestOn(s"http://127.0.0.1:8080/api/sm/1/select/$code")

  def withdraw() = requestOn(s"http://127.0.0.1:8080/api/sm/1/withdrawn")

  def insertCoin(amount: Int) = requestOn(s"http://127.0.0.1:8080/api/sm/1/credit/$amount")

  var start = now()
  var currentAction = "insert"
  var count = 0
  future.foreach { stream =>
    val userId = "1"
    statsActor ! LogUserStart(userId, start, now())
    val random = new Random(now())
    stream
      .runForeach { sse =>
        val data = sse.data

        if (data.length > 0) {
          val v = Try(resultV1Format.read(data.parseJson))
          v match {
            case Success(r) =>
              r match {
                case _: CreditInfoV1 =>
                  statsActor ! LogResponse(userId, "credit", start, now())
                  currentAction = "select"
                  if (random.nextInt(40) < 1) {
                    withdraw()
                  } else {
                    selectProduct(random.nextInt(11) + 1)
                  }

                  start = now()
                case _: GiveProductAndChangeV1 =>
                  statsActor ! LogResponse(userId, "buy", start, now())
                  currentAction = "insert"
                  insertCoin(random.nextInt(30))
                  start = now()
                case NotEnoughOfCreditV1(diff, _) =>
                  statsActor ! LogResponse(userId, "no enough money", start, now())
                  currentAction = "insert"
                  insertCoin(diff)
                  start = now()
                case _: CollectYourMoneyV1 =>
                  statsActor ! LogResponse(userId, "withdrawn", start, now())
                  currentAction = "insert"
                  insertCoin(random.nextInt(30))
                  start = now()
                case _: WrongProductV1 =>
                  statsActor ! LogResponse(userId, "wron product", start, now())
                  currentAction = "select"
                  selectProduct(random.nextInt(10) + 1)
                  start = now()
                case _ =>
              }

            case Failure(e) =>
              statsActor ! FlushAndClose
              e.printStackTrace()
          }
        }
      }

  }

  future.onComplete {
    case Success(value) =>
      println(s"Have stream $value")
      insertCoin(5)
    case Failure(e) => e.printStackTrace()
  }

  case class LogResponse(user: String, action: String, start: Long, end: Long)
  case class LogUserStart(user: String, start: Long, now: Long)
  case object FlushAndClose

  class StatsActor(writer: BufferedFileChannelWriter) extends Actor {

    override def preStart(): Unit = {
      writer.startSimulation("zssdfsfsfz", "ss", simulationStart)
    }
    override def receive: Receive = {
      case LogResponse(user, action, begin, now) =>
        writer.response(user, action, begin, now)
      case LogUserStart(user, begin, end) =>
        writer.startUser(user, "vm", begin, end)
      case FlushAndClose =>
        writer.flush()
        writer.close()
    }
  }
}
