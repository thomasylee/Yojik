import org.scalatest.{ FlatSpec, Matchers }

import xyz.thomaslee.yojik.xml.XmlString

class XmlStringSpec extends FlatSpec with Matchers {
  it should "return the String value for toString()" in {
    val string = "This is a test string."
    val xmlString = new XmlString(string)

    assert(xmlString.value == string)
    assert(xmlString.toString == string)
  }
}
