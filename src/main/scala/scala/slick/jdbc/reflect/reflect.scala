package scala.slick.jdbc.reflect
import scala.slick.session.Session
import scala.slick.jdbc.meta._

class Schema(tableNames : List[String])(implicit session:Session){
  def table(t:MTable) = new Table(this,t)
  def tables = MTable.getTables(None, None, None, None).list.filter(t => tableNames.contains(t.name.name)).map(table _)
}
class Table(s:Schema,t:MTable)(implicit session:Session){
  def name = t.name.name
  def column(c:MColumn) = new Column(this,c)
  def columns = t.getColumns.list.map(column _)
  def primaryKey = t.getPrimaryKeys.list.map(x=>columns.find(_.name == x.column))
}

class Column(t:Table,c:MColumn)(implicit session:Session){
  def name = c.column
  def autoInc = c.isAutoInc
  def primaryKey = t.primaryKey.contains(this)
  
  def nullable = c.nullable // FIXME: what is the difference between nullable and isNullable?
  def sqlType = c.sqlType
  def sqlTypeName = c.sqlTypeName // <- shouldn't this go into the code generator?
  def columnSize = c.columnSize
  def options : List[ColumnOption]
}
