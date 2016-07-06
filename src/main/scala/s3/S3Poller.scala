package s3

import java.io.InputStream
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

import akka.routing.ActorRefRoutee
import akka.stream.actor.{MaxInFlightRequestStrategy, ActorSubscriberMessage, ActorSubscriber}
import akka.{Done, NotUsed}
import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import com.mfglabs.commons.aws.s3.{AmazonS3AsyncClient, S3StreamBuilder}
import core.{Core, CoreActors, ZipUtils}
import core.ZipUtils.UnzippedFileContent
import parsing.PassengerInfoParser

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

trait S3Poller {
  def builder: S3StreamBuilder

  def bucket: String

  def fileNameStream: Source[(String, Date), NotUsed] = builder.listFilesAsStream(bucket)

  def zipFiles(implicit mat: ActorMaterializer): Source[UnzippedFileContent, NotUsed] = fileNameStream
    .mapConcat { (fileAndDate) =>
      val zippedByteStream = builder.getFileAsStream(bucket, fileAndDate._1)
      val inputStream: InputStream = zippedByteStream.runWith(
        StreamConverters.asInputStream(FiniteDuration(3, TimeUnit.SECONDS))
      )
      ZipUtils.unzipAllFilesInStream(new ZipInputStream(inputStream)).toList
    }
}

trait SimpleS3Poller extends S3Poller with Core {
  this: CoreActors =>
  val bucket: String = "drt-deveu-west-1"
  val builder = S3StreamBuilder(new AmazonS3AsyncClient())

  implicit val flowMaterializer = ActorMaterializer()

  val streamAllThis: ActorRef = this.zipFiles.runWith(Sink.actorSubscriber(WorkerPool.props(flightPassengerReporter)))

  def streamAllThisToPrintln: Future[Done] = this.zipFiles.runWith(Sink.foreach(println))
}

case class S3PollingActor(bucket: String) extends Actor with S3Poller {
  val builder = S3StreamBuilder(new AmazonS3AsyncClient())

  def receive = ???
}

object WorkerPool {

  case class Msg(id: Int, replyTo: ActorRef)

  case class Work(id: Int)

  case class Reply(id: Int)

  case class Done(id: Int)

  def props(passengerInfoRouter: ActorRef): Props = Props(new WorkerPool(passengerInfoRouter))
}

class WorkerPool(flightPassengerInfoRouter: ActorRef) extends ActorSubscriber with ActorLogging{

  import WorkerPool._
  import ActorSubscriberMessage._
  import PassengerInfoParser._
  import FlightPassengerInfoProtocol._
  import spray.json._
  import PassengerInfoParser._

  val MaxQueueSize = 10
  var queue = Map.empty[Int, ActorRef]

  override val requestStrategy = new MaxInFlightRequestStrategy(max = MaxQueueSize) {
    override def inFlightInternally: Int = queue.size
  }

  def receive = {
    case OnNext(UnzippedFileContent(filename, content)) =>
      //      queue += sender

      println(s"Found a file content $filename")
      val parsed = Try(content.parseJson.convertTo[VoyagePassengerInfo])
      parsed match {
        case Success(voyagePassengerInfo) =>
          flightPassengerInfoRouter ! voyagePassengerInfo
        case Failure(f) =>
          log.error(f, s"Could not parse $content")
      }
    //    case Reply(id) =>
    //      queue(id) ! Done(id)
    //      queue -= id
  }
}

//trait S3Actors {
//  self: Core =>
//  val s3PollingActor = system.actorOf(Props[])
//}
