package scala.slick.test.migrations
import scala.slick.migrations._
import scala.slick.testutil._
import scala.slick.testutil.TestDBs._
import scala.slick.session._
import org.junit.Test
import org.junit.Assert._
import com.typesafe.slick.testkit.util.TestDB

object MigrationsTest extends DBTestObject(H2Mem)//, H2Disk, SQLiteMem, SQLiteDisk, Postgres, MySQL, DerbyMem, DerbyDisk, HsqldbMem, MSAccess, SQLServer)
class MigrationsTest(val tdb: TestDB) extends DBTest {
  @Test def testMigrations() {
    db.withSession{
      session:Session =>
    import tdb.driver._
    var applied : List[Migration] = List()
    object Tasks extends Table[(Int,String)]("tasks"){
      def id = column[Int]("id",O.PrimaryKey)
      def name = column[String]("name")
      def * = id ~ name
    }
    val m = new SlickMigrationManager(
      List(
        new Migration( "m1" ){
          def up   {}
          def down {}
        }
        ,new Migration( "m2" ){
          def up   {}
          def down {}
        }
        ,new Migration( "m3" ){
          def up   {}
          def down {}
        }
        ,new Migration( "m4" ){
          def up   {}
          def down {}
        }
      )
    )(session,tdb.driver){
      override protected def lastMigration_=(value:Migration){
        super.lastMigration_= (value)
        println("Set last to '"+lastMigration.id+"'")
      }
      override def up( to:Migration ){
        print( "Upgrading '"+to.id+"' ... ")
        super.up(to)
        applied = applied :+ to
        println("done!")
      }
      override def down( to:Migration ){
        print( "Downgrading '"+to.id+"' ... ")
        super.down(to)
        applied = applied.reverse.drop(1).reverse
        println("done!")
      }
    }
    m.initialize
    
    assertEquals( m.lastApplied, m.initial )
    m.upgradeTo("m3")
    assertEquals( m.lastApplied, m.byName("m3") )
    assertEquals( applied.length, 3 )
    
    m.downgradeTo("m1")
    assertEquals( m.lastApplied, m.byName("m1") )
    assertEquals( applied.length, 1 )

    m.upgradeTo("m3")
    assertEquals( applied.length, 3 )

    try{
      m.downgradeTo("m4")
      assert(false)
    } catch{ case e:Exception => () }
    assertEquals( applied.length, 3 )
    
    m.downgradeTo(m.initial)
    assertEquals( applied.length, 0 )

    try{
      m.upgradeTo("fooooo")
      assert(false)
    } catch{ case e:Exception => () }
    
}}
}