import org.kalasim.Component
import org.kalasim.createSimulation

// can components be extended into data classes? Yes they can.

data class Foo(val bar: String): Component(){
    override fun process(): Sequence<Component> {
        return super.process()
    }
}

fun main() {
    createSimulation {
        Foo("").apply {
            println(toString())
        }


    }.run(10)
}