package core

import java.io.InputStream
import java.util.zip.ZipInputStream

import core.ZipUtils.UnzippedFileContent
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import parsing.PassengerInfoParser


class ZipSpec extends Specification with Matchers {

  import spray.json._
  import PassengerInfoParser._
  import spray.json.DefaultJsonProtocol
  import FlightPassengerInfoProtocol._

  "Can extract file content from a zip" >> {
    "given a zip file inputstream" in {
      val results = ZipUtils.usingZip(new ZipInputStream(openResourceZip)) {
        zip =>
          val unzippedStream: Stream[UnzippedFileContent] = ZipUtils.unzipAllFilesInStream(zip)
          unzippedStream.toList
      }
      val numberOfFIlesInZip: Int = results.toList.length
      numberOfFIlesInZip should beEqualTo(59)
    }
    "can parse from the zipped file" in {
      val results = ZipUtils.usingZip(new ZipInputStream(openResourceZip)) {
        zip =>
          val unzippedStream: Stream[UnzippedFileContent] = ZipUtils.unzipAllFilesInStream(zip)
          unzippedStream.take(1).map {
            fc => (fc.filename, fc.content.parseJson.convertTo[FlightPassengerInfoResponse])
          }
      }
      results.toList match {
        case ("drt_160302_165000_SU2584_CI_0915.json",
        FlightPassengerInfoResponse("LHR", "2584", "SU", "2016-03-02", _)) :: Nil => true
        case default =>
          assert(false, "Didn't match expectation, got: " + default)
          false
      }
    }
    "a PassengerInfo has origin country, and DocumentType" in {
      val results = ZipUtils.usingZip(new ZipInputStream(openResourceZip)) {
        zip =>
          val unzippedStream: Stream[UnzippedFileContent] = ZipUtils.unzipAllFilesInStream(zip)
          unzippedStream.take(1).map {
            fc => (fc.filename, fc.content.parseJson.convertTo[FlightPassengerInfoResponse])
          }
      }
      results.toList match {
        //it is not clear to me why I'm getting this file, which is not alphabetically first
        // will it matter? do we care?
        case ("drt_160302_165000_SU2584_CI_0915.json",
        FlightPassengerInfoResponse("LHR", "2584", "SU", "2016-03-02",
        PassengerInfoJson(Some("V"), "GTM", Some("67")) :: passengerInfoTail)) :: Nil => true
        case default =>
          assert(false, "Didn't match expectation, got: " + default)
          false
      }
    }
  }

  def openResourceZip: InputStream = {
    getClass.getClassLoader.getResourceAsStream("s3content/zippedtest/drt_dq_160617_165737_5153.zip")
  }
}
