package scala.slick.migrations
import scala.slick.session.Session
import scala.slick.driver._
import scala.slick.lifted.Query
class MigrationManager( migrations:List[UpMigration], slickConfigTable:String="__slick_migrations__" )
                           (implicit session:Session, driver:ExtendedProfile)
                           extends base.VersionedMigrationManager[UpMigration](migrations){
  override lazy val initial = new UpMigration(0){ def up = throw new Exception("this should never happen") }
//  import scala.slick.driver.H2Driver.simple._ // FIXME how to get rid of this line
  import driver.Implicit._
  private object SlickConfig extends driver.Table[(String,String)](slickConfigTable){
    def key = column[String]("key",O.PrimaryKey)
    def value = column[String]("value")
    def * = key ~ value
  }
  def initialize = {
    SlickConfig.ddl.create
    SlickConfig.insertAll(
      ("version","0")
    )
  }
  override protected def up( migration : UpMigration ) = {
    session.withTransaction{
      super.up(migration)
    }
  }
  protected def lastMigration_=(value:UpMigration){
    Query(SlickConfig).filter(_.key==="version").mutate( m => m.row = (m.row._1,value.version.toString) )
  }
  protected def lastMigration = 
    byVersion( SlickConfig.filter(_.key==="version").map(_.value).first.toInt )
  
  def lastApplied = lastMigration
  def currentVersion = lastMigration.version
}
abstract class UpMigration(version:Int)(implicit session:Session) extends base.Migration(version){
  /**
   * This method contains the actual code for this migration (database queries, etc.). It needs to be defined in a subclass.
   */
  def up
}
protected class UpMigrationFactory{
  /**
   * @param migration A function performing the actual migration.
   */
  def apply( version :Int )( migration: =>Unit )(implicit session:Session) = new UpMigration(version){
    def up = migration
  }
}
object upTo extends UpMigrationFactory
