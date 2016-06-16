package core

import java.util.zip.ZipInputStream

import org.specs2.mutable.Specification

class ZipSpec extends Specification {
  "Can extract file content from a zip" >> {
    "given a zip file inputstream" in {
      val file = getClass.getClassLoader.getResourceAsStream("s3content/zippedtest/PREPRODdrt_dq_160330_145942_0682.zip")
      val zip = new ZipInputStream(file)
      println(ZipUtils.unzipAllFilesInStream(zip))
      true
    }
  }
}
