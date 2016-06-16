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
      results.toList.length should beEqualTo(439)
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
        case ("drt_151124_203000_U25416_CI_6834.json",
        FlightPassengerInfoResponse("LGW", "5416", "U2", "2015-11-24", _)) :: Nil => true
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
        case ("drt_151124_203000_U25416_CI_6834.json",
        FlightPassengerInfoResponse("LGW", "5416", "U2", "2015-11-24",
        (PassengerInfo("MNE", Some("1931-05-19")) :: passengerInfoTail))) :: Nil => true
        case default =>
          assert(false, "Didn't match expectation, got: " + default)
          false
      }
    }
  }

  def openResourceZip: InputStream = {
    getClass.getClassLoader.getResourceAsStream("s3content/zippedtest/PREPRODdrt_dq_160330_145942_0682.zip")
  }
}
