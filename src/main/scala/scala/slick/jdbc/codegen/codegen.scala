package scala.slick.jdbc.codegen

import scala.slick.jdbc.reflect
import scala.slick.session.Session
import scala.slick.jdbc.meta.CodeGen
import sys.process._
import java.io.File

class Schema( schema:reflect.Schema, package_ :String )(implicit session:Session) extends GeneratorBase{
  val s = schema
  def table(t:reflect.Table) = new Table(this,t) 
  def tables : List[Table] = s.tables.map(table _)
  def render : String = render(tables).mkString(lineBreak+lineBreak)
  def renderBase = tables.map(_.renderBase).mkString(lineBreak)
  def renderConcrete = tables.map(_.renderConcrete).mkString(lineBreak)
  def concretePackage = package_
  def basePackage = package_ +".base"
  private def dump( code:String, srcFolder:String, package_ :String, fileName:String ) {
    assert( new File(srcFolder).exists() )
    val folder : String = srcFolder + "/" + (package_.replace(".","/")) + "/"
    new File(folder).mkdirs()
    code.#>(new File( folder+fileName )).!
  } 
  def singleFile( srcFolder:String, fileName:String="" ) {
    dump( render, srcFolder, package_, if(fileName != "") fileName else "entities.scala" )
  }
  def twoFiles( srcFolder:String, concreteFileName:String="", baseFileName:String="" ) {
    dump( renderBase, srcFolder, concretePackage, if(concreteFileName != "") concreteFileName else "entities.scala" )
    dump( renderConcrete, srcFolder, basePackage, if(baseFileName != "") baseFileName else "entities.scala" )
  }
  def manyFiles( srcFolder:String, concreteFileName:String="", baseFileName:String="" ) {
    tables.foreach{ table =>
      dump( table.renderConcrete, srcFolder, concretePackage, if(concreteFileName != "") concreteFileName else table.scalaName+".scala" )
      dump( table.renderBase, srcFolder, basePackage, if(baseFileName != "") baseFileName else table.scalaName+".scala" )
    }
  }
}

protected trait GeneratorBase{
  val lineBreak = "\n"
  def indent( lines:Traversable[GeneratorBase] ) : String = render(lines).map(x=>s"  ${x}").mkString(lineBreak)
  def indent( s:String ) = s.split(lineBreak).map("  "+_).mkString(lineBreak)
  def commas( v:Traversable[String] ) = v.mkString(", ")
  def scalaName(name:String,cap:Boolean=true) = CodeGen.mkScalaName(name,cap) // can we do better than the cap parameter?
  def scalaType( sqlType : Int ) = CodeGen.scalaTypeFor(sqlType)
  def render : String
  def render(renderers:Traversable[GeneratorBase]) : List[String] = renderers.map(_.render).toList
}

class Column( table:Table,column:reflect.Column ) extends GeneratorBase{
  val t = table
  val c = column
  def _scalaType = scalaType(c.sqlType)
  def scalaType : String = c.nullable.filter(x=>x).map( _=>"Option[${_scalaType}]" ).getOrElse(_scalaType)
  def scalaName : String = scalaName(name,false)
  def name = c.name
  def columnSize = c.columnSize.map("(${_})").getOrElse("")
  def tpe(name:String) = """
    DBType "${name}"${columnSize}
   """.trim()
  def types = c.sqlTypeName.map( tpe _ )
  def autoIncrement = c.autoInc.filter(x=>x).map(_=>"AutoInc")
  def primaryKey = if(c.primaryKey) Some("PrimaryKey") else None
  def flags = (autoIncrement ++ primaryKey)
  def options = commas( (types ++ flags).map("O "+_) )
  def render = s"""
    def ${name} = column[${scalaType}]("${name}"${options})
  """.trim()
}

class Table (val schema:Schema,val table:reflect.Table)(implicit session:Session) extends GeneratorBase{
  val s = schema
  val t = table
  def name = t.name
  def scalaName : String = scalaName(name)
  def column(c:reflect.Column) = new Column(this,c)
  def columns = t.columns.map(column _)
  def star = "def * = " + columns.map(_.scalaName).mkString(" ~ ")
  def types = ""
  def foreignKeys = "" //many(foreignKey(key))
  def constraints = ""
  def render = s"""
package ${s.basePackage}{
${indent(renderBase)}
}
package ${s.concretePackage}{
${indent(renderConcrete)}
}
"""
  def renderBase = s"""
trait ${scalaName} extends Table[${types}](name){
  // columns
${indent(columns)}

  ${star}
  
  // foreign keys
  ${foreignKeys}
  
  // constraints
  ${constraints}
  
  
}
      """.trim()
  def renderConcrete = s"""
class ${scalaName} extends ${s.basePackage}.${scalaName}
"""
}
/*
    val pkeys = table.getPrimaryKeys.mapResult(k => (k.column, k)).list.toMap
    if(!columns.isEmpty) {
      out.print("object "+mkScalaName(table.name.name)+" extends Table[")
      if(columns.tail.isEmpty) out.print(scalaTypeFor(columns.head))
      else out.print("(" + columns.map(c => scalaTypeFor(c)).mkString(", ") + ")")
      out.println("](\""+table.name.name+"\") {")
      for(c <- columns) output(c, pkeys.get(c.column), out)
      out.println("  def * = " + columns.map(c => mkScalaName(c.column, false)).mkString(" ~ "))
      out.println("}")
    }
*/