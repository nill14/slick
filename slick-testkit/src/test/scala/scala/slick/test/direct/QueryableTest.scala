package scala.slick.test.direct
import scala.slick.direct.order._

import scala.language.{reflectiveCalls,implicitConversions}
import org.junit.Test
import org.junit.Assert._
import scala.slick.lifted._
import scala.slick.ast.Library.{SqlOperator =>Op,_}
import scala.slick.ast.{Library => Ops}
import scala.slick.ast._
import scala.slick.direct._
import scala.slick.direct.AnnotationMapper._
import scala.slick.testutil._
import slick.jdbc.StaticQuery.interpolation
import scala.reflect.runtime.universe.TypeTag
import scala.reflect.ClassTag
import com.typesafe.slick.testkit.util.TestDB


object QueryableTest extends DBTestObject(TestDBs.H2Mem)

class Foo[T]( val q : Queryable[T] )

@table(name="COFFEES")
case class Coffee(
  @column(name="COF_NAME")
  name : String,
  @column // <- assumes "SALES" automatically
  sales : Int
)

class QueryableTest(val tdb: TestDB) extends DBTest {
  import tdb.driver.backend.Database.threadLocalSession

  object backend extends SlickBackend(tdb.driver,AnnotationMapper)

  object TestingTools{
    def enableAssertQuery[T:TypeTag:ClassTag]( q:Queryable[T] ) = new{
      def assertQuery( matcher : Node => Unit ) = {
        //backend.dump(q)
        println( backend.toSql(q,threadLocalSession) )
        println( backend.result(q,threadLocalSession) )
        try{
          matcher( backend.toQuery( q )._2.node : @unchecked ) : @unchecked
          print(".")
        } catch {
          case e:MatchError => {
            println("F")
            println("")
            backend.dump(q)
            assert(false,"did not match")
          }
        }
      }
    }
    object TableName{
      def unapply( t:TableNode ) = {
        val name = t.tableName
        Some(name)
      }
    }
    object ColumnName{
      def unapply( t:Symbol ) = t match {
        case FieldSymbol( name ) =>
          /*case RawNamedColumn( name, _, _ ) =>*/
          Some(name)
      }
    }
    def fail(msg:String = ""){
      println("F")
      throw new Exception(msg)
    }
    def fail : Unit = fail()
    def success{ print(".") }
    def assertEqualMultiSet[T]( lhs:scala.collection.Traversable[T], rhs:scala.collection.Traversable[T] ) = assertEquals( lhs.groupBy(x=>x), rhs.groupBy(x=>x) )
    def assertMatch[T:TypeTag:ClassTag]( queryable:Queryable[T], expected: Traversable[T] ) = assertEqualMultiSet( backend.result(queryable,threadLocalSession), expected)
    def assertNotEqualMultiSet[T]( lhs:scala.collection.Traversable[T], rhs:scala.collection.Traversable[T] ) = assertEquals( lhs.groupBy(x=>x), rhs.groupBy(x=>x) )
    def assertNoMatch[T:TypeTag:ClassTag]( queryable:Queryable[T], expected: Traversable[T] ) = try{
      assertEqualMultiSet( backend.result(queryable,threadLocalSession), expected)
    } catch {
      case e:AssertionError => 
      case e:Throwable => throw e
    }
    def assertMatchOrdered[T:TypeTag:ClassTag]( queryable:Queryable[T], expected: Traversable[T] ) = assertEquals( backend.result(queryable,threadLocalSession), expected )
  }

  @Test def test() {
    import TestingTools._
    
    val coffees_data = Vector(
      ("Colombian",          1),
      ("French_Roast",       2),
      ("Espresso",           3),
      ("Colombian_Decaf",    4),
      ("French_Roast_Decaf", 5)
    )
    
    db withSession {
      // create test table
      sqlu"create table COFFEES(COF_NAME varchar(255), SALES int)".execute
      (for {
        (name, sales) <- coffees_data
      } yield sqlu"insert into COFFEES values ($name, $sales)".first).sum

      // FIXME: reflective typecheck failed:  backend.result(Queryable[Coffee].map(x=>x))
      
      // setup query and equivalent inMemory data structure
      val inMem = coffees_data.map{ case (name, sales) => Coffee(name, sales) }
      val query : Queryable[Coffee] = Queryable[Coffee]

      // test framework sanity checks
      assertNoMatch(query, inMem ++ inMem)
      assertNoMatch(query, List())

      // fetch whole table
      assertMatch( query, inMem )

      // FIXME: make this test less artificial
      class MyQuerycollection{
        def findUserByName( name:String ) = query.filter( _.name == name )
      }  
      val qc = new MyQuerycollection
      qc.findUserByName("some value")
  
      // simple map
      assertMatch(
        query.map( (_:Coffee).sales + 5 ),
        inMem.map( (_:Coffee).sales + 5 )
      )
      
      // left-hand-side coming from attribute
      val foo = new Foo(query)
      assertMatch(
        foo.q.map( (_:Coffee).sales + 5 ),
        inMem.map( (_:Coffee).sales + 5 )
      )
  
      // map with string concatenation
      assertMatch(
        query.map( _.name + "." ),
        inMem.map( _.name + "." )
      )
  
      // filter with more complex condition
      assertMatch(
        query.filter( c => c.sales > 5 || "Chris" == c.name ),
        inMem.filter( c => c.sales > 5 || "Chris" == c.name )
      )
  
      // type annotations FIXME canBuildFrom
      assertMatch(
        query.map[String]( (_:Coffee).name : String ),
        inMem.map        ( (_:Coffee).name : String )
      )

      // chaining
      assertMatch(
        query.map( _.name ).filter(_ == ""),
        inMem.map( _.name ).filter(_ == "")
      )
  
      // referenced values are inlined as constants using reflection
      val o = 2 + 3
      assertMatch(
        query.filter( _.sales > o ),
        inMem.filter( _.sales > o )
      )

      // nesting (not supported yet: query.map(e1 => query.map(e2=>e1))) 
      assertMatch(
        query.flatMap(e1 => query.map(e2=>e1)),
        inMem.flatMap(e1 => inMem.map(e2=>e1))
      )
  
      // query scope
      {
        val inMemResult = inMem.filter( _.sales > 5 )
        List(
          query.filter( _.sales > 5 ),
          Queryable( query.filter( _.sales > 5 ) ),
          Queryable{
            val foo = query
            val bar = foo.filter( _.sales > 5 )
            bar  
          }
        ).foreach{
          query_ => assertMatch( query_, inMemResult )
        }
      }

      // comprehension with map
      assertMatch(
        for( c <- query ) yield c.name,
        for( c <- inMem ) yield c.name
      )
  
      // nesting with flatMap
      {
        val inMemResult = for( o <- inMem; i <- inMem ) yield i.name
        List(
                   query.flatMap( o => query.map(i => i.name) ),
                    for( o <- query; i <- query ) yield i.name ,
          Queryable(for( o <- query; i <- query ) yield i.name)
        ).foreach{
          query_ => assertMatch( query_, inMemResult )
        }
      }

      assertMatch(
        query.flatMap(e1 => query.map(e2=>e1).map(e2=>e1)),
        inMem.flatMap(e1 => inMem.map(e2=>e1).map(e2=>e1))
      ) 

      // nesting with outer macro reference
      {
        val inMemResult = for( o <- inMem; i <- inMem ) yield o.name
        List(
                   query.flatMap( o => query.map(i => o.name) ),
                   for( o <- query; i <- query ) yield o.name ,
          Queryable(for( o <- query; i <- query ) yield o.name)
        ).foreach{
          query_ => assertMatch( query_, inMemResult )
        }
      }
  
      // nesting with chaining / comprehension with cartesian product and if
      {
        val inMemResult = for( o <- inMem; i <- inMem; if i.sales == o.sales ) yield i.name
        List(
          query.flatMap(o => query.filter( i => i.sales == o.sales ).map(i => i.name)),
                    for( o <- query; i <- query; if i.sales == o.sales ) yield i.name ,
          Queryable(for( o <- query; i <- query; if i.sales == o.sales ) yield i.name)
        ).foreach{
          query_ => assertMatch( query_, inMemResult )
        }
      }

      // tuples
      assertMatch(
        query.map( c=> (c.name,c.sales) ),
        inMem.map( c=> (c.name,c.sales) )
      )
      
      // nested structures (here tuples and case classes)
      assertMatch(
        query.map( c=> (c.name,c.sales,c) ),
        inMem.map( c=> (c.name,c.sales,c) )
      )
      // length
      assertEquals( backend.result(query.length,threadLocalSession), inMem.length )

      val iquery = ImplicitQueryable( query, backend, threadLocalSession )
      assertEquals( iquery.length, inMem.length )
      
      // iquery.filter( _.sales > 10.0 ).map( _.name ) // currently crashed compiler
      
      assertMatch(
        query.map( c=>c ),
        iquery.map( c=>c ).toSeq
      )
      
      ({
        import ImplicitQueryable.implicitExecution._
        assertMatch(
          query.map( c=>c ),
          iquery.map( c=>c )
        )
      })
   
      assertMatch(
           for( o <-  query; i <-  query; if i.sales == o.sales  ) yield i.name,
          (for( o <- iquery; i <- iquery; if i.sales == o.sales ) yield i.name).toSeq
      )
      
      assertMatch(
        for( v1<-query;v2<-query; if !(v1.name == v2.name)) yield (v1.name,v2.name)
        ,for( v1<-inMem;v2<-inMem; if !(v1.name == v2.name)) yield (v1.name,v2.name)
      )
      
      assertMatch(
        for( v1<-query;v2<-query; if v1.name != v2.name) yield (v1.name,v2.name)
        ,for( v1<-inMem;v2<-inMem; if v1.name != v2.name) yield (v1.name,v2.name)
      )
      
      assertMatch(
        query.take(2)
        ,inMem.take(2)
      )
      
      assertMatch(
        query.drop(2)
        ,inMem.drop(2)
      )
      
      assertMatchOrdered(
        query.sortBy(_.name)
        ,inMem.sortBy(_.name)
      )
      
      assertMatchOrdered(
        query.sortBy(reversed(_.name))
        ,inMem.sortBy(reversed(_.name))
      )
    }
  }
  @Test def sortingTest(){
//    def assertEquals[T]( a:T, b:T ) = assert( a == b)
    val cA0 = Coffee("A",0) 
    val cA1 = Coffee("A",1) 
    val cB0 = Coffee("B",0) 
    val cB1 = Coffee("B",1) 
    val coffees = List( cA1, cB0, cA0, cB1 )
    assertEquals(
      List(cA1,cA0,cB0,cB1),
      coffees.sortBy(_.name)
    )
    assertEquals(
      List(cA1,cA0,cB0,cB1),
      coffees.sortBy(_.name)
    )
    assertEquals(
      List(cB0,cB1,cA1,cA0),
      coffees.sortBy(c=>reversed(c.name))
    )
    assertEquals(
      List(cA1,cA0,cB1,cB0),
      coffees.sortBy(c => (c.name,reversed(c.sales)))
    )
    //assertEquals( coffees.sortBy(_.name,asc), by(_.sales,desc) ), List(cA1,cA0,cB1,cB0) )
    assertEquals(
      List(cA1,cA0,cB1,cB0),
      coffees.sortBy( c => (
        c.name
        ,reversed(c.sales)
        ,reversed(c.sales)
      ))
    )
  }
}
