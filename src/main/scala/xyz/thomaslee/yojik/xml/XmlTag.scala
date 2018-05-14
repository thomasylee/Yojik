package xyz.thomaslee.yojik.xml

/**
 * XmlTag is a tag element in a parsed XML document or stream.
 */
class XmlTag(val name: String, val prefix: Option[String], val namespaceUri: Option[String], val attributes: Map[String, String]) extends XmlEntity {
  private var contentsList: List[XmlEntity] = List()
  private var contentsMap: Map[String, List[XmlTag]] = Map()
  private var strings: List[XmlString] = List()

  /**
   * Adds content, such as text or child tags, to the XmlTag.
   *
   * @param entity the content to add to the XmlTag
   */
  def addContent(entity: XmlEntity): Unit = entity match {
    case tag: XmlTag => {
      contentsList = contentsList ::: List(tag)
      contentsMap = contentsMap + (tag.name -> (contentsMap.getOrElse(tag.name, List()) ::: List(tag)))
    }
    case string: XmlString => {
      contentsList = contentsList ::: List(string)
      strings = strings ::: List(string)
    }
    case _ => contentsList = contentsList ::: List(entity)
  }

  /**
   * Returns the child tags of the specified type.
   *
   * @param name the name of the tags to return
   * @return a list of child tags that match the specified tag name
   */
  def getChildTags(name: String): List[XmlTag] = contentsMap.getOrElse(name, List())

  /**
   * Returns the contents of the tag in the order that they were received.
   *
   * @return the contents of the tag
   */
  def getContents: List[XmlEntity] = contentsList

  /**
   * Returns the strings that are direct children of this tag.
   *
   * @return the strings in the tag
   */
  def getStrings: List[XmlString] = strings
}
