package s3

import java.io.InputStream
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

import akka.routing.ActorRefRoutee
import akka.stream.actor.{ActorSubscriber, ActorSubscriberMessage, MaxInFlightRequestStrategy}
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision}
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.S3ClientOptions
import com.mfglabs.commons.aws.s3.{AmazonS3AsyncClient, S3StreamBuilder}
import core.{Core, CoreActors, CoreLogging, ZipUtils}
import core.ZipUtils.UnzippedFileContent
import org.apache.commons.logging.LogFactory
import parsing.PassengerInfoParser

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

trait S3Poller extends CoreLogging {

  def builder: S3StreamBuilder

  def createBuilder: S3StreamBuilder

  def bucket: String

  def createS3client: AmazonS3AsyncClient

  def numberOfCores = 8

  def unzipTimeout = FiniteDuration(400, TimeUnit.SECONDS)

  def fileNameStream: Source[(String, Date), NotUsed] = builder.listFilesAsStream(bucket)

  def intermediate(actorMaterializer: ActorMaterializer)(implicit ec: ExecutionContext): Source[List[UnzippedFileContent], NotUsed] = fileNameStream
    .mapAsync(numberOfCores) { (fileAndDate) =>
      Future {
        try {
          log.info(s"Will parse ${fileAndDate}")
          val threadSpecificBuilder = createBuilder
          val zippedByteStream = threadSpecificBuilder.getFileAsStream(bucket, fileAndDate._1)
          //todo! here is a timeout! why? am I insane?
          val inputStream: InputStream = zippedByteStream.runWith(
            StreamConverters.asInputStream(unzipTimeout)
          )(actorMaterializer)
          val unzippedFileContent = ZipUtils.unzipAllFilesInStream(new ZipInputStream(inputStream)).toList
          unzippedFileContent.headOption.map(uzfch => {
            log.debug(s"unzipped file content! ${uzfch.filename}:${uzfch.content.length}")
          })
          unzippedFileContent
        } catch {
          case e: Throwable =>
            log.error(e, s"Error in S3Poller for ${fileAndDate}: ")
            throw e
        }
      }
    }

  def zipFiles(actorMaterializer: ActorMaterializer)(implicit ec: ExecutionContext): Source[UnzippedFileContent, NotUsed] = {
    intermediate(actorMaterializer).mapConcat {
      t => t
    }
  }

}

trait SimpleS3Poller extends S3Poller with Core {
  this: CoreActors =>
  val bucket: String = "drt-deveu-west-1"

  def createBuilder = S3StreamBuilder(new AmazonS3AsyncClient())

  val builder = createBuilder

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val flowMaterializer = ActorMaterializer()

  val streamAllThis: ActorRef = this.zipFiles(flowMaterializer).runWith(Sink.actorSubscriber(WorkerPool.props(flightPassengerReporter)))

  def streamAllThisToPrintln: Future[Done] = this.zipFiles(flowMaterializer).runWith(Sink.foreach(println))
}

object Decider {
  val decider: Supervision.Decider = {
    case _: java.io.IOException => Supervision.Restart
    case _ => Supervision.Stop
  }
}

trait SimpleAtmosPoller extends S3Poller with Core {
  this: CoreActors =>
  val bucket: String = "drtdqprod"
  val skyscapeAtmosHost: String = "cas00003.skyscapecloud.com:8443"

  def createS3client: AmazonS3AsyncClient = {
    val key = ""
    val prefix = ""
    val configuration: ClientConfiguration = new ClientConfiguration()
    //    configuration.setSignerOverride("NoOpSignerType")
    configuration.setSignerOverride("S3SignerType")
    val provider: ProfileCredentialsProvider = new ProfileCredentialsProvider("drt-atmos")
    log.info("Creating S3 client")

    val client = new AmazonS3AsyncClient(provider, configuration)
    client.client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build)
    client.client.setEndpoint(skyscapeAtmosHost)
    client
  }

  override def createBuilder: S3StreamBuilder = S3StreamBuilder(createS3client)

  override lazy val builder = createBuilder


  implicit val flowMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  lazy val streamAllThis: ActorRef = this.zipFiles(flowMaterializer)
    .map {
      case x =>
        log.debug(s"passing: $x")
        x
    }
    .runWith(Sink.actorSubscriber(WorkerPool.props(flightPassengerReporter)))

  def streamAllThisToPrintln: Future[Done] = this.zipFiles(flowMaterializer)
    .withAttributes(ActorAttributes.supervisionStrategy(Decider.decider))
    .runWith(Sink.foreach(ln =>
      println(ln.toString.take(200))))
}

//case class S3PollingActor(bucket: String) extends Actor with S3Poller {
//  val builder = S3StreamBuilder(new AmazonS3AsyncClient())
//
//  def receive = ???
//}

object WorkerPool {

  case class Msg(id: Int, replyTo: ActorRef)

  case class Work(id: Int)

  case class Reply(id: Int)

  case class Done(id: Int)

  def props(passengerInfoRouter: ActorRef): Props = Props(new WorkerPool(passengerInfoRouter))
}

class WorkerPool(flightPassengerInfoRouter: ActorRef) extends ActorSubscriber with ActorLogging {

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
