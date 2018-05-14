package xyz.thomaslee.yojik.xml

/**
 * XmlString contains a string value that was found in an XML document or stream.
 */
class XmlString(stringVal: String) extends XmlEntity {
  val value = stringVal

  override def toString: String = value
}
