package xyz.thomaslee.yojik.xml

/** Represents a string value in an XML document or stream. */
case class XmlString(stringVal: String) extends XmlEntity {
  val value = stringVal

  override def toString: String = value
}
