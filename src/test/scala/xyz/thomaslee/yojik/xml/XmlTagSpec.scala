import org.scalatest.{ FlatSpec, Matchers }

import xyz.thomaslee.yojik.xml.{ XmlString, XmlTag }

class XmlTagSpec extends FlatSpec with Matchers {
  it should "add and retrieve content correctly" in {
    val tag = new XmlTag("test", None, None, Map())
    val child1 = new XmlTag("child", None, None, Map())
    val string1 = new XmlString("string1")
    val child2 = new XmlTag("child", None, None, Map())
    val string2 = new XmlString("string2")

    // addContent()
    tag.addContent(child1)
    tag.addContent(string1)
    tag.addContent(child2)
    tag.addContent(string2)

    // getChildTags()
    assert(tag.getChildTags("nonexistent").size == 0)
    assert(tag.getChildTags("child").size == 2)
    assert(tag.getChildTags("child") == List(child1, child2))

    // getContents()
    assert(tag.getContents == List(child1, string1, child2, string2))

    // getStrings()
    assert(tag.getStrings == List(string1, string2))
  }

  it should "convert to string recursively" in {
    val root = new XmlTag("root", Some("test"), Some("namespace"), Map("id" -> "1", "type" -> "test"))
    root.addContent(new XmlTag("child", None, Some("another-ns"), Map("value" -> "abc")))
    root.addContent(new XmlString("string1"))
    val child = new XmlTag("child", None, None, Map())
    child.addContent(new XmlString("subchild-string"))
    child.addContent(new XmlTag("subchild", None, None, Map("id" -> "2")))
    root.addContent(child)
    root.addContent(new XmlString("string2"))

    val expected = "<test:root xmlns=\"namespace\" id=\"1\" type=\"test\">" +
      "<child xmlns=\"another-ns\" value=\"abc\"></child>string1" +
      "<child>subchild-string<subchild id=\"2\"></subchild></child>" +
      "string2</test:root>"

    assert(root.toString == expected)
  }
}
