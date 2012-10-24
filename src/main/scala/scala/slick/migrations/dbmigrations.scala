package scala.slick.migrations
import scala.slick.migrations._
import scala.slick.session.Session
import scala.slick.driver._
import scala.slick.lifted.Query
class SlickMigrationManager( protected val migrations:List[Migration], slickConfigTable:String="__slick_migrations__" )
                           (implicit session:Session, driver:ExtendedDriver)
                           extends NamedMigrationManagerBase[Migration]{
//  import scala.slick.driver.H2Driver.simple._ // FIXME how to get rid of this line
  import driver.Implicit._
  private object SlickConfig extends driver.Table[(String,String)](slickConfigTable){
    def key = column[String]("key",O.PrimaryKey)
    def value = column[String]("value")
    def * = key ~ value
  }
  lazy val initial = new Migration("__initial_migration__"){ 
    def up = {}
    def down = { throw new Exception("This should never happen. There is a bug.") }
  }
  def initialize = {
    SlickConfig.ddl.create
    SlickConfig.insertAll(
      ("migration",initial.id)
    )
  }
  protected def lastMigration_=(value:Migration){
    Query(SlickConfig).filter(_.key==="migration").mutate( m => m.row = (m.row._1,value.id) )
  }
  protected def lastMigration = 
    byName( SlickConfig.filter(_.key==="migration").map(_.value).first )
  
  def lastApplied = lastMigration 
}